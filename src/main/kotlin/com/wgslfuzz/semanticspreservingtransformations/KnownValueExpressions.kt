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
import com.wgslfuzz.core.Scope
import com.wgslfuzz.core.ShaderJob
import com.wgslfuzz.core.Type
import com.wgslfuzz.core.UnaryOperator
import com.wgslfuzz.core.clone
import com.wgslfuzz.core.getUniformDeclaration
import com.wgslfuzz.core.toType
import kotlin.math.max
import kotlin.math.truncate

fun generateKnownValueExpression(
    depth: Int,
    knownValue: Expression,
    type: Type,
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
): AugmentedExpression.KnownValue {
    if (!fuzzerSettings.goDeeper(depth)) {
        return AugmentedExpression.KnownValue(
            knownValue = knownValue.clone(),
            expression = knownValue.clone(),
        )
    }
    if (type !is Type.Scalar) {
        TODO("Need to support non-scalar known values, e.g. vectors and matrices.")
    }
    if (type is Type.Bool) {
        val knownBoolValue = knownValue as Expression.BoolLiteral
        if (knownBoolValue.text == "true") {
            return generateTrueByConstructionExpression(
                depth = depth,
                fuzzerSettings = fuzzerSettings,
                shaderJob = shaderJob,
                scope = scope,
            )
        }
        check(knownBoolValue.text == "false")
        return generateFalseByConstructionExpression(
            depth = depth,
            fuzzerSettings = fuzzerSettings,
            shaderJob = shaderJob,
            scope = scope,
        )
    }
    val knownValueAsInt: Int =
        getNumericValueFromConstant(
            knownValue,
        )
    if (knownValueAsInt !in 0..LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE) {
        throw UnsupportedOperationException("Known values are currently only supported within a limited range.")
    }
    val literalSuffix = getNumericLiteralSuffix(type)

    val choices: List<Pair<Int, () -> AugmentedExpression.KnownValue>> =
        listOfNotNull(
            fuzzerSettings.knownValueWeights.plainKnownValue(depth) to {
                AugmentedExpression.KnownValue(
                    knownValue = knownValue.clone(),
                    expression = knownValue.clone(),
                )
            },
            fuzzerSettings.knownValueWeights.sumOfKnownValues(depth) to {
                // Deriving a known value by using addition of two numbers.
                val randomValue = fuzzerSettings.randomInt(knownValueAsInt + 1)
                assert(randomValue <= knownValueAsInt)
                val difference: Int = knownValueAsInt - randomValue
                assert(difference in 0..knownValueAsInt)
                val randomValueText = "$randomValue$literalSuffix"
                val differenceText = "$difference$literalSuffix"
                val randomValueKnownExpression =
                    if (type is Type.Integer) {
                        Expression.IntLiteral(randomValueText)
                    } else {
                        Expression.FloatLiteral(randomValueText)
                    }
                val differenceKnownExpression =
                    if (type is Type.Integer) {
                        Expression.IntLiteral(differenceText)
                    } else {
                        Expression.FloatLiteral(differenceText)
                    }
                AugmentedExpression.KnownValue(
                    knownValue = knownValue.clone(),
                    expression =
                        binaryExpressionRandomOperandOrder(
                            fuzzerSettings,
                            BinaryOperator.PLUS,
                            generateKnownValueExpression(
                                depth = depth + 1,
                                knownValue = randomValueKnownExpression,
                                type = type,
                                fuzzerSettings = fuzzerSettings,
                                shaderJob = shaderJob,
                                scope = scope,
                            ),
                            generateKnownValueExpression(
                                depth = depth + 1,
                                knownValue = differenceKnownExpression,
                                type = type,
                                fuzzerSettings = fuzzerSettings,
                                shaderJob = shaderJob,
                                scope = scope,
                            ),
                        ),
                )
            },
            fuzzerSettings.knownValueWeights.differenceOfKnownValues(depth) to {
                // Deriving a known value by using subtraction of two numbers.
                val randomValue = fuzzerSettings.randomInt(LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE - knownValueAsInt + 1)
                val sum: Int = knownValueAsInt + randomValue
                assert(sum in 0..LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE)
                val randomValueText = "$randomValue$literalSuffix"
                val sumText = "$sum$literalSuffix"
                val randomValueKnownExpression =
                    if (type is Type.Integer) {
                        Expression.IntLiteral(randomValueText)
                    } else {
                        Expression.FloatLiteral(randomValueText)
                    }
                val sumKnownExpression =
                    if (type is Type.Integer) {
                        Expression.IntLiteral(sumText)
                    } else {
                        Expression.FloatLiteral(sumText)
                    }
                AugmentedExpression.KnownValue(
                    knownValue = knownValue.clone(),
                    expression =
                        Expression.Binary(
                            BinaryOperator.MINUS,
                            generateKnownValueExpression(
                                depth = depth + 1,
                                knownValue = sumKnownExpression,
                                type = type,
                                fuzzerSettings = fuzzerSettings,
                                shaderJob = shaderJob,
                                scope = scope,
                            ),
                            generateKnownValueExpression(
                                depth = depth + 1,
                                knownValue = randomValueKnownExpression,
                                type = type,
                                fuzzerSettings = fuzzerSettings,
                                shaderJob = shaderJob,
                                scope = scope,
                            ),
                        ),
                )
            },
            fuzzerSettings.knownValueWeights.productOfKnownValues(depth) to {
                // Deriving a known value by using multiplication of two numbers.
                val randomValue = max(1, fuzzerSettings.randomInt(max(1, knownValueAsInt / 2)))
                val quotient: Int = knownValueAsInt / randomValue
                val remainder: Int = knownValueAsInt % randomValue

                val randomValueText = "$randomValue$literalSuffix"
                val quotientText = "$quotient$literalSuffix"
                val remainderText = "$remainder$literalSuffix"
                val randomValueKnownExpression =
                    if (type is Type.Integer) {
                        Expression.IntLiteral(randomValueText)
                    } else {
                        Expression.FloatLiteral(randomValueText)
                    }
                val quotientKnownExpression =
                    if (type is Type.Integer) {
                        Expression.IntLiteral(quotientText)
                    } else {
                        Expression.FloatLiteral(quotientText)
                    }
                val remainderKnownExpression =
                    if (type is Type.Integer) {
                        Expression.IntLiteral(remainderText)
                    } else {
                        Expression.FloatLiteral(remainderText)
                    }

                var resultExpression =
                    binaryExpressionRandomOperandOrder(
                        fuzzerSettings,
                        BinaryOperator.TIMES,
                        generateKnownValueExpression(
                            depth = depth + 1,
                            knownValue = randomValueKnownExpression,
                            type = type,
                            fuzzerSettings = fuzzerSettings,
                            shaderJob = shaderJob,
                            scope = scope,
                        ),
                        generateKnownValueExpression(
                            depth = depth + 1,
                            knownValue = quotientKnownExpression,
                            type = type,
                            fuzzerSettings = fuzzerSettings,
                            shaderJob = shaderJob,
                            scope = scope,
                        ),
                    )
                // If multiplication of the random number and the quotient does not result in the knownValue add the remainder to the expression to make resultExpression equal to knownValue
                if (remainder != 0 || fuzzerSettings.randomBool()) {
                    resultExpression =
                        binaryExpressionRandomOperandOrder(
                            fuzzerSettings,
                            BinaryOperator.PLUS,
                            resultExpression,
                            generateKnownValueExpression(
                                depth = depth + 1,
                                knownValue = remainderKnownExpression,
                                type = type,
                                fuzzerSettings = fuzzerSettings,
                                shaderJob = shaderJob,
                                scope = scope,
                            ),
                        )
                }
                AugmentedExpression.KnownValue(
                    knownValue = knownValue.clone(),
                    expression = resultExpression,
                )
            },
            // Deriving a known value from a uniform only works with concrete types.
            if (type.isAbstract()) {
                // Removed by listOfNotNull
                null
            } else {
                // Deriving a known value from a uniform while adjusting as necessary using addition and subtraction.
                fuzzerSettings.knownValueWeights.knownValueDerivedFromUniform(depth) to {
                    val (knownScalarFromUniform, scalarType) = randomKnownScalarValueFromUniform(shaderJob, fuzzerSettings)

                    // valueOfUniformAdjusted is the underlying int value of uniformScalarAdjusted.
                    // valueOfUniformAdjusted is in the range 0..LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE and by extension so is the underlying value of uniformScalarAdjusted.
                    // uniformScalarAdjusted is uniformScalar wrapped in type casts, truncate and/or abs(x) % LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE.
                    val (valueOfUniformAdjusted: Int, uniformScalarAdjusted: Expression) =
                        getNumericValueWithAdjustedExpression(
                            valueExpression = knownScalarFromUniform,
                            valueExpressionType = scalarType,
                            constantExpression = knownScalarFromUniform.knownValue,
                            outputType = type,
                        )

                    // Given the valueOfUniformAdjusted create and expression that equals the value of the knownValue using addition and subtraction.
                    val expression =
                        when (valueOfUniformAdjusted) {
                            // valueOfUniformAdjusted == knownValueAsInt
                            knownValueAsInt -> uniformScalarAdjusted
                            // valueOfUniformAdjusted > knownValueAsInt
                            in knownValueAsInt + 1..LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE -> {
                                val difference = valueOfUniformAdjusted - knownValueAsInt
                                val differenceText = "$difference$literalSuffix"
                                val differenceKnownExpression =
                                    if (type is Type.Integer) {
                                        Expression.IntLiteral(differenceText)
                                    } else {
                                        Expression.FloatLiteral(differenceText)
                                    }
                                Expression.Binary(
                                    BinaryOperator.MINUS,
                                    uniformScalarAdjusted,
                                    generateKnownValueExpression(
                                        depth = depth + 1,
                                        knownValue = differenceKnownExpression,
                                        fuzzerSettings = fuzzerSettings,
                                        shaderJob = shaderJob,
                                        type = type,
                                        scope = scope,
                                    ),
                                )
                            }
                            // valueOfUniformAdjusted < knownValueAsInt
                            in 0..<knownValueAsInt -> {
                                val difference = knownValueAsInt - valueOfUniformAdjusted
                                val differenceText = "$difference$literalSuffix"
                                val differenceKnownExpression =
                                    if (type is Type.Integer) {
                                        Expression.IntLiteral(differenceText)
                                    } else {
                                        Expression.FloatLiteral(differenceText)
                                    }
                                binaryExpressionRandomOperandOrder(
                                    fuzzerSettings,
                                    BinaryOperator.PLUS,
                                    uniformScalarAdjusted,
                                    generateKnownValueExpression(
                                        depth = depth + 1,
                                        knownValue = differenceKnownExpression,
                                        fuzzerSettings = fuzzerSettings,
                                        shaderJob = shaderJob,
                                        type = type,
                                        scope = scope,
                                    ),
                                )
                            }

                            else -> throw RuntimeException(
                                "valueOfUniformAdjusted is not in correct range. This should be logically impossible.",
                            )
                        }
                    AugmentedExpression.KnownValue(
                        knownValue = knownValue.clone(),
                        expression = expression,
                    )
                }
            },
        )
    return choose(fuzzerSettings, choices)
}

private fun getNumericLiteralSuffix(type: Type.Scalar) =
    when (type) {
        is Type.I32 -> "i"
        is Type.U32 -> "u"
        is Type.AbstractInteger -> ""
        is Type.F32 -> "f"
        is Type.AbstractFloat -> ""
        else -> throw RuntimeException("Unsupported type.")
    }

fun generateFalseByConstructionExpression(
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
): AugmentedExpression.KnownValue = generateFalseByConstructionExpression(0, fuzzerSettings, shaderJob, scope)

fun generateTrueByConstructionExpression(
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
): AugmentedExpression.KnownValue = generateTrueByConstructionExpression(0, fuzzerSettings, shaderJob, scope)

private fun generateFalseByConstructionExpression(
    depth: Int,
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
): AugmentedExpression.KnownValue {
    if (!fuzzerSettings.goDeeper(depth)) {
        return AugmentedExpression.KnownValue(
            Expression.BoolLiteral("false"),
            Expression.BoolLiteral("false"),
        )
    }
    val choices: List<Pair<Int, () -> Expression>> =
        listOf(
            // A plain "false"
            fuzzerSettings.falseByConstructionWeights.plainFalse(depth) to {
                Expression.BoolLiteral("false")
            },
            // A false expression && an arbitrary expression
            fuzzerSettings.falseByConstructionWeights.falseAndArbitrary(depth) to {
                Expression.Binary(
                    operator = BinaryOperator.SHORT_CIRCUIT_AND,
                    generateFalseByConstructionExpression(depth + 1, fuzzerSettings, shaderJob, scope),
                    generateArbitraryExpression(
                        depth = depth + 1,
                        type = Type.Bool,
                        sideEffectsAllowed = true,
                        fuzzerSettings = fuzzerSettings,
                        shaderJob = shaderJob,
                        scope = scope,
                    ),
                )
            },
            // An arbitrary expression && a false expression
            fuzzerSettings.falseByConstructionWeights.arbitraryAndFalse(depth) to {
                Expression.Binary(
                    operator = BinaryOperator.SHORT_CIRCUIT_AND,
                    generateArbitraryExpression(
                        depth = depth + 1,
                        type = Type.Bool,
                        sideEffectsAllowed = false, // No side effects as this will be executable
                        fuzzerSettings = fuzzerSettings,
                        shaderJob = shaderJob,
                        scope = scope,
                    ),
                    generateFalseByConstructionExpression(depth + 1, fuzzerSettings, shaderJob, scope),
                )
            },
            // ! true expression
            fuzzerSettings.falseByConstructionWeights.notTrue(depth) to {
                Expression.Unary(
                    operator = UnaryOperator.LOGICAL_NOT,
                    generateTrueByConstructionExpression(depth + 1, fuzzerSettings, shaderJob, scope),
                )
            },
            fuzzerSettings.falseByConstructionWeights.opaqueFalseFromUniformValues(depth) to {
                compareUniformWithKnownValue(
                    depth = depth,
                    fuzzerSettings = fuzzerSettings,
                    shaderJob = shaderJob,
                    scope = scope,
                    comparisonOperators = listOf(BinaryOperator.NOT_EQUAL, BinaryOperator.LESS_THAN, BinaryOperator.GREATER_THAN),
                )
            },
        )
    return AugmentedExpression.KnownValue(
        expression = choose(fuzzerSettings, choices),
        knownValue = Expression.BoolLiteral("false"),
    )
}

private fun generateTrueByConstructionExpression(
    depth: Int,
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
): AugmentedExpression.KnownValue {
    if (!fuzzerSettings.goDeeper(depth)) {
        return AugmentedExpression.KnownValue(
            expression = Expression.BoolLiteral("true"),
            knownValue = Expression.BoolLiteral("true"),
        )
    }
    val choices: List<Pair<Int, () -> Expression>> =
        listOf(
            // A plain "true"
            fuzzerSettings.trueByConstructionWeights.plainTrue(depth) to {
                Expression.BoolLiteral("true")
            },
            // A true expression || an arbitrary expression
            fuzzerSettings.trueByConstructionWeights.trueOrArbitrary(depth) to {
                Expression.Binary(
                    operator = BinaryOperator.SHORT_CIRCUIT_OR,
                    generateTrueByConstructionExpression(depth + 1, fuzzerSettings, shaderJob, scope),
                    generateArbitraryExpression(
                        depth = depth + 1,
                        type = Type.Bool,
                        sideEffectsAllowed = true,
                        fuzzerSettings = fuzzerSettings,
                        shaderJob = shaderJob,
                        scope = scope,
                    ),
                )
            },
            // An arbitrary expression || a true expression
            fuzzerSettings.trueByConstructionWeights.arbitraryOrTrue(depth) to {
                Expression.Binary(
                    operator = BinaryOperator.SHORT_CIRCUIT_OR,
                    generateArbitraryExpression(
                        depth = depth + 1,
                        type = Type.Bool,
                        sideEffectsAllowed = false, // No side effects as this will be executable
                        fuzzerSettings = fuzzerSettings,
                        shaderJob = shaderJob,
                        scope = scope,
                    ),
                    generateTrueByConstructionExpression(depth + 1, fuzzerSettings, shaderJob, scope),
                )
            },
            // ! false expression
            fuzzerSettings.trueByConstructionWeights.notFalse(depth) to {
                Expression.Unary(
                    operator = UnaryOperator.LOGICAL_NOT,
                    generateFalseByConstructionExpression(depth + 1, fuzzerSettings, shaderJob, scope),
                )
            },
            fuzzerSettings.trueByConstructionWeights.opaqueTrueFromUniformValues(depth) to {
                compareUniformWithKnownValue(
                    depth = depth,
                    fuzzerSettings = fuzzerSettings,
                    shaderJob = shaderJob,
                    scope = scope,
                    comparisonOperators =
                        listOf(
                            BinaryOperator.EQUAL_EQUAL,
                            BinaryOperator.LESS_THAN_EQUAL,
                            BinaryOperator.GREATER_THAN_EQUAL,
                        ),
                )
            },
        )
    return AugmentedExpression.KnownValue(
        expression = choose(fuzzerSettings, choices),
        knownValue = Expression.BoolLiteral("true"),
    )
}

fun randomKnownScalarValueFromUniform(
    shaderJob: ShaderJob,
    fuzzerSettings: FuzzerSettings,
): Pair<AugmentedExpression.KnownValue, Type> {
    val groups =
        shaderJob.pipelineState
            .getUniformGroups()
            .toList()
            .sorted()
    val group = fuzzerSettings.randomElement(groups)
    val bindings =
        shaderJob.pipelineState
            .getUniformBindingsForGroup(group)
            .toList()
            .sorted()
    val binding = fuzzerSettings.randomElement(bindings)
    val uniformDeclaration = shaderJob.tu.getUniformDeclaration(group, binding)

    var currentType: Type =
        uniformDeclaration.typeDecl?.toType(shaderJob.environment.globalScope, shaderJob.environment)
            ?: throw IllegalStateException("Uniform should have type")

    var currentUniformExpr: Expression = Expression.Identifier(uniformDeclaration.name)
    var currentValueExpr: Expression = shaderJob.pipelineState.getUniformValue(group, binding)

    while (true) {
        when (currentType) {
            is Type.I32, is Type.F32, is Type.U32 -> break

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
            is Type.Array -> {
                val randomElementIndex = fuzzerSettings.randomInt(currentType.elementCount!!)
                currentType = currentType.elementType
                currentUniformExpr =
                    Expression.IndexLookup(
                        currentUniformExpr,
                        Expression.IntLiteral(randomElementIndex.toString()),
                    )
                currentValueExpr = (currentValueExpr as Expression.ArrayValueConstructor).args[randomElementIndex]
            }
            else -> TODO()
        }
    }
    return Pair(
        AugmentedExpression.KnownValue(
            knownValue = currentValueExpr,
            expression = currentUniformExpr,
        ),
        currentType,
    )
}

private fun compareUniformWithKnownValue(
    depth: Int,
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
    comparisonOperators: List<BinaryOperator>,
): Expression {
    val (knownScalarFromUniform, scalarType) = randomKnownScalarValueFromUniform(shaderJob, fuzzerSettings)
    val (valueOfUniformAdjusted: Int, uniformScalarAdjusted: Expression) =
        getNumericValueWithAdjustedExpression(
            valueExpression = knownScalarFromUniform.expression,
            valueExpressionType = scalarType,
            constantExpression = knownScalarFromUniform.knownValue,
            outputType = scalarType,
        )
    val adjustedValueAsLiteralExpr =
        when (scalarType) {
            is Type.F32 -> Expression.FloatLiteral("${valueOfUniformAdjusted}f")
            is Type.I32 -> Expression.IntLiteral("${valueOfUniformAdjusted}i")
            is Type.U32 -> Expression.IntLiteral("${valueOfUniformAdjusted}u")
            else -> throw UnsupportedOperationException("Unsupported scalar type $scalarType")
        }
    val knownValue =
        generateKnownValueExpression(
            depth = depth + 1,
            knownValue = adjustedValueAsLiteralExpr,
            type = scalarType,
            fuzzerSettings = fuzzerSettings,
            shaderJob = shaderJob,
            scope = scope,
        )
    // Choose a random suitable operator and choose randomly on which side of the expression the uniform access should appear.
    // No need for custom weights for these choices.
    return binaryExpressionRandomOperandOrder(
        fuzzerSettings,
        fuzzerSettings.randomElement(comparisonOperators),
        uniformScalarAdjusted,
        knownValue,
    )
}

private fun getNumericValueFromConstant(constantExpression: Expression): Int {
    val result = getValueAsDoubleFromConstant(constantExpression)
    if (result.toInt().toDouble() != result) {
        throw RuntimeException("Only integer-valued doubles are supported in known value expressions.")
    }
    return result.toInt()
}

/**
 * Takes in a constant expression and determines its value and outputs an adjusted expression if changes were made to make it conform to requirements.
 * Requirements:
 * - Correct output type
 * - If the value is a float with a fractional part the function truncates the value and expression
 * - If the value is outside the range which known values are allowed uses absolute and modulo to bring value within allowed range
 */
private fun getNumericValueWithAdjustedExpression(
    valueExpression: Expression,
    valueExpressionType: Type,
    constantExpression: Expression,
    outputType: Type,
): Pair<Int, Expression> {
    val value = getValueAsDoubleFromConstant(constantExpression)

    // Determine if truncation is necessary by checking if value has a fractional part
    val truncate = truncate(value) != value

    // Performs type cast and wraps in truncation if necessary
    // Type casts to integer involve truncation and hence do not need to a call wgsl trunc function in addition to their type cast
    val outputExpressionWithCastIfNeeded =
        if (valueExpressionType !is Type.U32 && outputType is Type.U32) {
            // This truncates - https://www.w3.org/TR/WGSL/#u32-builtin
            Expression.U32ValueConstructor(listOf(valueExpression))
        } else if (valueExpressionType is Type.Integer && outputType is Type.Float) {
            // Should not have to truncate a scalar of type Integer
            assert(!truncate)
            Expression.F32ValueConstructor(listOf(valueExpression))
        } else if (valueExpressionType !is Type.I32 && outputType is Type.I32) {
            // This truncates https://www.w3.org/TR/WGSL/#i32-builtin
            Expression.I32ValueConstructor(listOf(valueExpression))
        } else if (truncate) {
            truncateExpression(valueExpression)
        } else {
            valueExpression
        }
    // This is the underlying value of outputExpressionWithCastIfNeeded
    val truncatedValue = truncate(value)

    // Brings the truncatedValue into the range 0..LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE if truncatedValue isn't in the range
    // The operation to bring truncatedValue into range is abs(truncatedValue) % LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE
    val (outputValueInRangeAndInteger, outputExpressionWithCastAndInRange) =
        if (truncatedValue !in
            0.0..LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE.toDouble()
        ) {
            val largestIntegerInPreciseFloatRangeExpression =
                when (outputType) {
                    is Type.U32 -> Expression.IntLiteral(LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE.toString() + "u")
                    is Type.I32 -> Expression.IntLiteral(LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE.toString() + "i")
                    is Type.F32 -> Expression.FloatLiteral(LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE.toString() + "f")
                    else -> throw UnsupportedOperationException("Cannot create a expression of this type")
                }
            Pair(
                truncatedValue.mod(LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE.toDouble()),
                modExpression(absExpression(outputExpressionWithCastIfNeeded), largestIntegerInPreciseFloatRangeExpression),
            )
        } else {
            Pair(truncatedValue, outputExpressionWithCastIfNeeded)
        }

    return Pair(outputValueInRangeAndInteger.toInt(), outputExpressionWithCastAndInRange)
}

private fun truncateExpression(expression: Expression) =
    Expression.FunctionCall(
        callee = "trunc",
        templateParameter = null,
        args = listOf(expression),
    )

private fun modExpression(
    expression: Expression,
    modByExpression: Expression,
) = Expression.Binary(
    operator = BinaryOperator.MODULO,
    lhs = expression,
    rhs = modByExpression,
)

private fun absExpression(expression: Expression) =
    Expression.FunctionCall(
        callee = "abs",
        templateParameter = null,
        args = listOf(expression),
    )
