package com.wgslfuzz.semanticspreservingtransformations

import com.wgslfuzz.core.AssignmentOperator
import com.wgslfuzz.core.AstNode
import com.wgslfuzz.core.AugmentedStatement
import com.wgslfuzz.core.BinaryOperator
import com.wgslfuzz.core.ContinuingStatement
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.LhsExpression
import com.wgslfuzz.core.ShaderJob
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.SwitchClause
import com.wgslfuzz.core.Type
import com.wgslfuzz.core.clone
import com.wgslfuzz.core.traverse

private typealias ControlFlowWrappingsInjections = MutableMap<Statement.Compound, Pair<Int, Int>>

private class ControlFlowWrapping(
    private val shaderJob: ShaderJob,
    private val fuzzerSettings: FuzzerSettings,
) {
    private fun selectStatementsToControlFlowWrap(
        node: AstNode,
        injections: ControlFlowWrappingsInjections,
    ) {
        val traverseSubExpression = { node: AstNode -> traverse(::selectStatementsToControlFlowWrap, node, injections) }
        when (node) {
            is Statement.Switch -> return // TODO()
            is SwitchClause -> return // TODO()

            is ContinuingStatement -> return // TODO()

            is Statement.Compound -> {
                assert(node.statements.isNotEmpty())
                traverseSubExpression(node)

                if (!fuzzerSettings.controlFlowWrap()) return

                val allPossibleAcceptableSectionsOfStatements = (0..<node.statements.size).map { it to node.statements.size }
//                    // All possible sublists of contiguous sections of stmts
//                    (0..<node.statements.size)
//                        .asSequence()
//                        .flatMap { i ->
//                            (i + 1..node.statements.size).asSequence().map { j -> Pair(i, j) }
//                        }
//                        // Removes sublists of stmts which have variables declared in them which are used after in the scope
//                        .filter { (x, y) ->
//                            val declaredVariableNamesInStmts =
//                                node.statements.subList(x, y).mapNotNull { stmt ->
//                                    when (stmt) {
//                                        is Statement.Variable -> stmt.name
//                                        is Statement.Value -> stmt.name
//                                        else -> null
//                                    }
//                                }
//
//                            y == node.statements.size ||
//                                !node.statements.subList(y + 1, node.statements.size).any { stmt ->
//                                    astNodeAny(stmt) { node ->
//                                        node is Expression.Identifier && node.name in declaredVariableNamesInStmts
//                                    }
//                                }
//                        }.toList()

                injections[node] = fuzzerSettings.randomElement(allPossibleAcceptableSectionsOfStatements)
            }

            else -> traverseSubExpression(node)
        }
    }

    private fun wrapInControlFlow(
        originalStatements: List<Statement>,
        injections: ControlFlowWrappingsInjections,
        depth: Int = 0,
    ): AugmentedStatement.ControlFlowWrapper {
        require(originalStatements.isNotEmpty()) { "Cannot control flow wrap an empty list of statements" }

        val containsBreakOrContinue =
            originalStatements.any { stmt -> astNodeAny(stmt) { it is Statement.Break || it is Statement.Continue } }

        val originalStatementCompound = Statement.Compound(originalStatements.clone { injectControlFlowWrapper(it, injections) })
        val scope = shaderJob.environment.scopeAvailableBefore(originalStatements[0])

        val choices: List<Pair<Int, () -> AugmentedStatement.ControlFlowWrapper>> =
            listOfNotNull(
                fuzzerSettings.controlFlowWrappingWeights.ifTrueWrapping to {
                    val wrappedStatement =
                        Statement.If(
                            attributes = emptyList(),
                            condition = generateTrueByConstructionExpression(fuzzerSettings, shaderJob, scope),
                            thenBranch = originalStatementCompound,
                            elseBranch =
                                generateArbitraryElseBranch(
                                    sideEffectsAllowed = true,
                                    fuzzerSettings = fuzzerSettings,
                                    shaderJob = shaderJob,
                                    scope = scope,
                                ),
                        )

                    AugmentedStatement.ControlFlowWrapper(
                        statement = wrappedStatement,
                        originalStatement = originalStatementCompound.clone(),
                    )
                },
                fuzzerSettings.controlFlowWrappingWeights.ifFalseWrapping to {
                    val wrappedStatement =
                        Statement.If(
                            attributes = emptyList(),
                            condition = generateFalseByConstructionExpression(fuzzerSettings, shaderJob, scope),
                            thenBranch =
                                generateArbitraryCompound(
                                    sideEffectsAllowed = true,
                                    fuzzerSettings = fuzzerSettings,
                                    shaderJob = shaderJob,
                                    scope = scope,
                                ),
                            elseBranch = originalStatementCompound,
                        )
                    AugmentedStatement.ControlFlowWrapper(
                        statement = wrappedStatement,
                        originalStatement = originalStatementCompound.clone(),
                    )
                },
                if (containsBreakOrContinue) {
                    null
                } else {
                    // Single iteration for loop control flow wrapper
                    fuzzerSettings.controlFlowWrappingWeights.singleIterForLoop to {
                        val (init, condition, update) =
                            fuzzerSettings.randomElement(
                                listOf<() -> Triple<Statement.ForInit, Expression, Statement.ForUpdate>>(
                                    {
                                        // A loop which uses addition to update the loop counter
                                        val updateValue = fuzzerSettings.randomInt(1000) + 1
                                        integerCounterForLoop(
                                            counterName = originalStatements.hashCode().toString(),
                                            depth = depth,
                                            computeFinalValue = { initialValue ->
                                                initialValue + updateValue
                                            },
                                            computeForUpdate = { initialValue, counterName, typeCast ->
                                                Statement.Assignment(
                                                    lhsExpression = LhsExpression.Identifier(counterName),
                                                    assignmentOperator = AssignmentOperator.PLUS_EQUAL,
                                                    rhs = typeCast(Expression.IntLiteral(updateValue.toString())),
                                                )
                                            },
                                        )
                                    },
                                    {
                                        // A loop which uses subtraction to update the loop counter
                                        val updateValue = fuzzerSettings.randomInt(1000) + 1
                                        integerCounterForLoop(
                                            counterName = originalStatements.hashCode().toString(),
                                            depth = depth,
                                            computeFinalValue = { initialValue ->
                                                if (initialValue > updateValue) {
                                                    initialValue - updateValue
                                                } else {
                                                    updateValue - initialValue
                                                }
                                            },
                                            computeForUpdate = { initialValue, counterName, typeCast ->
                                                if (initialValue > updateValue) {
                                                    Statement.Assignment(
                                                        lhsExpression = LhsExpression.Identifier(counterName),
                                                        assignmentOperator = AssignmentOperator.MINUS_EQUAL,
                                                        rhs = typeCast(Expression.IntLiteral(updateValue.toString())),
                                                    )
                                                } else {
                                                    Statement.Assignment(
                                                        lhsExpression = LhsExpression.Identifier(counterName),
                                                        assignmentOperator = AssignmentOperator.EQUAL,
                                                        rhs =
                                                            typeCast(
                                                                Expression.Binary(
                                                                    operator = BinaryOperator.MINUS,
                                                                    lhs = Expression.IntLiteral(updateValue.toString()),
                                                                    rhs = Expression.IntLiteral(initialValue.toString()),
                                                                ),
                                                            ),
                                                    )
                                                }
                                            },
                                        )
                                    },
                                    {
                                        // A simple loop which increments the loop counter
                                        integerCounterForLoop(
                                            counterName = originalStatements.hashCode().toString(),
                                            depth = depth,
                                            computeFinalValue = { initialValue ->
                                                initialValue + 1
                                            },
                                            computeForUpdate = { initialValue, counterName, typeCast ->
                                                Statement.Increment(LhsExpression.Identifier(counterName))
                                            },
                                        )
                                    },
                                ),
                            )()

                        val wrappedStatement =
                            Statement.For(
                                attributes = emptyList(),
                                init = init,
                                condition = condition,
                                update = update,
                                body = originalStatementCompound,
                            )
                        AugmentedStatement.ControlFlowWrapper(
                            statement = wrappedStatement,
                            originalStatement = originalStatementCompound.clone(),
                        )
                    }
                },
            )

        return choose(fuzzerSettings, choices)
    }

    private fun integerCounterForLoop(
        counterName: String,
        depth: Int,
        // Called computeFinalValue(initialValue) where initialValue is the initial value of the counter
        computeFinalValue: (Int) -> Int,
        // Called computeForUpdate(initialValue, counterName, typeCast)
        computeForUpdate: (Int, String, (Expression) -> Expression) -> Statement.ForUpdate,
    ): Triple<Statement.ForInit, Expression, Statement.ForUpdate> {
        val typeCast =
            mapOf<Type, (Expression) -> Expression>(
                Type.I32 to { it },
                Type.U32 to { Expression.U32ValueConstructor(listOf(it)) },
            )

        val initialValue = fuzzerSettings.randomInt(1000)
        val finalValue = computeFinalValue(initialValue)
        check(finalValue >= 0) { "computeFinalValue must return a value greater than or equal to 0" }

        val counterName = "counter_$counterName"
        val type = fuzzerSettings.randomElement(Type.I32, Type.U32)

        val init =
            Statement.Variable(
                name = counterName,
                initializer =
                    generateKnownValueExpression(
                        depth = depth + 1,
                        knownValue = Expression.IntLiteral(initialValue.toString()),
                        type = type,
                        fuzzerSettings = fuzzerSettings,
                        shaderJob = shaderJob,
                    ),
            )

        val update: Statement.ForUpdate = computeForUpdate(initialValue, counterName, typeCast[type]!!)

        val condition =
            binaryExpressionRandomOperandOrder(
                fuzzerSettings = fuzzerSettings,
                operator =
                    fuzzerSettings.randomElement(
                        BinaryOperator.NOT_EQUAL,
                        BinaryOperator.LESS_THAN,
                        BinaryOperator.GREATER_THAN,
                    ),
                operand1 = Expression.Identifier(counterName),
                operand2 =
                    generateKnownValueExpression(
                        depth = depth + 1,
                        knownValue = Expression.IntLiteral(finalValue.toString()),
                        type = type,
                        fuzzerSettings = fuzzerSettings,
                        shaderJob = shaderJob,
                    ),
            )

        return Triple(init, condition, update)
    }

    /**
     * Similar to the `any` function for lists
     */
    private fun astNodeAny(
        node: AstNode,
        predicate: (AstNode) -> Boolean,
    ): Boolean {
        var predicateResult = false

        fun checkNode(node: AstNode) {
            predicateResult = predicateResult || predicate(node)
            if (!predicateResult) {
                traverse({ node, unusedState -> checkNode(node) }, node, null)
            }
        }

        checkNode(node)

        return predicateResult
    }

    // Similar to injectDeadJumps
    private fun injectControlFlowWrapper(
        node: AstNode,
        injections: ControlFlowWrappingsInjections,
    ): AstNode? =
        injections[node]?.let { (x, y) ->
            val compound = node as Statement.Compound
            if (x == 0 && y == compound.statements.size) {
                Statement.Compound(listOf(wrapInControlFlow(node.statements, injections)))
            } else {
                val newBody = mutableListOf<Statement>()
                for (i in 0 until compound.statements.size) {
                    if (i !in x..<y) {
                        newBody.add(compound.statements[i].clone { injectControlFlowWrapper(it, injections) })
                    }
                }

                newBody.add(x, wrapInControlFlow(node.statements.subList(x, y), injections))
                Statement.Compound(newBody)
            }
        }

    fun apply(): ShaderJob {
        val injections: ControlFlowWrappingsInjections = mutableMapOf()
        traverse(::selectStatementsToControlFlowWrap, shaderJob.tu, injections)
        val tu = shaderJob.tu.clone { injectControlFlowWrapper(it, injections) }
        return ShaderJob(
            tu = tu,
            pipelineState = shaderJob.pipelineState,
        )
    }
}

fun controlFlowWrap(
    shaderJob: ShaderJob,
    fuzzerSettings: FuzzerSettings,
): ShaderJob =
    ControlFlowWrapping(
        shaderJob,
        fuzzerSettings,
    ).apply()
