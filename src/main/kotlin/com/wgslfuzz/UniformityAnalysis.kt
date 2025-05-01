package com.wgslfuzz

data class PresentRecord(
    val ifControls: List<Set<String>>,
    val variableUniformityInfo: Map<String, Set<String>>,
)

data class StatementUniformityRecord(
    val present: PresentRecord?,
    val breakControls: Set<String>,
    val breakVariableUniformityInfo: Map<String, Set<String>>,
    val continueControls: Set<String>,
    val continueVariableUniformityInfo: Map<String, Set<String>>,
    val returnControls: Set<String>,
) {
    fun updateVariableUniformityInfo(variable: String, newParameters: Set<String>): StatementUniformityRecord =
        this.copy(
            present = present!!.copy(
                variableUniformityInfo = present.variableUniformityInfo + (variable to newParameters),
            )
        )

    fun strengthenIfControl(newParameters: Set<String>): StatementUniformityRecord {
        val newIfControls: MutableList<Set<String>> =
            present!!.ifControls.dropLast(1).toMutableList()
        newIfControls.add(present.ifControls.last() union newParameters)
        return this.copy(
            present = present.copy(
                ifControls = newIfControls,
            )
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
    fun transferInToOutWithUpdate(statement: Statement, uniformityRecord: StatementUniformityRecord) =
        this.copy(
            stmtOut = stmtIn + (statement to uniformityRecord)
        )
}

fun runAnalysis(function: GlobalDecl.Function): AnalysisState {
    val parameters : Set<String> = function.parameters.map(ParameterDecl::name).toSet()
    val variables : Set<String> = function.body.filterIsInstance<Statement.Variable>().map(
        Statement.Variable::name).toSet()
    assert((parameters intersect variables).isEmpty())

    val statements = function.body.filter {
        it !is Statement.Variable
    }

    var analysisState = AnalysisState(
        callSiteMustBeUniform = false,
        uniformParams = setOf(),
        returnedValueUniformity = setOf(),
        stmtIn = mapOf(statements[0] to StatementUniformityRecord(
            present = PresentRecord(
                ifControls = listOf(emptySet()),
                variableUniformityInfo = variables.associateWith { emptySet() },
            ),
            breakControls = emptySet(),
            breakVariableUniformityInfo = variables.associateWith { emptySet() },
            continueControls = emptySet(),
            continueVariableUniformityInfo = variables.associateWith { emptySet() },
            returnControls = emptySet(),
        )),
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

fun analyseStatements(initialAnalysisState: AnalysisState,
                      statements: List<Statement>,
                      parameters: Set<String>,
                      variables: Set<String>): AnalysisState {
    var result = initialAnalysisState
    statements.forEachIndexed { index, currentStatement ->
        result = analyseStatement(result, currentStatement, parameters, variables)
        if (index < statements.size - 1) {
            val nextStatement = statements[index + 1]
            val newStmtIn: Map<Statement, StatementUniformityRecord> = result.stmtIn + (nextStatement to result.stmtOut[currentStatement]!!)
            result = result.copy(
                stmtIn = newStmtIn,
            )
        }
    }
    return result
}

fun analyseStatement(analysisState: AnalysisState,
                     statement: Statement,
                     parameters: Set<String>,
                     variables: Set<String>,
                     ): AnalysisState {
    val inStmt = analysisState.stmtIn[statement]!!
    if (inStmt.present == null) {
        return analysisState.copy(
            stmtOut = analysisState.stmtOut + (statement to inStmt)
        )
    }
    val presentVariables = inStmt.present.variableUniformityInfo
    val presentControls = inStmt.present.ifControls.reduce {
        first, second -> first union second
    }
    val breakControls = inStmt.breakControls
    val breakVariableUniformityInfo = inStmt.breakVariableUniformityInfo
    val continueControls = inStmt.continueControls
    val continueVariableUniformityInfo = inStmt.continueVariableUniformityInfo
    val returnControls = inStmt.returnControls
    return when (statement) {
        is Statement.Empty -> analysisState.transferInToOutWithUpdate(statement, inStmt)
        is Statement.Assignment -> {
            val lhsName = (statement.lhsExpression as LhsExpression.Identifier).name
            assert(lhsName in variables)
            when (val rhs = statement.rhs) {
                is Expression.FunctionCall -> {
                    TODO("Not handling calls yet")
                }
                is Expression.IntLiteral -> {
                    // The statement has the form "v = C"
                    analysisState.transferInToOutWithUpdate(
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
                            presentControls union breakControls union continueControls union returnControls union presentVariables[rhs.name]!!
                        )
                    }
                    analysisState.transferInToOutWithUpdate(statement, newUniformityRecord)
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
                    val presentVariablesBinaryLhs = maybeBinaryLhsName?.let {
                        presentVariables[it]!!
                    } ?: emptySet()

                    val presentVariablesBinaryRhs = maybeBinaryRhsName?.let {
                        presentVariables[it]!!
                    } ?: emptySet()

                    val newUniformityRecord =
                            inStmt.updateVariableUniformityInfo(
                                lhsName,
                                presentControls union breakControls union continueControls union returnControls union presentVariablesBinaryLhs union presentVariablesBinaryRhs
                            )
                    analysisState.transferInToOutWithUpdate(statement, newUniformityRecord)
                }
                else -> TODO()
            }
        }
        is Statement.FunctionCall -> {
            assert(statement.callee == "barrier")
            analysisState.copy(
                callSiteMustBeUniform = true,
                uniformParams = analysisState.uniformParams union presentControls union breakControls union continueControls union returnControls,
            ).transferInToOutWithUpdate(statement, inStmt)
        }
        is Statement.If -> {
            val conditionVar = (statement.condition as Expression.Identifier).name
            assert(conditionVar in variables)
            val thenStatements = statement.thenBranch.statements
            val elseStatements = (statement.elseBranch as Statement.Compound).statements
            val updatedUniformityRecord: StatementUniformityRecord = inStmt.strengthenIfControl(
                presentVariables[conditionVar]!!
            )
            val intermediateState = analysisState.copy(
                stmtIn = analysisState.stmtIn + mapOf(
                    thenStatements[0] to updatedUniformityRecord,
                    elseStatements[0] to updatedUniformityRecord,
                ),
            )
            val afterAnalysingThenSide = analyseStatements(intermediateState,
                thenStatements,
                parameters,
                variables,
            )
            val afterAnalysingElseSide = analyseStatements(afterAnalysingThenSide,
                elseStatements,
                parameters,
                variables,
            )
            val endOfThenSideRecord = afterAnalysingThenSide.stmtOut[thenStatements.last()]!!
            val endOfElseSideRecord = afterAnalysingElseSide.stmtOut[elseStatements.last()]!!

            val mergedPresent: PresentRecord? = if (endOfThenSideRecord.present == null && endOfElseSideRecord.present == null) {
                null
            } else {
                PresentRecord(
                    ifControls = inStmt.present.ifControls,
                    variableUniformityInfo = mergeMaps(
                        endOfThenSideRecord.present?.variableUniformityInfo,
                        endOfElseSideRecord.present?.variableUniformityInfo,
                    )
                )
            }
            val mergedRecord = StatementUniformityRecord(
                present = mergedPresent,
                breakControls = endOfThenSideRecord.breakControls union endOfElseSideRecord.breakControls,
                breakVariableUniformityInfo = mergeMaps(endOfThenSideRecord.breakVariableUniformityInfo, endOfElseSideRecord.breakVariableUniformityInfo),
                continueControls = endOfThenSideRecord.continueControls union endOfElseSideRecord.continueControls,
                continueVariableUniformityInfo = mergeMaps(endOfThenSideRecord.continueVariableUniformityInfo, endOfElseSideRecord.continueVariableUniformityInfo),
                returnControls = endOfThenSideRecord.returnControls union endOfElseSideRecord.returnControls,
            )

            afterAnalysingElseSide.copy(
                stmtOut = afterAnalysingElseSide.stmtOut + (statement to mergedRecord)
            )
        }
        is Statement.Return -> {
            val returnedVariableName = (statement.expression!! as Expression.Identifier).name
            assert(returnedVariableName in variables)
            analysisState.copy(
                returnedValueUniformity =
                    analysisState.returnedValueUniformity union presentControls union breakControls union continueControls union returnControls union presentVariables[returnedVariableName]!!
            ).transferInToOutWithUpdate(
                statement=statement,
                uniformityRecord = inStmt.copy(
                    present = null,
                    returnControls = inStmt.returnControls union presentControls,
                ),
            )
        }
        is Statement.Loop -> {
            // Not currently handled: continuing statements
            assert(statement.continuingStatement == null)
            val loopBody = statement.body
            val loopBodyStart = loopBody[0]
            val loopBodyEnd = loopBody.last()

            // First, handle transfer into loop body
            var loopAnalysisState = run {
                val existingLoopEntryRecord = analysisState.stmtIn[loopBodyStart]
                val loopEntryRecordUpdatedWithIncomingEdge: StatementUniformityRecord =
                    if (existingLoopEntryRecord == null) {
                        StatementUniformityRecord(
                            present = inStmt.present.copy(
                                ifControls = inStmt.present.ifControls + listOf(emptySet()),
                            ),
                            breakControls = emptySet(), // This is a fresh loop
                            breakVariableUniformityInfo = variables.associateWith { emptySet() },
                            continueControls = emptySet(), // This is a fresh loop
                            continueVariableUniformityInfo = variables.associateWith { emptySet() },
                            returnControls = inStmt.returnControls,
                        )
                    } else {
                        assert(existingLoopEntryRecord.continueControls.isEmpty())
                        assert(existingLoopEntryRecord.continueVariableUniformityInfo == variables.associateWith { emptySet<String>() })
                        assert(
                            existingLoopEntryRecord.present!!.ifControls == inStmt.present.ifControls + listOf(
                                emptySet()
                            )
                        )
                        existingLoopEntryRecord.copy(
                            present = existingLoopEntryRecord.present.copy(
                                variableUniformityInfo = mergeMaps(
                                    presentVariables,
                                    existingLoopEntryRecord.present.variableUniformityInfo
                                ),
                            ),
                            // Break information: retain whatever has been propagated via back edges, since on entry this information is empty
                            // Continue information: empty by construction as asserted above
                            returnControls = existingLoopEntryRecord.returnControls union returnControls,
                        )
                    }

                analysisState.copy(
                    stmtIn = analysisState.stmtIn + (loopBodyStart to loopEntryRecordUpdatedWithIncomingEdge)
                )
            }

            while (true) {
                // Analyse loop body
                val analysisStateAfterAnalysingBody = analyseStatements(
                    statements = loopBody,
                    initialAnalysisState = loopAnalysisState,
                    variables = variables,
                    parameters = parameters,
                )

                // Transfer back to loop header
                val finalLoopStatementRecord: StatementUniformityRecord = analysisStateAfterAnalysingBody.stmtOut[loopBodyEnd]!!
                val existingLoopEntryRecord: StatementUniformityRecord = analysisStateAfterAnalysingBody.stmtIn[loopBodyStart]!!
                val loopEntryRecordUpdatedWithBackEdge: StatementUniformityRecord = StatementUniformityRecord(
                    present = existingLoopEntryRecord.present!!.copy(
                        variableUniformityInfo =
                            mergeMaps(
                                mergeMaps(existingLoopEntryRecord.present.variableUniformityInfo, finalLoopStatementRecord.present?.variableUniformityInfo),
                                finalLoopStatementRecord.continueVariableUniformityInfo,
                            ),
                    ),
                    breakControls = finalLoopStatementRecord.breakControls, // No need to merge here as this is guaranteed to be larger
                    breakVariableUniformityInfo = finalLoopStatementRecord.breakVariableUniformityInfo,
                    continueControls = existingLoopEntryRecord.continueControls,
                    continueVariableUniformityInfo = existingLoopEntryRecord.continueVariableUniformityInfo,
                    returnControls = existingLoopEntryRecord.returnControls union finalLoopStatementRecord.returnControls,
                )
                val analysisStateAfterTransferBackToLoopHeader: AnalysisState = analysisStateAfterAnalysingBody.copy(
                    stmtIn = analysisStateAfterAnalysingBody.stmtIn + (loopBodyStart to loopEntryRecordUpdatedWithBackEdge)
                )
                if (loopAnalysisState == analysisStateAfterTransferBackToLoopHeader) {
                    // Fixpoint reached
                    break
                }
                loopAnalysisState = analysisStateAfterTransferBackToLoopHeader
            }

            // Transfer to loop exit
            run {
                val finalLoopStatementRecord: StatementUniformityRecord = loopAnalysisState.stmtOut[loopBodyEnd]!!
                val loopExitRecord: StatementUniformityRecord = StatementUniformityRecord(
                    present = PresentRecord(
                        ifControls = inStmt.present.ifControls,
                        variableUniformityInfo = mergeMaps(
                            finalLoopStatementRecord.present?.variableUniformityInfo,
                            finalLoopStatementRecord.breakVariableUniformityInfo,
                        )
                    ),
                    breakControls = inStmt.breakControls,
                    breakVariableUniformityInfo = inStmt.breakVariableUniformityInfo,
                    continueControls = inStmt.continueControls,
                    continueVariableUniformityInfo = inStmt.continueVariableUniformityInfo,
                    returnControls = finalLoopStatementRecord.returnControls,
                )
                loopAnalysisState.copy(
                    stmtOut = loopAnalysisState.stmtOut + (statement to loopExitRecord),
                )
            }
        }
        is Statement.Break -> {
            analysisState.transferInToOutWithUpdate(
                statement = statement,
                uniformityRecord = inStmt.copy(
                    present = null,
                    breakControls = inStmt.breakControls union inStmt.present.ifControls.last(),
                    breakVariableUniformityInfo = mergeMaps(breakVariableUniformityInfo, presentVariables),
                )
            )
        }
        is Statement.Continue -> {
            analysisState.transferInToOutWithUpdate(
                statement = statement,
                uniformityRecord = inStmt.copy(
                    present = null,
                    continueControls = inStmt.continueControls union inStmt.present.ifControls.last(),
                    continueVariableUniformityInfo = mergeMaps(continueVariableUniformityInfo, presentVariables),
                )
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
