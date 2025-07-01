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

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.wgslfuzz.core.UniformBufferInfoByteLevel
import com.wgslfuzz.server.ClientToServer
import com.wgslfuzz.server.ImageData
import com.wgslfuzz.server.ServerToClient
import com.wgslfuzz.server.ShaderJob
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.X509TrustManager
import kotlin.io.path.createFile
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val username =
        System.getenv("WGSL_FUZZ_ADMIN_USERNAME")
            ?: error("Environment variable WGSL_FUZZ_ADMIN_USERNAME not set")
    val password =
        System.getenv("WGSL_FUZZ_ADMIN_PASSWORD")
            ?: error("Environment variable WGSL_FUZZ_ADMIN_PASSWORD not set")

    val parser = ArgParser("Tool for running shaders job via a server")

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
            description = "Path to a shader job to be executed",
        )

    val jobDir by parser
        .option(
            ArgType.String,
            fullName = "jobDir",
            description = "Path to a directory of shader jobs to be executed",
        )

    val workerName by parser
        .option(
            ArgType.String,
            fullName = "workerName",
            description = "The name of the worker that should execute this job",
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
            description = "Number of repeat runs of the job",
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

    parser.parse(args)

    val jobs: Array<File> = if (jobFile != null) {
        if (jobDir != null) {
            System.err.println("jobFile and jobDir arguments must be mutually exclusive")
            return
        }
        val file = File(jobFile!!)
        if (!file.isFile) {
            System.err.println("Job file ${jobFile} does not exist.")
            return
        }
        arrayOf(file)
    } else if (jobDir != null) {
        val dir = File(jobDir)
        if (!dir.isDirectory) {
            System.err.println("Job directory ${jobDir} does not exist.")
            return
        }
        dir.listFiles { file ->
            file.isFile && file.extension == "wgsl"
        } ?: emptyArray()
    } else {
        System.err.println("One of jobFile or jobDir must be provided.")
        return
    }

    val outputDirPath = Paths.get(outputDir)

    createClient(developerMode, username, password).use { httpClient ->
        for (job in jobs) {
            println("Running job ${job.name}")
            val jobFilenameNoSuffix = job.name.removeSuffix(".wgsl")
            val resultFile =
                outputDirPath.resolve("$jobFilenameNoSuffix.result").toFile()
            if (resultFile.exists()) {
                System.err.println("Result file ${resultFile.absolutePath} already exists.")
                continue
            }
            runJobViaServer(
                job = job,
                httpClient = httpClient,
                serverUrl = serverUrl,
                workerName = workerName,
                repetitions = repetitions,
                timeoutMillis = timeoutMillis,
                outputDirPath = outputDirPath,
            )
            println("Completed job ${job.name}")
        }
    }
}

fun runJobViaServer(
    job: File,
    httpClient: HttpClient,
    serverUrl: String,
    workerName: String,
    repetitions: Int,
    timeoutMillis: Int,
    outputDirPath: Path,
) {
    val jobFilenameNoSuffix = job.name.removeSuffix(".wgsl")
    val messageIssueJob =
        ClientToServer.MessageIssueJob(
            workerName = workerName,
            shaderJob =
                ShaderJob(
                    shaderText = job.readText(),
                    uniformBuffers =
                        jacksonObjectMapper().readValue<List<UniformBufferInfoByteLevel>>(
                            File("${job.absolutePath.removeSuffix(".wgsl")}.uniforms").readText(),
                        ),
                ),
            repetitions = repetitions,
            timeoutMillis = timeoutMillis.toLong(),
        )
    val jobResponse: ServerToClient = runBlocking {
        val response = httpClient.post("$serverUrl/client-submit-job") {
            contentType(ContentType.Application.Json)
            setBody(messageIssueJob)
            timeout {
                requestTimeoutMillis = timeoutMillis.toLong()
            }
        }
        response.body()
    }
    jacksonObjectMapper().writeValue(outputDirPath.resolve("$jobFilenameNoSuffix.result").toFile(), jobResponse)
    if (jobResponse is ServerToClient.MessageRenderJobResult) {
        val renderJobResult = jobResponse.content
        if (renderJobResult.renderImageResults.isNotEmpty()) {
            val firstRunResult = renderJobResult.renderImageResults.first()
            var isDeterministic = true
            for (subsequentRunResult in renderJobResult.renderImageResults.drop(1)) {
                if (firstRunResult != subsequentRunResult) {
                    isDeterministic = false
                    break
                }
            }
            if (isDeterministic) {
                firstRunResult.frame?.let {
                    writeImageToFile(it, outputDirPath.resolve("$jobFilenameNoSuffix.png").toFile())
                }
            } else {
                outputDirPath.resolve("$jobFilenameNoSuffix.nondet").createFile()
                renderJobResult.renderImageResults.forEachIndexed { index, renderImageResult ->
                    renderImageResult.frame?.let {
                        writeImageToFile(it, outputDirPath.resolve("$jobFilenameNoSuffix.$index.png").toFile())
                    }
                }
            }
        }
    }
}

fun createClient(
    developerMode: Boolean,
    username: String,
    password: String
) = HttpClient(CIO) {
    if (developerMode) {
        // This disables certificate validation, which is useful during development but should not be used in production.
        engine {
            https {
                trustManager =
                    object : X509TrustManager {
                        override fun checkClientTrusted(
                            chain: Array<out X509Certificate>?,
                            authType: String?,
                        ) {
                        }

                        override fun checkServerTrusted(
                            chain: Array<out X509Certificate>?,
                            authType: String?,
                        ) {
                        }

                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    }
            }
        }
    }

    install(Auth) {
        basic {
            credentials {
                BasicAuthCredentials(username = username, password = password)
            }
            sendWithoutRequest { true }
        }
    }
    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
            registerKotlinModule()
        }
    }
}

private fun writeImageToFile(
    imageData: ImageData,
    outputFile: File,
) {
    if (imageData.type != "image/png" || imageData.encoding != "base64") {
        throw IllegalArgumentException("Unsupported image type or encoding")
    }

    val decodedBytes = Base64.getDecoder().decode(imageData.data)
    outputFile.writeBytes(decodedBytes)
}
