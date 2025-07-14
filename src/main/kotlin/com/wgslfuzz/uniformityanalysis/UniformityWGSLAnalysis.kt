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
import com.wgslfuzz.core.isFunctionCallBuiltin
import com.wgslfuzz.core.traverse
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

private class FunctionInfo(
    val function: GlobalDecl.Function,
) {
    // Create special nodes.
    val requiredToBeUniformError = createUniformityNode("RequiredToBeUniformError")
    val requiredToBeUniformWarning = createUniformityNode("RequiredToBeUniformWarning")
    val requiredToBeUniformInfo = createUniformityNode("RequiredToBeUniformInfo")
    val mayBeNonUniform = createUniformityNode("MayBeNonUniform")
    val cfStart = createUniformityNode("CF_start")
    var params: List<UniformityNode> = function.parameters.map { param -> createUniformityNode(param.name) }
    var valueReturn: UniformityNode? = function.returnType?.let { createUniformityNode("Value_return") }

    // Initialise tags
    var functionTag = FunctionTag.NoRestriction
    var callSiteTag: CallSiteTag = INITIAL_CALL_SITE_TAG
    var parameterTags = params.map { _ -> INITIAL_PARAMETER_TAG }
    var parameterReturnTags = params.map { _ -> ParameterReturnTag.ParameterReturnNoRestriction }

    // Keep track of the node currently representing the uniformity of a variable
    val variableNodes: ScopeStack = ScopeStack()

    // The modifiable variables in scope at any point in the program. These are the set of variables that could have
    // been modified in branching control flow and so must be merged.
    val variablesInScope: HashSet<String> = HashSet()

    var loopInfo: LoopInfo? = null

    // TODO(JLJ): Change param name
    private fun updateNodesReaching(map: MutableMap<String, MutableSet<UniformityNode>>) {
        for (variable in variablesInScope) {
            val variableNode = variableNodes.get(variable)!!
            if (variable !in loopInfo!!.nodesReachingContinue) {
                map.put(variable, mutableSetOf(variableNode))
            } else {
                map[variable]?.add(variableNode)
            }
        }
    }

    fun updateNodesReachingContinuing() {
        updateNodesReaching(loopInfo!!.nodesReachingContinue)
    }

    fun updateNodesExitingLoop() {
        updateNodesReaching(loopInfo!!.nodesBreaking)
    }

    fun swapOutLoopInfo(): LoopInfo? {
        val result = loopInfo
        loopInfo = LoopInfo()
        return result
    }

    // TODO(JLJ): Add support for pointer parameters

    fun callSiteTagToUniformityNode(callSiteTag: CallSiteTag): UniformityNode? =
        when (callSiteTag) {
            CallSiteTag.CallSiteRequiredToBeUniformError -> requiredToBeUniformError
            CallSiteTag.CallSiteRequiredToBeUniformWarning -> requiredToBeUniformWarning
            CallSiteTag.CallSiteRequiredToBeUniformInfo -> requiredToBeUniformError
            CallSiteTag.CallSiteNoRestriction -> null
        }

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
    fun traverse(severity: Severity): Boolean {
        val reachable = reachableUnvisited(functionInfo.requiredToBeUniform(severity))
        if (reachable.contains(functionInfo.mayBeNonUniform)) {
            return false
        }

        if (reachable.contains(functionInfo.cfStart) && functionInfo.callSiteTag == INITIAL_CALL_SITE_TAG) {
            functionInfo.callSiteTag = CallSiteTag.callSiteRequiredToBeUniform(severity)
        }

        functionInfo.parameterTags =
            functionInfo.parameterTags.map { parameterTag ->
                if (parameterTag ==
                    INITIAL_PARAMETER_TAG
                ) {
                    ParameterTag.parameterRequiredToBeUniform(severity)
                } else {
                    parameterTag
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

    if (functionInfo.valueReturn != null) {
        val reachable = reachableUnvisited(functionInfo.valueReturn!!)
        if (reachable.contains(functionInfo.mayBeNonUniform)) {
            functionInfo.functionTag = FunctionTag.ReturnValueMayBeNonUniform
        }

        // TODO(JLJ): It is not clear from the spec if this should be in the above if, check
        functionInfo.parameterReturnTags =
            functionInfo.parameterTags.map { _ -> ParameterReturnTag.ParameterReturnContentsRequiredToBeUniform }
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

            functionInfo.variablesInScope.add(statement.name)
            functionInfo.variableNodes.set(statement.name, valueNode)

            newCf
        }

        is Statement.Variable -> {
            // Add variable to the set of in-scope variables.
            functionInfo.variablesInScope.add(statement.name)

            // If this variable declaration has an initialiser, analyse the initialising expression
            // and continue with the node produced. Otherwise, continue analysis with the current
            // control flow node.
            if (statement.initializer ==
                null
            ) {
                // Variable Value Analysis: Since the variable doesn't have an initialiser, V(0) is the
                // current control flow context.
                functionInfo.variableNodes.set(statement.name, cf)
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
                functionInfo.variableNodes.set(statement.name, valueNode)
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
            val previousLoopInfo = functionInfo.swapOutLoopInfo()

            val variableNodesAtLoopEntry = mutableMapOf<String, UniformityNode>()
            for (variable in functionInfo.variablesInScope) {
                variableNodesAtLoopEntry[variable] = functionInfo.variableNodes.get(variable)!!
            }

            val newCf = createUniformityNode("CF'_loop_start")
            val cf1 = analyseStatement(newCf, statement.body, functionInfo, functionInfoMap, behaviourMap, environment)

            // Statement Behaviour Analysis: create new nodes for any variables that may continue.
            for ((variable, nodes) in functionInfo.loopInfo!!.nodesReachingContinue.iterator()) {
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
            val nodesBreaking = functionInfo.loopInfo!!.nodesBreaking

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

            // Rest loop info to previous value incase we are inside a nested loop.
            functionInfo.loopInfo = previousLoopInfo

            if (behaviourMap[statement] == setOf(StatementBehaviour.NEXT)) cf else newCf
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
                if (statement.lhsExpression is LhsExpression.Identifier) {
                    functionInfo.variableNodes.set(statement.lhsExpression.name, lv)
                }

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

        // Implement the rules for function calls described in: https://www.w3.org/TR/WGSL/#uniformity-function-calls
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
                analyseExpression(
                    cf,
                    statement.expression,
                    functionInfo,
                    functionInfoMap,
                    environment.scopeAvailableBefore(statement),
                    environment,
                ).cfNode
            }
        }

        is Statement.Switch -> {
            val (newCf, v) =
                analyseExpression(
                    cf,
                    statement.expression,
                    functionInfo,
                    functionInfoMap,
                    environment.scopeAvailableBefore(statement),
                    environment,
                )
            val cfNodes =
                statement.clauses.map { clause ->
                    analyseStatement(v, clause.compoundStatement, functionInfo, functionInfoMap, behaviourMap, environment)
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

            if (resolvedScope is ScopeEntry.LocalVariable) {
                val result = createUniformityNode("Result_lhs_ident")
                // Get the node representing this variable produced by statement behaviour analysis.
                val statementBehaviourAnalysisNode = functionInfo.variableNodes.get(lhsExpression.name)!!

                result.addEdges(cf, statementBehaviourAnalysisNode)
                ExpressionAnalysisResult(cf, result)
            } else if (resolvedScope is ScopeEntry.GlobalVariable) {
                ExpressionAnalysisResult(cf, functionInfo.mayBeNonUniform)
            } else {
                // TODO(JLJ): Add other identifier rule.
                TODO()
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
            else -> throw IllegalArgumentException("The argument to analyseFunctionCall must be a FunctionCall Ast Node.")
        }

    val lastCfi =
        args.fold(
            cf,
        ) { lastCf, expr ->
            analyseExpression(
                lastCf,
                expr,
                functionInfo,
                functionInfoMap,
                scope,
                environment,
            ).cfNode
        }

    // TODO(JLJ) Handle argument expressions

    val result = createUniformityNode("Result_function_call")
    val cfAfter = createUniformityNode("CF_after_function_call")

    if (isFunctionCallBuiltin(callee)) {
        if (callee == "atomic_store" || callee == "texture_store") {
            TODO()
        } else {
            // The remaining builtin statement function calls are barriers.
            // Add an edge from Required to be uniform to the last CF_i
            functionInfo.requiredToBeUniformError.addEdges(lastCfi)
            // TODO(JLJ): Potential trigger set additions
        }
    } else {
        val calleeFunctionInfo = functionInfoMap[callee]!!
        if (CallSiteTag.isRequiredToBeUniform(calleeFunctionInfo.callSiteTag)) {
            val uniformityNode = functionInfo.callSiteTagToUniformityNode(calleeFunctionInfo.callSiteTag)!!
            uniformityNode.addEdges(lastCfi)
            // TODO(JLJ): This ignore potential trigger set, but shouldn't matter for functionality
        }
        if (functionInfo.functionTag == FunctionTag.ReturnValueMayBeNonUniform) {
            result.addEdges(functionInfo.mayBeNonUniform)
        }
    }

    // Add edge from CF_after to the last CF_i
    cfAfter.addEdges(lastCfi)
    result.addEdges(cfAfter)

    args.forEach { TODO() }

    // NOTE: The spec doesn't explicitly say to use this node going forward, but it seems sensible
    return ExpressionAnalysisResult(cfAfter, result)
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
                    } else if (isNonUniformBuiltin(entry)) {
                        ExpressionAnalysisResult(cf, cf)
                    } else {
                        TODO()
                    }
                is ScopeEntry.LocalVariable ->
                    // If Load Rule applies: https://www.w3.org/TR/WGSL/#load-rule
                    // TODO(JLJ): This load rule condition isn't quite right. It doesn't check if the only matching type
                    // rule requires the type to be the references store type.
                    if (entry.type is Type.Reference &&
                        (entry.type.accessMode == AccessMode.READ || entry.type.accessMode == AccessMode.READ_WRITE)
                    ) {
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
                else -> TODO()
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

        is Expression.ArrayValueConstructor -> TODO()
        is Expression.Mat2x2ValueConstructor -> TODO()
        is Expression.Mat2x3ValueConstructor -> TODO()
        is Expression.Mat2x4ValueConstructor -> TODO()
        is Expression.Mat3x2ValueConstructor -> TODO()
        is Expression.Mat3x3ValueConstructor -> TODO()
        is Expression.Mat3x4ValueConstructor -> TODO()
        is Expression.Mat4x2ValueConstructor -> TODO()
        is Expression.Mat4x3ValueConstructor -> TODO()
        is Expression.Mat4x4ValueConstructor -> TODO()
        is Expression.BoolValueConstructor -> TODO()
        is Expression.F16ValueConstructor -> TODO()
        is Expression.F32ValueConstructor -> TODO()
        is Expression.I32ValueConstructor -> TODO()
        is Expression.U32ValueConstructor -> TODO()
        is Expression.StructValueConstructor -> TODO()
        is Expression.TypeAliasValueConstructor -> TODO()
        is Expression.Vec2ValueConstructor -> TODO()
        is Expression.Vec3ValueConstructor -> TODO()
        is Expression.Vec4ValueConstructor -> TODO()
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
