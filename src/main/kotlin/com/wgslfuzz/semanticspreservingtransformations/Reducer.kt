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
import com.wgslfuzz.core.Type
import com.wgslfuzz.core.clone
import com.wgslfuzz.core.nodesPreOrder
import com.wgslfuzz.core.traverse
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
            ReduceDeadCodeFragments(),
            UndoIdentityOperations(),
            ReplaceKnownValues(),
            ReduceControlFlowWrapped(),
            ReduceArbitraryExpression(),
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
                    Statement.Compound(newStatements, node.metadata)
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
                val flattenedStatements =
                    node.statements.clone(::replaceControlFlowWrapped).flatMap {
                        if (it is Statement.Compound) {
                            assert(it.metadata == null)
                            it.statements
                        } else {
                            listOf(it)
                        }
                    }

                // Remove ControlFlowReturns if possible
                val wrappedReturnWithoutControlFlowWrapper =
                    flattenedStatements
                        .filterIsInstance<AugmentedStatement.ControlFlowWrapReturn>()
                        .filter { wrappedReturn ->
                            val uniqueId = wrappedReturn.id

                            !flattenedStatements.any { it is AugmentedStatement.ControlFlowWrapper && it.id == uniqueId }
                        }
                val statementsWithReturnsRemoved = flattenedStatements.filter { it !in wrappedReturnWithoutControlFlowWrapper }

                return Statement.Compound(
                    statements = statementsWithReturnsRemoved,
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

/**
 * ReduceArbitraryExpression cannot fully remove arbitrary expressions but it can make them smaller
 */
private class ReduceArbitraryExpression : ReductionPass<AugmentedExpression.ArbitraryExpression>() {
    override fun findOpportunities(originalShaderJob: ShaderJob): List<AugmentedExpression.ArbitraryExpression> =
        nodesPreOrder(originalShaderJob.tu).filterIsInstance<AugmentedExpression.ArbitraryExpression>()

    override fun removeOpportunities(
        originalShaderJob: ShaderJob,
        opportunities: List<AugmentedExpression.ArbitraryExpression>,
    ): ShaderJob {
        val opportunitiesAsSet = opportunities.toSet()

        fun removeArbitraryExpression(node: AstNode): AstNode? =
            if (node !is AugmentedExpression.ArbitraryExpression || node !in opportunitiesAsSet) {
                null
            } else {
                val underlyingExpression = node.expression
                val underlyingExpressionType = originalShaderJob.environment.typeOf(underlyingExpression)

                when (underlyingExpression) {
                    is Expression.BoolLiteral,
                    is Expression.FloatLiteral,
                    is Expression.IntLiteral,
                    is Expression.Identifier,
                    is Expression.IndexLookup,
                    is Expression.MemberLookup,
                    is Expression.ArrayValueConstructor,
                    is Expression.Mat2x2ValueConstructor,
                    is Expression.Mat2x3ValueConstructor,
                    is Expression.Mat2x4ValueConstructor,
                    is Expression.Mat3x2ValueConstructor,
                    is Expression.Mat3x3ValueConstructor,
                    is Expression.Mat3x4ValueConstructor,
                    is Expression.Mat4x2ValueConstructor,
                    is Expression.Mat4x3ValueConstructor,
                    is Expression.Mat4x4ValueConstructor,
                    is Expression.BoolValueConstructor,
                    is Expression.F16ValueConstructor,
                    is Expression.F32ValueConstructor,
                    is Expression.I32ValueConstructor,
                    is Expression.U32ValueConstructor,
                    is Expression.StructValueConstructor,
                    is Expression.TypeAliasValueConstructor,
                    is Expression.Vec2ValueConstructor,
                    is Expression.Vec3ValueConstructor,
                    is Expression.Vec4ValueConstructor,
                    -> constantWithSameValueEverywhere(value = 1, type = underlyingExpressionType)

                    is Expression.Binary ->
                        when (underlyingExpressionType) {
                            originalShaderJob.environment.typeOf(underlyingExpression.lhs) ->
                                underlyingExpression.lhs

                            originalShaderJob.environment.typeOf(underlyingExpression.rhs) ->
                                underlyingExpression.rhs

                            else -> constantWithSameValueEverywhere(value = 1, type = underlyingExpressionType)
                        }

                    is Expression.Unary ->
                        if (originalShaderJob.environment.typeOf(underlyingExpression.target) == underlyingExpressionType) {
                            underlyingExpression.target
                        } else {
                            constantWithSameValueEverywhere(
                                1,
                                underlyingExpressionType,
                            )
                        }

                    is Expression.FunctionCall ->
                        underlyingExpression.args.firstOrNull {
                            originalShaderJob.environment.typeOf(it) == underlyingExpressionType
                        } ?: constantWithSameValueEverywhere(1, underlyingExpressionType)

                    is AugmentedExpression.ArbitraryExpression -> throw IllegalStateException(
                        "An arbitrary expression should not wrap another arbitrary expression",
                    )
                    is Expression.Paren -> throw IllegalStateException(
                        "An arbitrary expression does not wrap a parentheses",
                    )

                    is AugmentedExpression.AddZero -> TODO()
                    is AugmentedExpression.DivOne -> TODO()
                    is AugmentedExpression.MulOne -> TODO()
                    is AugmentedExpression.SubZero -> TODO()
                    is AugmentedExpression.KnownValue -> TODO()
                }
            }

        return ShaderJob(
            originalShaderJob.tu.clone(::removeArbitraryExpression),
            originalShaderJob.pipelineState,
        )
    }
}

private fun nodeSizeDelta(
    shaderJob1: ShaderJob,
    shaderJob2: ShaderJob,
): Int = abs(nodesPreOrder(shaderJob1.tu).size - nodesPreOrder(shaderJob2.tu).size)
