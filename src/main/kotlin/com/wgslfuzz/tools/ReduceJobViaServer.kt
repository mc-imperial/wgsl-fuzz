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
import com.wgslfuzz.core.ShaderJob
import com.wgslfuzz.semanticspreservingtransformations.reduce
import io.ktor.client.HttpClient
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.exists

fun main(args: Array<String>) {
    val username =
        System.getenv("WGSL_FUZZ_ADMIN_USERNAME")
            ?: error("Environment variable WGSL_FUZZ_ADMIN_USERNAME not set")
    val password =
        System.getenv("WGSL_FUZZ_ADMIN_PASSWORD")
            ?: error("Environment variable WGSL_FUZZ_ADMIN_PASSWORD not set")

    val parser = ArgParser("Tool for reducing a shader job via a server")

    val serverUrl by parser
        .option(
            ArgType.String,
            fullName = "serverUrl",
            description = "URL of the server",
        ).required()

    val jobFile by parser
        .option(
            ArgType.String,
            fullName = "jobFile",
            description = "Path to a shader job to be reduced",
        ).required()

    val workerName by parser
        .option(
            ArgType.String,
            fullName = "workerName",
            description = "The name of the worker that should execute jobs",
        ).required()

    val outputDir by parser
        .option(
            ArgType.String,
            fullName = "outputDir",
            description = "Directory to write output files",
        ).required()

    val repetitions by parser
        .option(
            ArgType.Int,
            fullName = "repetitions",
            description = "Number of repeat runs of each job",
        ).default(1)

    val timeoutMillis by parser
        .option(
            ArgType.Int,
            fullName = "timeoutMillis",
            description = "Timeout in milliseconds",
        ).default(60 * 1000)

    val developerMode by parser
        .option(
            ArgType.Boolean,
            fullName = "developerMode",
            description = "Enable developer mode",
        ).default(false)

    val expectedOutputText by parser
        .option(
            ArgType.String,
            fullName = "expectedOutputText",
            description = "Text expected in the output for it to be deemed interesting",
        )

    val referenceImage by parser
        .option(
            ArgType.String,
            fullName = "referenceImage",
            description = "Reference image for bad image reduction",
        )

    parser.parse(args)

    println("Parsing shader job from file")
    val shaderJob = Json.decodeFromString<ShaderJob>(File(jobFile.removeSuffix(".wgsl") + ".json").readText())
    println("Parsing complete")

    createClient(
        developerMode = developerMode,
        username = username,
        password = password,
    ).use { httpClient ->
        println("Checking that original shader job is interesting")
        if (!isInteresting(
                shaderJob = shaderJob,
                jobFilename = "original.wgsl",
                reductionWorkDir = outputDir,
                serverUrl = serverUrl,
                httpClient = httpClient,
                repetitions = repetitions,
                workerName = workerName,
                timeoutMillis = timeoutMillis,
                expectedOutputText = expectedOutputText,
                referenceImage = referenceImage,
            )
        ) {
            throw RuntimeException("Original shader job not interesting.")
        }
        println("Original shader job is interesting!")
        val counter = AtomicInteger(0)
        shaderJob.reduce { candidate: ShaderJob ->
            isInteresting(
                shaderJob = candidate,
                jobFilename = "candidate${counter.getAndIncrement()}.wgsl",
                reductionWorkDir = outputDir,
                serverUrl = serverUrl,
                httpClient = httpClient,
                workerName = workerName,
                timeoutMillis = timeoutMillis,
                repetitions = repetitions,
                expectedOutputText = expectedOutputText,
                referenceImage = referenceImage,
            )
        }
    }
}

private fun isInteresting(
    shaderJob: ShaderJob,
    jobFilename: String,
    reductionWorkDir: String,
    serverUrl: String,
    workerName: String,
    httpClient: HttpClient,
    repetitions: Int,
    timeoutMillis: Int,
    expectedOutputText: String?,
    referenceImage: String?,
): Boolean {
    AstWriter(
        PrintStream(
            FileOutputStream(
                File(
                    reductionWorkDir,
                    jobFilename,
                ),
            ),
        ),
    ).emit(shaderJob.tu)
    File(
        reductionWorkDir,
        jobFilename.removeSuffix(".wgsl") + ".uniforms.json",
    ).writeText(Json.encodeToString(shaderJob.getByteLevelContentsForUniformBuffers()))
    runJobViaServer(
        job = File(reductionWorkDir, jobFilename),
        serverUrl = serverUrl,
        httpClient = httpClient,
        repetitions = repetitions,
        workerName = workerName,
        timeoutMillis = timeoutMillis,
        outputDirPath = Path.of(reductionWorkDir),
    )
    if (expectedOutputText != null) {
        if (expectedOutputText !in File(reductionWorkDir, jobFilename.removeSuffix(".wgsl") + ".result.json").readText()) {
            println(1)
            return false
        }
    }
    if (referenceImage != null) {
        if (File(reductionWorkDir, jobFilename.removeSuffix(".wgsl") + ".nondet").exists()) {
            // Nondeterministic output - we would need a separate test for this
            println(2)
            return false
        }
        val resultImage = jobFilename.removeSuffix(".wgsl") + ".png"
        if (!File(reductionWorkDir, resultImage).exists()) {
            // No image - not interesting
            println(resultImage + " does not exist")
            return false
        }
        val referenceImageFile = File(referenceImage)
        val resultImageFile = File(reductionWorkDir, resultImage)
        println(referenceImageFile)
        println(resultImageFile)
        if (identicalImages(referenceImageFile, resultImageFile)) {
            // Identical images - not interesting
            println(4)
            return false
        }
    }
    AstWriter(
        out = PrintStream(FileOutputStream(File(reductionWorkDir, "best_annotated.wgsl"))),
        emitCommentary = true,
    ).emit(
        shaderJob.tu,
    )
    Files.copy(
        Path.of(reductionWorkDir).resolve(jobFilename),
        Path.of(reductionWorkDir).resolve("best.wgsl"),
        StandardCopyOption.REPLACE_EXISTING,
    )
    Files.copy(
        Path.of(reductionWorkDir).resolve(jobFilename.removeSuffix(".wgsl") + ".uniforms.json"),
        Path.of(reductionWorkDir).resolve("best.uniforms.json"),
        StandardCopyOption.REPLACE_EXISTING,
    )
    File(
        reductionWorkDir,
        "best.json",
    ).writeText(Json.encodeToString(shaderJob))
    val resultFile = Path.of(reductionWorkDir).resolve(jobFilename.removeSuffix(".wgsl") + ".result.json")
    if (resultFile.exists()) {
        Files.copy(
            resultFile,
            Path.of(reductionWorkDir).resolve("best.result.json"),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
    val imageFile = Path.of(reductionWorkDir).resolve(jobFilename.removeSuffix(".wgsl") + ".png")
    if (resultFile.exists()) {
        Files.copy(
            imageFile,
            Path.of(reductionWorkDir).resolve("best.png"),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
    return true
}
