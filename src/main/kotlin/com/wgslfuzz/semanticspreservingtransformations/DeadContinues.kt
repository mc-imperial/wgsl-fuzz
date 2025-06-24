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
import com.wgslfuzz.core.ParsedShaderJob
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.clone
import com.wgslfuzz.core.traverse
import java.util.LinkedList

private typealias DeadContinueInjections = MutableMap<Statement.Compound, Set<Int>>

private class InjectDeadContinues(
    private val parsedShaderJob: ParsedShaderJob,
    private val fuzzerSettings: FuzzerSettings,
) {
    // Records all enclosing loops (of all kinds) and continuing statements during traversal.
    // This is necessary because a continue can only occur from inside a loop, but should not occur from
    // inside a continuing statement (unless inside a loop within that statement).
    private val enclosingConstructsStack: MutableList<AstNode> = LinkedList()

    private fun injectDeadContinues(
        node: AstNode?,
        injections: DeadContinueInjections,
    ): AstNode? =
        injections[node]?.let { injectionPoints ->
            val compound = node as Statement.Compound
            val newBody = mutableListOf<Statement>()
            compound.statements.forEachIndexed { index, statement ->
                if (index in injectionPoints) {
                    newBody.add(
                        createDeadContinue(),
                    )
                }
                newBody.add(
                    statement.clone {
                        injectDeadContinues(it, injections)
                    },
                )
            }
            if (compound.statements.size in injectionPoints) {
                newBody.add(
                    createDeadContinue(),
                )
            }
            Statement.Compound(newBody)
        }

    private fun createDeadContinue() =
        AugmentedStatement.DeadCodeFragment(
            Statement.If(
                condition =
                    generateFalseByConstructionExpression(fuzzerSettings, parsedShaderJob),
                thenBranch =
                    Statement.Compound(
                        listOf(Statement.Continue()),
                    ),
            ),
        )

    private fun selectInjectionPoints(
        node: AstNode,
        injectionPoints: DeadContinueInjections,
    ) {
        if (node is ContinuingStatement ||
            node is Statement.Loop ||
            node is Statement.For ||
            node is Statement.While
        ) {
            // Push node on to constructs stack
            enclosingConstructsStack.addFirst(node)
        }
        traverse(::selectInjectionPoints, node, injectionPoints)
        if (node is ContinuingStatement ||
            node is Statement.Loop ||
            node is Statement.For ||
            node is Statement.While
        ) {
            enclosingConstructsStack.removeFirst()
        }
        if (node is Statement.Compound &&
            enclosingConstructsStack.isNotEmpty() &&
            enclosingConstructsStack.first() !is ContinuingStatement
        ) {
            injectionPoints[node] =
                (0..node.statements.size)
                    .filter {
                        fuzzerSettings.injectDeadContinue()
                    }.toSet()
        }
    }

    fun apply(): ParsedShaderJob {
        val injections: DeadContinueInjections = mutableMapOf()
        traverse(::selectInjectionPoints, parsedShaderJob.tu, injections)
        return ParsedShaderJob(
            tu = parsedShaderJob.tu.clone { injectDeadContinues(it, injections) },
            pipelineState = parsedShaderJob.pipelineState,
        )
    }
}

fun addDeadContinues(
    parsedShaderJob: ParsedShaderJob,
    fuzzerSettings: FuzzerSettings,
): ParsedShaderJob =
    InjectDeadContinues(
        parsedShaderJob,
        fuzzerSettings,
    ).apply()
