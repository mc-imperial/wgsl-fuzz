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

fun generateArbitraryExpression(
    depth: Int,
    type: Type,
    sideEffectsAllowed: Boolean,
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
): Expression =
    when (type) {
        Type.Bool -> generateArbitraryBool(depth, sideEffectsAllowed, fuzzerSettings, shaderJob, scope)
        // TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/42): Support arbitrary expression generation
        else -> constantWithSameValueEverywhere(1, type)
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
                    randomVariableFromScope(scope, type = Type.Bool, fuzzerSettings)!!
                }
            } else {
                null
            },
        )

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
        )

    return AugmentedExpression.ArbitraryExpression(
        if (depth == fuzzerSettings.maxDepth) {
            choose(fuzzerSettings, nonRecursiveChoices)
        } else {
            choose(fuzzerSettings, recursiveChoices + nonRecursiveChoices)
        },
    )
}
