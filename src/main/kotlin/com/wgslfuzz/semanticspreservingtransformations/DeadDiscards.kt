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

import com.wgslfuzz.analysis.ShaderStage
import com.wgslfuzz.analysis.runFunctionToShaderStageAnalysis
import com.wgslfuzz.core.AstNode
import com.wgslfuzz.core.AugmentedStatement
import com.wgslfuzz.core.ContinuingStatement
import com.wgslfuzz.core.GlobalDecl
import com.wgslfuzz.core.Scope
import com.wgslfuzz.core.ShaderJob
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.clone
import com.wgslfuzz.core.traverse

private typealias DeadDiscardInjections = MutableMap<Statement.Compound, Set<Int>>

private class InjectDeadDiscards(
    private val shaderJob: ShaderJob,
    private val fuzzerSettings: FuzzerSettings,
) {
    private val functionToShaderStage: Map<String, Set<ShaderStage>> =
        runFunctionToShaderStageAnalysis(shaderJob.tu, shaderJob.environment)

    private fun injectDeadDiscards(
        node: AstNode?,
        injections: DeadDiscardInjections,
    ): AstNode? =
        injections[node]?.let { injectionPoints ->
            val compound = node as Statement.Compound
            val newBody = mutableListOf<Statement>()
            compound.statements.forEachIndexed { index, statement ->
                if (index in injectionPoints) {
                    newBody.add(
                        createDeadDiscard(shaderJob.environment.scopeAvailableBefore(statement)),
                    )
                }
                newBody.add(
                    statement.clone {
                        injectDeadDiscards(it, injections)
                    },
                )
            }
            if (compound.statements.size in injectionPoints) {
                newBody.add(
                    createDeadDiscard(TODO()),
                )
            }
            Statement.Compound(newBody)
        }

    private fun createDeadDiscard(scope: Scope) =
        AugmentedStatement.DeadCodeFragment(
            Statement.If(
                condition =
                    generateFalseByConstructionExpression(fuzzerSettings, shaderJob, scope),
                thenBranch =
                    Statement.Compound(
                        listOf(Statement.Discard()),
                    ),
            ),
        )

    private fun selectInjectionPoints(
        node: AstNode,
        injectionPoints: DeadDiscardInjections,
    ) {
        if (node is GlobalDecl.Function) {
            functionToShaderStage[node.name]?.let {
                if (ShaderStage.VERTEX in it || ShaderStage.COMPUTE in it) {
                    // Discards are only allowed in fragment shaders.
                    return
                }
            }
        }
        if (node is ContinuingStatement) {
            // A discard is not allowed from inside a continuing statement, so cut off traversal.
            return
        }
        traverse(::selectInjectionPoints, node, injectionPoints)
        if (node is Statement.Compound) {
            injectionPoints[node] =
                (0..node.statements.size)
                    .filter {
                        fuzzerSettings.injectDeadDiscard()
                    }.toSet()
        }
    }

    fun apply(): ShaderJob {
        val injections: DeadDiscardInjections = mutableMapOf()
        traverse(::selectInjectionPoints, shaderJob.tu, injections)
        return ShaderJob(
            tu = shaderJob.tu.clone { injectDeadDiscards(it, injections) },
            pipelineState = shaderJob.pipelineState,
        )
    }
}

fun addDeadDiscards(
    shaderJob: ShaderJob,
    fuzzerSettings: FuzzerSettings,
): ShaderJob =
    InjectDeadDiscards(
        shaderJob,
        fuzzerSettings,
    ).apply()
