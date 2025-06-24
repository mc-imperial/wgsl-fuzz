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
import com.wgslfuzz.core.Attribute
import com.wgslfuzz.core.AugmentedExpression
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.ParsedShaderJob
import com.wgslfuzz.core.Type
import com.wgslfuzz.core.TypeDecl
import com.wgslfuzz.core.clone
import com.wgslfuzz.core.traverse

private typealias IdentityOperationReplacements = MutableMap<Expression, AugmentedExpression.IdentityOperation>

private class AddIdentityOperations(
    private val parsedShaderJob: ParsedShaderJob,
    private val fuzzerSettings: FuzzerSettings,
) {
    private fun selectIdentityOperationReplacements(
        node: AstNode,
        identityReplacements: IdentityOperationReplacements,
    ) {
        if (node is Attribute) {
            // We do not want to mutate, for example, the argument to the 'location' attribute.
            return
        }
        if (node is TypeDecl) {
            // We do not want to mutate, for example, a constant array size.
            return
        }
        if (node is AugmentedExpression.KnownValue) {
            // In the case of a known value expression, the "known value" part should be left intact, and only the
            // obfuscated expression that evaluates to the known value should be considered for transformation.
            traverse(::selectIdentityOperationReplacements, node.expression, identityReplacements)
            return
        }
        traverse(::selectIdentityOperationReplacements, node, identityReplacements)
        if (node !is Expression || !fuzzerSettings.applyIdentityOperation()) {
            // Identity operations only apply to expressions, and we randomly select which
            // expressions to mutate.
            return
        }
        val type = parsedShaderJob.environment.typeOf(node)
        if (type is Type.Integer || type is Type.Float) {
            val choices: List<Pair<Int, () -> AugmentedExpression.IdentityOperation>> =
                listOf(
                    fuzzerSettings.scalarIdentityOperationWeights.addZeroLeft to {
                        AugmentedExpression.AddZero(
                            originalExpression = node,
                            zeroExpression = generateZero(type),
                            zeroOnLeft = true,
                        )
                    },
                    fuzzerSettings.scalarIdentityOperationWeights.addZeroRight to {
                        AugmentedExpression.AddZero(
                            originalExpression = node,
                            zeroExpression = generateZero(type),
                            zeroOnLeft = false,
                        )
                    },
                    fuzzerSettings.scalarIdentityOperationWeights.subZero to {
                        AugmentedExpression.SubZero(
                            originalExpression = node,
                            zeroExpression = generateZero(type),
                        )
                    },
                    fuzzerSettings.scalarIdentityOperationWeights.mulOneLeft to {
                        AugmentedExpression.MulOne(
                            originalExpression = node,
                            oneExpression = generateOne(type),
                            oneOnLeft = true,
                        )
                    },
                    fuzzerSettings.scalarIdentityOperationWeights.mulOneRight to {
                        AugmentedExpression.MulOne(
                            originalExpression = node,
                            oneExpression = generateOne(type),
                            oneOnLeft = false,
                        )
                    },
                    fuzzerSettings.scalarIdentityOperationWeights.divOne to {
                        AugmentedExpression.DivOne(
                            originalExpression = node,
                            oneExpression = generateOne(type),
                        )
                    },
                )
            identityReplacements[node] = choose(fuzzerSettings, choices)
        } else {
            // TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/38): Identity operations on vectors
            // TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/39): Identity operations on vectors
            // TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/40): Identity operations on structs
            // TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/41): Identity operations on arrays
        }
    }

    private fun generateOne(type: Type) =
        generateKnownValueExpression(
            depth = 0,
            knownValue = constantWithSameValueEverywhere(1, type),
            type = type,
            fuzzerSettings = fuzzerSettings,
            parsedShaderJob = parsedShaderJob,
        )

    private fun generateZero(type: Type) =
        generateKnownValueExpression(
            depth = 0,
            knownValue = constantWithSameValueEverywhere(0, type),
            type = type,
            fuzzerSettings = fuzzerSettings,
            parsedShaderJob = parsedShaderJob,
        )

    fun apply(): ParsedShaderJob {
        val identityReplacements: IdentityOperationReplacements = mutableMapOf()
        traverse(::selectIdentityOperationReplacements, parsedShaderJob.tu, identityReplacements)
        return ParsedShaderJob(
            tu = parsedShaderJob.tu.clone { identityReplacements[it] },
            pipelineState = parsedShaderJob.pipelineState,
        )
    }
}

fun addIdentityOperations(
    parsedShaderJob: ParsedShaderJob,
    fuzzerSettings: FuzzerSettings,
): ParsedShaderJob =
    AddIdentityOperations(
        parsedShaderJob,
        fuzzerSettings,
    ).apply()
