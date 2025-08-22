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

private const val I32_LOWEST_VALUE: Long = -0x80000000
private const val I32_HIGHEST_VALUE: Long = 0x7fffffff
private const val U32_LOWEST_VALUE: Long = 0
private const val U32_HIGHEST_VALUE: Long = 0xffffffff

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
                scope = scope,
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
            if (scope.containsVariableOfType(Type.Bool)) {
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

    val outputTypeNumRange =
        when (outputType) {
            is Type.I32 -> I32_LOWEST_VALUE..I32_HIGHEST_VALUE
            is Type.U32 -> U32_LOWEST_VALUE..U32_HIGHEST_VALUE
            Type.AbstractInteger -> throw RuntimeException("Cannot get here require above should guard against this")
        }

    val nonRecursiveChoices: List<Pair<Int, () -> Expression>> =
        listOfNotNull(
            fuzzerSettings.arbitraryIntExpressionWeights.literal(depth) to {
                Expression.IntLiteral(
                    fuzzerSettings
                        .randomElementFromRange(outputTypeNumRange)
                        .toString() + literalSuffix,
                )
            },
            if (scope.containsVariableOfType(outputType)) {
                fuzzerSettings.arbitraryIntExpressionWeights
                    .variableFromScope(depth) to {
                    randomVariableFromScope(scope, outputType, fuzzerSettings)!!
                }
            } else {
                null
            },
        )

    fun generateArbitraryIntHelper(type: Type.Integer) =
        generateArbitraryInt(
            depth = depth + 1,
            sideEffectsAllowed,
            fuzzerSettings,
            shaderJob,
            scope,
            type,
        )

    fun arbitraryBinaryOperation(operator: BinaryOperator) =
        Expression.Binary(
            operator = operator,
            lhs = generateArbitraryIntHelper(outputType),
            rhs = generateArbitraryIntHelper(outputType),
        )

    // Overflow characteristics of i32 and u32 are define here: https://www.w3.org/TR/WGSL/#integer-types
    // Since generating an arbitrary expression do not care what they are just that they exist.
    val recursiveChoices: List<Pair<Int, () -> Expression>> =
        listOfNotNull(
            fuzzerSettings.arbitraryIntExpressionWeights.swapIntType(depth) to {
                when (outputType) {
                    Type.AbstractInteger -> throw RuntimeException("outputType cannot be AbstractInteger")
                    Type.I32 -> Expression.I32ValueConstructor(listOf(generateArbitraryIntHelper(Type.U32)))
                    Type.U32 -> Expression.U32ValueConstructor(listOf(generateArbitraryIntHelper(Type.I32)))
                }
            },
            fuzzerSettings.arbitraryIntExpressionWeights.binaryOr(depth) to {
                arbitraryBinaryOperation(BinaryOperator.BINARY_OR)
            },
            fuzzerSettings.arbitraryIntExpressionWeights.binaryAnd(depth) to {
                arbitraryBinaryOperation(BinaryOperator.BINARY_AND)
            },
            fuzzerSettings.arbitraryIntExpressionWeights.binaryXor(depth) to {
                arbitraryBinaryOperation(BinaryOperator.BINARY_XOR)
            },
            if (outputType is Type.I32) {
                fuzzerSettings.arbitraryIntExpressionWeights.negate(depth) to {
                    Expression.Unary(
                        operator = UnaryOperator.MINUS,
                        target = generateArbitraryIntHelper(outputType),
                    )
                }
            } else {
                null
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
                Expression.Binary(
                    operator = BinaryOperator.DIVIDE,
                    lhs = generateArbitraryIntHelper(outputType),
                    rhs =
                        generateKnownValueExpression(
                            depth = depth + 1,
                            knownValue =
                                Expression.IntLiteral(
                                    fuzzerSettings
                                        .randomElementFromRange(1..LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE)
                                        .toString() + literalSuffix,
                                ),
                            type = outputType,
                            fuzzerSettings = fuzzerSettings,
                            shaderJob = shaderJob,
                            scope = scope,
                        ),
                )
            },
            fuzzerSettings.arbitraryIntExpressionWeights.modulo(depth) to {
                Expression.Binary(
                    operator = BinaryOperator.MODULO,
                    lhs = generateArbitraryIntHelper(outputType),
                    rhs =
                        generateKnownValueExpression(
                            depth = depth + 1,
                            knownValue =
                                Expression.IntLiteral(
                                    fuzzerSettings
                                        .randomElementFromRange(1..LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE)
                                        .toString() + literalSuffix,
                                ),
                            type = outputType,
                            fuzzerSettings = fuzzerSettings,
                            shaderJob = shaderJob,
                            scope = scope,
                        ),
                )
            },
            fuzzerSettings.arbitraryIntExpressionWeights.abs(depth) to {
                Expression.FunctionCall(
                    callee = "abs",
                    templateParameter = null,
                    args = listOf(generateArbitraryIntHelper(outputType)),
                )
            },
            fuzzerSettings.arbitraryIntExpressionWeights.clamp(depth) to {
                val maxValue = fuzzerSettings.randomElementFromRange(0..LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE)
                val max =
                    generateKnownValueExpression(
                        depth = depth + 1,
                        knownValue = Expression.IntLiteral(maxValue.toString() + literalSuffix),
                        type = outputType,
                        fuzzerSettings = fuzzerSettings,
                        shaderJob = shaderJob,
                        scope = scope,
                    )
                val min =
                    generateKnownValueExpression(
                        depth = depth + 1,
                        knownValue =
                            Expression.IntLiteral(
                                fuzzerSettings
                                    .randomElementFromRange(0..<maxValue)
                                    .toString() + literalSuffix,
                            ),
                        type = outputType,
                        fuzzerSettings = fuzzerSettings,
                        shaderJob = shaderJob,
                        scope = scope,
                    )

                Expression.FunctionCall(
                    callee = "clamp",
                    templateParameter = null,
                    args = listOf(generateArbitraryIntHelper(outputType), min, max),
                )
            },
            fuzzerSettings.arbitraryIntExpressionWeights.countLeadingZeros(depth) to {
                Expression.FunctionCall(
                    callee = "countLeadingZeros",
                    templateParameter = null,
                    args = listOf(generateArbitraryIntHelper(outputType)),
                )
            },
            fuzzerSettings.arbitraryIntExpressionWeights.countOneBits(depth) to {
                Expression.FunctionCall(
                    callee = "countOneBits",
                    templateParameter = null,
                    args = listOf(generateArbitraryIntHelper(outputType)),
                )
            },
            fuzzerSettings.arbitraryIntExpressionWeights.countTrailingZeros(depth) to {
                Expression.FunctionCall(
                    callee = "countTrailingZeros",
                    templateParameter = null,
                    args = listOf(generateArbitraryIntHelper(outputType)),
                )
            },
            if (outputType is Type.U32) {
                fuzzerSettings.arbitraryIntExpressionWeights.dot4U8Packed(depth) to {
                    Expression.FunctionCall(
                        callee = "dot4U8Packed",
                        templateParameter = null,
                        args = listOf(generateArbitraryIntHelper(Type.U32), generateArbitraryIntHelper(Type.U32)),
                    )
                }
            } else {
                null
            },
            if (outputType is Type.I32) {
                fuzzerSettings.arbitraryIntExpressionWeights.dot4I8Packed(depth) to {
                    Expression.FunctionCall(
                        callee = "dot4I8Packed",
                        templateParameter = null,
                        args = listOf(generateArbitraryIntHelper(Type.U32), generateArbitraryIntHelper(Type.U32)),
                    )
                }
            } else {
                null
            },
            fuzzerSettings.arbitraryIntExpressionWeights.extractBits(depth) to {
                // sign extends if i32 and does not sign extend for u32
                val bitWidth = 32
                val countValue = fuzzerSettings.randomElementFromRange(1..bitWidth)
                val count =
                    generateKnownValueExpression(
                        depth = depth + 1,
                        knownValue =
                            Expression.IntLiteral(
                                countValue.toString() + "u",
                            ),
                        type = Type.U32,
                        fuzzerSettings = fuzzerSettings,
                        shaderJob = shaderJob,
                        scope = scope,
                    )
                val offset =
                    generateKnownValueExpression(
                        depth = depth + 1,
                        knownValue =
                            Expression.IntLiteral(
                                fuzzerSettings
                                    .randomElementFromRange(0..bitWidth - countValue)
                                    .toString() + "u",
                            ),
                        type = Type.U32,
                        fuzzerSettings = fuzzerSettings,
                        shaderJob = shaderJob,
                        scope = scope,
                    )
                Expression.FunctionCall(
                    callee = "extractBits",
                    templateParameter = null,
                    args =
                        listOf(
                            generateArbitraryIntHelper(outputType),
                            offset,
                            count,
                        ),
                )
            },
            fuzzerSettings.arbitraryIntExpressionWeights.firstLeadingBit(depth) to {
                Expression.FunctionCall(
                    callee = "firstLeadingBit",
                    templateParameter = null,
                    args = listOf(generateArbitraryIntHelper(outputType)),
                )
            },
            fuzzerSettings.arbitraryIntExpressionWeights.firstTrailingBit(depth) to {
                Expression.FunctionCall(
                    callee = "firstTrailingBit",
                    templateParameter = null,
                    args = listOf(generateArbitraryIntHelper(outputType)),
                )
            },
            fuzzerSettings.arbitraryIntExpressionWeights.insertBits(depth) to {
                val bitWidth = 32
                val countValue = fuzzerSettings.randomElementFromRange(1..bitWidth)
                val count =
                    generateKnownValueExpression(
                        depth = depth + 1,
                        knownValue =
                            Expression.IntLiteral(
                                countValue.toString() + "u",
                            ),
                        type = Type.U32,
                        fuzzerSettings = fuzzerSettings,
                        shaderJob = shaderJob,
                        scope = scope,
                    )
                val offset =
                    generateKnownValueExpression(
                        depth = depth + 1,
                        knownValue =
                            Expression.IntLiteral(
                                fuzzerSettings
                                    .randomElementFromRange(0..bitWidth - countValue)
                                    .toString() + "u",
                            ),
                        type = Type.U32,
                        fuzzerSettings = fuzzerSettings,
                        shaderJob = shaderJob,
                        scope = scope,
                    )
                Expression.FunctionCall(
                    callee = "insertBits",
                    templateParameter = null,
                    args =
                        listOf(
                            generateArbitraryIntHelper(outputType), // e: T
                            generateArbitraryIntHelper(outputType), // newBits: T
                            offset,
                            count,
                        ),
                )
            },
            fuzzerSettings.arbitraryIntExpressionWeights.max(depth) to {
                Expression.FunctionCall(
                    callee = "max",
                    templateParameter = null,
                    args =
                        listOf(
                            generateArbitraryIntHelper(outputType),
                            generateArbitraryIntHelper(outputType),
                        ),
                )
            },
            fuzzerSettings.arbitraryIntExpressionWeights.min(depth) to {
                Expression.FunctionCall(
                    callee = "min",
                    templateParameter = null,
                    args =
                        listOf(
                            generateArbitraryIntHelper(outputType),
                            generateArbitraryIntHelper(outputType),
                        ),
                )
            },
            fuzzerSettings.arbitraryIntExpressionWeights.reverseBits(depth) to {
                Expression.FunctionCall(
                    callee = "reverseBits",
                    templateParameter = null,
                    args = listOf(generateArbitraryIntHelper(outputType)),
                )
            },
            if (outputType is Type.I32) {
                fuzzerSettings.arbitraryIntExpressionWeights.sign(depth) to {
                    Expression.FunctionCall(
                        callee = "sign",
                        templateParameter = null,
                        args = listOf(generateArbitraryIntHelper(outputType)),
                    )
                }
            } else {
                null
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

private fun FuzzerSettings.randomElementFromRange(range: IntRange): Int = range.random(Random(this.randomInt(Int.MAX_VALUE)))

private fun FuzzerSettings.randomElementFromRange(range: LongRange): Long = range.random(Random(this.randomInt(Int.MAX_VALUE)))
