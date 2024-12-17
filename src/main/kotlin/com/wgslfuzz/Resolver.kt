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

    override fun typeOf(expression: Expression): Type = expressionTypes[expression]!!

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
        is Statement.Variable -> {
            val type: Type =
                node.type?.let {
                    resolveTypeDecl(node.type!!, resolverState)
                } ?: TODO()
            resolverState.currentScope.addEntry(
                node.name,
                ScopeEntry.LocalVariable(node, type),
            )
        }
        is Expression.IntLiteral -> {
            val type =
                if (node.text.endsWith("u")) {
                    Type.U32
                } else if (node.text.endsWith("i")) {
                    Type.I32
                } else {
                    Type.AbstractInt
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
                                is Type.ScalarType -> Type.Bool
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
                        } else if (lhsType is Type.I32 && rhsType is Type.AbstractInt) {
                            Type.I32
                        } else {
                            TODO()
                        }
                    resolverState.resolvedEnvironment.recordType(node, resultType)
                }
                else -> TODO("Not implemented support for ${node.operator}")
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
        else -> {
            // No action
        }
    }
}

private fun resolveFloatTypeDecl(floatTypeDecl: TypeDecl.FloatTypeDecl): Type.FloatType =
    when (floatTypeDecl) {
        TypeDecl.F16 -> Type.F16
        TypeDecl.F32 -> Type.F32
    }

private fun resolveScalarTypeDecl(scalarTypeDecl: TypeDecl.ScalarTypeDecl): Type.ScalarType =
    when (scalarTypeDecl) {
        is TypeDecl.FloatTypeDecl -> resolveFloatTypeDecl(scalarTypeDecl)
        TypeDecl.Bool -> Type.Bool
        TypeDecl.I32 -> Type.I32
        TypeDecl.U32 -> Type.U32
    }

private fun resolveVectorTypeDecl(vectorTypeDecl: TypeDecl.VectorTypeDecl): Type.VectorType {
    val elementType = resolveScalarTypeDecl(vectorTypeDecl.elementType)
    return when (vectorTypeDecl) {
        is TypeDecl.Vec2 -> Type.Vec2(elementType)
        is TypeDecl.Vec3 -> Type.Vec3(elementType)
        is TypeDecl.Vec4 -> Type.Vec4(elementType)
    }
}

private fun resolveMatrixTypeDecl(matrixTypeDecl: TypeDecl.MatrixTypeDecl): Type.MatrixType {
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
        is TypeDecl.NamedType -> TODO()
        is TypeDecl.Pointer -> TODO()
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
