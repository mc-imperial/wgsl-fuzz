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

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import java.io.File

fun main(args: Array<String>) {
    val parser = ArgParser("Tool for running shaders job via a server")

    val wgslFilePath by parser
        .option(
            ArgType.String,
            fullName = "wgslFile",
            description = "Wgsl sample shader to generate html file for",
        )

    val uniformJsonPath by parser
        .option(
            ArgType.String,
            fullName = "uniformJson",
            description = "Json file to read uniform data from",
        )

    val shaderFolderPath by parser
        .option(
            ArgType.String,
            fullName = "shaderFolderPath",
            description = "Folder that contains both the wgsl and uniform json files to convert",
        )

    val outputFilePath by parser
        .option(
            ArgType.String,
            fullName = "output",
            description = "File to write output html to",
        ).default("index.html")

    val watch by parser
        .option(
            ArgType.Boolean,
            fullName = "watch",
            description =
                "Set a file watcher so if either wgslFile or uniformJson changes output html updates with the changes",
        ).default(false)

    parser.parse(args)

    if (wgslFilePath != null && uniformJsonPath != null && shaderFolderPath == null) {
        println("Converting a single file")
        var wgslCode = File(wgslFilePath!!).readText()
        var uniformJson = File(uniformJsonPath!!).readText()
        outputHtml(wgslCode, uniformJson, outputFilePath)

        if (watch) {
            while (true) {
                Thread.sleep(500) // 0.5 seconds
                val newWgslCode = File(wgslFilePath!!).readText()
                val newUniformJson = File(uniformJsonPath!!).readText()
                if (wgslCode != newWgslCode || uniformJson != newUniformJson) {
                    wgslCode = newWgslCode
                    uniformJson = newUniformJson
                    outputHtml(wgslCode, uniformJson, outputFilePath)
                }
            }
        }
    } else if (shaderFolderPath != null && wgslFilePath == null && uniformJsonPath == null) {
        println("Converting a folder")
        File(shaderFolderPath!!).walk().forEach {
            if (it.isDirectory || it.extension != "wgsl") {
                return@forEach
            }
            val wgslCode = File(it.path).readText()
            val uniformJson = File(it.path.removeSuffix(".wgsl") + ".uniforms.json").readText()
            outputHtml(wgslCode, uniformJson, "$outputFilePath/${it.name.removeSuffix(".wgsl")}.html")
        }
    } else {
        throw IllegalArgumentException("Invalid setting of wgslFilePath, uniformJsonPath and shaderFolderPath")
    }
}

private fun outputHtml(
    wgslCode: String,
    uniformJson: String,
    outputFilePath: String,
) {
    File(outputFilePath)
        .writeText(
            renderStandAloneTemplate(
                wgslCode,
                uniformJson,
            ),
        )
}

private fun renderStandAloneTemplate(
    wgslCode: String,
    uniformJson: String,
): String =
    generateHtml(
        """
        |${File(WORKER_JS_PATH).readText()}
        |
        |const shaderText = `
        |$wgslCode
        |`;
        |
        |const uniformBuffers = $uniformJson;
        |
        |const job = {
        |  shaderText,
        |  uniformBuffers,
        |};
        |
        |async function main() {
        |  const result = await executeJob(job, 1);
        |
        |  console.log(result);
        |}
        |
        |main();
        """.trimMargin(),
    )

private fun String.indent(n: Int): String = this.replace("\n", "\n" + " ".repeat(n))

private fun generateHtml(javascript: String): String =
    File(INDEX_HTML_PATH)
        .readText()
        .replace(
            WORKER_JS,
            """
            |<script type="module">
            |  ${javascript.indent(2)}
            |</script>
            """.trimMargin(),
        ).removeAll(SCRIPT_TAGS)

private fun String.removeAll(strings: Iterable<String>): String =
    strings.fold(this) { acc, stringToRemove ->
        acc.replace(stringToRemove, "")
    }

private const val INDEX_HTML_PATH = "src/main/resources/static/worker/index.html"
private const val WORKER_JS_PATH = "src/main/resources/static/worker/js/worker.js"

private const val WORKER_JS = "<script type=\"module\" src=\"js/worker.js\"></script>"

private val SCRIPT_TAGS =
    listOf(
        "<script type=\"module\" src=\"js/logger.js\"></script>",
        "<script type=\"module\" src=\"js/server.js\"></script>",
    )
