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

import com.wgslfuzz.utils.CompareImages
import com.wgslfuzz.utils.IdenticalImageCompare
import com.wgslfuzz.utils.MSEImageCompare
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val parser = ArgParser("image compare")

    val file1Path by parser
        .option(
            ArgType.String,
            fullName = "file1Path",
            description = "Path to the first image",
        ).required()

    val file2Path by parser
        .option(
            ArgType.String,
            fullName = "file2Path",
            description = "Path to the second image",
        )

    val file2Dir by parser
        .option(
            ArgType.String,
            fullName = "file2Dir",
            description = "Path to directory of second images to compare relative to file1",
        )

    val identicalImageCompare by parser
        .option(
            ArgType.Boolean,
            fullName = "identicalImageCompare",
            description = "Compare the images to see if they are identical",
        ).default(false)

    val mseThreshold by parser
        .option(
            ArgType.Double,
            fullName = "mseThreshold",
            description = "Compare the images to see if their a mean squared error less than the threshold",
        ).default(-1.0)

    parser.parse(args)

    val compare =
        if (identicalImageCompare && mseThreshold == -1.0) {
            IdenticalImageCompare
        } else if (mseThreshold != -1.0 && !identicalImageCompare) {
            MSEImageCompare(mseThreshold)
        } else {
            throw IllegalArgumentException("Did not set flags correctly for comparison operation")
        }

    val file1 = File(file1Path)
    file2Path?.let { runComparisons(file1, File(it), compare) }

    file2Dir?.let { dirPath ->
        File(dirPath).walk().forEach {
            if (!it.isFile && it.extension != "png") {
                return@forEach
            }

            runComparisons(file1, File(it.path), compare)
        }
    }
}

fun runComparisons(
    file1: File,
    file2: File,
    imageCompareImages: CompareImages,
) {
    val equivalent = imageCompareImages.equivalentImages(file1, file2)
    if (!equivalent) {
        println("Found two images that are not equivalent")
        exitProcess(1)
    }
    println("All images where equivalent")
}
