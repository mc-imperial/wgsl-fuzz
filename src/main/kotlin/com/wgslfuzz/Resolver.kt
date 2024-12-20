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
        entries[name] = node
    }

    override fun getEntry(name: String): ScopeEntry? = entries[name] ?: parent?.getEntry(name)
}

sealed interface ResolvedEnvironment {
    fun typeOf(expression: Expression): Type

    fun typeOf(functionDecl: GlobalDecl.Function): FunctionType
}

private class ResolvedEnvironmentImpl : ResolvedEnvironment {
    private val expressionTypes: MutableMap<Expression, Type> = mutableMapOf()

    fun recordType(
        expression: Expression,
        type: Type,
    ) {
        assert(expression !in expressionTypes.keys)
        expressionTypes[expression] = type
    }

    override fun typeOf(expression: Expression): Type =
        expressionTypes[expression] ?: throw UnsupportedOperationException("No type for $expression")

    override fun typeOf(functionDecl: GlobalDecl.Function): FunctionType {
        TODO("Not yet implemented")
    }
}

private fun collectUsedModuleScopeNames(node: AstNode): Set<String> {
    fun collectAction(
        node: AstNode,
        collectedNames: MutableSet<String>,
    ) {
        traverse(::collectAction, node, collectedNames)
        when (node) {
            is Expression.Identifier -> collectedNames.add(node.name)
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
            GlobalDecl.Empty -> {}
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
    // TODO - consider cutting off traversal so that type declarations are not visited as these are handled separately
    traverse(::resolveAstNode, node, resolverState)
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
                node.type?.let {
                    resolveTypeDecl(node.type!!, resolverState)
                } ?: resolverState.resolvedEnvironment.typeOf(node.initializer!!)
            if (type.isAbstract()) {
                TODO()
            }
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
            val type: Type =
                node.type?.let {
                    resolveTypeDecl(node.type!!, resolverState)
                } ?: resolverState.resolvedEnvironment.typeOf(node.initializer)
            if (type.isAbstract()) {
                TODO()
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
                type = concretizeInitializerType(type)
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
                else -> throw RuntimeException("Index lookup attempted on unsuitable type.")
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
                else -> TODO()
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
        is Expression.Binary -> {
            val lhsType = resolverState.resolvedEnvironment.typeOf(expression.lhs)
            val rhsType = resolverState.resolvedEnvironment.typeOf(expression.rhs)
            when (expression.operator) {
                BinaryOperator.LESS_THAN,
                BinaryOperator.LESS_THAN_EQUAL,
                BinaryOperator.GREATER_THAN,
                BinaryOperator.GREATER_THAN_EQUAL,
                ->
                    when (lhsType) {
                        is Type.Scalar -> Type.Bool
                        else -> TODO()
                    }
                BinaryOperator.PLUS ->
                    if (lhsType == rhsType) {
                        lhsType
                    } else if (lhsType is Type.I32 && rhsType is Type.AbstractInteger) {
                        Type.I32
                    } else {
                        TODO()
                    }
                BinaryOperator.EQUAL_EQUAL ->
                    when (lhsType) {
                        is Type.Scalar -> Type.Bool
                        is Type.Vector -> TODO()
                        else -> throw RuntimeException("== operator is only supported for scalar and vector types.")
                    }
                BinaryOperator.SHORT_CIRCUIT_AND ->
                    if (lhsType != Type.Bool || rhsType != Type.Bool) {
                        throw RuntimeException("Short circuit && and || operators require bool arguments.")
                    } else {
                        Type.Bool
                    }
                else -> TODO("Not implemented support for ${expression.operator}")
            }
        }
//        is Expression.Unary -> {
//            when (node.operator) {
//                UnaryOperator.DEREFERENCE -> {
//                    val pointerType = resolverState.resolvedEnvironment.typeOf(node.target)
//                    if (pointerType !is Type.Pointer) {
//                        throw RuntimeException("Dereference applied to expression $node with non-pointer type")
//                    }
//                    resolverState.resolvedEnvironment.recordType(node, pointerType.pointeeType)
//                }
//
//                UnaryOperator.ADDRESS_OF -> {
//                    resolveAddressOfExpression(node, resolverState)
//                }
//
//                else -> {
//                    TODO("Not implemented support for ${node.operator}")
//                }
//            }
//        }
        is Expression.Paren -> resolverState.resolvedEnvironment.typeOf(expression.target)
        is Expression.Identifier ->
            when (val scopeEntry = resolverState.currentScope.getEntry(expression.name)) {
                is ScopeEntry.TypedDecl -> scopeEntry.type
                else -> throw RuntimeException("Identifier ${expression.name} does not have a typed scope entry.")
            }
        is Expression.BoolValueConstructor -> Type.Bool
        is Expression.I32ValueConstructor -> Type.I32
        is Expression.U32ValueConstructor -> Type.U32
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
        else -> TODO("Unsupported expression $expression")
    }

private fun resolveTypeOfVectorValueConstructor(
    expression: Expression.VectorValueConstructor,
    resolverState: ResolverState,
): Type.Vector {
    val elementType: Type.Scalar =
        if (expression.elementType != null) {
            resolveTypeDecl(expression.elementType!!, resolverState) as Type.Scalar
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
    node: Expression.MatrixValueConstructor,
    resolverState: ResolverState,
): Type.Matrix = TODO()

private fun resolveTypeOfArrayValueConstructor(
    expression: Expression.ArrayValueConstructor,
    resolverState: ResolverState,
): Type.Array {
    val elementType: Type =
        expression.elementType?.let { resolveTypeDecl(it, resolverState) } ?: run {
            if (expression.args.isEmpty()) {
                throw RuntimeException("Cannot work out element type of empty array constructor.")
            }
            var candidateType = resolverState.resolvedEnvironment.typeOf(expression.args[0])
            for (i in (1..<expression.args.size)) {
                val argType = resolverState.resolvedEnvironment.typeOf(expression.args[i])
                if (candidateType != argType) {
                    if (candidateType.isAbstractionOf(argType)) {
                        candidateType = argType
                    } else {
                        throw RuntimeException("Inconsistently-typed arguments to array constructor.")
                    }
                }
            }
            candidateType
        }
    val elementCount: Int =
        expression.elementCount?.let {
            evaluateToInt(expression.elementCount!!, resolverState)
                ?: throw RuntimeException(
                    "An element count is given but cannot be evaluated to an integer value. It may be that the integer evaluator needs to be improved.",
                )
        } ?: expression.args.size
    return Type.Array(elementType, elementCount)
}

private fun resolveTypeOfFunctionCallExpression(
    functionCallExpression: Expression.FunctionCall,
    resolverState: ResolverState,
): Type =
    when (val scopeEntry = resolverState.currentScope.getEntry(functionCallExpression.callee)) {
        null ->
            when (functionCallExpression.callee) {
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
                "dpdx" -> {
                    if (functionCallExpression.args.size != 1) {
                        throw RuntimeException("dpdx requires one argument.")
                    } else {
                        resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0])
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
                "textureSample" -> {
                    if (functionCallExpression.args.size < 1) {
                        throw RuntimeException("Not enough arguments provided to textureSample.")
                    } else {
                        when (
                            val textureType =
                                resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0])
                        ) {
                            is Type.Texture.Sampled ->
                                if (textureType.sampledType is Type.F32) {
                                    Type.Vector(4, Type.F32)
                                } else {
                                    throw RuntimeException("Incorrect sample type used with textureSample.")
                                }

                            Type.Texture.Depth2D, Type.Texture.Depth2DArray, Type.Texture.DepthCube, Type.Texture.DepthCubeArray -> Type.F32
                            else -> throw RuntimeException("First argument to textureSample must be a suitable texture.")
                        }
                    }
                }
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
                else -> TODO("Unsupported builtin function ${functionCallExpression.callee}")
            }
        is ScopeEntry.Function ->
            scopeEntry.type.returnType
                ?: throw RuntimeException(
                    "Call expression used with function ${functionCallExpression.callee}, which does not return a value.",
                )
        else -> throw RuntimeException("Function call attempted on unknown callee ${functionCallExpression.callee}")
    }

private fun resolveAddressOfExpression(
    node: Expression.Unary,
    resolverState: ResolverState,
) {
    var target = node.target
    while (target !is Expression.Identifier) {
        when (target) {
            is Expression.Paren -> target = target.target
            is Expression.MemberLookup -> target = target.receiver
            is Expression.Unary -> {
                if (target.operator == UnaryOperator.DEREFERENCE) {
                    target = target.target
                } else {
                    throw RuntimeException("Unsupported use of unary operator ${target.operator} under address-of")
                }
            }
            else -> throw RuntimeException("Unsupported expression $target under address-of")
        }
    }
    val targetType = resolverState.resolvedEnvironment.typeOf(target)
    val pointeeType = resolverState.resolvedEnvironment.typeOf(node.target)
    if (targetType is Type.Pointer) {
        resolverState.resolvedEnvironment.recordType(
            node,
            Type.Pointer(
                pointeeType = pointeeType,
                addressSpace = targetType.addressSpace,
                accessMode = targetType.accessMode,
            ),
        )
    } else {
        when (val scopeEntry = resolverState.currentScope.getEntry(target.name)) {
            is ScopeEntry.GlobalVariable -> {
                resolverState.resolvedEnvironment.recordType(
                    node,
                    Type.Pointer(
                        pointeeType = pointeeType,
                        // The spec seems to indicate that "handle" is the default for module-level variable declarations
                        // for which no address space is specified.
                        addressSpace = scopeEntry.astNode.addressSpace ?: AddressSpace.HANDLE,
                        accessMode = scopeEntry.astNode.accessMode ?: AccessMode.READ_WRITE,
                    ),
                )
            }
            is ScopeEntry.LocalVariable -> {
                resolverState.resolvedEnvironment.recordType(
                    node,
                    Type.Pointer(
                        pointeeType = pointeeType,
                        addressSpace = scopeEntry.astNode.addressSpace ?: AddressSpace.FUNCTION,
                        accessMode = scopeEntry.astNode.accessMode ?: AccessMode.READ_WRITE,
                    ),
                )
            }
            else -> TODO("Unsupported target for address-of - scope entry is $scopeEntry")
        }
    }
}

private fun resolveFloatTypeDecl(floatTypeDecl: TypeDecl.FloatTypeDecl): Type.Float =
    when (floatTypeDecl) {
        TypeDecl.F16 -> Type.F16
        TypeDecl.F32 -> Type.F32
    }

private fun resolveScalarTypeDecl(scalarTypeDecl: TypeDecl.ScalarTypeDecl): Type.Scalar =
    when (scalarTypeDecl) {
        is TypeDecl.FloatTypeDecl -> resolveFloatTypeDecl(scalarTypeDecl)
        TypeDecl.Bool -> Type.Bool
        TypeDecl.I32 -> Type.I32
        TypeDecl.U32 -> Type.U32
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

private fun concretizeInitializerType(type: Type): Type =
    when (type) {
        is Type.AbstractInteger -> Type.I32
        is Type.AbstractFloat -> Type.F32
        is Type.Vector -> Type.Vector(type.width, concretizeInitializerType(type.elementType) as Type.Scalar)
        is Type.Matrix -> Type.Matrix(type.numCols, type.numRows, concretizeInitializerType(type.elementType) as Type.Float)
        is Type.Array ->
            Type.Array(
                elementType = concretizeInitializerType(type.elementType),
                elementCount = type.elementCount,
            )
        else -> {
            assert(!type.isAbstract())
            throw RuntimeException("Attempt to concretize non-abstract type.")
        }
    }

private fun evaluateToInt(
    expression: Expression,
    resolverState: ResolverState,
): Int? {
    // TODO: resolverState is not currently used, but for more sophisticated evaluations it will be needed.
    if (expression is Expression.IntLiteral) {
        if (expression.text.endsWith("u") || expression.text.endsWith("i")) {
            return expression.text.substring(0, expression.text.length - 1).toInt()
        }
        return expression.text.toInt()
    }
    return null
}

private fun isSwizzle(memberName: String): Boolean =
    memberName.length in (2..4) &&
        memberName.all { it in setOf('x', 'y', 'z', 'w') }

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
                        "atomic" -> {
                            if (typeDecl.templateArgs.size != 1) {
                                throw RuntimeException("Atomic type must have exactly one template argument")
                            }
                            when (resolveTypeDecl(typeDecl.templateArgs[0], resolverState)) {
                                Type.I32 -> Type.AtomicI32
                                Type.U32 -> Type.AtomicU32
                                else -> throw RuntimeException("Argument to atomic type must be u32 or i32")
                            }
                        }
                        "sampler" -> Type.SamplerRegular
                        "sampler_comparison" -> Type.SamplerComparison
                        "texture_1d" -> Type.Texture.Sampled1D(getSampledType(typeDecl, resolverState))
                        "texture_2d" -> Type.Texture.Sampled2D(getSampledType(typeDecl, resolverState))
                        "texture_2d_array" -> Type.Texture.Sampled2DArray(getSampledType(typeDecl, resolverState))
                        "texture_3d" -> Type.Texture.Sampled3D(getSampledType(typeDecl, resolverState))
                        "texture_cube" -> Type.Texture.SampledCube(getSampledType(typeDecl, resolverState))
                        "texture_cube_array" -> Type.Texture.SampledCubeArray(getSampledType(typeDecl, resolverState))
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
        traverse(::resolveAstNode, functionDecl.body, resolverState)
    }
}

private fun getSampledType(
    typeDecl: TypeDecl.NamedType,
    resolverState: ResolverState,
): Type.Scalar =
    if (typeDecl.templateArgs.size == 1) {
        when (val sampledType = resolveTypeDecl(typeDecl.templateArgs[0], resolverState)) {
            Type.I32, Type.U32, Type.F32 -> sampledType as Type.Scalar
            else -> throw RuntimeException("Sampled type must be f32, i32 or u32.")
        }
    } else {
        throw RuntimeException("Texture type requires a sampled type argument.")
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
