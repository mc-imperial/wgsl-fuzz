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

package com.wgslfuzz.tools

import com.wgslfuzz.core.AstWriter
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.ShaderJob
import com.wgslfuzz.core.UniformBufferInfoByteLevel
import com.wgslfuzz.core.createShaderJob
import com.wgslfuzz.core.nodesPreOrder
import com.wgslfuzz.semanticspreservingtransformations.DefaultFuzzerSettings
import com.wgslfuzz.semanticspreservingtransformations.FuzzerSettings
import com.wgslfuzz.semanticspreservingtransformations.metamorphicTransformations
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.util.Random

fun main(args: Array<String>) {
    val shaderText = File(args[0]).readText()
    val uniformBuffers = Json.decodeFromString<List<UniformBufferInfoByteLevel>>(File(args[1]).readText())
    val shaderJob = createShaderJob(shaderText, uniformBuffers)

    val fuzzerSettings: FuzzerSettings = DefaultFuzzerSettings(Random())

    for (i in 1..10) {
        var transformedShaderJob: ShaderJob =
            shaderJob
        do {
            // This is for early debugging: ensure that every expression resolves to a type.
            for (node in nodesPreOrder(
                shaderJob.tu,
            )) {
                // Confirm that a type was found for every expression.
                if (node is Expression) {
                    shaderJob.environment.typeOf(node)
                }
            }
            transformedShaderJob =
                fuzzerSettings.randomElement(metamorphicTransformations)(
                    transformedShaderJob,
                    fuzzerSettings,
                )
        } while (fuzzerSettings.randomBool())

        AstWriter(PrintStream(FileOutputStream(File("variant$i.wgsl")))).emit(transformedShaderJob.tu)
        File("variant$i.uniforms").writeText(Json.encodeToString(transformedShaderJob.getByteLevelContentsForUniformBuffers()))
        File("variant$i.json").writeText(Json.encodeToString(transformedShaderJob))
    }
}
