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

// Messages from client to server

/**
 * An encoding of an image - typically a PNG in practice
 */
data class ImageData(
    val type: String,
    val encoding: String,
    val data: String,
)

/**
 * Records information obtained by a client by requesting information from a WebGPU adapter
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

/**
 * A message from client to server encapsulating the result of a render job.
 */
data class MessageRenderJobResult(
    val type: String = "RenderJobResult",
    // The job ID. The client must ensure that this matches the ID that was provided in the job it was sent.
    val clientName: String,
    val jobId: Int,
    val renderJobResult: RenderJobResult,
)
