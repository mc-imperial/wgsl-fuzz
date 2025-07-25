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
import com.wgslfuzz.core.Attribute
import com.wgslfuzz.core.AugmentedExpression
import com.wgslfuzz.core.AugmentedMetadata
import com.wgslfuzz.core.AugmentedStatement
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.LhsExpression
import com.wgslfuzz.core.ShaderJob
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.clone
import com.wgslfuzz.core.nodesPreOrder
import com.wgslfuzz.core.traverse
import kotlin.math.max
import kotlin.math.min

typealias InterestingnessTest = (candidate: ShaderJob) -> Boolean

abstract class ReductionPass<ReductionOpportunityT> {
    abstract fun findOpportunities(originalShaderJob: ShaderJob): List<ReductionOpportunityT>

    abstract fun removeOpportunities(
        originalShaderJob: ShaderJob,
        opportunities: List<ReductionOpportunityT>,
    ): ShaderJob

    fun run(
        originalShaderJob: ShaderJob,
        interestingnessTest: InterestingnessTest,
    ): Pair<ShaderJob, Boolean> {
        var fragments = findOpportunities(originalShaderJob)
        if (fragments.isEmpty()) {
            return Pair(originalShaderJob, false)
        }
        var progressMade = false
        var bestSoFar = originalShaderJob
        var granularity = fragments.size
        while (granularity > 0) {
            var offset = fragments.size - granularity
            while (offset + granularity > 0) {
                val candidateReducedShaderJob =
                    removeOpportunities(
                        bestSoFar,
                        fragments.slice(
                            max(0, offset)..<min(fragments.size, offset + granularity),
                        ),
                    )
                if (interestingnessTest(candidateReducedShaderJob)) {
                    bestSoFar = candidateReducedShaderJob
                    progressMade = true
                    fragments = findOpportunities(bestSoFar)
                    granularity = min(granularity, fragments.size)
                    offset = min(offset, fragments.size - granularity)
                } else {
                    offset -= granularity
                }
            }
            granularity /= 2
        }
        return Pair(bestSoFar, progressMade)
    }
}

fun ShaderJob.reduce(interestingnessTest: InterestingnessTest): Pair<ShaderJob, Boolean> {
    val passes: List<ReductionPass<*>> =
        listOf(
            ReduceDeadCodeFragments(),
            UndoIdentityOperations(),
            ReplaceKnownValues(),
            ReduceControlFlowWrapped(),
        )
    var someReductionWorked = false
    var reducedShaderJob = this
    var continueReducing = true
    while (continueReducing) {
        continueReducing = false
        for (reductionPass in passes) {
            val (newShaderJob, success) = reductionPass.run(reducedShaderJob, interestingnessTest)
            if (success) {
                someReductionWorked = true
                continueReducing = true
                reducedShaderJob = newShaderJob
            }
        }
    }
    return Pair(reducedShaderJob, someReductionWorked)
}

private class CandidateDeadCodeFragment(
    val enclosingCompound: Statement.Compound,
    val offset: Int,
)

private class ReduceDeadCodeFragments : ReductionPass<CandidateDeadCodeFragment>() {
    override fun findOpportunities(originalShaderJob: ShaderJob): List<CandidateDeadCodeFragment> {
        fun finder(
            node: AstNode,
            fragments: MutableList<CandidateDeadCodeFragment>,
        ) {
            when (node) {
                is Expression -> return
                is LhsExpression -> return
                is Attribute -> return
                is Statement.Compound -> {
                    node.statements.forEachIndexed { index, statement ->
                        if (statement is AugmentedStatement.DeadCodeFragment) {
                            fragments.add(CandidateDeadCodeFragment(node, index))
                        }
                    }
                }
                else -> {}
            }
            traverse(::finder, node, fragments)
        }
        val fragments = mutableListOf<CandidateDeadCodeFragment>()
        traverse(::finder, originalShaderJob.tu, fragments)
        return fragments
    }

    override fun removeOpportunities(
        originalShaderJob: ShaderJob,
        opportunities: List<CandidateDeadCodeFragment>,
    ): ShaderJob {
        val fragmentsByCompound: Map<Statement.Compound, List<CandidateDeadCodeFragment>> = opportunities.groupBy { it.enclosingCompound }
        return ShaderJob(
            originalShaderJob.tu.clone { node ->
                fragmentsByCompound[node]?.let { relevantFragments ->
                    // To have been a key in the map, node must be a Compound.
                    node as Statement.Compound
                    val indices = relevantFragments.map { it.offset }.toSet()
                    val newStatements = mutableListOf<Statement>()
                    for (i in 0..<node.statements.size) {
                        if (i in indices && node.statements[i] is AugmentedStatement.DeadCodeFragment) {
                            continue
                        }
                        newStatements.add(node.statements[i])
                    }
                    Statement.Compound(newStatements)
                }
            },
            originalShaderJob.pipelineState,
        )
    }
}

private class UndoIdentityOperations : ReductionPass<AugmentedExpression.IdentityOperation>() {
    override fun findOpportunities(originalShaderJob: ShaderJob): List<AugmentedExpression.IdentityOperation> =
        nodesPreOrder(originalShaderJob.tu).filterIsInstance<AugmentedExpression.IdentityOperation>()

    override fun removeOpportunities(
        originalShaderJob: ShaderJob,
        opportunities: List<AugmentedExpression.IdentityOperation>,
    ): ShaderJob {
        val opportunitiesAsSet = opportunities.toSet()

        fun undoIdentityOperations(node: AstNode): AstNode? {
            if (node !is AugmentedExpression.IdentityOperation || node !in opportunitiesAsSet) {
                return null
            }
            return node.originalExpression.clone(::undoIdentityOperations)
        }
        return ShaderJob(
            originalShaderJob.tu.clone(::undoIdentityOperations),
            originalShaderJob.pipelineState,
        )
    }
}

private class ReplaceKnownValues : ReductionPass<AugmentedExpression.KnownValue>() {
    override fun findOpportunities(originalShaderJob: ShaderJob): List<AugmentedExpression.KnownValue> =
        nodesPreOrder(originalShaderJob.tu).filterIsInstance<AugmentedExpression.KnownValue>()

    override fun removeOpportunities(
        originalShaderJob: ShaderJob,
        opportunities: List<AugmentedExpression.KnownValue>,
    ): ShaderJob {
        val opportunitiesAsSet = opportunities.toSet()

        fun replaceKnownValue(node: AstNode): AstNode? {
            if (node !is AugmentedExpression.KnownValue || node !in opportunitiesAsSet) {
                return null
            }
            return node.knownValue.clone(::replaceKnownValue)
        }
        return ShaderJob(
            originalShaderJob.tu.clone(::replaceKnownValue),
            originalShaderJob.pipelineState,
        )
    }
}

private class ReduceControlFlowWrapped : ReductionPass<AugmentedStatement.ControlFlowWrapper>() {
    override fun findOpportunities(originalShaderJob: ShaderJob): List<AugmentedStatement.ControlFlowWrapper> =
        nodesPreOrder(originalShaderJob.tu).filterIsInstance<AugmentedStatement.ControlFlowWrapper>()

    override fun removeOpportunities(
        originalShaderJob: ShaderJob,
        opportunities: List<AugmentedStatement.ControlFlowWrapper>,
    ): ShaderJob {
        val opportunitiesAsSet = opportunities.toSet()

        fun replaceControlFlowWrapped(node: AstNode): AstNode? {
            if (node is Statement.Compound) {
                // This flattens compounds within compounds if they occur
                val newStatements =
                    node.statements.clone(::replaceControlFlowWrapped).flatMap {
                        if (it is Statement.Compound) {
                            assert(it.metadata == null)
                            it.statements
                        } else {
                            listOf(it)
                        }
                    }
                return Statement.Compound(
                    statements = newStatements,
                    metadata = node.metadata,
                )
            }

            if (node !is AugmentedStatement.ControlFlowWrapper || node !in opportunitiesAsSet) {
                return null
            }

            val originalStatementNode =
                nodesPreOrder(originalShaderJob.tu)
                    .asSequence()
                    .filterIsInstance<Statement.Compound>()
                    .firstOrNull {
                        (it.metadata as? AugmentedMetadata.ControlFlowWrapperMetaData)?.id == node.id
                    } ?: throw AssertionError("Could not find the matching original statement compound for: $node")

            return Statement.Compound(originalStatementNode.statements.clone(::replaceControlFlowWrapped))
        }

        return ShaderJob(
            originalShaderJob.tu.clone(::replaceControlFlowWrapped),
            originalShaderJob.pipelineState,
        )
    }
}
