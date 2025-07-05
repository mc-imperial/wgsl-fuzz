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
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.http.content.staticFiles
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

private object Config {
    val adminUsername: String = getenv("WGSL_FUZZ_ADMIN_USERNAME")
    val adminPassword: String = getenv("WGSL_FUZZ_ADMIN_PASSWORD")
    val keyStorePassword: String = getenv("WGSL_FUZZ_KEYSTORE_PASSWORD")

    private fun getenv(key: String): String = System.getenv(key) ?: error("Environment variable $key not set.")
}

private class WorkerSession {
    private val pendingJobs: ConcurrentLinkedQueue<Pair<RenderJobInfo, CompletableDeferred<RenderJobResult>>> = ConcurrentLinkedQueue()
    private val issuedJobs: ConcurrentHashMap<Int, CompletableDeferred<RenderJobResult>> = ConcurrentHashMap()
    private val nextJobId: AtomicInteger = AtomicInteger()

    fun enqueueJob(
        shaderJob: ShaderJob,
        repetitions: Int,
        completion: CompletableDeferred<RenderJobResult>,
    ) {
        pendingJobs.add(
            Pair(
                RenderJobInfo(
                    jobId = nextJobId.getAndIncrement(),
                    job = shaderJob,
                    repetitions = repetitions,
                ),
                completion,
            ),
        )
    }

    fun issueJob(): RenderJobInfo? =
        pendingJobs.poll()?.let {
            issuedJobs[it.first.jobId] = it.second
            it.first
        }

    fun removeIssuedJob(id: Int): CompletableDeferred<RenderJobResult>? = issuedJobs.remove(id)
}

private val workerSessions: ConcurrentHashMap<String, WorkerSession> = ConcurrentHashMap()

private val logger: Logger = LoggerFactory.getLogger("com.wgslfuzz")

fun main(args: Array<String>) {
    val parser = ArgParser("Server for handling shader jobs")

    val port by parser
        .option(
            ArgType.Int,
            fullName = "port",
            description = "Set port of the server",
        ).default(443)

    parser.parse(args)

    embeddedServer(
        Netty,
        applicationEnvironment {
            log = logger
        },
        {
            envConfig(serverPort = port)
        },
        module = Application::module,
    ).start(wait = true)
}

private fun ApplicationEngine.Configuration.envConfig(serverPort: Int) {
    sslConnector(
        keyStore =
            KeyStore.getInstance("JKS").apply {
                FileInputStream("keystore.jks").use {
                    load(it, Config.keyStorePassword.toCharArray())
                }
            },
        keyAlias = "alias",
        keyStorePassword = { Config.keyStorePassword.toCharArray() },
        privateKeyPassword = { Config.keyStorePassword.toCharArray() },
    ) {
        host = "0.0.0.0"
        port = serverPort
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

    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }

    routing {
        staticFiles("/", File("src/main/resources/static/worker")) {
            default("index.html")
        }

        authenticate("admin-auth") {
            post("/client-submit-job") {
                handleClientSubmitJob(call)
            }
        }

        post("/worker-request-job") {
            handleWorkerRequestJob(call)
        }

        post("/worker-job-result") {
            handleWorkerJobResult(call)
        }
    }
}

private fun getOrRegisterWorker(workerName: String): WorkerSession {
    workerSessions.putIfAbsent(workerName, WorkerSession())
    return workerSessions[workerName]!!
}

private suspend fun handleWorkerJobResult(call: ApplicationCall) {
    val result = call.receive<WorkerToServer.MessageRenderJobResult>()
    val workerSession = getOrRegisterWorker(result.workerName)
    val completion: CompletableDeferred<RenderJobResult>? = workerSession.removeIssuedJob(result.jobId)
    if (completion == null) {
        call.respond(
            ServerToWorker.MessageResultForUnknownJob(
                info = "Result received for job with unexpected ID ${result.jobId}",
            ),
        )
        return
    }
    completion.complete(result.renderJobResult)
    call.respond(
        ServerToWorker.MessageRenderJobResultReceived(),
    )
}

private suspend fun handleWorkerRequestJob(call: ApplicationCall) {
    getOrRegisterWorker(call.receiveText()).issueJob()?.let {
        call.respond(
            ServerToWorker.MessageRenderJob(
                content = it,
            ),
        )
    } ?: call.respond(ServerToWorker.MessageNoJob())
}

private suspend fun handleClientSubmitJob(call: ApplicationCall) {
    val job = call.receive<ClientToServer.MessageIssueJob>()
    val workerSession = workerSessions[job.workerName]
    if (workerSession == null) {
        call.respond(ServerToClient.MessageUnknownWorker())
        return
    }
    val completion = CompletableDeferred<RenderJobResult>()
    workerSession.enqueueJob(
        shaderJob = job.shaderJob,
        repetitions = job.repetitions,
        completion = completion,
    )
    withTimeoutOrNull(job.timeoutMillis) {
        completion.await()
    }?.let { renderJobResult: RenderJobResult ->
        call.respond(
            ServerToClient.MessageRenderJobResult(
                content = renderJobResult,
            ),
        )
    } ?: call.respond(ServerToClient.MessageTimeout())
}
