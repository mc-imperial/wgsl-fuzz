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

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertNotEquals

class ColorgridModuloTransformReduceTests : TransformReduceTests() {
    override val filenameNoExtension: String
        get() = "colorgrid_modulo"

    @Test
    fun `Check filenameNoExtension is not empty`() {
        // A solution for cases where IDEs are unable to detect child classes of test classes as valid test classes.
        assertNotEquals(filenameNoExtension, "")
    }

    @Disabled("TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/137)")
    @Test
    override fun testAddDeadReturns() {
        super.testAddDeadReturns()
    }

    @Disabled("TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/138)")
    @Test
    override fun testAddDeadBreaks() {
        super.testAddDeadBreaks()
    }

    @Disabled("TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/139)")
    @Test
    override fun testAddDeadContinues() {
        super.testAddDeadContinues()
    }

    @Disabled("TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/140)")
    @Test
    override fun testAddIdentityOperations() {
        super.testAddIdentityOperations()
    }

    @Disabled("TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/141)")
    @Test
    override fun testMultipleTransformations() {
        super.testMultipleTransformations()
    }
}
