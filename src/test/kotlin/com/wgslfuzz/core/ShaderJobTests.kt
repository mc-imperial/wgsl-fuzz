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

package com.wgslfuzz.core

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ShaderJobTests {
    private val shaderText =
        """
        struct S {
           a: i32,       // 1
           b: vec2<i32>, // 2, 3
           c: i32,       // 4
           d: vec3<i32>, // 5, 6, 7
           e: vec4<i32>, // 8, 9, 10, 11
        }
        
        @group(0)
        @binding(0)
        var<uniform> u: S;
        
        @vertex
        fn vertexMain(@location(0) pos: vec2f) -> @builtin(position) vec4f {
          return vec4f(pos, 0, 1);
        }
        
        @fragment
        fn fragmentMain() -> @location(0) vec4f {
            if (u.a == 1 && u.b.x == 2 && u.b.y == 3 && u.c == 4 && u.d.x == 5 && u.d.y == 6 && u.d.z == 7 && u.e.x == 8 && u.e.y == 9 && u.e.z == 10 && u.e.w == 11) {
               return vec4(1.0f, 0.0f, 0.0f, 1.0f);
            }
            return vec4(0.0f, 1.0f, 0.0f, 1.0f);
        }
        """.trimIndent()

    private val uniformBuffers =
        """
        [
            {
                "binding": 0,
                "group": 0,
                "data": [
                    1, 0, 0, 0,
                    0, 0, 0, 0,
                    2, 0, 0, 0,
                    3, 0, 0, 0,
                    4, 0, 0, 0,
                    0, 0, 0, 0,
                    0, 0, 0, 0,
                    0, 0, 0, 0,
                    5, 0, 0, 0,
                    6, 0, 0, 0,
                    7, 0, 0, 0,
                    0, 0, 0, 0,
                    8, 0, 0, 0,
                    9, 0, 0, 0,
                    10, 0, 0, 0,
                    11, 0, 0, 0
                ]
            }
        ]
        """.trimIndent()

    @Test
    fun testSimpleUniformValues() {
        val shaderJob =
            createShaderJob(
                shaderText,
                Json.decodeFromString(uniformBuffers),
            )
        val structValue = shaderJob.pipelineState.getUniformValue(0, 0) as Expression.StructValueConstructor
        val aExpr = structValue.args[0] as Expression.IntLiteral
        assertEquals("1", aExpr.text)
        val bExpr = structValue.args[1] as Expression.Vec2ValueConstructor
        assertEquals("2", (bExpr.args[0] as Expression.IntLiteral).text)
        assertEquals("3", (bExpr.args[1] as Expression.IntLiteral).text)
        val cExpr = structValue.args[2] as Expression.IntLiteral
        assertEquals("4", cExpr.text)
        val dExpr = structValue.args[3] as Expression.Vec3ValueConstructor
        assertEquals("5", (dExpr.args[0] as Expression.IntLiteral).text)
        assertEquals("6", (dExpr.args[1] as Expression.IntLiteral).text)
        assertEquals("7", (dExpr.args[2] as Expression.IntLiteral).text)
        val eExpr = structValue.args[4] as Expression.Vec4ValueConstructor
        assertEquals("8", (eExpr.args[0] as Expression.IntLiteral).text)
        assertEquals("9", (eExpr.args[1] as Expression.IntLiteral).text)
        assertEquals("10", (eExpr.args[2] as Expression.IntLiteral).text)
        assertEquals("11", (eExpr.args[3] as Expression.IntLiteral).text)
    }
}
