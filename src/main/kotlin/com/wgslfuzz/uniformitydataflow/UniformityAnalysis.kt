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

package com.wgslfuzz.uniformitydataflow

import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.GlobalDecl
import com.wgslfuzz.core.LhsExpression
import com.wgslfuzz.core.ParameterDecl
import com.wgslfuzz.core.Statement

data class PresentInfo(
    val ifControls: List<Set<String>>,
    val variableUniformityInfo: Map<String, Set<String>>,
)

data class BreakContinueInfo(
    val controls: Set<String>,
    val variableUniformityInfo: Map<String, Set<String>>,
)

private fun mergeBreakContinueInfo(
    first: BreakContinueInfo?,
    second: BreakContinueInfo?,
): BreakContinueInfo? {
    if (first == null) {
        return second
    }
    if (second == null) {
        return first
    }
    return BreakContinueInfo(
        controls = first.controls union second.controls,
        variableUniformityInfo = mergeMaps(first.variableUniformityInfo, second.variableUniformityInfo),
    )
}

fun mergeReturnInfo(
    first: Set<String>?,
    second: Set<String>?,
): Set<String>? {
    if (first == null) {
        return second
    }
    if (second == null) {
        return first
    }
    return first union second
}

data class StatementUniformityRecord(
    val presentInfo: PresentInfo?,
    val breakInfo: BreakContinueInfo?,
    val continueInfo: BreakContinueInfo?,
    val returnControls: Set<String>?,
) {
    fun updateVariableUniformityInfo(
        variable: String,
        newParameters: Set<String>,
    ): StatementUniformityRecord =
        this.copy(
            presentInfo =
                presentInfo!!.copy(
                    variableUniformityInfo = presentInfo.variableUniformityInfo + (variable to newParameters),
                ),
        )

    fun strengthenIfControl(newParameters: Set<String>): StatementUniformityRecord {
        val newIfControls: MutableList<Set<String>> =
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

data class AnalysisState(
    val callSiteMustBeUniform: Boolean,
    val uniformParams: Set<String>,
    val returnedValueUniformity: Set<String>,
    val stmtIn: Map<Statement, StatementUniformityRecord>,
    val stmtOut: Map<Statement, StatementUniformityRecord>,
) {
    fun updateOutForStatement(
        statement: Statement,
        uniformityRecord: StatementUniformityRecord,
    ) = this.copy(
        stmtOut = stmtOut + (statement to uniformityRecord),
    )
}

fun runAnalysis(function: GlobalDecl.Function): AnalysisState {
    val parameters: Set<String> = function.parameters.map(ParameterDecl::name).toSet()
    val variables: Set<String> =
        function.body.statements
            .filterIsInstance<Statement.Variable>()
            .map(
                Statement.Variable::name,
            ).toSet()
    assert((parameters intersect variables).isEmpty())

    val statements =
        function.body.statements.filter {
            it !is Statement.Variable
        }

    var analysisState =
        AnalysisState(
            callSiteMustBeUniform = false,
            uniformParams = setOf(),
            returnedValueUniformity = setOf(),
            stmtIn =
                mapOf(
                    statements[0] to
                        StatementUniformityRecord(
                            presentInfo =
                                PresentInfo(
                                    ifControls = listOf(emptySet()),
                                    variableUniformityInfo = variables.associateWith { emptySet() },
                                ),
                            breakInfo = null,
                            continueInfo = null,
                            returnControls = null,
                        ),
                ),
            stmtOut = mapOf(),
        )

    while (true) {
        val newAnalaysisState = analyseStatements(analysisState, statements, parameters, variables)
        if (analysisState == newAnalaysisState) {
            break
        }
        analysisState = newAnalaysisState
    }
    return analysisState
}

fun analyseStatements(
    initialAnalysisState: AnalysisState,
    statements: List<Statement>,
    parameters: Set<String>,
    variables: Set<String>,
): AnalysisState {
    var result = initialAnalysisState
    statements.forEachIndexed { index, currentStatement ->
        result = analyseStatement(result, currentStatement, parameters, variables)
        if (index < statements.size - 1) {
            val nextStatement = statements[index + 1]
            val newStmtIn: Map<Statement, StatementUniformityRecord> = result.stmtIn + (nextStatement to result.stmtOut[currentStatement]!!)
            result =
                result.copy(
                    stmtIn = newStmtIn,
                )
        }
    }
    return result
}

fun analyseStatement(
    analysisState: AnalysisState,
    statement: Statement,
    parameters: Set<String>,
    variables: Set<String>,
): AnalysisState {
    val inStmt = analysisState.stmtIn[statement]!!
    if (inStmt.presentInfo == null) {
        return analysisState.copy(
            stmtOut = analysisState.stmtOut + (statement to inStmt),
        )
    }
    val presentVariables = inStmt.presentInfo.variableUniformityInfo
    val presentControls =
        inStmt.presentInfo.ifControls.reduce { first, second ->
            first union second
        }
    val breakControls = inStmt.breakInfo?.controls ?: emptySet()
    val continueControls = inStmt.continueInfo?.controls ?: emptySet()
    val returnControls = inStmt.returnControls ?: emptySet()
    return when (statement) {
        is Statement.Empty -> analysisState.updateOutForStatement(statement, inStmt)
        is Statement.Assignment -> {
            val lhsName = (statement.lhsExpression as LhsExpression.Identifier).name
            assert(lhsName in variables)
            when (val rhs = statement.rhs) {
                is Expression.FunctionCall -> {
                    TODO("Not handling calls yet")
                }
                is Expression.IntLiteral -> {
                    // The statement has the form "v = C"
                    analysisState.updateOutForStatement(
                        statement,
                        inStmt.updateVariableUniformityInfo(
                            lhsName,
                            presentControls union breakControls union continueControls union returnControls,
                        ),
                    )
                }
                is Expression.Identifier -> {
                    // The statement either has the form "v = x" where x is a parameter,
                    // or "v = w" where w is a variable.
                    val newUniformityRecord =
                        if (rhs.name in parameters) {
                            inStmt.updateVariableUniformityInfo(
                                lhsName,
                                presentControls union breakControls union continueControls union returnControls union setOf(rhs.name),
                            )
                        } else {
                            assert(rhs.name in variables)
                            inStmt.updateVariableUniformityInfo(
                                lhsName,
                                presentControls union breakControls union continueControls union returnControls union
                                    presentVariables[rhs.name]!!,
                            )
                        }
                    analysisState.updateOutForStatement(statement, newUniformityRecord)
                }
                is Expression.Binary -> {
                    val maybeBinaryLhsName =
                        if (rhs.lhs is Expression.Identifier) {
                            rhs.lhs.name
                        } else {
                            assert(rhs.lhs is Expression.IntLiteral)
                            null
                        }
                    val maybeBinaryRhsName =
                        if (rhs.rhs is Expression.Identifier) {
                            rhs.rhs.name
                        } else {
                            assert(rhs.rhs is Expression.IntLiteral)
                            null
                        }
                    maybeBinaryLhsName?.let {
                        assert(it in variables)
                    }
                    maybeBinaryRhsName?.let {
                        assert(it in variables)
                    }
                    val presentVariablesBinaryLhs =
                        maybeBinaryLhsName?.let {
                            presentVariables[it]!!
                        } ?: emptySet()

                    val presentVariablesBinaryRhs =
                        maybeBinaryRhsName?.let {
                            presentVariables[it]!!
                        } ?: emptySet()

                    val newUniformityRecord =
                        inStmt.updateVariableUniformityInfo(
                            lhsName,
                            presentControls union breakControls union continueControls union returnControls union
                                presentVariablesBinaryLhs union
                                presentVariablesBinaryRhs,
                        )
                    analysisState.updateOutForStatement(statement, newUniformityRecord)
                }
                else -> TODO()
            }
        }
        is Statement.FunctionCall -> {
            assert(statement.callee == "workgroupBarrier")
            analysisState
                .copy(
                    callSiteMustBeUniform = true,
                    uniformParams =
                        analysisState.uniformParams union presentControls union breakControls union continueControls union returnControls,
                ).updateOutForStatement(statement, inStmt)
        }
        is Statement.If -> {
            val conditionVar = (statement.condition as Expression.Identifier).name
            assert(conditionVar in variables)
            val thenStatements = statement.thenBranch.statements
            val elseStatements =
                statement.elseBranch?.let {
                    (it as Statement.Compound).statements
                }
            val updatedUniformityRecord: StatementUniformityRecord =
                inStmt.strengthenIfControl(
                    presentVariables[conditionVar]!!,
                )
            val afterAnalysingThenSide: AnalysisState =
                analyseStatements(
                    analysisState.copy(
                        stmtIn =
                            analysisState.stmtIn +
                                mapOf(
                                    thenStatements[0] to updatedUniformityRecord,
                                ),
                    ),
                    thenStatements,
                    parameters,
                    variables,
                )

            val afterAnalysingElseSide: AnalysisState =
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
                        parameters,
                        variables,
                    )
                }
            val endOfThenSideRecord: StatementUniformityRecord = afterAnalysingElseSide.stmtOut[thenStatements.last()]!!
            val endOfElseSideRecord: StatementUniformityRecord =
                elseStatements?.let {
                    afterAnalysingElseSide.stmtOut[elseStatements.last()]!!
                } ?: inStmt

            val mergedPresent: PresentInfo? =
                if (endOfThenSideRecord.presentInfo == null && endOfElseSideRecord.presentInfo == null) {
                    null
                } else {
                    PresentInfo(
                        ifControls = inStmt.presentInfo.ifControls,
                        variableUniformityInfo =
                            mergeMaps(
                                endOfThenSideRecord.presentInfo?.variableUniformityInfo,
                                endOfElseSideRecord.presentInfo?.variableUniformityInfo,
                            ),
                    )
                }
            val mergedRecord =
                StatementUniformityRecord(
                    presentInfo = mergedPresent,
                    breakInfo = mergeBreakContinueInfo(endOfThenSideRecord.breakInfo, endOfElseSideRecord.breakInfo),
                    continueInfo = mergeBreakContinueInfo(endOfThenSideRecord.continueInfo, endOfElseSideRecord.continueInfo),
                    returnControls = mergeReturnInfo(endOfThenSideRecord.returnControls, endOfElseSideRecord.returnControls),
                )

            afterAnalysingElseSide.copy(
                stmtOut = afterAnalysingElseSide.stmtOut + (statement to mergedRecord),
            )
        }
        is Statement.Loop -> {
            // Not currently handled: continuing statements
            assert(statement.continuingStatement == null)
            val loopBody = statement.body
            val loopBodyStart = loopBody.statements[0]
            val loopBodyEnd = loopBody.statements.last()

            // First, handle transfer into loop body
            var loopAnalysisState =
                run {
                    val existingLoopEntryRecord = analysisState.stmtIn[loopBodyStart]
                    val loopEntryRecordUpdatedWithIncomingEdge: StatementUniformityRecord =
                        if (existingLoopEntryRecord == null) {
                            StatementUniformityRecord(
                                presentInfo =
                                    inStmt.presentInfo.copy(
                                        ifControls = inStmt.presentInfo.ifControls + listOf(emptySet()),
                                    ),
                                breakInfo = null, // This is a fresh loop
                                continueInfo = null, // This is a fresh loop
                                returnControls = inStmt.returnControls,
                            )
                        } else {
                            assert(existingLoopEntryRecord.continueInfo == null)
                            assert(
                                existingLoopEntryRecord.presentInfo!!.ifControls == inStmt.presentInfo.ifControls +
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
                                returnControls = mergeReturnInfo(existingLoopEntryRecord.returnControls, returnControls),
                            )
                        }

                    analysisState.copy(
                        stmtIn = analysisState.stmtIn + (loopBodyStart to loopEntryRecordUpdatedWithIncomingEdge),
                    )
                }

            // loopAnalysis state here is the analysis state after transfer into the loop body

            while (true) {
                // Analyse loop body
                val analysisStateAfterAnalysingBody =
                    analyseStatements(
                        statements = loopBody.statements,
                        initialAnalysisState = loopAnalysisState,
                        variables = variables,
                        parameters = parameters,
                    )

                // Transfer back to loop header
                val finalLoopStatementRecord: StatementUniformityRecord = analysisStateAfterAnalysingBody.stmtOut[loopBodyEnd]!!
                if (finalLoopStatementRecord.presentInfo == null && finalLoopStatementRecord.continueInfo == null) {
                    loopAnalysisState = analysisStateAfterAnalysingBody
                    break
                }
                val existingLoopEntryRecord: StatementUniformityRecord = analysisStateAfterAnalysingBody.stmtIn[loopBodyStart]!!
                val loopEntryRecordUpdatedWithBackEdge =
                    StatementUniformityRecord(
                        presentInfo =
                            existingLoopEntryRecord.presentInfo!!.copy(
                                variableUniformityInfo =
                                    mergeMaps(
                                        mergeMaps(
                                            existingLoopEntryRecord.presentInfo.variableUniformityInfo,
                                            finalLoopStatementRecord.presentInfo?.variableUniformityInfo,
                                        ),
                                        finalLoopStatementRecord.continueInfo?.variableUniformityInfo,
                                    ),
                            ),
                        // TODO - reconsider setting break and continue info to null above (with test case)
                        breakInfo = finalLoopStatementRecord.breakInfo, // No need to merge here as this is guaranteed to be larger
                        continueInfo = existingLoopEntryRecord.continueInfo,
                        returnControls = mergeReturnInfo(existingLoopEntryRecord.returnControls, finalLoopStatementRecord.returnControls),
                    )
                val updatedAnalysisState =
                    analysisStateAfterAnalysingBody.copy(
                        stmtIn = analysisStateAfterAnalysingBody.stmtIn + (loopBodyStart to loopEntryRecordUpdatedWithBackEdge),
                    )
                if (loopAnalysisState == updatedAnalysisState) {
                    // Fixpoint reached
                    break
                }
                loopAnalysisState = updatedAnalysisState
            }

            // Transfer to loop exit
            run {
                val finalLoopStatementRecord: StatementUniformityRecord = loopAnalysisState.stmtOut[loopBodyEnd]!!
                val loopExitRecord =
                    StatementUniformityRecord(
                        presentInfo =
                            if (finalLoopStatementRecord.breakInfo == null) {
                                // There is no possibility of breaking from this loop.
                                // Therefore, record that the loop exit is not reachable by nulling out 'present'.
                                null
                            } else {
                                PresentInfo(
                                    ifControls = inStmt.presentInfo.ifControls,
                                    variableUniformityInfo =
                                        mergeMaps(
                                            finalLoopStatementRecord.presentInfo?.variableUniformityInfo,
                                            finalLoopStatementRecord.breakInfo.variableUniformityInfo,
                                        ),
                                )
                            },
                        breakInfo = inStmt.breakInfo,
                        continueInfo = inStmt.continueInfo,
                        returnControls = finalLoopStatementRecord.returnControls,
                    )
                loopAnalysisState.copy(
                    stmtOut = loopAnalysisState.stmtOut + (statement to loopExitRecord),
                )
            }
        }
        is Statement.Return -> {
            val returnedVariableName = (statement.expression!! as Expression.Identifier).name
            assert(returnedVariableName in variables)
            analysisState
                .copy(
                    returnedValueUniformity =
                        analysisState.returnedValueUniformity union presentControls union breakControls union continueControls union
                            returnControls union
                            presentVariables[returnedVariableName]!!,
                ).updateOutForStatement(
                    statement = statement,
                    uniformityRecord =
                        inStmt.copy(
                            presentInfo = null,
                            returnControls = mergeReturnInfo(inStmt.returnControls, presentControls),
                        ),
                )
        }
        is Statement.Break -> {
            analysisState.updateOutForStatement(
                statement = statement,
                uniformityRecord =
                    inStmt.copy(
                        presentInfo = null,
                        breakInfo =
                            BreakContinueInfo(
                                controls = breakControls union inStmt.presentInfo.ifControls.last(),
                                variableUniformityInfo = mergeMaps(inStmt.breakInfo?.variableUniformityInfo, presentVariables),
                            ),
                    ),
            )
        }
        is Statement.Continue -> {
            analysisState.updateOutForStatement(
                statement = statement,
                uniformityRecord =
                    inStmt.copy(
                        presentInfo = null,
                        continueInfo =
                            BreakContinueInfo(
                                controls = continueControls union inStmt.presentInfo.ifControls.last(),
                                variableUniformityInfo = mergeMaps(inStmt.continueInfo?.variableUniformityInfo, presentVariables),
                            ),
                    ),
            )
        }
        else -> TODO()
    }
}

fun mergeMaps(
    variableUniformityInfo1: Map<String, Set<String>>?,
    variableUniformityInfo2: Map<String, Set<String>>?,
): Map<String, Set<String>> {
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
