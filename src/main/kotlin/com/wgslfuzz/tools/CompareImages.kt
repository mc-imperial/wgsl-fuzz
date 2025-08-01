package com.wgslfuzz.tools

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import java.io.File

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
            fullName = "fil2Dir",
            description = "Path to directory of second images to compare relative to file1",
        )

    val identicalImageCompare by parser
        .option(
            ArgType.Boolean,
            fullName = "identicalImageCompare",
            description = "Compare the images to see if they are identical",
        ).default(false)

    parser.parse(args)

    val compare: (File, File) -> Unit = { file1, file2 -> runComparisons(file1, file2, identicalImageCompare) }

    val file1 = File(file1Path)
    file2Path?.let { runComparisons(file1, File(it), identicalImageCompare) }

    file2Dir?.let { dirPath ->
        File(dirPath).walk().forEach {
            if (!it.isFile && it.extension != "png") {
                return@forEach
            }

            runComparisons(file1, File(it.path), identicalImageCompare)
        }
    }
}

fun runComparisons(
    file1: File,
    file2: File,
    identicalImageCompare: Boolean,
) {
    if (identicalImageCompare) {
        val identical = IdenticalImageCompare.equivalentImages(file1, file2)
        println("The images are: ${if (identical) "identical" else "not identical"}")
    }
}
