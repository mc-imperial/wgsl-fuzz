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

package com.wgslfuzz.uniformityanalysis

import com.wgslfuzz.core.AstNode
import com.wgslfuzz.core.ContinuingStatement
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.GlobalDecl
import com.wgslfuzz.core.LhsExpression
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.TranslationUnit
import com.wgslfuzz.core.clone
import com.wgslfuzz.core.traverse
import kotlin.String
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.Set
import kotlin.collections.contains
import kotlin.collections.last
import kotlin.collections.plus

data class PresentInfo(
    val ifControls: List<Set<Int>>,
    val variableUniformityInfo: Map<String, Set<Int>>,
)

data class StatementUniformityRecord(
    val presentInfo: PresentInfo?,
    val breakControls: Set<Int>,
    val continueControls: Set<Int>,
    val returnControls: Set<Int>,
) {
    fun updateVariableUniformityInfo(
        variable: String,
        newParameters: Set<Int>,
    ): StatementUniformityRecord =
        this.copy(
            presentInfo =
                presentInfo!!.copy(
                    variableUniformityInfo = presentInfo.variableUniformityInfo + (variable to newParameters),
                ),
        )

    fun strengthenIfControl(newParameters: Set<Int>): StatementUniformityRecord {
        val newIfControls: MutableList<Set<Int>> =
            presentInfo!!.ifControls.dropLast(1).toMutableList()
        newIfControls.add(presentInfo.ifControls.last() union newParameters)
        return this.copy(
            presentInfo =
                presentInfo.copy(
                    ifControls = newIfControls,
                ),
        )
    }
}

private class FunctionAnalysisContext(
    val previouslyAnalysedFunctions: Map<String, FunctionAnalysisState>,
    val parameters: Map<String, Int>,
    val variables: Set<String>,
    val breakToLoop: Map<Statement.Break, Statement.Loop>,
    val continueToLoop: Map<Statement.Continue, Statement.Loop>,
    val maximalReconvergence: Boolean,
)

data class FunctionAnalysisState(
    val callSiteMustBeUniform: Boolean,
    val uniformParams: Set<Int>,
    val returnedValueUniformity: Set<Int>,
    val stmtIn: Map<Statement, StatementUniformityRecord>,
    val stmtOut: Map<Statement, StatementUniformityRecord>,
) {
    fun updateOutForStatement(
        statement: Statement,
        uniformityRecord: StatementUniformityRecord,
    ) = this.copy(
        stmtOut = stmtOut + (statement to uniformityRecord),
    )

    fun updateInForStatement(
        statement: Statement,
        uniformityRecord: StatementUniformityRecord,
    ) = this.copy(
        stmtIn = stmtIn + (statement to uniformityRecord),
    )
}

fun runAnalysis(
    originalTu: TranslationUnit,
    maximalReconvergence: Boolean = false,
): Map<String, FunctionAnalysisState> {
    val adaptedTu = addContinuesToEndsOfLoops(addContinuingStatements(originalTu)) as TranslationUnit
    val result = mutableMapOf<String, FunctionAnalysisState>()
    for (functionDecl in functionsOrderedByCallGraph(adaptedTu)) {
        result[functionDecl.name] =
            analyseFunction(
                functionDecl,
                FunctionAnalysisContext(
                    result,
                    parameters = extractParameters(functionDecl),
                    variables = extractVariables(functionDecl),
                    breakToLoop = extractBreakToLoopMapping(functionDecl),
                    continueToLoop = extractContinueToLoopMapping(functionDecl),
                    maximalReconvergence = maximalReconvergence,
                ),
            )
    }
    return result
}

private fun findUserDefinedCallees(
    function: GlobalDecl.Function,
    userDefinedFunctions: Set<String>,
): Set<String> {
    fun action(
        node: AstNode,
        callees: MutableSet<String>,
    ) {
        traverse(::action, node, callees)
        when (node) {
            is Statement.FunctionCall -> {
                if (node.callee in userDefinedFunctions) {
                    callees.add(node.callee)
                }
            }
            is Expression.FunctionCall -> {
                if (node.callee in userDefinedFunctions) {
                    callees.add(node.callee)
                }
            }
            else -> {}
        }
    }

    val result = mutableSetOf<String>()
    traverse(::action, function, result)
    return result
}

fun functionsOrderedByCallGraph(tu: TranslationUnit): List<GlobalDecl.Function> {
    fun topologicalSortLeavesFirst(
        callGraphRoots: Set<String>,
        callGraphEdges: Map<String, Set<String>>,
    ): List<String> {
        val visited = mutableSetOf<String>()
        val stack = mutableSetOf<String>() // current recursion stack
        val result = mutableListOf<String>()

        fun dfs(
            node: String,
            path: MutableList<String>,
        ) {
            if (node in stack) {
                throw UnsupportedOperationException("Recursion detected in call graph.")
            }
            if (!visited.add(node)) {
                return
            }
            stack.add(node)
            path.add(node)
            for (callee in callGraphEdges[node].orEmpty()) {
                dfs(callee, path)
            }
            stack.remove(node)
            path.removeLast()
            result.add(node)
        }

        for (root in callGraphRoots) {
            if (root !in visited) dfs(root, mutableListOf())
        }
        return result
    }

    val declaredFunctions: Set<GlobalDecl.Function> = tu.globalDecls.filterIsInstance<GlobalDecl.Function>().toSet()
    if (declaredFunctions.isEmpty()) {
        return emptyList()
    }
    val functionNameToDecl = declaredFunctions.associateBy { it.name }
    val declaredFunctionNames: Set<String> = declaredFunctions.map { it.name }.toSet()
    val callGraphEdges: Map<String, Set<String>> =
        declaredFunctions.associate {
            it.name to findUserDefinedCallees(it, declaredFunctionNames)
        }
    val calledBySomeFunction = callGraphEdges.values.flatMap { it }.toSet()
    val callGraphRoots = declaredFunctionNames - calledBySomeFunction
    if (callGraphRoots.isEmpty()) {
        throw UnsupportedOperationException("There are no call graph roots - the call graph must be cyclic!")
    }
    return topologicalSortLeavesFirst(callGraphRoots, callGraphEdges).map { it ->
        functionNameToDecl[it]!!
    }
}

private fun extractBreakToLoopMapping(function: GlobalDecl.Function): Map<Statement.Break, Statement.Loop> {
    fun action(
        node: AstNode,
        state: Pair<MutableList<Statement.Loop>, MutableMap<Statement.Break, Statement.Loop>>,
    ) {
        when (node) {
            is Statement.Loop -> state.first.add(node)
            else -> {}
        }
        traverse(::action, node, state)
        when (node) {
            is Statement.Loop -> state.first.removeLast()
            else -> {}
        }
        when (node) {
            is Statement.Break -> state.second[node] = state.first.last()
            else -> {}
        }
    }
    val result = mutableMapOf<Statement.Break, Statement.Loop>()
    traverse(::action, function, Pair(mutableListOf(), result))
    return result
}

private fun extractContinueToLoopMapping(function: GlobalDecl.Function): Map<Statement.Continue, Statement.Loop> {
    fun action(
        node: AstNode,
        state: Pair<MutableList<Statement.Loop>, MutableMap<Statement.Continue, Statement.Loop>>,
    ) {
        when (node) {
            is Statement.Loop -> state.first.add(node)
            else -> {}
        }
        traverse(::action, node, state)
        when (node) {
            is Statement.Loop -> state.first.removeLast()
            else -> {}
        }
        when (node) {
            is Statement.Continue -> state.second[node] = state.first.last()
            else -> {}
        }
    }
    val result = mutableMapOf<Statement.Continue, Statement.Loop>()
    traverse(::action, function, Pair(mutableListOf(), result))
    return result
}

private fun extractParameters(function: GlobalDecl.Function): Map<String, Int> =
    function.parameters
        .mapIndexed { index, decl ->
            decl.name to index
        }.toMap()

private fun extractVariables(function: GlobalDecl.Function): Set<String> =
    function.body.statements
        .filterIsInstance<Statement.Variable>()
        .map(
            Statement.Variable::name,
        ).toSet()

private fun analyseFunction(
    function: GlobalDecl.Function,
    functionAnalysisContext: FunctionAnalysisContext,
): FunctionAnalysisState {
    assert((functionAnalysisContext.parameters.keys intersect functionAnalysisContext.variables).isEmpty())

    val statements =
        function.body.statements.filter {
            it !is Statement.Variable
        }

    var functionAnalysisState =
        FunctionAnalysisState(
            callSiteMustBeUniform = false,
            uniformParams = setOf(),
            returnedValueUniformity = setOf(),
            stmtIn =
                if (statements.isEmpty()) {
                    mapOf()
                } else {
                    mapOf(
                        statements[0] to
                            StatementUniformityRecord(
                                presentInfo =
                                    PresentInfo(
                                        ifControls = listOf(emptySet()),
                                        variableUniformityInfo = functionAnalysisContext.variables.associateWith { emptySet() },
                                    ),
                                breakControls = emptySet(),
                                continueControls = emptySet(),
                                returnControls = emptySet(),
                            ),
                    )
                },
            stmtOut = mapOf(),
        )

    while (true) {
        val newAnalaysisState =
            analyseStatements(
                functionAnalysisState,
                statements,
                functionAnalysisContext,
            )
        if (functionAnalysisState == newAnalaysisState) {
            break
        }
        functionAnalysisState = newAnalaysisState
    }
    return functionAnalysisState
}

private fun analyseStatements(
    initialAnalysisState: FunctionAnalysisState,
    statements: List<Statement>,
    functionAnalysisContext: FunctionAnalysisContext,
): FunctionAnalysisState {
    var result = initialAnalysisState
    statements.forEachIndexed { index, currentStatement ->
        result =
            analyseStatement(
                functionAnalysisState = result,
                statement = currentStatement,
                functionAnalysisContext = functionAnalysisContext,
            )
        if (index < statements.size - 1) {
            val outForCurrentStatement = result.stmtOut[currentStatement]
            if (outForCurrentStatement == null) {
                // We have hit unreachable code - nothing to do.
                return result
            }
            val nextStatement = statements[index + 1]
            val newStmtIn: Map<Statement, StatementUniformityRecord> = result.stmtIn + (nextStatement to outForCurrentStatement)
            result =
                result.copy(
                    stmtIn = newStmtIn,
                )
        }
    }
    return result
}

private fun analyseStatement(
    functionAnalysisState: FunctionAnalysisState,
    statement: Statement,
    functionAnalysisContext: FunctionAnalysisContext,
): FunctionAnalysisState {
    val inStmt = functionAnalysisState.stmtIn[statement]!!
    if (inStmt.presentInfo == null) {
        return functionAnalysisState.copy(
            stmtOut = functionAnalysisState.stmtOut + (statement to inStmt),
        )
    }
    val presentVariables: Map<String, Set<Int>> = inStmt.presentInfo.variableUniformityInfo
    val presentControls: Set<Int> =
        inStmt.presentInfo.ifControls.reduce { first, second ->
            first union second
        }
    return when (statement) {
        is Statement.Empty ->
            analyseEmptyStatement(
                statement = statement,
                functionAnalysisState = functionAnalysisState,
                inStmt = inStmt,
            )
        is Statement.Assignment ->
            analyseAssignmentStatement(
                statement = statement,
                functionAnalysisContext = functionAnalysisContext,
                functionAnalysisState = functionAnalysisState,
                inStmt = inStmt,
                presentVariables = presentVariables,
                presentControls = presentControls,
            )
        is Statement.FunctionCall ->
            analyseFunctionCallStatement(
                statement = statement,
                functionAnalysisContext = functionAnalysisContext,
                functionAnalysisState = functionAnalysisState,
                inStmt = inStmt,
                presentVariables = presentVariables,
                presentControls = presentControls,
            )
        is Statement.If ->
            analyseIfStatement(
                statement = statement,
                functionAnalysisContext = functionAnalysisContext,
                functionAnalysisState = functionAnalysisState,
                inStmt = inStmt,
                presentVariables = presentVariables,
            )
        is Statement.Loop ->
            analyseLoopStatement(
                statement = statement,
                functionAnalysisContext = functionAnalysisContext,
                functionAnalysisState = functionAnalysisState,
                inStmt = inStmt,
                presentVariables = presentVariables,
            )
        is Statement.Return ->
            analyseReturnStatement(
                statement = statement,
                functionAnalysisContext = functionAnalysisContext,
                functionAnalysisState = functionAnalysisState,
                inStmt = inStmt,
                presentVariables = presentVariables,
                presentControls = presentControls,
            )
        is Statement.Break ->
            analyseBreakStatement(
                statement = statement,
                functionAnalysisContext = functionAnalysisContext,
                functionAnalysisState = functionAnalysisState,
                inStmt = inStmt,
                presentVariables = presentVariables,
            )
        is Statement.Continue ->
            analyseContinueStatement(
                statement = statement,
                functionAnalysisContext = functionAnalysisContext,
                functionAnalysisState = functionAnalysisState,
                inStmt = inStmt,
                presentVariables = presentVariables,
            )
        else -> throw UnsupportedOperationException("Unknown kind of statement: " + statement)
    }
}

private fun analyseEmptyStatement(
    statement: Statement.Empty,
    functionAnalysisState: FunctionAnalysisState,
    inStmt: StatementUniformityRecord,
): FunctionAnalysisState = functionAnalysisState.updateOutForStatement(statement, inStmt)

private fun analyseAssignmentStatement(
    statement: Statement.Assignment,
    functionAnalysisState: FunctionAnalysisState,
    functionAnalysisContext: FunctionAnalysisContext,
    inStmt: StatementUniformityRecord,
    presentVariables: Map<String, Set<Int>>,
    presentControls: Set<Int>,
): FunctionAnalysisState {
    val lhsName = (statement.lhsExpression as LhsExpression.Identifier).name
    assert(lhsName in functionAnalysisContext.variables)
    return when (val rhs = statement.rhs) {
        is Expression.FunctionCall -> {
            val calleeSummary = functionAnalysisContext.previouslyAnalysedFunctions[rhs.callee]!!

            val parametersAffectingReturnedValueUniformity: Set<Int> =
                determineParametersAffectingReturnValueUniformityForCall(
                    calleeSummary = calleeSummary,
                    callerArguments = rhs.args,
                    functionAnalysisContext = functionAnalysisContext,
                    variableUniformityInfo = presentVariables,
                )

            val newUniformityRecord =
                inStmt.updateVariableUniformityInfo(
                    lhsName,
                    presentControls union inStmt.breakControls union inStmt.continueControls union inStmt.returnControls union
                        parametersAffectingReturnedValueUniformity,
                )

            val parametersThatMustBeUniformDueToThisCall: Set<Int> =
                determineParametersRequiredToBeUniformFromCall(
                    calleeSummary = calleeSummary,
                    callerArguments = rhs.args,
                    functionAnalysisContext = functionAnalysisContext,
                    variableUniformityInfo = presentVariables,
                )

            if (calleeSummary.callSiteMustBeUniform) {
                functionAnalysisState
                    .copy(
                        callSiteMustBeUniform = true,
                        uniformParams =
                            functionAnalysisState.uniformParams union
                                parametersThatMustBeUniformDueToThisCall union
                                presentControls union
                                inStmt.breakControls union
                                inStmt.continueControls union
                                inStmt.returnControls,
                    ).updateOutForStatement(statement, newUniformityRecord)
            } else {
                functionAnalysisState
                    .copy(
                        uniformParams = functionAnalysisState.uniformParams union parametersThatMustBeUniformDueToThisCall,
                    ).updateOutForStatement(statement, newUniformityRecord)
            }
        }
        else -> {
            val parametersAffectingRhs =
                determineParametersAffectingUniformityOfExpressionResult(
                    expression = rhs,
                    functionAnalysisContext = functionAnalysisContext,
                    presentVariables,
                )
            val newUniformityRecord =
                inStmt.updateVariableUniformityInfo(
                    lhsName,
                    presentControls union inStmt.breakControls union inStmt.continueControls union inStmt.returnControls union
                        parametersAffectingRhs,
                )
            functionAnalysisState.updateOutForStatement(statement, newUniformityRecord)
        }
    }
}

private fun analyseFunctionCallStatement(
    statement: Statement.FunctionCall,
    functionAnalysisState: FunctionAnalysisState,
    functionAnalysisContext: FunctionAnalysisContext,
    inStmt: StatementUniformityRecord,
    presentVariables: Map<String, Set<Int>>,
    presentControls: Set<Int>,
): FunctionAnalysisState =
    if (statement.callee == "workgroupBarrier") {
        assert(statement.callee !in functionAnalysisContext.previouslyAnalysedFunctions)
        functionAnalysisState
            .copy(
                callSiteMustBeUniform = true,
                uniformParams =
                    functionAnalysisState.uniformParams union presentControls union inStmt.breakControls union inStmt.continueControls union
                        inStmt.returnControls,
            ).updateOutForStatement(statement, inStmt)
    } else {
        val calleeSummary = functionAnalysisContext.previouslyAnalysedFunctions[statement.callee]!!
        // This function is being called via a statement with no return value captured, thus it must have void
        // return type.
        assert(calleeSummary.returnedValueUniformity.isEmpty())

        val parametersThatMustBeUniformDueToThisCall =
            determineParametersRequiredToBeUniformFromCall(
                calleeSummary = calleeSummary,
                callerArguments = statement.args,
                functionAnalysisContext = functionAnalysisContext,
                variableUniformityInfo = presentVariables,
            )

        if (calleeSummary.callSiteMustBeUniform) {
            functionAnalysisState
                .copy(
                    callSiteMustBeUniform = true,
                    uniformParams =
                        functionAnalysisState.uniformParams union
                            parametersThatMustBeUniformDueToThisCall union
                            presentControls union
                            inStmt.breakControls union
                            inStmt.continueControls union
                            inStmt.returnControls,
                ).updateOutForStatement(statement, inStmt)
        } else {
            functionAnalysisState
                .copy(
                    uniformParams = functionAnalysisState.uniformParams union parametersThatMustBeUniformDueToThisCall,
                ).updateOutForStatement(statement, inStmt)
        }
    }

private fun analyseIfStatement(
    statement: Statement.If,
    functionAnalysisState: FunctionAnalysisState,
    functionAnalysisContext: FunctionAnalysisContext,
    inStmt: StatementUniformityRecord,
    presentVariables: Map<String, Set<Int>>,
): FunctionAnalysisState {
    val parametersAffectingCondition =
        determineParametersAffectingUniformityOfExpressionResult(
            expression = statement.condition,
            functionAnalysisContext = functionAnalysisContext,
            presentVariables = presentVariables,
        )
    val thenStatements = statement.thenBranch.statements
    val elseStatements =
        statement.elseBranch?.let {
            (it as Statement.Compound).statements
        }
    val updatedUniformityRecord: StatementUniformityRecord =
        inStmt.strengthenIfControl(
            parametersAffectingCondition,
        )
    val afterAnalysingThenSide: FunctionAnalysisState =
        analyseStatements(
            functionAnalysisState.copy(
                stmtIn =
                    functionAnalysisState.stmtIn +
                        mapOf(
                            thenStatements[0] to updatedUniformityRecord,
                        ),
            ),
            thenStatements,
            functionAnalysisContext,
        )

    val afterAnalysingElseSide: FunctionAnalysisState =
        if (elseStatements == null) {
            afterAnalysingThenSide
        } else {
            analyseStatements(
                afterAnalysingThenSide.copy(
                    stmtIn =
                        afterAnalysingThenSide.stmtIn +
                            mapOf(
                                elseStatements[0] to updatedUniformityRecord,
                            ),
                ),
                elseStatements,
                functionAnalysisContext,
            )
        }
    val endOfThenSideRecord: StatementUniformityRecord? = afterAnalysingElseSide.stmtOut[thenStatements.last()]
    val endOfElseSideRecord: StatementUniformityRecord? =
        elseStatements?.let {
            afterAnalysingElseSide.stmtOut[elseStatements.last()]
        } ?: inStmt

    val mergedPresent: PresentInfo? =
        if ((endOfThenSideRecord == null || endOfThenSideRecord.presentInfo == null) &&
            (endOfElseSideRecord == null || endOfElseSideRecord.presentInfo == null)
        ) {
            null
        } else {
            PresentInfo(
                ifControls = inStmt.presentInfo!!.ifControls,
                variableUniformityInfo =
                    mergeMaps(
                        endOfThenSideRecord?.presentInfo?.variableUniformityInfo,
                        endOfElseSideRecord?.presentInfo?.variableUniformityInfo,
                    ),
            )
        }
    val mergedRecord =
        StatementUniformityRecord(
            presentInfo = mergedPresent,
            breakControls = (endOfThenSideRecord?.breakControls ?: emptySet()) union (endOfElseSideRecord?.breakControls ?: emptySet()),
            continueControls =
                (endOfThenSideRecord?.continueControls ?: emptySet()) union (endOfElseSideRecord?.continueControls ?: emptySet()),
            returnControls = (endOfThenSideRecord?.returnControls ?: emptySet()) union (endOfElseSideRecord?.returnControls ?: emptySet()),
        )

    return afterAnalysingElseSide.copy(
        stmtOut = afterAnalysingElseSide.stmtOut + (statement to mergedRecord),
    )
}

private fun analyseLoopStatement(
    statement: Statement.Loop,
    functionAnalysisState: FunctionAnalysisState,
    functionAnalysisContext: FunctionAnalysisContext,
    inStmt: StatementUniformityRecord,
    presentVariables: Map<String, Set<Int>>,
): FunctionAnalysisState {
    // Continuing statements have been added if not present
    assert(statement.continuingStatement != null)
    assert(
        statement.continuingStatement!!
            .statements.statements
            .isNotEmpty(),
    )
    val loopBody = statement.body
    // Continues should have been added to all loops
    assert(loopBody.statements.last() is Statement.Continue)
    val loopBodyStart = loopBody.statements[0]
    val loopBodyEnd = loopBody.statements.last()

    // First, handle transfer into loop body
    var loopAnalysisState =
        run {
            val existingLoopEntryRecord = functionAnalysisState.stmtIn[loopBodyStart]
            val loopEntryRecordUpdatedWithIncomingEdge: StatementUniformityRecord =
                if (existingLoopEntryRecord == null) {
                    StatementUniformityRecord(
                        presentInfo =
                            inStmt.presentInfo!!.copy(
                                ifControls = inStmt.presentInfo.ifControls + listOf(emptySet()),
                            ),
                        breakControls = emptySet(), // This is a fresh loop
                        continueControls = emptySet(), // This is a fresh loop
                        returnControls = inStmt.returnControls,
                    )
                } else {
                    if (functionAnalysisContext.maximalReconvergence) {
                        assert(existingLoopEntryRecord.continueControls.isEmpty())
                    }
                    assert(
                        existingLoopEntryRecord.presentInfo!!.ifControls == inStmt.presentInfo!!.ifControls +
                            listOf(
                                emptySet(),
                            ),
                    )
                    existingLoopEntryRecord.copy(
                        presentInfo =
                            existingLoopEntryRecord.presentInfo.copy(
                                variableUniformityInfo =
                                    mergeMaps(
                                        presentVariables,
                                        existingLoopEntryRecord.presentInfo.variableUniformityInfo,
                                    ),
                            ),
                        // Break information: retain whatever has been propagated via back edges, since on entry this information is empty
                        // Continue information: absent by construction as asserted above
                        returnControls = existingLoopEntryRecord.returnControls union inStmt.returnControls,
                    )
                }

            functionAnalysisState.copy(
                stmtIn = functionAnalysisState.stmtIn + (loopBodyStart to loopEntryRecordUpdatedWithIncomingEdge),
            )
        }

    // loopAnalysisState here is the analysis state after transfer into the loop body

    while (true) {
        // Analyse loop body
        val analysisStateAfterAnalysingBody =
            analyseStatements(
                statements = loopBody.statements,
                initialAnalysisState = loopAnalysisState,
                functionAnalysisContext = functionAnalysisContext,
            )

        // Continues should have been added to the end of loop bodies.
        assert(loopBodyEnd is Statement.Continue)

        // Handle transfer into continuing statement
        val continueStatementStart = statement.continuingStatement.statements.statements[0]
        val existingContinueStatementStartRecord: StatementUniformityRecord? =
            analysisStateAfterAnalysingBody.stmtIn[continueStatementStart]
        if (existingContinueStatementStartRecord == null) {
            // It is not possible to get to the continuing statement.
            loopAnalysisState = analysisStateAfterAnalysingBody
            break
        }
        if (functionAnalysisContext.maximalReconvergence) {
            assert(existingContinueStatementStartRecord.continueControls.isEmpty())
        }

        // Analyse continuing statement
        val analysisStateAfterAnalysingContinuingStatement =
            analyseStatements(
                statements = statement.continuingStatement.statements.statements,
                initialAnalysisState = analysisStateAfterAnalysingBody,
                functionAnalysisContext = functionAnalysisContext,
            )

        // Handle back edge to loop header
        val existingLoopEntryRecord: StatementUniformityRecord = analysisStateAfterAnalysingContinuingStatement.stmtIn[loopBodyStart]!!
        val continueStatementEnd =
            statement.continuingStatement.statements.statements
                .last()
        val finalContinuingStatementRecord = analysisStateAfterAnalysingContinuingStatement.stmtOut[continueStatementEnd]!!

        val updatedContinueControls: Set<Int> =
            if (functionAnalysisContext.maximalReconvergence) {
                assert(finalContinuingStatementRecord.continueControls.isEmpty())
                assert(existingLoopEntryRecord.continueControls.isEmpty())
                emptySet()
            } else {
                existingLoopEntryRecord.continueControls union finalContinuingStatementRecord.continueControls
            }

        var updatedBreakControls: Set<Int>
        val analysisStateAfterAnalysingBreakIf =
            if (statement.continuingStatement.breakIfExpr != null) {
                val parametersAffectingBreakIfCondition =
                    determineParametersAffectingUniformityOfExpressionResult(
                        expression = statement.continuingStatement.breakIfExpr,
                        functionAnalysisContext = functionAnalysisContext,
                        presentVariables = finalContinuingStatementRecord.presentInfo!!.variableUniformityInfo,
                    )
                updatedBreakControls = parametersAffectingBreakIfCondition union finalContinuingStatementRecord.breakControls

                val existingOutUniformityRecordForLoop: StatementUniformityRecord? =
                    analysisStateAfterAnalysingContinuingStatement.stmtOut[statement]
                val newOutUniformityRecordForLoop: StatementUniformityRecord =
                    if (existingOutUniformityRecordForLoop == null) {
                        StatementUniformityRecord(
                            presentInfo =
                                PresentInfo(
                                    ifControls = inStmt.presentInfo!!.ifControls,
                                    variableUniformityInfo = finalContinuingStatementRecord.presentInfo.variableUniformityInfo,
                                ),
                            breakControls = inStmt.breakControls,
                            continueControls = inStmt.continueControls,
                            returnControls = inStmt.returnControls union finalContinuingStatementRecord.returnControls,
                        )
                    } else {
                        assert(existingOutUniformityRecordForLoop.presentInfo != null)
                        existingOutUniformityRecordForLoop.copy(
                            presentInfo =
                                existingOutUniformityRecordForLoop.presentInfo!!.copy(
                                    variableUniformityInfo =
                                        mergeMaps(
                                            existingOutUniformityRecordForLoop.presentInfo.variableUniformityInfo,
                                            finalContinuingStatementRecord.presentInfo.variableUniformityInfo,
                                        ),
                                ),
                            returnControls =
                                existingOutUniformityRecordForLoop.returnControls union finalContinuingStatementRecord.returnControls,
                        )
                    }
                analysisStateAfterAnalysingContinuingStatement.updateOutForStatement(
                    statement = statement,
                    uniformityRecord = newOutUniformityRecordForLoop,
                )
            } else {
                updatedBreakControls = finalContinuingStatementRecord.breakControls
                analysisStateAfterAnalysingContinuingStatement
            }

        val loopEntryRecordUpdatedWithBackEdge =
            StatementUniformityRecord(
                presentInfo =
                    existingLoopEntryRecord.presentInfo!!.copy(
                        variableUniformityInfo =
                            mergeMaps(
                                existingLoopEntryRecord.presentInfo.variableUniformityInfo,
                                finalContinuingStatementRecord.presentInfo?.variableUniformityInfo,
                            ),
                    ),
                breakControls = updatedBreakControls,
                continueControls = updatedContinueControls,
                returnControls = existingLoopEntryRecord.returnControls union finalContinuingStatementRecord.returnControls,
            )
        val updatedAnalysisState =
            analysisStateAfterAnalysingBreakIf.copy(
                stmtIn = analysisStateAfterAnalysingContinuingStatement.stmtIn + (loopBodyStart to loopEntryRecordUpdatedWithBackEdge),
            )
        if (loopAnalysisState == updatedAnalysisState) {
            // Fixpoint reached
            break
        }
        loopAnalysisState = updatedAnalysisState
    }
    // Note: Transfer to loop exit is handled via the analysis of break statements.
    return loopAnalysisState
}

private fun analyseReturnStatement(
    statement: Statement.Return,
    functionAnalysisState: FunctionAnalysisState,
    functionAnalysisContext: FunctionAnalysisContext,
    inStmt: StatementUniformityRecord,
    presentVariables: Map<String, Set<Int>>,
    presentControls: Set<Int>,
): FunctionAnalysisState {
    val returnedIdentifiers: Set<String> = collectNamesInExpression(statement.expression!!)
    val parametersInfluencingReturnedValueUniformity =
        (
            presentControls union inStmt.breakControls union inStmt.continueControls union
                inStmt.returnControls
        ).toMutableSet()
    for (identifier in returnedIdentifiers) {
        if (identifier in functionAnalysisContext.variables) {
            parametersInfluencingReturnedValueUniformity.addAll(presentVariables[identifier]!!)
        } else {
            check(identifier in functionAnalysisContext.parameters)
            parametersInfluencingReturnedValueUniformity.add(functionAnalysisContext.parameters[identifier]!!)
        }
    }
    return functionAnalysisState
        .copy(
            returnedValueUniformity =
                functionAnalysisState.returnedValueUniformity union parametersInfluencingReturnedValueUniformity,
        ).updateOutForStatement(
            statement = statement,
            uniformityRecord =
                inStmt.copy(
                    presentInfo = null,
                    returnControls = inStmt.returnControls union presentControls,
                ),
        )
}

private fun analyseBreakStatement(
    statement: Statement.Break,
    functionAnalysisState: FunctionAnalysisState,
    functionAnalysisContext: FunctionAnalysisContext,
    inStmt: StatementUniformityRecord,
    presentVariables: Map<String, Set<Int>>,
): FunctionAnalysisState {
    val associatedLoop: Statement.Loop = functionAnalysisContext.breakToLoop[statement]!!
    val existingInUniformityRecordForLoop: StatementUniformityRecord = functionAnalysisState.stmtIn[associatedLoop]!!
    val existingOutUniformityRecordForLoop: StatementUniformityRecord? = functionAnalysisState.stmtOut[associatedLoop]
    val newOutUniformityRecordForLoop: StatementUniformityRecord =
        if (existingOutUniformityRecordForLoop == null) {
            StatementUniformityRecord(
                presentInfo =
                    PresentInfo(
                        ifControls = existingInUniformityRecordForLoop.presentInfo!!.ifControls,
                        variableUniformityInfo = presentVariables,
                    ),
                breakControls = existingInUniformityRecordForLoop.breakControls,
                continueControls = existingInUniformityRecordForLoop.continueControls,
                returnControls = existingInUniformityRecordForLoop.returnControls union inStmt.returnControls,
            )
        } else {
            assert(existingOutUniformityRecordForLoop.presentInfo != null)
            existingOutUniformityRecordForLoop.copy(
                presentInfo =
                    existingOutUniformityRecordForLoop.presentInfo!!.copy(
                        variableUniformityInfo =
                            mergeMaps(
                                existingOutUniformityRecordForLoop.presentInfo.variableUniformityInfo,
                                presentVariables,
                            ),
                    ),
                returnControls = existingOutUniformityRecordForLoop.returnControls union inStmt.returnControls,
            )
        }

    return functionAnalysisState
        .updateOutForStatement(
            statement = statement,
            uniformityRecord =
                inStmt.copy(
                    presentInfo = null,
                    breakControls = inStmt.breakControls union inStmt.presentInfo!!.ifControls.last(),
                ),
        ).updateOutForStatement(
            statement = associatedLoop,
            uniformityRecord = newOutUniformityRecordForLoop,
        )
}

private fun analyseContinueStatement(
    statement: Statement.Continue,
    functionAnalysisContext: FunctionAnalysisContext,
    functionAnalysisState: FunctionAnalysisState,
    inStmt: StatementUniformityRecord,
    presentVariables: Map<String, Set<Int>>,
): FunctionAnalysisState {
    val associatedLoop: Statement.Loop = functionAnalysisContext.continueToLoop[statement]!!
    val existingInUniformityRecordForLoop: StatementUniformityRecord =
        functionAnalysisState.stmtIn[associatedLoop]!!
    val startOfContinuingStatement = associatedLoop.continuingStatement!!.statements.statements[0]
    val existingInUniformityRecordForContinuingStatement: StatementUniformityRecord? =
        functionAnalysisState.stmtIn[startOfContinuingStatement]

    val strengthenedContinueControls = inStmt.continueControls union inStmt.presentInfo!!.ifControls.last()

    val newInUniformityRecordForContinuingStatement: StatementUniformityRecord =
        if (existingInUniformityRecordForContinuingStatement ==
            null
        ) {
            StatementUniformityRecord(
                presentInfo =
                    PresentInfo(
                        ifControls = existingInUniformityRecordForLoop.presentInfo!!.ifControls,
                        variableUniformityInfo = presentVariables,
                    ),
                breakControls = inStmt.breakControls,
                continueControls =
                    if (functionAnalysisContext.maximalReconvergence) {
                        emptySet()
                    } else {
                        strengthenedContinueControls
                    },
                returnControls = existingInUniformityRecordForLoop.returnControls union inStmt.returnControls,
            )
        } else {
            assert(existingInUniformityRecordForContinuingStatement.presentInfo != null)
            existingInUniformityRecordForContinuingStatement.copy(
                presentInfo =
                    existingInUniformityRecordForContinuingStatement.presentInfo!!.copy(
                        variableUniformityInfo =
                            mergeMaps(
                                existingInUniformityRecordForContinuingStatement.presentInfo.variableUniformityInfo,
                                presentVariables,
                            ),
                    ),
                breakControls =
                    existingInUniformityRecordForContinuingStatement.breakControls union inStmt.breakControls,
                continueControls =
                    if (functionAnalysisContext.maximalReconvergence) {
                        emptySet()
                    } else {
                        existingInUniformityRecordForContinuingStatement.continueControls union strengthenedContinueControls
                    },
                returnControls = existingInUniformityRecordForContinuingStatement.returnControls union inStmt.returnControls,
            )
        }
    return functionAnalysisState
        .updateOutForStatement(
            statement = statement,
            uniformityRecord =
                inStmt.copy(
                    presentInfo = null,
                    continueControls = strengthenedContinueControls,
                ),
        ).updateInForStatement(
            statement = startOfContinuingStatement,
            uniformityRecord = newInUniformityRecordForContinuingStatement,
        )
}

private fun mergeMaps(
    variableUniformityInfo1: Map<String, Set<Int>>?,
    variableUniformityInfo2: Map<String, Set<Int>>?,
): Map<String, Set<Int>> {
    if (variableUniformityInfo1 == null && variableUniformityInfo2 == null) {
        throw UnsupportedOperationException("Cannot merge two null maps.")
    }
    if (variableUniformityInfo1 == null) {
        return variableUniformityInfo2!!
    }
    if (variableUniformityInfo2 == null) {
        return variableUniformityInfo1
    }
    assert(variableUniformityInfo1.keys == variableUniformityInfo2.keys)
    return variableUniformityInfo1.keys.associateWith {
        variableUniformityInfo1[it]!! union variableUniformityInfo2[it]!!
    }
}

private fun collectNamesInExpression(expr: Expression): Set<String> {
    fun action(
        node: AstNode,
        names: MutableSet<String>,
    ) {
        traverse(::action, node, names)
        if (node is Expression.Identifier) {
            names.add(node.name)
        }
        if (node is Expression.FunctionCall) {
            throw UnsupportedOperationException(
                "Function call expression should only occur at the top level and should not occur in conditions.",
            )
        }
    }
    val result = mutableSetOf<String>()
    action(expr, result)
    return result
}

private fun determineParametersAffectingReturnValueUniformityForCall(
    calleeSummary: FunctionAnalysisState,
    callerArguments: List<Expression>,
    functionAnalysisContext: FunctionAnalysisContext,
    variableUniformityInfo: Map<String, Set<Int>>,
): Set<Int> =
    callerArguments
        .mapIndexed { index, arg ->
            if (index !in calleeSummary.returnedValueUniformity) {
                emptySet<Int>()
            } else {
                collectNamesInExpression(arg)
                    .map { name ->
                        if (name in functionAnalysisContext.variables) {
                            variableUniformityInfo[name]!!
                        } else {
                            setOf(functionAnalysisContext.parameters[name]!!)
                        }
                    }.fold(emptySet()) { acc, set -> acc union set }
            }
        }.fold(emptySet<Int>()) { acc, set -> acc union set }

private fun determineParametersRequiredToBeUniformFromCall(
    calleeSummary: FunctionAnalysisState,
    callerArguments: List<Expression>,
    functionAnalysisContext: FunctionAnalysisContext,
    variableUniformityInfo: Map<String, Set<Int>>,
): Set<Int> =
    callerArguments
        .mapIndexed { index, arg ->
            if (index !in calleeSummary.uniformParams) {
                emptySet<Int>()
            } else {
                collectNamesInExpression(arg)
                    .map { name ->
                        if (name in functionAnalysisContext.variables) {
                            variableUniformityInfo[name]!!
                        } else {
                            setOf(functionAnalysisContext.parameters[name]!!)
                        }
                    }.fold(emptySet()) { acc, set -> acc union set }
            }
        }.fold(emptySet<Int>()) { acc, set -> acc union set }

private fun addContinuingStatements(node: AstNode): AstNode =
    node.clone {
        if (it is Statement.Loop && it.continuingStatement == null) {
            // Adds a continuing statement as this was absent.
            Statement.Loop(
                attributesAtStart = it.attributesAtStart,
                attributesBeforeBody = it.attributesBeforeBody,
                body = addContinuingStatements(it.body) as Statement.Compound,
                continuingStatement =
                    ContinuingStatement(
                        statements =
                            Statement.Compound(
                                listOf(Statement.Empty()),
                            ),
                    ),
            )
        } else if (it is Statement.Loop &&
            it.continuingStatement!!
                .statements.statements
                .isEmpty()
        ) {
            // Makes the existing continuing statement non-empty.
            Statement.Loop(
                attributesAtStart = it.attributesAtStart,
                attributesBeforeBody = it.attributesBeforeBody,
                body = addContinuingStatements(it.body) as Statement.Compound,
                continuingStatement =
                    ContinuingStatement(
                        attributes = it.continuingStatement.attributes,
                        statements =
                            Statement.Compound(
                                listOf(Statement.Empty()),
                            ),
                        breakIfExpr = it.continuingStatement.breakIfExpr,
                    ),
            )
        } else {
            null
        }
    }

private fun addContinuesToEndsOfLoops(node: AstNode): AstNode =
    node.clone {
        if (it is Statement.Loop && (it.body.statements.isEmpty() || it.body.statements.last() !is Statement.Continue)) {
            // Adds a continue to the end of the loop body.
            val newBodyStatements = mutableListOf<Statement>()
            for (statement in it.body.statements) {
                newBodyStatements.add(addContinuesToEndsOfLoops(statement) as Statement)
            }
            newBodyStatements.add(Statement.Continue())
            Statement.Loop(
                attributesAtStart = it.attributesAtStart,
                attributesBeforeBody = it.attributesBeforeBody,
                body =
                    Statement.Compound(
                        statements = newBodyStatements,
                    ),
                continuingStatement =
                    it.continuingStatement?.let { continuingStatement ->
                        addContinuesToEndsOfLoops(
                            continuingStatement,
                        ) as ContinuingStatement
                    },
            )
        } else {
            null
        }
    }

private fun determineParametersAffectingUniformityOfExpressionResult(
    expression: Expression,
    functionAnalysisContext: FunctionAnalysisContext,
    presentVariables: Map<String, Set<Int>>,
): Set<Int> {
    val result = mutableSetOf<Int>()
    val identifiersOccurringInExpression = collectNamesInExpression(expression)
    identifiersOccurringInExpression.forEach {
        if (it in functionAnalysisContext.parameters) {
            result.add(functionAnalysisContext.parameters[it]!!)
        } else {
            assert(it in functionAnalysisContext.variables)
            result.addAll(presentVariables[it]!!)
        }
    }
    return result
}
