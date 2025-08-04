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
import com.wgslfuzz.core.Scope
import com.wgslfuzz.core.ShaderJob
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.Type
import com.wgslfuzz.core.asStoreTypeIfReference
import com.wgslfuzz.core.clone
import com.wgslfuzz.core.nodesPreOrder
import com.wgslfuzz.core.traverse
import java.util.SortedSet
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

private typealias ControlFlowWrappingsInjections = MutableMap<Statement.Compound, SortedSet<Pair<Int, Int>>>

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
            // Only perform ControlFlowWrapping within compounds
            is Statement.Compound -> {
                traverseSubExpression(node)

                // If compound is empty cannot inject into statements
                if (node.statements.isEmpty()) return

                // This sequence contains a subset of all start and end points where injection of control flow wrappings can occur
                // The sequences ordering is random.
                var allPossibleAcceptableSectionsOfStatements: Sequence<Pair<Int, Lazy<IntRange>>> =
                    (0..<node.statements.size)
                        // Sequence so that map operations is lazy when generating elements
                        .asSequence()
                        // Performance note: shuffled creates a MutableList copy of the sequence and then lazily yields
                        // values from this list.
                        .shuffled(Random(fuzzerSettings.randomInt(Int.MAX_VALUE)))
                        .map { i ->
                            i to
                                lazy {
                                    var x = node.statements.size
                                    while (x - 1 > i &&
                                        checkDeclarations(
                                            declarations = node.statements.subList(i, x - 1),
                                            statementsToCheck = node.statements.subList(x - 1, node.statements.size),
                                        )
                                    ) {
                                        x--
                                    }
                                    x..node.statements.size
                                }
                        }

                // Add injection locations to the compound
                while (fuzzerSettings.controlFlowWrap()) {
                    // If null then no more injection locations
                    val (x, ySequence) = allPossibleAcceptableSectionsOfStatements.firstOrNull() ?: break

                    val random = Random(fuzzerSettings.randomInt(Int.MAX_VALUE))
                    val injectionLocation: Pair<Int, Int> = Pair(x, ySequence.value.random(random))

                    injections.getOrPut(node) { sortedSetOf(compareBy { it.first }) }.add(injectionLocation)

                    // Remove all invalid injection locations given that injectionLocation has got a control flow wrapping it
                    allPossibleAcceptableSectionsOfStatements =
                        removeOverlapping(injectionLocation, allPossibleAcceptableSectionsOfStatements)
                }
            }

            // Potential future issue: Continuing statements must not contain a return within their body.
            // https://www.w3.org/TR/WGSL/#continuing-statement
            is ContinuingStatement -> traverseSubExpression(node)

            else -> traverseSubExpression(node)
        }
    }

    /**
     * Returns true if and only if no variables that are declared in `declarations` are used in `statementsToCheck`
     *
     * Example that would return true:
     * ```
     * var x = 0;
     * --------------------- Beginning of declarations
     * for (var i = 0; i < y; i++) {
     *   x += 2;
     * }
     * --------------------- End of declarations
     * --------------------- Beginning of statementsToCheck
     * return x;
     * --------------------- End of statementsToCheck
     *```
     *
     * Example that would return false:
     * ```
     * --------------------- Beginning of declarations
     * var x = 0;
     * if (y == 1) {
     *   x = 2;
     * }
     * let z = 5 * x;
     * --------------------- End of declarations
     * --------------------- Beginning of statementsToCheck
     * return z + y;
     * --------------------- End of statementsToCheck
     * ```
     */
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

        return statementsToCheck.all { statement ->
            !nodesPreOrder(statement).any {
                when (it) {
                    is LhsExpression.Identifier -> it.name in variableNamesDeclared
                    is Expression.Identifier -> it.name in variableNamesDeclared
                    else -> false
                }
            }
        }
    }

    private fun removeOverlapping(
        indexRange: Pair<Int, Int>,
        ranges: Sequence<Pair<Int, Lazy<IntRange>>>,
    ): Sequence<Pair<Int, Lazy<IntRange>>> =
        ranges.mapNotNull {
            val endPointRangeStart = it.second.value.start // .filter { endPoint -> endPoint <= indexRange.first }
            val endPointRangeEnd = it.second.value.endInclusive
            val newEndPointRangeEnd = min(endPointRangeEnd, indexRange.first)
            if (indexRange.second <= it.first && endPointRangeStart <= newEndPointRangeEnd) {
                it.first to lazy { endPointRangeStart..newEndPointRangeEnd }
            } else {
                null
            }
        }

    private fun wrapInControlFlow(
        originalStatements: List<Statement>,
        injections: ControlFlowWrappingsInjections,
        uniqueId: Int,
        depth: Int = 0,
    ): AugmentedStatement.ControlFlowWrapper {
        require(originalStatements.isNotEmpty()) { "Cannot control flow wrap an empty list of statements" }

        val containsBreakOrContinue =
            originalStatements.any { stmt -> nodesPreOrder(stmt).any { it is Statement.Break || it is Statement.Continue } }

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
                    // `if ( <true expression> ) { <original statements> } else { <arbitrary statements> }`
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
                    // `if ( <false expression> ) { <arbitrary statements> } else { <original statements> }`
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
                    // Cannot wrap statements in a loop when the statements contain a `break` or `continue`
                    null
                } else {
                    // Single iteration for loop control flow wrapper
                    fuzzerSettings.controlFlowWrappingWeights.singleIterForLoop to {
                        val forLoopInitConditionUpdateChoices:
                            List<Pair<Int, () -> Triple<Statement.ForInit, Expression, Statement.ForUpdate>>> =
                            listOf(
                                1 to {
                                    // A loop which uses addition to update the loop counter
                                    // `for (var counter = <x>; counter != <x+y>; counter += <y>) { <original statements> }`
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
                                        conditions = listOf(BinaryOperator.LESS_THAN, BinaryOperator.NOT_EQUAL),
                                        scope = scope,
                                    )
                                },
                                1 to {
                                    // A loop which uses subtraction to update the loop counter
                                    // `for (var counter = <x>; counter != <x-y>; counter -= y) { <original statements> }`
                                    // Or similar depending on if y is bigger than or smaller than x
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
                                        conditions = listOf(BinaryOperator.NOT_EQUAL),
                                        scope = scope,
                                    )
                                },
                                1 to {
                                    // A simple loop which increments the loop counter
                                    // `for (var counter = <x>; counter != <x+1>; counter++) { <original statements> }`
                                    integerCounterForLoop(
                                        counterName = "counter_${abs(originalStatements.hashCode())}",
                                        depth = depth,
                                        computeFinalValue = { initialValue ->
                                            initialValue + 1
                                        },
                                        computeForUpdate = { _, counterName, _ ->
                                            Statement.Increment(LhsExpression.Identifier(counterName))
                                        },
                                        conditions = listOf(BinaryOperator.LESS_THAN, BinaryOperator.NOT_EQUAL),
                                        scope = scope,
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
        conditions: List<BinaryOperator>,
        scope: Scope,
    ): Triple<Statement.ForInit, Expression, Statement.ForUpdate> {
        val initialValue = fuzzerSettings.randomInt(1000)
        val finalValue = computeFinalValue(initialValue)
        check(finalValue >= 0) { "computeFinalValue must return a value greater than or equal to 0" }

        val type = fuzzerSettings.randomElement(Type.I32, Type.U32)
        val intToExpression =
            when (type) {
                is Type.I32 -> { value: Int ->
                    Expression.IntLiteral(value.toString() + "i")
                }
                is Type.U32 -> { value: Int ->
                    Expression.IntLiteral(value.toString() + "u")
                }
                else -> TODO()
            }

        // Init expression that sets counter to initialValue
        val init =
            Statement.Variable(
                name = counterName,
                initializer =
                    generateKnownValueExpression(
                        depth = depth + 1,
                        knownValue = intToExpression(initialValue),
                        type = type,
                        fuzzerSettings = fuzzerSettings,
                        shaderJob = shaderJob,
                        scope = scope,
                    ),
            )

        val update: Statement.ForUpdate = computeForUpdate(initialValue, counterName, intToExpression)

        // condition expression that evaluates to false after running the loop.
        val condition =
            Expression.Binary(
                operator = fuzzerSettings.randomElement(conditions),
                lhs = Expression.Identifier(counterName),
                rhs =
                    generateKnownValueExpression(
                        depth = depth + 1,
                        knownValue = intToExpression(finalValue),
                        type = type,
                        fuzzerSettings = fuzzerSettings,
                        shaderJob = shaderJob,
                        scope = scope,
                    ),
            )

        return Triple(init, condition, update)
    }

    // Similar to injectDeadJumps
    private fun injectControlFlowWrapper(
        node: AstNode,
        injections: ControlFlowWrappingsInjections,
    ): AstNode? =
        injections[node]?.let { locations ->
            val compound = node as Statement.Compound

            val newBody = mutableListOf<Statement>()

            // This is the id of the control flow wrapped statements that contain the return
            var wrappedReturnUniqueId: Int? = null

            var i = 0
            for ((x, y) in locations) {
                while (i < x) {
                    newBody.add(compound.statements[i].clone { injectControlFlowWrapper(it, injections) })
                    i++
                }

                val uniqueId = fuzzerSettings.getUniqueId()
                val originalStatements = node.statements.subList(x, y)
                if (originalStatements.any { it is Statement.Return }) wrappedReturnUniqueId = uniqueId
                newBody.add(wrapInControlFlow(originalStatements, injections, uniqueId))

                i = y
            }
            while (i < compound.statements.size) {
                newBody.add(compound.statements[i].clone { injectControlFlowWrapper(it, injections) })
                i++
            }

            val originalBodyReturn = node.statements.filterIsInstance<Statement.Return>().firstOrNull()
            if (wrappedReturnUniqueId != null && originalBodyReturn?.expression != null) {
                newBody.add(
                    AugmentedStatement.ControlFlowWrapReturn(
                        Statement.Return(
                            generateArbitraryExpression(
                                depth = 0,
                                type = shaderJob.environment.typeOf(originalBodyReturn.expression).asStoreTypeIfReference(),
                                sideEffectsAllowed = true,
                                fuzzerSettings = fuzzerSettings,
                                shaderJob = shaderJob,
                                scope = shaderJob.environment.globalScope,
                            ),
                        ),
                        wrappedReturnUniqueId,
                    ),
                )
            }

            Statement.Compound(newBody, compound.metadata)
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

fun addControlFlowWrappers(
    shaderJob: ShaderJob,
    fuzzerSettings: FuzzerSettings,
): ShaderJob =
    ControlFlowWrapping(
        shaderJob,
        fuzzerSettings,
    ).apply()
