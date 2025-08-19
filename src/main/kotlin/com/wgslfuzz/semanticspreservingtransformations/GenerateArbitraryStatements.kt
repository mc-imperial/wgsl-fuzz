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

import com.wgslfuzz.core.AugmentedMetadata
import com.wgslfuzz.core.AugmentedStatement
import com.wgslfuzz.core.Scope
import com.wgslfuzz.core.ShaderJob
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.Type

fun generateArbitraryElseBranch(
    depth: Int,
    sideEffectsAllowed: Boolean,
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
): Statement.ElseBranch? {
    val choices =
        listOf(
            fuzzerSettings.arbitraryElseBranchWeights.empty(depth) to {
                null
            },
            fuzzerSettings.arbitraryElseBranchWeights.ifStatement(depth) to {
                Statement.If(
                    attributes = emptyList(),
                    condition =
                        generateArbitraryExpression(
                            depth = depth + 1,
                            type = Type.Bool,
                            sideEffectsAllowed = sideEffectsAllowed,
                            fuzzerSettings = fuzzerSettings,
                            shaderJob = shaderJob,
                            scope = scope,
                        ),
                    thenBranch =
                        generateArbitraryCompound(
                            depth = depth + 1,
                            sideEffectsAllowed = sideEffectsAllowed,
                            fuzzerSettings = fuzzerSettings,
                            shaderJob = shaderJob,
                            scope = scope,
                        ),
                    elseBranch =
                        generateArbitraryElseBranch(
                            depth = depth + 1,
                            sideEffectsAllowed = sideEffectsAllowed,
                            fuzzerSettings = fuzzerSettings,
                            shaderJob = shaderJob,
                            scope = scope,
                        ),
                )
            },
            fuzzerSettings.arbitraryElseBranchWeights.compound(depth) to {
                generateArbitraryCompound(
                    depth = depth + 1,
                    sideEffectsAllowed = sideEffectsAllowed,
                    fuzzerSettings = fuzzerSettings,
                    shaderJob = shaderJob,
                    scope = scope,
                )
            },
        )
    return choose(fuzzerSettings, choices)?.let { AugmentedStatement.ArbitraryElseBranch(it) }
}

fun generateArbitraryCompound(
    depth: Int,
    sideEffectsAllowed: Boolean,
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
): Statement.Compound {
    val compoundLength = fuzzerSettings.randomArbitraryCompoundLength(depth)
    return Statement.Compound(
        statements =
            List(compoundLength) {
                generateArbitraryStatement(depth + 1, sideEffectsAllowed, shaderJob, scope)
            },
        metadata = AugmentedMetadata.ArbitraryCompoundMetaData,
    )
}

// TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/223)
fun generateArbitraryStatement(
    depth: Int,
    sideEffectsAllowed: Boolean,
    shaderJob: ShaderJob,
    scope: Scope,
): Statement = AugmentedStatement.ArbitraryStatement(Statement.Empty())
