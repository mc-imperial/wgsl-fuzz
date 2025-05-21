package com.wgslfuzz.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class CloneAstTests {
    @Test
    fun replaceIdentifierWithBinary() {
        val original =
            """
            fn foo(
              x : i32,
              y : i32,
            ) -> i32
            {
              return x;
            }

            """.trimIndent()
        val expected =
            """
            fn foo(
              x : i32,
              y : i32,
            ) -> i32
            {
              return x + y;
            }

            """.trimIndent()
        val tu = parseFromString(original, LoggingParseErrorListener())
        val identifierExpression =
            ((tu.globalDecls[0] as GlobalDecl.Function).body.statements[0] as Statement.Return).expression
                as Expression.Identifier
        val replacement =
            mapOf(
                identifierExpression to Expression.Binary(BinaryOperator.PLUS, Expression.Identifier("x"), Expression.Identifier("y")),
            )
        val tuCloned = tu.clone({ replacement[it] })
        val outputStream = ByteArrayOutputStream()
        AstWriter(
            out = PrintStream(outputStream),
            indentValue = 2,
        ).emit(tuCloned)
        assertEquals(expected, outputStream.toString())
    }

    @Test
    fun transplantLoop() {
        val original =
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
        val expected =
            """
            fn foo() -> i32
            {
              var x : i32 = 0i;
              {
                var i : i32 = 0i;
                loop
                {
                  if !(i < 100)
                  {
                    break;
                  }
                  x += i;
                  continuing
                  {
                    i++;
                  }
                }
              }
              return x;
            }

            """.trimIndent()
        val tu = parseFromString(original, LoggingParseErrorListener())
        val forLoop = (tu.globalDecls[0] as GlobalDecl.Function).body.statements[1] as Statement.For
        val replacementForLoop =
            Statement.Compound(
                listOf(
                    forLoop.init!!.clone(),
                    Statement.Loop(
                        body =
                            Statement.Compound(
                                listOf(
                                    Statement.If(
                                        condition =
                                            Expression.Unary(
                                                operator = UnaryOperator.LOGICAL_NOT,
                                                target = Expression.Paren(forLoop.condition!!),
                                            ),
                                        thenBranch = Statement.Compound(listOf(Statement.Break())),
                                    ),
                                    forLoop.body.statements[0].clone(),
                                ),
                            ),
                        continuingStatement =
                            ContinuingStatement(
                                statements =
                                    Statement.Compound(
                                        listOf(
                                            forLoop.update!!.clone(),
                                        ),
                                    ),
                            ),
                    ),
                ),
            )
        val replacement = mapOf(forLoop to replacementForLoop)
        val tuCloned = tu.clone({ replacement[it] })
        val outputStream = ByteArrayOutputStream()
        AstWriter(
            out = PrintStream(outputStream),
            indentValue = 2,
        ).emit(tuCloned)
        assertEquals(expected, outputStream.toString())
    }
}
