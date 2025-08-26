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
import com.wgslfuzz.core.GlobalDecl
import com.wgslfuzz.core.ShaderJob
import com.wgslfuzz.core.UniformBufferInfoByteLevel
import com.wgslfuzz.core.createShaderJob
import com.wgslfuzz.core.nodesPreOrder
import com.wgslfuzz.semanticspreservingtransformations.DefaultFuzzerSettings
import com.wgslfuzz.semanticspreservingtransformations.FuzzerSettings
import com.wgslfuzz.semanticspreservingtransformations.initMetamorphicTransformations
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.random.Random
import kotlin.random.asJavaRandom
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val parser = ArgParser("wgsl-fuzz generator")

    val originalShaderFilePath by parser
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

    val nodeLimit by parser
        .option(
            ArgType.Int,
            fullName = "nodeLimit",
            description =
                "Optional limit on the number of AST nodes - once this limit is reached no further " +
                    "transformations will be applied; however the limit might be exceeded due to a transformation being " +
                    "applied before the limit was reached than then takes the number of nodes beyond the limit",
        ).default(200000)

    val donorShaderFilePath by parser
        .option(
            ArgType.String,
            fullName = "donorShader",
            description =
                "File path to a donor shader to be used to generate arbitrary compounds. " +
                    "Donor shader must not contain any Structs",
        ).required()

    parser.parse(args)

    println("Original shader file: $originalShaderFilePath")
    println("Number of variants: $numVariants")
    println("Output directory: $outputDir")
    println("Seed: ${seed ?: "not provided"}")

    // Check output directory exists and is a directory
    if (!File(outputDir).isDirectory) {
        System.err.println("Output directory $outputDir must be a directory")
        exitProcess(1)
    }

    val shaderJob = getShaderJobFromFile(originalShaderFilePath)

    val donorShaderJob = getShaderJobFromFile(donorShaderFilePath)
    require(donorShaderJob.tu.globalDecls.none { it is GlobalDecl.Struct }) { "Donor shader must not contain any Structs" }

    val seedAsLong = seed?.toLong() ?: Random.nextLong() // If a seed is not passed in get a random seed from the default Random object
    println("Using seed: $seedAsLong")
    val generator = Random(seedAsLong).asJavaRandom()

    val fuzzerSettings: FuzzerSettings = DefaultFuzzerSettings(generator)

    val digitsInOutputFilenames = max(2, ceil(log10(numVariants.toDouble())).toInt())

    for (i in 0..<numVariants) {
        var transformedShaderJob: ShaderJob =
            shaderJob
        do {
            if (nodesPreOrder(
                    transformedShaderJob.tu,
                ).size > nodeLimit
            ) {
                break
            }
            val metamorphicTransformations = initMetamorphicTransformations(donorShaderJob)
            transformedShaderJob =
                fuzzerSettings.randomElement(metamorphicTransformations)(
                    transformedShaderJob,
                    fuzzerSettings,
                )
        } while (fuzzerSettings.randomBool())

        val prettyJson = Json { prettyPrint = true }

        val paddedNumber = i.toString().padStart(digitsInOutputFilenames, '0')
        AstWriter(PrintStream(FileOutputStream(File(outputDir, "variant$paddedNumber.wgsl")))).emit(transformedShaderJob.tu)
        File(
            outputDir,
            "variant$paddedNumber.uniforms.json",
        ).writeText(prettyJson.encodeToString(transformedShaderJob.getByteLevelContentsForUniformBuffers()))
        File(outputDir, "variant$paddedNumber.shaderjob.json").writeText(prettyJson.encodeToString(transformedShaderJob))
    }
}

private fun getShaderJobFromFile(shaderFilePath: String): ShaderJob {
    if (!shaderFilePath.endsWith(".wgsl")) {
        System.err.println("Donor shader file $shaderFilePath must have extension .wgsl")
        exitProcess(1)
    }

    if (!File(shaderFilePath).exists()) {
        System.err.println("Donor shader file $shaderFilePath does not exist")
        exitProcess(1)
    }

    val uniformsFilePath = shaderFilePath.removeSuffix(".wgsl") + ".uniforms.json"
    if (!File(uniformsFilePath).exists()) {
        System.err.println("Uniforms file $shaderFilePath does not exist")
        exitProcess(1)
    }

    val shaderText = File(shaderFilePath).readText()
    val uniformBuffers = Json.decodeFromString<List<UniformBufferInfoByteLevel>>(File(uniformsFilePath).readText())
    return createShaderJob(shaderText, uniformBuffers)
}
