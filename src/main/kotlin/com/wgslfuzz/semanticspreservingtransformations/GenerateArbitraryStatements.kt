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
import com.wgslfuzz.core.LhsExpression
import com.wgslfuzz.core.Scope
import com.wgslfuzz.core.ShaderJob
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.Type
import com.wgslfuzz.core.clone
import com.wgslfuzz.core.nodesPreOrder

fun generateArbitraryElseBranch(
    depth: Int,
    sideEffectsAllowed: Boolean,
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
    donorShaderJob: ShaderJob,
    returnType: Type?,
): AugmentedStatement.ArbitraryElseBranch? {
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
                            returnType = returnType,
                        ),
                    elseBranch =
                        generateArbitraryElseBranch(
                            depth = depth + 1,
                            sideEffectsAllowed = sideEffectsAllowed,
                            fuzzerSettings = fuzzerSettings,
                            shaderJob = shaderJob,
                            scope = scope,
                            donorShaderJob = donorShaderJob,
                            returnType = returnType,
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
                    returnType = returnType,
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
    returnType: Type?,
): Statement.Compound {
    if (!sideEffectsAllowed) TODO("Not yet implemented side effect free arbitrary compound generation")
    // Select random compound out of donorShaderJob
    val compoundFromDonor = randomCompound(fuzzerSettings, donorShaderJob)

    // Process the donorShaderJob code
    val compoundWithVariablesRenamed =
        compoundFromDonor.renameVariables(
            fuzzerSettings = fuzzerSettings,
            sideEffectsAllowed = sideEffectsAllowed,
            scope = scope,
            donorShaderJob = donorShaderJob,
            shaderJob = shaderJob,
        )

    val compoundWithReturnsOfCorrectType =
        compoundWithVariablesRenamed.clone { node ->
            if (node is Statement.Return) {
                Statement.Return(
                    returnType?.let {
                        generateArbitraryExpression(
                            depth = depth + 1,
                            type = it,
                            sideEffectsAllowed = sideEffectsAllowed,
                            fuzzerSettings = fuzzerSettings,
                            shaderJob = shaderJob,
                            scope = scope,
                        )
                    },
                )
            } else {
                null
            }
        }

    return compoundWithReturnsOfCorrectType
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
    shaderJob: ShaderJob,
): Statement.Compound {
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
                    else -> null
                }
            }.toSet()

    val identifierNamesToTypes =
        preOrderDonorNodes
            .mapNotNull { node ->
                when (node) {
                    is LhsExpression.Identifier -> node.name to donorShaderJob.environment.typeOf(node)
                    is Expression.Identifier -> node.name to donorShaderJob.environment.typeOf(node)
                    is Statement.Value -> {
                        val type =
                            node.typeDecl?.toType(donorShaderJob.environment)
                                ?: donorShaderJob.environment.typeOf(node.initializer)
                        node.name to type
                    }
                    is Statement.Variable -> {
                        val type =
                            node.typeDecl?.toType(donorShaderJob.environment)
                                ?: node.initializer?.let { donorShaderJob.environment.typeOf(it) }

                        type?.let { node.name to it }
                    }
                    else -> null
                }
            }.toMap()

    val oldNamesToNewNames = identifierNamesToTypes.map { it.key }.associateWith { "${it}_${fuzzerSettings.getUniqueId()}" }

    val renamedStatements =
        this.statements.clone {
            when (it) {
                is LhsExpression.Identifier -> LhsExpression.Identifier(oldNamesToNewNames[it.name]!!)
                is Expression.Identifier -> Expression.Identifier(oldNamesToNewNames[it.name]!!)
                is Statement.Value ->
                    Statement.Value(
                        isConst = it.isConst,
                        name = oldNamesToNewNames[it.name]!!,
                        typeDecl = it.typeDecl,
                        initializer = it.initializer,
                    )
                is Statement.Variable ->
                    Statement.Variable(
                        name = oldNamesToNewNames[it.name]!!,
                        addressSpace = it.addressSpace,
                        accessMode = it.accessMode,
                        typeDecl = it.typeDecl,
                        initializer = it.initializer,
                    )
                else -> null
            }
        }

    // Add definitions for undefined variables in compound
    val variablesUndefinedInCompound = variablesUsedInCompound - variablesDefinedInCompound
    val compoundWithDefinitionsAndVariablesRenamed =
        Statement.Compound(
            statements =
                variablesUndefinedInCompound.map {
                    val initializer =
                        generateArbitraryExpression(
                            depth = 0,
                            type = identifierNamesToTypes[it]!!,
                            sideEffectsAllowed = sideEffectsAllowed,
                            fuzzerSettings = fuzzerSettings,
                            shaderJob = shaderJob,
                            scope = scope,
                        )
                    if (it in variablesMutatedInCompound) {
                        Statement.Variable(
                            name = oldNamesToNewNames[it]!!,
                            initializer = initializer,
                        )
                    } else {
                        Statement.Value(
                            isConst = false,
                            name = oldNamesToNewNames[it]!!,
                            initializer = initializer,
                        )
                    }
                } + renamedStatements,
        )

    return compoundWithDefinitionsAndVariablesRenamed
}
