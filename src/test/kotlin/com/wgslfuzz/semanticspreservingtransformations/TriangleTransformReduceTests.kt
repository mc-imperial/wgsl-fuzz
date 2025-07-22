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

class TriangleTransformReduceTests : TransformReduceTests() {
    override val filenameNoExtension: String
        get() = "triangle"

    @Test
    fun `Check filenameNoExtension is not empty`() {
        // A solution for cases where IDEs are unable to detect child classes of test classes as valid test classes.
        assertNotEquals(filenameNoExtension, "")
    }

    @Test
    override fun testAddDeadBreaks() {
        // Ignoring test since no loops present hence cannot inject any breaks and so test will fail since transformed code won't change
    }

    @Test
    override fun testAddDeadContinues() {
        // Ignoring test since no loops present hence cannot inject any continues and so test will fail since transformed code won't change
    }
}
