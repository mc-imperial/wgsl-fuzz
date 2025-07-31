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

// TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/186)
fun generateArbitraryElseBranch(
    sideEffectsAllowed: Boolean,
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
): Statement.ElseBranch? = null

// TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/186)
fun generateArbitraryCompound(
    sideEffectsAllowed: Boolean,
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
): Statement.Compound = Statement.Compound(emptyList())
