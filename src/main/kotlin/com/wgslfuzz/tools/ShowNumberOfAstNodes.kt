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

import com.wgslfuzz.core.nodesPreOrder
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import kotlinx.serialization.json.Json
import java.io.File

fun main(args: Array<String>) {
    val parser = ArgParser("Utility to show number of AST nodes associated with a shader job")

    val jobFile by parser
        .option(
            ArgType.String,
            fullName = "jobFile",
            description = "Path to a shader job to be examined",
        ).required()

    parser.parse(args)

    val shaderJob = Json.decodeFromString<com.wgslfuzz.core.ShaderJob>(File(jobFile.removeSuffix(".wgsl") + ".shaderjob.json").readText())
    println(nodesPreOrder(shaderJob.tu).size)
}
