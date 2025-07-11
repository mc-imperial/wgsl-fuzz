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

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class ResolverTests {
    private fun gatherExpressions(
        node: AstNode,
        expressions: MutableSet<Expression>,
    ) {
        traverse(::gatherExpressions, node, expressions)
        when (node) {
            is Expression -> {
                expressions.add(node)
            }
            else -> { }
        }
    }

    @Test
    fun miscTest() {
        val input =
            """
            fn f() -> i32
            {
              var i : i32;
              while (i < 4)
              {
                workgroupBarrier();
                i = i + 1;
              }
              return i;
            }

            """.trimIndent()
        val errorListener = LoggingParseErrorListener()
        val tu = parseFromString(input, errorListener)
        val environment = resolve(tu)

        val expressions = mutableSetOf<Expression>()
        gatherExpressions(tu, expressions)

        // Confirm that a type was found for every expression.
        expressions.forEach(environment::typeOf)

        val functionDecl = tu.globalDecls[0] as GlobalDecl.Function
        val whileStmt = functionDecl.body.statements[1] as Statement.While
        val whileCondition = whileStmt.condition as Expression.Paren
        assertEquals(Type.Bool, environment.typeOf(whileCondition))
        val whileConditionInner = whileCondition.target as Expression.Binary
        assertEquals(Type.Bool, environment.typeOf(whileConditionInner))
        assertEquals(Type.I32, environment.typeOf(whileConditionInner.lhs))
        assertEquals(Type.AbstractInteger, environment.typeOf(whileConditionInner.rhs))
    }

    @Test
    fun pointerTest() {
        val input =
            """
            @group(0, )
            @binding(0, )
            var<storage, read_write> s : i32;

            var<workgroup> g1 : atomic<i32, >;

            struct S {
              a : i32,
              b : i32,
            }

            fn accept_ptr_deref_pass_through(
              val : ptr<function, i32>,
            ) -> i32
            {
              return (*(val) + accept_ptr_deref_call_func(val, ));
            }

            fn accept_ptr_to_struct_and_access(
              val : ptr<function, S>,
            ) -> i32
            {
              return ((*(val)).a + (*(val)).b);
            }

            fn accept_ptr_to_struct_access_pass_ptr(
              val : ptr<function, S>,
            ) -> i32
            {
              let b = &((*(val)).a);
              *(b) = 2;
              return *(b);
            }

            fn accept_ptr_deref_call_func(
              val : ptr<function, i32>,
            ) -> i32
            {
              return (*(val) + accept_value(*(val), ));
            }

            fn accept_value(
              val : i32,
            ) -> i32
            {
              return val;
            }

            fn accept_ptr_vec_access_elements(
              v1 : ptr<function, vec3f>,
            ) -> i32
            {
              (*(v1)).x = cross(*(v1), *(v1), ).x;
              return i32((*(v1)).x, );
            }

            fn call_builtin_with_mod_scope_ptr() -> i32
            {
              return atomicLoad(&(g1), );
            }

            @compute
            @workgroup_size(1)
            fn main()
            {
              var v1 = 0;
              var v2 = S();
              let v3 = &(v2);
              var v4 = vec3f();
              let t1 = atomicLoad(&(g1), );
              s = ((((((accept_ptr_deref_pass_through(&(v1), ) + accept_ptr_to_struct_and_access(&(v2), )) + accept_ptr_to_struct_and_access(v3, )) + accept_ptr_vec_access_elements(&(v4), )) + accept_ptr_to_struct_access_pass_ptr(&(v2), )) + call_builtin_with_mod_scope_ptr()) + t1);
            }
            
            """.trimIndent()
        val errorListener = LoggingParseErrorListener()
        val tu = parseFromString(input, errorListener)
        val environment = resolve(tu)

        val expressions = mutableSetOf<Expression>()
        gatherExpressions(tu, expressions)

        // Confirm that a type was found for every expression.
        expressions.forEach(environment::typeOf)
    }

    @Test
    fun ifScopeTest() {
        val input =
            """
            const x = 5;
            var v: i32 = 42i;
            fn f(x: f32, y: i32) {
              let c = 5; // a1
              if (c > y) { // a2
                var c: f32 = 12f; // b1
                c += 1.0; // b2
              } else if (c == y) { // a3
                var v: f32 = 13f; // c1
                v += 1.0; // c2
              } else { // a4
                var c: bool = false; // d1
                c = !c; // d2
              }
              v++; // a5
            }
            """.trimIndent()
        val errorListener = LoggingParseErrorListener()
        val tu = parseFromString(input, errorListener)
        val environment = resolve(tu)

        val vGlobal = tu.globalDecls[1] as GlobalDecl.Variable
        val fn = tu.globalDecls[2] as GlobalDecl.Function
        val xParam = fn.parameters[0]
        val yParam = fn.parameters[1]
        val a1 = fn.body.statements[0] as Statement.Value
        val a2 = fn.body.statements[1] as Statement.If
        val a2Body = a2.thenBranch
        val b1 = a2Body.statements[0]
        val a3 = a2.elseBranch as Statement.If
        val a3Body = a3.thenBranch
        val c1 = a3Body.statements[0]
        val a4 = a3.elseBranch as Statement.Compound
        val d1 = a4.statements[0]

        run {
            val scopeAtEndOfFunctionBody = environment.scopeAvailableAtEnd(fn.body)
            val outerEntryX = scopeAtEndOfFunctionBody.getEntry("x") as ScopeEntry.Parameter
            assertSame(xParam, outerEntryX.astNode)
            assertEquals(Type.F32, outerEntryX.type)
            val outerEntryV = scopeAtEndOfFunctionBody.getEntry("v") as ScopeEntry.GlobalVariable
            assertSame(vGlobal, outerEntryV.astNode)
            assertEquals(Type.I32, outerEntryV.type)
            val outerEntryY = scopeAtEndOfFunctionBody.getEntry("y") as ScopeEntry.Parameter
            assertSame(yParam, outerEntryY.astNode)
            assertEquals(Type.I32, outerEntryY.type)
            val outerEntryC = scopeAtEndOfFunctionBody.getEntry("c") as ScopeEntry.LocalValue
            assertSame(a1, outerEntryC.astNode)
            assertEquals(Type.I32, outerEntryC.type)
        }

        run {
            val scopeAtEndOfThenBranch = environment.scopeAvailableAtEnd(a2Body)
            val inner1EntryX = scopeAtEndOfThenBranch.getEntry("x") as ScopeEntry.Parameter
            assertSame(xParam, inner1EntryX.astNode)
            assertEquals(Type.F32, inner1EntryX.type)
            val inner1EntryV = scopeAtEndOfThenBranch.getEntry("v") as ScopeEntry.GlobalVariable
            assertSame(vGlobal, inner1EntryV.astNode)
            assertEquals(Type.I32, inner1EntryV.type)
            val inner1EntryY = scopeAtEndOfThenBranch.getEntry("y") as ScopeEntry.Parameter
            assertSame(yParam, inner1EntryY.astNode)
            assertEquals(Type.I32, inner1EntryY.type)
            val inner1EntryC = scopeAtEndOfThenBranch.getEntry("c") as ScopeEntry.LocalVariable
            assertSame(b1, inner1EntryC.astNode)
            assertEquals(Type.F32, inner1EntryC.type)
        }

        run {
            val scopeAtEndOfElseIfBranch = environment.scopeAvailableAtEnd(a3Body)
            val inner2EntryX = scopeAtEndOfElseIfBranch.getEntry("x") as ScopeEntry.Parameter
            assertSame(xParam, inner2EntryX.astNode)
            assertEquals(Type.F32, inner2EntryX.type)
            val inner2EntryV = scopeAtEndOfElseIfBranch.getEntry("v") as ScopeEntry.LocalVariable
            assertSame(c1, inner2EntryV.astNode)
            assertEquals(Type.F32, inner2EntryV.type)
            val inner2EntryY = scopeAtEndOfElseIfBranch.getEntry("y") as ScopeEntry.Parameter
            assertSame(yParam, inner2EntryY.astNode)
            assertEquals(Type.I32, inner2EntryY.type)
            val inner2EntryC = scopeAtEndOfElseIfBranch.getEntry("c") as ScopeEntry.LocalValue
            assertSame(a1, inner2EntryC.astNode)
            assertEquals(Type.I32, inner2EntryC.type)
        }

        run {
            val scopeAtEndOfElseBranch = environment.scopeAvailableAtEnd(a4)
            val inner3EntryX = scopeAtEndOfElseBranch.getEntry("x") as ScopeEntry.Parameter
            assertSame(xParam, inner3EntryX.astNode)
            assertEquals(Type.F32, inner3EntryX.type)
            val inner3EntryV = scopeAtEndOfElseBranch.getEntry("v") as ScopeEntry.GlobalVariable
            assertSame(vGlobal, inner3EntryV.astNode)
            assertEquals(Type.I32, inner3EntryV.type)
            val inner3EntryY = scopeAtEndOfElseBranch.getEntry("y") as ScopeEntry.Parameter
            assertSame(yParam, inner3EntryY.astNode)
            assertEquals(Type.I32, inner3EntryY.type)
            val inner3EntryC = scopeAtEndOfElseBranch.getEntry("c") as ScopeEntry.LocalVariable
            assertSame(d1, inner3EntryC.astNode)
            assertEquals(Type.Bool, inner3EntryC.type)
        }
    }

    @Test
    fun duplicateScopeEntryFunctionBody() {
        val input =
            """
            fn f(i: i32) {
               var i: i32 = 42;
            }
            """.trimIndent()
        val errorListener = LoggingParseErrorListener()
        val tu = parseFromString(input, errorListener)
        try {
            resolve(tu)
        } catch (exception: IllegalArgumentException) {
            assertEquals("An entry for i already exists in the current scope.", exception.message)
        }
    }

    @Test
    fun noDuplicateScopeEntryForLoop() {
        val input =
            """
            fn f() {
               for (var i = 0; i < 10; i++) {
                  var i: i32 = 42;
               }
            }
            """.trimIndent()
        val errorListener = LoggingParseErrorListener()
        val tu = parseFromString(input, errorListener)
        val environment = resolve(tu)
        val forLoop = (tu.globalDecls[0] as GlobalDecl.Function).body.statements[0] as Statement.For
        val forLoopInit = forLoop.init!!
        val forLoopUpdate = forLoop.update!!
        val forLoopFirstStatement = forLoop.body.statements[0]

        val scopeBeforeForLoop = environment.scopeAvailableBefore(forLoop)
        assertNull(scopeBeforeForLoop.getEntry("i"))

        val scopeBeforeForLoopInit = environment.scopeAvailableBefore(forLoopInit)
        assertNull(scopeBeforeForLoopInit.getEntry("i"))

        val scopeBeforeForLoopUpdate = environment.scopeAvailableBefore(forLoopUpdate)
        val loopVariable = scopeBeforeForLoopUpdate.getEntry("i")
        assertNotNull(loopVariable)

        val scopeBeforeForLoopFirstStatement = environment.scopeAvailableBefore(forLoopFirstStatement)
        val loopVariableAgain = scopeBeforeForLoopFirstStatement.getEntry("i")
        assertSame(loopVariable, loopVariableAgain)

        val scopeAtEndOfForLoopBody = environment.scopeAvailableAtEnd(forLoop.body)
        val differentVariable = scopeAtEndOfForLoopBody.getEntry("i")
        assertNotSame(differentVariable, loopVariable)
    }

    @Test
    fun testScopeOfContinuing() {
        val input =
            """
            fn foo() {
              var i = 0;
              loop {
                var x = 42;
                continuing {
                  var x = x + 4;
                  i += x;
                  let j = i;
                  break if j > 10;
                }
              }
            }
            """.trimIndent()
        val errorListener = LoggingParseErrorListener()
        val tu = parseFromString(input, errorListener)
        val environment = resolve(tu)

        val functionBody = (tu.globalDecls[0] as GlobalDecl.Function).body

        val declBeforeLoopStatement = functionBody.statements[0]
        val scopeBeforeDeclBeforeLoopStatement = environment.scopeAvailableBefore(declBeforeLoopStatement)

        val loopStatement = functionBody.statements[1] as Statement.Loop
        val scopeBeforeLoopStatement = environment.scopeAvailableBefore(loopStatement)

        val loopBody = loopStatement.body
        val scopeBeforeLoopBody = environment.scopeAvailableBefore(loopBody)

        val firstStatementInLoop = loopBody.statements[0]
        val scopeBeforeFirstStatementInLoop = environment.scopeAvailableBefore(firstStatementInLoop)

        val continuingStatementCompound = loopStatement.continuingStatement!!.statements
        val scopeBeforeContinuingStatementCompound = environment.scopeAvailableBefore(continuingStatementCompound)

        val continuingStatementFirstInnerStatement = continuingStatementCompound.statements[0]
        val scopeBeforeContinuingStatementFirstInnerStatement = environment.scopeAvailableBefore(continuingStatementFirstInnerStatement)

        val continuingStatementSecondInnerStatement = continuingStatementCompound.statements[1]
        val scopeBeforeContinuingStatementSecondInnerStatement = environment.scopeAvailableBefore(continuingStatementSecondInnerStatement)

        val continuingStatementThirdInnerStatement = continuingStatementCompound.statements[2]
        val scopeBeforeContinuingStatementThirdInnerStatement = environment.scopeAvailableBefore(continuingStatementThirdInnerStatement)

        val scopeAtEndOfContinuing = environment.scopeAvailableAtEnd(continuingStatementCompound)
        val scopeAtEndOfLoopBody = environment.scopeAvailableAtEnd(loopBody)
        val scopeAtEndOfFunctionBody = environment.scopeAvailableAtEnd(functionBody)

        assertNull(scopeBeforeDeclBeforeLoopStatement.getEntry("i"))
        assertNull(scopeBeforeDeclBeforeLoopStatement.getEntry("j"))
        assertNull(scopeBeforeDeclBeforeLoopStatement.getEntry("x"))

        val declOfI = scopeBeforeLoopStatement.getEntry("i")
        assertNotNull(declOfI)
        assertNull(scopeBeforeLoopStatement.getEntry("j"))
        assertNull(scopeBeforeLoopStatement.getEntry("x"))

        assertSame(declOfI, scopeBeforeLoopBody.getEntry("i"))
        assertNull(scopeBeforeLoopBody.getEntry("j"))
        assertNull(scopeBeforeLoopBody.getEntry("x"))

        assertSame(declOfI, scopeBeforeFirstStatementInLoop.getEntry("i"))
        assertNull(scopeBeforeFirstStatementInLoop.getEntry("j"))
        assertNull(scopeBeforeFirstStatementInLoop.getEntry("x"))

        assertSame(declOfI, scopeBeforeContinuingStatementCompound.getEntry("i"))
        assertNull(scopeBeforeContinuingStatementCompound.getEntry("j"))
        val outerDeclOfX = scopeBeforeContinuingStatementCompound.getEntry("x")
        assertNotNull(outerDeclOfX)

        assertSame(declOfI, scopeBeforeContinuingStatementFirstInnerStatement.getEntry("i"))
        assertNull(scopeBeforeContinuingStatementFirstInnerStatement.getEntry("j"))
        assertSame(outerDeclOfX, scopeBeforeContinuingStatementFirstInnerStatement.getEntry("x"))

        assertSame(declOfI, scopeBeforeContinuingStatementSecondInnerStatement.getEntry("i"))
        val innerDeclOfX = scopeBeforeContinuingStatementSecondInnerStatement.getEntry("x")
        assertNotNull(innerDeclOfX)
        assertNotSame(outerDeclOfX, innerDeclOfX)
        assertNull(scopeBeforeContinuingStatementSecondInnerStatement.getEntry("j"))

        assertSame(declOfI, scopeBeforeContinuingStatementThirdInnerStatement.getEntry("i"))
        assertSame(innerDeclOfX, scopeBeforeContinuingStatementThirdInnerStatement.getEntry("x"))
        assertNull(scopeBeforeContinuingStatementThirdInnerStatement.getEntry("j"))

        assertSame(declOfI, scopeAtEndOfContinuing.getEntry("i"))
        assertSame(innerDeclOfX, scopeAtEndOfContinuing.getEntry("x"))
        assertNotNull(scopeAtEndOfContinuing.getEntry("j"))

        assertSame(declOfI, scopeAtEndOfLoopBody.getEntry("i"))
        assertSame(outerDeclOfX, scopeAtEndOfLoopBody.getEntry("x"))
        assertNull(scopeAtEndOfLoopBody.getEntry("j"))

        assertSame(declOfI, scopeAtEndOfFunctionBody.getEntry("i"))
        assertNull(scopeAtEndOfFunctionBody.getEntry("x"))
        assertNull(scopeAtEndOfFunctionBody.getEntry("j"))
    }

    @Test
    fun testWorkgroupSizeExpressionsAreTyped() {
        val input =
            """
            @compute
            @workgroup_size(1, )
            fn main()
            {
            }
            """.trimIndent()
        val errorListener = LoggingParseErrorListener()
        val tu = parseFromString(input, errorListener)
        val environment = resolve(tu)

        val expressions = mutableSetOf<Expression>()
        gatherExpressions(tu, expressions)
        assertEquals(1, expressions.size)
        val workgroupSize = expressions.toList()[0] as Expression.IntLiteral
        assertEquals(Type.AbstractInteger, environment.typeOf(workgroupSize))
    }
}
