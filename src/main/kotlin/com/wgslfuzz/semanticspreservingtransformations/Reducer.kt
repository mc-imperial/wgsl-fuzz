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
import com.wgslfuzz.core.AugmentedStatement
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.LhsExpression
import com.wgslfuzz.core.ParsedShaderJob
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.clone
import com.wgslfuzz.core.nodesPreOrder
import com.wgslfuzz.core.traverse
import kotlin.math.min

typealias InterestingnessTest = (candidate: ParsedShaderJob) -> Boolean

abstract class ReductionPass<ReductionOpportunityT> {
    abstract fun findOpportunities(originalShaderJob: ParsedShaderJob): List<ReductionOpportunityT>

    abstract fun removeOpportunities(
        originalShaderJob: ParsedShaderJob,
        opportunities: List<ReductionOpportunityT>,
    ): ParsedShaderJob

    fun run(
        originalShaderJob: ParsedShaderJob,
        interestingnessTest: InterestingnessTest,
    ): Pair<ParsedShaderJob, Boolean> {
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
                val candidateReducedShaderJob = removeOpportunities(bestSoFar, fragments.slice(offset..<offset + granularity))
                if (interestingnessTest(candidateReducedShaderJob)) {
                    bestSoFar = candidateReducedShaderJob
                    progressMade = true
                    fragments = findOpportunities(bestSoFar)
                    granularity = min(granularity, fragments.size)
                } else {
                    offset -= granularity
                }
            }
            granularity /= 2
        }
        return Pair(bestSoFar, progressMade)
    }
}

fun ParsedShaderJob.reduce(interestingnessTest: InterestingnessTest): Pair<ParsedShaderJob, Boolean> {
    val passes: List<ReductionPass<*>> =
        listOf(
            ReduceDeadCodeFragments(),
            UndoIdentityOperations(),
            ReplaceKnownValues(),
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
    override fun findOpportunities(originalShaderJob: ParsedShaderJob): List<CandidateDeadCodeFragment> {
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
        originalShaderJob: ParsedShaderJob,
        opportunities: List<CandidateDeadCodeFragment>,
    ): ParsedShaderJob {
        val fragmentsByCompound: Map<Statement.Compound, List<CandidateDeadCodeFragment>> = opportunities.groupBy { it.enclosingCompound }
        return ParsedShaderJob(
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
    override fun findOpportunities(originalShaderJob: ParsedShaderJob): List<AugmentedExpression.IdentityOperation> =
        nodesPreOrder(originalShaderJob.tu).filterIsInstance<AugmentedExpression.IdentityOperation>()

    override fun removeOpportunities(
        originalShaderJob: ParsedShaderJob,
        opportunities: List<AugmentedExpression.IdentityOperation>,
    ): ParsedShaderJob {
        val opportunitiesAsSet = opportunities.toSet()

        fun undoIdentityOperations(node: AstNode): AstNode? {
            if (node !is AugmentedExpression.IdentityOperation || node !in opportunitiesAsSet) {
                return null
            }
            return node.originalExpression.clone(::undoIdentityOperations)
        }
        return ParsedShaderJob(
            originalShaderJob.tu.clone(::undoIdentityOperations),
            originalShaderJob.pipelineState,
        )
    }
}

private class ReplaceKnownValues : ReductionPass<AugmentedExpression.KnownValue>() {
    override fun findOpportunities(originalShaderJob: ParsedShaderJob): List<AugmentedExpression.KnownValue> =
        nodesPreOrder(originalShaderJob.tu).filterIsInstance<AugmentedExpression.KnownValue>()

    override fun removeOpportunities(
        originalShaderJob: ParsedShaderJob,
        opportunities: List<AugmentedExpression.KnownValue>,
    ): ParsedShaderJob {
        val opportunitiesAsSet = opportunities.toSet()

        fun replaceKnownValue(node: AstNode): AstNode? {
            if (node !is AugmentedExpression.KnownValue || node !in opportunitiesAsSet) {
                return null
            }
            return node.knownValue.clone(::replaceKnownValue)
        }
        return ParsedShaderJob(
            originalShaderJob.tu.clone(::replaceKnownValue),
            originalShaderJob.pipelineState,
        )
    }
}
