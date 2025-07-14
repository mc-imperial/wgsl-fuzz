/*
 * Copyright 2025 The wgsl-fuzz Project Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wgslfuzz.core

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

sealed interface Scope {
    val parent: Scope?

    fun getEntry(name: String): ScopeEntry?
}

sealed interface ResolvedEnvironment {
    val globalScope: Scope

    fun typeOf(expression: Expression): Type

    fun typeOf(lhsExpression: LhsExpression): Type

    /**
     * Yields a scope that can be used to query all declarations that are in scope immediately _before_ the given
     * statement. Therefore, if the statement is a declaration this scope does _not_ include the declaration itself.
     */
    fun scopeAvailableBefore(statement: Statement): Scope

    /**
     * Yields a scope that can be used to query all declarations that are in scope at the very end of a compound
     * statement - i.e., right before its closing curly brace.
     */
    fun scopeAvailableAtEnd(compound: Statement.Compound): Scope

    /**
     * Yields a scope corresponding to the given index into the given compound statement.
     */
    fun scopeAtIndex(
        compound: Statement.Compound,
        index: Int,
    ): Scope {
        check(index in 0..compound.statements.size)
        return if (index < compound.statements.size) {
            scopeAvailableBefore(compound.statements[index])
        } else {
            scopeAvailableAtEnd(compound)
        }
    }
}

private class ScopeImpl(
    override val parent: ScopeImpl?,
) : Scope {
    private val entries: MutableMap<String, ScopeEntry> = mutableMapOf()

    fun addEntry(
        name: String,
        node: ScopeEntry,
    ) {
        if (name in entries.keys) {
            throw IllegalArgumentException("An entry for $name already exists in the current scope.")
        }
        entries[name] = node
    }

    /**
     * Creates a new scope based on this scope. The parent of the resulting scope is referentially equal to the parent
     * of this scope (so that parent scopes are not copied), but the immediate contents of this scope are captured via
     * a fresh map in the newly-created scope. The point of this is to allow each statement in a compound to have its
     * own view of what is in scope so far in the compound.
     */
    fun shallowCopy(): Scope {
        val result =
            ScopeImpl(
                parent = parent,
            )
        for (nameEntryPair: Map.Entry<String, ScopeEntry> in entries) {
            result.entries[nameEntryPair.key] = nameEntryPair.value
        }
        return result
    }

    override fun getEntry(name: String): ScopeEntry? = entries[name] ?: parent?.getEntry(name)
}

private class ResolvedEnvironmentImpl(
    override val globalScope: Scope,
) : ResolvedEnvironment {
    // The resolving process gives a type to _every_ expression in the AST.
    private val expressionTypes: MutableMap<Expression, Type> = mutableMapOf()

    // The resolving process gives a type to _every_ left-hand-side expression in the AST.
    private val lhsExpressionTypes: MutableMap<LhsExpression, Type> = mutableMapOf()

    // For every statement in the AST, we record a scope capturing what is available right before the statement. For
    // statements in the same compound, these scopes can be different due to intervening local variable/value
    // declarations.
    private val scopeAvailableBeforeEachStatement: MutableMap<Statement, Scope> = mutableMapOf()

    // For every compound in the AST, we record a scope capturing what is available right at the end of the compound,
    // i.e. just before its closing curly brace.
    private val scopeAvailableAtEndOfEachCompound: MutableMap<Statement, Scope> = mutableMapOf()

    fun recordType(
        expression: Expression,
        type: Type,
    ) {
        assert(expression !in expressionTypes.keys)
        expressionTypes[expression] = type
    }

    fun recordType(
        lhsExpression: LhsExpression,
        type: Type,
    ) {
        assert(lhsExpression !in lhsExpressionTypes.keys)
        lhsExpressionTypes[lhsExpression] = type
    }

    fun recordScopeAvailableBeforeStatement(
        statement: Statement,
        scope: Scope,
    ) {
        assert(statement !in scopeAvailableBeforeEachStatement.keys)
        scopeAvailableBeforeEachStatement[statement] = scope
    }

    fun recordScopeAvailableAtEndOfCompound(
        compound: Statement.Compound,
        scope: Scope,
    ) {
        assert(compound !in scopeAvailableAtEndOfEachCompound.keys)
        scopeAvailableAtEndOfEachCompound[compound] = scope
    }

    override fun typeOf(expression: Expression): Type =
        expressionTypes[expression]
            ?: throw IllegalArgumentException("No type for $expression")

    override fun typeOf(lhsExpression: LhsExpression): Type =
        lhsExpressionTypes[lhsExpression]
            ?: throw IllegalArgumentException("No type for $lhsExpression")

    override fun scopeAvailableBefore(statement: Statement): Scope =
        scopeAvailableBeforeEachStatement[statement] ?: throw IllegalArgumentException("No scope for $statement")

    override fun scopeAvailableAtEnd(compound: Statement.Compound): Scope =
        scopeAvailableAtEndOfEachCompound[compound] ?: throw IllegalArgumentException("No scope for $compound")
}

private fun collectUsedModuleScopeNames(node: AstNode): Set<String> {
    fun collectAction(
        node: AstNode,
        collectedNames: MutableSet<String>,
    ) {
        traverse(::collectAction, node, collectedNames)
        when (node) {
            is Expression.Identifier -> collectedNames.add(node.name)
            is Expression.StructValueConstructor -> collectedNames.add(node.constructorName)
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
                        decl.typeDecl,
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
                    decl.attributes + listOf(decl.typeDecl, decl.initializer),
                )
            }
            is GlobalDecl.Struct -> {
                nameToDecl[decl.name] = decl
                collectUsedModuleScopeNames(decl.name, nameDependencies, decl.members)
            }
            is GlobalDecl.TypeAlias -> {
                nameToDecl[decl.name] = decl
                collectUsedModuleScopeNames(decl.name, nameDependencies, listOf(decl.typeDecl))
            }
            is GlobalDecl.Variable -> {
                nameToDecl[decl.name] = decl
                collectUsedModuleScopeNames(
                    decl.name,
                    nameDependencies,
                    decl.attributes + listOf(decl.typeDecl, decl.initializer),
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
            throw IllegalArgumentException("Cycle detected in globally-scoped declarations")
        }
        for (name in addedThisRound) {
            toProcess.remove(name)
        }
        for (entry in toProcess) {
            entry.value.removeAll(addedThisRound.toSet())
        }
        result.addAll(addedThisRound)
    }
    return result
}

private class ResolverState {
    var currentScope: ScopeImpl = ScopeImpl(null)
        private set

    val resolvedEnvironment: ResolvedEnvironmentImpl = ResolvedEnvironmentImpl(currentScope)

    val ancestorsStack: MutableList<AstNode> = mutableListOf()

    fun maybeWithScope(
        newScopeRequired: Boolean,
        action: () -> Unit,
    ) {
        if (newScopeRequired) {
            currentScope =
                ScopeImpl(
                    parent = currentScope,
                )
        }
        action()
        if (newScopeRequired) {
            currentScope = currentScope.parent!!
        }
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
        // Creating a shallow copy of the current scope and associating the shallow copy with this statement ensures that:
        // - all statements in the current compound statement will share all enclosing scopes
        // - this statement will have its own view of what is in scope at this point in the current compound statement,
        //   so that if local variables/values are declared in sequence, only those that have already been declared
        //   this statement will be in scope for the statement.
        resolverState.resolvedEnvironment.recordScopeAvailableBeforeStatement(node, resolverState.currentScope.shallowCopy())
    }

    val parentNode = resolverState.ancestorsStack.firstOrNull()
    resolverState.maybeWithScope(nodeIntroducesNewScope(node, parentNode)) {
        resolverState.ancestorsStack.addFirst(node)
        traverse(::resolveAstNode, node, resolverState)
        if (node is Statement.Compound) {
            resolverState.resolvedEnvironment.recordScopeAvailableAtEndOfCompound(node, resolverState.currentScope.shallowCopy())
        }
        resolverState.ancestorsStack.removeFirst()
    }

    when (node) {
        is GlobalDecl.TypeAlias -> {
            resolverState.currentScope.addEntry(
                node.name,
                ScopeEntry.TypeAlias(
                    node,
                    resolveTypeDecl(node.typeDecl, resolverState),
                ),
            )
        }
        is GlobalDecl.Variable -> resolveGlobalVariable(node, resolverState)
        is GlobalDecl.Constant -> {
            resolverState.currentScope.addEntry(
                node.name,
                ScopeEntry.GlobalConstant(
                    astNode = node,
                    type =
                        node.typeDecl?.let {
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
                        node.typeDecl?.let {
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
                                node.members.map {
                                    it.name to resolveTypeDecl(it.typeDecl, resolverState)
                                },
                        ),
                ),
            )
        }
        is Statement.FunctionCall -> {
            when (val entry = resolverState.currentScope.getEntry(node.callee)) {
                is ScopeEntry.Function -> {
                    // Good; nothing to do.
                }
                null -> {
                    if (!isStatementFunctionCallBuiltin(node)) {
                        throw UnsupportedOperationException(
                            "Statement function call refers to ${node.callee} which is not in scope not the name of a known builtin.",
                        )
                    }
                }
                else -> {
                    throw UnsupportedOperationException(
                        "Statement function call to name ${node.callee} does not refer to a function; scope entry is $entry",
                    )
                }
            }
        }
        is Statement.Value -> {
            var type: Type =
                node.typeDecl?.let {
                    resolveTypeDecl(node.typeDecl, resolverState)
                } ?: resolverState.resolvedEnvironment.typeOf(node.initializer)
            if (type.isAbstract()) {
                type = defaultConcretizationOf(type)
            }
            resolverState.currentScope.addEntry(
                node.name,
                ScopeEntry.LocalValue(node, type),
            )
        }
        is Statement.Variable -> resolveLocalVariable(node, resolverState)
        is Expression -> resolverState.resolvedEnvironment.recordType(node, resolveExpressionType(node, resolverState))
        is LhsExpression -> resolverState.resolvedEnvironment.recordType(node, resolveLhsExpressionType(node, resolverState))
        else -> {
            // No action
        }
    }
}

private fun resolveGlobalVariable(
    node: GlobalDecl.Variable,
    resolverState: ResolverState,
) {
    val type: Type =
        defaultConcretizationOf(
            node.typeDecl?.let {
                resolveTypeDecl(node.typeDecl, resolverState)
            } ?: resolverState.resolvedEnvironment.typeOf(node.initializer!!),
        )

    if (node.addressSpace == AddressSpace.FUNCTION) {
        throw IllegalArgumentException(
            "Variables in the function address space must only be declared in function scope.",
        )
    } else if (node.accessMode != null && node.addressSpace == null) {
        throw IllegalArgumentException(
            "If an access mode is specified for a variable an address space must also be specified.",
        )
    } else if (node.addressSpace != AddressSpace.STORAGE && node.accessMode != null) {
        throw IllegalArgumentException(
            "The access mode must not be specified unless a variable is in the storage address space.",
        )
    } else if (node.addressSpace == AddressSpace.HANDLE) {
        // A handle address space cannot be specified in WGSL source.
        throw IllegalArgumentException(
            "The handle address space cannot be specified in WGSL source.",
        )
    }

    val refType =
        if (type is Type.Texture || type is Type.Sampler) {
            // Access to texture and sampler types are mediated through a handle.
            // https://www.w3.org/TR/WGSL/#texture-sampler-types
            Type.Reference(type, AddressSpace.HANDLE, AccessMode.READ)
        } else {
            val addressSpace = node.addressSpace ?: AddressSpace.FUNCTION
            val accessMode = node.accessMode ?: defaultAccessModeOf(addressSpace)
            type as? Type.Reference ?: Type.Reference(type, addressSpace, accessMode)
        }

    resolverState.currentScope.addEntry(
        node.name,
        ScopeEntry.GlobalVariable(node, refType),
    )
}

private fun resolveLocalVariable(
    node: Statement.Variable,
    resolverState: ResolverState,
) {
    var type: Type =
        node.typeDecl?.let {
            resolveTypeDecl(node.typeDecl, resolverState)
        } ?: resolverState.resolvedEnvironment.typeOf(node.initializer!!)
    if (type.isAbstract()) {
        type = defaultConcretizationOf(type)
    }

    if (node.accessMode != null && node.addressSpace == null) {
        throw IllegalArgumentException(
            "If an access mode is specified for a variable an address space must also be specified.",
        )
    } else if (node.addressSpace != null && node.addressSpace != AddressSpace.FUNCTION) {
        throw IllegalArgumentException(
            "The address spaces private, storage, uniform, workgroup and handle can only be used for module scope variables",
        )
    } else if (node.accessMode != null) {
        throw IllegalArgumentException(
            "The access mode must not be specified unless a variable is in the storage address space.",
        )
    }

    // If the address space is not specified in the source, default to function.
    val addressSpace = node.addressSpace ?: AddressSpace.FUNCTION
    val accessMode = defaultAccessModeOf(addressSpace)
    type = type as? Type.Reference ?: Type.Reference(type, addressSpace, accessMode)

    resolverState.currentScope.addEntry(
        node.name,
        ScopeEntry.LocalVariable(node, type),
    )
}

/**
 * Whether a node introduces a new lexical scope is a little subtle. Generally, a compound statement introduces a new
 * scope, but there are exceptions. For example, the parameters of a function declaration share the same lexical scope
 * as the function body, so the function body's compound statement does not introduce a new scope. As another example,
 * the continuing construct of a loop inherits the scope of the loop construct, therefore we cannot rely on the
 * (disjoint) compound statements of a loop and its continuing construct when it comes to the introduction of scopes.
 * This helper encapsulates the subtleties of when new scopes are introduced.
 */
private fun nodeIntroducesNewScope(
    node: AstNode,
    parentNode: AstNode?,
) = node is Statement.Loop ||
    node is ContinuingStatement ||
    node is Statement.For ||
    (
        node is Statement.Compound &&
            parentNode !is GlobalDecl.Function &&
            parentNode !is Statement.Loop &&
            parentNode !is ContinuingStatement
    )

private fun Type.asStoreTypeIfReference(): Type =
    when (this) {
        is Type.Reference -> this.storeType
        else -> this
    }

private fun defaultAccessModeOf(addressSpace: AddressSpace): AccessMode =
    when (addressSpace) {
        AddressSpace.FUNCTION -> AccessMode.READ_WRITE
        AddressSpace.PRIVATE -> AccessMode.READ_WRITE
        AddressSpace.WORKGROUP -> AccessMode.READ_WRITE
        AddressSpace.UNIFORM -> AccessMode.READ
        AddressSpace.STORAGE -> AccessMode.READ
        AddressSpace.HANDLE -> AccessMode.READ
    }

private fun resolveExpressionType(
    expression: Expression,
    resolverState: ResolverState,
): Type =
    when (expression) {
        is Expression.FunctionCall -> resolveTypeOfFunctionCallExpression(expression, resolverState)
        is Expression.IndexLookup -> resolveTypeOfIndexLookupExpression(expression, resolverState)
        is Expression.MemberLookup -> resolveTypeOfMemberLookupExpression(expression, resolverState)
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
                else -> throw IllegalArgumentException("Identifier ${expression.name} does not have a typed scope entry")
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
            when (val scopeEntry = resolverState.currentScope.getEntry(expression.constructorName)) {
                is ScopeEntry.Struct -> scopeEntry.type
                else -> throw IllegalArgumentException(
                    "Attempt to construct a struct with constructor ${expression.constructorName}, which is not a struct type",
                )
            }
        is Expression.TypeAliasValueConstructor ->
            (
                resolverState.currentScope.getEntry(
                    expression.constructorName,
                ) as ScopeEntry.TypeAlias
            ).type
        is AugmentedExpression.FalseByConstruction -> resolverState.resolvedEnvironment.typeOf(expression.falseExpression)
        is AugmentedExpression.TrueByConstruction -> resolverState.resolvedEnvironment.typeOf(expression.trueExpression)
        is AugmentedExpression.IdentityOperation -> resolverState.resolvedEnvironment.typeOf(expression.originalExpression)
        is AugmentedExpression.KnownValue -> {
            val knownValueType = resolverState.resolvedEnvironment.typeOf(expression.knownValue).asStoreTypeIfReference()
            val expressionType = resolverState.resolvedEnvironment.typeOf(expression.expression).asStoreTypeIfReference()
            if (knownValueType != expressionType) {
                throw RuntimeException("Types for known value expression and its corresponding obfuscated expression do not match.")
            }
            expressionType
        }
    }

private fun resolveTypeOfIndexLookupExpression(
    indexLookup: Expression.IndexLookup,
    resolverState: ResolverState,
): Type {
    fun resolveDirectIndexType(type: Type): Type =
        when (type) {
            is Type.Matrix -> Type.Vector(width = type.numRows, elementType = type.elementType)
            is Type.Vector -> type.elementType
            // TODO: Throw a shader creation error if the index is a const expr greater than the
            // element count of the array
            is Type.Array -> type.elementType
            else -> throw IllegalArgumentException("Index lookup attempted on unsuitable type $type")
        }

    // Check the type of the index is i32 or u32.
    // TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/94): This is not really strict enough.
    val indexType = resolverState.resolvedEnvironment.typeOf(indexLookup.index).asStoreTypeIfReference()
    if (!indexType.isAbstractionOf(Type.I32) && !indexType.isAbstractionOf(Type.U32)) {
        println(resolverState.resolvedEnvironment.typeOf(indexLookup.index))
        throw IllegalArgumentException("Array index expression must be of type i32 or u32.")
    }

    /* When accessing an index through a memory view expression (i.e. through a pointer or reference),
    a reference type is returned. The store type of the reference is the same as the type of the elements in the
    target.

    For example, consider the following code snippet in the body of a function. The type of arr is
    ref<function, array<i32,2>, read_write>. The variable elem then has the type ref<function, i32, read_write>.

        var arr = array<i32, 2>(0,1);
        var elem = arr[0];

    The same thing applies to arrays and matrices. In the following example, the variable mat has the type
    ref<function, mat2x3<i32>, read_write>. The variable row has the type ref<function, vec3<i32>, read_write>.

        var mat = mat2x3<f32>(vec3<f32>(0,1, 2), vec3<f32>(3,4,5));
        var row = mat[0];
     */
    return when (val targetType = resolverState.resolvedEnvironment.typeOf(indexLookup.target)) {
        // https://www.w3.org/TR/WGSL/#array-access-expr
        is Type.Pointer -> {
            val newStoreType = resolveDirectIndexType(targetType.pointeeType)
            Type.Reference(newStoreType, targetType.addressSpace, targetType.accessMode)
        }
        // https://www.w3.org/TR/WGSL/#array-access-expr
        is Type.Reference -> {
            val newStoreType = resolveDirectIndexType(targetType.storeType)
            Type.Reference(newStoreType, targetType.addressSpace, targetType.accessMode)
        }
        else -> resolveDirectIndexType(targetType)
    }
}

private fun resolveTypeOfMemberLookupExpression(
    memberLookup: Expression.MemberLookup,
    resolverState: ResolverState,
): Type {
    fun resolveDirectMemberLookupType(receiverType: Type): Type =
        when (receiverType) {
            is Type.Struct ->
                receiverType.members
                    .firstOrNull {
                        it.first == memberLookup.memberName
                    }?.second
                    ?: throw IllegalArgumentException(
                        "Struct with type $receiverType does not have a member ${memberLookup.memberName}",
                    )

            is Type.Vector ->
                // In the following, we could check whether the vector indices exist, e.g. using z on a vec2 is not be allowed.
                if (memberLookup.memberName in setOf("x", "y", "z", "w", "r", "g", "b", "a")) {
                    receiverType.elementType
                } else if (isSwizzle(memberLookup.memberName)) {
                    Type.Vector(memberLookup.memberName.length, receiverType.elementType)
                } else {
                    TODO()
                }

            else -> throw UnsupportedOperationException("Member lookup not implemented for receiver of type $receiverType")
        }

    /* When accessing a member through a memory view expression (i.e. through a pointer or reference),
    a reference type is returned. The store type of the reference is the same as the type of the member in the
    target.

    For example, consider the following code snippet in the body of a function. The type of `t` is
    ref<function, T, read_write>. The variable elem will then have the type ref<function, i32, read_write>.

        struct T {
            a: i32,
        }
        var t: T;
        var elem = t.a;
     */
    return when (val receiverType = resolverState.resolvedEnvironment.typeOf(memberLookup.receiver)) {
        // https://www.w3.org/TR/WGSL/#component-reference-from-vector-memory-view
        is Type.Pointer -> {
            val newStoreType = resolveDirectMemberLookupType(receiverType.pointeeType)
            Type.Reference(newStoreType, receiverType.addressSpace, receiverType.accessMode)
        }
        // https://www.w3.org/TR/WGSL/#component-reference-from-vector-memory-view
        is Type.Reference -> {
            val newStoreType = resolveDirectMemberLookupType(receiverType.storeType)
            Type.Reference(newStoreType, receiverType.addressSpace, receiverType.accessMode)
        }

        else -> resolveDirectMemberLookupType(receiverType)
    }
}

private fun resolveLhsExpressionType(
    lhsExpression: LhsExpression,
    resolverState: ResolverState,
): Type =
    when (lhsExpression) {
        is LhsExpression.AddressOf -> {
            val referenceType = resolverState.resolvedEnvironment.typeOf(lhsExpression.target)
            if (referenceType !is Type.Reference) {
                throw RuntimeException(
                    "Address-of in LHS expression applied to expression ${lhsExpression.target} with non-reference " +
                        "type",
                )
            }
            // The AddressOf expression converts a pointer to a reference.
            // https://www.w3.org/TR/WGSL/#address-of-expr
            Type.Pointer(referenceType.storeType, referenceType.addressSpace, referenceType.accessMode)
        }
        is LhsExpression.Dereference -> {
            val pointerType = resolverState.resolvedEnvironment.typeOf(lhsExpression.target)
            if (pointerType !is Type.Pointer) {
                throw RuntimeException("Dereference in LHS expression applied to expression ${lhsExpression.target} with non-pointer type")
            }
            // The Indirection expression converts a reference to a pointer.
            // https://www.w3.org/TR/WGSL/#indirection-expr
            Type.Reference(pointerType.pointeeType, pointerType.addressSpace, pointerType.accessMode)
        }
        is LhsExpression.Identifier -> {
            when (val scopeEntry = resolverState.currentScope.getEntry(lhsExpression.name)) {
                is ScopeEntry.LocalValue -> {
                    assert(scopeEntry.type is Type.Pointer)
                    scopeEntry.type
                }
                is ScopeEntry.Parameter -> {
                    assert(scopeEntry.type is Type.Pointer)
                    scopeEntry.type
                }
                is ScopeEntry.LocalVariable -> {
                    assert(scopeEntry.type !is Type.Pointer)
                    scopeEntry.type
                }
                is ScopeEntry.GlobalVariable -> {
                    assert(scopeEntry.type !is Type.Pointer)
                    scopeEntry.type
                }
                else -> throw RuntimeException("Unsuitable scope entry for identifier occurring in LHS expression")
            }
        }
        is LhsExpression.IndexLookup -> {
            val targetType = resolverState.resolvedEnvironment.typeOf(lhsExpression.target)
            val addressSpace: AddressSpace?
            val accessMode: AccessMode?
            val storeType: Type?
            when (targetType) {
                is Type.Reference -> {
                    storeType = targetType.storeType
                    addressSpace = targetType.addressSpace
                    accessMode = targetType.accessMode
                }
                is Type.Pointer -> {
                    storeType = targetType.pointeeType
                    addressSpace = targetType.addressSpace
                    accessMode = targetType.accessMode
                }
                else -> throw RuntimeException(
                    "Index lookup in LHS expression applied to expression ${lhsExpression.target} with non-reference / pointer type",
                )
            }

            when (storeType) {
                is Type.Vector -> Type.Reference(storeType.elementType, addressSpace, accessMode)
                is Type.Array -> Type.Reference(storeType.elementType, addressSpace, accessMode)
                is Type.Matrix ->
                    Type.Reference(
                        Type.Vector(storeType.numRows, storeType.elementType),
                        addressSpace,
                        accessMode,
                    )
                else -> throw RuntimeException("Index lookup in LHS expression applied to non-indexable reference")
            }
        }
        is LhsExpression.MemberLookup -> {
            val receiverType = resolverState.resolvedEnvironment.typeOf(lhsExpression.receiver)
            val addressSpace: AddressSpace?
            val accessMode: AccessMode?
            val storeType: Type?
            when (receiverType) {
                is Type.Reference -> {
                    storeType = receiverType.storeType
                    addressSpace = receiverType.addressSpace
                    accessMode = receiverType.accessMode
                }
                is Type.Pointer -> {
                    storeType = receiverType.pointeeType
                    addressSpace = receiverType.addressSpace
                    accessMode = receiverType.accessMode
                }
                else -> throw RuntimeException(
                    "Member lookup in LHS expression applied to expression ${lhsExpression.receiver} with non-reference / pointer type",
                )
            }

            when (storeType) {
                is Type.Struct ->
                    Type.Reference(
                        storeType.members
                            .first {
                                it.first == lhsExpression.memberName
                            }.second,
                        addressSpace,
                        accessMode,
                    )
                is Type.Vector -> Type.Reference(storeType.elementType, addressSpace, accessMode)
                else -> throw RuntimeException("Member lookup in LHS expression applied to non-indexable reference")
            }
        }
        is LhsExpression.Paren -> resolverState.resolvedEnvironment.typeOf(lhsExpression.target)
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
        // The Indirection expression converts a reference to a pointer.
        // https://www.w3.org/TR/WGSL/#indirection-expr
        Type.Reference(pointerType.pointeeType, pointerType.addressSpace, pointerType.accessMode)
    }

    UnaryOperator.ADDRESS_OF -> {
        val referenceType = resolverState.resolvedEnvironment.typeOf(expression.target)
        if (referenceType !is Type.Reference) {
            throw RuntimeException("Address-of applied to expression $expression with non-reference type")
        }
        // The AddressOf expression converts a pointer to a reference.
        // https://www.w3.org/TR/WGSL/#address-of-expr
        Type.Pointer(referenceType.storeType, referenceType.addressSpace, referenceType.accessMode)
    }

    UnaryOperator.LOGICAL_NOT -> {
        val targetType = resolverState.resolvedEnvironment.typeOf(expression.target)
        if (targetType.asStoreTypeIfReference() != Type.Bool) {
            throw IllegalArgumentException("Logical not applied to expression $expression with non-bool type")
        }
        Type.Bool
    }

    UnaryOperator.MINUS, UnaryOperator.BINARY_NOT -> resolverState.resolvedEnvironment.typeOf(expression.target)
}

private fun resolveBinary(
    resolverState: ResolverState,
    expression: Expression.Binary,
): Type {
    val lhsType = resolverState.resolvedEnvironment.typeOf(expression.lhs).asStoreTypeIfReference()
    val rhsType = resolverState.resolvedEnvironment.typeOf(expression.rhs).asStoreTypeIfReference()
    return when (val operator = expression.operator) {
        BinaryOperator.LESS_THAN,
        BinaryOperator.LESS_THAN_EQUAL,
        BinaryOperator.GREATER_THAN,
        BinaryOperator.GREATER_THAN_EQUAL,
        -> {
            if (!rhsType.isAbstractionOf(lhsType) &&
                !lhsType.isAbstractionOf(rhsType)
            ) {
                TODO("$operator not supported for $lhsType and $rhsType")
            }
            if (lhsType is Type.Scalar || (lhsType is Type.Reference && lhsType.storeType is Type.Scalar)) {
                return Type.Bool
            } else if (lhsType is Type.Vector) {
                return Type.Vector(lhsType.width, Type.Bool)
            }
            TODO("$lhsType")
        }

        BinaryOperator.PLUS, BinaryOperator.MINUS, BinaryOperator.DIVIDE, BinaryOperator.MODULO ->
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
            } else if (lhsType is Type.Scalar && rhsType is Type.Vector) {
                val rhsElementType = rhsType.elementType
                if (rhsElementType.isAbstractionOf(lhsType)) {
                    Type.Vector(rhsType.width, lhsType)
                } else if (lhsType.isAbstractionOf(rhsElementType)) {
                    rhsType
                } else {
                    TODO("$operator not supported for $lhsType and $rhsType")
                }
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
            } else if (lhsType is Type.Scalar && rhsType is Type.Vector) {
                val rhsElementType = rhsType.elementType
                if (rhsElementType.isAbstractionOf(lhsType)) {
                    Type.Vector(rhsType.width, lhsType)
                } else if (lhsType.isAbstractionOf(rhsElementType)) {
                    rhsType
                } else {
                    TODO("$operator not supported for $lhsType and $rhsType")
                }
            } else if (lhsType is Type.Matrix && rhsType is Type.Vector) {
                Type.Vector(
                    lhsType.numRows,
                    findCommonType(listOf(lhsType.elementType, rhsType.elementType)) as Type.Float,
                )
            } else if (lhsType is Type.Vector && rhsType is Type.Matrix) {
                Type.Vector(
                    rhsType.numCols,
                    findCommonType(listOf(lhsType.elementType, rhsType.elementType)) as Type.Float,
                )
            } else if (lhsType is Type.Matrix && rhsType is Type.Matrix) {
                Type.Matrix(
                    rhsType.numCols,
                    lhsType.numRows,
                    findCommonType(listOf(lhsType.elementType, rhsType.elementType)) as Type.Float,
                )
            } else if (lhsType is Type.Scalar && rhsType is Type.Matrix) {
                Type.Matrix(
                    rhsType.numCols,
                    rhsType.numRows,
                    findCommonType(listOf(lhsType, rhsType.elementType)) as Type.Float,
                )
            } else if (lhsType is Type.Matrix && rhsType is Type.Scalar) {
                Type.Matrix(
                    lhsType.numCols,
                    lhsType.numRows,
                    findCommonType(listOf(lhsType.elementType, rhsType)) as Type.Float,
                )
            } else {
                TODO("$operator not supported for $lhsType and $rhsType")
            }

        BinaryOperator.EQUAL_EQUAL, BinaryOperator.NOT_EQUAL ->
            when (lhsType) {
                is Type.Scalar -> Type.Bool
                is Type.Vector -> Type.Vector(width = lhsType.width, elementType = Type.Bool)
                else -> throw RuntimeException("== operator is only supported for scalar and vector types")
            }

        BinaryOperator.SHORT_CIRCUIT_AND, BinaryOperator.SHORT_CIRCUIT_OR ->
            if (lhsType != Type.Bool || rhsType != Type.Bool) {
                throw RuntimeException("Short circuit && and || operators require bool arguments")
            } else {
                Type.Bool
            }

        BinaryOperator.BINARY_AND, BinaryOperator.BINARY_OR, BinaryOperator.BINARY_XOR ->
            if (rhsType.isAbstractionOf(lhsType)) {
                lhsType
            } else if (lhsType.isAbstractionOf(rhsType)) {
                rhsType
            } else {
                throw RuntimeException("Unsupported types for bitwise operation")
            }

        BinaryOperator.SHIFT_LEFT, BinaryOperator.SHIFT_RIGHT -> lhsType
    }
}

private fun resolveTypeOfVectorValueConstructor(
    expression: Expression.VectorValueConstructor,
    resolverState: ResolverState,
): Type.Vector {
    val elementType: Type.Scalar =
        expression.elementType?.let {
            resolveTypeDecl(it, resolverState) as Type.Scalar
        } ?: if (expression.args.isEmpty()) {
            Type.AbstractInteger
        } else {
            var candidateElementType: Type.Scalar? = null
            for (arg in expression.args) {
                var elementTypeForArg = resolverState.resolvedEnvironment.typeOf(arg).asStoreTypeIfReference()
                when (elementTypeForArg) {
                    is Type.Scalar -> {
                        // Nothing to do
                    }
                    is Type.Vector -> {
                        elementTypeForArg = elementTypeForArg.elementType
                    }
                    else -> {
                        throw RuntimeException("A vector may only be constructed from vectors and scalars")
                    }
                }
                if (candidateElementType == null || candidateElementType.isAbstractionOf(elementTypeForArg)) {
                    candidateElementType = elementTypeForArg
                } else if (!elementTypeForArg.isAbstractionOf(candidateElementType)) {
                    throw RuntimeException("Vector constructed from incompatible mix of element types")
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
        expression.elementType?.let {
            resolveTypeDecl(it, resolverState) as Type.Float
        } ?: run {
            var candidateElementType: Type.Scalar? = null
            for (arg in expression.args) {
                var elementTypeForArg = resolverState.resolvedEnvironment.typeOf(arg).asStoreTypeIfReference()
                when (elementTypeForArg) {
                    is Type.Float -> {
                        // Nothing to do
                    }
                    is Type.AbstractInteger -> elementTypeForArg = Type.AbstractFloat
                    is Type.Vector -> {
                        elementTypeForArg = elementTypeForArg.elementType
                    }
                    is Type.Matrix -> {
                        elementTypeForArg = elementTypeForArg.elementType
                    }
                    else -> {
                        throw RuntimeException("A matrix may only be constructed from matrices, vectors and scalars")
                    }
                }
                if (candidateElementType == null || candidateElementType.isAbstractionOf(elementTypeForArg)) {
                    candidateElementType = (elementTypeForArg as Type.Scalar) // Kotlin typechecker bug? This "as" should not be needed.
                } else if (!elementTypeForArg.isAbstractionOf(candidateElementType)) {
                    throw RuntimeException("Matrix constructed from incompatible mix of element types")
                }
            }
            when (candidateElementType) {
                is Type.Float -> candidateElementType
                is Type.AbstractInteger -> Type.AbstractFloat
                else -> throw RuntimeException("Invalid types provided to matrix constructor")
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
            throw RuntimeException("Cannot work out element type of empty array constructor")
        } else {
            findCommonType(expression.args, resolverState)
        }
    val elementCount: Int =
        expression.elementCount?.let {
            evaluateToInt(expression.elementCount, resolverState)
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
                // TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/37) go through these and check which ones support
                //  abstract types. Those that need concretisation should be moved to the next case.
                "abs", "acos", "acosh", "asin", "asinh", "atan", "atanh", "ceil", "cos", "cosh", "degrees", "dpdx",
                "dpdxCoarse", "dpdxFine", "dpdy", "dpdyCoarse", "dpdyFine", "exp", "exp2", "floor", "fract", "fwidth",
                "fwidthCoarse", "fwidthFine", "inverseSqrt", "log", "log2", "normalize", "quantizeToF16", "radians",
                "round", "saturate", "sign", "sin", "sinh", "sqrt", "tan", "tanh", "trunc",
                -> {
                    if (functionCallExpression.args.size != 1) {
                        throw RuntimeException("$calleeName requires one argument")
                    } else {
                        resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0])
                    }
                }
                // 1-argument homogeneous functions with return type same as concretisation of argument type
                "reverseBits",
                -> {
                    if (functionCallExpression.args.size != 1) {
                        throw RuntimeException("$calleeName requires one argument")
                    } else {
                        defaultConcretizationOf(resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0]))
                    }
                }
                // 2-argument homogeneous functions with return type same as argument type
                // TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/37): go through these and check which ones support
                //  abstract types. Those that don't need concretisation
                "atan2", "max", "min", "pow", "reflect", "step" ->
                    if (functionCallExpression.args.size != 2) {
                        throw RuntimeException("$calleeName requires two arguments")
                    } else {
                        findCommonType(functionCallExpression.args, resolverState)
                    }
                // 3-argument homogeneous functions with return type same as argument type
                "clamp", "faceForward", "fma", "smoothstep" ->
                    if (functionCallExpression.args.size != 3) {
                        throw RuntimeException("$calleeName requires three arguments")
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
                        Type.AbstractInteger -> throw RuntimeException("An atomic integer should not have an abstract target type")
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
                        throw RuntimeException("bitcast requires a template parameter for the target type")
                    }
                    resolveTypeDecl(functionCallExpression.templateParameter, resolverState)
                }
                "countLeadingZeros", "countOneBits", "countTrailingZeros" -> {
                    if (functionCallExpression.args.size != 1) {
                        throw RuntimeException("$calleeName requires one argument")
                    } else {
                        defaultConcretizationOf(resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0]))
                    }
                }
                "cross" -> {
                    if (functionCallExpression.args.size != 2) {
                        throw RuntimeException("cross builtin takes two arguments")
                    }
                    val arg1Type = resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0]).asStoreTypeIfReference()
                    val arg2Type = resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[1]).asStoreTypeIfReference()
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
                    val argType = resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0]).asStoreTypeIfReference()
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
                    val commonType = findCommonType(functionCallExpression.args, resolverState).asStoreTypeIfReference()
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
                    val commonType = findCommonType(functionCallExpression.args, resolverState).asStoreTypeIfReference()
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
                        throw RuntimeException("$calleeName requires one argument")
                    } else {
                        defaultConcretizationOf(resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0]))
                    }
                "frexp" -> {
                    if (functionCallExpression.args.size != 1) {
                        throw RuntimeException("frexp requires one argument")
                    }
                    when (val argType = resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0]).asStoreTypeIfReference()) {
                        Type.F16 -> FrexpResultF16
                        Type.F32 -> FrexpResultF32
                        Type.AbstractFloat, Type.AbstractInteger -> FrexpResultAbstract
                        is Type.Vector -> {
                            when (argType.elementType) {
                                Type.F16 -> {
                                    when (argType.width) {
                                        2 -> FrexpResultVec2F16
                                        3 -> FrexpResultVec3F16
                                        4 -> FrexpResultVec4F16
                                        else -> throw RuntimeException("Bad vector size")
                                    }
                                }
                                Type.F32 -> {
                                    when (argType.width) {
                                        2 -> FrexpResultVec2F32
                                        3 -> FrexpResultVec3F32
                                        4 -> FrexpResultVec4F32
                                        else -> throw RuntimeException("Bad vector size")
                                    }
                                }
                                Type.AbstractFloat -> {
                                    when (argType.width) {
                                        2 -> FrexpResultVec2Abstract
                                        3 -> FrexpResultVec3Abstract
                                        4 -> FrexpResultVec4Abstract
                                        else -> throw RuntimeException("Bad vector size")
                                    }
                                }
                                else -> throw RuntimeException("Unexpected vector element type of frexp vector argument")
                            }
                        }
                        else -> throw RuntimeException("Unexpected type of frexp argument")
                    }
                }
                "insertBits" -> {
                    if (functionCallExpression.args.size != 4) {
                        throw RuntimeException("$calleeName requires three arguments")
                    } else {
                        defaultConcretizationOf(findCommonType(functionCallExpression.args.dropLast(2), resolverState))
                    }
                }
                "ldexp" -> {
                    if (functionCallExpression.args.size != 2) {
                        throw RuntimeException("$calleeName requires two arguments")
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
                    when (val argType = resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0]).asStoreTypeIfReference()) {
                        is Type.Float -> argType
                        is Type.Vector -> argType.elementType
                        else -> throw RuntimeException("Unsupported argument type for length builtin function")
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
                        throw RuntimeException("modf requires one argument")
                    }
                    when (val argType = resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0]).asStoreTypeIfReference()) {
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
                                        else -> throw RuntimeException("Bad vector size")
                                    }
                                }
                                Type.F32 -> {
                                    when (argType.width) {
                                        2 -> ModfResultVec2F32
                                        3 -> ModfResultVec3F32
                                        4 -> ModfResultVec4F32
                                        else -> throw RuntimeException("Bad vector size")
                                    }
                                }
                                Type.AbstractFloat -> {
                                    when (argType.width) {
                                        2 -> ModfResultVec2Abstract
                                        3 -> ModfResultVec3Abstract
                                        4 -> ModfResultVec4Abstract
                                        else -> throw RuntimeException("Bad vector size")
                                    }
                                }
                                else -> throw RuntimeException("Unexpected vector element type of modf vector argument")
                            }
                        }
                        else -> throw RuntimeException("Unexpected type of modf argument")
                    }
                }
                "pack4x8snorm", "pack4x8unorm", "pack4xI8", "pack4xU8", "pack4xI8Clamp", "pack4xU8Clamp",
                "pack2x16snorm", "pack2x16unorm", "pack2x16float",
                -> Type.U32
                "select" -> {
                    if (functionCallExpression.args.size != 3) {
                        throw RuntimeException("select requires three arguments")
                    } else {
                        findCommonType(functionCallExpression.args.dropLast(1), resolverState)
                    }
                }
                "textureDimensions" -> {
                    if (functionCallExpression.args.size !in 1..2) {
                        throw RuntimeException("textureDimensions requires two arguments")
                    }
                    val textureType = resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0]).asStoreTypeIfReference()
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
                    when (resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0]).asStoreTypeIfReference()) {
                        Type.Texture.Depth2D, Type.Texture.DepthCube, Type.Texture.Depth2DArray, Type.Texture.DepthCubeArray ->
                            Type.Vector(
                                4,
                                Type.F32,
                            )
                        else -> {
                            when (
                                val arg2Type =
                                    resolverState.resolvedEnvironment
                                        .typeOf(
                                            functionCallExpression.args[1],
                                        ).asStoreTypeIfReference()
                            ) {
                                is Type.Texture.Sampled -> Type.Vector(4, arg2Type.sampledType)
                                else -> throw RuntimeException("$calleeName requires a suitable texture as its first or second argument")
                            }
                        }
                    }
                }
                "textureGatherCompare" -> Type.Vector(4, Type.F32)
                "textureLoad" -> {
                    if (functionCallExpression.args.isEmpty()) {
                        throw RuntimeException("textureLoad requires a first argument of texture type")
                    }
                    val textureArg = resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0]).asStoreTypeIfReference()
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
                    if (functionCallExpression.args.isEmpty()) {
                        throw RuntimeException("Not enough arguments provided to $calleeName")
                    } else {
                        when (
                            val textureType =
                                resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0]).asStoreTypeIfReference()
                        ) {
                            is Type.Texture.Sampled ->
                                if (textureType.sampledType is Type.F32) {
                                    Type.Vector(4, Type.F32)
                                } else {
                                    throw RuntimeException("Incorrect sample type used with $calleeName")
                                }
                            Type.Texture.Depth2D, Type.Texture.Depth2DArray, Type.Texture.DepthCube, Type.Texture.DepthCubeArray -> Type.F32
                            else -> throw RuntimeException("First argument to $calleeName must be a suitable texture")
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
                    val arg1Type = resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0]).asStoreTypeIfReference()
                    if (arg1Type is Type.Matrix) {
                        Type.Matrix(
                            numCols = arg1Type.numRows,
                            numRows = arg1Type.numCols,
                            elementType = arg1Type.elementType,
                        )
                    } else {
                        throw RuntimeException("$calleeName requires a matrix argument")
                    }
                }
                "unpack4x8snorm", "unpack4x8unorm", "unpack2x16snorm", "unpack2x16unorm", "unpack2x16float" ->
                    Type.Vector(
                        4,
                        Type.F32,
                    )
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
                        throw RuntimeException("workgroupUniformLoad requires one argument")
                    }
                    val argType = resolverState.resolvedEnvironment.typeOf(functionCallExpression.args[0])
                    if (argType !is Type.Pointer) {
                        throw RuntimeException("workgroupUniformLoad requires a pointer argument")
                    }
                    argType.pointeeType
                }
                else -> TODO("Unsupported builtin function $calleeName")
            }
        is ScopeEntry.Function ->
            scopeEntry.type.returnType
                ?: throw RuntimeException(
                    "Call expression used with function ${functionCallExpression.callee}, which does not return a value",
                )
        else -> throw RuntimeException("Function call attempted on unknown callee ${functionCallExpression.callee}")
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
        TexelFormat.RGBA16UNORM -> Type.F32
        TexelFormat.RGBA16SNORM -> Type.F32
        TexelFormat.RGBA16UINT -> Type.U32
        TexelFormat.RGBA16SINT -> Type.I32
        TexelFormat.RGBA16FLOAT -> Type.F32
        TexelFormat.RG8UNORM -> Type.F32
        TexelFormat.RG8SNORM -> Type.F32
        TexelFormat.RG8UINT -> Type.U32
        TexelFormat.RG8SINT -> Type.I32
        TexelFormat.RG16UNORM -> Type.F32
        TexelFormat.RG16SNORM -> Type.F32
        TexelFormat.RG16UINT -> Type.U32
        TexelFormat.RG16SINT -> Type.I32
        TexelFormat.RG16FLOAT -> Type.F32
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
        TexelFormat.R8UNORM -> Type.F32
        TexelFormat.R8SNORM -> Type.F32
        TexelFormat.R8UINT -> Type.U32
        TexelFormat.R8SINT -> Type.I32
        TexelFormat.R16UNORM -> Type.F32
        TexelFormat.R16SNORM -> Type.F32
        TexelFormat.R16UINT -> Type.U32
        TexelFormat.R16SINT -> Type.I32
        TexelFormat.R16FLOAT -> Type.F32
        TexelFormat.RGB10A2UNORM -> Type.F32
        TexelFormat.RGB10A2UINT -> Type.U32
        TexelFormat.RG11B10UFLOAT -> Type.F32
    }

private fun Type.isAbstractionOf(maybeConcretizedVersion: Type): Boolean =
    if (this == maybeConcretizedVersion || (this is Type.Reference && maybeConcretizedVersion.isAbstractionOf(this.storeType))) {
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
        is Type.Matrix ->
            Type.Matrix(
                type.numCols,
                type.numRows,
                defaultConcretizationOf(type.elementType) as Type.Float,
            )
        is Type.Array ->
            Type.Array(
                elementType = defaultConcretizationOf(type.elementType),
                elementCount = type.elementCount,
            )
        else -> type
    }

sealed class EvaluatedValue {
    class IntIndexed(
        val mapping: (Int) -> EvaluatedValue,
    ) : EvaluatedValue()

    class NameIndexed(
        val mapping: (String) -> EvaluatedValue,
    ) : EvaluatedValue()

    class Integer(
        val value: Int,
    ) : EvaluatedValue()
}

// TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/36): Expression evaluation is in a prototypical state; a number
//  of known issues are discussed below
private fun evaluate(
    expression: Expression,
    resolverState: ResolverState,
): EvaluatedValue =
    when (expression) {
        is Expression.IntLiteral ->
            EvaluatedValue.Integer(
                if (expression.text.endsWith("u") || expression.text.endsWith("i")) {
                    expression.text.substring(0, expression.text.length - 1).toInt()
                } else {
                    expression.text.toInt()
                },
            )
        is Expression.IndexLookup ->
            (evaluate(expression.target, resolverState) as EvaluatedValue.IntIndexed).mapping(
                (evaluate(expression.index, resolverState) as EvaluatedValue.Integer).value,
            )
        is Expression.ArrayValueConstructor -> {
            val arrayType = resolverState.resolvedEnvironment.typeOf(expression) as Type.Array
            if (arrayType.elementCount == null) {
                throw RuntimeException("Constant evaluation encountered array with non-constant size")
            }
            if (expression.args.isEmpty()) {
                TODO()
            } else if (expression.args.size == arrayType.elementCount) {
                EvaluatedValue.IntIndexed(mapping = { x -> evaluate(expression.args[x], resolverState) })
            } else {
                TODO()
            }
        }
        is Expression.Identifier -> {
            when (val scopeEntry = resolverState.currentScope.getEntry(expression.name)) {
                // TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/36): Avoid re-evaluating global constants,
                //  and/or handle the problem that the resolver state needed to evaluate the global constant would
                //  really be global scope.
                is ScopeEntry.GlobalConstant -> evaluate(scopeEntry.astNode.initializer, resolverState)
                is ScopeEntry.GlobalOverride -> {
                    scopeEntry.astNode.initializer?.let { evaluate(it, resolverState) }
                        ?: throw UnsupportedOperationException(
                            "The use of override expressions without initializers is not supported in expression evaluation",
                        )
                }
                else -> throw IllegalArgumentException("Inappropriate declaration used in constant expression: ${expression.name}")
            }
        }
        is Expression.Paren -> evaluate(expression.target, resolverState)
        is Expression.Binary -> {
            val lhs = evaluate(expression.lhs, resolverState)
            val rhs = evaluate(expression.lhs, resolverState)
            if (lhs !is EvaluatedValue.Integer || rhs !is EvaluatedValue.Integer) {
                TODO("Evaluation of arithmetic on non-integer values is not supported")
            }
            when (expression.operator) {
                // TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/36): These do not take account of signedness.
                BinaryOperator.TIMES -> {
                    EvaluatedValue.Integer(lhs.value * rhs.value)
                }
                BinaryOperator.SHIFT_LEFT -> {
                    EvaluatedValue.Integer(lhs.value.shl(rhs.value))
                }
                else -> TODO("${expression.operator}")
            }
        }
        else -> TODO("$expression")
    }

private fun evaluateToInt(
    expression: Expression,
    resolverState: ResolverState,
): Int = (evaluate(expression, resolverState) as EvaluatedValue.Integer).value

private fun isSwizzle(memberName: String): Boolean =
    memberName.length in (2..4) &&
        (memberName.all { it in setOf('x', 'y', 'z', 'w') } || memberName.all { it in setOf('r', 'g', 'b', 'a') })

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
                throw RuntimeException("No common type found")
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
                        "vec2f" -> Type.Vector(2, Type.F32)
                        "vec3f" -> Type.Vector(3, Type.F32)
                        "vec4f" -> Type.Vector(4, Type.F32)
                        "vec2i" -> Type.Vector(2, Type.I32)
                        "vec3i" -> Type.Vector(3, Type.I32)
                        "vec4i" -> Type.Vector(4, Type.I32)
                        "vec2u" -> Type.Vector(2, Type.U32)
                        "vec3u" -> Type.Vector(3, Type.U32)
                        "vec4u" -> Type.Vector(4, Type.U32)
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
                        else -> throw UnsupportedOperationException("Unknown typed declaration ${typeDecl.name}")
                    }
                }
                else -> {
                    throw IllegalArgumentException(
                        "Non-type declaration associated with ${typeDecl.name}, which is used where a type is required",
                    )
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
            when (val targetType = resolveTypeDecl(typeDecl.targetType, resolverState)) {
                Type.I32 -> Type.AtomicI32
                Type.U32 -> Type.AtomicU32
                else -> throw IllegalArgumentException("Inappropriate target type $targetType for atomic type")
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
                else -> throw IllegalArgumentException("texture_multisampled_2d requires a scalar sampler type")
            }
        }
        is TypeDecl.TextureSampled1D -> {
            when (val sampledType = resolveTypeDecl(typeDecl.sampledType, resolverState)) {
                is Type.Scalar -> Type.Texture.Sampled1D(sampledType)
                else -> throw IllegalArgumentException("texture_1d requires a scalar sampler type")
            }
        }
        is TypeDecl.TextureSampled2D -> {
            when (val sampledType = resolveTypeDecl(typeDecl.sampledType, resolverState)) {
                is Type.Scalar -> Type.Texture.Sampled2D(sampledType)
                else -> throw IllegalArgumentException("texture_2d requires a scalar sampler type")
            }
        }
        is TypeDecl.TextureSampled2DArray -> {
            when (val sampledType = resolveTypeDecl(typeDecl.sampledType, resolverState)) {
                is Type.Scalar -> Type.Texture.Sampled2DArray(sampledType)
                else -> throw IllegalArgumentException("texture_2d_array requires a scalar sampler type")
            }
        }
        is TypeDecl.TextureSampled3D -> {
            when (val sampledType = resolveTypeDecl(typeDecl.sampledType, resolverState)) {
                is Type.Scalar -> Type.Texture.Sampled3D(sampledType)
                else -> throw IllegalArgumentException("texture_3d requires a scalar sampler type")
            }
        }
        is TypeDecl.TextureSampledCube -> {
            when (val sampledType = resolveTypeDecl(typeDecl.sampledType, resolverState)) {
                is Type.Scalar -> Type.Texture.SampledCube(sampledType)
                else -> throw IllegalArgumentException("texture_cube requires a scalar sampler type")
            }
        }
        is TypeDecl.TextureSampledCubeArray -> {
            when (val sampledType = resolveTypeDecl(typeDecl.sampledType, resolverState)) {
                is Type.Scalar -> Type.Texture.SampledCubeArray(sampledType)
                else -> throw IllegalArgumentException("texture_cube_array requires a scalar sampler type")
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
    functionDecl.attributes.forEach {
        resolveAstNode(it, resolverState)
    }

    functionDecl.parameters.forEach { parameter ->
        parameter.attributes.forEach {
            resolveAstNode(it, resolverState)
        }
    }

    functionDecl.returnAttributes.forEach {
        resolveAstNode(it, resolverState)
    }

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
    resolverState.maybeWithScope(true) {
        functionDecl.parameters.forEachIndexed { index, parameterDecl ->
            resolverState.currentScope.addEntry(
                parameterDecl.name,
                ScopeEntry.Parameter(
                    astNode = parameterDecl,
                    type = functionScopeEntry.type.argTypes[index],
                ),
            )
        }
        functionDecl.body.statements.forEach {
            resolveAstNode(it, resolverState)
        }
        resolverState.resolvedEnvironment.recordScopeAvailableAtEndOfCompound(functionDecl.body, resolverState.currentScope.shallowCopy())
    }
}

fun resolve(tu: TranslationUnit): ResolvedEnvironment {
    val (topLevelNameDependencies, nameToDecl) = collectTopLevelNameDependencies(tu)
    val orderedGlobalDecls = orderGlobalDeclNames(topLevelNameDependencies)

    val resolverState =
        ResolverState()

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
