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
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.parseFromString
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class StatementBehaviourTests {
    @Test
    fun behaviourOfIfReturnInAllBranches() {
        val input =
            """
            @compute
            fn main() {
                if (true) {
                    return;  
                } else {
                    return; 
                }
            }
            """.trimIndent()
        val tu = parseFromString(input, LoggingParseErrorListener())
        val behaviourMap = runStatementBehaviourAnalysis(tu)
        val expectedBehaviour = setOf(Behaviour.RETURN)

        behaviourMap.keys.forEach { key ->
            if (key is Statement.If) {
                assertEquals(expectedBehaviour, behaviourMap[key])
            }
        }
    }

    @Test
    fun behaviourOfIfReturnInOneBranches() {
        val input =
            """
            @compute
            fn main() {
                if (true) {
                    return;  
                } 
            }
            """.trimIndent()
        val tu = parseFromString(input, LoggingParseErrorListener())

        val behaviourMap = runStatementBehaviourAnalysis(tu)
        val expectedBehaviour = setOf(Behaviour.RETURN, Behaviour.NEXT)

        behaviourMap.keys.forEach { key ->
            if (key is Statement.If) {
                assertEquals(expectedBehaviour, behaviourMap[key])
            }
        }
    }

    @Test
    fun behaviourOfReturningLoop() {
        val input =
            """
            @compute
            fn main() {
                loop {
                    return;    
                }
            }
            """.trimIndent()
        val tu = parseFromString(input, LoggingParseErrorListener())

        val behaviourMap = runStatementBehaviourAnalysis(tu)
        val expectedBehaviour = setOf(Behaviour.RETURN)

        behaviourMap.keys.forEach { key ->
            if (key is Statement.Loop) {
                assertEquals(expectedBehaviour, behaviourMap[key])
            }
        }
    }

    @Test
    fun behaviourOfBreakingLoop() {
        val input =
            """
            @compute
            fn main() {
                loop {
                    break;    
                }
            }
            """.trimIndent()
        val tu = parseFromString(input, LoggingParseErrorListener())

        val behaviourMap = runStatementBehaviourAnalysis(tu)
        val expectedBehaviour = setOf(Behaviour.NEXT)

        behaviourMap.keys.forEach { key ->
            if (key is Statement.Loop) {
                assertEquals(expectedBehaviour, behaviourMap[key])
            }
        }
    }

    @Test
    fun behaviourOfBreakingAndReturningLoop() {
        val input =
            """
            @compute
            fn main() {
                loop {
                    if (true) {
                        break;    
                    } else {
                        return;
                    }
                }
            }
            """.trimIndent()
        val tu = parseFromString(input, LoggingParseErrorListener())

        val behaviourMap = runStatementBehaviourAnalysis(tu)
        val expectedBehaviour = setOf(Behaviour.NEXT, Behaviour.RETURN)

        behaviourMap.keys.forEach { key ->
            if (key is Statement.Loop) {
                assertEquals(expectedBehaviour, behaviourMap[key])
            }
        }
    }

    @Test
    fun behaviourOfBreakingLoopWithUnreachableReturn() {
        val input =
            """
            @compute
            fn main() {
                loop {
                    break;    
                    return;
                }
            }
            """.trimIndent()
        val tu = parseFromString(input, LoggingParseErrorListener())

        val behaviourMap = runStatementBehaviourAnalysis(tu)
        val expectedBehaviour = setOf(Behaviour.NEXT)

        behaviourMap.keys.forEach { key ->
            if (key is Statement.Loop) {
                assertEquals(expectedBehaviour, behaviourMap[key])
            }
        }
    }

    @Test
    fun behaviourOfReturningLoopWithUnreachableBreak() {
        val input =
            """
            @compute
            fn main() {
                loop {
                    return;
                    break;    
                }
            }
            """.trimIndent()
        val tu = parseFromString(input, LoggingParseErrorListener())

        val behaviourMap = runStatementBehaviourAnalysis(tu)
        val expectedBehaviour = setOf(Behaviour.RETURN)

        behaviourMap.keys.forEach { key ->
            if (key is Statement.Loop) {
                assertEquals(expectedBehaviour, behaviourMap[key])
            }
        }
    }

    @Test
    fun behaviourOfForLoop() {
        val input =
            """
            @compute
            fn main() {
                var a: i32 = 0;
                for (var i: i32 = 0; i < 10; i++) { 
                   a += 2; 
                }
            }
            """.trimIndent()
        val tu = parseFromString(input, LoggingParseErrorListener())

        val behaviourMap = runStatementBehaviourAnalysis(tu)
        val expectedBehaviour = setOf(Behaviour.NEXT)

        behaviourMap.keys.forEach { key ->
            if (key is Statement.For) {
                assertEquals(expectedBehaviour, behaviourMap[key])
            }
        }
    }

    @Test
    fun behaviourOfReturningForLoop() {
        val input =
            """
            @compute
            fn main() {
                var a: i32 = 0;
                for (var i: i32 = 0; i < 10; i++) { 
                    return;
                }
            }
            """.trimIndent()
        val tu = parseFromString(input, LoggingParseErrorListener())

        val behaviourMap = runStatementBehaviourAnalysis(tu)
        val expectedBehaviour = setOf(Behaviour.NEXT, Behaviour.RETURN)

        behaviourMap.keys.forEach { key ->
            if (key is Statement.For) {
                assertEquals(expectedBehaviour, behaviourMap[key])
            }
        }
    }


    @Test
    fun behaviourOfWhileLoop() {
        val input =
            """
            @compute
            fn main() {
                var a: i32 = 0;
                while (a < 10) {
                    a++;
                }
            }
            """.trimIndent()
        val tu = parseFromString(input, LoggingParseErrorListener())

        val behaviourMap = runStatementBehaviourAnalysis(tu)
        val expectedBehaviour = setOf(Behaviour.NEXT)

        behaviourMap.keys.forEach { key ->
            if (key is Statement.For) {
                assertEquals(expectedBehaviour, behaviourMap[key])
            }
        }
    }

    @Test
    fun behaviourOfReturningWhileLoop() {
        val input =
            """
            @compute
            fn main() {
                var a: i32 = 0;
                while (a < 10) {
                    a++;
                    return;
                }
            }
            """.trimIndent()
        val tu = parseFromString(input, LoggingParseErrorListener())

        val behaviourMap = runStatementBehaviourAnalysis(tu)
        val expectedBehaviour = setOf(Behaviour.NEXT, Behaviour.RETURN)

        behaviourMap.keys.forEach { key ->
            if (key is Statement.For) {
                assertEquals(expectedBehaviour, behaviourMap[key])
            }
        }
    }
}
