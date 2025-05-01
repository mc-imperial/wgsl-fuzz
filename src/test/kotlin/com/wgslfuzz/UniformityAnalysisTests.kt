package com.wgslfuzz

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UniformityAnalysisTests {

    @Test
    fun simpleProgram() {
        val program = """
            fn f() {
                ;
            }
        """.trimIndent()
        val state = runAnalysisHelper(program)
        assertFalse(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun unconditionalBarrier() {
        val program = """
            fn f() {
                barrier();
            }
        """.trimIndent()
        val state = runAnalysisHelper(program)
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun conditionalBarrier() {
        val program = """
            fn f(a: u32) {
                var x: u32;
                x = a;
                if x {
                  barrier();
                } else {
                  ;
                }
            }
        """.trimIndent()
        val state = runAnalysisHelper(program)
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertEquals(setOf("a"), state.uniformParams)
    }

    @Test
    fun barrierFollowingConditionalReturn() {
        val program = """
            fn f(a: u32, b: u32) {
                var x: u32;
                var y: u32;
                x = a;
                if x {
                  y = b;
                  return y;
                } else {
                  ;
                }
                barrier();
            }
        """.trimIndent()
        val state = runAnalysisHelper(program)
        assertTrue(state.callSiteMustBeUniform)
        assertEquals(setOf("a", "b"), state.returnedValueUniformity)
        assertEquals(setOf("a"), state.uniformParams)
    }

    @Test
    fun unreachableBarrier() {
        val program = """
            fn f(a: u32, b: u32) {
                var x: u32;
                var y: u32;
                x = a;
                y = b;
                if x {
                  return x;
                } else {
                  return y;
                }
                if x {
                  barrier();
                } else {
                  ;
                }
            }
        """.trimIndent()
        val state = runAnalysisHelper(program)
        assertFalse(state.callSiteMustBeUniform)
        assertEquals(setOf("a", "b"), state.returnedValueUniformity)
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun barrierAfterLoop() {
        val program = """
            fn f(a: u32, b: u32) {
                var x: u32;
                var y: u32;
                var z: u32;
                x = a;
                y = b;
                loop {
                   z = x > y;
                   if z {
                     break;
                   } else {
                     x = x + 1;
                   }
                }
                barrier();
            }
        """.trimIndent()
        val state = runAnalysisHelper(program)
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun conditionalBarrierAfterLoop() {
        val program = """
            fn f(a: u32, b: u32) {
                var x: u32;
                var y: u32;
                var z: u32;
                var one: u32;
                x = a;
                y = b;
                z = 1;
                one = 1;
                loop {
                   z = x > y;
                   if z {
                     break;
                   } else {
                     x = x + one;
                   }
                }
                if z {
                  barrier();
                } else {
                  ;
                }
            }
        """.trimIndent()
        val state = runAnalysisHelper(program)
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertEquals(setOf("a", "b"), state.uniformParams)
    }

    @Test
    fun barrierAfterLoopWithReturn() {
        val program = """
            fn f(a: u32, b: u32) {
                var x: u32;
                var y: u32;
                var z: u32;
                var t: u32;
                x = a;
                y = b;
                t = 0;
                loop {
                   z = x > y;
                   if z {
                     return t;
                   } else {
                     x = x + 1;
                   }
                }
                barrier();
            }
        """.trimIndent()
        val state = runAnalysisHelper(program)
        assertTrue(state.callSiteMustBeUniform)
        assertEquals(setOf("a", "b"), state.returnedValueUniformity)
        assertEquals(setOf("a", "b"), state.uniformParams)
    }

    private fun runAnalysisHelper(program: String): AnalysisState =
        runAnalysis(parseFromString(program, LoggingParseErrorListener()).globalDecls[0] as GlobalDecl.Function)
}
