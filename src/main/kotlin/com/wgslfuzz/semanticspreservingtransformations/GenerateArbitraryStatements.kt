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

fun generateArbitraryCompound(
    sideEffectsAllowed: Boolean,
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
): Statement.Compound = Statement.Compound(emptyList())
