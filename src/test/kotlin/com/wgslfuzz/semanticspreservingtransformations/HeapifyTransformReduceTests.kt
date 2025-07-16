package com.wgslfuzz.semanticspreservingtransformations

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertNotEquals

@Disabled("TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/145)")
class HeapifyTransformReduceTests : TransformReduceTests() {
    override val filenameNoExtension: String
        get() = "heapify"

    @Test
    fun `Check filenameNoExtension is not empty`() {
        // A solution for cases where IDEs are unable to detect child classes of test classes as valid test classes.
        assertNotEquals(filenameNoExtension, "")
    }
}
