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
                generateArbitraryIfStatement(depth + 1, sideEffectsAllowed, fuzzerSettings, shaderJob, scope)
            },
            fuzzerSettings.arbitraryElseBranchWeights.compound(depth) to {
                generateArbitraryCompound(depth + 1, sideEffectsAllowed, fuzzerSettings, shaderJob, scope)
            },
        )
    return choose(fuzzerSettings, choices)
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
        List(compoundLength) {
            generateArbitraryStatement(depth + 1, sideEffectsAllowed, shaderJob, scope)
        },
    )
}

fun generateArbitraryStatement(
    depth: Int,
    sideEffectsAllowed: Boolean,
    shaderJob: ShaderJob,
    scope: Scope,
): Statement = Statement.Compound(emptyList())

fun generateArbitraryIfStatement(
    depth: Int,
    sideEffectsAllowed: Boolean,
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
) = Statement.If(
    attributes = emptyList(),
    condition = generateArbitraryExpression(depth + 1, Type.Bool, sideEffectsAllowed, fuzzerSettings, shaderJob, scope),
    thenBranch = generateArbitraryCompound(depth + 1, sideEffectsAllowed, fuzzerSettings, shaderJob, scope),
    elseBranch = generateArbitraryElseBranch(depth + 1, sideEffectsAllowed, fuzzerSettings, shaderJob, scope),
)
