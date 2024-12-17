package com.wgslfuzz

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

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
        expressions.forEach {
            environment.typeOf(it)
        }

        val functionDecl = tu.globalDecls[0] as GlobalDecl.Function
        val whileStmt = functionDecl.body.statements[1] as Statement.While
        val whileCondition = whileStmt.expression as Expression.Paren
        assertEquals(Type.Bool, environment.typeOf(whileCondition))
        val whileConditionInner = whileCondition.target as Expression.Binary
        assertEquals(Type.Bool, environment.typeOf(whileConditionInner))
        assertEquals(Type.I32, environment.typeOf(whileConditionInner.lhs))
        assertEquals(Type.AbstractInt, environment.typeOf(whileConditionInner.rhs))
    }
}
