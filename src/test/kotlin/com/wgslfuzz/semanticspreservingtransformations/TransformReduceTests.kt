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

package com.wgslfuzz.semanticspreservingtransformations

import com.wgslfuzz.core.AstWriter
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.createShaderJob
import com.wgslfuzz.core.nodesPreOrder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.util.Random

abstract class TransformReduceTests {
    protected abstract val filenameNoExtension: String

    @Test
    open fun testAddDeadReturns() {
        testTransformationAndReduction(
            42,
            filenameNoExtension,
            ::addDeadReturns,
        )
    }

    @Test
    open fun testAddDeadBreaks() {
        testTransformationAndReduction(
            43,
            filenameNoExtension,
            ::addDeadBreaks,
        )
    }

    @Test
    open fun testAddDeadContinues() {
        testTransformationAndReduction(
            44,
            filenameNoExtension,
            ::addDeadContinues,
        )
    }

    @Test
    open fun testAddIdentityOperations() {
        testTransformationAndReduction(
            45,
            filenameNoExtension,
            ::addIdentityOperations,
        )
    }

    @Test
    open fun testControlFlowWrapping() {
        testTransformationAndReduction(
            45,
            filenameNoExtension,
            ::addControlFlowWrappers,
        )
    }

    @Test
    open fun testMultipleTransformations() {
        testTransformationAndReduction(
            45,
            filenameNoExtension,
        ) { shaderJob, fuzzerSettings ->
            addIdentityOperations(addDeadReturns(addIdentityOperations(shaderJob, fuzzerSettings), fuzzerSettings), fuzzerSettings)
        }
    }

    fun testTransformationAndReduction(
        pnrgSeed: Long,
        filenameNoExtension: String,
        transformation: MetamorphicTransformation,
    ) {
        val generator = Random(pnrgSeed)
        val shaderJob =
            createShaderJob(
                File("samples", "$filenameNoExtension.wgsl").readText(),
                Json.decodeFromString(
                    File("samples", "$filenameNoExtension.uniforms.json").readText(),
                ),
            )
        val shaderJobAsJson = Json.encodeToJsonElement(shaderJob)

        val shaderJobWgslTextOutStream = ByteArrayOutputStream()
        AstWriter(out = PrintStream(shaderJobWgslTextOutStream)).emit(shaderJob.tu)
        shaderJobWgslTextOutStream.flush()
        shaderJobWgslTextOutStream.close()
        val shaderJobWgslText = shaderJobWgslTextOutStream.toString("UTF-8")

        val transformedShaderJob = transformation(shaderJob, DefaultFuzzerSettings(generator))
        assertNotEquals(shaderJobAsJson, Json.encodeToJsonElement(transformedShaderJob))
        // Ensure that every expression resolves to a type.
        for (node in nodesPreOrder(transformedShaderJob.tu)) {
            if (node is Expression) {
                transformedShaderJob.environment.typeOf(node)
            }
        }
        val reductionResult = transformedShaderJob.reduce { true }
        assertNotNull(reductionResult)

        val reductionResultWgslTextOutStream = ByteArrayOutputStream()
        AstWriter(out = PrintStream(reductionResultWgslTextOutStream)).emit(reductionResult!!.first.tu)
        reductionResultWgslTextOutStream.flush()
        reductionResultWgslTextOutStream.close()
        val reductionResultWgslText = reductionResultWgslTextOutStream.toString("UTF-8")
        assertEquals(shaderJobWgslText, reductionResultWgslText)

        assertEquals(shaderJobAsJson, Json.encodeToJsonElement(reductionResult!!.first))
    }
}
