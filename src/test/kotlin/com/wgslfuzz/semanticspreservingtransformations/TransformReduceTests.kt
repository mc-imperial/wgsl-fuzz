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

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.wgslfuzz.core.ShaderJob
import com.wgslfuzz.core.parseShaderJob
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Random

class TransformReduceTests {
    @Test
    fun testAddDeadReturns() {
        testTransformationAndReduction(42, "bubblesort_flag.json", ::addDeadReturns)
    }

    @Test
    fun testAddDeadBreaks() {
        testTransformationAndReduction(43, "bubblesort_flag.json", ::addDeadBreaks)
    }

    @Test
    fun testAddDeadContinues() {
        testTransformationAndReduction(44, "bubblesort_flag.json", ::addDeadContinues)
    }

    @Test
    fun testAddIdentityOperations() {
        testTransformationAndReduction(45, "bubblesort_flag.json", ::addIdentityOperations)
    }

    @Test
    fun testMultipleTransformations() {
        testTransformationAndReduction(45, "bubblesort_flag.json") { shaderJob, fuzzerSettings ->
            addIdentityOperations(addDeadReturns(addIdentityOperations(shaderJob, fuzzerSettings), fuzzerSettings), fuzzerSettings)
        }
    }

    private fun testTransformationAndReduction(
        pnrgSeed: Long,
        shaderJobFilename: String,
        transformation: MetamorphicTransformation,
    ) {
        val generator = Random(pnrgSeed)
        val shaderJob =
            parseShaderJob(
                jacksonObjectMapper().readValue<ShaderJob>(
                    File("samples", shaderJobFilename).readText(),
                ),
            )
        val shaderJobAsJson = Json.encodeToJsonElement(shaderJob)
        val transformedShaderJob = transformation(shaderJob, DefaultFuzzerSettings(generator))
        assertNotEquals(shaderJobAsJson, Json.encodeToJsonElement(transformedShaderJob))
        val (reducedShaderJob, reductionMadeChanges) = transformedShaderJob.reduce { true }
        assertTrue(reductionMadeChanges)
        assertEquals(shaderJobAsJson, Json.encodeToJsonElement(reducedShaderJob))
    }
}
