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

package com.wgslfuzz.uniformityanalysis

import com.wgslfuzz.core.GlobalDecl
import com.wgslfuzz.core.LoggingParseErrorListener
import com.wgslfuzz.core.parseFromString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Uncomment these if using processDirectoryOfShaders
// import com.wgslfuzz.core.parseFromFile
// import java.io.File

class UniformityDataFlowAnalysisTests {
    // // Helpers to crunch through a large number of shaders to check that the analysis agrees with tint.

    //    fun runExternalProgram(command: List<String>, workingDir: File? = null): Pair<Int, String> {
    //        val process = ProcessBuilder(command)
    //            .apply {
    //                if (workingDir != null) directory(workingDir)
    //                redirectErrorStream(true) // merge stderr into stdout
    //            }
    //            .start()
    //
    //        val output = process.inputStream.bufferedReader().use { it.readText() }
    //        val exitCode = process.waitFor()
    //
    //        return exitCode to output
    //    }
    //
    //    @Test
    //    fun processDirectoryOfShaders() {
    //        val path = "/path/to/your/shaders"
    //        val tint_path = "/path/to/tint"
    //        // File needs to be imported
    //        val dir = File(path)
    //        if (!dir.isDirectory) {
    //            println("Not a directory: $path")
    //            return
    //        }
    //
    //        dir.walkTopDown()  // recursively traverse subdirectories too
    //            .filter { it.isFile && it.extension == "wgsl" }
    //            .forEach { file ->
    //                println("Found WGSL file: ${file.absolutePath}")
    //                // parseFromFile needs to be imported
    //                val tu = parseFromFile(file.absolutePath, LoggingParseErrorListener())
    //                val analysisResult = runAnalysis(tu, maximalReconvergence = false)
    //                val tintResult = runExternalProgram(listOf(tint_path, file.absolutePath))
    //                // Run with maximalReconvergence simply to confirm it doesn't fall over.
    //                runAnalysis(tu, maximalReconvergence = true)
    //
    //                assertEquals (tintResult.first == 0, analysisResult.uniformityErrors.isEmpty())
    //            }
    //    }

    @Test
    fun simpleProgram() {
        val program =
            """
            fn f() -> u32 {
                return 0;
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertFalse(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun unconditionalBarrier() {
        val program =
            """
            fn f() -> u32 {
                workgroupBarrier();
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun conditionalBarrier() {
        val program =
            """
            fn f(a: u32) -> u32 {
                var x: u32;
                x = a;
                if x {
                  workgroupBarrier();
                } else {
                  ;
                }
                return 0;
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertEquals(setOf(0), state.uniformParams)
    }

    @Test
    fun barrierFollowingConditionalReturn() {
        val program =
            """
            fn f(a: u32, b: u32) -> u32 {
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
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertTrue(state.callSiteMustBeUniform)
        assertEquals(setOf(0, 1), state.returnedValueUniformity)
        assertEquals(setOf(0), state.uniformParams)
    }

    @Test
    fun unreachableBarrier() {
        val program =
            """
            fn f(a: u32, b: u32) -> u32 {
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
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertFalse(state.callSiteMustBeUniform)
        assertEquals(setOf(0, 1), state.returnedValueUniformity)
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun barrierAfterLoop() {
        val program =
            """
            fn f(a: u32, b: u32) -> u32 {
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
                return 0;
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun conditionalBarrierAfterLoop() {
        val program =
            """
            fn f(a: u32, b: u32) -> u32 {
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
                return 0;
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertEquals(setOf(0, 1), state.uniformParams)
    }

    @Test
    fun barrierAfterLoopWithReturn() {
        val program =
            """
            fn f(a: u32, b: u32) -> u32 {
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
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertTrue(state.callSiteMustBeUniform)
        assertEquals(setOf(0, 1), state.returnedValueUniformity)
        assertEquals(setOf(0, 1), state.uniformParams)
    }

    @Test
    fun barrierAfterBreakFreeLoopWithReturn() {
        val program =
            """
            fn f(a: u32, b: u32) -> u32 {
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
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertFalse(state.callSiteMustBeUniform)
        assertEquals(setOf(0, 1), state.returnedValueUniformity)
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun barrierAfterLoopWithUnconditionalReturn() {
        val program =
            """
            fn f() -> u32 {
                var x: u32;
                x = 1;
                loop {
                   return x;
                }
                workgroupBarrier();
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertFalse(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun barrierInLoopAfterUnconditionalBreak() {
        val program =
            """
            fn f() -> u32 {
                loop {
                   break;
                   workgroupBarrier();
                }
                return 0;
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertFalse(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun barrierInLoopAfterUnconditionalBreaks() {
        val program =
            """
            fn f() -> i32 {
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
                return 0;
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertFalse(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun barrierInLoopAfterUnconditionalContinue() {
        val program =
            """
            fn f() -> u32 {
                loop {
                   continue;
                   workgroupBarrier();
                }
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertFalse(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun barrierInLoopAfterUnconditionalContinues() {
        val program =
            """
            fn f() -> u32 {
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
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertFalse(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun barrierInLoopAfterUnconditionalBreakContinuePair() {
        val program =
            """
            fn f() -> u32 {
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
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertFalse(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun misc1() {
        val program =
            """
            fn f(tid: u32) -> u32 {
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
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertFalse(state.callSiteMustBeUniform)
        assertEquals(setOf(0), state.returnedValueUniformity)
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun misc2() {
        val program =
            """
            fn f(tid: u32) -> u32 {
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
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertFalse(state.callSiteMustBeUniform)
        assertEquals(setOf(0), state.returnedValueUniformity)
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun misc3() {
        val program =
            """
            fn f(a: u32, tid: u32) -> u32 {
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
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertFalse(state.callSiteMustBeUniform)
        assertEquals(setOf(1), state.returnedValueUniformity)
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun misc4() {
        val program =
            """
            fn f(a: u32, tid: u32) -> u32 {
              var x: u32;
              loop {
                ;
              }
              x = tid;
              return x;
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertFalse(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun misc5() {
        val program =
            """
            fn f(a: u32, tid: u32) -> u32 {
              var x: u32;
              loop {
                continue;
              }
              x = tid;
              return x;
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertFalse(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun misc6() {
        val program =
            """
            fn f(tid: u32) -> u32 {
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
              return 0;
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertEquals(setOf(0), state.uniformParams)
    }

    @Test
    fun manyIterationsToConverge() {
        val program =
            """
            fn f(tid: u32) -> u32 {
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
              return 0;
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertEquals(setOf(0), state.uniformParams)
    }

    @Test
    fun loop1() {
        val program =
            """
            @compute @workgroup_size(16,1,1)
            fn main(@builtin(local_invocation_index) lid: u32) {
              var x: u32;
              var _lid: u32;
              _lid = lid;
              if _lid {
                loop {
                  x = 1;
                  if x {
                    break;
                  }
                }
              }
              if x {
                workgroupBarrier();
              }
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertEquals(setOf(Pair("main", 0)), errors)
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertEquals(setOf(0), state.uniformParams)
    }

    @Test
    fun loop1NoBreak() {
        val program =
            """
            fn f(tid: u32) -> u32 {
              var x: u32;
              var _tid: u32;
              _tid = tid;
              if _tid {
                loop {
                  x = 1;
                }
              }
              if x {
                workgroupBarrier();
              }
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun loop2() {
        val program =
            """
            @compute @workgroup_size(16,1,1)
            fn main(@builtin(local_invocation_index) lid: u32) {
              var x: u32;
              var myLid: u32;
              myLid = lid;
              if myLid {
                loop {
                  // This does not lead to non-uniformity at the barrier because the loop
                  // does not terminate; it's a different kind of problem.
                  x = 1;
                }
              }
              if x {
                workgroupBarrier();
              }
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun loop3() {
        val program =
            """
            @compute @workgroup_size(16,1,1)
            fn main(@builtin(local_invocation_index) lid: u32) {
              var myLid: u32;
              var x: u32;
              var tt: u32;
              myLid = lid;
              loop {
                if myLid { continue; }
                x = 1;
                if tt { break; }
              }
              if x {
                workgroupBarrier();
              }
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertEquals(setOf(Pair("main", 0)), errors)
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertEquals(setOf(0), state.uniformParams)
    }

    @Test
    fun loop3MaximalReconvergence() {
        val program =
            """
            @compute @workgroup_size(16,1,1)
            fn main(@builtin(local_invocation_index) lid: u32) {
              var myLid: u32;
              var x: u32;
              var tt: u32;
              myLid = lid;
              loop {
                if myLid { continue; }
                x = 1;
                if tt { break; }
              }
              if x {
                workgroupBarrier();
              }
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program, maximalReconvergence = true)
        assertEquals(setOf(Pair("main", 0)), errors)
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertEquals(setOf(0), state.uniformParams)
    }

    @Test
    fun loop4() {
        val program =
            """
            @compute @workgroup_size(16,1,1)
            fn main(@builtin(local_invocation_index) lid: u32) {
              var myLid: u32;
              var x1: u32;
              var x2: u32;
              var x3: u32;
              var x4: u32;
              var x5: u32;
              var result: u32;
              myLid = lid;
              loop {
                if x5 { result = myLid; }
                if x4 { x5 = 1; }
                if x3 { x4 = 1; }
                if x2 { x3 = 1; }
                if x1 { x2 = 1; }
              }
              // The barrier is not reachable (infinite loop) so call site need not be uniform
              if result {
                workgroupBarrier();
              }
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertFalse(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun loop5() {
        val program =
            """
            @compute @workgroup_size(16, 1, 1)
            fn main(@builtin(local_invocation_index) lid: u32) {
              var x: u32;
              var myLid: u32;
              myLid = lid;
              loop {
                if x { workgroupBarrier(); }
                x = myLid;
              }
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertEquals(setOf(Pair("main", 0)), errors)
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertEquals(setOf(0), state.uniformParams)
    }

    @Test
    fun loop6() {
        val program =
            """
            @compute @workgroup_size(16, 1, 1)
            fn main(@builtin(local_invocation_index) lid: u32) {
              var x: u32;
              var y: u32;
              var myLid: u32;
              myLid = lid;
              loop {
                x = myLid;
                if y { break; }
                x = 5;
              }
              if x {
                workgroupBarrier();
              }
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertEquals(setOf(Pair("main", 0)), errors)
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertEquals(setOf(0), state.uniformParams)
    }

    @Test
    fun loop7() {
        val program =
            """
            @compute @workgroup_size(16,1,1)
            fn main(@builtin(local_invocation_index) lid: u32) {
              var x: u32;
              var y: u32;
              var myLid: u32;
              myLid = lid;
              loop {
                x = myLid;
                if y { break; }
                x = 5;
                if y { break; }
              }
              if x {
                workgroupBarrier();
              }
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertEquals(setOf(Pair("main", 0)), errors)
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertEquals(setOf(0), state.uniformParams)
    }

    @Test
    fun loop8() {
        val program =
            """
            @compute @workgroup_size(16, 1, 1)
            fn main(@builtin(local_invocation_index) lid: u32) {
              var x: u32;
              var myLid: u32;
              myLid = lid;
              loop {
                if myLid { continue; }
                // This introduces non-uniformity from the analysis' perspective
                x = x;
                break;
              }
            
              if x { workgroupBarrier(); }
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertEquals(setOf(Pair("main", 0)), errors)
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertEquals(setOf(0), state.uniformParams)
    }

    @Test
    fun loop8MaximalReconvergence() {
        val program =
            """
            @compute @workgroup_size(16, 1, 1)
            fn main(@builtin(local_invocation_index) lid: u32) {
              var x: u32;
              var myLid: u32;
              myLid = lid;
              loop {
                if myLid { continue; }
                // This introduces non-uniformity from the analysis' perspective
                x = x;
                break;
              }
            
              if x { workgroupBarrier(); }
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program, maximalReconvergence = true)
        assertEquals(setOf(Pair("main", 0)), errors)
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertEquals(setOf(0), state.uniformParams)
    }

    @Test
    fun nestedLoop1() {
        val program =
            """
            fn f(tid: u32) -> u32 {
              var x: u32;
              var y: u32;
              x = 0;
              y = 0;
              loop {
                if x {
                  break;
                }
                loop {
                  x = tid;  
                  if y {
                    break;
                  }
                }
              }
              if x {
                workgroupBarrier();
              }
              return 0;
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertEquals(setOf(0), state.uniformParams)
    }

    @Test
    fun nestedLoop2() {
        val program =
            """
            @compute @workgroup_size(16,1,1)
            fn main(@builtin(local_invocation_index) lid: u32) {
              var myLid: u32;
              var u: u32;
              myLid = lid;
              loop {
                loop {
                  if myLid { return u; }
                }
              }
              workgroupBarrier();
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertFalse(state.callSiteMustBeUniform)
        assertEquals(setOf(0), state.returnedValueUniformity)
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun nestedLoop3() {
        val program =
            """
            @compute @workgroup_size(16,1,1)
            fn main(@builtin(local_invocation_index) lid: u32) {
              var myLid: u32;
              var u: u32;
              myLid = lid;
              loop {
                workgroupBarrier();
                loop {
                  if myLid {
                    return u;
                  }
                }
              }
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertTrue(state.callSiteMustBeUniform)
        assertEquals(setOf(0), state.returnedValueUniformity)
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun nestedLoop4() {
        val program =
            """
            @compute @workgroup_size(16,1,1)
            fn main(@builtin(local_invocation_index) lid: u32) {
              var myLid: u32;
              var x: u32;
              var ff: u32;
              myLid = lid;
              loop {
                if x {
                  workgroupBarrier();
                }
            
                loop {
                  if myLid {
                    continue;
                  } 
            
                  x = 1;
                  break;
                }
            
                if ff {
                  break;
                }
              }
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertEquals(setOf(Pair("main", 0)), errors)
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertEquals(setOf(0), state.uniformParams)
    }

    @Test
    fun nestedLoop4MaximalReconvergence() {
        val program =
            """
            @compute @workgroup_size(16,1,1)
            fn main(@builtin(local_invocation_index) lid: u32) {
              var myLid: u32;
              var x: u32;
              var ff: u32;
              myLid = lid;
              loop {
                if x {
                  workgroupBarrier();
                }
            
                loop {
                  if myLid {
                    continue;
                  } 
            
                  x = 1;
                  break;
                }
            
                if ff {
                  break;
                }
              }
            }
            """.trimIndent()
        val (state, errors) =
            runSingleFunctionAnalysisHelper(
                program,
                maximalReconvergence = true,
            )
        assertEquals(setOf(Pair("main", 0)), errors)
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertEquals(setOf(0), state.uniformParams)
    }

    @Test
    fun stealthyContinue() {
        val program =
            """
            @compute @workgroup_size(16,1,1)
            fn main(@builtin(local_invocation_index) lid : u32) {
              var count: u32;
              var myLid: u32;
              myLid = lid;
              loop {
                // As we do not have maximal reconvergence, the non-uniform continue below means that this barrier
                // is reachable under non-uniform control flow.
                workgroupBarrier();
                if count {
                   break;
                }
                // Nonuniform from analysis's perspective; with full-fledged implementation would be count++
                count = count;
                if myLid {
                  continue;
                }
              }
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertEquals(setOf(Pair("main", 0)), errors)
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertEquals(setOf(0), state.uniformParams)
    }

    @Test
    fun stealthyContinueMaximalReconvergence() {
        val program =
            """
            @compute @workgroup_size(16,1,1)
            fn main(@builtin(local_invocation_index) lid : u32) {
              var count: u32;
              var myLid: u32;
              myLid = lid;
              loop {
                workgroupBarrier();
                if count {
                   break;
                }
                // Nonuniform from analysis's perspective; with full-fledged implementation would be count++
                count = count;
                if myLid {
                  continue;
                }
              }
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program, maximalReconvergence = true)
        assertTrue(errors.isEmpty())
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun simpleContinue() {
        val program =
            """
            @compute @workgroup_size(16,1,1)
            fn main(@builtin(local_invocation_index) lid : u32) {
              loop {
                if (lid) {
                  continue;
                }
              }
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertTrue(errors.isEmpty())
        assertFalse(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun simpleContinueMaximalReconvergence() {
        val program =
            """
            @compute @workgroup_size(16,1,1)
            fn main(@builtin(local_invocation_index) lid : u32) {
              loop {
                if (lid) {
                  continue;
                }
              }
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program, maximalReconvergence = true)
        assertTrue(errors.isEmpty())
        assertFalse(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertTrue(state.uniformParams.isEmpty())
    }

    @Test
    fun simpleExampleFromAlloy() {
        val program =
            """
            @compute @workgroup_size(16,1,1)
            fn main(@builtin(local_invocation_index) Parameter0: u32, ) {
                var Local0: u32;
                var Local1: u32;
                var Local2: u32;
                var Local3: u32;
                if (Parameter0 == 0) {
                    workgroupBarrier();
                } else {
                    Local3 = 0;
                }
            }
            """.trimIndent()
        val (state, errors) = runSingleFunctionAnalysisHelper(program)
        assertEquals(setOf(Pair("main", 0)), errors)
        assertTrue(state.callSiteMustBeUniform)
        assertTrue(state.returnedValueUniformity.isEmpty())
        assertEquals(setOf(0), state.uniformParams)
    }

    @Test
    fun simpleWithTwoFunctionsNonUniform() {
        val program =
            """
            fn Function0() {
                workgroupBarrier();
            }
            
            @compute @workgroup_size(16,1,1)
            fn Function1(@builtin(local_invocation_index) Parameter0: u32, ) {
                if (Parameter0 == 0) {
                    Function0();
                } else {
                    Function0();
                }
            }
            """.trimIndent()
        val analysisResult = runAnalysisHelper(program)
        assertEquals(
            setOf(Pair("Function1", 0)),
            analysisResult.uniformityErrors,
        )
        run {
            val function0Result = analysisResult.resultsPerFunction["Function0"]!!
            assertTrue(function0Result.callSiteMustBeUniform)
            assertTrue(function0Result.returnedValueUniformity.isEmpty())
            assertTrue(function0Result.uniformParams.isEmpty())
        }
        run {
            val function1Result = analysisResult.resultsPerFunction["Function1"]!!
            assertTrue(function1Result.callSiteMustBeUniform)
            assertTrue(function1Result.returnedValueUniformity.isEmpty())
            assertEquals(setOf(0), function1Result.uniformParams)
        }
    }

    @Test
    fun simpleWithTwoFunctionsUniform() {
        val program =
            """
            fn Function0() {
            }
            
            fn Function1(Parameter0: u32, ) {
                if (Parameter0 == 0) {
                    Function0();
                } else {
                    Function0();
                }
            }
            """.trimIndent()
        val analysisResult = runAnalysisHelper(program)
        assertTrue(analysisResult.uniformityErrors.isEmpty())
        run {
            val function0Result = analysisResult.resultsPerFunction["Function0"]!!
            assertFalse(function0Result.callSiteMustBeUniform)
            assertTrue(function0Result.returnedValueUniformity.isEmpty())
            assertTrue(function0Result.uniformParams.isEmpty())
        }
        run {
            val function1Result = analysisResult.resultsPerFunction["Function1"]!!
            assertFalse(function1Result.callSiteMustBeUniform)
            assertTrue(function1Result.returnedValueUniformity.isEmpty())
            assertTrue(function1Result.uniformParams.isEmpty())
        }
    }

    @Test
    fun functionCallExpressions() {
        val program =
            """
            fn f(p0: u32, p1: u32) -> u32 {
              if (p0 == 0) {
                workgroupBarrier();
              }
              return p1;
            }
            
            fn g(p0: u32, p1: u32) {
              var temp: u32;
              temp = f(p0, p1);
              temp = f(p1, p0);
            }
            """.trimIndent()
        val analysisResult = runAnalysisHelper(program)
        assertTrue(analysisResult.uniformityErrors.isEmpty())
        run {
            val fResult = analysisResult.resultsPerFunction["f"]!!
            assertTrue(fResult.callSiteMustBeUniform)
            assertEquals(setOf(1), fResult.returnedValueUniformity)
            assertEquals(setOf(0), fResult.uniformParams)
        }
        run {
            val gResult = analysisResult.resultsPerFunction["g"]!!
            assertTrue(gResult.callSiteMustBeUniform)
            assertTrue(gResult.returnedValueUniformity.isEmpty())
            assertEquals(setOf(0, 1), gResult.uniformParams)
        }
    }

    @Test
    fun functionCallExpressionsDeclarationsOutOfOrder() {
        val program =
            """
            fn g(p0: u32, p1: u32) {
              var temp: u32;
              temp = f(p0, p1);
              temp = f(p1, p0);
            }
            
            fn f(p0: u32, p1: u32) -> u32 {
              if (p0 == 0) {
                workgroupBarrier();
              }
              return p1;
            }
            """.trimIndent()
        val analysisResult = runAnalysisHelper(program)
        assertTrue(analysisResult.uniformityErrors.isEmpty())
        run {
            val fResult = analysisResult.resultsPerFunction["f"]!!
            assertTrue(fResult.callSiteMustBeUniform)
            assertEquals(setOf(1), fResult.returnedValueUniformity)
            assertEquals(setOf(0), fResult.uniformParams)
        }
        run {
            val gResult = analysisResult.resultsPerFunction["g"]!!
            assertTrue(gResult.callSiteMustBeUniform)
            assertTrue(gResult.returnedValueUniformity.isEmpty())
            assertEquals(setOf(0, 1), gResult.uniformParams)
        }
    }

    @Test
    fun continuingConstruct() {
        val program =
            """
            fn g(p0: u32, p1: u32) {
              var temp: u32;
              loop {
                  if (temp) {
                    workgroupBarrier();
                  }
                  if (temp > 100) {
                    break;
                  }
                  continuing {
                    if (p1) {
                      temp = 12;
                    } else {
                      temp = temp + 1;
                    }
                  }
              }
            }
            """.trimIndent()
        val analysisResult = runAnalysisHelper(program)
        assertTrue(analysisResult.uniformityErrors.isEmpty())
        run {
            val gResult = analysisResult.resultsPerFunction["g"]!!
            assertTrue(gResult.callSiteMustBeUniform)
            assertTrue(gResult.returnedValueUniformity.isEmpty())
            assertEquals(setOf(1), gResult.uniformParams)
        }
    }

    @Test
    fun breakIf() {
        val program =
            """
            @compute @workgroup_size(16,1,1)
            fn Function0(@builtin(local_invocation_index) Parameter0: u32, ) {
                workgroupBarrier();
                loop {
                    workgroupBarrier();
                    continuing {
                        break if (Parameter0 == 0);
                    }
                }
            }
            """.trimIndent()
        val analysisResult = runAnalysisHelper(program)
        assertEquals(
            setOf(Pair("Function0", 0)),
            analysisResult.uniformityErrors,
        )
        run {
            val gResult = analysisResult.resultsPerFunction["Function0"]!!
            assertTrue(gResult.callSiteMustBeUniform)
            assertTrue(gResult.returnedValueUniformity.isEmpty())
            assertEquals(setOf(0), gResult.uniformParams)
        }
    }

    @Test
    fun breakIf2() {
        val program =
            """
            @compute @workgroup_size(16,1,1)
            fn Function0(@builtin(local_invocation_index) Parameter0: u32, ) {
                workgroupBarrier();
                loop {
                    continuing {
                        break if (Parameter0 == 0);
                    }
                }
                workgroupBarrier();
            }
            """.trimIndent()
        val analysisResult = runAnalysisHelper(program)
        assertTrue(analysisResult.uniformityErrors.isEmpty())
        run {
            val result = analysisResult.resultsPerFunction["Function0"]!!
            assertTrue(result.callSiteMustBeUniform)
            assertTrue(result.returnedValueUniformity.isEmpty())
            assertTrue(result.uniformParams.isEmpty())
        }
    }

    @Test
    fun nestedLoopWithOuterBreak() {
        val program =
            """
            @compute @workgroup_size(16,1,1)
            fn Function0(@builtin(local_invocation_index) Parameter0: u32, ) {
                loop {
                    if (Parameter0 == 0) {
                         break;
                    }
                    loop {
                        workgroupBarrier();
                        break;
                    }
                }
            }
            """.trimIndent()
        val analysisResult = runAnalysisHelper(program)
        assertEquals(
            setOf(Pair("Function0", 0)),
            analysisResult.uniformityErrors,
        )
        run {
            val result = analysisResult.resultsPerFunction["Function0"]!!
            assertTrue(result.callSiteMustBeUniform)
            assertTrue(result.returnedValueUniformity.isEmpty())
            assertEquals(setOf(0), result.uniformParams)
        }
    }

    @Test
    fun nestedLoopWithOuterContinue() {
        val program =
            """
            @compute @workgroup_size(16,1,1)
            fn Function0(@builtin(local_invocation_index) Parameter0: u32, ) {
                loop {
                    if (Parameter0 == 0) {
                         continue;
                    }
                    loop {
                        workgroupBarrier();
                        break;
                    }
                }
            }
            """.trimIndent()
        val analysisResult = runAnalysisHelper(program)
        assertEquals(
            setOf(Pair("Function0", 0)),
            analysisResult.uniformityErrors,
        )
        run {
            val result = analysisResult.resultsPerFunction["Function0"]!!
            assertTrue(result.callSiteMustBeUniform)
            assertTrue(result.returnedValueUniformity.isEmpty())
            assertEquals(setOf(0), result.uniformParams)
        }
    }

    @Test
    fun nestedLoopWithContinuing() {
        val program =
            """
            @compute @workgroup_size(16,1,1)
            fn Function0(@builtin(local_invocation_index) Parameter0: u32, ) {
                loop {
                    continue;
                    continuing {
                        loop {
                            workgroupBarrier();
                            continuing {
                                break if (true);
                            }
                        }
                        break if (Parameter0 == 0);
                    }
                }
            }
            """.trimIndent()
        val analysisResult = runAnalysisHelper(program)
        assertEquals(
            setOf(Pair("Function0", 0)),
            analysisResult.uniformityErrors,
        )
        run {
            val result = analysisResult.resultsPerFunction["Function0"]!!
            assertTrue(result.callSiteMustBeUniform)
            assertTrue(result.returnedValueUniformity.isEmpty())
            assertEquals(setOf(0), result.uniformParams)
        }
    }

    @Test
    fun nestedLoopWithContinuing2() {
        val program =
            """
            @compute @workgroup_size(16,1,1)
            fn Function0(@builtin(local_invocation_index) Parameter0: u32, ) {
                loop {
                    workgroupBarrier();
                    continue;
                    continuing {
                        loop {
                            workgroupBarrier();
                            continuing {
                                break if (true);
                            }
                        }
                        break if (Parameter0 == 0);
                    }
                }
            }
            """.trimIndent()
        val analysisResult = runAnalysisHelper(program)
        assertEquals(
            setOf(Pair("Function0", 0)),
            analysisResult.uniformityErrors,
        )
        run {
            val result = analysisResult.resultsPerFunction["Function0"]!!
            assertTrue(result.callSiteMustBeUniform)
            assertTrue(result.returnedValueUniformity.isEmpty())
            assertEquals(setOf(0), result.uniformParams)
        }
    }

    @Test
    fun nestedLoopWithContinuing3() {
        val program =
            """
            @compute @workgroup_size(16,1,1)
            fn Function0(@builtin(local_invocation_index) Parameter0: u32, ) {
                loop {
                    continuing {
                        loop {
                            workgroupBarrier();
                            // Cannot break this loop, so no way to get to non-uniformity
                        }
                        break if (Parameter0 == 0);
                    }
                }
            }
            """.trimIndent()
        val analysisResult = runAnalysisHelper(program)
        assertTrue(analysisResult.uniformityErrors.isEmpty())
        run {
            val result = analysisResult.resultsPerFunction["Function0"]!!
            assertTrue(result.callSiteMustBeUniform)
            assertTrue(result.returnedValueUniformity.isEmpty())
            assertTrue(result.returnedValueUniformity.isEmpty())
        }
    }

    private fun runSingleFunctionAnalysisHelper(
        program: String,
        maximalReconvergence: Boolean = false,
    ): Pair<FunctionAnalysisState, Set<Pair<String, Int>>> {
        val tu = parseFromString(program, LoggingParseErrorListener())
        check(tu.globalDecls.size == 1) { "This helper is for single-function programs." }
        checkProgramIsFeatherweight(tu)
        val singleFunction = tu.globalDecls[0] as GlobalDecl.Function
        val analysisResult = runAnalysis(originalTu = tu, maximalReconvergence = maximalReconvergence)
        return Pair(analysisResult.resultsPerFunction[singleFunction.name]!!, analysisResult.uniformityErrors)
    }

    private fun runAnalysisHelper(program: String): UniformityAnalysisResult {
        val tu = parseFromString(program, LoggingParseErrorListener())
        checkProgramIsFeatherweight(tu)
        return runAnalysis(tu)
    }
}
