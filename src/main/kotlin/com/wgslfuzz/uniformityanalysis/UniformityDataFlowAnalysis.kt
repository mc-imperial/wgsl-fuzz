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
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.GlobalDecl
import com.wgslfuzz.core.LhsExpression
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.TranslationUnit
import com.wgslfuzz.core.traverse

data class PresentInfo(
    val ifControls: List<Set<Int>>,
    val variableUniformityInfo: Map<String, Set<Int>>,
)

data class BreakContinueInfo(
    val controls: Set<Int>,
    val variableUniformityInfo: Map<String, Set<Int>>,
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
    first: Set<Int>?,
    second: Set<Int>?,
): Set<Int>? {
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
    val returnControls: Set<Int>?,
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
}

fun runAnalysis(tu: TranslationUnit): Map<String, FunctionAnalysisState> {
    val result = mutableMapOf<String, FunctionAnalysisState>()
    for (decl in tu.globalDecls) {
        assert(decl is GlobalDecl.Function)
        val functionDecl = decl as GlobalDecl.Function
        result[functionDecl.name] = analyseFunction(functionDecl, result)
    }
    return result
}

private fun analyseFunction(
    function: GlobalDecl.Function,
    previouslyAnalysedFunctions: Map<String, FunctionAnalysisState>,
): FunctionAnalysisState {
    val parameters: Map<String, Int> =
        function.parameters
            .mapIndexed { index, decl ->
                decl.name to index
            }.toMap()
    val variables: Set<String> =
        function.body.statements
            .filterIsInstance<Statement.Variable>()
            .map(
                Statement.Variable::name,
            ).toSet()
    assert((parameters.keys intersect variables).isEmpty())

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
                                        variableUniformityInfo = variables.associateWith { emptySet() },
                                    ),
                                breakInfo = null,
                                continueInfo = null,
                                returnControls = null,
                            ),
                    )
                },
            stmtOut = mapOf(),
        )

    while (true) {
        val newAnalaysisState = analyseStatements(functionAnalysisState, statements, parameters, variables, previouslyAnalysedFunctions)
        if (functionAnalysisState == newAnalaysisState) {
            break
        }
        functionAnalysisState = newAnalaysisState
    }
    return functionAnalysisState
}

fun analyseStatements(
    initialFunctionAnalysisState: FunctionAnalysisState,
    statements: List<Statement>,
    parameters: Map<String, Int>,
    variables: Set<String>,
    previouslyAnalysedFunctions: Map<String, FunctionAnalysisState>,
): FunctionAnalysisState {
    var result = initialFunctionAnalysisState
    statements.forEachIndexed { index, currentStatement ->
        result = analyseStatement(result, currentStatement, parameters, variables, previouslyAnalysedFunctions)
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
    functionAnalysisState: FunctionAnalysisState,
    statement: Statement,
    parameters: Map<String, Int>,
    variables: Set<String>,
    previouslyAnalysedFunctions: Map<String, FunctionAnalysisState>,
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
    val breakControls: Set<Int> = inStmt.breakInfo?.controls ?: emptySet()
    val continueControls: Set<Int> = inStmt.continueInfo?.controls ?: emptySet()
    val returnControls: Set<Int> = inStmt.returnControls ?: emptySet()
    return when (statement) {
        is Statement.Empty -> functionAnalysisState.updateOutForStatement(statement, inStmt)
        is Statement.Assignment -> {
            val lhsName = (statement.lhsExpression as LhsExpression.Identifier).name
            assert(lhsName in variables)
            when (val rhs = statement.rhs) {
                is Expression.FunctionCall -> {
                    val calleeSummary = previouslyAnalysedFunctions[rhs.callee]!!

                    val parametersAffectingReturnedValueUniformity: Set<Int> =
                        determineParametersAffectingReturnValueUniformityForCall(
                            calleeSummary = calleeSummary,
                            callerArguments = rhs.args,
                            variables = variables,
                            parameters = parameters,
                            variableUniformityInfo = inStmt.presentInfo.variableUniformityInfo,
                        )

                    val newUniformityRecord =
                        inStmt.updateVariableUniformityInfo(
                            lhsName,
                            presentControls union breakControls union continueControls union returnControls union
                                parametersAffectingReturnedValueUniformity,
                        )

                    val parametersThatMustBeUniformDueToThisCall: Set<Int> =
                        determineParametersRequiredToBeUniformFromCall(
                            calleeSummary = calleeSummary,
                            callerArguments = rhs.args,
                            variables = variables,
                            parameters = parameters,
                            variableUniformityInfo = inStmt.presentInfo.variableUniformityInfo,
                        )

                    if (calleeSummary.callSiteMustBeUniform) {
                        functionAnalysisState
                            .copy(
                                callSiteMustBeUniform = true,
                                uniformParams =
                                    functionAnalysisState.uniformParams union
                                        parametersThatMustBeUniformDueToThisCall union
                                        presentControls union
                                        breakControls union
                                        continueControls union
                                        returnControls,
                            ).updateOutForStatement(statement, newUniformityRecord)
                    } else {
                        functionAnalysisState
                            .copy(
                                uniformParams = functionAnalysisState.uniformParams union parametersThatMustBeUniformDueToThisCall,
                            ).updateOutForStatement(statement, newUniformityRecord)
                    }
                }
                is Expression.IntLiteral -> {
                    // The statement has the form "v = C"
                    functionAnalysisState.updateOutForStatement(
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
                                presentControls union breakControls union continueControls union returnControls union
                                    setOf(parameters[rhs.name]!!),
                            )
                        } else {
                            assert(rhs.name in variables)
                            inStmt.updateVariableUniformityInfo(
                                lhsName,
                                presentControls union breakControls union continueControls union returnControls union
                                    presentVariables[rhs.name]!!,
                            )
                        }
                    functionAnalysisState.updateOutForStatement(statement, newUniformityRecord)
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
                    functionAnalysisState.updateOutForStatement(statement, newUniformityRecord)
                }
                else -> TODO()
            }
        }
        is Statement.FunctionCall -> {
            if (statement.callee == "workgroupBarrier") {
                assert(statement.callee !in previouslyAnalysedFunctions)
                functionAnalysisState
                    .copy(
                        callSiteMustBeUniform = true,
                        uniformParams =
                            functionAnalysisState.uniformParams union presentControls union breakControls union continueControls union
                                returnControls,
                    ).updateOutForStatement(statement, inStmt)
            } else {
                val calleeSummary = previouslyAnalysedFunctions[statement.callee]!!
                // This function is being called via a statement with no return value captured, thus it must have void
                // return type.
                assert(calleeSummary.returnedValueUniformity.isEmpty())

                val parametersThatMustBeUniformDueToThisCall =
                    determineParametersRequiredToBeUniformFromCall(
                        calleeSummary = calleeSummary,
                        callerArguments = statement.args,
                        variables = variables,
                        parameters = parameters,
                        variableUniformityInfo = inStmt.presentInfo.variableUniformityInfo,
                    )

                if (calleeSummary.callSiteMustBeUniform) {
                    functionAnalysisState
                        .copy(
                            callSiteMustBeUniform = true,
                            uniformParams =
                                functionAnalysisState.uniformParams union
                                    parametersThatMustBeUniformDueToThisCall union
                                    presentControls union
                                    breakControls union
                                    continueControls union
                                    returnControls,
                        ).updateOutForStatement(statement, inStmt)
                } else {
                    functionAnalysisState
                        .copy(
                            uniformParams = functionAnalysisState.uniformParams union parametersThatMustBeUniformDueToThisCall,
                        ).updateOutForStatement(statement, inStmt)
                }
            }
        }
        is Statement.If -> {
            val identifiersOccurringInCondition = collectNamesInExpression(statement.condition)
            val parametersAffectingCondition = mutableSetOf<Int>()
            identifiersOccurringInCondition.forEach {
                if (it in parameters) {
                    parametersAffectingCondition.add(parameters[it]!!)
                } else {
                    assert(it in variables)
                    parametersAffectingCondition.addAll(presentVariables[it]!!)
                }
            }
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
                    parameters,
                    variables,
                    previouslyAnalysedFunctions,
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
                        parameters,
                        variables,
                        previouslyAnalysedFunctions,
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
                    val existingLoopEntryRecord = functionAnalysisState.stmtIn[loopBodyStart]
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

                    functionAnalysisState.copy(
                        stmtIn = functionAnalysisState.stmtIn + (loopBodyStart to loopEntryRecordUpdatedWithIncomingEdge),
                    )
                }

            // loopAnalysis state here is the analysis state after transfer into the loop body

            while (true) {
                // Analyse loop body
                val analysisStateAfterAnalysingBody =
                    analyseStatements(
                        statements = loopBody.statements,
                        initialFunctionAnalysisState = loopAnalysisState,
                        variables = variables,
                        parameters = parameters,
                        previouslyAnalysedFunctions = previouslyAnalysedFunctions,
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
            val returnedIdentifiers: Set<String> = collectNamesInExpression(statement.expression!!)
            val parametersInfluencingReturnedValueUniformity =
                (
                    presentControls union breakControls union continueControls union
                        returnControls
                ).toMutableSet()
            for (identifier in returnedIdentifiers) {
                if (identifier in variables) {
                    parametersInfluencingReturnedValueUniformity.addAll(presentVariables[identifier]!!)
                } else {
                    check(identifier in parameters)
                    parametersInfluencingReturnedValueUniformity.add(parameters[identifier]!!)
                }
            }
            functionAnalysisState
                .copy(
                    returnedValueUniformity =
                        functionAnalysisState.returnedValueUniformity union parametersInfluencingReturnedValueUniformity,
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
            functionAnalysisState.updateOutForStatement(
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
            functionAnalysisState.updateOutForStatement(
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
    }
    val result = mutableSetOf<String>()
    action(expr, result)
    return result
}

private fun determineParametersAffectingReturnValueUniformityForCall(
    calleeSummary: FunctionAnalysisState,
    callerArguments: List<Expression>,
    variables: Set<String>,
    parameters: Map<String, Int>,
    variableUniformityInfo: Map<String, Set<Int>>,
): Set<Int> =
    callerArguments
        .mapIndexed { index, arg ->
            if (index !in calleeSummary.returnedValueUniformity) {
                emptySet<Int>()
            } else {
                collectNamesInExpression(arg)
                    .map { name ->
                        if (name in variables) {
                            variableUniformityInfo[name]!!
                        } else {
                            setOf(parameters[name]!!)
                        }
                    }.fold(emptySet()) { acc, set -> acc union set }
            }
        }.fold(emptySet<Int>()) { acc, set -> acc union set }

private fun determineParametersRequiredToBeUniformFromCall(
    calleeSummary: FunctionAnalysisState,
    callerArguments: List<Expression>,
    variables: Set<String>,
    parameters: Map<String, Int>,
    variableUniformityInfo: Map<String, Set<Int>>,
): Set<Int> =
    callerArguments
        .mapIndexed { index, arg ->
            if (index !in calleeSummary.uniformParams) {
                emptySet<Int>()
            } else {
                collectNamesInExpression(arg)
                    .map { name ->
                        if (name in variables) {
                            variableUniformityInfo[name]!!
                        } else {
                            setOf(parameters[name]!!)
                        }
                    }.fold(emptySet()) { acc, set -> acc union set }
            }
        }.fold(emptySet<Int>()) { acc, set -> acc union set }
