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
        assertEquals(
            Type.Reference(storeType = Type.I32, addressSpace = AddressSpace.FUNCTION, accessMode = AccessMode.READ_WRITE),
            environment.typeOf(whileConditionInner.lhs),
        )
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
            assertEquals(Type.Reference(Type.I32, AddressSpace.FUNCTION, AccessMode.READ_WRITE), outerEntryV.type)
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
            assertEquals(Type.Reference(Type.I32, AddressSpace.FUNCTION, AccessMode.READ_WRITE), inner1EntryV.type)
            val inner1EntryY = scopeAtEndOfThenBranch.getEntry("y") as ScopeEntry.Parameter
            assertSame(yParam, inner1EntryY.astNode)
            assertEquals(Type.I32, inner1EntryY.type)
            val inner1EntryC = scopeAtEndOfThenBranch.getEntry("c") as ScopeEntry.LocalVariable
            assertSame(b1, inner1EntryC.astNode)
            assertEquals(Type.Reference(Type.F32, AddressSpace.FUNCTION, AccessMode.READ_WRITE), inner1EntryC.type)
        }

        run {
            val scopeAtEndOfElseIfBranch = environment.scopeAvailableAtEnd(a3Body)
            val inner2EntryX = scopeAtEndOfElseIfBranch.getEntry("x") as ScopeEntry.Parameter
            assertSame(xParam, inner2EntryX.astNode)
            assertEquals(Type.F32, inner2EntryX.type)
            val inner2EntryV = scopeAtEndOfElseIfBranch.getEntry("v") as ScopeEntry.LocalVariable
            assertSame(c1, inner2EntryV.astNode)
            assertEquals(Type.Reference(Type.F32, AddressSpace.FUNCTION, AccessMode.READ_WRITE), inner2EntryV.type)
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
            assertEquals(Type.Reference(Type.I32, AddressSpace.FUNCTION, AccessMode.READ_WRITE), inner3EntryV.type)
            val inner3EntryY = scopeAtEndOfElseBranch.getEntry("y") as ScopeEntry.Parameter
            assertSame(yParam, inner3EntryY.astNode)
            assertEquals(Type.I32, inner3EntryY.type)
            val inner3EntryC = scopeAtEndOfElseBranch.getEntry("c") as ScopeEntry.LocalVariable
            assertSame(d1, inner3EntryC.astNode)
            assertEquals(Type.Reference(Type.Bool, AddressSpace.FUNCTION, AccessMode.READ_WRITE), inner3EntryC.type)
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

    @Test
    fun testArrayRefDeref() {
        val input =
            """
            fn f() {
                var arr1 = array<i32, 3>(0, 1, 2);
                let arr1_pointer = &(arr1);
                var arr1_elem = arr1[0];
                var arr1_pointer_elem = arr1_pointer[1];
                var arr2 = *(&(arr1));
                let arr2_elem = &((&arr1)[0]);
                
                var mat = mat2x3<f32>(vec3<f32>(0,1,2), vec3<f32>(3,4,5));
                let mat_pointer = &mat;
                var col = mat[0];
                var col_from_pointer = mat_pointer[0];
                let col_pointer = &(mat[0]);
                var col_elem = col_from_pointer[0];
            }
            """.trimIndent()
        val errorListener = LoggingParseErrorListener()
        val tu = parseFromString(input, errorListener)
        val environment = resolve(tu)

        val fn = tu.globalDecls[0] as GlobalDecl.Function

        var statementIndex = 0

        fun nextStatement(): Statement = fn.body.statements[statementIndex++]

        run {
            val arr1 = nextStatement() as Statement.Variable
            val arr1Pointer = nextStatement() as Statement.Value
            val arr1Elem = nextStatement() as Statement.Variable
            val arr1PointerElem = nextStatement() as Statement.Variable
            val arr2 = nextStatement() as Statement.Variable
            val arr2Elem = nextStatement() as Statement.Value

            val scopeAtEndOfFunctionBody = environment.scopeAvailableAtEnd(fn.body)

            val entryArr1 = scopeAtEndOfFunctionBody.getEntry("arr1") as ScopeEntry.LocalVariable
            assertSame(arr1, entryArr1.astNode)
            assertEquals(
                Type.Reference(Type.Array(Type.I32, 3), AddressSpace.FUNCTION, AccessMode.READ_WRITE),
                entryArr1.type,
            )

            val entryArr1Pointer = scopeAtEndOfFunctionBody.getEntry("arr1_pointer") as ScopeEntry.LocalValue
            assertSame(arr1Pointer, entryArr1Pointer.astNode)
            assertEquals(
                Type.Pointer(Type.Array(Type.I32, 3), AddressSpace.FUNCTION, AccessMode.READ_WRITE),
                entryArr1Pointer.type,
            )

            val entryArr1Elem = scopeAtEndOfFunctionBody.getEntry("arr1_elem") as ScopeEntry.LocalVariable
            assertSame(arr1Elem, entryArr1Elem.astNode)
            assertEquals(Type.Reference(Type.I32, AddressSpace.FUNCTION, AccessMode.READ_WRITE), entryArr1Elem.type)

            val entryArr1PointerElem =
                scopeAtEndOfFunctionBody.getEntry("arr1_pointer_elem") as ScopeEntry.LocalVariable
            assertSame(arr1PointerElem, entryArr1PointerElem.astNode)
            assertEquals(
                Type.Reference(Type.I32, AddressSpace.FUNCTION, AccessMode.READ_WRITE),
                entryArr1PointerElem.type,
            )

            val entryArr2 = scopeAtEndOfFunctionBody.getEntry("arr2") as ScopeEntry.LocalVariable
            assertSame(arr2, entryArr2.astNode)
            assertEquals(
                Type.Reference(Type.Array(Type.I32, 3), AddressSpace.FUNCTION, AccessMode.READ_WRITE),
                entryArr2.type,
            )

            val entryArr2Elem = scopeAtEndOfFunctionBody.getEntry("arr2_elem") as ScopeEntry.LocalValue
            assertSame(arr2Elem, entryArr2Elem.astNode)
            assertEquals(Type.Pointer(Type.I32, AddressSpace.FUNCTION, AccessMode.READ_WRITE), entryArr2Elem.type)
        }

        run {
            val mat = nextStatement() as Statement.Variable
            val matPointer = nextStatement() as Statement.Value
            val col = nextStatement() as Statement.Variable
            val colFromPointer = nextStatement() as Statement.Variable
            val colPointer = nextStatement() as Statement.Value
            val colElem = nextStatement() as Statement.Variable

            val scopeAtEndOfFunctionBody = environment.scopeAvailableAtEnd(fn.body)

            val entryMat = scopeAtEndOfFunctionBody.getEntry("mat") as ScopeEntry.LocalVariable
            assertSame(mat, entryMat.astNode)
            assertEquals(
                Type.Reference(Type.Matrix(2, 3, Type.F32), AddressSpace.FUNCTION, AccessMode.READ_WRITE),
                entryMat.type,
            )

            val entryMatPointer = scopeAtEndOfFunctionBody.getEntry("mat_pointer") as ScopeEntry.LocalValue
            assertSame(matPointer, entryMatPointer.astNode)
            assertEquals(
                Type.Pointer(Type.Matrix(2, 3, Type.F32), AddressSpace.FUNCTION, AccessMode.READ_WRITE),
                entryMatPointer.type,
            )

            val entryCol = scopeAtEndOfFunctionBody.getEntry("col") as ScopeEntry.LocalVariable
            assertSame(col, entryCol.astNode)
            assertEquals(
                Type.Reference(Type.Vector(3, Type.F32), AddressSpace.FUNCTION, AccessMode.READ_WRITE),
                entryCol.type,
            )

            val entryColFromPointer = scopeAtEndOfFunctionBody.getEntry("col_from_pointer") as ScopeEntry.LocalVariable
            assertSame(colFromPointer, entryColFromPointer.astNode)
            assertEquals(
                Type.Reference(Type.Vector(3, Type.F32), AddressSpace.FUNCTION, AccessMode.READ_WRITE),
                entryColFromPointer.type,
            )

            val entryColPointer = scopeAtEndOfFunctionBody.getEntry("col_pointer") as ScopeEntry.LocalValue
            assertSame(colPointer, entryColPointer.astNode)
            assertEquals(
                Type.Pointer(Type.Vector(3, Type.F32), AddressSpace.FUNCTION, AccessMode.READ_WRITE),
                entryColPointer.type,
            )

            val entryColElem = scopeAtEndOfFunctionBody.getEntry("col_elem") as ScopeEntry.LocalVariable
            assertSame(colElem, entryColElem.astNode)
            assertEquals(
                Type.Reference(Type.F32, AddressSpace.FUNCTION, AccessMode.READ_WRITE),
                entryColElem.type,
            )
        }
    }

    @Test
    fun testMemberLookupRefDeref() {
        val input =
            """
            struct S {
                elem: i32,
            }
            
            fn f() {
                var s: S;
                var e = s.elem;
                let s_pointer = &(*(&s));
                var e_from_pointer = s_pointer.elem;
                
                var vec = vec3<i32>(0,1,2);
                var vec_x = vec.x;
                var vec_y_from_pointer = (&vec).y;
            }
            """.trimIndent()
        val errorListener = LoggingParseErrorListener()
        val tu = parseFromString(input, errorListener)
        val environment = resolve(tu)

        val fn = tu.globalDecls[1] as GlobalDecl.Function
        val scopeAtEndOfFunctionBody = environment.scopeAvailableAtEnd(fn.body)

        var statementIndex = 0

        fun nextStatement(): Statement = fn.body.statements[statementIndex++]

        run {
            val s = nextStatement() as Statement.Variable
            val e = nextStatement() as Statement.Variable
            val sPointer = nextStatement() as Statement.Value
            val eFromPointer = nextStatement() as Statement.Variable

            val entryS = scopeAtEndOfFunctionBody.getEntry("s") as ScopeEntry.LocalVariable
            assertSame(s, entryS.astNode)
            assertEquals(
                Type.Reference(Type.Struct("S", listOf(Pair("elem", Type.I32))), AddressSpace.FUNCTION, AccessMode.READ_WRITE),
                entryS.type,
            )

            val entryE = scopeAtEndOfFunctionBody.getEntry("e") as ScopeEntry.LocalVariable
            assertSame(e, entryE.astNode)
            assertEquals(
                Type.Reference(Type.I32, AddressSpace.FUNCTION, AccessMode.READ_WRITE),
                entryE.type,
            )

            val entrySPointer = scopeAtEndOfFunctionBody.getEntry("s_pointer") as ScopeEntry.LocalValue
            assertSame(sPointer, entrySPointer.astNode)
            assertEquals(
                Type.Pointer(Type.Struct("S", listOf(Pair("elem", Type.I32))), AddressSpace.FUNCTION, AccessMode.READ_WRITE),
                entrySPointer.type,
            )

            val entryEFromPointer = scopeAtEndOfFunctionBody.getEntry("e_from_pointer") as ScopeEntry.LocalVariable
            assertSame(eFromPointer, entryEFromPointer.astNode)
            assertEquals(
                Type.Reference(Type.I32, AddressSpace.FUNCTION, AccessMode.READ_WRITE),
                entryEFromPointer.type,
            )
        }

        run {
            val vec = nextStatement() as Statement.Variable
            val vecX = nextStatement() as Statement.Variable
            val vecXFromPointer = nextStatement() as Statement.Variable

            val entryVec = scopeAtEndOfFunctionBody.getEntry("vec") as ScopeEntry.LocalVariable
            assertSame(vec, entryVec.astNode)
            assertEquals(
                Type.Reference(Type.Vector(3, Type.I32), AddressSpace.FUNCTION, AccessMode.READ_WRITE),
                entryVec.type,
            )

            val entryVecX = scopeAtEndOfFunctionBody.getEntry("vec_x") as ScopeEntry.LocalVariable
            assertSame(vecX, entryVecX.astNode)
            assertEquals(
                Type.Reference(Type.I32, AddressSpace.FUNCTION, AccessMode.READ_WRITE),
                entryVecX.type,
            )

            val entryVecXFromPointer = scopeAtEndOfFunctionBody.getEntry("vec_y_from_pointer") as ScopeEntry.LocalVariable
            assertSame(vecXFromPointer, entryVecXFromPointer.astNode)
            assertEquals(
                Type.Reference(Type.I32, AddressSpace.FUNCTION, AccessMode.READ_WRITE),
                entryVecXFromPointer.type,
            )
        }
    }
}
