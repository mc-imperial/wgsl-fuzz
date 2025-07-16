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
