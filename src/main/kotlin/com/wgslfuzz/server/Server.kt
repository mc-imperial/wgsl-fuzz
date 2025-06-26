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

package com.wgslfuzz.server

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.wgslfuzz.core.UniformBufferInfoByteLevel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticFiles
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.isDirectory
import kotlin.system.exitProcess

private object Config {
    val adminUsername: String = getenv("WGSL_FUZZ_ADMIN_USERNAME")
    val adminPassword: String = getenv("WGSL_FUZZ_ADMIN_PASSWORD")

    private fun getenv(key: String): String = System.getenv(key) ?: error("Environment variable $key not set.")

    val consoleCommands: String =
        """
        help        Display this help message
        clients     Get a list of the ids of all connected clients
        job         Issue a job to a client (usage: job <client> <pattern>...)
        """.trimIndent()
}

private class ClientSessionInfo {
    val mutex: Mutex = Mutex()
    val pendingJobFiles: MutableList<String> = mutableListOf()
    var currentlyIssuedJobFile: String? = null
}

private val clientSessions: MutableMap<String, ClientSessionInfo> = ConcurrentHashMap()
private val logger: Logger = LoggerFactory.getLogger("com.wgslfuzz")
private val pathToShaderJobs: Path = Paths.get("work", "shader_jobs")
private val pathToClientDirectories: Path = Paths.get("work", "clients")

fun main() {
    if (!pathToShaderJobs.isDirectory()) {
        logger.error("Shader jobs directory $pathToShaderJobs must exist")
        exitProcess(1)
    }

    if (!pathToClientDirectories.isDirectory()) {
        logger.error("Clients directory $pathToClientDirectories must exist")
        exitProcess(1)
    }

    embeddedServer(
        Netty,
        applicationEnvironment {
            log = logger
        },
        {
            envConfig()
        },
        module = Application::module,
    ).start(wait = true)
}

private fun ApplicationEngine.Configuration.envConfig() {
    connector {
        host = "0.0.0.0"
        port = 8080
    }
}

private fun Application.module() {
    install(Authentication) {
        basic("admin-auth") {
            realm = "Admin Area"
            validate { credentials ->
                if (credentials.name == Config.adminUsername && credentials.password == Config.adminPassword) {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }
    }

    install(CORS) {
        anyHost() // TODO: this is for dev only - don't use in production!
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
        staticFiles("/", File("src/main/resources/static/client")) {
            default("index.html")
        }

        authenticate("admin-auth") {
            post("/console") {
                val command = call.receiveText().split(" ").filter { it.isNotBlank() }
                if (command.isEmpty()) {
                    call.respondText("Empty command")
                    return@post
                }
                handleConsoleCommand(call, command)
            }
        }

        get("/admin/console-ui") {
            call.respondFile(File("src/main/resources/static/admin/console.html"))
        }

        post("/register") {
            val name = call.receiveText()
            val clientDir = pathToClientDirectories.resolve(name)
            if (!clientDir.isDirectory()) {
                clientDir.createDirectory()
            }
            if (clientSessions.containsKey(name)) {
                call.respondText("Client $name already registered")
                return@post
            }
            clientSessions[name] = ClientSessionInfo()
            call.respondText("Registered client $name")
        }

        post("/job") {
            val name = call.receiveText()
            val clientSession = clientSessions[name]
            if (clientSession == null) {
                call.respond(
                    MessageUnknownClient(
                        clientName = name,
                    ),
                )
                return@post
            }
            clientSession.mutex.withLock {
                if (clientSession.pendingJobFiles.isEmpty()) {
                    call.respond(MessageNoJob())
                } else {
                    check(clientSession.currentlyIssuedJobFile == null)
                    clientSession.currentlyIssuedJobFile = clientSession.pendingJobFiles.removeFirst()
                    val shaderFile = clientSession.currentlyIssuedJobFile!!
                    assert(shaderFile.endsWith(".wgsl"))
                    val uniformsFile = shaderFile.removeSuffix(".wgsl") + ".uniforms"
                    val shaderText = pathToShaderJobs.resolve(shaderFile).toFile().readText()
                    val job =
                        ShaderJob(
                            shaderText = shaderText,
                            uniformBuffers =
                                jacksonObjectMapper().readValue<List<UniformBufferInfoByteLevel>>(
                                    pathToShaderJobs.resolve(uniformsFile).toFile().readText(),
                                ),
                        )
                    call.respond(
                        MessageRenderJob(
                            job = job,
                            repetitions = 3,
                        ),
                    )
                }
            }
        }

        post("/renderjobresult") {
            val result = call.receive<MessageRenderJobResult>()
            val clientSession = clientSessions[result.clientName]
            if (clientSession == null) {
                call.respondText("Client ${result.clientName} not found")
                return@post
            }
            clientSession.mutex.withLock {
                check(clientSession.currentlyIssuedJobFile != null)
                val jobFilename = File(clientSession.currentlyIssuedJobFile!!).name
                check(jobFilename.endsWith(".wgsl"))
                val jobFilenameNoSuffix = jobFilename.removeSuffix(".wgsl")
                val clientDir =
                    pathToClientDirectories
                        .resolve(result.clientName)
                val outputFile =
                    clientDir.resolve("$jobFilenameNoSuffix.result")
                jacksonObjectMapper().writeValue(outputFile.toFile(), result.renderJobResult)
                check(result.renderJobResult.renderImageResults.isNotEmpty())
                val firstRunResult = result.renderJobResult.renderImageResults.first()
                var isDeterministic = true
                for (subsequentRunResult in result.renderJobResult.renderImageResults.drop(1)) {
                    if (firstRunResult != subsequentRunResult) {
                        isDeterministic = false
                        break
                    }
                }
                if (isDeterministic) {
                    firstRunResult.frame?.let {
                        writeImageToFile(it, clientDir.resolve("$jobFilenameNoSuffix.png").toFile())
                    }
                } else {
                    clientDir.resolve("$jobFilenameNoSuffix.nondet").createFile()
                    result.renderJobResult.renderImageResults.forEachIndexed { index, renderImageResult ->
                        renderImageResult.frame?.let {
                            writeImageToFile(it, clientDir.resolve("$jobFilenameNoSuffix.$index.png").toFile())
                        }
                    }
                }
                clientSession.currentlyIssuedJobFile = null
                call.respondText("Job result received.")
            }
        }
    }
}

private suspend fun handleConsoleCommand(
    call: ApplicationCall,
    command: List<String>,
) {
    when (command[0]) {
        "help" -> call.respondText(Config.consoleCommands)
        "clients" -> {
            val clients =
                clientSessions.keys
                    .sorted()
                    .joinToString("\n")
                    .ifEmpty { "No client sessions" }
            call.respondText(clients)
        }
        "job" -> {
            if (command.size < 3) {
                call.respondText("Usage: job <client> <pattern>...")
                return
            }
            val clientName = command[1]
            val clientSession = clientSessions[clientName]
            if (clientSession == null) {
                call.respondText("Client $clientName not found")
                return
            }

            val jobPatterns = command.drop(2)
            val matchedJobs =
                jobPatterns.flatMap { pattern ->
                    val regex = pattern.replace("*", ".*").plus("\\.wgsl").toRegex()
                    pathToShaderJobs
                        .toFile()
                        .listFiles()
                        ?.filter { it.isFile && it.name.matches(regex) }
                        ?.map { it.name } ?: emptyList()
                }

            clientSession.mutex.withLock {
                clientSession.pendingJobFiles += matchedJobs
            }

            call.respondText("Jobs issued to client $clientName:\n  ${matchedJobs.joinToString("\n  ")}")
        }
        else -> call.respondText("Unknown command: ${command[0]}")
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
