package com.wgslfuzz.core

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ParsedShaderJobTests {
    private val shaderJobText =
        """
        {
            "uniformBuffers": [
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
            ],
            "shaderText": "struct S {\n   a: i32,       // 1\n   b: vec2<i32>, // 2, 3\n   c: i32,       // 4\n   d: vec3<i32>, // 5, 6, 7\n   e: vec4<i32>, // 8, 9, 10, 11\n}\n\n@group(0)\n@binding(0)\nvar<uniform> u: S;\n\n@vertex\nfn vertexMain(@location(0) pos: vec2f) -> @builtin(position) vec4f {\n  return vec4f(pos, 0, 1);\n}\n\n@fragment\nfn fragmentMain() -> @location(0) vec4f {\n    if (u.a == 1 && u.b.x == 2 && u.b.y == 3 && u.c == 4 && u.d.x == 5 && u.d.y == 6 && u.d.z == 7 && u.e.x == 8 && u.e.y == 9 && u.e.z == 10 && u.e.w == 11) {\n       return vec4(1.0f, 0.0f, 0.0f, 1.0f);\n    }\n    return vec4(0.0f, 1.0f, 0.0f, 1.0f);\n}\n"
        }
        """.trimIndent()

    @Test
    fun testSimpleUniformValues() {
        val mapper = jacksonObjectMapper()
        val shaderJob: ShaderJob = mapper.readValue<ShaderJob>(shaderJobText)
        val parsedShaderJob = parseShaderJob(shaderJob)
        val structValue = parsedShaderJob.uniformValues[0]!![0] as Expression.StructValueConstructor
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
