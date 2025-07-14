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

// This file encapsulates the logic for injecting dead returns. While there is a lot in common with the logic for
// injecting dead discards, breaks and continues, there are enough differences to make code sharing artificial (save for
// certain helper functions).

/**
 * See DeadBreakContinueInjections - the purpose of this typealias is similar.
 */
private typealias DeadReturnInjections = MutableMap<Statement.Compound, Set<Int>>

private class InjectDeadReturns(
    private val shaderJob: ShaderJob,
    private val fuzzerSettings: FuzzerSettings,
) {
    private var enclosingFunctionReturnType: Type? = null

    /**
     * See corresponding comment in DeadBreaksAndContinues.
     */
    private fun injectDeadReturns(
        node: AstNode?,
        injections: DeadReturnInjections,
    ): AstNode? =
        if (node is GlobalDecl.Function) {
            // Intercepting function declarations is necessary to capture the return type of the function being
            // traversed. This is important so that dead return statements can use the right return type.
            check(enclosingFunctionReturnType == null)
            enclosingFunctionReturnType =
                (shaderJob.environment.globalScope.getEntry(node.name) as ScopeEntry.Function).type.returnType
            // Leave the function intact, except for its body which must be cloned with replacements that will inject
            // dead returns.
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
            // The logic here is analogous to that in DeadBreaksAndContinues.
            injections[node]?.let { indices ->
                val compound = node as Statement.Compound
                val newBody = mutableListOf<Statement>()
                for (index in 0..compound.statements.size) {
                    if (index in indices) {
                        newBody.add(
                            createDeadReturn(
                                scope = shaderJob.environment.scopeAtIndex(compound, index),
                            ),
                        )
                    }
                    if (index < compound.statements.size) {
                        newBody.add(
                            compound.statements[index].clone {
                                injectDeadReturns(it, injections)
                            },
                        )
                    }
                }
                Statement.Compound(newBody)
            }
        }

    private fun createDeadReturn(scope: Scope): AugmentedStatement.DeadCodeFragment {
        // Make a return statement, returning a randomly-generated expression of the right type when there is a return
        // type.
        val returnStatement =
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
            )
        return deadDiscardOrReturn(
            shaderJob = shaderJob,
            fuzzerSettings = fuzzerSettings,
            scope = scope,
            discardOrReturn = returnStatement,
        )
    }

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
