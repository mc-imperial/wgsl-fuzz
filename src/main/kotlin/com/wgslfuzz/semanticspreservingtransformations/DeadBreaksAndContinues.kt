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

import com.wgslfuzz.core.AstNode
import com.wgslfuzz.core.AugmentedStatement
import com.wgslfuzz.core.ContinuingStatement
import com.wgslfuzz.core.Scope
import com.wgslfuzz.core.ShaderJob
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.clone
import com.wgslfuzz.core.traverse
import java.util.LinkedList

private typealias DeadBreakContinueInjections = MutableMap<Statement.Compound, Set<Int>>

private class InjectDeadBreaksOrContinues(
    private val shaderJob: ShaderJob,
    private val fuzzerSettings: FuzzerSettings,
    // This controls whether breaks or continues are injected
    private val injectBreaks: Boolean,
) {
    // Records relevant enclosing AST nodes that determine whether a jump is possible. This is necessary because e.g.
    // a break can only occur from inside a loop or switch, a continue can only occur from inside a loop, and neither
    // should appear at the top level of a continuing statement.
    private val enclosingConstructsStack: MutableList<AstNode> = LinkedList()

    private fun injectDeadJumps(
        node: AstNode?,
        injections: DeadBreakContinueInjections,
    ): AstNode? =
        injections[node]?.let { indices ->
            val compound = node as Statement.Compound
            val newBody = mutableListOf<Statement>()
            for (index in 0..compound.statements.size) {
                if (index in indices) {
                    newBody.add(
                        createDeadBreakOrContinue(
                            scope = shaderJob.environment.scopeAtIndex(compound, index),
                        ),
                    )
                }
                if (index < compound.statements.size) {
                    newBody.add(
                        compound.statements[index].clone {
                            injectDeadJumps(it, injections)
                        },
                    )
                }
            }
            Statement.Compound(newBody)
        }

    private fun createBreakOrContinueStatement(): Statement =
        if (injectBreaks) {
            Statement.Break()
        } else {
            Statement.Continue()
        }

    private fun createDeadBreakOrContinue(scope: Scope): AugmentedStatement.DeadCodeFragment {
        val deadStatement =
            Statement.Compound(
                listOf(createBreakOrContinueStatement()),
            )
        val choices =
            mutableListOf(
                1 to {
                    createIfFalseThenDeadStatement(
                        falseCondition = generateFalseByConstructionExpression(fuzzerSettings, shaderJob, scope),
                        deadStatement = deadStatement,
                        includeEmptyElseBranch = false,
                    )
                },
                1 to {
                    createIfFalseThenDeadStatement(
                        falseCondition = generateFalseByConstructionExpression(fuzzerSettings, shaderJob, scope),
                        deadStatement = deadStatement,
                        includeEmptyElseBranch = true,
                    )
                },
                2 to {
                    createIfTrueElseDeadStatement(
                        trueCondition = generateTrueByConstructionExpression(fuzzerSettings, shaderJob, scope),
                        deadStatement = deadStatement,
                    )
                },
            )
        return choose(fuzzerSettings, choices)
    }

    private fun selectInjectionPoints(
        node: AstNode,
        injectionPoints: DeadBreakContinueInjections,
    ) {
        if (isRelevantEnclosingConstruct(node)) {
            // Push node on to constructs stack
            enclosingConstructsStack.addFirst(node)
        }
        traverse(::selectInjectionPoints, node, injectionPoints)
        if (isRelevantEnclosingConstruct(node)) {
            enclosingConstructsStack.removeFirst()
        }
        if (node is Statement.Compound &&
            enclosingConstructsStack.isNotEmpty() &&
            enclosingConstructsStack.first() !is ContinuingStatement
        ) {
            injectionPoints[node] =
                (0..node.statements.size)
                    .filter {
                        randomChoiceToInject()
                    }.toSet()
        }
    }

    private fun randomChoiceToInject(): Boolean =
        if (injectBreaks) {
            fuzzerSettings.injectDeadBreak()
        } else {
            fuzzerSettings.injectDeadContinue()
        }

    private fun isRelevantEnclosingConstruct(node: AstNode): Boolean {
        if (node is ContinuingStatement ||
            node is Statement.Loop ||
            node is Statement.For ||
            node is Statement.While
        ) {
            return true
        }
        if (injectBreaks && node is Statement.Switch) {
            return true
        }
        return false
    }

    fun apply(): ShaderJob {
        val injections: DeadBreakContinueInjections = mutableMapOf()
        traverse(::selectInjectionPoints, shaderJob.tu, injections)
        return ShaderJob(
            tu = shaderJob.tu.clone { injectDeadJumps(it, injections) },
            pipelineState = shaderJob.pipelineState,
        )
    }
}

fun addDeadBreaks(
    shaderJob: ShaderJob,
    fuzzerSettings: FuzzerSettings,
): ShaderJob =
    InjectDeadBreaksOrContinues(
        shaderJob = shaderJob,
        fuzzerSettings = fuzzerSettings,
        injectBreaks = true,
    ).apply()

fun addDeadContinues(
    shaderJob: ShaderJob,
    fuzzerSettings: FuzzerSettings,
): ShaderJob =
    InjectDeadBreaksOrContinues(
        shaderJob = shaderJob,
        fuzzerSettings = fuzzerSettings,
        injectBreaks = false,
    ).apply()
