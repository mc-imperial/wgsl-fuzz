package com.wgslfuzz.uniformityanalysis

import com.wgslfuzz.analysis.StatementBehaviour
import com.wgslfuzz.analysis.runStatementBehaviourAnalysis
import com.wgslfuzz.core.AccessMode
import com.wgslfuzz.core.AstNode
import com.wgslfuzz.core.Attribute
import com.wgslfuzz.core.AugmentedExpression
import com.wgslfuzz.core.AugmentedStatement
import com.wgslfuzz.core.BinaryOperator
import com.wgslfuzz.core.BuiltinValue
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.GlobalDecl
import com.wgslfuzz.core.LhsExpression
import com.wgslfuzz.core.ResolvedEnvironment
import com.wgslfuzz.core.Scope
import com.wgslfuzz.core.ScopeEntry
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.TranslationUnit
import com.wgslfuzz.core.Type
import com.wgslfuzz.core.asStoreTypeIfReference
import com.wgslfuzz.core.targetIdentifier
import com.wgslfuzz.core.traverse
import com.wgslfuzz.uniformityanalysis.CallSiteTag.CallSiteRequiredToBeUniformError
import com.wgslfuzz.uniformityanalysis.CallSiteTag.Companion.severity
import com.wgslfuzz.uniformityanalysis.ParameterTag.Companion.severity
import com.wgslfuzz.uniformityanalysis.UniformityNode.Companion.createUniformityNode

private val INITIAL_PARAMETER_TAG = ParameterTag.ParameterNoRestriction
private val INITIAL_CALL_SITE_TAG = CallSiteTag.CallSiteNoRestriction

private const val OUTPUT_UNIFORMITY_GRAPH = true

private class UniformityNode private constructor(
    val name: String,
    val astNode: AstNode? = null,
    val edges: MutableList<UniformityNode> = mutableListOf(),
) {
    var visited: Boolean = false
        private set

    fun setVisited() {
        visited = true
    }

    fun setUnvisited() {
        visited = false
    }

    fun addEdges(vararg nodes: UniformityNode) = addEdges(nodes.toList())

    fun addEdges(nodes: List<UniformityNode>) =
        nodes.forEach {
            edges.add(it)
            if (OUTPUT_UNIFORMITY_GRAPH) {
                println("  \"${this.name}\" -> \"${it.name}\"")
            }
        }

    companion object {
        private val existingNames = hashSetOf<String>()

        fun createUniformityNode(name: String): UniformityNode {
            // It only matters if node names are unique when outputting the uniformity graph
            if (OUTPUT_UNIFORMITY_GRAPH) {
                var suffix = 0
                while (existingNames.contains("${name}_$$suffix")) {
                    suffix++
                }
                val uniqueName = "${name}_$$suffix"
                existingNames.add(uniqueName)
                println("  \"$uniqueName\";")
                return UniformityNode(uniqueName)
            }
            return UniformityNode(name = name)
        }
    }
}

private fun <T : Statement> T.variablesDeclaredInScope(): Set<String> {
    val result = HashSet<String>()

    fun getDeclared(
        node: AstNode,
        variableDecls: HashSet<String>,
    ) {
        when (node) {
            is Statement.Variable -> variableDecls.add(node.name)
            else -> traverse(::getDeclared, node, variableDecls)
        }
    }
    traverse(::getDeclared, this, result)
    return result
}

// NOTE: This is pretty much the same as the ScopeStack in tint.
private class ScopeStack {
    private val stack: ArrayDeque<HashMap<String, UniformityNode>> = ArrayDeque()

    init {
        stack.addLast(hashMapOf())
    }

    fun push() {
        stack.addLast(hashMapOf())
    }

    fun pop(): Map<String, UniformityNode> = stack.removeLast()

    fun set(
        name: String,
        node: UniformityNode,
    ) {
        stack.last()[name] = node
    }

    fun get(name: String): UniformityNode? {
        for (i in stack.lastIndex downTo 0) {
            if (stack[i].containsKey(name)) {
                return stack[i][name]
            }
        }
        return null
    }
}

private data class ExpressionAnalysisResult(
    val cfNode: UniformityNode,
    val valueNode: UniformityNode,
)

private enum class Severity {
    Error,
    Warning,
    Info,
}

private enum class CallSiteTag {
    CallSiteRequiredToBeUniformError,
    CallSiteRequiredToBeUniformWarning,
    CallSiteRequiredToBeUniformInfo,
    CallSiteNoRestriction,
    ;

    companion object {
        fun callSiteRequiredToBeUniform(severity: Severity): CallSiteTag =
            when (severity) {
                Severity.Error -> CallSiteRequiredToBeUniformError
                Severity.Warning -> CallSiteRequiredToBeUniformWarning
                Severity.Info -> CallSiteRequiredToBeUniformInfo
            }

        fun isRequiredToBeUniform(callSiteTag: CallSiteTag): Boolean =
            callSiteTag == CallSiteRequiredToBeUniformInfo ||
                callSiteTag == CallSiteRequiredToBeUniformWarning ||
                callSiteTag == CallSiteRequiredToBeUniformError

        fun CallSiteTag.severity(): Severity? =
            when (this) {
                CallSiteRequiredToBeUniformError -> Severity.Error
                CallSiteRequiredToBeUniformWarning -> Severity.Warning
                CallSiteRequiredToBeUniformInfo -> Severity.Info
                CallSiteNoRestriction -> null
            }
    }
}

private enum class FunctionTag {
    ReturnValueMayBeNonUniform,
    NoRestriction,
}

private enum class ParameterTag {
    ParameterRequiredToBeUniformError,
    ParameterRequiredToBeUniformWarning,
    ParameterRequiredToBeUniformInfo,
    ParameterContentsRequiredToBeUniformError,
    ParameterContentsRequiredToBeUniformWarning,
    ParameterContentsRequiredToBeUniformInfo,
    ParameterNoRestriction,
    ;

    companion object {
        fun parameterRequiredToBeUniform(severity: Severity): ParameterTag =
            when (severity) {
                Severity.Error -> ParameterRequiredToBeUniformError
                Severity.Warning -> ParameterRequiredToBeUniformWarning
                Severity.Info -> ParameterRequiredToBeUniformInfo
            }

        fun parameterContentsRequiredToBeUniform(severity: Severity): ParameterTag =
            when (severity) {
                Severity.Error -> ParameterContentsRequiredToBeUniformError
                Severity.Warning -> ParameterContentsRequiredToBeUniformWarning
                Severity.Info -> ParameterContentsRequiredToBeUniformInfo
            }

        fun isRequiredToBeUniform(parameterTag: ParameterTag): Boolean =
            parameterTag == ParameterRequiredToBeUniformInfo ||
                parameterTag == ParameterRequiredToBeUniformWarning ||
                parameterTag == ParameterRequiredToBeUniformError

        fun ParameterTag.severity(): Severity? =
            when (this) {
                ParameterContentsRequiredToBeUniformError,
                ParameterRequiredToBeUniformError,
                -> Severity.Error
                ParameterContentsRequiredToBeUniformWarning,
                ParameterRequiredToBeUniformWarning,
                -> Severity.Warning
                ParameterContentsRequiredToBeUniformInfo,
                ParameterRequiredToBeUniformInfo,
                -> Severity.Info
                ParameterNoRestriction -> null
            }
    }
}

private enum class ParameterReturnTag {
    ParameterReturnContentsRequiredToBeUniform,
    ParameterReturnNoRestriction,
}

private enum class PointerParameterTag {
    PointerParameterMayBeNonUniform,
    PointerParameterNoRestriction,
}

// TODO(JLJ): Think about how to combine this with functionInfo
private data class FunctionTags(
    val functionTag: FunctionTag,
    val callSiteTag: CallSiteTag,
    val paramInfo: List<Pair<ParameterTag, ParameterReturnTag>>,
)

private class FunctionInfo(
    val function: GlobalDecl.Function,
) {
    data class ParamInfo(
        val uniformityNode: UniformityNode,
        val parameterTag: ParameterTag,
        val parameterReturnTag: ParameterReturnTag,
    )

    // The modifiable variables in scope at any point in the program. These are the set of variables that could have
    // been modified in branching control flow and so must be merged.
    val variablesInScope: HashSet<String> = HashSet()

    // Keep track of the node currently representing the uniformity of a variable
    val variableNodes: ScopeStack = ScopeStack()

    // Create special nodes.
    val requiredToBeUniformError = createUniformityNode("RequiredToBeUniformError")
    val requiredToBeUniformWarning = createUniformityNode("RequiredToBeUniformWarning")
    val requiredToBeUniformInfo = createUniformityNode("RequiredToBeUniformInfo")
    val mayBeNonUniform = createUniformityNode("MayBeNonUniform")
    val cfStart = createUniformityNode("CF_start")
    var paramsInfo: List<ParamInfo> =
        function.parameters.map { param ->
            val paramNode = createUniformityNode(param.name)
            variablesInScope.add(param.name)
            variableNodes.set(param.name, paramNode)
            ParamInfo(paramNode, INITIAL_PARAMETER_TAG, ParameterReturnTag.ParameterReturnNoRestriction)
        }
    var valueReturn: UniformityNode? = function.returnType?.let { createUniformityNode("Value_return") }

    // Initialise tags
    var functionTag = FunctionTag.NoRestriction
    var callSiteTag: CallSiteTag = INITIAL_CALL_SITE_TAG

    var nodesReachingContinue: MutableMap<String, MutableSet<UniformityNode>>? = null
    var nodesBreaking: MutableMap<String, MutableSet<UniformityNode>>? = null

    fun functionTags(): FunctionTags =
        FunctionTags(functionTag, callSiteTag, paramsInfo.map { paramInfo -> Pair(paramInfo.parameterTag, paramInfo.parameterReturnTag) })

    // TODO(JLJ): Change param name
    private fun updateNodesReaching(map: MutableMap<String, MutableSet<UniformityNode>>) {
        for (variable in variablesInScope) {
            val variableNode = variableNodes.get(variable)!!
            if (variable !in map) {
                map.put(variable, mutableSetOf(variableNode))
            } else {
                map[variable]?.add(variableNode)
            }
        }
    }

    fun updateNodesReachingContinuing() {
        updateNodesReaching(nodesReachingContinue!!)
    }

    fun updateNodesExitingLoop() {
        updateNodesReaching(nodesBreaking!!)
    }

    fun <T> inNewLoop(action: () -> T): T {
        val oldNodesReachingContinue = nodesReachingContinue
        val oldNodesBreaking = nodesBreaking
        nodesReachingContinue = mutableMapOf()
        nodesBreaking = mutableMapOf()
        val result = action()
        nodesReachingContinue = oldNodesReachingContinue
        nodesBreaking = oldNodesBreaking
        return result
    }

    fun <T> inNewSwitchBranch(action: () -> T): T {
        val oldNodesBreaking = nodesBreaking
        nodesBreaking = mutableMapOf()
        val result = action()
        nodesBreaking = oldNodesBreaking
        return result
    }

    // TODO(JLJ): Add support for pointer parameters

    fun requiredToBeUniform(severity: Severity) =
        when (severity) {
            Severity.Error -> requiredToBeUniformError
            Severity.Warning -> requiredToBeUniformWarning
            Severity.Info -> requiredToBeUniformInfo
        }
}

// This class stores information used for the variable value analysis of loops.
// We need to know which variable nodes may continue to the next loop iteration due to a continue
// and which may exit the loop due to a break.
private class LoopInfo {
    val nodesReachingContinue: MutableMap<String, MutableSet<UniformityNode>> = mutableMapOf()
    val nodesBreaking: MutableMap<String, MutableSet<UniformityNode>> = mutableMapOf()
}

// NOTE: Must be run on the desugared translation unit.
fun runWGSLUniformityGraphAnalysis(
    tu: TranslationUnit,
    environment: ResolvedEnvironment,
) {
    val behaviourMap = runStatementBehaviourAnalysis(tu)

    fun analyseFunction(
        node: AstNode,
        functionInfoMap: FunctionInfoMap,
    ) {
        when (node) {
            is GlobalDecl.Function -> {
                if (OUTPUT_UNIFORMITY_GRAPH) {
                    println("subgraph cluster_${node.name} {")
                    println("  label=${node.name};")
                }

                val functionInfo = FunctionInfo(node)
                // Build the uniformity graph for the current function.
                analyseStatement(
                    functionInfo.cfStart,
                    node.body,
                    functionInfo,
                    functionInfoMap,
                    behaviourMap,
                    environment,
                )
                functionInfoMap[node.name] = functionInfo

                // Traverse the graph to check for uniformity violations.
                traverseGraph(functionInfo)

                if (OUTPUT_UNIFORMITY_GRAPH) {
                    println("}\n")
                }
            }

            else -> null
        }
    }

    if (OUTPUT_UNIFORMITY_GRAPH) {
        println("digraph G {")
        println("rankdir=BT\n")
    }

    traverse(::analyseFunction, tu, mutableMapOf())

    if (OUTPUT_UNIFORMITY_GRAPH) {
        println("}\n")
    }
}

private fun reachableUnvisited(startNode: UniformityNode): Set<UniformityNode> {
    val result = mutableSetOf<UniformityNode>()
    val stack = ArrayDeque<UniformityNode>()
    stack.addLast(startNode)
    startNode.setVisited()
    while (stack.isNotEmpty()) {
        val node = stack.removeFirst()
        node.edges.forEach { reachableNode ->
            if (!reachableNode.visited) {
                reachableNode.setVisited()
                result.add(reachableNode)
                stack.addLast(reachableNode)
            }
        }
    }

    return result
}

private fun traverseGraph(functionInfo: FunctionInfo): Boolean {
    val visited = mutableSetOf<UniformityNode>()

    fun traverse(severity: Severity): Boolean {
        val reachable = reachableUnvisited(functionInfo.requiredToBeUniform(severity))

        // TODO(JLJ): Come back and refactor this
        visited.addAll(reachable)

        if (reachable.contains(functionInfo.mayBeNonUniform)) {
            return false
        }

        if (reachable.contains(functionInfo.cfStart) && functionInfo.callSiteTag == INITIAL_CALL_SITE_TAG) {
            functionInfo.callSiteTag = CallSiteTag.callSiteRequiredToBeUniform(severity)
        }

        functionInfo.paramsInfo =
            functionInfo.paramsInfo.map { paramInfo ->
                if (paramInfo.parameterTag ==
                    INITIAL_PARAMETER_TAG
                ) {
                    paramInfo.copy(parameterTag = ParameterTag.parameterRequiredToBeUniform(severity))
                } else {
                    paramInfo
                }
            }

        // TODO(JLJ): Add support for pointer params
        return true
    }

    // TODO(JLJ): Do not throw an error early. This stops the full dot graph from being output.
    if (!traverse(Severity.Error)) {
        throw IllegalArgumentException("Uniformity Error")
    } else if (!traverse(Severity.Warning)) {
        throw IllegalArgumentException("Uniformity Warning")
    } else if (!traverse(Severity.Info)) {
        throw IllegalArgumentException("Uniformity Info")
    }

    // TODO(JLJ): Mark everything as unvisited
    visited.forEach { it.setUnvisited() }

    if (functionInfo.valueReturn != null) {
        val reachable = reachableUnvisited(functionInfo.valueReturn!!)
        if (reachable.contains(functionInfo.mayBeNonUniform)) {
            functionInfo.functionTag = FunctionTag.ReturnValueMayBeNonUniform
        }

        // TODO(JLJ): It is not clear from the spec if this should be in the above if, check
        functionInfo.paramsInfo =
            functionInfo.paramsInfo.map { paramInfo ->
                paramInfo.copy(parameterReturnTag = ParameterReturnTag.ParameterReturnContentsRequiredToBeUniform)
            }
    }

    // TODO(JLJ): Add Value_return_i_contents

    return true
}

private typealias FunctionInfoMap = MutableMap<String, FunctionInfo>

private fun analyseStatements(
    cf: UniformityNode,
    statements: List<Statement>,
    functionInfo: FunctionInfo,
    functionInfoMap: FunctionInfoMap,
    behaviourMap: Map<Statement, Set<StatementBehaviour>>,
    environment: ResolvedEnvironment,
): UniformityNode {
    var cfNode: UniformityNode = cf
    for (statement in statements) {
        cfNode = analyseStatement(cfNode, statement, functionInfo, functionInfoMap, behaviourMap, environment)

        if (!behaviourMap[statement]!!.contains(StatementBehaviour.NEXT)) {
            break
        }
    }

    return cfNode
}

private fun analyseStatement(
    cf: UniformityNode,
    statement: Statement,
    functionInfo: FunctionInfo,
    functionInfoMap: FunctionInfoMap,
    behaviourMap: Map<Statement, Set<StatementBehaviour>>,
    environment: ResolvedEnvironment,
): UniformityNode =
    when (statement) {
        is Statement.Compound -> {
            val cf =
                analyseStatements(
                    cf,
                    statement.statements,
                    functionInfo,
                    functionInfoMap,
                    behaviourMap,
                    environment,
                )
            val stmtsDeclaredInCompound = statement.variablesDeclaredInScope()
            functionInfo.variablesInScope.removeAll(stmtsDeclaredInCompound)
            cf
        }

        is Statement.Value -> {
            // Continue analysis with the control flow node produced
            val (newCf, valueNode) =
                analyseExpression(
                    cf,
                    statement.initializer,
                    functionInfo,
                    functionInfoMap,
                    environment.scopeAvailableBefore(statement),
                    environment,
                )

            functionInfo.variableNodes.set(statement.name, valueNode)

            newCf
        }

        is Statement.Variable -> {
            // Add variable to the set of in-scope variables.
            functionInfo.variablesInScope.add(statement.name)

            // TODO(JLJ): It is not clear that this is what the spec intends
            val variableNode = createUniformityNode(statement.name)

            // If this variable declaration has an initialiser, analyse the initialising expression
            // and continue with the node produced. Otherwise, continue analysis with the current
            // control flow node.
            if (statement.initializer ==
                null
            ) {
                // Variable Value Analysis: Since the variable doesn't have an initialiser, V(0) is the
                // current control flow context.
                functionInfo.variableNodes.set(statement.name, variableNode)
                variableNode.addEdges(cf)
                // Uniformity Rules for Statements: Return current control flow context
                // current control flow context.
                cf
            } else {
                // Uniformity Rules for Statements: Recursively analyse the initialiser expression.
                val (newCf, valueNode) =
                    analyseExpression(
                        cf,
                        statement.initializer,
                        functionInfo,
                        functionInfoMap,
                        environment.scopeAvailableBefore(statement),
                        environment,
                    )
                // Variable Value Analysis: Use the initialiser expression as the value of the variable going forward.
                functionInfo.variableNodes.set(statement.name, variableNode)
                variableNode.addEdges(valueNode)
                // Uniformity Rules for Statements: continue analysis with the control flow node produced when analysing
                // the initialiser expressions
                newCf
            }
        }

        is Statement.Break -> {
            // Statement Behaviour Analysis: Keep track of the variable uniformity nodes that may exit the loop.
            functionInfo.updateNodesExitingLoop()
            cf
        }
        is Statement.Continue -> {
            // Statement Behaviour Analysis: Keep track of the variable uniformity nodes that may enter the continuing construct.
            functionInfo.updateNodesReachingContinuing()
            cf
        }

        is Statement.If -> {
            // Uniformity Rules for Statements: Analyse the if condition.
            val (_, v) =
                analyseExpression(
                    cf,
                    statement.condition,
                    functionInfo,
                    functionInfoMap,
                    environment.scopeAvailableBefore(statement),
                    environment,
                )

            // Variable Value Analysis (Not in spec): Push on a new scope for analysing the if branch.
            functionInfo.variableNodes.push()
            val cf1: UniformityNode =
                analyseStatement(
                    v,
                    statement.thenBranch,
                    functionInfo,
                    functionInfoMap,
                    behaviourMap,
                    environment,
                )
            // Variable Value Analysis (Not in spec): Pop the added stack to get the variables assigned in
            // the if branch.
            val ifAssignments = functionInfo.variableNodes.pop()

            // Variable Value Analysis (Not in spec): Push on a new scope for analysing the else branch.
            functionInfo.variableNodes.push()
            // Since uniformity analysis is performed on the desugared AST, the else branch will always be non-null
            val cf2: UniformityNode =
                analyseStatement(v, statement.elseBranch!!, functionInfo, functionInfoMap, behaviourMap, environment)

            // Variable Value Analysis (Not in spec): Pop the added stack to get the variables assigned in
            // the else branch.
            val elseAssignments = functionInfo.variableNodes.pop()

            // Variable Value Analysis: Carry forward the uniformity of variables at the end of each branch of an if
            // statement.
            for (variable in functionInfo.variablesInScope) {
                // Create a new node to carry forth the variable value
                val newNode = createUniformityNode("${variable}_after_if")

                // Determine the node representing the uniformity of the variable before the if statement.
                val previousNode = functionInfo.variableNodes.get(variable)!!

                // If the 'if' branch contains behaviour next, i.e it may continue, then determine what node
                // represents the uniformity of the variable after the if.
                if (behaviourMap[statement.thenBranch]!!.contains(StatementBehaviour.NEXT)) {
                    if (ifAssignments[variable] != null && ifAssignments[variable] != previousNode) {
                        // If the node representing the value of the variable in the if branch is not null and not-equal
                        // to the previous node representing the uniformity of the variable, the variable must have been
                        // assigned in the if branch.
                        newNode.addEdges(ifAssignments[variable]!!)
                    } else {
                        // Otherwise, the variable was not updated in the if branch and the uniformity of the variable
                        // will be the same as before the if statement.
                        newNode.addEdges(previousNode)
                    }
                }

                // The else branch is treated identically to the if branch.
                if (behaviourMap[statement.elseBranch]!!.contains(StatementBehaviour.NEXT)) {
                    if (elseAssignments[variable] != null && elseAssignments[variable] != previousNode) {
                        newNode.addEdges(elseAssignments[variable]!!)
                    } else {
                        newNode.addEdges(previousNode)
                    }
                }

                functionInfo.variableNodes.set(variable, newNode)
            }

            if (behaviourMap[statement] != setOf(StatementBehaviour.NEXT)) {
                val cfEnd = createUniformityNode("CFend_if")
                cfEnd.addEdges(cf1, cf2)
                cfEnd
            } else {
                cf
            }
        }

        // Since uniformity analysis is performed on the desugared AST, the rules for `loop {s1}` and
        // `loop {s1 continuing {s2}}` are identical and so can be collapsed into one, as is done here.
        is Statement.Loop -> {
            functionInfo.inNewLoop {
                val variableNodesAtLoopEntry = mutableMapOf<String, UniformityNode>()
                for (variable in functionInfo.variablesInScope) {
                    variableNodesAtLoopEntry[variable] = functionInfo.variableNodes.get(variable)!!
                }

                val newCf = createUniformityNode("CF'_loop_start")
                val cf1 =
                    analyseStatement(newCf, statement.body, functionInfo, functionInfoMap, behaviourMap, environment)

                // Statement Behaviour Analysis: create new nodes for any variables that may continue.
                for ((variable, nodes) in functionInfo.nodesReachingContinue!!.iterator()) {
                    val newNode = createUniformityNode(variable)
                    // Connect the new node to the nodes representing the variable at any continues.
                    nodes.forEach { newNode.addEdges(it) }
                    // Connect the new node to the node representing the variable at the end of the loop body.
                    // This is connecting to Vout(cf1)
                    newNode.addEdges(functionInfo.variableNodes.get(variable)!!)
                    functionInfo.variableNodes.set(variable, newNode)
                }

                // Statement Behaviour Analysis: Delay creating new nodes for these until after the continuing has been
                // processes. They should only feed into Vin(next) (https://www.w3.org/TR/WGSL/#func-var-value-analysis)
                val nodesBreaking = functionInfo.nodesBreaking!!

                // The continuing statement is guaranteed to be non-null because we are operating on the desugared AST.
                val cf2 =
                    analyseStatement(
                        cf1,
                        statement.continuingStatement!!.statements,
                        functionInfo,
                        functionInfoMap,
                        behaviourMap,
                        environment,
                    )

                // NOTE: This deviates from the spec slightly due the layout of the AST.
                if (statement.continuingStatement.breakIfExpr != null) {
                    val (breakIfCf, _) =
                        analyseExpression(
                            cf2,
                            statement.continuingStatement.breakIfExpr,
                            functionInfo,
                            functionInfoMap,
                            environment.scopeAvailableBefore(statement),
                            environment,
                        )
                    newCf.addEdges(cf, breakIfCf)
                } else {
                    newCf.addEdges(cf, cf2)
                }

                // Statement Behaviour Analysis: Connect any variable nodes at loop entry to their value at the end
                // of the continuing construct.
                for ((variable, node) in variableNodesAtLoopEntry) {
                    node.addEdges(functionInfo.variableNodes.get(variable)!!)
                }

                // Statement Behaviour Analysis: create new nodes for any variables that may break.
                for ((variable, nodes) in nodesBreaking) {
                    val newNode = createUniformityNode("${variable}_after_loop")
                    // Connect the new node to the nodes representing the variable at any breaks.
                    nodes.forEach { newNode.addEdges(it) }
                    // Connect the new node to the node representing the variable at the end of the continuing body.
                    newNode.addEdges(functionInfo.variableNodes.get(variable)!!)
                    functionInfo.variableNodes.set(variable, newNode)
                }

                if (behaviourMap[statement] == setOf(StatementBehaviour.NEXT)) cf else newCf
            }
        }

        is Statement.For ->
            throw UnsupportedOperationException("For statements must be desugared to general loops.")

        is Statement.While ->
            throw UnsupportedOperationException("While statements must be desugared to general loops.")

        // NON-STANDARD: the code here is statically unreachable so has no effect on uniformity analysis.
        is AugmentedStatement.DeadCodeFragment -> cf

        // NOTE: There are no rules given for these in the language specification.
        // It seems reasonable that non should be able to affect the uniformity of
        // the following statements.
        is Statement.ConstAssert -> cf
        is Statement.Discard -> cf
        is Statement.Empty -> cf

        is Statement.Assignment -> {
            if (statement.lhsExpression != null) {
                // Uniformity Rules for Statements: Recursively analyse the lhs expression.
                val (cf1, lv) =
                    analyseLhsExpression(
                        cf,
                        statement.lhsExpression,
                        functionInfo,
                        functionInfoMap,
                        environment.scopeAvailableBefore(statement),
                        environment,
                    )
                // Uniformity Rules for Statements: Recursively analyse the rhs expression.
                val (cf2, rv) =
                    analyseExpression(
                        cf1,
                        statement.rhs,
                        functionInfo,
                        functionInfoMap,
                        environment.scopeAvailableBefore(statement),
                        environment,
                    )

                // Uniformity Rules for Statements: The uniformity of the lhs depends on the uniformity of the rhs
                lv.addEdges(rv)

                // Variable Value Analysis: Use lv as the node representing the uniformity of the variable being assigned
                // to on the lhs going forward.
                // TODO: I don't think it is that clear that this should be lv instead of rv. The statement rules say that
                // lv is the result of the value analysis, but the variable value analysis rules don't seem to clarify, they
                // just say Vin(next).
                functionInfo.variableNodes.set(statement.lhsExpression.targetIdentifier().name, lv)

                // Uniformity Rules for Statements: Continue analysis with the control flow after analysis of the lhs
                cf2
            } else {
                analyseExpression(
                    cf,
                    statement.rhs,
                    functionInfo,
                    functionInfoMap,
                    environment.scopeAvailableBefore(statement),
                    environment,
                ).cfNode
            }
        }

        is Statement.Decrement -> TODO()

        is Statement.FunctionCall ->
            analyseFunctionCall(
                cf,
                statement,
                functionInfo,
                functionInfoMap,
                environment.scopeAvailableBefore(statement),
                environment,
            ).cfNode

        is Statement.Increment -> TODO()

        is Statement.Return -> {
            if (statement.expression ==
                null
            ) {
                cf
            } else {
                val (newCfNode, valueNode) = analyseExpression(
                    cf,
                    statement.expression,
                    functionInfo,
                    functionInfoMap,
                    environment.scopeAvailableBefore(statement),
                    environment,
                )

                functionInfo.valueReturn!!.addEdges(valueNode)

                newCfNode
            }
        }

        is Statement.Switch -> {
            // TODO(JLJ): Implement statement behaviour analysis
            val (newCf, v) =
                analyseExpression(
                    cf,
                    statement.expression,
                    functionInfo,
                    functionInfoMap,
                    environment.scopeAvailableBefore(statement),
                    environment,
                )

            val variableNodesAfterSwitch =
                functionInfo.variablesInScope.associateWith { createUniformityNode("${it}_after_switch") }

            val cfNodes =
                statement.clauses.map { clause ->
                    functionInfo.inNewSwitchBranch {
                        functionInfo.variableNodes.push()
                        val result =
                            analyseStatement(
                                v,
                                clause.compoundStatement,
                                functionInfo,
                                functionInfoMap,
                                behaviourMap,
                                environment,
                            )
                        // Variable Value Analysis: Connect the node representing the uniformity after the switch
                        // to the node representing uniformity at the end of a branch.
                        // TODO(JLJ): The number of !! here is gross, fix it.
                        if (behaviourMap[clause.compoundStatement]!!.contains(StatementBehaviour.NEXT) ||
                            behaviourMap[clause.compoundStatement]!!.contains(StatementBehaviour.BREAK)
                        ) {
                            for (variable in variableNodesAfterSwitch.keys) {
                                variableNodesAfterSwitch[variable]!!.addEdges(functionInfo.variableNodes.get(variable)!!)
                                if (behaviourMap[clause.compoundStatement]!!.contains(StatementBehaviour.BREAK)) {
                                    variableNodesAfterSwitch[variable]!!.addEdges(functionInfo.nodesBreaking!![variable]!!.toList())
                                }
                            }
                        }

                        functionInfo.variableNodes.pop()
                        result
                    }
                }

            // Variable Value Analysis: Use the new variable nodes representing the effects of the switch going forward.
            for ((variable, nodeAfterSwitch) in variableNodesAfterSwitch) {
                functionInfo.variableNodes.set(variable, nodeAfterSwitch)
            }

            if (behaviourMap[statement] == setOf(StatementBehaviour.NEXT)) {
                cf
            } else {
                val cfEnd = createUniformityNode("CFend_switch")
                cfEnd.addEdges(cfNodes)
                cfEnd
            }
        }
    }

private fun analyseLhsExpression(
    cf: UniformityNode,
    lhsExpression: LhsExpression,
    functionInfo: FunctionInfo,
    functionInfoMap: FunctionInfoMap,
    scope: Scope,
    environment: ResolvedEnvironment,
): ExpressionAnalysisResult =
    when (lhsExpression) {
        is LhsExpression.Identifier -> {
            val resolvedScope = scope.getEntry(lhsExpression.name)

            when (resolvedScope) {
                is ScopeEntry.LocalVariable -> {
                    val result = createUniformityNode("Result_lhs_ident")
                    // Get the node representing this variable produced by statement behaviour analysis.
                    val statementBehaviourAnalysisNode = functionInfo.variableNodes.get(lhsExpression.name)!!

                    result.addEdges(cf, statementBehaviourAnalysisNode)
                    ExpressionAnalysisResult(cf, result)
                }

                is ScopeEntry.GlobalVariable -> {
                    ExpressionAnalysisResult(cf, functionInfo.mayBeNonUniform)
                }

                else -> {
                    val variableNode = functionInfo.variableNodes.get(lhsExpression.name)
                    val valueNode = variableNode ?: cf
                    // TODO: This is not in the spec, but for module scope variables, we don't have a node representing its value so it seem that
                    // returning cf as the value is correct.
                    ExpressionAnalysisResult(cf, valueNode)
                }
            }
        }

        is LhsExpression.IndexLookup -> {
            val (cf1, l1) = analyseLhsExpression(cf, lhsExpression.target, functionInfo, functionInfoMap, scope, environment)
            val (cf2, v2) = analyseExpression(cf1, lhsExpression.index, functionInfo, functionInfoMap, scope, environment)
            l1.addEdges(v2)
            ExpressionAnalysisResult(cf2, l1)
        }

        is LhsExpression.AddressOf -> analyseLhsExpression(cf, lhsExpression.target, functionInfo, functionInfoMap, scope, environment)
        is LhsExpression.Dereference -> analyseLhsExpression(cf, lhsExpression.target, functionInfo, functionInfoMap, scope, environment)
        is LhsExpression.MemberLookup -> analyseLhsExpression(cf, lhsExpression.receiver, functionInfo, functionInfoMap, scope, environment)

        // NOTE: This rule is not given in the language specification, but seems obvious
        is LhsExpression.Paren -> analyseLhsExpression(cf, lhsExpression.target, functionInfo, functionInfoMap, scope, environment)
    }

private fun builtinFunctionInfo(
    functionName: String,
    args: List<Expression>,
    environment: ResolvedEnvironment,
): FunctionTags =
    when (functionName) {
        "storageBarrier", "textureBarrier", "workgroupBarrier" ->
            FunctionTags(functionTag = FunctionTag.NoRestriction, callSiteTag = CallSiteRequiredToBeUniformError, listOf())
        "workgroupUniformLoad" ->
            FunctionTags(
                functionTag = FunctionTag.NoRestriction,
                callSiteTag = CallSiteRequiredToBeUniformError,
                listOf(
                    // TODO: The parameter return tag isn't actually specified in the langauges specification, but should be.
                    Pair(ParameterTag.ParameterRequiredToBeUniformError, ParameterReturnTag.ParameterReturnNoRestriction),
                ),
            )
        "dpdx", "dpdxCoarse", "dpdxFine", "dpdy", "dpdyCoarse", "dpdyFine", "fwidth",
        "fwidthCoarse", "fwidthFine", "textureSample", "textureSampleBias", "textureSampleCompare",
        ->
            // TODO(JLJ): This doesn't support diagnostic filtering. See https://www.w3.org/TR/WGSL/#uniformity-function-calls
            // NOTE: The spec doesn't say what the parameter tags should be here, I assumed then there are no requirements
            FunctionTags(
                FunctionTag.ReturnValueMayBeNonUniform,
                CallSiteRequiredToBeUniformError,
                args.map {
                    Pair(ParameterTag.ParameterNoRestriction, ParameterReturnTag.ParameterReturnNoRestriction)
                },
            )
        "textureLoad" -> {
            // NOTE: The spec doesn't say what the parameter tags should be here, I assumed then there are no requirements
            val arg1Type = environment.typeOf(args[0]).asStoreTypeIfReference()
            val functionTag =
                if (arg1Type is Type.Texture.Storage && arg1Type.accessMode == AccessMode.READ_WRITE) {
                    FunctionTag.ReturnValueMayBeNonUniform
                } else {
                    FunctionTag.NoRestriction
                }
            FunctionTags(
                functionTag,
                CallSiteTag.CallSiteNoRestriction,
                args.map {
                    Pair(ParameterTag.ParameterNoRestriction, ParameterReturnTag.ParameterReturnNoRestriction)
                },
            )
        }
        "subgroupAdd", "subgroupExclusiveAdd", "subgroupInclusiveAdd", "subgroupAll", "subgroupAnd",
        "subgroupAny", "subgroupBallot", "subgroupBroadcast", "subgroupBroadcastFirst", "subgroupElect",
        "subgroupMax", "subgroupMin", "subgroupMul", "subgroupExclusiveMul", "subgroupInclusiveMul", "subgroupOr",
        "subgroupShuffle", "subgroupXor", "quadBroadcast", "quadSwapDiagonal", "quadSwapX", "quadSwapY",
        ->
            // TODO(JLJ): This doesn't handle diagnostics
            // NOTE: The spec doesn't say what the parameter tags should be here, I assumed then there are no requirements
            FunctionTags(
                FunctionTag.ReturnValueMayBeNonUniform,
                CallSiteRequiredToBeUniformError,
                args.map {
                    Pair(ParameterTag.ParameterNoRestriction, ParameterReturnTag.ParameterReturnNoRestriction)
                },
            )
        "subgroupShuffleUp", "subgroupShuffleDown", "subgroupShuffleXor" ->
            // TODO(JLJ): This doesn't handle diagnostics
            FunctionTags(
                FunctionTag.ReturnValueMayBeNonUniform,
                CallSiteRequiredToBeUniformError,
                listOf(
                    Pair(ParameterTag.ParameterNoRestriction, ParameterReturnTag.ParameterReturnNoRestriction),
                    Pair(ParameterTag.ParameterRequiredToBeUniformError, ParameterReturnTag.ParameterReturnNoRestriction),
                ),
            )
        // Since the program parsed and resolve, we can assume anything else must still be a built-in
        else ->
            FunctionTags(
                FunctionTag.NoRestriction,
                CallSiteTag.CallSiteNoRestriction,
                args.map {
                    Pair(ParameterTag.ParameterNoRestriction, ParameterReturnTag.ParameterReturnContentsRequiredToBeUniform)
                },
            )
    }

// Implement the rules for function calls described in: https://www.w3.org/TR/WGSL/#uniformity-function-calls
private fun analyseFunctionCall(
    cf: UniformityNode,
    astNode: AstNode,
    functionInfo: FunctionInfo,
    functionInfoMap: FunctionInfoMap,
    scope: Scope,
    environment: ResolvedEnvironment,
): ExpressionAnalysisResult {
    val (args, callee) =
        when (astNode) {
            is Statement.FunctionCall -> Pair(astNode.args, astNode.callee)
            is Expression.FunctionCall -> Pair(astNode.args, astNode.callee)
            is Expression.ArrayValueConstructor -> Pair(astNode.args, astNode.constructorName)
            is Expression.Mat2x2ValueConstructor -> Pair(astNode.args, astNode.constructorName)
            is Expression.Mat2x3ValueConstructor -> Pair(astNode.args, astNode.constructorName)
            is Expression.Mat2x4ValueConstructor -> Pair(astNode.args, astNode.constructorName)
            is Expression.Mat3x2ValueConstructor -> Pair(astNode.args, astNode.constructorName)
            is Expression.Mat3x3ValueConstructor -> Pair(astNode.args, astNode.constructorName)
            is Expression.Mat3x4ValueConstructor -> Pair(astNode.args, astNode.constructorName)
            is Expression.Mat4x2ValueConstructor -> Pair(astNode.args, astNode.constructorName)
            is Expression.Mat4x3ValueConstructor -> Pair(astNode.args, astNode.constructorName)
            is Expression.Mat4x4ValueConstructor -> Pair(astNode.args, astNode.constructorName)
            is Expression.BoolValueConstructor -> Pair(astNode.args, astNode.constructorName)
            is Expression.F16ValueConstructor -> Pair(astNode.args, astNode.constructorName)
            is Expression.F32ValueConstructor -> Pair(astNode.args, astNode.constructorName)
            is Expression.I32ValueConstructor -> Pair(astNode.args, astNode.constructorName)
            is Expression.U32ValueConstructor -> Pair(astNode.args, astNode.constructorName)
            is Expression.StructValueConstructor -> Pair(astNode.args, astNode.constructorName)
            is Expression.TypeAliasValueConstructor -> Pair(astNode.args, astNode.constructorName)
            is Expression.Vec2ValueConstructor -> Pair(astNode.args, astNode.constructorName)
            is Expression.Vec3ValueConstructor -> Pair(astNode.args, astNode.constructorName)
            is Expression.Vec4ValueConstructor -> Pair(astNode.args, astNode.constructorName)
            else -> throw IllegalArgumentException("The argument to analyseFunctionCall must be a FunctionCall Ast Node.")
        }

    // TODO(JLJ): This could be done by writing a mapAccum function.
    var currentCf = cf
    val argsAndCfs: List<ExpressionAnalysisResult> =
        args.map { expr ->
            val result =
                analyseExpression(
                    currentCf,
                    expr,
                    functionInfo,
                    functionInfoMap,
                    scope,
                    environment,
                )
            currentCf = result.cfNode
            result
        }
    val lastCf = if (argsAndCfs.isNotEmpty()) argsAndCfs.last().cfNode else cf

    val result = createUniformityNode("Result_function_call_$callee")
    val cfAfter = createUniformityNode("CF_after_function_call_$callee")

    val calleeFunctionTags =
        if (callee in
            functionInfoMap
        ) {
            functionInfoMap[callee]!!.functionTags()
        } else {
            builtinFunctionInfo(callee, args, environment)
        }

    if (CallSiteTag.isRequiredToBeUniform(calleeFunctionTags.callSiteTag)) {
        val uniformityNode = functionInfo.requiredToBeUniform(calleeFunctionTags.callSiteTag.severity()!!)
        uniformityNode.addEdges(lastCf)
        // TODO(JLJ): This ignore potential trigger set, but shouldn't matter for functionality
    }

    cfAfter.addEdges(lastCf)

    if (calleeFunctionTags.functionTag == FunctionTag.ReturnValueMayBeNonUniform) {
        result.addEdges(functionInfo.mayBeNonUniform)
    }

    result.addEdges(cfAfter)

    // TODO(JLJ): The argument related rules need implementing
    assert(argsAndCfs.size == calleeFunctionTags.paramInfo.size)
    for (i in 0..argsAndCfs.lastIndex) {
        val (cfI, argI) = argsAndCfs[i]
        val (parameterTag, parameterReturnTag) = calleeFunctionTags.paramInfo[i]

        if (ParameterTag.isRequiredToBeUniform(parameterTag)) {
            val requiredToBeUniformNode = functionInfo.requiredToBeUniform(parameterTag.severity()!!)
            requiredToBeUniformNode.addEdges(argI)
        }

        if (parameterReturnTag == ParameterReturnTag.ParameterReturnContentsRequiredToBeUniform) {
            result.addEdges(argI)
        }

        // TODO(JLJ): This doesn't handle pointer parameters
    }

    // NOTE: The spec doesn't explicitly say to use this node going forward, but it seems sensible
    return ExpressionAnalysisResult(cfAfter, result)
}

// TODO(JLJ): This load rule condition isn't quite right. It doesn't check if the only matching type
private fun isLoadRuleInvoked(type: Type): Boolean =
    when (type) {
        is Type.Reference -> type.accessMode == AccessMode.READ || type.accessMode == AccessMode.READ_WRITE
        else -> false
    }

private fun analyseExpression(
    cf: UniformityNode,
    expression: Expression,
    functionInfo: FunctionInfo,
    functionInfoMap: FunctionInfoMap,
    scope: Scope,
    environment: ResolvedEnvironment,
): ExpressionAnalysisResult =
    when (expression) {
        is Expression.Binary -> {
            val (cf1, v1) = analyseExpression(cf, expression.lhs, functionInfo, functionInfoMap, scope, environment)

            if (expression.operator == BinaryOperator.SHORT_CIRCUIT_AND || expression.operator == BinaryOperator.SHORT_CIRCUIT_OR) {
                val (cf2, v2) = analyseExpression(v1, expression.rhs, functionInfo, functionInfoMap, scope, environment)
                ExpressionAnalysisResult(cf, v2)
            } else {
                val (cf2, v2) = analyseExpression(cf1, expression.rhs, functionInfo, functionInfoMap, scope, environment)
                val resultNode = createUniformityNode("Result_binary")
                resultNode.addEdges(v1, v2)
                ExpressionAnalysisResult(cf2, resultNode)
            }
        }

        is Expression.IndexLookup -> {
            // This rule is the same as the rule for a binary operator that is not && or ||.
            val (cf1, v1) = analyseExpression(cf, expression.target, functionInfo, functionInfoMap, scope, environment)
            val (cf2, v2) = analyseExpression(cf1, expression.index, functionInfo, functionInfoMap, scope, environment)
            val resultNode = createUniformityNode("Result_index_lookup")
            resultNode.addEdges(v1, v2)
            ExpressionAnalysisResult(cf2, resultNode)
        }

        is Expression.BoolLiteral -> ExpressionAnalysisResult(cf, cf)
        is Expression.FloatLiteral -> ExpressionAnalysisResult(cf, cf)
        is Expression.IntLiteral -> ExpressionAnalysisResult(cf, cf)

        is Expression.Unary -> analyseExpression(cf, expression.target, functionInfo, functionInfoMap, scope, environment)
        is Expression.MemberLookup -> analyseExpression(cf, expression.receiver, functionInfo, functionInfoMap, scope, environment)

        // NOTE: There are no rules given for these in the language specification.
        // It seems sensible that parenthesis should have no effect on this uniformity of an expression.
        is Expression.Paren -> analyseExpression(cf, expression.target, functionInfo, functionInfoMap, scope, environment)

        is Expression.Identifier ->
            when (val entry = scope.getEntry(expression.name)) {
                is ScopeEntry.Parameter ->
                    if (isNonUniformBuiltin(entry)) {
                        ExpressionAnalysisResult(cf, functionInfo.mayBeNonUniform)
                    } else if (isUniformBuiltin(entry)) {
                        ExpressionAnalysisResult(cf, cf)
                    } else if (entry.type !is Type.Pointer) {
                        // Expression Uniformity Analysis: non-built-in parameter of non-pointer type.
                        val resultNode = createUniformityNode("Result")
                        resultNode.addEdges(cf, functionInfo.variableNodes.get(entry.astNode.name)!!)
                        ExpressionAnalysisResult(cf, resultNode)
                    } else {
                        TODO()
                    }
                is ScopeEntry.LocalVariable ->
                    // If Load Rule applies: https://www.w3.org/TR/WGSL/#load-rule
                    // rule requires the type to be the references store type.
                    if (isLoadRuleInvoked(entry.type)) {
                        val resultNode = createUniformityNode("Result_ident_expr")
                        resultNode.addEdges(cf)
                        resultNode.addEdges(functionInfo.variableNodes.get(expression.name)!!)
                        ExpressionAnalysisResult(cf, resultNode)
                    } else {
                        // TODO(JLJ): This doesn't currently handle local variables that are desugared pointer parameters (also not implemented)
                        ExpressionAnalysisResult(cf, cf)
                    }
                is ScopeEntry.LocalValue -> {
                    val resultNode = createUniformityNode("Result_ident_expr")
                    resultNode.addEdges(cf)
                    resultNode.addEdges(functionInfo.variableNodes.get(expression.name)!!)
                    ExpressionAnalysisResult(cf, resultNode)
                }
                // TODO(JLJ): Same as for values, factor out
                is ScopeEntry.GlobalOverride -> {
                    val resultNode = createUniformityNode("Result_ident_expr")
                    resultNode.addEdges(cf)
                    // TODO: it seems the folloiwng is not necessary for override expressions or global values (not a variable) the spec doesn't say this though.
                    // resultNode.addEdges(functionInfo.variableNodes.get(expression.name)!!)
                    ExpressionAnalysisResult(cf, resultNode)
                }
                // TODO(JLJ): I don't think the following is correct.
                is ScopeEntry.GlobalConstant -> ExpressionAnalysisResult(cf, cf)
                is ScopeEntry.Function -> TODO()
                is ScopeEntry.GlobalVariable -> {
                    if (entry.type is Type.Reference && entry.type.accessMode != AccessMode.READ) {
                        if (isLoadRuleInvoked(entry.type)) {
                            ExpressionAnalysisResult(cf, functionInfo.mayBeNonUniform)
                        } else {
                            ExpressionAnalysisResult(cf, cf)
                        }
                    } else if (entry.type is Type.Reference && entry.type.accessMode == AccessMode.READ) {
                        // This is a read-only module-scope variable, even though it's a variable
                        ExpressionAnalysisResult(cf, cf)
                    } else {
                        // This shouldn't be reachable because all global variables should have reference type.
                        TODO()
                    }
                }

                is ScopeEntry.Struct -> TODO()
                is ScopeEntry.TypeAlias -> TODO()
                null -> TODO()
            }

        // TODO(JLJ): Reduce duplication with statement function calls
        is Expression.FunctionCall -> analyseFunctionCall(cf, expression, functionInfo, functionInfoMap, scope, environment)

        is AugmentedExpression.FalseByConstruction -> TODO()
        is AugmentedExpression.AddZero -> TODO()
        is AugmentedExpression.DivOne -> TODO()
        is AugmentedExpression.MulOne -> TODO()
        is AugmentedExpression.SubZero -> TODO()
        is AugmentedExpression.KnownValue -> TODO()
        is AugmentedExpression.TrueByConstruction -> TODO()

        is Expression.ArrayValueConstructor,
        is Expression.Mat2x2ValueConstructor,
        is Expression.Mat2x3ValueConstructor,
        is Expression.Mat2x4ValueConstructor,
        is Expression.Mat3x2ValueConstructor,
        is Expression.Mat3x3ValueConstructor,
        is Expression.Mat3x4ValueConstructor,
        is Expression.Mat4x2ValueConstructor,
        is Expression.Mat4x3ValueConstructor,
        is Expression.Mat4x4ValueConstructor,
        is Expression.BoolValueConstructor,
        is Expression.F16ValueConstructor,
        is Expression.F32ValueConstructor,
        is Expression.I32ValueConstructor,
        is Expression.U32ValueConstructor,
        is Expression.StructValueConstructor,
        is Expression.TypeAliasValueConstructor,
        is Expression.Vec2ValueConstructor,
        is Expression.Vec3ValueConstructor,
        is Expression.Vec4ValueConstructor,
        -> analyseFunctionCall(cf, expression, functionInfo, functionInfoMap, scope, environment)
    }

// WGSL treats these as normal function calls.
private fun analyseValueConstructorExpression(expression: Expression.ValueConstructor) {
}

private fun isNonUniformBuiltinValue(builtinValue: BuiltinValue): Boolean {
    // Builtins from: https://www.w3.org/TR/WGSL/#built-in-values
    // The following builtins are uniform, according to https://www.w3.org/TR/WGSL/#uniformity-expressions:
    // - workgroup_id, num_workgroups, subgroup_size if in a compute shader
    val nonUniformBuiltins =
        setOf(
            BuiltinValue.VERTEX_INDEX,
            BuiltinValue.INSTANCE_INDEX,
            BuiltinValue.CLIP_DISTANCES,
            BuiltinValue.POSITION,
            BuiltinValue.FRONT_FACING,
            BuiltinValue.FRAG_DEPTH,
            BuiltinValue.SAMPLE_INDEX,
            BuiltinValue.SAMPLE_MASK,
            BuiltinValue.LOCAL_INVOCATION_ID,
            BuiltinValue.LOCAL_INVOCATION_INDEX,
            BuiltinValue.GLOBAL_INVOCATION_ID,
            BuiltinValue.SUBGROUP_INVOCATION_ID,
        )
    // TODO(JLJ): Subgroup size should be treated differently depending on shader stage
    return nonUniformBuiltins.contains(builtinValue)
}

private fun isNonUniformBuiltin(param: ScopeEntry.Parameter): Boolean =
    param.astNode.attributes.any {
        it is Attribute.Builtin && isNonUniformBuiltinValue(it.name)
    }

private fun isUniformBuiltin(param: ScopeEntry.Parameter): Boolean =
    param.astNode.attributes.any {
        it is Attribute.Builtin && !isNonUniformBuiltinValue(it.name)
    }
