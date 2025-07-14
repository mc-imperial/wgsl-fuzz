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

import com.wgslfuzz.analysis.desugar
import com.wgslfuzz.core.LoggingParseErrorListener
import com.wgslfuzz.core.parseFromString
import com.wgslfuzz.core.resolve
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.lang.IllegalArgumentException
import kotlin.test.assertContains

class UniformityWGSLAnalysisTests {
    fun checkNonUniform(shader: String) {
        val tu = parseFromString(shader, LoggingParseErrorListener()).desugar()
        val environment = resolve(tu)

        val message =
            assertThrows(IllegalArgumentException::class.java) {
                runWGSLUniformityGraphAnalysis(tu, environment)
            }
        println(message.message.toString())
        assertContains("Uniformity Error", message.message.toString())
    }

    fun checkUniform(shader: String) {
        val tu = parseFromString(shader, LoggingParseErrorListener()).desugar()
        val environment = resolve(tu)
        assertDoesNotThrow {runWGSLUniformityGraphAnalysis(tu, environment)}
    }

    @Test
    fun basicUniformityViolation() {
        val input =
            """
            @compute @workgroup_size(16)
            fn main(@builtin(local_invocation_index) lid : u32) {
                if (lid > 0) {
                    workgroupBarrier();
                }
            }
            """.trimIndent()
        checkNonUniform(input)
    }

    @Test
    fun nonUniformVariable() {
        val input =
            """
            @compute @workgroup_size(16)
            fn main(@builtin(local_invocation_index) lid : u32) {
                var x = 0;
                if (lid > 0) {
                    x = 1;
                }
                if (x > 0) {
                    workgroupBarrier();
                }
            }
            """.trimIndent()
        checkNonUniform(input)
    }
    @Test

    fun nonUniformVariable2() {
        val input =
            """
            @compute @workgroup_size(16,1,1)
            fn main(@builtin(local_invocation_index) lid: u32) {
              var x = 0;
              if (lid >= 0) { x = 1; }
              else { x = 1; }
              if (x >= 1) { workgroupBarrier(); }
            }
            """.trimIndent()
        checkNonUniform(input)
    }

    @Test
    fun nonUniformNestedIf() {
        val input =
            """
            @compute @workgroup_size(16,1,1)
            fn main(@builtin(local_invocation_index) lid: u32) {
              if (lid >= 0) {
                if (true) {
                  workgroupBarrier();
                }
              }
            }
            """.trimIndent()
        checkNonUniform(input)
    }

    @Test
    fun nonUniformVariableCond() {
        val input =
            """
            @compute @workgroup_size(16,1,1)
            fn main(@builtin(local_invocation_index) lid: u32) {
              let b = lid >= 0;
              if (b) {
                workgroupBarrier();
              }
            }
            """.trimIndent()
        checkNonUniform(input)
    }

    @Test
    fun nonUniformVariableCond2() {
        val input =
            """
            @compute @workgroup_size(16,1,1)
            fn main(@builtin(local_invocation_index) lid: u32) {
              let x = 0 + lid;
              if (x >= 0) {
                workgroupBarrier();
              }
            }
            """.trimIndent()
        checkNonUniform(input)
    }


    @Test
    fun nonUniformVariableCond3() {
        val input =
            """
            @compute @workgroup_size(16,1,1)
            fn main(@builtin(local_invocation_index) lid: u32) {
              var x: u32 = 0;

              if (x >= 0) { x = lid; }

              if (x >= 0) { workgroupBarrier(); }
            }
            """.trimIndent()
        checkNonUniform(input)
    }

    @Test
    fun differentBarriers() {
        val input =
            """
            @compute @workgroup_size(16)
            fn main(@builtin(local_invocation_index) lid : u32) {
                if (lid > 0) {
                    workgroupBarrier();
                } else {
                    workgroupBarrier();
                }
            }
            """.trimIndent()
        checkNonUniform(input)
    }

    @Test
    fun nonUniformLoop1() {
        val input =
            """
            @compute @workgroup_size(16,1,1)
            fn main(@builtin(local_invocation_index) lid: u32) {
              var x = 0;
              if (lid >= 0) {
                loop {
                  x = 1;
                  if (true) { break; }
                }
              }
              if (x == 1) {
                workgroupBarrier();
              }
            }
            """.trimIndent()
        checkNonUniform(input)
    }


    @Test
    fun nonUniformLoop2() {
        val input =
            """
            @compute @workgroup_size(16,1,1)
            fn main(@builtin(local_invocation_index) lid: u32) {
              var x = 0;
              if (lid >= 0) {
                loop {
                  x = 1;
                  if (true) { break; }
                }
              }
              if (x == 1) {
                workgroupBarrier();
              }
            }
            """.trimIndent()
        checkNonUniform(input)
    }

    @Test
    fun nonUniformLoop3() {
        val input =
            """
            @compute @workgroup_size(16,1,1)
            fn main(@builtin(local_invocation_index) lid: u32) {
              var x = 0;
              loop {
                if (lid >= 0) { continue; }
                x = 1;
                if (true) { break; }
              }
              if (x == 1) {
                workgroupBarrier();
              }
            }
            """.trimIndent()
        checkNonUniform(input)
    }

    @Test
    fun nonUniformLoop4() {
        val input =
            """
            @compute @workgroup_size(16,1,1)
            fn main(@builtin(local_invocation_index) lid: u32) {
              var x1 = 0;
              var x2 = 0;
              var x3 = 0;
              var x4 = 0;
              var x5 = 0;
              var result: u32 = 0;
              loop {
                if (x5 == 1) { result = lid; break; }
                if (x4 == 1) { x5 = 1; }
                if (x3 == 1) { x4 = 1; }
                if (x2 == 1) { x3 = 1; }
                if (x1 == 0) { x2 = 1; }
              }
              if (result == 1) {
                workgroupBarrier();
              }
            }
            """.trimIndent()
        checkNonUniform(input)
    }
}
