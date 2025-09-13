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
import com.wgslfuzz.core.GlobalDecl
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.TranslationUnit
import com.wgslfuzz.core.isStatementFunctionCallBuiltin
import com.wgslfuzz.core.traverse

enum class StatementBehaviour {
    NEXT,
    BREAK,
    CONTINUE,
    RETURN,
}

// A statement behaviour map maps a statement to the set of possible behaviours of that statement.
// The set of possible behaviour of each statement is a non-empty subset of { Next, Return, Break, Continue }
// For more information, see: https://www.w3.org/TR/WGSL/#behaviors
typealias StatementBehaviourMap = MutableMap<Statement, Set<StatementBehaviour>>

// In statement behaviour analysis, the behaviour of a function is equivalent to the behaviour of its body.
// The function behaviour map allows the behaviour of a function call statement to be determined from the name
// of the callee. For more information, see:
typealias FunctionBehaviourMap = MutableMap<String, Set<StatementBehaviour>>

fun runStatementBehaviourAnalysis(tu: TranslationUnit): Map<Statement, Set<StatementBehaviour>> {
    fun traversalAction(
        node: AstNode,
        behaviourMap: Pair<StatementBehaviourMap, FunctionBehaviourMap>,
    ) {
        when (node) {
            is GlobalDecl.Function -> functionBehaviour(node, behaviourMap.first, behaviourMap.second)
            else -> null
        }
    }

    val result: StatementBehaviourMap = mutableMapOf()
    traverse(::traversalAction, tu, Pair(result, mutableMapOf()))
    return result
}

// The behaviour of a function is the behaviour of the function's body with any 'Returns' replaced by 'Next'
// https://www.w3.org/TR/WGSL/#behaviors-rules
private fun functionBehaviour(
    function: GlobalDecl.Function,
    behaviourMap: StatementBehaviourMap,
    functionMap: FunctionBehaviourMap,
): Set<StatementBehaviour> {
    val bodyBehaviour = statementBehaviour(function.body, behaviourMap, functionMap)
    if (function.returnType != null && bodyBehaviour != setOf(StatementBehaviour.RETURN)) {
        throw IllegalArgumentException("A function with a return type must return in all branches")
    }

    if (bodyBehaviour.contains(StatementBehaviour.RETURN)) {
        return bodyBehaviour.minus(StatementBehaviour.RETURN).plus(StatementBehaviour.NEXT)
    }

    return bodyBehaviour
}

private fun statementsBehaviour(
    statements: List<Statement>,
    behaviourMap: StatementBehaviourMap,
    functionMap: FunctionBehaviourMap,
): Set<StatementBehaviour> {
    var result: Set<StatementBehaviour> = setOf(StatementBehaviour.NEXT)
    for (statement in statements) {
        val headBehaviour = statementBehaviour(statement, behaviourMap, functionMap)
        // While the current statement is reachable, continue to update the behaviour of the statement list.
        // The statement is still analysed if it is unreachable to add its behaviour to the behaviour map
        if (result.contains(StatementBehaviour.NEXT)) {
            result = result.minus(StatementBehaviour.NEXT).plus(headBehaviour)
        }
    }

    return result
}

private fun statementBehaviour(
    statement: Statement,
    behaviourMap: StatementBehaviourMap,
    functionMap: FunctionBehaviourMap,
): Set<StatementBehaviour> {
    val behaviour: Set<StatementBehaviour> =
        when (statement) {
            is Statement.Empty,
            is Statement.Value,
            is Statement.Variable,
            is Statement.Assignment,
            is Statement.Discard,
            is Statement.ConstAssert,
            is Statement.Decrement,
            is Statement.Increment,
            -> setOf(StatementBehaviour.NEXT)

            is Statement.Break -> setOf(StatementBehaviour.BREAK)
            is Statement.Continue -> setOf(StatementBehaviour.CONTINUE)
            is Statement.Return -> setOf(StatementBehaviour.RETURN)

            is Statement.Compound -> statementsBehaviour(statement.statements, behaviourMap, functionMap)

            is Statement.If -> {
                val ifBehaviour = statementBehaviour(statement.thenBranch, behaviourMap, functionMap)
                val elseBehaviour =
                    statement.elseBranch?.let { statementBehaviour(it, behaviourMap, functionMap) } ?: setOf(StatementBehaviour.NEXT)

                ifBehaviour union elseBehaviour
            }

            is Statement.For ->
                throw IllegalArgumentException("Statement behaviour analysis must be performed on the desugared AST.")
            is Statement.While ->
                throw IllegalArgumentException("Statement behaviour analysis must be performed on the desugared AST.")

            is Statement.Loop -> {
                // TODO(JLJ): Currently implements the spec rule, not the bug fix I proposed:
                // https://github.com/gpuweb/gpuweb/issues/5100
                val bodyBehaviour = statementBehaviour(statement.body, behaviourMap, functionMap)
                val loopBehaviour =
                    statement.continuingStatement
                        ?.let {
                            val continuingBehaviour = statementBehaviour(it.statements, behaviourMap, functionMap)
                            if (continuingBehaviour.contains(StatementBehaviour.CONTINUE) ||
                                continuingBehaviour.contains(StatementBehaviour.RETURN)
                            ) {
                                throw IllegalArgumentException(
                                    "continue and return statements cannot appear inside a continuing construct unless inside and a loop.",
                                )
                            }
                            continuingBehaviour
                        } // If the loop has a continuing, calculate its behaviour
                        ?.let { bodyBehaviour union it }
                        ?: bodyBehaviour // Combine the body and continuing body behaviours or fall back to body

                if (loopBehaviour.contains(StatementBehaviour.BREAK)) {
                    loopBehaviour.plus(StatementBehaviour.NEXT).subtract(setOf(StatementBehaviour.BREAK, StatementBehaviour.CONTINUE))
                } else {
                    loopBehaviour.subtract(setOf(StatementBehaviour.NEXT, StatementBehaviour.CONTINUE))
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
                if (clauseBehaviour.contains(StatementBehaviour.BREAK)) {
                    clauseBehaviour.plus(StatementBehaviour.NEXT).minus(StatementBehaviour.BREAK)
                } else {
                    clauseBehaviour
                }
            }

            is Statement.FunctionCall ->
                // All builtin functions have the behaviour set `{ Next }`: https://www.w3.org/TR/WGSL/#behaviors-rules
                if (isStatementFunctionCallBuiltin(statement)) {
                    setOf(StatementBehaviour.NEXT)
                } else {
                    // Functions have been reordered so that a callee will be analysed before callers. This means
                    // the behaviour of the callee is known when called.
                    functionMap[statement.callee]!!
                }

            // NON-STANDARD: the code here is statically unreachable so has behaviour next.
            is AugmentedStatement.ControlFlowWrapper -> TODO("Not implemented ControlFlowWrapper in statementBehaviour")
            is AugmentedStatement.ControlFlowWrapReturn -> TODO("Not implemented ControlFlowWrapReturn in statementBehaviour")
            is AugmentedStatement.ControlFlowWrapHelperStatement ->
                TODO("Not implemented ControlFlowWrapHelperStatement in statementBehaviour")
        }

    behaviourMap.put(statement, behaviour)
    return behaviour
}
