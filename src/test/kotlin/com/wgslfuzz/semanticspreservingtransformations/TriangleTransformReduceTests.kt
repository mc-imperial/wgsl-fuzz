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
}
