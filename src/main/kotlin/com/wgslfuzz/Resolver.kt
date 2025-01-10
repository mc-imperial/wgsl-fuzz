package com.wgslfuzz

sealed interface ScopeEntry {
    val astNode: AstNode

    class Function(
        override val astNode: GlobalDecl.Function,
        val type: FunctionType,
    ) : ScopeEntry

    sealed interface TypedDecl : ScopeEntry {
        val type: Type
    }

    class Parameter(
        override val astNode: ParameterDecl,
        override val type: Type,
    ) : TypedDecl

    class LocalVariable(
        override val astNode: Statement.Variable,
        override val type: Type,
    ) : TypedDecl

    class LocalValue(
        override val astNode: Statement.Value,
        override val type: Type,
    ) : TypedDecl

    class GlobalVariable(
        override val astNode: GlobalDecl.Variable,
        override val type: Type,
    ) : TypedDecl

    class GlobalConstant(
        override val astNode: GlobalDecl.Constant,
        override val type: Type,
    ) : TypedDecl

    class GlobalOverride(
        override val astNode: GlobalDecl.Override,
        override val type: Type,
    ) : TypedDecl

    class Struct(
        override val astNode: GlobalDecl.Struct,
        override val type: Type.Struct,
    ) : TypedDecl

    class TypeAlias(
        override val astNode: GlobalDecl.TypeAlias,
        override val type: Type,
    ) : TypedDecl
}

interface Scope {
    val parent: Scope?
    val enclosingAstNode: AstNode

    fun getEntry(name: String): ScopeEntry?
}

private class ScopeImpl(
    override val parent: ScopeImpl?,
    override val enclosingAstNode: AstNode,
) : Scope {
    private val entries: MutableMap<String, ScopeEntry> = mutableMapOf()

    fun addEntry(
        name: String,
        node: ScopeEntry,
    ) {
        assert(name !in entries.keys)
        entries[name] = node
    }

    override fun getEntry(name: String): ScopeEntry? = entries[name] ?: parent?.getEntry(name)
}

sealed interface ResolvedEnvironment {
    fun typeOf(expression: Expression): Type

    fun typeOf(functionDecl: GlobalDecl.Function): FunctionType

    fun enclosingScope(statement: Statement): Scope
}

private class ResolvedEnvironmentImpl : ResolvedEnvironment {
    private val expressionTypes: MutableMap<Expression, Type> = mutableMapOf()

    private val statementScopes: MutableMap<Statement, Scope> = mutableMapOf()

    fun recordType(
        expression: Expression,
        type: Type,
    ) {
        assert(expression !in expressionTypes.keys)
        expressionTypes[expression] = type
    }

    fun recordScope(
        statement: Statement,
        scope: Scope,
    ) {
        assert(statement !in statementScopes.keys)
        statementScopes[statement] = scope
    }

    override fun typeOf(expression: Expression): Type =
        expressionTypes[expression] ?: throw UnsupportedOperationException("No type for $expression")

    override fun typeOf(functionDecl: GlobalDecl.Function): FunctionType {
        TODO("Not yet implemented")
    }

    override fun enclosingScope(statement: Statement): Scope =
        statementScopes[statement] ?: throw UnsupportedOperationException("No scope for $statement")
}

private fun collectUsedModuleScopeNames(node: AstNode): Set<String> {
    fun collectAction(
        node: AstNode,
        collectedNames: MutableSet<String>,
    ) {
        traverse(::collectAction, node, collectedNames)
        when (node) {
            is Expression.Identifier -> collectedNames.add(node.name)
            is Expression.StructValueConstructor -> collectedNames.add(node.typeName)
            is TypeDecl.NamedType -> {
                traverse(::collectAction, node, collectedNames)
                collectedNames.add(node.name)
            }
            else -> {}
        }
    }

    val result = mutableSetOf<String>()
    collectAction(node, result)
    return result
}

private fun collectUsedModuleScopeNames(
    name: String,
    result: MutableMap<String, Set<String>>,
    topLevelFunctionComponents: List<AstNode?>,
) {
    if (name in result.keys) {
        throw IllegalArgumentException("Duplicate module-scope name: $name")
    }
    result[name] =
        topLevelFunctionComponents
            .filterNotNull()
            .map(::collectUsedModuleScopeNames)
            .reduceOrNull(
                operation = Set<String>::plus,
            ) ?: emptySet()
}

private fun collectTopLevelNameDependencies(tu: TranslationUnit): Pair<
    Map<String, Set<String>>,
    Map<String, GlobalDecl>,
> {
    val nameDependencies = mutableMapOf<String, Set<String>>()
    val nameToDecl = mutableMapOf<String, GlobalDecl>()
    for (decl in tu.globalDecls) {
        when (decl) {
            is GlobalDecl.Empty -> {}
            is GlobalDecl.ConstAssert -> {}
            is GlobalDecl.Constant -> {
                nameToDecl[decl.name] = decl
                collectUsedModuleScopeNames(
                    decl.name,
                    nameDependencies,
                    listOf(
                        decl.type,
                        decl.initializer,
                    ),
                )
            }
            is GlobalDecl.Function -> {
                nameToDecl[decl.name] = decl
                collectUsedModuleScopeNames(
                    decl.name,
                    nameDependencies,
                    decl.attributes + decl.parameters + listOf(decl.returnType),
                )
            }
            is GlobalDecl.Override -> {
                nameToDecl[decl.name] = decl
                collectUsedModuleScopeNames(
                    decl.name,
                    nameDependencies,
                    decl.attributes + listOf(decl.type, decl.initializer),
                )
            }
            is GlobalDecl.Struct -> {
                nameToDecl[decl.name] = decl
                collectUsedModuleScopeNames(decl.name, nameDependencies, decl.members)
            }
            is GlobalDecl.TypeAlias -> {
                nameToDecl[decl.name] = decl
                collectUsedModuleScopeNames(decl.name, nameDependencies, listOf(decl.type))
            }
            is GlobalDecl.Variable -> {
                nameToDecl[decl.name] = decl
                collectUsedModuleScopeNames(
                    decl.name,
                    nameDependencies,
                    decl.attributes + listOf(decl.type, decl.initializer),
                )
            }
        }
    }
    return Pair(nameDependencies, nameToDecl)
}

private fun orderGlobalDeclNames(topLevelNameDependences: Map<String, Set<String>>): List<String> {
    val toProcess = mutableMapOf<String, MutableSet<String>>()
    for (entry in topLevelNameDependences) {
        toProcess[entry.key] = entry.value.toMutableSet()
    }
    for (key in toProcess.keys) {
        toProcess[key] =
            toProcess[key]!!
                .filter {
                    toProcess.keys.contains(it)
                }.toMutableSet()
    }
    val result = mutableListOf<String>()
    while (toProcess.isNotEmpty()) {
        val addedThisRound = toProcess.entries.filter { it.value.isEmpty() }.map { it.key }
        if (addedThisRound.isEmpty()) {
            throw RuntimeException("Cycle detected in globally-scoped declarations.")
        }
        for (name in addedThisRound) {
            toProcess.remove(name)
        }
        for (entry in toProcess) {
            entry.value.removeAll(addedThisRound)
        }
        result.addAll(addedThisRound)
    }
    return result
}

private class ResolverState(
    val resolvedEnvironment: ResolvedEnvironmentImpl,
    var currentScope: ScopeImpl,
) {
    fun withScope(
        node: AstNode,
        action: () -> Unit,
    ) {
        val newScope: ScopeImpl =
            ScopeImpl(
                parent = currentScope,
                enclosingAstNode = node,
            )
        currentScope = newScope
        action()
        currentScope = newScope.parent!!
    }
}

private fun resolveAstNode(
    node: AstNode,
    resolverState: ResolverState,
) {
    if (node is GlobalDecl) {
        // Functions are handled in a special manner
        assert(node !is GlobalDecl.Function)
    }

    if (node is Statement) {
        resolverState.resolvedEnvironment.recordScope(node, resolverState.currentScope)
    }

    if (node is Statement.Compound ||
        node is Statement.For ||
        node is Statement.Loop ||
        node is ContinuingStatement
    ) {
        resolverState.withScope(node) {
            traverse(::resolveAstNode, node, resolverState)
        }
    } else {
        traverse(::resolveAstNode, node, resolverState)
    }

    // TODO - consider cutting off traversal so that type declarations are not visited as these are handled separately
    when (node) {
        is GlobalDecl.TypeAlias -> {
            resolverState.currentScope.addEntry(
                node.name,
                ScopeEntry.TypeAlias(
                    node,
                    resolveTypeDecl(node.type, resolverState),
                ),
            )
        }
        is GlobalDecl.Variable -> {
            val type: Type =
                defaultConcretizationOf(
                    node.type?.let {
                        resolveTypeDecl(node.type!!, resolverState)
                    } ?: resolverState.resolvedEnvironment.typeOf(node.initializer!!),
                )
            resolverState.currentScope.addEntry(
                node.name,
                ScopeEntry.GlobalVariable(node, type),
            )
        }
        is GlobalDecl.Constant -> {
            resolverState.currentScope.addEntry(
                node.name,
                ScopeEntry.GlobalConstant(
                    astNode = node,
                    type =
                        node.type?.let {
                            resolveTypeDecl(it, resolverState)
                        } ?: resolverState.resolvedEnvironment.typeOf(node.initializer),
                ),
            )
        }
        is GlobalDecl.Override -> {
            resolverState.currentScope.addEntry(
                node.name,
                ScopeEntry.GlobalOverride(
                    astNode = node,
                    type =
                        node.type?.let {
                            resolveTypeDecl(it, resolverState)
                        } ?: resolverState.resolvedEnvironment.typeOf(node.initializer!!),
                ),
            )
        }
        is GlobalDecl.Struct -> {
            resolverState.currentScope.addEntry(
                node.name,
                ScopeEntry.Struct(
                    astNode = node,
                    type =
                        Type.Struct(
                            name = node.name,
                            members =
                                node.members.associate {
                                    it.name to resolveTypeDecl(it.type, resolverState)
                                },
                        ),
                ),
            )
        }
        is Statement.Value -> {
            var type: Type =
                node.type?.let {
                    resolveTypeDecl(node.type!!, resolverState)
                } ?: resolverState.resolvedEnvironment.typeOf(node.initializer)
            if (type.isAbstract()) {
                type = defaultConcretizationOf(type)
            }
            resolverState.currentScope.addEntry(
                node.name,
                ScopeEntry.LocalValue(node, type),
            )
        }
        is Statement.Variable -> {
            var type: Type =
                node.type?.let {
                    resolveTypeDecl(node.type!!, resolverState)
                } ?: resolverState.resolvedEnvironment.typeOf(node.initializer!!)
            if (type.isAbstract()) {
                type = defaultConcretizationOf(type)
            }
            resolverState.currentScope.addEntry(
                node.name,
                ScopeEntry.LocalVariable(node, type),
            )
        }
        is Expression -> resolverState.resolvedEnvironment.recordType(node, resolveExpressionType(node, resolverState))
        else -> {
            // No action
        }
    }
}

private fun resolveExpressionType(
    expression: Expression,
    resolverState: ResolverState,
): Type =
    when (expression) {
        is Expression.FunctionCall -> resolveTypeOfFunctionCallExpression(expression, resolverState)
        is Expression.IndexLookup ->
            when (val targetType = resolverState.resolvedEnvironment.typeOf(expression.target)) {
                is Type.Matrix ->
                    Type.Vector(
                        width = targetType.numRows,
                        elementType = targetType.elementType,
                    )
                is Type.Vector -> targetType.elementType
                is Type.Array -> targetType.elementType
                is Type.Pointer -> {
                    when (val pointeeType = targetType.pointeeType) {
                        is Type.Vector -> pointeeType.elementType
                        is Type.Array -> pointeeType.elementType
                        is Type.Matrix -> Type.Vector(pointeeType.numRows, pointeeType.elementType)
                        else -> TODO("Index lookup on pointer with pointee type ${targetType.pointeeType}")
                    }
                }
                else -> throw RuntimeException("Index lookup attempted on unsuitable type $targetType.")
            }
        is Expression.MemberLookup ->
            when (val receiverType = resolverState.resolvedEnvironment.typeOf(expression.receiver)) {
                is Type.Struct ->
                    receiverType.members[expression.memberName]
                        ?: throw RuntimeException("Struct with type $receiverType does not have a member ${expression.memberName}")
                is Type.Vector ->
                    // In the following we could check whether the vector indices exist, e.g. using z on a vec2 is not be allowed.
                    if (expression.memberName in setOf("x", "y", "z", "w")) {
                        receiverType.elementType
                    } else if (isSwizzle(expression.memberName)) {
                        Type.Vector(expression.memberName.length, receiverType.elementType)
                    } else {
                        TODO()
                    }
                is Type.Pointer ->
                    when (val pointeeType = receiverType.pointeeType) {
                        is Type.Vector ->
                            // In the following we could check whether the vector indices exist, e.g. using z on a vec2 is not be allowed.
                            if (expression.memberName in setOf("x", "y", "z", "w")) {
                                pointeeType.elementType
                            } else if (isSwizzle(expression.memberName)) {
                                Type.Vector(expression.memberName.length, pointeeType.elementType)
                            } else {
                                TODO()
                            }
                        is Type.Struct ->
                            pointeeType.members[expression.memberName]
                                ?: throw RuntimeException("Struct with type $receiverType does not have a member ${expression.memberName}")
                        else -> TODO("${receiverType.pointeeType}")
                    }
                else -> TODO("Member lookup not implemented for receiver of type $receiverType")
            }
        is Expression.FloatLiteral ->
            if (expression.text.endsWith("f")) {
                Type.F32
            } else if (expression.text.endsWith("h")) {
                Type.F16
            } else {
                Type.AbstractFloat
            }
        is Expression.IntLiteral ->
            if (expression.text.endsWith("u")) {
                Type.U32
            } else if (expression.text.endsWith("i")) {
                Type.I32
            } else {
                Type.AbstractInteger
            }
        is Expression.BoolLiteral ->
            Type.Bool
        is Expression.Binary -> resolveBinary(resolverState, expression)
        is Expression.Unary -> resolveUnary(expression, resolverState)
        is Expression.Paren -> resolverState.resolvedEnvironment.typeOf(expression.target)
        is Expression.Identifier ->
            when (val scopeEntry = resolverState.currentScope.getEntry(expression.name)) {
                is ScopeEntry.TypedDecl -> scopeEntry.type
                else -> throw RuntimeException("Identifier ${expression.name} does not have a typed scope entry.")
            }
        is Expression.BoolValueConstructor -> Type.Bool
        is Expression.I32ValueConstructor -> Type.I32
        is Expression.U32ValueConstructor -> Type.U32
        is Expression.F32ValueConstructor -> Type.F32
        is Expression.F16ValueConstructor -> Type.F16
        is Expression.VectorValueConstructor -> resolveTypeOfVectorValueConstructor(expression, resolverState)
        is Expression.MatrixValueConstructor -> resolveTypeOfMatrixValueConstructor(expression, resolverState)
        is Expression.ArrayValueConstructor -> resolveTypeOfArrayValueConstructor(expression, resolverState)
        is Expression.StructValueConstructor ->
            when (val scopeEntry = resolverState.currentScope.getEntry(expression.typeName)) {
                is ScopeEntry.Struct -> scopeEntry.type
                else -> throw RuntimeException(
                    "Attempt to construct a struct with constructor ${expression.typeName}, which is not a struct type.",
                )
            }
        is Expression.TypeAliasValueConstructor ->
            (
                resolverState.currentScope.getEntry(
                    expression.typeName,
                ) as ScopeEntry.TypeAlias
            ).type
        else -> TODO("Unsupported expression $expression")
    }

private fun resolveUnary(
    expression: Expression.Unary,
    resolverState: ResolverState,
) = when (expression.operator) {
    UnaryOperator.DEREFERENCE -> {
        val pointerType = resolverState.resolvedEnvironment.typeOf(expression.target)
        if (pointerType !is Type.Pointer) {
            throw RuntimeException("Dereference applied to expression $expression with non-pointer type")
        }
        pointerType.pointeeType
    }

    UnaryOperator.ADDRESS_OF -> {
        resolveTypeOfAddressOfExpression(expression, resolverState)
    }

    UnaryOperator.LOGICAL_NOT -> {
        assert(resolverState.resolvedEnvironment.typeOf(expression.target) == Type.Bool)
        Type.Bool
    }

    UnaryOperator.MINUS, UnaryOperator.BINARY_NOT -> resolverState.resolvedEnvironment.typeOf(expression.target)
}

private fun resolveBinary(
    resolverState: ResolverState,
    expression: Expression.Binary,
): Type {
    val lhsType = resolverState.resolvedEnvironment.typeOf(expression.lhs)
    val rhsType = resolverState.resolvedEnvironment.typeOf(expression.rhs)
    return when (val operator = expression.operator) {
        BinaryOperator.LESS_THAN,
        BinaryOperator.LESS_THAN_EQUAL,
        BinaryOperator.GREATER_THAN,
        BinaryOperator.GREATER_THAN_EQUAL,
        ->
            when (lhsType) {
                is Type.Scalar -> Type.Bool
                else -> TODO()
            }

        BinaryOperator.PLUS, BinaryOperator.MINUS, BinaryOperator.DIVIDE, BinaryOperator.MODULO ->
            if (rhsType.isAbstractionOf(lhsType)) {
                lhsType
            } else if (lhsType.isAbstractionOf(rhsType)) {
                rhsType
            } else {
                TODO("$operator not supported for $lhsType and $rhsType")
            }

        BinaryOperator.TIMES ->
            if (rhsType.isAbstractionOf(lhsType)) {
                lhsType
            } else if (lhsType.isAbstractionOf(rhsType)) {
                rhsType
            } else if (lhsType is Type.Vector && rhsType is Type.Scalar) {
                val lhsElementType = lhsType.elementType
                if (rhsType.isAbstractionOf(lhsElementType)) {
                    lhsType
                } else if (lhsElementType.isAbstractionOf(rhsType)) {
                    Type.Vector(lhsType.width, rhsType)
                } else {
                    TODO("$operator not supported for $lhsType and $rhsType")
                }
            } else if (lhsType is Type.Matrix && rhsType is Type.Vector) {
                Type.Vector(lhsType.numRows, findCommonType(listOf(lhsType.elementType, rhsType.elementType)) as Type.Float)
            } else if (lhsType is Type.Vector && rhsType is Type.Matrix) {
                Type.Vector(rhsType.numCols, findCommonType(listOf(lhsType.elementType, rhsType.elementType)) as Type.Float)
            } else if (lhsType is Type.Matrix && rhsType is Type.Matrix) {
                TODO()
            } else {
                TODO("$operator not supported for $lhsType and $rhsType")
            }

        BinaryOperator.EQUAL_EQUAL, BinaryOperator.NOT_EQUAL ->
            when (lhsType) {
                is Type.Scalar -> Type.Bool
                is Type.Vector -> Type.Vector(width = lhsType.width, elementType = Type.Bool)
                else -> throw RuntimeException("== operator is only supported for scalar and vector types.")
            }

        BinaryOperator.SHORT_CIRCUIT_AND ->
            if (lhsType != Type.Bool || rhsType != Type.Bool) {
                throw RuntimeException("Short circuit && and || operators require bool arguments.")
            } else {
                Type.Bool
            }

        BinaryOperator.BINARY_AND, BinaryOperator.BINARY_OR, BinaryOperator.BINARY_XOR ->
            if (rhsType.isAbstractionOf(lhsType)) {
                lhsType
            } else if (lhsType.isAbstractionOf(rhsType)) {
                rhsType
            } else {
                throw RuntimeException("Unsupported types for bitwise operation.")
            }

        BinaryOperator.SHIFT_LEFT, BinaryOperator.SHIFT_RIGHT -> lhsType

        else -> TODO("Not implemented support for ${expression.operator}")
    }
}

private fun resolveTypeOfVectorValueConstructor(
    expression: Expression.VectorValueConstructor,
    resolverState: ResolverState,
): Type.Vector {
    val elementType: Type.Scalar =
        if (expression.elementType != null) {
            resolveTypeDecl(expression.elementType!!, resolverState) as Type.Scalar
        } else if (expression.args.isEmpty()) {
            Type.AbstractInteger
        } else {
            var candidateElementType: Type.Scalar? = null
            for (arg in expression.args) {
                var elementTypeForArg = resolverState.resolvedEnvironment.typeOf(arg)
                when (elementTypeForArg) {
                    is Type.Scalar -> {
                        // Nothing to do
                    }
                    is Type.Vector -> {
                        elementTypeForArg = elementTypeForArg.elementType
                    }
                    else -> {
                        throw RuntimeException("A vector may only be constructed from vectors and scalars.")
                    }
                }
                if (candidateElementType == null || candidateElementType.isAbstractionOf(elementTypeForArg)) {
                    candidateElementType = elementTypeForArg
                } else if (!elementTypeForArg.isAbstractionOf(candidateElementType)) {
                    throw RuntimeException("Vector constructed from incompatible mix of element types.")
                }
            }
            candidateElementType!!
        }
    return when (expression) {
        is Expression.Vec2ValueConstructor -> Type.Vector(2, elementType)
        is Expression.Vec3ValueConstructor -> Type.Vector(3, elementType)
        is Expression.Vec4ValueConstructor -> Type.Vector(4, elementType)
    }
}

private fun resolveTypeOfMatrixValueConstructor(
    expression: Expression.MatrixValueConstructor,
    resolverState: ResolverState,
): Type.Matrix {
    val elementType: Type.Float =
        if (expression.elementType != null) {
            resolveTypeDecl(expression.elementType!!, resolverState) as Type.Float
        } else {
            var candidateElementType: Type.Scalar? = null
            for (arg in expression.args) {
                var elementTypeForArg = resolverState.resolvedEnvironment.typeOf(arg)
                when (elementTypeForArg) {
                    is Type.Float -> {
                        // Nothing to do
                    }
                    is Type.Vector -> {
                        elementTypeForArg = elementTypeForArg.elementType
                    }
                    is Type.Matrix -> {
                        elementTypeForArg = elementTypeForArg.elementType
                    }
                    else -> {
                        throw RuntimeException("A matrix may only be constructed from matrices, vectors and scalars.")
                    }
                }
                if (candidateElementType == null || candidateElementType.isAbstractionOf(elementTypeForArg)) {
                    candidateElementType = (elementTypeForArg as Type.Scalar) // Kotlin typechecker bug? This "as" should not be needed.
                } else if (!elementTypeForArg.isAbstractionOf(candidateElementType)) {
                    throw RuntimeException("Matrix constructed from incompatible mix of element types.")
                }
            }
            when (candidateElementType) {
                is Type.Float -> candidateElementType
                is Type.AbstractInteger -> Type.AbstractFloat
                else -> throw RuntimeException("Invalid types provided to matrix constructor.")
            }
        }
    return when (expression) {
        is Expression.Mat2x2ValueConstructor -> Type.Matrix(2, 2, elementType)
        is Expression.Mat2x3ValueConstructor -> Type.Matrix(2, 3, elementType)
        is Expression.Mat2x4ValueConstructor -> Type.Matrix(2, 4, elementType)
        is Expression.Mat3x2ValueConstructor -> Type.Matrix(3, 2, elementType)
        is Expression.Mat3x3ValueConstructor -> Type.Matrix(3, 3, elementType)
        is Expression.Mat3x4ValueConstructor -> Type.Matrix(3, 4, elementType)
        is Expression.Mat4x2ValueConstructor -> Type.Matrix(4, 2, elementType)
        is Expression.Mat4x3ValueConstructor -> Type.Matrix(4, 3, elementType)
        is Expression.Mat4x4ValueConstructor -> Type.Matrix(4, 4, elementType)
    }
}

private fun resolveTypeOfArrayValueConstructor(
    expression: Expression.ArrayValueConstructor,
    resolverState: ResolverState,
): Type.Array {
    val elementType: Type =
        expression.elementType?.let { resolveTypeDecl(it, resolverState) } ?: if (expression.args.isEmpty()) {
            throw RuntimeException("Cannot work out element type of empty array constructor.")
        } else {
            findCommonType(expression.args, resolverState)
        }
    val elementCount: Int =
        expression.elementCount?.let {
            evaluateToInt(expression.elementCount!!, resolverState)
        } ?: expression.args.size
    return Type.Array(elementType, elementCount)
}

private fun resolveTypeOfFunctionCallExpression(
    functionCallExpression: Expression.FunctionCall,
    resolverState: ResolverState,
): Type =
    when (val scopeEntry = resolverState.currentScope.getEntry(functionCallExpression.callee)) {
        null ->
            when (val calleeName = functionCallExpression.callee) {
                // 1-argument functions with return type same as argument type, allowing for the case where both are
                // abstract.
                // TODO go through these and check which ones support abstract types. Those that need concretisation
                //  should be moved to the next case.
                "abs", "acos", "acosh", "asin", "asinh", "atan", "atanh", "ceil", "cos", "cosh", "degrees", "dpdx",
                "dpdxCoarse", "dpdxFine", "dpdy", "dpdyCoarse", "dpdyFine", "exp", "exp2", "floor", "fract", "fwidth",
                "fwidthCoarse", "fwidthFine", "inverseSqrt", "log", "log2", "normalize", "quantizeToF16", "radians",
                "round", "saturate", "sign", "sin", "sinh", "sqrt", "tan", "tanh", "trunc",
                -> {
                    if (functionCallExpression.args.size != 1) {
                        throw RuntimeException("$calleeName requires one argument.")
                    } else {
                        resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0])
                    }
                }
                // 1-argument homogeneous functions with return type same as concretisation of argument type
                "reverseBits",
                -> {
                    if (functionCallExpression.args.size != 1) {
                        throw RuntimeException("$calleeName requires one argument.")
                    } else {
                        defaultConcretizationOf(resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0]))
                    }
                }
                // 2-argument homogeneous functions with return type same as argument type
                // TODO go through these and check which ones support abstract types. Those that don't need
                //  concretisation
                "atan2", "max", "min", "pow", "reflect", "step" ->
                    if (functionCallExpression.args.size != 2) {
                        throw RuntimeException("$calleeName requires two arguments.")
                    } else {
                        findCommonType(functionCallExpression.args, resolverState)
                    }
                // 3-argument homogeneous functions with return type same as argument type
                "clamp", "faceForward", "fma", "smoothstep" ->
                    if (functionCallExpression.args.size != 3) {
                        throw RuntimeException("$calleeName requires three arguments.")
                    } else {
                        findCommonType(functionCallExpression.args, resolverState)
                    }
                "all", "any" -> Type.Bool
                "arrayLength" -> Type.U32
                "atomicAdd", "atomicSub", "atomicMax", "atomicMin", "atomicAnd", "atomicOr", "atomicXor", "atomicExchange" -> {
                    if (functionCallExpression.args.size != 2) {
                        throw RuntimeException("$calleeName builtin takes two arguments")
                    }
                    val argType = resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0])
                    if (argType !is Type.Pointer || argType.pointeeType !is Type.Atomic) {
                        throw RuntimeException("$calleeName requires a pointer to an atomic integer")
                    }
                    argType.pointeeType.targetType
                }
                "atomicCompareExchangeWeak" -> {
                    if (functionCallExpression.args.size != 3) {
                        throw RuntimeException("atomicCompareExchangeWeak builtin takes three arguments")
                    }
                    val argType = resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0])
                    if (argType !is Type.Pointer || argType.pointeeType !is Type.Atomic) {
                        throw RuntimeException("atomicCompareExchangeWeak requires a pointer to an atomic integer")
                    }
                    when (argType.pointeeType.targetType) {
                        Type.I32 -> AtomicCompareExchangeResultI32
                        Type.U32 -> AtomicCompareExchangeResultU32
                        Type.AbstractInteger -> throw RuntimeException("An atomic integer should not have an abstract target type.")
                    }
                }
                "atomicLoad" -> {
                    if (functionCallExpression.args.size != 1) {
                        throw RuntimeException("atomicLoad builtin takes one argument")
                    }
                    val argType = resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0])
                    if (argType !is Type.Pointer || argType.pointeeType !is Type.Atomic) {
                        throw RuntimeException("atomicLoad requires a pointer to an atomic integer")
                    }
                    argType.pointeeType.targetType
                }
                "bitcast" -> {
                    if (functionCallExpression.templateParameter == null) {
                        throw RuntimeException("bitcast requires a template parameter for the target type.")
                    }
                    resolveTypeDecl(functionCallExpression.templateParameter!!, resolverState)
                }
                "countLeadingZeros", "countOneBits", "countTrailingZeros" -> {
                    if (functionCallExpression.args.size != 1) {
                        throw RuntimeException("$calleeName requires one argument.")
                    } else {
                        defaultConcretizationOf(resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0]))
                    }
                }
                "cross" -> {
                    if (functionCallExpression.args.size != 2) {
                        throw RuntimeException("cross builtin takes two arguments")
                    }
                    val arg1Type = resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0])
                    val arg2Type = resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[1])
                    if (arg1Type !is Type.Vector || arg2Type !is Type.Vector) {
                        throw RuntimeException("cross builtin requires vector arguments")
                    }
                    if (arg1Type.width != 3 || arg2Type.width != 3) {
                        throw RuntimeException("cross builtin requires vec3 arguments")
                    }
                    if (arg1Type.elementType !is Type.Float || arg2Type.elementType !is Type.Float) {
                        throw RuntimeException("cross builtin only works with float vectors")
                    }
                    if (arg1Type.elementType != arg2Type.elementType) {
                        throw RuntimeException("Mismatched vector elemenet types for cross builtin")
                    }
                    arg1Type
                }
                "determinant" -> {
                    if (functionCallExpression.args.size != 1) {
                        throw RuntimeException("determinant builtin function requires one argument")
                    }
                    val argType = resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0])
                    if (argType !is Type.Matrix) {
                        throw RuntimeException("determinant builtin function requires a matrix argument")
                    }
                    if (argType.numRows != argType.numCols) {
                        throw RuntimeException("determinant builtin function requires a square matrix argument")
                    }
                    argType.elementType
                }
                "distance" -> {
                    if (functionCallExpression.args.size != 2) {
                        throw RuntimeException("$calleeName requires two arguments")
                    }
                    val commonType = findCommonType(functionCallExpression.args, resolverState)
                    if (commonType is Type.Vector) {
                        commonType.elementType
                    } else {
                        commonType
                    }
                }
                "dot" -> {
                    if (functionCallExpression.args.size != 2) {
                        throw RuntimeException("dot requires two arguments")
                    }
                    val commonType = findCommonType(functionCallExpression.args, resolverState)
                    if (commonType is Type.Vector) {
                        commonType.elementType
                    } else {
                        throw RuntimeException("dot requires vector arguments")
                    }
                }
                "dot4U8Packed" -> Type.U32
                "dot4I8Packed" -> Type.I32
                "extractBits" -> {
                    if (functionCallExpression.args.size != 3) {
                        throw RuntimeException("extractBits expects three arguments")
                    }
                    defaultConcretizationOf(resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0]))
                }
                "firstLeadingBit", "firstTrailingBit" ->
                    if (functionCallExpression.args.size != 1) {
                        throw RuntimeException("$calleeName requires one argument.")
                    } else {
                        defaultConcretizationOf(resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0]))
                    }
                "frexp" -> {
                    if (functionCallExpression.args.size != 1) {
                        throw RuntimeException("frexp requires one argument.")
                    }
                    when (val argType = resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0])) {
                        Type.F16 -> FrexpResultF16
                        Type.F32 -> FrexpResultF32
                        Type.AbstractFloat -> FrexpResultAbstract
                        is Type.Vector -> {
                            when (argType.elementType) {
                                Type.F16 -> {
                                    when (argType.width) {
                                        2 -> FrexpResultVec2F16
                                        3 -> FrexpResultVec3F16
                                        4 -> FrexpResultVec4F16
                                        else -> throw RuntimeException("Bad vector size.")
                                    }
                                }
                                Type.F32 -> {
                                    when (argType.width) {
                                        2 -> FrexpResultVec2F32
                                        3 -> FrexpResultVec3F32
                                        4 -> FrexpResultVec4F32
                                        else -> throw RuntimeException("Bad vector size.")
                                    }
                                }
                                Type.AbstractFloat -> {
                                    when (argType.width) {
                                        2 -> FrexpResultVec2Abstract
                                        3 -> FrexpResultVec3Abstract
                                        4 -> FrexpResultVec4Abstract
                                        else -> throw RuntimeException("Bad vector size.")
                                    }
                                }
                                else -> throw RuntimeException("Unexpected vector element type of frexp vector argument.")
                            }
                        }
                        else -> throw RuntimeException("Unexpected type of frexp argument.")
                    }
                }
                "insertBits" -> {
                    if (functionCallExpression.args.size != 4) {
                        throw RuntimeException("$calleeName requires three arguments.")
                    } else {
                        defaultConcretizationOf(findCommonType(functionCallExpression.args.dropLast(2), resolverState))
                    }
                }
                "ldexp" -> {
                    if (functionCallExpression.args.size != 2) {
                        throw RuntimeException("$calleeName requires two arguments.")
                    }
                    val arg1Type = resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0])
                    val arg2Type = resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[1])
                    if (arg1Type.isAbstract() && !arg2Type.isAbstract()) {
                        defaultConcretizationOf(arg1Type)
                    } else {
                        arg1Type
                    }
                }
                "length" -> {
                    if (functionCallExpression.args.size != 1) {
                        throw RuntimeException("length requires one argument")
                    }
                    when (val argType = resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0])) {
                        is Type.Float -> argType
                        is Type.Vector -> argType.elementType
                        else -> throw RuntimeException("Unsupported argument for length builtin function")
                    }
                }
                "mat2x2f" -> Type.Matrix(2, 2, Type.F32)
                "mat2x3f" -> Type.Matrix(2, 3, Type.F32)
                "mat2x4f" -> Type.Matrix(2, 4, Type.F32)
                "mat3x2f" -> Type.Matrix(3, 2, Type.F32)
                "mat3x3f" -> Type.Matrix(3, 3, Type.F32)
                "mat3x4f" -> Type.Matrix(3, 4, Type.F32)
                "mat4x2f" -> Type.Matrix(4, 2, Type.F32)
                "mat4x3f" -> Type.Matrix(4, 3, Type.F32)
                "mat4x4f" -> Type.Matrix(4, 4, Type.F32)
                "mat2x2h" -> Type.Matrix(2, 2, Type.F16)
                "mat2x3h" -> Type.Matrix(2, 3, Type.F16)
                "mat2x4h" -> Type.Matrix(2, 4, Type.F16)
                "mat3x2h" -> Type.Matrix(3, 2, Type.F16)
                "mat3x3h" -> Type.Matrix(3, 3, Type.F16)
                "mat3x4h" -> Type.Matrix(3, 4, Type.F16)
                "mat4x2h" -> Type.Matrix(4, 2, Type.F16)
                "mat4x3h" -> Type.Matrix(4, 3, Type.F16)
                "mat4x4h" -> Type.Matrix(4, 4, Type.F16)
                // Possible issue with these functions when called in its (vector, vector, scalar) form:
                // the first two vectors might be abstract and the scalar concrete. In this case a concrete type should
                // be returned (and won't be here).
                "mix", "refract" -> findCommonType(functionCallExpression.args.dropLast(1), resolverState)
                "modf" -> {
                    if (functionCallExpression.args.size != 1) {
                        throw RuntimeException("modf requires one argument.")
                    }
                    when (val argType = resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0])) {
                        Type.F16 -> ModfResultF16
                        Type.F32 -> ModfResultF32
                        Type.AbstractFloat -> ModfResultAbstract
                        is Type.Vector -> {
                            when (argType.elementType) {
                                Type.F16 -> {
                                    when (argType.width) {
                                        2 -> ModfResultVec2F16
                                        3 -> ModfResultVec3F16
                                        4 -> ModfResultVec4F16
                                        else -> throw RuntimeException("Bad vector size.")
                                    }
                                }
                                Type.F32 -> {
                                    when (argType.width) {
                                        2 -> ModfResultVec2F32
                                        3 -> ModfResultVec3F32
                                        4 -> ModfResultVec4F32
                                        else -> throw RuntimeException("Bad vector size.")
                                    }
                                }
                                Type.AbstractFloat -> {
                                    when (argType.width) {
                                        2 -> ModfResultVec2Abstract
                                        3 -> ModfResultVec3Abstract
                                        4 -> ModfResultVec4Abstract
                                        else -> throw RuntimeException("Bad vector size.")
                                    }
                                }
                                else -> throw RuntimeException("Unexpected vector element type of modf vector argument.")
                            }
                        }
                        else -> throw RuntimeException("Unexpected type of modf argument.")
                    }
                }
                "pack4x8snorm", "pack4x8unorm", "pack4xI8", "pack4xU8", "pack4xI8Clamp", "pack4xU8Clamp",
                "pack2x16snorm", "pack2x16unorm", "pack2x16float",
                -> Type.U32
                "select" -> {
                    if (functionCallExpression.args.size != 3) {
                        throw RuntimeException("select requires three arguments.")
                    } else {
                        findCommonType(functionCallExpression.args.dropLast(1), resolverState)
                    }
                }
                "textureDimensions" -> {
                    if (functionCallExpression.args.size !in 1..2) {
                        throw RuntimeException("textureDimensions requires two arguments")
                    }
                    val textureType = resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0])
                    if (textureType !is Type.Texture) {
                        throw RuntimeException("Type of first argument to textureDimensions must be a texture")
                    }
                    if (functionCallExpression.args.size == 1) {
                        when (textureType) {
                            Type.Texture.Depth2D -> Type.Vector(2, Type.U32)
                            Type.Texture.Depth2DArray -> Type.Vector(2, Type.U32)
                            Type.Texture.DepthCube -> Type.Vector(2, Type.U32)
                            Type.Texture.DepthCubeArray -> Type.Vector(2, Type.U32)
                            Type.Texture.DepthMultisampled2D -> Type.Vector(2, Type.U32)
                            Type.Texture.External -> Type.Vector(2, Type.U32)
                            is Type.Texture.Multisampled2d -> Type.Vector(2, Type.U32)
                            is Type.Texture.Sampled1D -> Type.U32
                            is Type.Texture.Sampled2D -> Type.Vector(2, Type.U32)
                            is Type.Texture.Sampled2DArray -> Type.Vector(2, Type.U32)
                            is Type.Texture.Sampled3D -> Type.Vector(3, Type.U32)
                            is Type.Texture.SampledCube -> Type.Vector(2, Type.U32)
                            is Type.Texture.SampledCubeArray -> Type.Vector(2, Type.U32)
                            is Type.Texture.Storage1D -> Type.U32
                            is Type.Texture.Storage2D -> Type.Vector(2, Type.U32)
                            is Type.Texture.Storage2DArray -> Type.Vector(2, Type.U32)
                            is Type.Texture.Storage3D -> Type.Vector(2, Type.U32)
                        }
                    } else {
                        assert(functionCallExpression.args.size == 2)
                        when (textureType) {
                            Type.Texture.Depth2D -> Type.Vector(2, Type.U32)
                            Type.Texture.Depth2DArray -> Type.Vector(2, Type.U32)
                            Type.Texture.DepthCube -> Type.Vector(2, Type.U32)
                            Type.Texture.DepthCubeArray -> Type.Vector(2, Type.U32)
                            is Type.Texture.Sampled1D -> Type.U32
                            is Type.Texture.Sampled2D -> Type.Vector(2, Type.U32)
                            is Type.Texture.Sampled2DArray -> Type.Vector(2, Type.U32)
                            is Type.Texture.Sampled3D -> Type.Vector(3, Type.U32)
                            is Type.Texture.SampledCube -> Type.Vector(2, Type.U32)
                            is Type.Texture.SampledCubeArray -> Type.Vector(2, Type.U32)
                            else -> throw RuntimeException("Unsuitable texture argument for textureDimensions with level")
                        }
                    }
                }
                "textureGather" -> {
                    if (functionCallExpression.args.size < 2) {
                        throw RuntimeException("$calleeName requires at least 2 arguments")
                    }
                    when (resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0])) {
                        Type.Texture.Depth2D, Type.Texture.DepthCube, Type.Texture.Depth2DArray, Type.Texture.DepthCubeArray ->
                            Type.Vector(
                                4,
                                Type.F32,
                            )
                        else -> {
                            when (val arg2Type = resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[1])) {
                                is Type.Texture.Sampled -> Type.Vector(4, arg2Type.sampledType)
                                else -> throw RuntimeException("$calleeName requires a suitable texture as its first or second argument")
                            }
                        }
                    }
                }
                "textureGatherCompare" -> Type.Vector(4, Type.F32)
                "textureLoad" -> {
                    if (functionCallExpression.args.size < 1) {
                        throw RuntimeException("textureLoad requires a first argument of texture type")
                    }
                    val textureArg = resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0])
                    if (textureArg !is Type.Texture) {
                        throw RuntimeException("textureLoad requires a first argument of texture type")
                    }
                    when (textureArg) {
                        Type.Texture.Depth2D, Type.Texture.Depth2DArray, Type.Texture.DepthMultisampled2D -> Type.F32
                        Type.Texture.External -> Type.Vector(4, Type.F32)
                        is Type.Texture.Multisampled2d -> Type.Vector(4, textureArg.sampledType)
                        is Type.Texture.Sampled1D -> Type.Vector(4, textureArg.sampledType)
                        is Type.Texture.Sampled2D -> Type.Vector(4, textureArg.sampledType)
                        is Type.Texture.Sampled2DArray -> Type.Vector(4, textureArg.sampledType)
                        is Type.Texture.Sampled3D -> Type.Vector(4, textureArg.sampledType)
                        is Type.Texture.Storage1D -> Type.Vector(4, textureArg.format.toVectorElementType())
                        is Type.Texture.Storage2D -> Type.Vector(4, textureArg.format.toVectorElementType())
                        is Type.Texture.Storage2DArray -> Type.Vector(4, textureArg.format.toVectorElementType())
                        is Type.Texture.Storage3D -> Type.Vector(4, textureArg.format.toVectorElementType())
                        else -> throw RuntimeException("textureLoad does not work on cube textures")
                    }
                }
                "textureNumLayers", "textureNumLevels", "textureNumSamples" -> Type.U32
                "textureSample", "textureSampleLevel" -> {
                    if (functionCallExpression.args.size < 1) {
                        throw RuntimeException("Not enough arguments provided to $calleeName.")
                    } else {
                        when (
                            val textureType =
                                resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0])
                        ) {
                            is Type.Texture.Sampled ->
                                if (textureType.sampledType is Type.F32) {
                                    Type.Vector(4, Type.F32)
                                } else {
                                    throw RuntimeException("Incorrect sample type used with $calleeName.")
                                }
                            Type.Texture.Depth2D, Type.Texture.Depth2DArray, Type.Texture.DepthCube, Type.Texture.DepthCubeArray -> Type.F32
                            else -> throw RuntimeException("First argument to $calleeName must be a suitable texture.")
                        }
                    }
                }
                "textureSampleBaseClampToEdge", "textureSampleBias" -> Type.Vector(4, Type.F32)
                "textureSampleCompare", "textureSampleCompareLevel" -> Type.F32
                "textureSampleGrad" -> Type.Vector(4, Type.F32)
                "transpose" -> {
                    if (functionCallExpression.args.size != 1) {
                        throw RuntimeException("$calleeName requires one argument")
                    }
                    val arg1Type = resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0])
                    if (arg1Type is Type.Matrix) {
                        Type.Matrix(numCols = arg1Type.numRows, numRows = arg1Type.numCols, elementType = arg1Type.elementType)
                    } else {
                        throw RuntimeException("$calleeName requires a matrix argument")
                    }
                }
                "unpack4x8snorm", "unpack4x8unorm", "unpack2x16snorm", "unpack2x16unorm", "unpack2x16float" -> Type.Vector(4, Type.F32)
                "unpack4xI8" -> Type.Vector(4, Type.I32)
                "unpack4xU8" -> Type.Vector(4, Type.U32)
                "vec2i" -> Type.Vector(2, Type.I32)
                "vec3i" -> Type.Vector(3, Type.I32)
                "vec4i" -> Type.Vector(4, Type.I32)
                "vec2u" -> Type.Vector(2, Type.U32)
                "vec3u" -> Type.Vector(3, Type.U32)
                "vec4u" -> Type.Vector(4, Type.U32)
                "vec2f" -> Type.Vector(2, Type.F32)
                "vec3f" -> Type.Vector(3, Type.F32)
                "vec4f" -> Type.Vector(4, Type.F32)
                "vec2h" -> Type.Vector(2, Type.F16)
                "vec3h" -> Type.Vector(3, Type.F16)
                "vec4h" -> Type.Vector(4, Type.F16)
                "workgroupUniformLoad" -> {
                    if (functionCallExpression.args.size != 1) {
                        throw RuntimeException("workgroupUniformLoad requires one argument.")
                    }
                    val argType = resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0])
                    if (argType !is Type.Pointer) {
                        throw RuntimeException("workgroupUniformLoad requires a pointer argument.")
                    }
                    argType.pointeeType
                }
                else -> TODO("Unsupported builtin function $calleeName")
            }
        is ScopeEntry.Function ->
            scopeEntry.type.returnType
                ?: throw RuntimeException(
                    "Call expression used with function ${functionCallExpression.callee}, which does not return a value.",
                )
        else -> throw RuntimeException("Function call attempted on unknown callee ${functionCallExpression.callee}")
    }

private fun resolveTypeOfAddressOfExpression(
    expression: Expression.Unary,
    resolverState: ResolverState,
): Type {
    var target = expression.target
    while (target !is Expression.Identifier) {
        when (target) {
            is Expression.Paren -> target = target.target
            is Expression.MemberLookup -> target = target.receiver
            is Expression.Unary -> {
                when (target.operator) {
                    UnaryOperator.ADDRESS_OF, UnaryOperator.DEREFERENCE -> target = target.target
                    else -> throw RuntimeException("Unsupported use of unary operator ${target.operator} under address-of")
                }
            }
            is Expression.IndexLookup -> target = target.target
            else -> throw RuntimeException("Unsupported expression $target under address-of")
        }
    }
    val targetType = resolverState.resolvedEnvironment.typeOf(target)
    val pointeeType = resolverState.resolvedEnvironment.typeOf(expression.target)
    return if (targetType is Type.Pointer) {
        Type.Pointer(
            pointeeType = pointeeType,
            addressSpace = targetType.addressSpace,
            accessMode = targetType.accessMode,
        )
    } else {
        when (val scopeEntry = resolverState.currentScope.getEntry(target.name)) {
            is ScopeEntry.GlobalVariable ->
                Type.Pointer(
                    pointeeType = pointeeType,
                    // The spec seems to indicate that "handle" is the default for module-level variable declarations
                    // for which no address space is specified.
                    addressSpace = scopeEntry.astNode.addressSpace ?: AddressSpace.HANDLE,
                    accessMode = scopeEntry.astNode.accessMode ?: AccessMode.READ_WRITE,
                )
            is ScopeEntry.LocalVariable ->
                Type.Pointer(
                    pointeeType = pointeeType,
                    addressSpace = scopeEntry.astNode.addressSpace ?: AddressSpace.FUNCTION,
                    accessMode = scopeEntry.astNode.accessMode ?: AccessMode.READ_WRITE,
                )
            else -> TODO("Unsupported target for address-of - scope entry is $scopeEntry")
        }
    }
}

private fun resolveFloatTypeDecl(floatTypeDecl: TypeDecl.FloatTypeDecl): Type.Float =
    when (floatTypeDecl) {
        is TypeDecl.F16 -> Type.F16
        is TypeDecl.F32 -> Type.F32
    }

private fun resolveScalarTypeDecl(scalarTypeDecl: TypeDecl.ScalarTypeDecl): Type.Scalar =
    when (scalarTypeDecl) {
        is TypeDecl.FloatTypeDecl -> resolveFloatTypeDecl(scalarTypeDecl)
        is TypeDecl.Bool -> Type.Bool
        is TypeDecl.I32 -> Type.I32
        is TypeDecl.U32 -> Type.U32
    }

private fun resolveVectorTypeDecl(vectorTypeDecl: TypeDecl.VectorTypeDecl): Type.Vector {
    val elementType = resolveScalarTypeDecl(vectorTypeDecl.elementType)
    return when (vectorTypeDecl) {
        is TypeDecl.Vec2 -> Type.Vector(2, elementType)
        is TypeDecl.Vec3 -> Type.Vector(3, elementType)
        is TypeDecl.Vec4 -> Type.Vector(4, elementType)
    }
}

private fun resolveMatrixTypeDecl(matrixTypeDecl: TypeDecl.MatrixTypeDecl): Type.Matrix {
    val elementType = resolveFloatTypeDecl(matrixTypeDecl.elementType)
    return when (matrixTypeDecl) {
        is TypeDecl.Mat2x2 -> {
            Type.Matrix(2, 2, elementType)
        }

        is TypeDecl.Mat2x3 -> {
            Type.Matrix(2, 3, elementType)
        }

        is TypeDecl.Mat2x4 -> {
            Type.Matrix(2, 4, elementType)
        }

        is TypeDecl.Mat3x2 -> {
            Type.Matrix(3, 2, elementType)
        }

        is TypeDecl.Mat3x3 -> {
            Type.Matrix(3, 3, elementType)
        }

        is TypeDecl.Mat3x4 -> {
            Type.Matrix(3, 4, elementType)
        }

        is TypeDecl.Mat4x2 -> {
            Type.Matrix(4, 2, elementType)
        }

        is TypeDecl.Mat4x3 -> {
            Type.Matrix(4, 3, elementType)
        }

        is TypeDecl.Mat4x4 -> {
            Type.Matrix(4, 4, elementType)
        }
    }
}

private fun TexelFormat.toVectorElementType(): Type.Scalar =
    when (this) {
        TexelFormat.RGBA8UNORM -> Type.F32
        TexelFormat.RGBA8SNORM -> Type.F32
        TexelFormat.RGBA8UINT -> Type.U32
        TexelFormat.RGBA8SINT -> Type.I32
        TexelFormat.RGBA16UINT -> Type.U32
        TexelFormat.RGBA16SINT -> Type.I32
        TexelFormat.RGBA16FLOAT -> Type.F32
        TexelFormat.R32UINT -> Type.U32
        TexelFormat.R32SINT -> Type.I32
        TexelFormat.R32FLOAT -> Type.F32
        TexelFormat.RG32UINT -> Type.U32
        TexelFormat.RG32SINT -> Type.I32
        TexelFormat.RG32FLOAT -> Type.F32
        TexelFormat.RGBA32UINT -> Type.U32
        TexelFormat.RGBA32SINT -> Type.I32
        TexelFormat.RGBA32FLOAT -> Type.F32
        TexelFormat.BGRA8UNORM -> Type.F32
    }

private fun Type.isAbstractionOf(maybeConcretizedVersion: Type): Boolean =
    if (this == maybeConcretizedVersion) {
        true
    } else {
        when (this) {
            Type.AbstractFloat -> maybeConcretizedVersion in setOf(Type.F16, Type.F32)
            Type.AbstractInteger ->
                maybeConcretizedVersion in
                    setOf(
                        Type.I32,
                        Type.U32,
                        Type.AbstractFloat,
                        Type.F32,
                        Type.F16,
                    )

            is Type.Vector ->
                maybeConcretizedVersion is Type.Vector &&
                    width == maybeConcretizedVersion.width &&
                    elementType.isAbstractionOf(maybeConcretizedVersion.elementType)

            is Type.Matrix ->
                maybeConcretizedVersion is Type.Matrix &&
                    numRows == maybeConcretizedVersion.numRows &&
                    numCols == maybeConcretizedVersion.numCols &&
                    elementType.isAbstractionOf(maybeConcretizedVersion.elementType)

            is Type.Array ->
                maybeConcretizedVersion is Type.Array &&
                    elementCount == maybeConcretizedVersion.elementCount &&
                    elementType.isAbstractionOf(maybeConcretizedVersion.elementType)

            else -> false
        }
    }

private fun defaultConcretizationOf(type: Type): Type =
    when (type) {
        is Type.AbstractInteger -> Type.I32
        is Type.AbstractFloat -> Type.F32
        is Type.Vector -> Type.Vector(type.width, defaultConcretizationOf(type.elementType) as Type.Scalar)
        is Type.Matrix -> Type.Matrix(type.numCols, type.numRows, defaultConcretizationOf(type.elementType) as Type.Float)
        is Type.Array ->
            Type.Array(
                elementType = defaultConcretizationOf(type.elementType),
                elementCount = type.elementCount,
            )
        else -> type
    }

private fun evaluateToInt(
    expression: Expression,
    resolverState: ResolverState,
): Int =
    when (expression) {
        is Expression.IntLiteral ->
            if (expression.text.endsWith("u") || expression.text.endsWith("i")) {
                expression.text.substring(0, expression.text.length - 1).toInt()
            } else {
                expression.text.toInt()
            }
        else -> throw RuntimeException("Cannot evaluate $expression")
    }

private fun isSwizzle(memberName: String): Boolean =
    memberName.length in (2..4) &&
        memberName.all { it in setOf('x', 'y', 'z', 'w') }

// Throws an exception if the types in the given list do not share a common concretization.
// Otherwise, returns the common concretization, unless all the types are the same abstract type in which case that type
// is returned.
private fun findCommonType(types: List<Type>): Type {
    var result = types[0]
    for (type in types.drop(1)) {
        if (result != type) {
            if (result.isAbstractionOf(type)) {
                result = type
            } else if (!type.isAbstractionOf(result)) {
                throw RuntimeException("No common type found.")
            }
        }
    }
    return result
}

private fun findCommonType(
    expressions: List<Expression>,
    resolverState: ResolverState,
): Type =
    findCommonType(
        expressions.map {
            resolverState.resolvedEnvironment.typeOf(it)
        },
    )

private fun resolveTypeDecl(
    typeDecl: TypeDecl,
    resolverState: ResolverState,
): Type =
    when (typeDecl) {
        is TypeDecl.Array ->
            Type.Array(
                elementType = resolveTypeDecl(typeDecl.elementType, resolverState),
                elementCount =
                    typeDecl.elementCount?.let {
                        evaluateToInt(it, resolverState)
                    },
            )
        is TypeDecl.NamedType -> {
            when (val scopeEntry = resolverState.currentScope.getEntry(typeDecl.name)) {
                is ScopeEntry.TypeAlias -> {
                    scopeEntry.type
                }
                is ScopeEntry.Struct -> {
                    scopeEntry.type
                }
                null -> {
                    when (typeDecl.name) {
                        "vec2f" -> Type.Vector(3, Type.F32)
                        "vec3f" -> Type.Vector(3, Type.F32)
                        "vec4f" -> Type.Vector(3, Type.F32)
                        "vec2i" -> Type.Vector(3, Type.I32)
                        "vec3i" -> Type.Vector(3, Type.I32)
                        "vec4i" -> Type.Vector(3, Type.I32)
                        "vec2u" -> Type.Vector(3, Type.U32)
                        "vec3u" -> Type.Vector(3, Type.U32)
                        "vec4u" -> Type.Vector(3, Type.U32)
                        else -> TODO("Unknown typed declaration: ${typeDecl.name}")
                    }
                }
                else -> {
                    throw RuntimeException("Non-type declaration associated with ${typeDecl.name}, which is used where a type is required.")
                }
            }
        }
        is TypeDecl.Pointer ->
            Type.Pointer(
                pointeeType =
                    resolveTypeDecl(
                        typeDecl.pointeeType,
                        resolverState,
                    ),
                addressSpace = typeDecl.addressSpace,
                accessMode = typeDecl.accessMode ?: AccessMode.READ_WRITE,
            )
        is TypeDecl.ScalarTypeDecl -> resolveScalarTypeDecl(typeDecl)
        is TypeDecl.VectorTypeDecl -> resolveVectorTypeDecl(typeDecl)
        is TypeDecl.MatrixTypeDecl -> resolveMatrixTypeDecl(typeDecl)
        is TypeDecl.Atomic -> {
            when (resolveTypeDecl(typeDecl.targetType, resolverState)) {
                Type.I32 -> Type.AtomicI32
                Type.U32 -> Type.AtomicU32
                else -> throw RuntimeException("Inappropriate target type for atomic type.")
            }
        }
        is TypeDecl.SamplerComparison -> Type.SamplerComparison
        is TypeDecl.SamplerRegular -> Type.SamplerRegular
        is TypeDecl.TextureDepth2D -> Type.Texture.Depth2D
        is TypeDecl.TextureDepth2DArray -> Type.Texture.Depth2DArray
        is TypeDecl.TextureDepthCube -> Type.Texture.DepthCube
        is TypeDecl.TextureDepthCubeArray -> Type.Texture.DepthCubeArray
        is TypeDecl.TextureDepthMultisampled2D -> Type.Texture.DepthMultisampled2D
        is TypeDecl.TextureExternal -> Type.Texture.External
        is TypeDecl.TextureMultisampled2d -> {
            when (val sampledType = resolveTypeDecl(typeDecl.sampledType, resolverState)) {
                is Type.Scalar -> Type.Texture.Multisampled2d(sampledType)
                else -> throw RuntimeException("texture_multisampled_2d requires a scalar sampler type.")
            }
        }
        is TypeDecl.TextureSampled1D -> {
            when (val sampledType = resolveTypeDecl(typeDecl.sampledType, resolverState)) {
                is Type.Scalar -> Type.Texture.Sampled1D(sampledType)
                else -> throw RuntimeException("texture_1d requires a scalar sampler type.")
            }
        }
        is TypeDecl.TextureSampled2D -> {
            when (val sampledType = resolveTypeDecl(typeDecl.sampledType, resolverState)) {
                is Type.Scalar -> Type.Texture.Sampled2D(sampledType)
                else -> throw RuntimeException("texture_2d requires a scalar sampler type.")
            }
        }
        is TypeDecl.TextureSampled2DArray -> {
            when (val sampledType = resolveTypeDecl(typeDecl.sampledType, resolverState)) {
                is Type.Scalar -> Type.Texture.Sampled2DArray(sampledType)
                else -> throw RuntimeException("texture_2d_array requires a scalar sampler type.")
            }
        }
        is TypeDecl.TextureSampled3D -> {
            when (val sampledType = resolveTypeDecl(typeDecl.sampledType, resolverState)) {
                is Type.Scalar -> Type.Texture.Sampled3D(sampledType)
                else -> throw RuntimeException("texture_3d requires a scalar sampler type.")
            }
        }
        is TypeDecl.TextureSampledCube -> {
            when (val sampledType = resolveTypeDecl(typeDecl.sampledType, resolverState)) {
                is Type.Scalar -> Type.Texture.SampledCube(sampledType)
                else -> throw RuntimeException("texture_cube requires a scalar sampler type.")
            }
        }
        is TypeDecl.TextureSampledCubeArray -> {
            when (val sampledType = resolveTypeDecl(typeDecl.sampledType, resolverState)) {
                is Type.Scalar -> Type.Texture.SampledCubeArray(sampledType)
                else -> throw RuntimeException("texture_cube_array requires a scalar sampler type.")
            }
        }
        is TypeDecl.TextureStorage1D -> Type.Texture.Storage1D(typeDecl.format, typeDecl.accessMode)
        is TypeDecl.TextureStorage2D -> Type.Texture.Storage2D(typeDecl.format, typeDecl.accessMode)
        is TypeDecl.TextureStorage2DArray -> Type.Texture.Storage2DArray(typeDecl.format, typeDecl.accessMode)
        is TypeDecl.TextureStorage3D -> Type.Texture.Storage3D(typeDecl.format, typeDecl.accessMode)
    }

private fun resolveFunctionHeader(
    functionDecl: GlobalDecl.Function,
    resolverState: ResolverState,
) {
    val functionType =
        FunctionType(
            functionDecl.parameters.map {
                resolveTypeDecl(it.typeDecl, resolverState)
            },
            functionDecl.returnType?.let {
                resolveTypeDecl(it, resolverState)
            },
        )
    resolverState.currentScope.addEntry(
        functionDecl.name,
        ScopeEntry.Function(
            astNode = functionDecl,
            type = functionType,
        ),
    )
}

private fun resolveFunctionBody(
    functionDecl: GlobalDecl.Function,
    resolverState: ResolverState,
) {
    val functionScopeEntry = resolverState.currentScope.getEntry(functionDecl.name) as ScopeEntry.Function
    assert(functionScopeEntry.astNode == functionDecl)
    resolverState.withScope(functionDecl) {
        functionDecl.parameters.forEachIndexed { index, parameterDecl ->
            resolverState.currentScope.addEntry(
                parameterDecl.name,
                ScopeEntry.Parameter(
                    astNode = parameterDecl,
                    type = functionScopeEntry.type.argTypes[index],
                ),
            )
        }
        functionDecl.body.forEach {
            resolveAstNode(it, resolverState)
        }
    }
}

fun resolve(tu: TranslationUnit): ResolvedEnvironment {
    val (topLevelNameDependencies, nameToDecl) = collectTopLevelNameDependencies(tu)
    val orderedGlobalDecls = orderGlobalDeclNames(topLevelNameDependencies)

    val globalScope: ScopeImpl =
        ScopeImpl(
            parent = null,
            enclosingAstNode = tu,
        )

    val resolverState =
        ResolverState(
            ResolvedEnvironmentImpl(),
            globalScope,
        )

    // Resolve name-introducing global decls in order, then other global decls
    for (name in orderedGlobalDecls) {
        val astNode = nameToDecl[name]!!
        if (astNode is GlobalDecl.Function) {
            resolveFunctionHeader(astNode, resolverState)
        } else {
            resolveAstNode(astNode, resolverState)
        }
    }
    for (decl in tu.globalDecls) {
        when (decl) {
            is GlobalDecl.ConstAssert -> resolveAstNode(decl.expression, resolverState)
            is GlobalDecl.Function -> resolveFunctionBody(decl, resolverState)
            else -> {
                // A name-introducing declaration: already resolved
            }
        }
    }
    return resolverState.resolvedEnvironment
}
