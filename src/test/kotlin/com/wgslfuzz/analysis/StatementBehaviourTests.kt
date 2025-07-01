package com.wgslfuzz.analysis

import com.wgslfuzz.core.AstWriter
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.LoggingParseErrorListener
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.clone
import com.wgslfuzz.core.parseFromString
import com.wgslfuzz.core.resolve
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
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