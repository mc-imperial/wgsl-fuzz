package com.wgslfuzz.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

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
        val variableStatement = function.body.statements[0] as Statement.Variable
        val forStatement = function.body.statements[1] as Statement.For
        val forInit = forStatement.init!! as Statement.Variable
        val forCond = forStatement.condition!! as Expression.Binary
        val forUpdate = forStatement.update!! as Statement.Increment
        val forBodyStatement = forStatement.body.statements[0] as Statement.Assignment
        val returnStatement = function.body.statements[2] as Statement.Return

        val expectedPreorder =
            listOf(
                tu,
                function,
                returnType,
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
                forStatement,
                returnStatement.expression,
                returnStatement,
                function,
                tu,
            )
        assertEquals(expectedPostOrder, nodesPostOrder(tu))
    }
}
