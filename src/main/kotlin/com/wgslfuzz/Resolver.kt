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
            .reduce(Set<String>::plus)
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
        is GlobalDecl.Struct -> {
            resolverState.currentScope.addEntry(
                node.name,
                ScopeEntry.Struct(
                    astNode = node,
                    type =
                        Type.Struct(
                            name = node.name,
                            members =
                                node.members
                                    .map {
                                        it.name to resolveTypeDecl(it.type, resolverState)
                                    }.toMap(),
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
        is Expression.FunctionCall -> {
            resolveFunctionCallExpression(node, resolverState)
        }
        is Expression.MemberLookup -> {
            val receiverType = resolverState.resolvedEnvironment.typeOf(node.receiver)
            when (receiverType) {
                is Type.Struct -> {
                    resolverState.resolvedEnvironment.recordType(
                        node,
                        receiverType.members[node.memberName]
                            ?: throw RuntimeException("Struct with type $receiverType does not have a member ${node.memberName}"),
                    )
                }
                is Type.Vector -> {
                    when (node.memberName) {
                        "x", "y", "z", "w" -> {
                            // We could check whether the vector index exists, e.g. using z on a vec2 is not be allowed.
                            resolverState.resolvedEnvironment.recordType(node, receiverType.elementType)
                        }
                        else -> TODO()
                    }
                }
                else -> TODO()
            }
        }
        is Expression.IntLiteral -> {
            val type =
                if (node.text.endsWith("u")) {
                    Type.U32
                } else if (node.text.endsWith("i")) {
                    Type.I32
                } else {
                    Type.AbstractInteger
                }
            resolverState.resolvedEnvironment.recordType(node, type)
        }
        is Expression.Binary -> {
            when (node.operator) {
                BinaryOperator.LESS_THAN,
                BinaryOperator.LESS_THAN_EQUAL,
                BinaryOperator.GREATER_THAN,
                BinaryOperator.GREATER_THAN_EQUAL,
                ->
                    {
                        resolverState.resolvedEnvironment.recordType(
                            node,
                            when (resolverState.resolvedEnvironment.typeOf(node.lhs)) {
                                is Type.Scalar -> Type.Bool
                                else -> TODO()
                            },
                        )
                    }
                BinaryOperator.PLUS -> {
                    val lhsType = resolverState.resolvedEnvironment.typeOf(node.lhs)
                    val rhsType = resolverState.resolvedEnvironment.typeOf(node.rhs)
                    val resultType =
                        if (lhsType is Type.I32 && rhsType is Type.I32) {
                            Type.I32
                        } else if (lhsType is Type.I32 && rhsType is Type.AbstractInteger) {
                            Type.I32
                        } else {
                            TODO()
                        }
                    resolverState.resolvedEnvironment.recordType(node, resultType)
                }
                else -> TODO("Not implemented support for ${node.operator}")
            }
        }
        is Expression.Unary -> {
            when (node.operator) {
                UnaryOperator.DEREFERENCE -> {
                    val pointerType = resolverState.resolvedEnvironment.typeOf(node.target)
                    if (pointerType !is Type.Pointer) {
                        throw RuntimeException("Dereference applied to expression $node with non-pointer type")
                    }
                    resolverState.resolvedEnvironment.recordType(node, pointerType.pointeeType)
                }
                UnaryOperator.ADDRESS_OF -> {
                    resolveAddressOfExpression(node, resolverState)
                }
                else -> {
                    TODO("Not implemented support for ${node.operator}")
                }
            }
        }
        is Expression.Paren -> {
            resolverState.resolvedEnvironment.recordType(node, resolverState.resolvedEnvironment.typeOf(node.target))
        }
        is Expression.Identifier -> {
            when (val scopeEntry = resolverState.currentScope.getEntry(node.name)) {
                is ScopeEntry.TypedDecl -> {
                    resolverState.resolvedEnvironment.recordType(node, scopeEntry.type)
                }
                else -> {
                    throw RuntimeException("Identifier ${node.name} does not have a typed scope entry.")
                }
            }
        }
        is Expression.BoolValueConstructor -> resolverState.resolvedEnvironment.recordType(node, Type.Bool)
        is Expression.I32ValueConstructor -> resolverState.resolvedEnvironment.recordType(node, Type.I32)
        is Expression.U32ValueConstructor -> resolverState.resolvedEnvironment.recordType(node, Type.U32)
        is Expression.VectorValueConstructor -> resolveVectorValueConstructor(node, resolverState)
        is Expression.MatrixValueConstructor -> resolveMatrixValueConstructor(node, resolverState)
        is Expression.ArrayValueConstructor -> resolveArrayValueConstructor(node, resolverState)
        is Expression.StructValueConstructor -> {
            when (val scopeEntry = resolverState.currentScope.getEntry(node.typeName)) {
                is ScopeEntry.Struct -> {
                    resolverState.resolvedEnvironment.recordType(node, scopeEntry.type)
                }
                else -> {
                    throw RuntimeException("Attempt to construct a struct with constructor ${node.typeName}, which is not a struct type.")
                }
            }
        }
        else -> {
            // No action
        }
    }
}

private fun resolveVectorValueConstructor(
    node: Expression.VectorValueConstructor,
    resolverState: ResolverState,
) {
    val elementType: Type.Scalar =
        if (node.elementType != null) {
            resolveTypeDecl(node.elementType!!, resolverState) as Type.Scalar
        } else {
            var candidateElementType: Type.Scalar? = null
            for (arg in node.args) {
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
                if (candidateElementType == null ||
                    candidateElementType is Type.AbstractInteger &&
                    elementTypeForArg is Type.Integer ||
                    candidateElementType is Type.AbstractFloat &&
                    elementTypeForArg is Type.AbstractFloat
                ) {
                    candidateElementType = elementTypeForArg
                } else if (candidateElementType != elementTypeForArg) {
                    throw RuntimeException("Vector constructed from incompatible mix of element types.")
                }
            }
            candidateElementType!!
        }
    when (node) {
        is Expression.Vec2ValueConstructor -> resolverState.resolvedEnvironment.recordType(node, Type.Vec2(elementType))
        is Expression.Vec3ValueConstructor -> resolverState.resolvedEnvironment.recordType(node, Type.Vec3(elementType))
        is Expression.Vec4ValueConstructor -> resolverState.resolvedEnvironment.recordType(node, Type.Vec4(elementType))
    }
}

private fun resolveMatrixValueConstructor(
    node: Expression.MatrixValueConstructor,
    resolverState: ResolverState,
) {
    TODO()
}

private fun resolveArrayValueConstructor(
    node: Expression.ArrayValueConstructor,
    resolverState: ResolverState,
) {
    TODO()
}

private fun resolveFunctionCallExpression(
    node: Expression.FunctionCall,
    resolverState: ResolverState,
) {
    when (val scopeEntry = resolverState.currentScope.getEntry(node.callee)) {
        null -> {
            when (node.callee) {
                "atomicLoad" -> {
                    if (node.args.size != 1) {
                        throw RuntimeException("atomicLoad builtin takes one argument")
                    }
                    val argType = resolverState.resolvedEnvironment.typeOf(node.args[0])
                    if (argType !is Type.Pointer || argType.pointeeType !is Type.Atomic) {
                        throw RuntimeException("atomicLoad requires a pointer to an atomic integer")
                    }
                    resolverState.resolvedEnvironment.recordType(node, argType.pointeeType.targetType)
                }
                "cross" -> {
                    if (node.args.size != 2) {
                        throw RuntimeException("cross builtin takes two arguments")
                    }
                    val arg1Type = resolverState.resolvedEnvironment.typeOf(node.args[0])
                    val arg2Type = resolverState.resolvedEnvironment.typeOf(node.args[1])
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
                    resolverState.resolvedEnvironment.recordType(node, arg1Type)
                }
                "mat2x2f" -> resolverState.resolvedEnvironment.recordType(node, Type.Mat2x2(Type.F32))
                "mat2x3f" -> resolverState.resolvedEnvironment.recordType(node, Type.Mat2x3(Type.F32))
                "mat2x4f" -> resolverState.resolvedEnvironment.recordType(node, Type.Mat2x4(Type.F32))
                "mat3x2f" -> resolverState.resolvedEnvironment.recordType(node, Type.Mat3x2(Type.F32))
                "mat3x3f" -> resolverState.resolvedEnvironment.recordType(node, Type.Mat3x3(Type.F32))
                "mat3x4f" -> resolverState.resolvedEnvironment.recordType(node, Type.Mat3x4(Type.F32))
                "mat4x2f" -> resolverState.resolvedEnvironment.recordType(node, Type.Mat4x2(Type.F32))
                "mat4x3f" -> resolverState.resolvedEnvironment.recordType(node, Type.Mat4x3(Type.F32))
                "mat4x4f" -> resolverState.resolvedEnvironment.recordType(node, Type.Mat4x4(Type.F32))
                "mat2x2h" -> resolverState.resolvedEnvironment.recordType(node, Type.Mat2x2(Type.F16))
                "mat2x3h" -> resolverState.resolvedEnvironment.recordType(node, Type.Mat2x3(Type.F16))
                "mat2x4h" -> resolverState.resolvedEnvironment.recordType(node, Type.Mat2x4(Type.F16))
                "mat3x2h" -> resolverState.resolvedEnvironment.recordType(node, Type.Mat3x2(Type.F16))
                "mat3x3h" -> resolverState.resolvedEnvironment.recordType(node, Type.Mat3x3(Type.F16))
                "mat3x4h" -> resolverState.resolvedEnvironment.recordType(node, Type.Mat3x4(Type.F16))
                "mat4x2h" -> resolverState.resolvedEnvironment.recordType(node, Type.Mat4x2(Type.F16))
                "mat4x3h" -> resolverState.resolvedEnvironment.recordType(node, Type.Mat4x3(Type.F16))
                "mat4x4h" -> resolverState.resolvedEnvironment.recordType(node, Type.Mat4x4(Type.F16))
                "vec2i" -> resolverState.resolvedEnvironment.recordType(node, Type.Vec2(Type.I32))
                "vec3i" -> resolverState.resolvedEnvironment.recordType(node, Type.Vec3(Type.I32))
                "vec4i" -> resolverState.resolvedEnvironment.recordType(node, Type.Vec4(Type.I32))
                "vec2u" -> resolverState.resolvedEnvironment.recordType(node, Type.Vec2(Type.U32))
                "vec3u" -> resolverState.resolvedEnvironment.recordType(node, Type.Vec3(Type.U32))
                "vec4u" -> resolverState.resolvedEnvironment.recordType(node, Type.Vec4(Type.U32))
                "vec2f" -> resolverState.resolvedEnvironment.recordType(node, Type.Vec2(Type.F32))
                "vec3f" -> resolverState.resolvedEnvironment.recordType(node, Type.Vec3(Type.F32))
                "vec4f" -> resolverState.resolvedEnvironment.recordType(node, Type.Vec4(Type.F32))
                "vec2h" -> resolverState.resolvedEnvironment.recordType(node, Type.Vec2(Type.F16))
                "vec3h" -> resolverState.resolvedEnvironment.recordType(node, Type.Vec3(Type.F16))
                "vec4h" -> resolverState.resolvedEnvironment.recordType(node, Type.Vec4(Type.F16))
                else -> TODO("Unsupported builtin function ${node.callee}")
            }
        }
        is ScopeEntry.Function -> {
            resolverState.resolvedEnvironment.recordType(
                node,
                scopeEntry.type.returnType
                    ?: throw RuntimeException("Call expression used with function ${node.callee}, which does not return a value."),
            )
        }

        else -> {
            throw RuntimeException("Function call attempted on unknown callee ${node.callee}")
        }
    }
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
        is TypeDecl.Vec2 -> Type.Vec2(elementType)
        is TypeDecl.Vec3 -> Type.Vec3(elementType)
        is TypeDecl.Vec4 -> Type.Vec4(elementType)
    }
}

private fun resolveMatrixTypeDecl(matrixTypeDecl: TypeDecl.MatrixTypeDecl): Type.Matrix {
    val elementType = resolveFloatTypeDecl(matrixTypeDecl.elementType)
    return when (matrixTypeDecl) {
        is TypeDecl.Mat2x2 -> {
            Type.Mat2x2(elementType)
        }

        is TypeDecl.Mat2x3 -> {
            Type.Mat2x3(elementType)
        }

        is TypeDecl.Mat2x4 -> {
            Type.Mat2x4(elementType)
        }

        is TypeDecl.Mat3x2 -> {
            Type.Mat3x2(elementType)
        }

        is TypeDecl.Mat3x3 -> {
            Type.Mat3x3(elementType)
        }

        is TypeDecl.Mat3x4 -> {
            Type.Mat3x4(elementType)
        }

        is TypeDecl.Mat4x2 -> {
            Type.Mat4x2(elementType)
        }

        is TypeDecl.Mat4x3 -> {
            Type.Mat4x3(elementType)
        }

        is TypeDecl.Mat4x4 -> {
            Type.Mat4x4(elementType)
        }
    }
}

private fun concretizeInitializerType(type: Type): Type =
    when (type) {
        is Type.AbstractInteger -> Type.I32
        is Type.AbstractFloat -> Type.F32
        is Type.Vec2 -> Type.Vec2(concretizeInitializerType(type.elementType) as Type.Scalar)
        is Type.Vec3 -> Type.Vec3(concretizeInitializerType(type.elementType) as Type.Scalar)
        is Type.Vec4 -> Type.Vec4(concretizeInitializerType(type.elementType) as Type.Scalar)
        is Type.Mat2x2 -> Type.Mat2x2(concretizeInitializerType(type.elementType) as Type.Float)
        is Type.Mat2x3 -> Type.Mat2x3(concretizeInitializerType(type.elementType) as Type.Float)
        is Type.Mat2x4 -> Type.Mat2x4(concretizeInitializerType(type.elementType) as Type.Float)
        is Type.Mat3x2 -> Type.Mat3x2(concretizeInitializerType(type.elementType) as Type.Float)
        is Type.Mat3x3 -> Type.Mat3x3(concretizeInitializerType(type.elementType) as Type.Float)
        is Type.Mat3x4 -> Type.Mat3x4(concretizeInitializerType(type.elementType) as Type.Float)
        is Type.Mat4x2 -> Type.Mat4x2(concretizeInitializerType(type.elementType) as Type.Float)
        is Type.Mat4x3 -> Type.Mat4x3(concretizeInitializerType(type.elementType) as Type.Float)
        is Type.Mat4x4 -> Type.Mat4x4(concretizeInitializerType(type.elementType) as Type.Float)
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

private fun evaluateToInt(expression: Expression): Int? {
    if (expression is Expression.IntLiteral) {
        if (expression.text.endsWith("u") || expression.text.endsWith("i")) {
            return expression.text.substring(0, expression.text.length - 1).toInt()
        }
        return expression.text.toInt()
    }
    return null
}

private fun resolveTypeDecl(
    typeDecl: TypeDecl,
    resolverState: ResolverState,
): Type =
    when (typeDecl) {
        is TypeDecl.Array ->
            Type.Array(
                elementType = resolveTypeDecl(typeDecl.elementType, resolverState),
                elementCount = typeDecl.elementCount?.let(::evaluateToInt),
            )
        is TypeDecl.NamedType -> {
            when (typeDecl.name) {
                "vec2f" -> Type.Vec3(Type.F32)
                "vec3f" -> Type.Vec3(Type.F32)
                "vec4f" -> Type.Vec3(Type.F32)
                "vec2i" -> Type.Vec3(Type.I32)
                "vec3i" -> Type.Vec3(Type.I32)
                "vec4i" -> Type.Vec3(Type.I32)
                "vec2u" -> Type.Vec3(Type.U32)
                "vec3u" -> Type.Vec3(Type.U32)
                "vec4u" -> Type.Vec3(Type.U32)
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
                else -> {
                    when (val scopeEntry = resolverState.currentScope.getEntry(typeDecl.name)) {
                        is ScopeEntry.Struct -> {
                            scopeEntry.type
                        }
                        else -> {
                            TODO("Unknown typed declaration: ${typeDecl.name}")
                        }
                    }
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
