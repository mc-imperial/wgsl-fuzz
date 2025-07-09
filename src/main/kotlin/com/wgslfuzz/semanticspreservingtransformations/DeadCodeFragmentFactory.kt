package com.wgslfuzz.semanticspreservingtransformations

import com.wgslfuzz.core.AugmentedExpression
import com.wgslfuzz.core.AugmentedStatement
import com.wgslfuzz.core.ContinuingStatement
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.Statement

fun createIfFalseThenDeadStatement(
    falseCondition: AugmentedExpression.FalseByConstruction,
    deadStatement: Statement.Compound,
    includeEmptyElseBranch: Boolean,
): AugmentedStatement.DeadCodeFragment =
    AugmentedStatement.DeadCodeFragment(
        Statement.If(
            condition = falseCondition,
            thenBranch = deadStatement,
            elseBranch =
                if (includeEmptyElseBranch) {
                    Statement.Compound(emptyList())
                } else {
                    null
                },
        ),
    )

fun createIfTrueElseDeadStatement(
    trueCondition: AugmentedExpression.TrueByConstruction,
    deadStatement: Statement.Compound,
): AugmentedStatement.DeadCodeFragment =
    AugmentedStatement.DeadCodeFragment(
        Statement.If(
            condition = trueCondition,
            thenBranch = Statement.Compound(emptyList()),
            elseBranch = deadStatement,
        ),
    )

fun createWhileFalseDeadStatement(
    falseCondition: AugmentedExpression.FalseByConstruction,
    deadStatement: Statement.Compound,
): AugmentedStatement.DeadCodeFragment =
    AugmentedStatement.DeadCodeFragment(
        Statement.While(
            condition = falseCondition,
            body = deadStatement,
        ),
    )

fun createForWithFalseConditionDeadStatement(
    falseCondition: AugmentedExpression.FalseByConstruction,
    deadStatement: Statement.Compound,
    unreachableUpdate: Statement.ForUpdate?,
): AugmentedStatement.DeadCodeFragment =
    AugmentedStatement.DeadCodeFragment(
        Statement.For(
            condition = falseCondition,
            body = deadStatement,
            update = unreachableUpdate,
        ),
    )

fun createLoopWithUnconditionalBreakDeadStatement(
    trueCondition: AugmentedExpression.TrueByConstruction,
    deadStatement: Statement.Compound,
    includeContinuingStatement: Boolean,
    breakIfExpr: Expression?,
): AugmentedStatement.DeadCodeFragment =
    AugmentedStatement.DeadCodeFragment(
        Statement.Loop(
            body =
                Statement.Compound(
                    listOf(
                        Statement.If(
                            condition = trueCondition,
                            thenBranch = Statement.Compound(listOf(Statement.Break())),
                        ),
                    ) + deadStatement.statements,
                ),
            continuingStatement =
                if (includeContinuingStatement) {
                    ContinuingStatement(
                        statements = Statement.Compound(emptyList()),
                        breakIfExpr = breakIfExpr,
                    )
                } else {
                    check(breakIfExpr == null)
                    null
                },
        ),
    )
