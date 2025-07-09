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
import com.wgslfuzz.core.Type
import com.wgslfuzz.core.clone
import com.wgslfuzz.core.traverse

/**
 * For convenience this helper is shared with the pass that injects dead returns.
 */
fun deadDiscardOrReturn(
    shaderJob: ShaderJob,
    fuzzerSettings: FuzzerSettings,
    scope: Scope,
    discardOrReturn: Statement,
): AugmentedStatement.DeadCodeFragment {
    check(discardOrReturn is Statement.Discard || discardOrReturn is Statement.Return)
    val deadStatement =
        Statement.Compound(
            listOf(discardOrReturn),
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
            1 to {
                createWhileFalseDeadStatement(
                    falseCondition = generateFalseByConstructionExpression(fuzzerSettings, shaderJob, scope),
                    deadStatement = deadStatement,
                )
            },
            1 to {
                createForWithFalseConditionDeadStatement(
                    falseCondition = generateFalseByConstructionExpression(fuzzerSettings, shaderJob, scope),
                    deadStatement = deadStatement,
                    // TODO: with some probability, randomly pick a variable and perform a random update to it.
                    unreachableUpdate = null,
                )
            },
            1 to {
                val includeContinuingStatement = fuzzerSettings.randomBool()
                val breakIfExpr =
                    if (includeContinuingStatement && fuzzerSettings.randomBool()) {
                        generateArbitraryExpression(
                            depth = 0,
                            type = Type.Bool,
                            sideEffectsAllowed = true,
                            fuzzerSettings = fuzzerSettings,
                            shaderJob = shaderJob,
                            scope = scope,
                        )
                    } else {
                        null
                    }
                createLoopWithUnconditionalBreakDeadStatement(
                    trueCondition = generateTrueByConstructionExpression(fuzzerSettings, shaderJob, scope),
                    deadStatement = deadStatement,
                    includeContinuingStatement = includeContinuingStatement,
                    breakIfExpr = breakIfExpr,
                )
            },
        )
    return choose(fuzzerSettings, choices)
}

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
        injections[node]?.let { indices ->
            val compound = node as Statement.Compound
            val newBody = mutableListOf<Statement>()
            for (index in 0..compound.statements.size) {
                if (index in indices) {
                    newBody.add(
                        deadDiscardOrReturn(
                            shaderJob = shaderJob,
                            fuzzerSettings = fuzzerSettings,
                            scope = shaderJob.environment.scopeAtIndex(compound, index),
                            discardOrReturn = Statement.Discard(),
                        ),
                    )
                }
                if (index < compound.statements.size) {
                    newBody.add(
                        compound.statements[index].clone {
                            injectDeadDiscards(it, injections)
                        },
                    )
                }
            }
            Statement.Compound(newBody)
        }

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
