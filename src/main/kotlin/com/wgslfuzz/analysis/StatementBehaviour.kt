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

package com.wgslfuzz.analysis

import com.wgslfuzz.core.AstNode
import com.wgslfuzz.core.AugmentedStatement
import com.wgslfuzz.core.ContinuingStatement
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.GlobalDecl
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.TranslationUnit
import com.wgslfuzz.core.UnaryOperator
import com.wgslfuzz.core.traverse

enum class Behaviour {
    NEXT,
    BREAK,
    CONTINUE,
    RETURN,
}

fun runStatementBehaviourAnalysis(tu: TranslationUnit): Map<Statement, Set<Behaviour>> {
    fun traversalAction(
        node: AstNode,
        behaviourMap: Pair<MutableMap<Statement, Set<Behaviour>>, MutableMap<String, Set<Behaviour>>>,
    ) {
        when (node) {
            is GlobalDecl.Function -> functionBehaviour(node, behaviourMap.first, behaviourMap.second)
            else -> null
        }
    }

    val result = mutableMapOf<Statement, Set<Behaviour>>()
    traverse(::traversalAction, tu, Pair(result, mutableMapOf()))
    return result
}

// The behaviour of a function is the behaviour of the function's body with any 'Returns' replaced by 'Next'
// https://www.w3.org/TR/WGSL/#behaviors-rules
private fun functionBehaviour(
    function: GlobalDecl.Function,
    behaviourMap: MutableMap<Statement, Set<Behaviour>>,
    functionMap: MutableMap<String, Set<Behaviour>>,
): Set<Behaviour> {
    val bodyBehaviour = statementBehaviour(function.body, behaviourMap, functionMap)
    if (function.returnType != null && bodyBehaviour != setOf(Behaviour.RETURN)) {
        throw IllegalArgumentException("A function with a return type must return in all branches")
    }

    if (bodyBehaviour.contains(Behaviour.RETURN)) {
        return bodyBehaviour.minus(Behaviour.RETURN).plus(Behaviour.NEXT)
    }

    return bodyBehaviour
}

private fun statementsBehaviour(
    statements: List<Statement>,
    behaviourMap: MutableMap<Statement, Set<Behaviour>>,
    functionMap: MutableMap<String, Set<Behaviour>>,
): Set<Behaviour> {
    var result: Set<Behaviour> = setOf(Behaviour.NEXT)
    for (statement in statements) {
        val headBehaviour = statementBehaviour(statement, behaviourMap, functionMap)
        // While the current statement is reachable, continue to update the behaviour of the statement list.
        // The statement is still analysed if it is unreachable to add its behaviour to the behaviour map
        if (result.contains(Behaviour.NEXT)) {
            result = result.minus(Behaviour.NEXT).plus(headBehaviour)
        }
    }

    return result
}

private fun desugarFor(statement: Statement.For): Statement {
    val loopBreak =
        statement.condition?.let {
            Statement.If(
                condition = Expression.Unary(UnaryOperator.LOGICAL_NOT, statement.condition),
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

private fun statementBehaviour(
    statement: Statement,
    behaviourMap: MutableMap<Statement, Set<Behaviour>>,
    functionMap: MutableMap<String, Set<Behaviour>>,
): Set<Behaviour> {
    val behaviour: Set<Behaviour> =
        when (statement) {
            is Statement.Empty -> setOf(Behaviour.NEXT)
            is Statement.Value -> setOf(Behaviour.NEXT)
            is Statement.Variable -> setOf(Behaviour.NEXT)
            is Statement.Assignment -> setOf(Behaviour.NEXT)
            is Statement.Discard -> setOf(Behaviour.NEXT)
            is Statement.ConstAssert -> setOf(Behaviour.NEXT)
            is Statement.Decrement -> setOf(Behaviour.NEXT)
            is Statement.Increment -> setOf(Behaviour.NEXT)

            is Statement.Break -> setOf(Behaviour.BREAK)
            is Statement.Continue -> setOf(Behaviour.CONTINUE)
            is Statement.Return -> setOf(Behaviour.RETURN)

            is Statement.Compound -> statementsBehaviour(statement.statements, behaviourMap, functionMap)

            is Statement.If -> {
                val ifBehaviour = statementBehaviour(statement.thenBranch, behaviourMap, functionMap)
                val elseBehaviour =
                    statement.elseBranch?.let { statementBehaviour(it, behaviourMap, functionMap) } ?: setOf(Behaviour.NEXT)

                ifBehaviour union elseBehaviour
            }

            is Statement.For -> statementBehaviour(desugarFor(statement), behaviourMap, functionMap)
            is Statement.While -> statementBehaviour(desugarWhile(statement), behaviourMap, functionMap)

            is Statement.Loop -> {
                // TODO(JLJ): Currently implements the spec rule, not the bug fix I proposed
                val bodyBehaviour = statementBehaviour(statement.body, behaviourMap, functionMap)
                val loopBehaviour =
                    statement.continuingStatement
                        ?.let {
                            val continuingBehaviour = statementBehaviour(it.statements, behaviourMap, functionMap)
                            if (continuingBehaviour.contains(Behaviour.CONTINUE) || continuingBehaviour.contains(Behaviour.RETURN)) {
                                throw IllegalArgumentException(
                                    "continue and return statements cannot appear inside a continuing construct unless inside and a loop.",
                                )
                            }
                            continuingBehaviour
                        } // If the loop has a continuing, calculate its behaviour
                        ?.let { bodyBehaviour union it }
                        ?: bodyBehaviour // Combine the body and continuing body behaviours or fall back to body

                if (loopBehaviour.contains(Behaviour.BREAK)) {
                    loopBehaviour.plus(Behaviour.NEXT).subtract(setOf(Behaviour.BREAK, Behaviour.CONTINUE))
                } else {
                    loopBehaviour.subtract(setOf(Behaviour.NEXT, Behaviour.CONTINUE))
                }
            }

            is Statement.Switch -> {
                // TODO: This can be made more efficient, if the set has four behaviour we can terminate early
                val clauseBehaviour =
                    statement.clauses
                        .map { clause ->
                            statementBehaviour(
                                clause.compoundStatement,
                                behaviourMap,
                                functionMap,
                            )
                        }.reduce { a, b -> a union b }
                if (clauseBehaviour.contains(Behaviour.BREAK)) {
                    clauseBehaviour.plus(Behaviour.NEXT).minus(Behaviour.BREAK)
                } else {
                    clauseBehaviour
                }
            }

            is Statement.FunctionCall ->
                // Functions have been reordered so that a callee will be analysed before callers. This means
                // the behaviour of the callee is known when called.
                functionMap[statement.callee]!!

            // NON-STANDARD: the code here is statically unreachable so has behaviour next.
            is AugmentedStatement.DeadCodeFragment -> setOf(Behaviour.NEXT)
        }

    behaviourMap.put(statement, behaviour)
    return behaviour
}
