package com.wgslfuzz.tools

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.wgslfuzz.core.UniformBufferInfoByteLevel
import io.ktor.serialization.jackson.jackson
import com.wgslfuzz.server.ClientToServer
import com.wgslfuzz.server.ShaderJob
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.ktor.serialization.kotlinx.json.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import java.io.File

suspend fun main(args: Array<String>) {
    val username = System.getenv("WGSL_FUZZ_ADMIN_USERNAME")
        ?: error("Environment variable WGSL_FUZZ_ADMIN_USERNAME not set")
    val password = System.getenv("WGSL_FUZZ_ADMIN_PASSWORD")
        ?: error("Environment variable WGSL_FUZZ_ADMIN_PASSWORD not set")

    val parser = ArgParser("Tool for running a shader job via a server")

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
            description = "The shader job to be executed",
        ).required()

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

    parser.parse(args)

    val client = HttpClient(CIO) {
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
            }
        }
    }

    try {
        val uniformText: String = File("${jobFile.removeSuffix(".wgsl")}.uniforms").readText()
        jacksonObjectMapper().readValue<UniformBufferInfoByteLevel>(uniformText)
//        val temp = jacksonObjectMapper().readValue<List<UniformBufferInfoByteLevel>>(
//            uniformText
//        )
        val messageIssueJob = ClientToServer.MessageIssueJob(
            workerName = workerName,
            shaderJob = ShaderJob(
                shaderText = File(jobFile).readText(),
                uniformBuffers = ,
            ),
            repetitions = repetitions,
            timeoutMillis = timeoutMillis.toLong(),
        )
        val response = client.post("$serverUrl/admin-auth/issue-job") {
            contentType(ContentType.Application.Json)
            setBody(messageIssueJob)
        }

    } finally {
        client.close()
    }
}

@Serializable
data class MyJsonPayload(
    val command: String,
    val parameters: Map<String, String>
)

// private suspend fun handleJob(call: ApplicationCall) {
//    val clientName = call.receiveText()
//    getOrRegisterWorker(clientName).issueJob()?.let { (jobId, jobFile) ->
//        assert(jobFile.endsWith(".wgsl"))
//        call.respond(
//            MessageRenderJob(
//                jobId = jobId,
//                job =
//                    ShaderJob(
//                        shaderText = pathToShaderJobs.resolve(jobFile).toFile().readText(),
//                        uniformBuffers =
//                            jacksonObjectMapper().readValue<List<UniformBufferInfoByteLevel>>(
//                                pathToShaderJobs.resolve("${jobFile.removeSuffix(".wgsl")}.uniforms").toFile().readText(),
//                            ),
//                    ),
//                repetitions = 3,
//            ),
//        )
//    } ?: call.respond(MessageNoJob())
//}
//
//private suspend fun handleRenderJobResult(call: ApplicationCall) {
//    val result = call.receive<MessageRenderJobResult>()
//    val clientSession = getOrRegisterWorker(result.clientName)
//    val clientJob = clientSession.removeIssuedJob(result.jobId)
//    if (clientJob == null) {
//        call.respond(
//            MessageResultForUnknownJob(
//                info = "Result received for job with unexpected ID ${result.jobId}",
//            ),
//        )
//        return
//    }
//    val jobFilenameNoSuffix = clientJob.jobFile.removeSuffix(".wgsl")
//    val clientDir =
//        pathToWorkerDirectories
//            .resolve(result.clientName)
//    val resultFile =
//        clientDir.resolve("$jobFilenameNoSuffix.result").toFile()
//    if (resultFile.exists()) {
//        call.respond(
//            MessageRenderJobResultAlreadyExists(),
//        )
//        return
//    }
//    jacksonObjectMapper().writeValue(resultFile, result.renderJobResult)
//    if (result.renderJobResult.renderImageResults.isNotEmpty()) {
//        val firstRunResult = result.renderJobResult.renderImageResults.first()
//        var isDeterministic = true
//        for (subsequentRunResult in result.renderJobResult.renderImageResults.drop(1)) {
//            if (firstRunResult != subsequentRunResult) {
//                isDeterministic = false
//                break
//            }
//        }
//        if (isDeterministic) {
//            firstRunResult.frame?.let {
//                writeImageToFile(it, clientDir.resolve("$jobFilenameNoSuffix.png").toFile())
//            }
//        } else {
//            clientDir.resolve("$jobFilenameNoSuffix.nondet").createFile()
//            result.renderJobResult.renderImageResults.forEachIndexed { index, renderImageResult ->
//                renderImageResult.frame?.let {
//                    writeImageToFile(it, clientDir.resolve("$jobFilenameNoSuffix.$index.png").toFile())
//                }
//            }
//        }
//    }
//    clientJob.completion.complete()
//    call.respond(
//        MessageRenderJobResultStored(),
//    )
//}

// private fun writeImageToFile(
//    imageData: ImageData,
//    outputFile: File,
//) {
//    if (imageData.type != "image/png" || imageData.encoding != "base64") {
//        throw IllegalArgumentException("Unsupported image type or encoding")
//    }
//
//    val decodedBytes = Base64.getDecoder().decode(imageData.data)
//    outputFile.writeBytes(decodedBytes)
//}