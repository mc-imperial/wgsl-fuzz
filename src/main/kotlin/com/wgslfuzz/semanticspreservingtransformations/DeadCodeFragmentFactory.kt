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

package com.wgslfuzz.semanticspreservingtransformations

import com.wgslfuzz.core.AugmentedExpression
import com.wgslfuzz.core.AugmentedStatement
import com.wgslfuzz.core.ContinuingStatement
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.Statement

// A collection of factory functions for making DeadCodeFragment statements. These help to ensure that dead code
// fragments are only constructed in meaningful ways.
//
// An alternative design choice would be to equip DeadCodeFragment with preconditions ensuring that only certain forms
// of statement are created. The problem with that design, however, is that it would make further transformation of
// DeadCodeFragment statements problematic, as transformations that _would_ preserve semantics might not preserve the
// exact structure required by these preconditions, and the transformation process relies on being able to smoothly
// clone the AST with transformations in tow.

/**
 * Makes a statement of the form:
 *
 * if (false-by-construction) {
 *     dead-statement
 * }
 *
 * with an optional empty else branch.
 */
fun createIfFalseThenDeadStatement(
    falseCondition: AugmentedExpression.KnownValue,
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

/**
 * Makes a statement of the form:
 *
 * if (true-by-construction) {
 *
 * } else {
 *     dead-statement
 * }
 */
fun createIfTrueElseDeadStatement(
    trueCondition: AugmentedExpression.KnownValue,
    deadStatement: Statement.Compound,
): AugmentedStatement.DeadCodeFragment =
    AugmentedStatement.DeadCodeFragment(
        Statement.If(
            condition = trueCondition,
            thenBranch = Statement.Compound(emptyList()),
            elseBranch = deadStatement,
        ),
    )

/**
 * Makes a statement of the form:
 *
 * while (false-by-construction) {
 *     dead-statement
 * }
 */
fun createWhileFalseDeadStatement(
    falseCondition: AugmentedExpression.KnownValue,
    deadStatement: Statement.Compound,
): AugmentedStatement.DeadCodeFragment =
    AugmentedStatement.DeadCodeFragment(
        Statement.While(
            condition = falseCondition,
            body = deadStatement,
        ),
    )

/**
 * Makes a statement of the form:
 *
 * for ( ; false-by-construction; ) {
 *     dead-statement
 * }
 */
fun createForWithFalseConditionDeadStatement(
    falseCondition: AugmentedExpression.KnownValue,
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

/**
 * Makes a statement of the form:
 *
 * loop {
 *     if (true-by-construction) {
 *         break;
 *     }
 *     dead-statement;
 *     // Optional:
 *     continuing {
 *         // Optional:
 *         break-if (given-expression)
 *     }
 * }
 */
fun createLoopWithUnconditionalBreakDeadStatement(
    trueCondition: AugmentedExpression.KnownValue,
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
