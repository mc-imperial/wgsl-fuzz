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

import com.wgslfuzz.core.ShaderJob
import com.wgslfuzz.core.UniformBufferInfoByteLevel
import com.wgslfuzz.core.createShaderJob
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.system.exitProcess

typealias MetamorphicTransformation = (shaderJob: ShaderJob, fuzzerSettings: FuzzerSettings) -> ShaderJob

fun initMetamorphicTransformations(donorShaderFilePath: String): List<MetamorphicTransformation> {
    if (!donorShaderFilePath.endsWith(".wgsl")) {
        System.err.println("Donor shader file $donorShaderFilePath must have extension .wgsl")
        exitProcess(1)
    }

    if (!File(donorShaderFilePath).exists()) {
        System.err.println("Donor shader file $donorShaderFilePath does not exist")
        exitProcess(1)
    }

    val donorUniformsFilePath = donorShaderFilePath.removeSuffix(".wgsl") + ".uniforms.json"
    if (!File(donorUniformsFilePath).exists()) {
        System.err.println("Uniforms file $donorShaderFilePath does not exist")
        exitProcess(1)
    }

    val donorShaderText = File(donorShaderFilePath).readText()
    val donorUniformBuffers = Json.decodeFromString<List<UniformBufferInfoByteLevel>>(File(donorUniformsFilePath).readText())
    val donorShaderJob = createShaderJob(donorShaderText, donorUniformBuffers)

    return listOf(
        ::addDeadDiscards,
        ::addDeadBreaks,
        ::addDeadContinues,
        ::addDeadReturns,
        ::addIdentityOperations,
        addControlFlowWrappers(donorShaderJob),
    )
}
