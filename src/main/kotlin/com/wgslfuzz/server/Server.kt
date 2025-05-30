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
import com.wgslfuzz.core.ShaderJob
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
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object Config {
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

class ClientSessionInfo {
    val mutex: Mutex = Mutex()
    val pendingJobFiles: MutableList<String> = mutableListOf()
    var currentlyIssuedJobFile: String? = null
}

val clientSessions: MutableMap<String, ClientSessionInfo> = ConcurrentHashMap()
val logger = LoggerFactory.getLogger("com.wgslfuzz")

fun main() {
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

fun Application.module() {
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
                call.respondText("Client $name not found")
                return@post
            }
            clientSession.mutex.withLock {
                if (clientSession.pendingJobFiles.isEmpty()) {
                    call.respond(MessageNoJob())
                } else {
                    check(clientSession.currentlyIssuedJobFile == null)
                    clientSession.currentlyIssuedJobFile = clientSession.pendingJobFiles.removeFirst()
                    val jsonString = File("work", clientSession.currentlyIssuedJobFile!!).readText()
                    val job = jacksonObjectMapper().readValue<ShaderJob>(jsonString)
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
            check(clientSession.currentlyIssuedJobFile != null)
            // TODO: process result (e.g. persist it)
            clientSession.currentlyIssuedJobFile = null
            call.respondText("Job result received.")
        }
    }
}

suspend fun handleConsoleCommand(
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
            val client = clientSessions[clientName]
            if (client == null) {
                call.respondText("Client $clientName not found")
                return
            }

            val jobPatterns = command.drop(2)
            val matchedJobs =
                jobPatterns.flatMap { pattern ->
                    val regex = pattern.replace("*", ".*").plus("\\.json").toRegex()
                    File("work").listFiles()?.filter { it.isFile && it.name.matches(regex) }?.map { it.name } ?: emptyList()
                }

            client.mutex.withLock {
                client.pendingJobFiles += matchedJobs
            }

            call.respondText("Jobs issued to client $clientName:\n  ${matchedJobs.joinToString("\n  ")}")
        }
        else -> call.respondText("Unknown command: ${command[0]}")
    }
}
