package com.wgslfuzz.analysis

import com.wgslfuzz.core.AstNode
import com.wgslfuzz.core.ContinuingStatement
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.UnaryOperator
import com.wgslfuzz.core.clone

// Desugar a `for` loop to a loop as described by: https://www.w3.org/TR/WGSL/#for-statement
private fun desugarFor(statement: Statement.For): Statement {
    val loopBreak =
        statement.condition?.let {
            Statement.If(
                condition = Expression.Unary(UnaryOperator.LOGICAL_NOT, Expression.Paren(statement.condition)),
                thenBranch =
                    Statement.Compound(
                        listOf<Statement>(
                            Statement.Break(),
                        ),
                    ),
            )
        }
    val continuing = statement.update?.let { ContinuingStatement(statements = Statement.Compound(listOf(it))) }
    val loopBody = if (loopBreak != null) listOf(loopBreak) + statement.body.statements else statement.body.statements

    val loop = Statement.Loop(body = Statement.Compound(loopBody), continuingStatement = continuing)

    val result = if (statement.init == null) loop else Statement.Compound(listOf(statement.init, loop))
    return result
}

// Desugar a `while` loop to a loop as described by: https://www.w3.org/TR/WGSL/#while-statement
private fun desugarWhile(whileStatement: Statement.While): Statement.Loop {
    val body =
        Statement.Compound(
            listOf(
                Statement.If(
                    condition = Expression.Unary(UnaryOperator.LOGICAL_NOT, whileStatement.condition),
                    thenBranch =
                        Statement.Compound(
                            listOf<Statement>(
                                Statement.Break(),
                            ),
                        ),
                ),
            ) + whileStatement.body.statements,
        )
    return Statement.Loop(body = body)
}

fun <T : AstNode> T.desugar(): T =
    this.clone { node ->
        when (node) {
            is Statement.While -> desugarWhile(node).desugar()
            is Statement.For -> desugarFor(node).desugar()

            // If a `loop` does not have a continuing construct, give it an empty continuing construct.
            // Otherwise, do nothing.
            is Statement.Loop ->
                if (node.continuingStatement == null) {
                    Statement.Loop(
                        node.attributesAtStart,
                        node.attributesBeforeBody,
                        node.body,
                        ContinuingStatement(statements = Statement.Compound(listOf())),
                    ).desugar()
                } else {
                    null
                }

            // If an `if` statement does not have an else branch, give it an empty else.
            // Otherwise, do nothing.
            is Statement.If ->
                if (node.elseBranch == null) {
                    Statement.If(
                        node.attributes,
                        node.condition,
                        node.thenBranch,
                        Statement.Compound(listOf()),
                    ).desugar()
                } else {
                    null
                }

            else -> null
        }
    }
