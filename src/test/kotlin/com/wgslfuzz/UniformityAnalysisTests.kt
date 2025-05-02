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
                workgroupBarrier();
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
                  workgroupBarrier();
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
                }
                workgroupBarrier();
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
                  workgroupBarrier();
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
                workgroupBarrier();
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
                  workgroupBarrier();
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
                   if x {
                     break;
                   } else {
                     ;
                   }
                }
                workgroupBarrier();
            }
        """.trimIndent()
        val state = runAnalysisHelper(program)
        assertTrue(state.callSiteMustBeUniform)
        assertEquals(setOf("a", "b"), state.returnedValueUniformity)
        assertEquals(setOf("a", "b"), state.uniformParams)
    }

    @Test
    fun barrierAfterBreakFreeLoopWithReturn() {
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
                workgroupBarrier();
            }
        """.trimIndent()
        val state = runAnalysisHelper(program)
        assertFalse(state.callSiteMustBeUniform)
        assertEquals(setOf("a", "b"), state.returnedValueUniformity)
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun barrierAfterLoopWithUnconditionalReturn() {
        val program = """
            fn f() {
                var x: u32;
                x = 1;
                loop {
                   return x;
                }
                workgroupBarrier();
            }
        """.trimIndent()
        val state = runAnalysisHelper(program)
        assertFalse(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun barrierInLoopAfterUnconditionalBreak() {
        val program = """
            fn f() {
                loop {
                   break;
                   workgroupBarrier();
                }
            }
        """.trimIndent()
        val state = runAnalysisHelper(program)
        assertFalse(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun barrierInLoopAfterUnconditionalBreaks() {
        val program = """
            fn f() {
                var x: u32;
                x = 1;
                loop {
                   if x {
                     break;
                   } else {
                     break;
                   }
                   workgroupBarrier();
                }
            }
        """.trimIndent()
        val state = runAnalysisHelper(program)
        assertFalse(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun barrierInLoopAfterUnconditionalContinue() {
        val program = """
            fn f() {
                loop {
                   continue;
                   workgroupBarrier();
                }
            }
        """.trimIndent()
        val state = runAnalysisHelper(program)
        assertFalse(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun barrierInLoopAfterUnconditionalContinues() {
        val program = """
            fn f() {
                var x: u32;
                x = 1;
                loop {
                   if x {
                     continue;
                   } else {
                     continue;
                   }
                   workgroupBarrier();
                }
            }
        """.trimIndent()
        val state = runAnalysisHelper(program)
        assertFalse(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun barrierInLoopAfterUnconditionalBreakContinuePair() {
        val program = """
            fn f() {
                var x: u32;
                x = 1;
                loop {
                   if x {
                     break;
                   } else {
                     continue;
                   }
                   workgroupBarrier();
                }
            }
        """.trimIndent()
        val state = runAnalysisHelper(program)
        assertFalse(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun misc1() {
        val program = """
            fn f(tid: u32) {
                var x: u32;
                x = 0;
                if x {
                  x = tid;
                } else {
                  x = 0;
                }
                return x;
            }
        """.trimIndent()
        val state = runAnalysisHelper(program)
        assertFalse(state.callSiteMustBeUniform)
        assertEquals(setOf("tid"), state.returnedValueUniformity)
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun misc2() {
        val program = """
            fn f(tid: u32) {
              var c: u32;
              var x: u32;
              c = 2;
              loop {
                if c {
                  x = tid;
                  continue;
                }
                break;
              }
              return x;
            }
        """.trimIndent()
        val state = runAnalysisHelper(program)
        assertFalse(state.callSiteMustBeUniform)
        assertEquals(setOf("tid"), state.returnedValueUniformity)
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun misc3() {
        val program = """
            fn f(a: u32, tid: u32) {
              var c: u32;
              var x: u32;
              c = a;
              loop {
                if c {
                  break;
                } else {
                  ;
                }
                continue;
              }
              x = tid;
              return x;
            }
        """.trimIndent()
        val state = runAnalysisHelper(program)
        assertFalse(state.callSiteMustBeUniform)
        assertEquals(setOf("tid"), state.returnedValueUniformity)
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun misc4() {
        val program = """
            fn f(a: u32, tid: u32) {
              var x: u32;
              loop {
                ;
              }
              x = tid;
              return x;
            }
        """.trimIndent()
        val state = runAnalysisHelper(program)
        assertFalse(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun misc5() {
        val program = """
            fn f(a: u32, tid: u32) {
              var x: u32;
              loop {
                continue;
              }
              x = tid;
              return x;
            }
        """.trimIndent()
        val state = runAnalysisHelper(program)
        assertFalse(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun misc6() {
        val program = """
            fn main(tid: u32) {
              var z1: u32;
              var z2: u32;
              var tid_: u32;
              tid_ = tid;
              loop {
                workgroupBarrier();
                if z2 {
                  break;
                }
                z2 = z1;
                if tid_ {
                  z1 = 0;
                } else {
                  ;
                }
              }
            }
        """.trimIndent()
        val state = runAnalysisHelper(program)
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertEquals(setOf("tid"), state.uniformParams)
    }

    @Test
    fun manyIterationsToConverge() {
        val program = """
            fn main(tid: u32) {
              var z1: u32;
              var z2: u32;
              var z3: u32;
              var z4: u32;
              var z5: u32;
              var z6: u32;
              var z7: u32;
              var z8: u32;
              var z9: u32;
              var z10: u32;
              var tid_: u32;
              tid_ = tid;
              loop {
                workgroupBarrier();
                if z10 {
                  break;
                }
                z10 = z9;
                z9 = z8;
                z8 = z7;
                z7 = z6;
                z6 = z5;
                z5 = z4;
                z4 = z3;
                z3 = z2;
                z2 = z1;
                if tid_ {
                  z1 = 0;
                } else {
                  ;
                }
              }
            }
        """.trimIndent()
        val state = runAnalysisHelper(program)
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertEquals(setOf("tid"), state.uniformParams)
    }

    @Test
    fun nestedLoop1() {
        val program = """
            fn main(lid: u32) {
              var x: u32;
              var y: u32;
              x = 0;
              y = 0;
              loop {
                if x {
                  break;
                }
                loop {
                  x = lid;  
                  if y {
                    break;
                  }
                }
              }
              if x {
                workgroupBarrier();
              }
            }
        """.trimIndent()
        val state = runAnalysisHelper(program)
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertEquals(setOf("lid"), state.uniformParams)
    }

//    @Test
//    fun nestedLoop1() {
//        val program = """
//        """.trimIndent()
//        val state = runAnalysisHelper(program)
//        assertTrue(state.callSiteMustBeUniform)
//        assertTrue(state.returnedValueUniformity.isEmpty())
//        assertEquals(setOf("tid"), state.uniformParams)
//    }

    private fun runAnalysisHelper(program: String): AnalysisState =
        runAnalysis(parseFromString(program, LoggingParseErrorListener()).globalDecls[0] as GlobalDecl.Function)
}
