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
import kotlin.test.assertTrue

class TraversalTests {
    @Test
    fun testPrePostOrder() {
        val shader =
            """
            fn foo() -> i32
            {
              var x : i32 = 0i;
              for (
                var i : i32 = 0i;
                i < 100;
                i++
              )
              {
                x += i;
              }
              return x;
            }

            """.trimIndent()
        val tu = parseFromString(shader, LoggingParseErrorListener())
        val function = tu.globalDecls[0] as GlobalDecl.Function
        val returnType = function.returnType
        val functionBody = function.body
        val variableStatement = functionBody.statements[0] as Statement.Variable
        val forStatement = function.body.statements[1] as Statement.For
        val forInit = forStatement.init!! as Statement.Variable
        val forCond = forStatement.condition!! as Expression.Binary
        val forUpdate = forStatement.update!! as Statement.Increment
        val forBody = forStatement.body
        val forBodyStatement = forBody.statements[0] as Statement.Assignment
        val returnStatement = function.body.statements[2] as Statement.Return

        val expectedPreorder =
            listOf(
                tu,
                function,
                returnType,
                functionBody,
                variableStatement,
                variableStatement.type!!,
                variableStatement.initializer!!,
                forStatement,
                forInit,
                forInit.type!!,
                forInit.initializer!!,
                forCond,
                forCond.lhs,
                forCond.rhs,
                forUpdate,
                forUpdate.target,
                forBody,
                forBodyStatement,
                forBodyStatement.lhsExpression,
                forBodyStatement.rhs,
                returnStatement,
                returnStatement.expression,
            )
        assertEquals(expectedPreorder, nodesPreOrder(tu))

        val expectedPostOrder =
            listOf(
                returnType,
                variableStatement.type!!,
                variableStatement.initializer!!,
                variableStatement,
                forInit.type!!,
                forInit.initializer!!,
                forInit,
                forCond.lhs,
                forCond.rhs,
                forCond,
                forUpdate.target,
                forUpdate,
                forBodyStatement.lhsExpression,
                forBodyStatement.rhs,
                forBodyStatement,
                forBody,
                forStatement,
                returnStatement.expression,
                returnStatement,
                functionBody,
                function,
                tu,
            )
        assertEquals(expectedPostOrder, nodesPostOrder(tu))
    }

    @Test
    fun testAttributeTraversal() {
        val shader =
            """
            struct S { a: i32, }
            @group(0)
            @binding(0)
            var<uniform> v: S;
            """.trimIndent()
        val tu = parseFromString(shader, LoggingParseErrorListener())
        val nodes = nodesPreOrder(tu)
        assertEquals(10, nodes.size)
        assertTrue(nodes[0] is TranslationUnit)
        assertTrue(nodes[1] is GlobalDecl.Struct)
        assertTrue(nodes[2] is StructMember)
        assertTrue(nodes[3] is TypeDecl.I32)
        assertTrue(nodes[4] is GlobalDecl.Variable)
        assertTrue(nodes[5] is Attribute)
        assertTrue(nodes[6] is Expression.IntLiteral)
        assertTrue(nodes[7] is Attribute)
        assertTrue(nodes[8] is Expression.IntLiteral)
        assertTrue(nodes[9] is TypeDecl.NamedType)
    }
}
