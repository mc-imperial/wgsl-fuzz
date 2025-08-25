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

import com.wgslfuzz.core.AugmentedStatement
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.GlobalDecl
import com.wgslfuzz.core.LhsExpression
import com.wgslfuzz.core.ParameterDecl
import com.wgslfuzz.core.Scope
import com.wgslfuzz.core.ShaderJob
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.StructMember
import com.wgslfuzz.core.TranslationUnit
import com.wgslfuzz.core.Type
import com.wgslfuzz.core.nodesPreOrder

fun generateArbitraryElseBranch(
    depth: Int,
    sideEffectsAllowed: Boolean,
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
    donorShaderJob: ShaderJob,
): Statement.ElseBranch {
    val nonRecursiveChoices =
        listOf(
            fuzzerSettings.arbitraryElseBranchWeights.empty(depth) to {
                null
            },
        )
    val recursiveChoices =
        listOf(
            fuzzerSettings.arbitraryElseBranchWeights.ifStatement(depth) to {
                Statement.If(
                    attributes = emptyList(),
                    condition =
                        generateArbitraryExpression(
                            depth = depth + 1,
                            type = Type.Bool,
                            sideEffectsAllowed = sideEffectsAllowed,
                            fuzzerSettings = fuzzerSettings,
                            shaderJob = shaderJob,
                            scope = scope,
                        ),
                    thenBranch =
                        generateArbitraryCompound(
                            depth = depth + 1,
                            sideEffectsAllowed = sideEffectsAllowed,
                            fuzzerSettings = fuzzerSettings,
                            shaderJob = shaderJob,
                            scope = scope,
                            donorShaderJob = donorShaderJob,
                        ),
                    elseBranch =
                        generateArbitraryElseBranch(
                            depth = depth + 1,
                            sideEffectsAllowed = sideEffectsAllowed,
                            fuzzerSettings = fuzzerSettings,
                            shaderJob = shaderJob,
                            scope = scope,
                            donorShaderJob = donorShaderJob,
                        ),
                )
            },
            fuzzerSettings.arbitraryElseBranchWeights.compound(depth) to {
                generateArbitraryCompound(
                    depth = depth + 1,
                    sideEffectsAllowed = sideEffectsAllowed,
                    fuzzerSettings = fuzzerSettings,
                    shaderJob = shaderJob,
                    scope = scope,
                    donorShaderJob = donorShaderJob,
                )
            },
        )

    return if (fuzzerSettings.goDeeper(depth)) {
        choose(fuzzerSettings, recursiveChoices + nonRecursiveChoices)
    } else {
        choose(fuzzerSettings, nonRecursiveChoices)
    }?.let { AugmentedStatement.ArbitraryElseBranch(it) }
}

fun generateArbitraryCompound(
    depth: Int,
    sideEffectsAllowed: Boolean,
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
    donorShaderJob: ShaderJob,
): Statement.Compound {
    // Select random compound out of donorShaderJob
    val compoundFromDonor = randomCompound(fuzzerSettings, donorShaderJob)

    // Process the donorShaderJob code
    // Rename variables
    val compoundWithVariablesRenamed = compoundFromDonor.renameVariables(fuzzerSettings, sideEffectsAllowed, scope, donorShaderJob)
}

private fun randomCompound(
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
): Statement.Compound = fuzzerSettings.randomElement(nodesPreOrder(shaderJob.tu).filterIsInstance<Statement.Compound>())

private fun Statement.Compound.renameVariables(
    fuzzerSettings: FuzzerSettings,
    sideEffectsAllowed: Boolean,
    scope: Scope,
    donorShaderJob: ShaderJob,
) {
    val preOrderDonorNodes = nodesPreOrder(this)

    val variablesMutatedInCompound =
        preOrderDonorNodes.filterIsInstance<LhsExpression.Identifier>().map { it.name }.toSet()
    val variablesUsedInCompound =
        preOrderDonorNodes.filterIsInstance<Expression.Identifier>().map { it.name }.toSet() + variablesMutatedInCompound

    val variablesDefinedInCompound =
        preOrderDonorNodes
            .mapNotNull {
                when (it) {
                    is Statement.Value -> it.name
                    is Statement.Variable -> it.name

                    is GlobalDecl -> throw RuntimeException("Cannot have GlobalDecl within a compound")
                    is ParameterDecl -> throw RuntimeException(
                        "Cannot define a function outside of a GlobalDecl hence cannot have a ParameterDecl in a Compound",
                    )
                    is StructMember -> throw RuntimeException("Cannot have StructMember within a compound")
                    is TranslationUnit -> throw RuntimeException("TranslationUnit should not be in a compound")

                    else -> null
                }
            }.toSet()

    // Add definitions for undefined variables in compound
    val variablesUndefinedInCompound = variablesUsedInCompound - variablesDefinedInCompound
    val compoundWithDefinitions =
        Statement.Compound(
            statements =
                variablesUndefinedInCompound.map {
                    val initializer = generateInitializer(TODO("Get type of it $it"))
                    if (it in variablesMutatedInCompound) {
                        Statement.Variable(
                            name = it,
                            initializer = initializer,
                        )
                    } else {
                        Statement.Value(
                            isConst = false,
                            name = it,
                            initializer = initializer,
                        )
                    }
                } + this.statements,
        )

    val oldNameToNewName =
        variablesUsedInCompound.associateWith { "${it}_${fuzzerSettings.getUniqueId()}" }
}

private fun generateInitializer(type: Type): Expression = TODO()
