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

import com.wgslfuzz.core.UniformBufferInfoByteLevel

/**
 * Message from server to client indicating that there is no current job
 */
data class MessageNoJob(
    val type: String = "NoJob",
)

/**
 * Information needed to render using a shader
 */
data class ShaderJob(
    val shaderText: String,
    val uniformBuffers: List<UniformBufferInfoByteLevel>,
)

/**
 * Message from server to client specifying a render job with a number of repetitions
 */
data class MessageRenderJob(
    val type: String = "RenderJob",
    // The WGSL file name. Providing this allows a client to identify which job it is sending a result for when it responds.
    val jobName: String,
    val job: ShaderJob,
    val repetitions: Int,
)

data class MessageBadlyFormedRenderJobResult(
    val type: String = "BadlyFormedRenderJobResult",
    val info: String,
)

data class MessageRenderJobResultAlreadyExists(
    val type: String = "RenderJobResultAlreadyExists",
)

data class MessageRenderJobResultStored(
    val type: String = "RenderJobResultStored",
)
