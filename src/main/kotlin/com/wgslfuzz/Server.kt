package com.wgslfuzz

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.serialization.jackson.jackson
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.Base64


data class ClientConnection(val nickname: String, val jobsIssued: Int)

data class ImageData(
    val type: String,
    val encoding: String,
    val data: String
)

data class UniformBufferInfo(
    val group: Int,
    val binding: Int,
    val data: List<Int>, // Each integer represents a byte
)

data class Job(
    val shaderText: String,
    val uniformBuffers: List<UniformBufferInfo>,
)

// Messages from client to server
data class MessageRegister(val type: String = "Register", val nickname: String)
data class MessageGetJob(val type: String = "GetJob", val clientId: Int)
// TODO: consider adding this; it's currently handled directly below: data class MessageJobResult(val type: String = "JobResult", val resultJson: String)

// Messages from server to client
data class MessageRenderJob(val type: String = "RenderJob", val job: Job)
data class MessageAck(val type: String = "Ack")
data class MessageStop(val type: String = "Stop")

private fun savePng(imageData: ImageData, outputFile: File) {
    // Validate type and encoding
    if (imageData.type != "image/png" || imageData.encoding != "base64") {
        throw IllegalArgumentException("Unsupported image type or encoding")
    }

    // Decode base64 and write to file
    val imageBytes = Base64.getDecoder().decode(imageData.data)
    outputFile.writeBytes(imageBytes)
}

fun listJsonFiles(workingDirname: String): List<String> {
    val workingDir = File(workingDirname)
    return workingDir
        .listFiles { file -> file.isFile && file.extension == "json" }
        ?.map { it.name }
        ?: emptyList()
}

// Main function to start the server
fun main() {
    embeddedServer(Netty, port = 8080) {

        val nextId = AtomicInteger(0)

        val clientConnections: MutableMap<Int, ClientConnection> = ConcurrentHashMap()

        val jobFiles = listJsonFiles("work")

        install(CORS) {
            anyHost() // ⚠️ For dev only — don't use in production!
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowHeader(HttpHeaders.ContentType)
        }

        install(ContentNegotiation) {
            jackson {
                enable(SerializationFeature.INDENT_OUTPUT)
            }
        }

        routing {
            // POST endpoint to receive various kinds of JSON messages
            post("/json") {
                try {
                    val receivedJson = call.receive<Map<String, Any>>()
                    when (val type = receivedJson["type"] as? String) {
                        "Register" -> {
                            val msg = MessageRegister(nickname = receivedJson["nickname"] as String)
                            val clientId = nextId.getAndIncrement()
                            clientConnections[clientId] = ClientConnection(
                                nickname = msg.nickname,
                                jobsIssued = 0,
                            )
                            call.respond(mapOf("clientId" to clientId))
                        }

                        "GetJob" -> {
                            val msg = MessageGetJob(clientId = receivedJson["clientId"] as Int)
                            val clientConnection = clientConnections[msg.clientId]!!
                            val jobsIssued = clientConnection.jobsIssued
                            if (jobsIssued == jobFiles.size) {
                                call.respond(MessageStop())
                            } else {
                                clientConnections[msg.clientId] = clientConnection.copy(
                                    jobsIssued = jobsIssued + 1
                                )
                                val jsonString = File("work", jobFiles[jobsIssued]).readText()
                                val job = jacksonObjectMapper().readValue<Job>(jsonString)
                                call.respond(
                                    MessageRenderJob(
                                        job = job
                                    )
                                )
                            }
                        }

                        "JobResult" -> {
                            val mapper = jacksonObjectMapper()
                            val imageData = mapper.readValue<ImageData>(receivedJson["result"] as String)
                            savePng(imageData, File("image.png"))
                            call.respond(MessageAck())
                        }

                        else -> call.respond(
                            mapOf(
                                "status" to "Error",
                                "message" to "Unknown message type: $type",
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    throw e
                }
            }
        }
    }.start(wait = true)
}
