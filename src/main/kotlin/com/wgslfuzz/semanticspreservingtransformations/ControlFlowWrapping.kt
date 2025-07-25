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

package com.wgslfuzz.semanticspreservingtransformations

import com.wgslfuzz.core.AssignmentOperator
import com.wgslfuzz.core.AstNode
import com.wgslfuzz.core.AugmentedMetadata
import com.wgslfuzz.core.AugmentedStatement
import com.wgslfuzz.core.BinaryOperator
import com.wgslfuzz.core.ContinuingStatement
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.LhsExpression
import com.wgslfuzz.core.ShaderJob
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.Type
import com.wgslfuzz.core.clone
import com.wgslfuzz.core.traverse
import kotlin.math.abs

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
            is Statement.Compound -> {
                traverseSubExpression(node)

                if (node.statements.isEmpty()) return

                var allPossibleAcceptableSectionsOfStatements =
                    // All possible sublists of contiguous sections of stmts
                    (0..<node.statements.size)
                        .asSequence()
                        .flatMap { i ->
                            var x = node.statements.size
                            while (x - 1 > i &&
                                checkDeclarations(node.statements.subList(i, x - 1), node.statements.subList(x - 1, node.statements.size))
                            ) {
                                x--
                            }
                            (x..node.statements.size).asSequence().map { j -> Pair(i, j) }
                        }.toList()

                while (fuzzerSettings.controlFlowWrap() && allPossibleAcceptableSectionsOfStatements.isNotEmpty()) {
                    val injectionLocation = fuzzerSettings.randomElement(allPossibleAcceptableSectionsOfStatements)
                    injections[node] = injectionLocation
                    allPossibleAcceptableSectionsOfStatements =
                        removeOverLapping(injectionLocation, allPossibleAcceptableSectionsOfStatements)
                }
            }

            // Potential future issue: Continuing statements must not contain a return within their body.
            // https://www.w3.org/TR/WGSL/#continuing-statement
            is ContinuingStatement -> traverseSubExpression(node)

            else -> traverseSubExpression(node)
        }
    }

    private fun removeOverLapping(
        indexRange: Pair<Int, Int>,
        ranges: List<Pair<Int, Int>>,
    ): List<Pair<Int, Int>> = ranges.filter { indexRange.second <= it.first || it.second <= indexRange.first }

    private fun wrapInControlFlow(
        originalStatements: List<Statement>,
        injections: ControlFlowWrappingsInjections,
        depth: Int = 0,
    ): AugmentedStatement.ControlFlowWrapper {
        require(originalStatements.isNotEmpty()) { "Cannot control flow wrap an empty list of statements" }

        val containsBreakOrContinue =
            originalStatements.any { stmt -> astNodeAny(stmt) { it is Statement.Break || it is Statement.Continue } }

        val uniqueId = fuzzerSettings.getUniqueId()

        val originalStatementCompound =
            Statement.Compound(
                originalStatements.clone {
                    injectControlFlowWrapper(it, injections)
                },
                metadata = AugmentedMetadata.ControlFlowWrapperMetaData(uniqueId),
            )
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
                        id = uniqueId,
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
                        id = uniqueId,
                    )
                },
                if (containsBreakOrContinue) {
                    null
                } else {
                    // Single iteration for loop control flow wrapper
                    fuzzerSettings.controlFlowWrappingWeights.singleIterForLoop to {
                        val forLoopInitConditionUpdateChoices:
                            List<Pair<Int, () -> Triple<Statement.ForInit, Expression, Statement.ForUpdate>>> =
                            listOf(
                                1 to {
                                    // A loop which uses addition to update the loop counter
                                    val updateValue = fuzzerSettings.randomInt(1000) + 1
                                    integerCounterForLoop(
                                        counterName = "counter_${abs(originalStatements.hashCode())}",
                                        depth = depth,
                                        computeFinalValue = { initialValue ->
                                            initialValue + updateValue
                                        },
                                        computeForUpdate = { initialValue, counterName, intToExpression ->
                                            Statement.Assignment(
                                                lhsExpression = LhsExpression.Identifier(counterName),
                                                assignmentOperator = AssignmentOperator.PLUS_EQUAL,
                                                rhs = intToExpression(updateValue),
                                            )
                                        },
                                    )
                                },
                                1 to {
                                    // A loop which uses subtraction to update the loop counter
                                    val updateValue = fuzzerSettings.randomInt(1000) + 1
                                    integerCounterForLoop(
                                        counterName = "counter_${abs(originalStatements.hashCode())}",
                                        depth = depth,
                                        computeFinalValue = { initialValue ->
                                            if (initialValue > updateValue) {
                                                initialValue - updateValue
                                            } else {
                                                updateValue - initialValue
                                            }
                                        },
                                        computeForUpdate = { initialValue, counterName, intToExpression ->
                                            if (initialValue > updateValue) {
                                                Statement.Assignment(
                                                    lhsExpression = LhsExpression.Identifier(counterName),
                                                    assignmentOperator = AssignmentOperator.MINUS_EQUAL,
                                                    rhs = intToExpression(updateValue),
                                                )
                                            } else {
                                                Statement.Assignment(
                                                    lhsExpression = LhsExpression.Identifier(counterName),
                                                    assignmentOperator = AssignmentOperator.EQUAL,
                                                    rhs =
                                                        Expression.Binary(
                                                            operator = BinaryOperator.MINUS,
                                                            lhs = intToExpression(updateValue),
                                                            rhs = Expression.Identifier(counterName),
                                                        ),
                                                )
                                            }
                                        },
                                    )
                                },
                                1 to {
                                    // A simple loop which increments the loop counter
                                    integerCounterForLoop(
                                        counterName = "counter_${abs(originalStatements.hashCode())}",
                                        depth = depth,
                                        computeFinalValue = { initialValue ->
                                            initialValue + 1
                                        },
                                        computeForUpdate = { _, counterName, _ ->
                                            Statement.Increment(LhsExpression.Identifier(counterName))
                                        },
                                    )
                                },
                            )
                        val (init, condition, update) = choose(fuzzerSettings, forLoopInitConditionUpdateChoices)

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
                            id = uniqueId,
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
        // Called computeForUpdate(initialValue, counterName, intToExpression)
        computeForUpdate: (Int, String, (Int) -> Expression) -> Statement.ForUpdate,
    ): Triple<Statement.ForInit, Expression, Statement.ForUpdate> {
        val intToExpressionWithType =
            mapOf<Type, (Int) -> Expression>(
                Type.I32 to { Expression.IntLiteral(it.toString() + "i") },
                Type.U32 to { Expression.IntLiteral(it.toString() + "u") },
            )

        val initialValue = fuzzerSettings.randomInt(1000)
        val finalValue = computeFinalValue(initialValue)
        check(finalValue >= 0) { "computeFinalValue must return a value greater than or equal to 0" }

        val type = fuzzerSettings.randomElement(Type.I32, Type.U32)

        val init =
            Statement.Variable(
                name = counterName,
                initializer =
                    generateKnownValueExpression(
                        depth = depth + 1,
                        knownValue = intToExpressionWithType[type]!!(initialValue),
                        type = type,
                        fuzzerSettings = fuzzerSettings,
                        shaderJob = shaderJob,
                    ),
            )

        val update: Statement.ForUpdate = computeForUpdate(initialValue, counterName, intToExpressionWithType[type]!!)

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
                        knownValue = intToExpressionWithType[type]!!(finalValue),
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

    private fun checkDeclarations(
        declarations: List<Statement>,
        statementsToCheck: List<Statement>,
    ): Boolean {
        val variableNamesDeclared =
            declarations
                .mapNotNull {
                    when (it) {
                        is Statement.Variable -> it.name
                        is Statement.Value -> it.name
                        else -> null
                    }
                }.toSet()

        for (statement in statementsToCheck) {
            if (astNodeAny(statement) {
                    when (it) {
                        is LhsExpression.Identifier -> it.name in variableNamesDeclared
                        is Expression.Identifier -> it.name in variableNamesDeclared
                        else -> false
                    }
                }
            ) {
                return false
            }
        }
        return true
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
