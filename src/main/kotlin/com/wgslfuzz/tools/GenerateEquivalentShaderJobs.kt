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

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.wgslfuzz.core.AstWriter
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.ParsedShaderJob
import com.wgslfuzz.core.ShaderJob
import com.wgslfuzz.core.nodesPreOrder
import com.wgslfuzz.core.parseShaderJob
import com.wgslfuzz.semanticspreservingtransformations.FuzzerSettings
import com.wgslfuzz.semanticspreservingtransformations.metamorphicTransformations
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.util.Random

private class DefaultFuzzerSettings : FuzzerSettings {
    private val generator = Random()

    override fun randomInt(limit: Int): Int = generator.nextInt(limit)

    override fun randomBool(): Boolean = generator.nextBoolean()
}

fun main(args: Array<String>) {
    val jsonString = File(args[0]).readText()
    val shaderJob = jacksonObjectMapper().readValue<ShaderJob>(jsonString)
    val parsedShaderJob = parseShaderJob(shaderJob)

    val fuzzerSettings: FuzzerSettings = DefaultFuzzerSettings()

    for (i in 1..10) {
        var transformedShaderJob: ParsedShaderJob = parsedShaderJob
        do {
            // This is for early debugging: ensure that every expression resolves to a type.
            for (node in nodesPreOrder(parsedShaderJob.tu)) {
                // Confirm that a type was found for every expression.
                if (node is Expression) {
                    parsedShaderJob.environment.typeOf(node)
                }
            }
            transformedShaderJob =
                fuzzerSettings.randomElement(metamorphicTransformations)(
                    transformedShaderJob,
                    fuzzerSettings,
                )
        } while (fuzzerSettings.randomBool())

        AstWriter(PrintStream(FileOutputStream(File("variant$i.wgsl")))).emit(transformedShaderJob.tu)
        File("variant$i.json").writeText(Json.encodeToString(transformedShaderJob))
    }
}
