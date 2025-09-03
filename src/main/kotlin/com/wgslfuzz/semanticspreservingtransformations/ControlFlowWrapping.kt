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
import com.wgslfuzz.core.GlobalDecl
import com.wgslfuzz.core.LhsExpression
import com.wgslfuzz.core.Scope
import com.wgslfuzz.core.ShaderJob
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.SwitchClause
import com.wgslfuzz.core.Type
import com.wgslfuzz.core.TypeDecl
import com.wgslfuzz.core.clone
import com.wgslfuzz.core.nodesPreOrder
import com.wgslfuzz.core.traverse
import java.util.SortedSet
import kotlin.collections.get
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
            val endPointRangeStart = it.second.value.first
            val endPointRangeEnd = it.second.value.last
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
        returnTypeDecl: TypeDecl?,
        uniqueId: Int,
        depth: Int = 0,
    ): AugmentedStatement.ControlFlowWrapper {
        val scope = shaderJob.environment.scopeAvailableBefore(originalStatements[0])
        return AugmentedStatement.ControlFlowWrapper(
            statement = wrapInControlFlowHelper(originalStatements, injections, returnTypeDecl, uniqueId, depth, scope),
            id = uniqueId,
        )
    }

    private fun wrapInControlFlowHelper(
        originalStatements: List<Statement>,
        injections: ControlFlowWrappingsInjections = mutableMapOf(),
        returnTypeDecl: TypeDecl?,
        uniqueId: Int,
        depth: Int = 0,
        scope: Scope,
    ): Statement {
        require(originalStatements.isNotEmpty()) { "Cannot control flow wrap an empty list of statements" }

        val containsBreakOrContinue =
            originalStatements.any { stmt -> nodesPreOrder(stmt).any { it is Statement.Break || it is Statement.Continue } }

        val originalStatementCompound =
            Statement.Compound(
                originalStatements.clone {
                    injectControlFlowWrapper(it, injections, returnTypeDecl)
                },
                metadata = AugmentedMetadata.ControlFlowWrapperMetaData(uniqueId),
            )

        val choices =
            listOfNotNull(
                fuzzerSettings.controlFlowWrappingWeights.ifTrueWrapping(depth) to {
                    // `if ( <true expression> ) { <original statements> } else { <arbitrary statements> }`
                    Statement.If(
                        attributes = emptyList(),
                        condition = generateTrueByConstructionExpression(fuzzerSettings, shaderJob, scope),
                        thenBranch = originalStatementCompound,
                        elseBranch =
                            generateArbitraryElseBranch(
                                depth = 0,
                                sideEffectsAllowed = true,
                                fuzzerSettings = fuzzerSettings,
                                shaderJob = shaderJob,
                                scope = scope,
                            ),
                    )
                },
                fuzzerSettings.controlFlowWrappingWeights.ifFalseWrapping(depth) to {
                    // `if ( <false expression> ) { <arbitrary statements> } else { <original statements> }`
                    Statement.If(
                        attributes = emptyList(),
                        condition = generateFalseByConstructionExpression(fuzzerSettings, shaderJob, scope),
                        thenBranch =
                            generateArbitraryCompound(
                                depth = 0,
                                sideEffectsAllowed = true,
                                fuzzerSettings = fuzzerSettings,
                                shaderJob = shaderJob,
                                scope = scope,
                            ),
                        elseBranch = originalStatementCompound,
                    )
                },
                if (containsBreakOrContinue) {
                    // Cannot wrap statements in a loop when the statements contain a `break` or `continue`
                    null
                } else {
                    // Single iteration for loop control flow wrapper
                    fuzzerSettings.controlFlowWrappingWeights.singleIterForLoop(depth) to {
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
                                        computeForUpdate = { _, counterName, intToExpression ->
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

                        Statement.For(
                            attributes = emptyList(),
                            init = init,
                            condition = condition,
                            update = update,
                            body = originalStatementCompound,
                        )
                    }
                },
                if (containsBreakOrContinue) {
                    // Cannot wrap statements in a loop when the statements contain a `break` or `continue`
                    null
                } else {
                    // Wraps <original statements> using the following:
                    // loop {
                    //    <part of original statements>
                    //    continuing {
                    //        <rest of original statements>
                    //        break-if true_by_construction
                    //    }
                    // }
                    fuzzerSettings.controlFlowWrappingWeights.singleIterLoop(depth) to {
                        val statements = originalStatementCompound.statements

                        val indexOfLastReturn =
                            statements
                                .indexOfLast { node ->
                                    nodesPreOrder(node).any { it is Statement.Return }
                                }

                        // splitIndex is in the range of [indexOfLastReturn + 1, statements.size)
                        val splitIndex = fuzzerSettings.randomInt(statements.size - indexOfLastReturn) + indexOfLastReturn + 1
                        val partOfStmts = Statement.Compound(statements.subList(0, splitIndex), originalStatementCompound.metadata)
                        val restOfStmts =
                            Statement.Compound(
                                statements.subList(splitIndex, statements.size),
                                originalStatementCompound.metadata,
                            )

                        val continuingStatement =
                            ContinuingStatement(
                                statements = restOfStmts,
                                breakIfExpr = generateTrueByConstructionExpression(fuzzerSettings, shaderJob, scope),
                            )

                        Statement.Loop(
                            body = partOfStmts,
                            continuingStatement = continuingStatement,
                        )
                    }
                },
                if (containsBreakOrContinue) {
                    null
                } else {
                    // Wraps <original statements> in:
                    // while {
                    //     <original statements>
                    //     ControlFlowWrappedHelper {
                    //         break
                    //     }
                    // }
                    fuzzerSettings.controlFlowWrappingWeights.singleIterWhileLoop(depth) to {
                        val wrappedBreak =
                            AugmentedStatement.ControlFlowWrapHelperStatement(
                                statement =
                                    wrapInControlFlowHelper(
                                        originalStatements = listOf(Statement.Break()),
                                        returnTypeDecl = returnTypeDecl,
                                        uniqueId = 0,
                                        depth = depth + 1,
                                        scope = scope,
                                    ),
                                id = uniqueId,
                            )

                        val whileBody =
                            Statement.Compound(
                                // TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/223)
                                // Add some arbitrary statements to end of statements
                                statements = originalStatementCompound.statements + wrappedBreak,
                                metadata = originalStatementCompound.metadata,
                            )

                        Statement.While(
                            condition = generateTrueByConstructionExpression(fuzzerSettings, shaderJob, scope),
                            body = whileBody,
                        )
                    }
                },
                // Wraps <original statements> in:
                // switch known_value {
                //    case ...
                //    ...
                //    case literal_for_the_known_value { Stmts }
                //    case ...
                // }
                fuzzerSettings.controlFlowWrappingWeights.switchCase(depth) to {
                    val randomNumber = fuzzerSettings.randomInt(LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE).toString()
                    val randomType = fuzzerSettings.randomElement(Type.I32, Type.U32)
                    val numberOfCases = fuzzerSettings.controlFlowWrappingWeights.switchCaseProbabilities.numberOfCases(fuzzerSettings)

                    val knownValue =
                        when (randomType) {
                            Type.I32 -> Expression.IntLiteral(randomNumber + "i")
                            Type.U32 -> Expression.IntLiteral(randomNumber + "u")
                            Type.AbstractInteger -> throw RuntimeException("randomType cannot be an AbstractInteger")
                        }
                    val knownValueExpression =
                        generateKnownValueExpression(
                            depth = depth + 1,
                            knownValue = knownValue,
                            type = randomType,
                            fuzzerSettings = fuzzerSettings,
                            shaderJob = shaderJob,
                            scope = scope,
                        )

                    val cases = mutableSetOf(randomNumber)
                    repeat(numberOfCases) {
                        var newCase = fuzzerSettings.randomInt(LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE).toString()
                        while (newCase in cases) {
                            newCase = fuzzerSettings.randomInt(LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE).toString()
                        }
                        cases.add(newCase)
                    }

                    val caseExpressions =
                        cases
                            .shuffled(Random(fuzzerSettings.randomInt(Int.MAX_VALUE)))
                            .map {
                                if (it == randomNumber) knownValue else Expression.IntLiteral(it)
                            } + null

                    val clauses = mutableListOf<SwitchClause>()
                    var i = 0
                    while (i < caseExpressions.size) {
                        val numberInCase =
                            fuzzerSettings.controlFlowWrappingWeights.switchCaseProbabilities.numberOfCasesInAClause(
                                fuzzerSettings,
                            )
                        val nextIndex = (i + numberInCase).coerceAtMost(caseExpressions.size)
                        val cases = caseExpressions.subList(i, nextIndex)
                        clauses.add(
                            SwitchClause(
                                caseSelectors = cases,
                                compoundStatement =
                                    if (knownValue in cases) {
                                        originalStatementCompound
                                    } else {
                                        generateArbitraryCompound(
                                            depth = depth + 1,
                                            sideEffectsAllowed = true,
                                            fuzzerSettings = fuzzerSettings,
                                            shaderJob = shaderJob,
                                            scope = scope,
                                        )
                                    },
                            ),
                        )
                        i = nextIndex
                    }

                    Statement.Switch(
                        expression = knownValueExpression,
                        clauses = clauses,
                    )
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
        injections: ControlFlowWrappingsInjections = mutableMapOf(),
        // The return type of parent function if it exists
        returnTypeDecl: TypeDecl?,
    ): AstNode? {
        if (node is GlobalDecl.Function) {
            // If cloning a GlobalDecl.Function add the functions
            return GlobalDecl.Function(
                node.attributes.clone { injectControlFlowWrapper(it, injections, node.returnType) },
                node.name,
                node.parameters.clone { injectControlFlowWrapper(it, injections, node.returnType) },
                node.returnAttributes.clone { injectControlFlowWrapper(it, injections, node.returnType) },
                node.returnType?.clone { injectControlFlowWrapper(it, injections, node.returnType) },
                node.body.clone { injectControlFlowWrapper(it, injections, node.returnType) },
            )
        }

        return injections[node]?.let { locations ->
            val compound = node as Statement.Compound

            val newBody = mutableListOf<Statement>()

            // This is the id of the control flow wrapped statements that contain the return
            var wrappedReturnUniqueId: Int? = null

            var i = 0
            for ((x, y) in locations) {
                while (i < x) {
                    newBody.add(compound.statements[i].clone { injectControlFlowWrapper(it, injections, returnTypeDecl) })
                    i++
                }

                val uniqueId = fuzzerSettings.getUniqueId()
                val originalStatements = node.statements.subList(x, y)
                if (originalStatements.any { it is Statement.Return || it is AugmentedStatement.ControlFlowWrapReturn }) {
                    check(wrappedReturnUniqueId == null) { "There should not be repeated returns in a compound" }
                    wrappedReturnUniqueId = uniqueId
                }
                newBody.add(wrapInControlFlow(originalStatements, injections, returnTypeDecl, uniqueId))

                i = y
            }
            while (i < compound.statements.size) {
                newBody.add(compound.statements[i].clone { injectControlFlowWrapper(it, injections, returnTypeDecl) })
                i++
            }

            if (wrappedReturnUniqueId != null && returnTypeDecl != null) {
                newBody.add(
                    AugmentedStatement.ControlFlowWrapReturn(
                        Statement.Return(
                            generateArbitraryExpression(
                                depth = 0,
                                type = returnTypeDecl.toType(shaderJob.environment),
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
    }

    fun apply(): ShaderJob {
        val injections: ControlFlowWrappingsInjections = mutableMapOf()
        traverse(::selectStatementsToControlFlowWrap, shaderJob.tu, injections)
        val tu =
            shaderJob.tu.clone {
                injectControlFlowWrapper(
                    node = it,
                    injections = injections,
                    // Since no parent returnTypeDecl leave as null
                    returnTypeDecl = null,
                )
            }
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
