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

package com.wgslfuzz.analysis

import com.wgslfuzz.core.LoggingParseErrorListener
import com.wgslfuzz.core.parseFromString
import com.wgslfuzz.core.resolve
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShaderStageTests {
    @Test
    fun testShaderStageAnalysis() {
        val input =
            """
            @compute
            fn computeMain() {
              j();
            }
            
            fn g(p: bool) {
               if (p) {
                  f();
               }
            }
            
            fn f() { }
            
            fn h() {
               f();
               g(true);
            }
            
            @fragment
            fn fragmentMain() {
               h();
            }
            
            fn j() {
            }
            
            fn i() -> i32 {
              j();
              return 12;
            }
            
            @vertex
            fn vertexMain() {
               let a: i32 = i();
            }
            """.trimIndent()
        val tu = parseFromString(input, LoggingParseErrorListener())
        val environment = resolve(tu)
        val shaderStageAnalysisResult = runFunctionToShaderStageAnalysis(tu, environment)
        assertEquals(setOf("f", "g", "h", "i", "j", "fragmentMain", "vertexMain", "computeMain"), shaderStageAnalysisResult.keys)
        assertEquals(setOf(ShaderStage.FRAGMENT), shaderStageAnalysisResult["f"])
        assertEquals(setOf(ShaderStage.FRAGMENT), shaderStageAnalysisResult["g"])
        assertEquals(setOf(ShaderStage.FRAGMENT), shaderStageAnalysisResult["h"])
        assertEquals(setOf(ShaderStage.VERTEX), shaderStageAnalysisResult["i"])
        assertEquals(setOf(ShaderStage.COMPUTE, ShaderStage.VERTEX), shaderStageAnalysisResult["j"])
        assertEquals(setOf(ShaderStage.FRAGMENT), shaderStageAnalysisResult["fragmentMain"])
        assertEquals(setOf(ShaderStage.VERTEX), shaderStageAnalysisResult["vertexMain"])
        assertEquals(setOf(ShaderStage.COMPUTE), shaderStageAnalysisResult["computeMain"])
    }
}
