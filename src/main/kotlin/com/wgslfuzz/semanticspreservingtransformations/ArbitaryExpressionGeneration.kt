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

import com.wgslfuzz.core.AstNode
import com.wgslfuzz.core.AugmentedExpression
import com.wgslfuzz.core.BinaryOperator
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.Scope
import com.wgslfuzz.core.ShaderJob
import com.wgslfuzz.core.Type
import com.wgslfuzz.core.UnaryOperator
import com.wgslfuzz.core.clone
import kotlin.random.Random

const val I32_LOWEST_VALUE = -0x80000000
const val I32_HIGHEST_VALUE = 0x7fffffff

fun generateArbitraryExpression(
    depth: Int,
    type: Type,
    sideEffectsAllowed: Boolean,
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
): Expression =
    when (type) {
        is Type.Bool -> generateArbitraryBool(depth, sideEffectsAllowed, fuzzerSettings, shaderJob, scope)
        is Type.I32, is Type.U32 -> generateArbitraryInt(depth, sideEffectsAllowed, fuzzerSettings, shaderJob, scope, type)
        is Type.F32 -> {
            // Fix this to not rely on hacky solution.
            // The hacky solution works by getting a known value with a random integer value and then replacing every
            // location of AugmentedExpression.KnownValue in the returned AST tree with
            // AugmentedExpression.ArbitraryExpression which then creates an arbitrary expression.
            // TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/109)
            generateKnownValueExpression(
                depth = depth,
                knownValue = constantWithSameValueEverywhere(fuzzerSettings.randomInt(LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE), type),
                type = type,
                fuzzerSettings = fuzzerSettings,
                shaderJob = shaderJob,
            ).clone(::replaceKnownValueWithArbitraryExpression)
        }
        // TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/42): Support arbitrary expression generation
        else -> constantWithSameValueEverywhere(1, type)
    }

private fun replaceKnownValueWithArbitraryExpression(node: AstNode): AstNode? =
    when (node) {
        is AugmentedExpression.KnownValue ->
            AugmentedExpression.ArbitraryExpression(
                node.expression.clone(::replaceKnownValueWithArbitraryExpression),
            )
        else -> null
    }

private fun generateArbitraryBool(
    depth: Int,
    sideEffectsAllowed: Boolean,
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
): AugmentedExpression.ArbitraryExpression {
    val nonRecursiveChoices: List<Pair<Int, () -> Expression>> =
        listOfNotNull(
            fuzzerSettings.arbitraryBooleanExpressionWeights.literal(depth) to {
                Expression.BoolLiteral(
                    text = fuzzerSettings.randomElement(listOf("true", "false")),
                )
            },
            if (isVariableOfTypeInScope(scope, Type.Bool)) {
                fuzzerSettings.arbitraryBooleanExpressionWeights
                    .variableFromScope(depth) to {
                    randomVariableFromScope(scope, Type.Bool, fuzzerSettings)!!
                }
            } else {
                null
            },
        )

    fun arbitraryIntComparison(operator: BinaryOperator): Expression {
        val type = fuzzerSettings.randomElement(listOf(Type.I32, Type.U32))
        return Expression.Binary(
            operator = operator,
            lhs =
                generateArbitraryInt(
                    depth = depth + 1,
                    sideEffectsAllowed,
                    fuzzerSettings,
                    shaderJob,
                    scope,
                    type,
                ),
            rhs =
                generateArbitraryInt(
                    depth = depth + 1,
                    sideEffectsAllowed,
                    fuzzerSettings,
                    shaderJob,
                    scope,
                    type,
                ),
        )
    }

    val recursiveChoices: List<Pair<Int, () -> Expression>> =
        listOf(
            fuzzerSettings.arbitraryBooleanExpressionWeights.not(depth) to {
                Expression.Unary(
                    operator = UnaryOperator.LOGICAL_NOT,
                    target =
                        generateArbitraryBool(
                            depth = depth + 1,
                            sideEffectsAllowed,
                            fuzzerSettings,
                            shaderJob,
                            scope,
                        ),
                )
            },
            fuzzerSettings.arbitraryBooleanExpressionWeights.or(depth) to {
                Expression.Binary(
                    operator = BinaryOperator.SHORT_CIRCUIT_OR,
                    lhs =
                        generateArbitraryBool(
                            depth = depth + 1,
                            sideEffectsAllowed,
                            fuzzerSettings,
                            shaderJob,
                            scope,
                        ),
                    rhs =
                        generateArbitraryBool(
                            depth = depth + 1,
                            sideEffectsAllowed,
                            fuzzerSettings,
                            shaderJob,
                            scope,
                        ),
                )
            },
            fuzzerSettings.arbitraryBooleanExpressionWeights.and(depth) to {
                Expression.Binary(
                    operator = BinaryOperator.SHORT_CIRCUIT_AND,
                    lhs =
                        generateArbitraryBool(
                            depth = depth + 1,
                            sideEffectsAllowed,
                            fuzzerSettings,
                            shaderJob,
                            scope,
                        ),
                    rhs =
                        generateArbitraryBool(
                            depth = depth + 1,
                            sideEffectsAllowed,
                            fuzzerSettings,
                            shaderJob,
                            scope,
                        ),
                )
            },
            fuzzerSettings.arbitraryBooleanExpressionWeights.lessThan(depth) to {
                arbitraryIntComparison(BinaryOperator.LESS_THAN)
            },
            fuzzerSettings.arbitraryBooleanExpressionWeights.greaterThan(depth) to {
                arbitraryIntComparison(BinaryOperator.GREATER_THAN)
            },
            fuzzerSettings.arbitraryBooleanExpressionWeights.lessThanOrEqual(depth) to {
                arbitraryIntComparison(BinaryOperator.LESS_THAN_EQUAL)
            },
            fuzzerSettings.arbitraryBooleanExpressionWeights.greaterThanOrEqual(depth) to {
                arbitraryIntComparison(BinaryOperator.GREATER_THAN_EQUAL)
            },
            fuzzerSettings.arbitraryBooleanExpressionWeights.equal(depth) to {
                arbitraryIntComparison(BinaryOperator.EQUAL_EQUAL)
            },
            fuzzerSettings.arbitraryBooleanExpressionWeights.notEqual(depth) to {
                arbitraryIntComparison(BinaryOperator.NOT_EQUAL)
            },
        )

    return AugmentedExpression.ArbitraryExpression(
        if (fuzzerSettings.goDeeper(depth)) {
            choose(fuzzerSettings, recursiveChoices + nonRecursiveChoices)
        } else {
            choose(fuzzerSettings, nonRecursiveChoices)
        },
    )
}

private fun generateArbitraryInt(
    depth: Int,
    sideEffectsAllowed: Boolean,
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
    outputType: Type.Integer,
): AugmentedExpression.ArbitraryExpression {
    require(outputType !is Type.AbstractInteger) { "outputType must be a concrete type" }

    val literalSuffix =
        when (outputType) {
            Type.I32 -> "i"
            Type.U32 -> "u"
            Type.AbstractInteger -> throw RuntimeException("Cannot get here require above should guard against this")
        }

    val nonRecursiveChoices: List<Pair<Int, () -> Expression>> =
        listOfNotNull(
            fuzzerSettings.arbitraryIntExpressionWeights.literal(depth) to {
                Expression.IntLiteral(
                    (I32_LOWEST_VALUE..I32_HIGHEST_VALUE)
                        .random(Random(fuzzerSettings.randomInt(Int.MAX_VALUE)))
                        .toString() + literalSuffix,
                )
            },
            if (isVariableOfTypeInScope(scope, outputType)) {
                fuzzerSettings.arbitraryIntExpressionWeights
                    .variableFromScope(depth) to {
                    randomVariableFromScope(scope, outputType, fuzzerSettings)!!
                }
            } else {
                null
            },
        )

    fun arbitraryBinaryOperation(operator: BinaryOperator) =
        Expression.Binary(
            operator = operator,
            lhs =
                generateArbitraryInt(
                    depth = depth + 1,
                    sideEffectsAllowed,
                    fuzzerSettings,
                    shaderJob,
                    scope,
                    outputType,
                ),
            rhs =
                generateArbitraryInt(
                    depth = depth + 1,
                    sideEffectsAllowed,
                    fuzzerSettings,
                    shaderJob,
                    scope,
                    outputType,
                ),
        )

    // Overflow characteristics of i32 and u32 are define here: https://www.w3.org/TR/WGSL/#integer-types
    // Since generating an arbitrary expression do not care what they are just that they exist.
    val recursiveChoices: List<Pair<Int, () -> Expression>> =
        listOf(
            fuzzerSettings.arbitraryIntExpressionWeights.binaryOr(depth) to {
                arbitraryBinaryOperation(BinaryOperator.BINARY_OR)
            },
            fuzzerSettings.arbitraryIntExpressionWeights.binaryAnd(depth) to {
                arbitraryBinaryOperation(BinaryOperator.BINARY_AND)
            },
            fuzzerSettings.arbitraryIntExpressionWeights.binaryXor(depth) to {
                arbitraryBinaryOperation(BinaryOperator.BINARY_XOR)
            },
            fuzzerSettings.arbitraryIntExpressionWeights.negate(depth) to {
                Expression.Unary(
                    operator = UnaryOperator.MINUS,
                    target =
                        generateArbitraryInt(
                            depth = depth + 1,
                            sideEffectsAllowed,
                            fuzzerSettings,
                            shaderJob,
                            scope,
                            outputType,
                        ),
                )
            },
            fuzzerSettings.arbitraryIntExpressionWeights.addition(depth) to {
                arbitraryBinaryOperation(BinaryOperator.PLUS)
            },
            fuzzerSettings.arbitraryIntExpressionWeights.subtraction(depth) to {
                arbitraryBinaryOperation(BinaryOperator.MINUS)
            },
            fuzzerSettings.arbitraryIntExpressionWeights.multiplication(depth) to {
                arbitraryBinaryOperation(BinaryOperator.TIMES)
            },
            fuzzerSettings.arbitraryIntExpressionWeights.division(depth) to {
                arbitraryBinaryOperation(BinaryOperator.DIVIDE)
            },
            fuzzerSettings.arbitraryIntExpressionWeights.modulo(depth) to {
                arbitraryBinaryOperation(BinaryOperator.MODULO)
            },
        )

    return AugmentedExpression.ArbitraryExpression(
        if (fuzzerSettings.goDeeper(depth)) {
            choose(fuzzerSettings, recursiveChoices + nonRecursiveChoices)
        } else {
            choose(fuzzerSettings, nonRecursiveChoices)
        },
    )
}
