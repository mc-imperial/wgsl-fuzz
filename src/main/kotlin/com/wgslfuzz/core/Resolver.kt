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

import kotlin.collections.set

sealed interface ScopeEntry {
    val astNode: AstNode
    val declName: String
        get() =
            when (this) {
                is GlobalConstant -> this.astNode.name
                is GlobalOverride -> this.astNode.name
                is GlobalVariable -> this.astNode.name
                is LocalValue -> this.astNode.name
                is LocalVariable -> this.astNode.name
                is Parameter -> this.astNode.name
                is Struct -> this.astNode.name
                is TypeAlias -> this.astNode.name
                is Function -> this.astNode.name
            }

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
    fun getEntry(name: String): ScopeEntry?

    /**
     * Returns all the entries that can be accessible from this scope.
     * If variable shadowing occurs in the scope it will not return the shadowed variables since they are not
     * accessible.
     */
    fun getAllEntries(): List<ScopeEntry>
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

/*
 * The arrows in the diagram are the previous pointers in the class
 * Each node is the line: level: <lexical graphic scope>, <scope entry>
 * +----------------------+--------------------------------------------------------------------------------------------+
 * |                      |         null                                                                               |
 * |                      |          ^                                                                                 |
 * |                      |          |                                                                                 |
 * |                      |   level: 0, null                                                                           |
 * |                      |          ^                                                                                 |
 * |                      |          |                                                                                 |
 * | var x = 0;           |   level: 0, x: i32                                                                         |
 * |                      |          ^                                                                                 |
 * |                      |          |                                                                                 |
 * | let y = 0;           |   level: 0, y: i32 <-----------+--------------------------------+                          |
 * |                      |          ^                     |                                |                          |
 * | if (x == 1) {        |          |              level: 1, null                          |                          |
 * |    x = 0;            |          |     (Corresponds to if statement scope)              |                          |
 * | } else {             |          |                                               level: 1, null                    |
 * |                      |          |                                    (Corresponds to else statement scope)        |
 * |                      |          |                                                      ^                          |
 * |                      |          |                                                      |                          |
 * |    let y = 1;        |          |                                               level: 1, y: i32                  |
 * |    x += y;           |          |                                                                                 |
 * | }                    |          |                                                                                 |
 * | let z: f32 = 0;      |    level 0: z: f32                                                                         |
 * +----------------------+--------------------------------------------------------------------------------------------+
 */
private class ScopeImpl(
    private val previous: ScopeImpl? = null,
    // Levels corresponds to the lexical scope of the scopeEntry
    // 0 is the global scope. 1 is a level below global scope and so on.
    // Note: name shadowing is allowed at different levels but is not allowed at the same level.
    private val level: Int = 0,
    // scopeEntry is nullable as we need a way of describing an empty scope at a particular level. Hence, the need
    // for the ability to create a "dud" scope. An example can be seen in the above diagram with the node representing
    // if (x == 1) scope having nothing in its level
    private val scopeEntry: Pair<String, ScopeEntry>? = null,
) : Scope {
    override fun getEntry(name: String): ScopeEntry? =
        scopeSequence()
            .map { it.scopeEntry }
            .filterNotNull()
            .firstOrNull { it.first == name }
            ?.second

    override fun getAllEntries(): List<ScopeEntry> =
        scopeSequence()
            .map { it.scopeEntry }
            .filterNotNull()
            .distinctBy { it.first }
            .map { it.second }
            .toList()

    fun pushScopeLevel(): ScopeImpl = ScopeImpl(previous = this, level = level + 1)

    fun popScopeLevel(): ScopeImpl? = scopeSequence().firstOrNull { it.level != this.level }

    fun addEntry(
        name: String,
        scopeEntry: ScopeEntry,
    ): ScopeImpl =
        if (existInLocalScope(name)) {
            throw IllegalArgumentException("An entry for $name already exists in the current scope.")
        } else {
            ScopeImpl(
                previous = this,
                level = level,
                scopeEntry = name to scopeEntry,
            )
        }

    private fun existInLocalScope(name: String): Boolean =
        scopeSequence()
            .takeWhile { it.level == this.level }
            .any { it.scopeEntry != null && it.scopeEntry.first == name }

    private fun scopeSequence(): Sequence<ScopeImpl> = generateSequence(this) { it.previous }
}

private class ResolvedEnvironmentImpl(
    override var globalScope: Scope,
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

private fun orderGlobalDeclNames(topLevelNameDependencies: Map<String, Set<String>>): List<String> {
    val toProcess = mutableMapOf<String, MutableSet<String>>()
    for (entry in topLevelNameDependencies) {
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
    var currentScope: ScopeImpl = ScopeImpl()

    val resolvedEnvironment: ResolvedEnvironmentImpl = ResolvedEnvironmentImpl(currentScope)

    val ancestorsStack: MutableList<AstNode> = mutableListOf()

    fun maybeWithScope(
        newScopeRequired: Boolean,
        action: () -> Unit,
    ) {
        if (newScopeRequired) {
            currentScope = currentScope.pushScopeLevel()
        }
        action()
        if (newScopeRequired) {
            currentScope = currentScope.popScopeLevel()!!
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
        resolverState.resolvedEnvironment.recordScopeAvailableBeforeStatement(
            node,
            resolverState.currentScope,
        )
    }

    val parentNode = resolverState.ancestorsStack.firstOrNull()
    resolverState.maybeWithScope(nodeIntroducesNewScope(node, parentNode)) {
        resolverState.ancestorsStack.addFirst(node)
        traverse(::resolveAstNode, node, resolverState)
        if (node is Statement.Compound) {
            resolverState.resolvedEnvironment.recordScopeAvailableAtEndOfCompound(
                node,
                resolverState.currentScope,
            )
        }
        resolverState.ancestorsStack.removeFirst()
    }

    when (node) {
        is GlobalDecl.TypeAlias -> {
            resolverState.currentScope =
                resolverState.currentScope.addEntry(
                    node.name,
                    ScopeEntry.TypeAlias(
                        node,
                        node.typeDecl.toType(resolverState.currentScope, resolverState.resolvedEnvironment),
                    ),
                )
        }
        is GlobalDecl.Variable -> resolveGlobalVariable(node, resolverState)
        is GlobalDecl.Constant -> {
            resolverState.currentScope =
                resolverState.currentScope.addEntry(
                    node.name,
                    ScopeEntry.GlobalConstant(
                        astNode = node,
                        type =
                            node.typeDecl?.let {
                                it.toType(resolverState.currentScope, resolverState.resolvedEnvironment)
                            } ?: resolverState.resolvedEnvironment.typeOf(node.initializer),
                    ),
                )
        }
        is GlobalDecl.Override -> {
            resolverState.currentScope =
                resolverState.currentScope.addEntry(
                    node.name,
                    ScopeEntry.GlobalOverride(
                        astNode = node,
                        type =
                            node.typeDecl?.let {
                                it.toType(resolverState.currentScope, resolverState.resolvedEnvironment)
                            } ?: resolverState.resolvedEnvironment.typeOf(node.initializer!!),
                    ),
                )
        }
        is GlobalDecl.Struct -> {
            resolverState.currentScope =
                resolverState.currentScope.addEntry(
                    node.name,
                    ScopeEntry.Struct(
                        astNode = node,
                        type =
                            Type.Struct(
                                name = node.name,
                                members =
                                    node.members.map {
                                        it.name to
                                            it.typeDecl.toType(resolverState.currentScope, resolverState.resolvedEnvironment)
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
                    node.typeDecl.toType(resolverState.currentScope, resolverState.resolvedEnvironment)
                } ?: resolverState.resolvedEnvironment.typeOf(node.initializer)
            if (type.isAbstract()) {
                type = defaultConcretizationOf(type)
            }
            resolverState.currentScope =
                resolverState.currentScope.addEntry(
                    node.name,
                    ScopeEntry.LocalValue(node, type),
                )
        }
        is Statement.Variable -> resolveLocalVariable(node, resolverState)
        is Expression ->
            resolverState.resolvedEnvironment.recordType(
                node,
                node.toType(resolverState.currentScope, resolverState.resolvedEnvironment),
            )
        is LhsExpression ->
            resolverState.resolvedEnvironment.recordType(
                node,
                node.toType(resolverState.currentScope, resolverState.resolvedEnvironment),
            )
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
                node.typeDecl.toType(resolverState.currentScope, resolverState.resolvedEnvironment)
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
    resolverState.currentScope =
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
            node.typeDecl.toType(resolverState.currentScope, resolverState.resolvedEnvironment)
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

    resolverState.currentScope =
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

private fun defaultAccessModeOf(addressSpace: AddressSpace): AccessMode =
    when (addressSpace) {
        AddressSpace.FUNCTION -> AccessMode.READ_WRITE
        AddressSpace.PRIVATE -> AccessMode.READ_WRITE
        AddressSpace.WORKGROUP -> AccessMode.READ_WRITE
        AddressSpace.UNIFORM -> AccessMode.READ
        AddressSpace.STORAGE -> AccessMode.READ
        AddressSpace.HANDLE -> AccessMode.READ
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
fun evaluate(
    expression: Expression,
    scope: Scope,
    resolvedEnvironment: ResolvedEnvironment,
): EvaluatedValue =
    when (expression) {
        is Expression.IntLiteral ->
            EvaluatedValue.Integer(
                if (expression.text.endsWith("u") || expression.text.endsWith("i")) {
                    expression.text.dropLast(1).toInt()
                } else {
                    expression.text.toInt()
                },
            )
        is Expression.IndexLookup ->
            (evaluate(expression.target, scope, resolvedEnvironment) as EvaluatedValue.IntIndexed).mapping(
                (evaluate(expression.index, scope, resolvedEnvironment) as EvaluatedValue.Integer).value,
            )
        is Expression.ArrayValueConstructor -> {
            val arrayType = resolvedEnvironment.typeOf(expression) as Type.Array
            if (arrayType.elementCount == null) {
                throw RuntimeException("Constant evaluation encountered array with non-constant size")
            }
            if (expression.args.isEmpty()) {
                TODO()
            } else if (expression.args.size == arrayType.elementCount) {
                EvaluatedValue.IntIndexed(mapping = { x -> evaluate(expression.args[x], scope, resolvedEnvironment) })
            } else {
                TODO()
            }
        }
        is Expression.Identifier -> {
            when (val scopeEntry = scope.getEntry(expression.name)) {
                // TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/36): Avoid re-evaluating global constants,
                //  and/or handle the problem that the resolver state needed to evaluate the global constant would
                //  really be global scope.
                is ScopeEntry.GlobalConstant -> evaluate(scopeEntry.astNode.initializer, scope, resolvedEnvironment)
                is ScopeEntry.GlobalOverride -> {
                    scopeEntry.astNode.initializer?.let { evaluate(it, scope, resolvedEnvironment) }
                        ?: throw UnsupportedOperationException(
                            "The use of override expressions without initializers is not supported in expression evaluation",
                        )
                }
                else -> throw IllegalArgumentException("Inappropriate declaration used in constant expression: ${expression.name}")
            }
        }
        is Expression.Paren -> evaluate(expression.target, scope, resolvedEnvironment)
        is Expression.Binary -> {
            val lhs = evaluate(expression.lhs, scope, resolvedEnvironment)
            val rhs = evaluate(expression.rhs, scope, resolvedEnvironment)
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
                BinaryOperator.DIVIDE -> {
                    EvaluatedValue.Integer(lhs.value / rhs.value)
                }
                else -> TODO("${expression.operator}")
            }
        }
        is AugmentedExpression.IdentityOperation -> evaluate(expression.originalExpression, scope, resolvedEnvironment)
        else -> TODO("$expression")
    }

fun evaluateToInt(
    expression: Expression,
    scope: Scope,
    resolvedEnvironment: ResolvedEnvironment,
): Int =
    (evaluate(expression, scope, resolvedEnvironment) as? EvaluatedValue.Integer)?.value
        ?: throw IllegalArgumentException("Expression $expression to int")

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
        resolveAstNode(parameter.typeDecl, resolverState)
    }

    functionDecl.returnAttributes.forEach {
        resolveAstNode(it, resolverState)
    }

    val functionType =
        FunctionType(
            functionDecl.parameters.map {
                it.typeDecl.toType(resolverState.currentScope, resolverState.resolvedEnvironment)
            },
            functionDecl.returnType?.let {
                it.toType(resolverState.currentScope, resolverState.resolvedEnvironment)
            },
        )
    resolverState.currentScope =
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
            resolverState.currentScope =
                resolverState.currentScope.addEntry(
                    parameterDecl.name,
                    ScopeEntry.Parameter(
                        astNode = parameterDecl,
                        type = functionScopeEntry.type.argTypes[index],
                    ),
                )
        }
        resolverState.resolvedEnvironment.recordScopeAvailableBeforeStatement(
            statement = functionDecl.body,
            scope = resolverState.currentScope,
        )
        functionDecl.body.statements.forEach {
            resolveAstNode(it, resolverState)
        }
        resolverState.resolvedEnvironment.recordScopeAvailableAtEndOfCompound(
            compound = functionDecl.body,
            scope = resolverState.currentScope,
        )
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
    resolverState.resolvedEnvironment.globalScope = resolverState.currentScope
    return resolverState.resolvedEnvironment
}
