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
import com.wgslfuzz.core.AugmentedMetadata
import com.wgslfuzz.core.AugmentedStatement
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.LhsExpression
import com.wgslfuzz.core.ShaderJob
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.Type
import com.wgslfuzz.core.asStoreTypeIfReference
import com.wgslfuzz.core.clone
import com.wgslfuzz.core.nodesPreOrder

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
): Pair<AugmentedStatement.ArbitraryElseBranch?, Set<String>> {
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

    val (arbitraryCompound, functionCalls) =
        if (fuzzerSettings.goDeeper(depth)) {
            choose(fuzzerSettings, recursiveChoices + nonRecursiveChoices)
        } else {
            choose(fuzzerSettings, nonRecursiveChoices)
        }
    return Pair(
        arbitraryCompound?.let { AugmentedStatement.ArbitraryElseBranch(it) },
        functionCalls,
    )
}

/**
 * generateArbitraryCompound generates arbitrary compounds by taking in a donor shader and selecting a random compound from the donor shader and returning it.
 * It performs a few transformations to the donor compound to ensure it is semantics preserving.
 * - Renames all variables in donor compound to "<original variable name>_<unique id>" to ensure no overlap between variable names in original shader and donor shader
 * - Adds variable declarations for all variables undeclared from the donor compound
 * - Changes all return expressions to be arbitrary expressions of the correct type
 *
 * @return a pair containing the arbitrary compound generated and a set containing all user defined function calls in the arbitrary compound
 *
 * Note: the donor shader cannot contain any structs
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
    val compoundWithVariablesRenamedAndDeclarationsAdded =
        compoundFromDonor.renameVariablesAndAddDeclarations(
            fuzzerSettings = fuzzerSettings,
            sideEffectsAllowed = sideEffectsAllowed,
            donorShaderJob = donorShaderJob,
            shaderJob = shaderJob,
        )

    val compoundWithReturnsOfCorrectType =
        compoundWithVariablesRenamedAndDeclarationsAdded.clone { node ->
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
            .filterIsInstance<Statement.FunctionCall>()
            .map { it.callee }
            .toSet()

    return Pair(
        Statement.Compound(
            statements = compoundWithReturnsOfCorrectType.statements,
            metadata = AugmentedMetadata.ArbitraryCompoundMetaData,
        ),
        functionCalls,
    )
}

private fun randomCompound(
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
): Statement.Compound = fuzzerSettings.randomElement(nodesPreOrder(shaderJob.tu).filterIsInstance<Statement.Compound>())

private fun Statement.Compound.renameVariablesAndAddDeclarations(
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
                            node.typeDecl?.toType(donorShaderJob.environment)
                                ?: donorShaderJob.environment.typeOf(node.initializer).asStoreTypeIfReference()
                        node.name to type
                    }
                    is Statement.Variable -> {
                        val type =
                            node.typeDecl?.toType(donorShaderJob.environment)
                                ?: node.initializer?.let { donorShaderJob.environment.typeOf(it) }?.asStoreTypeIfReference()

                        type?.let { node.name to it }
                    }
                    else -> null
                }
            }.toMap()

    // Take all variable names and map them to a new unique name
    // Then, clone the donorCompound and swap all variable names with their new names
    val oldNamesToNewNames = identifierNamesToTypes.map { it.key }.associateWith { "${it}_${fuzzerSettings.getUniqueId()}" }

    fun renameVariablesHelper(node: AstNode): AstNode? =
        when (node) {
            is LhsExpression.Identifier -> LhsExpression.Identifier(oldNamesToNewNames[node.name]!!)
            is Expression.Identifier -> Expression.Identifier(oldNamesToNewNames[node.name]!!)
            is Statement.Value ->
                Statement.Value(
                    isConst = node.isConst,
                    name = oldNamesToNewNames[node.name]!!,
                    typeDecl = node.typeDecl?.clone(::renameVariablesHelper),
                    initializer = node.initializer.clone(::renameVariablesHelper),
                )
            is Statement.Variable ->
                Statement.Variable(
                    name = oldNamesToNewNames[node.name]!!,
                    addressSpace = node.addressSpace,
                    accessMode = node.accessMode,
                    typeDecl = node.typeDecl?.clone(::renameVariablesHelper),
                    initializer = node.initializer?.clone(::renameVariablesHelper),
                )
            else -> null
        }
    val renamedStatements = this.statements.clone(::renameVariablesHelper)

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
        }

    return Statement.Compound(statements = variableInitializers + renamedStatements)
}
