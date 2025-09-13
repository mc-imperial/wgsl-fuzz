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

import com.wgslfuzz.core.AugmentedMetadata
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.LhsExpression
import com.wgslfuzz.core.ShaderJob
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.Type
import com.wgslfuzz.core.asStoreTypeIfReference
import com.wgslfuzz.core.builtinFunctionNames
import com.wgslfuzz.core.clone
import com.wgslfuzz.core.nodesPreOrder
import com.wgslfuzz.core.toType

/**
 * @return a pair containing the arbitrary else branch generated and a set containing all user defined function calls in the arbitrary else branch
 */
fun generateArbitraryElseBranch(
    depth: Int,
    sideEffectsAllowed: Boolean,
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    donorShaderJob: ShaderJob,
    returnType: Type?,
): Pair<Statement.ElseBranch?, Set<String>> {
    val nonRecursiveChoices: List<Pair<Int, () -> Pair<Statement.ElseBranch?, Set<String>>>> =
        listOf(
            fuzzerSettings.arbitraryElseBranchWeights.empty(depth) to {
                Pair(null, emptySet())
            },
        )
    val recursiveChoices: List<Pair<Int, () -> Pair<Statement.ElseBranch?, Set<String>>>> =
        listOf(
            fuzzerSettings.arbitraryElseBranchWeights.ifStatement(depth) to {
                val (thenCompound, thenFunctionCalls) =
                    generateArbitraryCompound(
                        depth = depth + 1,
                        sideEffectsAllowed = sideEffectsAllowed,
                        fuzzerSettings = fuzzerSettings,
                        shaderJob = shaderJob,
                        donorShaderJob = donorShaderJob,
                        returnType = returnType,
                    )
                val (elseCompound, elseFunctionCalls) =
                    generateArbitraryElseBranch(
                        depth = depth + 1,
                        sideEffectsAllowed = sideEffectsAllowed,
                        fuzzerSettings = fuzzerSettings,
                        shaderJob = shaderJob,
                        donorShaderJob = donorShaderJob,
                        returnType = returnType,
                    )
                Pair(
                    Statement.If(
                        attributes = emptyList(),
                        condition =
                            generateArbitraryExpression(
                                depth = depth + 1,
                                type = Type.Bool,
                                sideEffectsAllowed = sideEffectsAllowed,
                                fuzzerSettings = fuzzerSettings,
                                shaderJob = shaderJob,
                                scope = shaderJob.environment.globalScope,
                            ),
                        thenBranch = thenCompound,
                        elseBranch = elseCompound,
                    ),
                    thenFunctionCalls + elseFunctionCalls,
                )
            },
            fuzzerSettings.arbitraryElseBranchWeights.compound(depth) to {
                generateArbitraryCompound(
                    depth = depth + 1,
                    sideEffectsAllowed = sideEffectsAllowed,
                    fuzzerSettings = fuzzerSettings,
                    shaderJob = shaderJob,
                    donorShaderJob = donorShaderJob,
                    returnType = returnType,
                )
            },
        )

    val (arbitraryElseBranch, functionCalls) =
        if (fuzzerSettings.goDeeper(depth)) {
            choose(fuzzerSettings, recursiveChoices + nonRecursiveChoices)
        } else {
            choose(fuzzerSettings, nonRecursiveChoices)
        }
    return Pair(
        arbitraryElseBranch,
        functionCalls,
    )
}

/**
 * generateArbitraryCompound generates arbitrary compounds by taking in a donor shader and selecting a random compound from the donor shader and returning it.
 * It performs a few transformations to the donor compound to ensure it is semantics preserving.
 * - Adds variable declarations for all variables undeclared from the donor compound
 * - Changes all return expressions to be arbitrary expressions of the correct type
 *
 * @return a pair containing the arbitrary compound generated and a set containing all user defined function calls in the arbitrary compound
 *
 * Note: the donor shader cannot contain any structs and not share any variable names with shaderJob
 */
fun generateArbitraryCompound(
    depth: Int,
    sideEffectsAllowed: Boolean,
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    donorShaderJob: ShaderJob,
    returnType: Type?,
): Pair<Statement.Compound, Set<String>> {
    if (!sideEffectsAllowed) {
        TODO("Not yet implemented side effect free arbitrary compound generation")
    }
    // Select random compound out of donorShaderJob
    val compoundFromDonor = randomCompound(fuzzerSettings, donorShaderJob)

    // Process the donorShaderJob code
    val compoundWithDeclarationsAdded =
        compoundFromDonor.addDeclarations(
            fuzzerSettings = fuzzerSettings,
            sideEffectsAllowed = sideEffectsAllowed,
            donorShaderJob = donorShaderJob,
            shaderJob = shaderJob,
        )

    val compoundWithReturnsOfCorrectType =
        compoundWithDeclarationsAdded.clone { node ->
            if (node is Statement.Return) {
                Statement.Return(
                    returnType?.let {
                        generateArbitraryExpression(
                            depth = depth + 1,
                            type = it,
                            sideEffectsAllowed = sideEffectsAllowed,
                            fuzzerSettings = fuzzerSettings,
                            shaderJob = shaderJob,
                            scope = shaderJob.environment.globalScope,
                        )
                    },
                )
            } else {
                null
            }
        }

    val functionCalls =
        nodesPreOrder(compoundWithReturnsOfCorrectType)
            .filterIsInstance<Expression.FunctionCall>()
            .map { it.callee }
            .filter {
                it !in builtinFunctionNames
            }.toSet()

    return Pair(
        Statement.Compound(
            statements = compoundWithReturnsOfCorrectType.statements,
            metadata = setOf(AugmentedMetadata.EmptiableCompound(fuzzerSettings.getUniqueId(), "arbitrary compound")),
        ),
        functionCalls,
    )
}

private fun randomCompound(
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
): Statement.Compound = fuzzerSettings.randomElement(nodesPreOrder(shaderJob.tu).filterIsInstance<Statement.Compound>())

private fun Statement.Compound.addDeclarations(
    fuzzerSettings: FuzzerSettings,
    sideEffectsAllowed: Boolean,
    donorShaderJob: ShaderJob,
    shaderJob: ShaderJob,
): Statement.Compound {
    val preOrderDonorNodes = nodesPreOrder(this)

    val variablesMutatedInCompound =
        preOrderDonorNodes
            .filterIsInstance<LhsExpression.Identifier>()
            .map { it.name }
            .toSet()

    val variablesUsedInCompound =
        preOrderDonorNodes
            .filterIsInstance<Expression.Identifier>()
            .map { it.name }
            .toSet() + variablesMutatedInCompound

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
                    is LhsExpression.Identifier -> node.name to donorShaderJob.environment.typeOf(node).asStoreTypeIfReference()
                    is Expression.Identifier -> node.name to donorShaderJob.environment.typeOf(node).asStoreTypeIfReference()
                    is Statement.Value -> {
                        val type =
                            node.typeDecl?.toType(donorShaderJob.environment.globalScope, donorShaderJob.environment)
                                ?: donorShaderJob.environment.typeOf(node.initializer).asStoreTypeIfReference()
                        node.name to type
                    }
                    is Statement.Variable -> {
                        val type =
                            node.typeDecl?.toType(donorShaderJob.environment.globalScope, donorShaderJob.environment)
                                ?: node.initializer?.let { donorShaderJob.environment.typeOf(it) }?.asStoreTypeIfReference()

                        type?.let { node.name to it }
                    }
                    else -> null
                }
            }.toMap()

    // Create variable initializers
    val variablesUndefinedInCompound = variablesUsedInCompound - variablesDefinedInCompound
    val variableInitializers =
        variablesUndefinedInCompound.map {
            val initializer =
                generateArbitraryExpression(
                    depth = 0,
                    type = identifierNamesToTypes[it]!!,
                    sideEffectsAllowed = sideEffectsAllowed,
                    fuzzerSettings = fuzzerSettings,
                    shaderJob = shaderJob,
                    scope = shaderJob.environment.globalScope,
                )
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
        }

    return Statement.Compound(statements = variableInitializers + this.statements, metadata = this.metadata)
}
