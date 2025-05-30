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

import com.wgslfuzz.core.AugmentedExpression
import com.wgslfuzz.core.BinaryOperator
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.ParsedShaderJob
import com.wgslfuzz.core.ScopeEntry
import com.wgslfuzz.core.Type
import com.wgslfuzz.core.TypeDecl
import com.wgslfuzz.core.UnaryOperator
import com.wgslfuzz.core.getUniformDeclaration

interface FuzzerSettings {
    val maxDepth: Int
        get() = 10

    // Yields a random integer in the range [0, limit)
    fun randomInt(limit: Int): Int

    fun randomBool(): Boolean

    fun <T> randomElement(list: List<T>) = list[randomInt(list.size)]

    data class FalseByConstructionWeights(
        val plainFalse: (depth: Int) -> Int = { 1 },
        val falseAndArbitrary: (depth: Int) -> Int = { 3 },
        val arbitraryAndFalse: (depth: Int) -> Int = { 3 },
        val notTrue: (depth: Int) -> Int = { 3 },
        val opaqueFalseFromUniformValues: (depth: Int) -> Int = { 6 },
    )

    data class TrueByConstructionWeights(
        val plainTrue: (depth: Int) -> Int = { 1 },
        val trueOrArbitrary: (depth: Int) -> Int = { 3 },
        val arbitraryOrTrue: (depth: Int) -> Int = { 3 },
        val notFalse: (depth: Int) -> Int = { 3 },
        val opaqueTrueFromUniformValues: (depth: Int) -> Int = { 6 },
    )

    val falseByConstructionWeights: FalseByConstructionWeights
        get() = FalseByConstructionWeights()

    val trueByConstructionWeights: TrueByConstructionWeights
        get() = TrueByConstructionWeights()

    fun injectDeadBreak(): Boolean = randomInt(100) < 50

    fun injectDeadContinue(): Boolean = randomInt(100) < 50

    fun injectDeadDiscard(): Boolean = randomInt(100) < 50

    fun injectDeadReturn(): Boolean = randomInt(100) < 50
}

fun <T> choose(
    fuzzerSettings: FuzzerSettings,
    choices: List<Pair<Int, () -> T>>,
): T {
    val functions = mutableListOf<() -> T>()
    for (choice in choices) {
        (0..<choice.first).forEach { _ ->
            functions.add(choice.second)
        }
    }
    return fuzzerSettings.randomElement(functions)()
}

fun randomUniformScalarWithValue(
    parsedShaderJob: ParsedShaderJob,
    fuzzerSettings: FuzzerSettings,
): Pair<Expression, Expression> {
    val groups =
        parsedShaderJob.uniformValues.keys
            .toList()
            .sorted()
    val group = fuzzerSettings.randomElement(groups)
    val bindings =
        parsedShaderJob.uniformValues[group]!!
            .keys
            .toList()
            .sorted()
    val binding = fuzzerSettings.randomElement(bindings)
    val uniformDeclaration = parsedShaderJob.tu.getUniformDeclaration(group, binding)
    val typename: String = (uniformDeclaration.type as TypeDecl.NamedType).name

    var currentType: Type = (parsedShaderJob.environment.globalScope.getEntry(typename) as ScopeEntry.Struct).type
    var currentUniformExpr: Expression = Expression.Identifier(uniformDeclaration.name)
    var currentValueExpr: Expression = parsedShaderJob.uniformValues[group]!![binding]!!

    while (true) {
        when (currentType) {
            is Type.I32 -> break
            is Type.F32 -> break
            is Type.Vector -> {
                val randomVectorIndex = fuzzerSettings.randomInt(currentType.width)
                currentType = currentType.elementType
                currentUniformExpr = Expression.IndexLookup(currentUniformExpr, Expression.IntLiteral(randomVectorIndex.toString()))
                currentValueExpr = (currentValueExpr as Expression.VectorValueConstructor).args[randomVectorIndex]
            }
            is Type.Struct -> {
                val randomMemberIndex = fuzzerSettings.randomInt(currentType.members.size)
                val randomMember = currentType.members[randomMemberIndex]
                currentType = randomMember.second
                currentUniformExpr = Expression.MemberLookup(currentUniformExpr, randomMember.first)
                currentValueExpr = (currentValueExpr as Expression.StructValueConstructor).args[randomMemberIndex]
            }
            else -> TODO()
        }
    }
    return Pair(currentUniformExpr, currentValueExpr)
}

private fun generateFalseByConstructionExpression(
    depth: Int,
    fuzzerSettings: FuzzerSettings,
    parsedShaderJob: ParsedShaderJob,
): AugmentedExpression.FalseByConstruction {
    if (depth >= fuzzerSettings.maxDepth) {
        return AugmentedExpression.FalseByConstruction(
            Expression.BoolLiteral("false"),
        )
    }
    val choices: List<Pair<Int, () -> AugmentedExpression.FalseByConstruction>> =
        listOf(
            // A plain "false"
            fuzzerSettings.falseByConstructionWeights.plainFalse(depth) to {
                AugmentedExpression.FalseByConstruction(
                    Expression.BoolLiteral("false"),
                )
            },
            // A false expression && an arbitrary expression
            fuzzerSettings.falseByConstructionWeights.falseAndArbitrary(depth) to {
                AugmentedExpression.FalseByConstruction(
                    Expression.Binary(
                        operator = BinaryOperator.SHORT_CIRCUIT_AND,
                        generateFalseByConstructionExpression(depth + 1, fuzzerSettings, parsedShaderJob),
                        generateArbitraryExpression(
                            depth = depth + 1,
                            type = Type.Bool,
                            sideEffectsAllowed = true,
                            fuzzerSettings = fuzzerSettings,
                            parsedShaderJob = parsedShaderJob,
                        ),
                    ),
                )
            },
            // An arbitrary expression && a false expression
            fuzzerSettings.falseByConstructionWeights.arbitraryAndFalse(depth) to {
                AugmentedExpression.FalseByConstruction(
                    Expression.Binary(
                        operator = BinaryOperator.SHORT_CIRCUIT_AND,
                        generateArbitraryExpression(
                            depth = depth + 1,
                            type = Type.Bool,
                            sideEffectsAllowed = false, // No side effects as this will be executable
                            fuzzerSettings = fuzzerSettings,
                            parsedShaderJob = parsedShaderJob,
                        ),
                        generateFalseByConstructionExpression(depth + 1, fuzzerSettings, parsedShaderJob),
                    ),
                )
            },
            // ! true expression
            fuzzerSettings.falseByConstructionWeights.notTrue(depth) to {
                AugmentedExpression.FalseByConstruction(
                    Expression.Unary(
                        operator = UnaryOperator.LOGICAL_NOT,
                        generateTrueByConstructionExpression(depth + 1, fuzzerSettings, parsedShaderJob),
                    ),
                )
            },
            fuzzerSettings.falseByConstructionWeights.opaqueFalseFromUniformValues(depth) to {
                val (uniformScalarExpr, literalExpr) = randomUniformScalarWithValue(parsedShaderJob, fuzzerSettings)
                // Choose randomly on which side of the expression the uniform access should appear.
                // No need for a custom weight for this choice.
                val (lhs, rhs) =
                    if (fuzzerSettings.randomBool()) {
                        Pair(uniformScalarExpr, literalExpr)
                    } else {
                        Pair(literalExpr, uniformScalarExpr)
                    }
                // Choose a random suitable operator.
                // No need for custom weights for this choice.
                val operators = listOf(BinaryOperator.NOT_EQUAL, BinaryOperator.LESS_THAN, BinaryOperator.GREATER_THAN)
                AugmentedExpression.FalseByConstruction(
                    Expression.Binary(
                        fuzzerSettings.randomElement(operators),
                        lhs,
                        rhs,
                    ),
                )
            },
        )
    return choose(fuzzerSettings, choices)
}

private fun generateTrueByConstructionExpression(
    depth: Int,
    fuzzerSettings: FuzzerSettings,
    parsedShaderJob: ParsedShaderJob,
): AugmentedExpression.TrueByConstruction {
    if (depth >= fuzzerSettings.maxDepth) {
        return AugmentedExpression.TrueByConstruction(
            Expression.BoolLiteral("true"),
        )
    }
    val choices: List<Pair<Int, () -> AugmentedExpression.TrueByConstruction>> =
        listOf(
            // A plain "true"
            fuzzerSettings.trueByConstructionWeights.plainTrue(depth) to {
                AugmentedExpression.TrueByConstruction(
                    Expression.BoolLiteral("true"),
                )
            },
            // A true expression || an arbitrary expression
            fuzzerSettings.trueByConstructionWeights.trueOrArbitrary(depth) to {
                AugmentedExpression.TrueByConstruction(
                    Expression.Binary(
                        operator = BinaryOperator.SHORT_CIRCUIT_OR,
                        generateTrueByConstructionExpression(depth + 1, fuzzerSettings, parsedShaderJob),
                        generateArbitraryExpression(
                            depth = depth + 1,
                            type = Type.Bool,
                            sideEffectsAllowed = true,
                            fuzzerSettings = fuzzerSettings,
                            parsedShaderJob = parsedShaderJob,
                        ),
                    ),
                )
            },
            // An arbitrary expression || a true expression
            fuzzerSettings.trueByConstructionWeights.arbitraryOrTrue(depth) to {
                AugmentedExpression.TrueByConstruction(
                    Expression.Binary(
                        operator = BinaryOperator.SHORT_CIRCUIT_OR,
                        generateArbitraryExpression(
                            depth = depth + 1,
                            type = Type.Bool,
                            sideEffectsAllowed = false, // No side effects as this will be executable
                            fuzzerSettings = fuzzerSettings,
                            parsedShaderJob = parsedShaderJob,
                        ),
                        generateTrueByConstructionExpression(depth + 1, fuzzerSettings, parsedShaderJob),
                    ),
                )
            },
            // ! false expression
            fuzzerSettings.trueByConstructionWeights.notFalse(depth) to {
                AugmentedExpression.TrueByConstruction(
                    Expression.Unary(
                        operator = UnaryOperator.LOGICAL_NOT,
                        generateFalseByConstructionExpression(depth + 1, fuzzerSettings, parsedShaderJob),
                    ),
                )
            },
            fuzzerSettings.trueByConstructionWeights.opaqueTrueFromUniformValues(depth) to {
                val (uniformScalarExpr, literalExpr) = randomUniformScalarWithValue(parsedShaderJob, fuzzerSettings)
                // Choose randomly on which side of the expression the uniform access should appear.
                // No need for a custom weight for this choice.
                val (lhs, rhs) =
                    if (fuzzerSettings.randomBool()) {
                        Pair(uniformScalarExpr, literalExpr)
                    } else {
                        Pair(literalExpr, uniformScalarExpr)
                    }
                // Choose a random suitable operator.
                // No need for custom weights for this choice.
                val operators = listOf(BinaryOperator.EQUAL_EQUAL, BinaryOperator.LESS_THAN_EQUAL, BinaryOperator.GREATER_THAN_EQUAL)
                AugmentedExpression.TrueByConstruction(
                    Expression.Binary(
                        fuzzerSettings.randomElement(operators),
                        lhs,
                        rhs,
                    ),
                )
            },
        )
    return choose(fuzzerSettings, choices)
}

fun generateFalseByConstructionExpression(
    fuzzerSettings: FuzzerSettings,
    parsedShaderJob: ParsedShaderJob,
): AugmentedExpression.FalseByConstruction = generateFalseByConstructionExpression(0, fuzzerSettings, parsedShaderJob)

fun generateTrueByConstructionExpression(
    fuzzerSettings: FuzzerSettings,
    parsedShaderJob: ParsedShaderJob,
): AugmentedExpression.TrueByConstruction = generateTrueByConstructionExpression(0, fuzzerSettings, parsedShaderJob)

fun generateArbitraryExpression(
    depth: Int,
    type: Type,
    sideEffectsAllowed: Boolean,
    fuzzerSettings: FuzzerSettings,
    parsedShaderJob: ParsedShaderJob,
): Expression {
    if (type is Type.Bool) {
        return Expression.BoolLiteral("false")
    }
    TODO("Need to support arbitrary expression generation.")
}
