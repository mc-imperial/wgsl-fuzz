package com.wgslfuzz.server

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.wgslfuzz.core.UniformBufferInfoByteLevel

/**
 * Information needed to render using a shader
 */
data class ShaderJob(
    val shaderText: String,
    val uniformBuffers: List<UniformBufferInfoByteLevel>,
)

/**
 * An encoding of an image - typically a PNG in practice
 */
data class ImageData(
    val type: String,
    val encoding: String,
    val data: String,
)

/**
 * Records information obtained by a worker by requesting information from a WebGPU adapter
 */
data class AdapterInfo(
    val vendor: String,
    val architecture: String,
    val device: String,
    val description: String,
)

/**
 * Records information associated with a warning or error message obtained during WebGPU compilation
 */
data class GPUCompilationMessage(
    val message: String,
    val type: String,
    val lineNum: Int,
    val linePos: Int,
    val offset: Int,
    val length: Int,
)

/**
 * The result obtained on attempting to render an image. All being well, the error fields will all be null, the compilation messages will at worst contain warnings, and an image will be present.
 */
data class RenderImageResult(
    val compilationMessages: List<GPUCompilationMessage>,
    val createShaderModuleValidationError: String?,
    val createShaderModuleOutOfMemoryError: String?,
    val createShaderModuleInternalError: String?,
    val otherValidationError: String?,
    val otherOutOfMemoryError: String?,
    val otherInternalError: String?,
    val frame: ImageData?,
)

/**
 * A render job leads to information obtained from the adapter, plus a number of results. This is because rendering is attempted multiple times to check for nondeterminism.
 */
data class RenderJobResult(
    val adapterInfo: AdapterInfo,
    val renderImageResults: List<RenderImageResult>,
)

data class RenderJobInfo(
    // The ID of the job. Providing this allows a worker to identify which job it is sending a result for when it responds.
    val jobId: Int,
    val job: ShaderJob,
    val repetitions: Int,
)

sealed interface ClientToServer {
    data class MessageIssueJob(
        val type: String = "IssueJob",
        val workerName: String,
        val shaderJob: ShaderJob,
        val repetitions: Int,
        val timeoutMillis: Long,
    ) : ClientToServer
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ServerToClient.MessageUnknownWorker::class, name = "UnknownWorker"),
    JsonSubTypes.Type(value = ServerToClient.MessageTimeout::class, name = "Timeout"),
    JsonSubTypes.Type(value = ServerToClient.MessageRenderJobResult::class, name = "RenderJobResult"),
)
sealed interface ServerToClient {
    data class MessageUnknownWorker(
        val type: String = "UnknownWorker",
    ) : ServerToClient

    data class MessageTimeout(
        val type: String = "Timeout",
    ) : ServerToClient

    data class MessageRenderJobResult(
        val type: String = "RenderJobResult",
        val content: RenderJobResult,
    ) : ServerToClient
}

sealed interface ServerToWorker {
    data class MessageNoJob(
        val type: String = "NoJob",
    ) : ServerToWorker

    data class MessageRenderJob(
        val type: String = "RenderJob",
        val content: RenderJobInfo,
    ) : ServerToWorker

    data class MessageResultForUnknownJob(
        val type: String = "ResultForUnknownJob",
        val info: String,
    ) : ServerToWorker

    data class MessageRenderJobResultReceived(
        val type: String = "RenderJobResultReceived",
    ) : ServerToWorker
}

sealed interface WorkerToServer {
    data class MessageRenderJobResult(
        val type: String = "RenderJobResult",
        // The job ID. The worker must ensure that this matches the ID that was provided in the job it was sent.
        val workerName: String,
        val jobId: Int,
        val renderJobResult: RenderJobResult,
    ) : WorkerToServer
}
