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
import com.wgslfuzz.core.AugmentedMetadata
import com.wgslfuzz.core.BinaryOperator
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.Scope
import com.wgslfuzz.core.ShaderJob
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.Type
import com.wgslfuzz.core.TypeDecl
import com.wgslfuzz.core.asStoreTypeIfReference
import com.wgslfuzz.core.clone
import com.wgslfuzz.core.traverse

private typealias IdentityOperationReplacements = MutableMap<Expression, Expression>

private class AddIdentityOperations(
    private val shaderJob: ShaderJob,
    private val fuzzerSettings: FuzzerSettings,
) {
    private var scope: Scope = shaderJob.environment.globalScope

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
        if (node is Statement) {
            // Recording the scope to be the scope available right before this statement sets the scene for generating
            // identity operations for expressions occurring directly inside the statement, if any.
            scope = shaderJob.environment.scopeAvailableBefore(node)
        }
        traverse(::selectIdentityOperationReplacements, node, identityReplacements)
        if (node is Statement) {
            // Resetting the scope to global scope on leaving a statement is fine because if expressions in a
            // subsequent statement are to be mutated, that statement will be hit before such generation takes place,
            // while if mutation of globally-scoped expressions will occur instead then global scope is appropriate.
            scope = shaderJob.environment.globalScope
        }
        if (node !is Expression || !fuzzerSettings.applyIdentityOperation()) {
            // Identity operations only apply to expressions, and we randomly select which
            // expressions to mutate.
            return
        }
        val type = shaderJob.environment.typeOf(node).asStoreTypeIfReference()
        if (type is Type.Integer || type is Type.Float) {
            val id = fuzzerSettings.getUniqueId()
            val choices =
                listOf(
                    fuzzerSettings.scalarIdentityOperationWeights.addZeroLeft to {
                        Expression.Paren(
                            target =
                                Expression.Binary(
                                    operator = BinaryOperator.PLUS,
                                    lhs =
                                        Expression.Paren(
                                            target = generateZero(type),
                                            metadata = setOf(AugmentedMetadata.AdditionalParen(id)),
                                        ),
                                    rhs =
                                        Expression.Paren(
                                            target = node,
                                            metadata = setOf(AugmentedMetadata.AdditionalParen(id)),
                                        ),
                                    metadata =
                                        setOf(
                                            AugmentedMetadata.ReverseToRhsBinaryOperator(
                                                commentary = "add zero on left",
                                                id = id,
                                            ),
                                        ),
                                ),
                            metadata = setOf(AugmentedMetadata.AdditionalParen(id)),
                        )
                    },
                    fuzzerSettings.scalarIdentityOperationWeights.addZeroRight to {
                        Expression.Paren(
                            target =
                                Expression.Binary(
                                    operator = BinaryOperator.PLUS,
                                    lhs =
                                        Expression.Paren(
                                            target = node,
                                            metadata = setOf(AugmentedMetadata.AdditionalParen(id)),
                                        ),
                                    rhs =
                                        Expression.Paren(
                                            target = generateZero(type),
                                            metadata = setOf(AugmentedMetadata.AdditionalParen(id)),
                                        ),
                                    metadata =
                                        setOf(
                                            AugmentedMetadata.ReverseToLhsBinaryOperator(
                                                commentary = "add zero on right",
                                                id = id,
                                            ),
                                        ),
                                ),
                            metadata = setOf(AugmentedMetadata.AdditionalParen(id)),
                        )
                    },
                    fuzzerSettings.scalarIdentityOperationWeights.subZero to {
                        Expression.Paren(
                            target =
                                Expression.Binary(
                                    operator = BinaryOperator.MINUS,
                                    lhs =
                                        Expression.Paren(
                                            target = node,
                                            metadata = setOf(AugmentedMetadata.AdditionalParen(id)),
                                        ),
                                    rhs =
                                        Expression.Paren(
                                            target = generateZero(type),
                                            metadata = setOf(AugmentedMetadata.AdditionalParen(id)),
                                        ),
                                    metadata =
                                        setOf(
                                            AugmentedMetadata.ReverseToLhsBinaryOperator(
                                                commentary = "sub zero",
                                                id = id,
                                            ),
                                        ),
                                ),
                            metadata = setOf(AugmentedMetadata.AdditionalParen(id)),
                        )
                    },
                    fuzzerSettings.scalarIdentityOperationWeights.mulOneLeft to {
                        Expression.Paren(
                            target =
                                Expression.Binary(
                                    operator = BinaryOperator.TIMES,
                                    lhs =
                                        Expression.Paren(
                                            target = generateOne(type),
                                            metadata = setOf(AugmentedMetadata.AdditionalParen(id)),
                                        ),
                                    rhs =
                                        Expression.Paren(
                                            target = node,
                                            metadata = setOf(AugmentedMetadata.AdditionalParen(id)),
                                        ),
                                    metadata =
                                        setOf(
                                            AugmentedMetadata.ReverseToRhsBinaryOperator(
                                                commentary = "mul by one on left",
                                                id = id,
                                            ),
                                        ),
                                ),
                            metadata = setOf(AugmentedMetadata.AdditionalParen(id)),
                        )
                    },
                    fuzzerSettings.scalarIdentityOperationWeights.mulOneRight to {
                        Expression.Paren(
                            target =
                                Expression.Binary(
                                    operator = BinaryOperator.TIMES,
                                    lhs =
                                        Expression.Paren(
                                            target = node,
                                            metadata = setOf(AugmentedMetadata.AdditionalParen(id)),
                                        ),
                                    rhs =
                                        Expression.Paren(
                                            target = generateOne(type),
                                            metadata = setOf(AugmentedMetadata.AdditionalParen(id)),
                                        ),
                                    metadata =
                                        setOf(
                                            AugmentedMetadata.ReverseToLhsBinaryOperator(
                                                commentary = "mul by one on right",
                                                id = id,
                                            ),
                                        ),
                                ),
                            metadata = setOf(AugmentedMetadata.AdditionalParen(id)),
                        )
                    },
                    fuzzerSettings.scalarIdentityOperationWeights.divOne to {
                        Expression.Paren(
                            target =
                                Expression.Binary(
                                    operator = BinaryOperator.DIVIDE,
                                    lhs =
                                        Expression.Paren(
                                            target = node,
                                            metadata = setOf(AugmentedMetadata.AdditionalParen(id)),
                                        ),
                                    rhs =
                                        Expression.Paren(
                                            target = generateOne(type),
                                            metadata = setOf(AugmentedMetadata.AdditionalParen(id)),
                                        ),
                                    metadata =
                                        setOf(
                                            AugmentedMetadata.ReverseToLhsBinaryOperator(
                                                commentary = "div by one",
                                                id = id,
                                            ),
                                        ),
                                ),
                            metadata = setOf(AugmentedMetadata.AdditionalParen(id)),
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
            shaderJob = shaderJob,
            scope = scope,
        )

    private fun generateZero(type: Type) =
        generateKnownValueExpression(
            depth = 0,
            knownValue = constantWithSameValueEverywhere(0, type),
            type = type,
            fuzzerSettings = fuzzerSettings,
            shaderJob = shaderJob,
            scope = scope,
        )

    fun apply(): ShaderJob {
        val identityReplacements: IdentityOperationReplacements = mutableMapOf()
        traverse(::selectIdentityOperationReplacements, shaderJob.tu, identityReplacements)
        return ShaderJob(
            tu = shaderJob.tu.clone { identityReplacements[it] },
            pipelineState = shaderJob.pipelineState,
        )
    }
}

fun addIdentityOperations(
    shaderJob: ShaderJob,
    fuzzerSettings: FuzzerSettings,
): ShaderJob =
    AddIdentityOperations(
        shaderJob,
        fuzzerSettings,
    ).apply()
