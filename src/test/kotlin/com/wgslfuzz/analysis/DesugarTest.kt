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

import com.wgslfuzz.core.AstNode
import com.wgslfuzz.core.AstWriter
import com.wgslfuzz.core.LoggingParseErrorListener
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.parseFromString
import com.wgslfuzz.core.traverse
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals

class DesugarTest {
    @Test
    fun allIfStatementsHaveAnElse() {
        val input =
            """
            @compute
            fn main() {
                if (true) {}
                
                if (true) {} else {}
                
                if (true) {
                    if (true) {} 
                }
                
                loop {
                    if (true) {} 
                }
            }
            """.trimIndent()
        val desugaredTu = parseFromString(input, LoggingParseErrorListener()).desugar()

        fun checkNonEmptyElse(
            node: AstNode,
            state: Unit,
        ) {
            when (node) {
                is Statement.If -> {
                    assert(node.elseBranch != null)
                }
                else -> traverse(::checkNonEmptyElse, node, Unit)
            }
        }

        traverse(::checkNonEmptyElse, desugaredTu, Unit)
    }

    @Test
    fun allLoopsHaveAnEmptyContinue() {
        val input =
            """
            @compute
            fn main() {
                loop { x = 3; break; }
                
                if (true) {
                    loop { continuing {}}
                } else {}
                
                loop { loop { continuing {} }}
                
                loop {
                    if (true) {} 
                }
            }
            """.trimIndent()
        val desugaredTu = parseFromString(input, LoggingParseErrorListener()).desugar()

        fun checkNonEmptyContinuing(
            node: AstNode,
            state: Unit,
        ) {
            when (node) {
                is Statement.Loop -> {
                    assert(node.continuingStatement != null)
                }
                else -> traverse(::checkNonEmptyContinuing, node, Unit)
            }
        }

        traverse(::checkNonEmptyContinuing, desugaredTu, Unit)
    }

    @Test
    fun allForLoopsBecomeLoops() {
        val input =
            """
            @compute
            fn main() {
                for (var x = 0; x < 10; x++) {}

                var x = 0;
                for (; x < 10; x++) {}
                
                for (; ; x++) {}
                
                for (; x < 10; ) { x++; }
                for (; x < 10; ) { for ( ; x < 20; x++) {} }
            }
            """.trimIndent()
        val desugaredTu = parseFromString(input, LoggingParseErrorListener()).desugar()

        fun checkNoForLoops(
            node: AstNode,
            state: Unit,
        ) {
            when (node) {
                is Statement.For -> {
                    assert(false)
                }
                else -> traverse(::checkNoForLoops, node, Unit)
            }
        }

        traverse(::checkNoForLoops, desugaredTu, Unit)
    }

    @Test
    fun forLoopsWithInitHaveNewScope() {
        val input =
            """
            @compute
            fn main() {
                for (var x = 0; x < 10; x++) {
                    x += 2; 
                }
            }
            """.trimIndent()
        val expected =
            """
            @compute
            fn main()
            {
                {
                    var x = 0;
                    loop
                    {
                        if !(x < 10)
                        {
                            break;
                        }
                        else
                        {
                        }
                        x += 2;
                        continuing
                        {
                            x++;
                        }
                    }
                }
            }
            
            """.trimIndent()
        val desugaredTu = parseFromString(input, LoggingParseErrorListener()).desugar()
        val byteOutputStream = ByteArrayOutputStream()
        AstWriter(PrintStream(byteOutputStream), indentValue = 4).emit(desugaredTu)
        assertEquals(expected, byteOutputStream.toString())
    }

    @Test
    fun allWhileLoopsBecomeLoops() {
        val input =
            """
            @compute
            fn main() {
                var x = 0;
                while (x < 10) { x++; }

                if (true) {
                    while (x < 10) { x++; }
                }

                while (x < 10) { 
                    x++; 
                    while (x < 10) { x++; }
                }
            }
            """.trimIndent()
        val desugaredTu = parseFromString(input, LoggingParseErrorListener()).desugar()

        fun checkNoForLoops(
            node: AstNode,
            state: Unit,
        ) {
            when (node) {
                is Statement.For -> {
                    assert(false)
                }
                else -> traverse(::checkNoForLoops, node, Unit)
            }
        }

        traverse(::checkNoForLoops, desugaredTu, Unit)
    }
}
