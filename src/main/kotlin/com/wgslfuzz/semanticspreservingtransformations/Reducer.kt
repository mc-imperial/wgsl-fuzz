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

import com.wgslfuzz.core.AddedIdentifier
import com.wgslfuzz.core.AstNode
import com.wgslfuzz.core.AugmentedMetadata
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.GlobalDecl
import com.wgslfuzz.core.LhsExpression
import com.wgslfuzz.core.ParameterDecl
import com.wgslfuzz.core.ReverseResult
import com.wgslfuzz.core.ShaderJob
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.StructMember
import com.wgslfuzz.core.TranslationUnit
import com.wgslfuzz.core.clone
import com.wgslfuzz.core.cloneWithoutReplacementOnFirstNode
import com.wgslfuzz.core.map
import com.wgslfuzz.core.nodesPreOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

typealias InterestingnessTest = (candidate: ShaderJob) -> Boolean

abstract class ReductionPass<ReductionOpportunityT> {
    abstract fun findOpportunities(originalShaderJob: ShaderJob): List<ReductionOpportunityT>

    abstract fun removeOpportunities(
        originalShaderJob: ShaderJob,
        opportunities: List<ReductionOpportunityT>,
    ): ShaderJob

    /**
     * Runs the reduction pass on [originalInterestingShaderJob] using the given [interestingnessTest]
     *
     * @return Returns a pair whose first component is the simplest interesting shader job that was encountered; this
     *  will simply be [originalInterestingShaderJob] if no reduction attempt from this pass succeeds. The second
     *  component of the pair is the closest non-interesting shader job that is simpler than the last interesting shader
     *  job to be encountered. This component will be null if the last thing the pass tried turned out to be
     *  interesting.
     */
    fun run(
        originalInterestingShaderJob: ShaderJob,
        originalSimplerButNotInterestingShaderJob: ShaderJob?,
        interestingnessTest: InterestingnessTest,
    ): Pair<ShaderJob, ShaderJob?> {
        var fragments = findOpportunities(originalInterestingShaderJob)
        if (fragments.isEmpty()) {
            return Pair(originalInterestingShaderJob, originalSimplerButNotInterestingShaderJob)
        }
        var bestSoFar = originalInterestingShaderJob
        var simplerButNotInteresting: ShaderJob? = originalSimplerButNotInterestingShaderJob

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
                    simplerButNotInteresting = null
                    fragments = findOpportunities(bestSoFar)
                    granularity = min(granularity, fragments.size)
                    offset = min(offset, fragments.size - granularity)
                } else {
                    if (simplerButNotInteresting == null ||
                        nodeSizeDelta(bestSoFar, candidateReducedShaderJob) < nodeSizeDelta(bestSoFar, simplerButNotInteresting)
                    ) {
                        simplerButNotInteresting = candidateReducedShaderJob
                    }
                    offset -= granularity
                }
            }
            granularity /= 2
        }
        return Pair(bestSoFar, simplerButNotInteresting)
    }
}

/**
 * Reduces [this] using the given [interestingnessTest]
 *
 * @return null if the reduction had no effect -- i.e., no reduction attempt was interesting.
 *  Otherwise, returns a pair whose first component is the simplest shader job that was encountered.
 *  If a simpler but not interesting shader job was also encountered it is returned as the second component; this
 *  component will be null if the very last reducing transformation tried during the reduction process turned out to be
 *  interesting.
 */
fun ShaderJob.reduce(interestingnessTest: InterestingnessTest): Pair<ShaderJob, ShaderJob?>? {
    val passes: List<ReductionPass<*>> =
        listOf(
            UndoTransformations(),
            RemoveAddedIdentifiers(),
        )
    var reducedShaderJob = this
    var simplerButNotInterestingShaderJob: ShaderJob? = null
    var somePassWorkedOnLastRound = true
    while (somePassWorkedOnLastRound) {
        somePassWorkedOnLastRound = false
        for (reductionPass in passes) {
            val (newReducedShaderJob, newSimplerButNotInterestingShaderJob) =
                reductionPass.run(
                    originalInterestingShaderJob = reducedShaderJob,
                    originalSimplerButNotInterestingShaderJob = simplerButNotInterestingShaderJob,
                    interestingnessTest,
                )
            simplerButNotInterestingShaderJob = newSimplerButNotInterestingShaderJob
            if (newReducedShaderJob !== reducedShaderJob) {
                somePassWorkedOnLastRound = true
                reducedShaderJob = newReducedShaderJob
                simplerButNotInterestingShaderJob = newSimplerButNotInterestingShaderJob
            }
        }
    }
    if (reducedShaderJob === this) {
        return null
    }
    return Pair(reducedShaderJob, second = simplerButNotInterestingShaderJob)
}

private class UndoTransformations : ReductionPass<Int>() {
    override fun findOpportunities(originalShaderJob: ShaderJob): List<Int> =
        nodesPreOrder(originalShaderJob.tu)
            .flatMap { node ->
                node.metadata.mapNotNull { metadata -> (metadata as? AugmentedMetadata)?.id }
            }.distinct()

    override fun removeOpportunities(
        originalShaderJob: ShaderJob,
        opportunities: List<Int>,
    ): ShaderJob {
        val opportunitiesAsSet = opportunities.toSet()

        fun reverseResultToClone(result: ReverseResult?): AstNode? =
            result?.let {
                require(it is ReverseResult.ReversedNode) { "Only ReversedNode can be convert to clone replacement output" }
                it.node
            }

        fun undoTransformations(node: AstNode): ReverseResult? =
            if (node.metadata.filterIsInstance<AugmentedMetadata>().any { it.id in opportunitiesAsSet }) {
                node.metadata
                    .filterIsInstance<AugmentedMetadata>()
                    .first { it.id in opportunitiesAsSet }
                    .reverse(node)
                    .map { node -> node.clone { reverseResultToClone(undoTransformations(it)) } }
            } else if (node is Statement.Compound) {
                ReverseResult.ReversedNode(
                    Statement.Compound(
                        statements =
                            node.statements.flatMap { statement ->
                                val clonedNode =
                                    when (val result = undoTransformations(statement)) {
                                        // Any transformations undone in a compound will return a compound
                                        ReverseResult.DeletedNode -> null
                                        is ReverseResult.ReversedNode -> result.node as Statement
                                        null ->
                                            statement.cloneWithoutReplacementOnFirstNode {
                                                reverseResultToClone(undoTransformations(it))
                                            }
                                    }

                                if (clonedNode is Statement.Compound) {
                                    clonedNode.statements
                                } else if (clonedNode != null) {
                                    listOf(clonedNode)
                                } else {
                                    emptyList()
                                }
                            },
                        metadata = node.metadata,
                    ),
                )
            } else {
                null
            }

        val opportunitiesRemovedTu = originalShaderJob.tu.clone { reverseResultToClone(undoTransformations(it)) }

        val transformationsStillInTu =
            nodesPreOrder(opportunitiesRemovedTu)
                .flatMap { node ->
                    node.metadata.mapNotNull { metadata -> (metadata as? AugmentedMetadata)?.id }
                }.distinct()

        check((transformationsStillInTu intersect opportunitiesAsSet).isEmpty())

        return ShaderJob(
            opportunitiesRemovedTu,
            originalShaderJob.pipelineState,
        )
    }
}

private class RemoveAddedIdentifiers : ReductionPass<String>() {
    override fun findOpportunities(originalShaderJob: ShaderJob): List<String> =
        getAllIdentifiersCreated(originalShaderJob.tu) { node ->
            node.metadata.any {
                it is AddedIdentifier
            }
        }.minus(getAllIdentifiersUsed(originalShaderJob.tu).toSet())

    override fun removeOpportunities(
        originalShaderJob: ShaderJob,
        opportunities: List<String>,
    ): ShaderJob {
        val opportunitiesAsSet = opportunities.toSet()

        // TODO(Not implemented for anything other GlobalDecl.Function removal)

        val newGlobalDecls =
            originalShaderJob.tu.globalDecls.filter {
                !(it is GlobalDecl.Function && it.name in opportunitiesAsSet)
            }

        return ShaderJob(
            tu =
                TranslationUnit(
                    directives = originalShaderJob.tu.directives,
                    globalDecls = newGlobalDecls,
                    metadata = originalShaderJob.tu.metadata,
                ),
            pipelineState = originalShaderJob.pipelineState,
        )
    }

    private fun getAllIdentifiersCreated(
        tu: AstNode,
        predicate: (AstNode) -> Boolean = { true },
    ): List<String> =
        nodesPreOrder(tu)
            .filter(predicate)
            .mapNotNull {
                when (it) {
                    is GlobalDecl.Constant -> it.name
                    is GlobalDecl.Function -> it.name
                    is GlobalDecl.Override -> it.name
                    is GlobalDecl.Struct -> it.name
                    is GlobalDecl.TypeAlias -> it.name
                    is GlobalDecl.Variable -> it.name
                    is ParameterDecl -> it.name
                    is Statement.Value -> it.name
                    is Statement.Variable -> it.name
                    is StructMember -> it.name
                    else -> null
                }
            }.distinct()

    private fun getAllIdentifiersUsed(
        tu: AstNode,
        predicate: (AstNode) -> Boolean = { true },
    ): List<String> =
        nodesPreOrder(tu)
            .filter(predicate)
            .mapNotNull {
                when (it) {
                    is Expression.FunctionCall -> it.callee
                    is Expression.Identifier -> it.name
                    is LhsExpression.Identifier -> it.name
                    else -> null
                }
            }.distinct()
}

private fun nodeSizeDelta(
    shaderJob1: ShaderJob,
    shaderJob2: ShaderJob,
): Int = abs(nodesPreOrder(shaderJob1.tu).size - nodesPreOrder(shaderJob2.tu).size)
