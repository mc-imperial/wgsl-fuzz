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
    if (depth == fuzzerSettings.maxDepth) {
        constantWithSameValueEverywhere(1, type)
    } else {
        when (type) {
            Type.Bool -> generateArbitraryBool(depth, sideEffectsAllowed, fuzzerSettings, shaderJob, scope)
            // TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/42): Support arbitrary expression generation
            else -> constantWithSameValueEverywhere(1, type)
        }
    }

private fun generateArbitraryBool(
    depth: Int,
    sideEffectsAllowed: Boolean,
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
): Expression {
    if (depth == fuzzerSettings.maxDepth) {
        return constantWithSameValueEverywhere(1, Type.Bool)
    }

    val choices: List<Pair<Int, () -> AugmentedExpression.ArbitraryExpression>> =
        listOf(
            fuzzerSettings.arbitraryBooleanExpressionWeights.not(depth) to {
                AugmentedExpression.ArbitraryExpression(
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
                    ),
                )
            },
            fuzzerSettings.arbitraryBooleanExpressionWeights.or(depth) to {
                AugmentedExpression.ArbitraryExpression(
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
                    ),
                )
            },
            fuzzerSettings.arbitraryBooleanExpressionWeights.and(depth) to {
                AugmentedExpression.ArbitraryExpression(
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
                    ),
                )
            },
            fuzzerSettings.arbitraryBooleanExpressionWeights.literal(depth) to {
                AugmentedExpression.ArbitraryExpression(
                    Expression.BoolLiteral(
                        text = fuzzerSettings.randomElement(listOf("true", "false")),
                    ),
                )
            },
            fuzzerSettings.arbitraryBooleanExpressionWeights.variableFromScope(depth) to {
                AugmentedExpression.ArbitraryExpression(
                    expression =
                        randomVariableFromScope(scope, type = Type.Bool, fuzzerSettings) ?: throw
                            IllegalStateException("Could not find a variable within scope: $scope"),
                )
            },
        )
    return choose(fuzzerSettings, choices)
}
