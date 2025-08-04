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

import com.wgslfuzz.core.AstWriter
import com.wgslfuzz.core.LoggingParseErrorListener
import com.wgslfuzz.core.parseFromFile
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import java.io.FileOutputStream
import java.io.PrintStream

fun main(args: Array<String>) {
    val parser = ArgParser("wgsl-fuzz pretty printer")

    val shader by parser
        .option(
            ArgType.String,
            fullName = "shader",
            description = "Path to the shader to be pretty-printed",
        ).required()

    val output by parser
        .option(
            ArgType.String,
            fullName = "output",
            description = "File to which the pretty-printed shader will be written",
        ).required()

    parser.parse(args)

    AstWriter(
        PrintStream(FileOutputStream(output)),
    ).emit(parseFromFile(shader, LoggingParseErrorListener()))
}
