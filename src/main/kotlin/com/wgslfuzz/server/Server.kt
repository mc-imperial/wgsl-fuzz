package com.wgslfuzz.server

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.wgslfuzz.core.ShaderJob
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
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

val adminUsername: String =
    System.getenv("WGSL_FUZZ_ADMIN_USERNAME") ?: throw RuntimeException("Environment variable WGSL_FUZZ_ADMIN_USERNAME not set.")

val adminPassword: String =
    System.getenv("WGSL_FUZZ_ADMIN_PASSWORD") ?: throw RuntimeException("Environment variable WGSL_FUZZ_ADMIN_PASSWORD not set.")

val consoleCommands: String =
    """
    help        Display this help message
    clients     Get a list of the ids of all connected clients
    """.trimIndent()

class ClientSessionInfo(
    val mutex: Mutex,
    val pendingJobFiles: MutableList<String>,
    var currentlyIssuedJobFile: String?,
)

fun main() {
    embeddedServer(Netty, applicationEnvironment { log = LoggerFactory.getLogger("com.wgslfuzz") }, {
        envConfig()
    }, module = Application::module).start(wait = true)
}

private fun ApplicationEngine.Configuration.envConfig() {
    connector {
        host = "0.0.0.0"
        port = 8080
    }
}

fun Application.module() {
    val clientSessions: MutableMap<String, ClientSessionInfo> = ConcurrentHashMap()

    install(Authentication) {
        basic("admin-auth") {
            realm = "Admin Area"
            validate { credentials ->
                if (credentials.name == adminUsername && credentials.password == adminPassword) {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }
    }

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
        staticFiles("/", File("src/main/resources/static/client")) {
            default("index.html")
        }

        authenticate("admin-auth") {
            post("/console") {
                val command = call.receiveText().split(" ")
                if (command.isEmpty()) {
                    call.respondText("Empty command")
                } else {
                    when (command[0]) {
                        "help" -> call.respond(consoleCommands)
                        "clients" -> {
                            if (clientSessions.isEmpty()) {
                                call.respond("No client sessions")
                            } else {
                                call.respond(
                                    clientSessions.keys.sorted().joinToString("\n"),
                                )
                            }
                        }
                        "job" -> {
                            if (command.size < 3) {
                                call.respond("job takes client and JSON arguments")
                                return@post
                            }
                            val clientName = command[1]
                            val clientSession: ClientSessionInfo? = clientSessions[clientName]
                            if (clientSession == null) {
                                call.respond("Client $clientName not found")
                                return@post
                            }
                            val newJobs = mutableListOf<String>()
                            for (jobWildcard in command.subList(2, command.size)) {
                                val regexPattern = jobWildcard.replace("*", ".*").plus("\\.json").toRegex()
                                val files =
                                    File("work")
                                        .listFiles { file ->
                                            file.isFile && file.name.matches(regexPattern)
                                        }?.toList() ?: emptyList()
                                newJobs.addAll(
                                    files.map {
                                        it.name
                                    },
                                )
                            }
                            clientSession.mutex.withLock {
                                clientSession.pendingJobFiles.addAll(newJobs)
                            }
                            call.respond("Jobs issued to client $clientName:\n  ${newJobs.joinToString("\n  ")}")
                        }
                        else -> call.respond("Unknown command")
                    }
                }
            }
        }

        get("/admin/console-ui") {
            call.respondFile(File("src/main/resources/static/admin/console.html"))
        }

        post("/register") {
            val name = call.receiveText()
            if (name in clientSessions) {
                call.respondText("Client $name already registered")
            }
            clientSessions[name] =
                ClientSessionInfo(
                    mutex = Mutex(),
                    pendingJobFiles = mutableListOf(),
                    currentlyIssuedJobFile = null,
                )
            call.respondText("Registered client $name")
        }

        post("/job") {
            val name = call.receiveText()
            val clientConnection = clientSessions[name]
            if (clientConnection == null) {
                call.respond("Client $name not found")
                return@post
            }
            clientConnection.mutex.withLock {
                if (clientConnection.pendingJobFiles.isEmpty()) {
                    call.respond(MessageNoJob())
                } else {
                    assert(clientConnection.currentlyIssuedJobFile == null)
                    clientConnection.currentlyIssuedJobFile = clientConnection.pendingJobFiles.removeFirst()
                    val jsonString = File("work", clientConnection.currentlyIssuedJobFile!!).readText()
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
            val messageRenderJobResult = call.receive<MessageRenderJobResult>()
            val clientSession = clientSessions[messageRenderJobResult.clientName]
            if (clientSession == null) {
                call.respond("Client $messageRenderJobResult.clientName not found")
                return@post
            }
            assert(clientSession.currentlyIssuedJobFile != null)
            // TODO: do something meaningful with the result, e.g. save it out.
            clientSession.currentlyIssuedJobFile = null
            call.respondText("Job result received.")
        }
    }
}
