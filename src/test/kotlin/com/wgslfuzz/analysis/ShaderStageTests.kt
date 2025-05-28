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
