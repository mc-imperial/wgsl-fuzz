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
import com.wgslfuzz.core.GlobalDecl
import com.wgslfuzz.core.Scope
import com.wgslfuzz.core.ScopeEntry
import com.wgslfuzz.core.ShaderJob
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.Type
import com.wgslfuzz.core.clone
import com.wgslfuzz.core.traverse

private typealias DeadReturnInjections = MutableMap<Statement.Compound, Set<Int>>

private class InjectDeadReturns(
    private val shaderJob: ShaderJob,
    private val fuzzerSettings: FuzzerSettings,
) {
    private var enclosingFunctionReturnType: Type? = null

    private fun injectDeadReturns(
        node: AstNode?,
        injections: DeadReturnInjections,
    ): AstNode? =
        if (node is GlobalDecl.Function) {
            assert(enclosingFunctionReturnType == null)
            enclosingFunctionReturnType =
                (shaderJob.environment.globalScope.getEntry(node.name) as ScopeEntry.Function).type.returnType
            val result =
                GlobalDecl.Function(
                    attributes = node.attributes,
                    name = node.name,
                    parameters = node.parameters,
                    returnAttributes = node.returnAttributes,
                    returnType = node.returnType,
                    body = node.body.clone { injectDeadReturns(it, injections) },
                )
            enclosingFunctionReturnType = null
            result
        } else {
            injections[node]?.let { injectionPoints ->
                val compound = node as Statement.Compound
                val newBody = mutableListOf<Statement>()
                compound.statements.forEachIndexed { index, statement ->
                    if (index in injectionPoints) {
                        newBody.add(
                            createDeadReturn(shaderJob.environment.scopeAvailableBefore(statement)),
                        )
                    }
                    newBody.add(
                        statement.clone {
                            injectDeadReturns(it, injections)
                        },
                    )
                }
                if (compound.statements.size in injectionPoints) {
                    newBody.add(
                        createDeadReturn(shaderJob.environment.scopeAvailableAtEnd(compound)),
                    )
                }
                Statement.Compound(newBody)
            }
        }

    private fun createDeadReturn(scope: Scope): AugmentedStatement.DeadCodeFragment =
        AugmentedStatement.DeadCodeFragment(
            Statement.If(
                condition =
                    generateFalseByConstructionExpression(fuzzerSettings, shaderJob, scope),
                thenBranch =
                    Statement.Compound(
                        listOf(
                            Statement.Return(
                                enclosingFunctionReturnType?.let {
                                    generateArbitraryExpression(
                                        depth = 0,
                                        type = it,
                                        sideEffectsAllowed = true,
                                        fuzzerSettings = fuzzerSettings,
                                        shaderJob = shaderJob,
                                        scope = scope,
                                    )
                                },
                            ),
                        ),
                    ),
            ),
        )

    private fun selectInjectionPoints(
        node: AstNode,
        injectionPoints: DeadReturnInjections,
    ) {
        if (node is ContinuingStatement) {
            // A return is not allowed from inside a continuing statement, so cut off traversal.
            return
        }
        traverse(::selectInjectionPoints, node, injectionPoints)
        if (node is Statement.Compound) {
            injectionPoints[node] =
                (0..node.statements.size)
                    .filter {
                        fuzzerSettings.injectDeadReturn()
                    }.toSet()
        }
    }

    fun apply(): ShaderJob {
        val injections: DeadReturnInjections = mutableMapOf()
        traverse(::selectInjectionPoints, shaderJob.tu, injections)
        return ShaderJob(
            tu = shaderJob.tu.clone { injectDeadReturns(it, injections) },
            pipelineState = shaderJob.pipelineState,
        )
    }
}

fun addDeadReturns(
    shaderJob: ShaderJob,
    fuzzerSettings: FuzzerSettings,
): ShaderJob =
    InjectDeadReturns(
        shaderJob,
        fuzzerSettings,
    ).apply()
