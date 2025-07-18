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

import org.junit.jupiter.api.Test
import kotlin.test.assertNotEquals

class LogicOperationsTransformReduceTests : TransformReduceTests() {
    override val filenameNoExtension: String
        get() = "logic_operations"

    @Test
    fun `Check filenameNoExtension is not empty`() {
        // A solution for cases where IDEs are unable to detect child classes of test classes as valid test classes.
        assertNotEquals(filenameNoExtension, "")
    }

    @Test
    override fun testAddDeadBreaks() {
        // This test does not have any loops hence cannot test dead breaks
    }

    @Test
    override fun testAddDeadContinues() {
        // This test does not have any loops hence cannot test dead continues
    }

    @Test
    override fun testAddIdentityOperations() {
        super.testAddIdentityOperations()
    }
}
