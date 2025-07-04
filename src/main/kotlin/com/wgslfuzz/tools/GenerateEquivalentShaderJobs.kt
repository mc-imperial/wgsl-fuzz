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
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.util.Random
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val parser = ArgParser("wgsl-fuzz generator")

    val originalShader by parser
        .option(
            ArgType.String,
            fullName = "originalShader",
            description = "Path to the original shader file",
        ).required()

    val numVariants by parser
        .option(
            ArgType.Int,
            fullName = "numVariants",
            description = "Number of shader variants to generate",
        ).default(10)

    val outputDir by parser
        .option(
            ArgType.String,
            fullName = "outputDir",
            description = "Directory to write output files",
        ).required()

    val seed by parser.option(
        ArgType.Int,
        fullName = "seed",
        description = "Optional PRNG seed",
    )

    parser.parse(args)

    println("Original shader file: $originalShader")
    println("Number of variants: $numVariants")
    println("Output directory: $outputDir")
    println("Seed: ${seed ?: "not provided"}")

    if (!originalShader.endsWith(".wgsl")) {
        System.err.println("Original shader file $originalShader must have extension .wgsl")
        exitProcess(1)
    }

    if (!File(originalShader).exists()) {
        System.err.println("Original shader file $originalShader does not exist")
        exitProcess(1)
    }

    val uniforms = originalShader.removeSuffix(".wgsl") + ".uniforms.json"
    if (!File(uniforms).exists()) {
        System.err.println("Uniforms file $originalShader does not exist")
        exitProcess(1)
    }

    if (!File(outputDir).isDirectory()) {
        System.err.println("Output directory $outputDir must be a directory")
        exitProcess(1)
    }

    val shaderText = File(originalShader).readText()
    val uniformBuffers = Json.decodeFromString<List<UniformBufferInfoByteLevel>>(File(uniforms).readText())
    val shaderJob = createShaderJob(shaderText, uniformBuffers)

    val generator =
        seed?.let {
            Random(it.toLong())
        } ?: Random()

    val fuzzerSettings: FuzzerSettings = DefaultFuzzerSettings(generator)

    val digitsInOutputFilenames = max(2, ceil(log10(numVariants.toDouble())).toInt())

    for (i in 0..<numVariants) {
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

        val paddedNumber = i.toString().padStart(digitsInOutputFilenames, '0')
        AstWriter(PrintStream(FileOutputStream(File(outputDir, "variant$paddedNumber.wgsl")))).emit(transformedShaderJob.tu)
        File(
            outputDir,
            "variant$paddedNumber.uniforms.json",
        ).writeText(Json.encodeToString(transformedShaderJob.getByteLevelContentsForUniformBuffers()))
        File(outputDir, "variant$paddedNumber.shaderjob.json").writeText(Json.encodeToString(transformedShaderJob))
    }
}
