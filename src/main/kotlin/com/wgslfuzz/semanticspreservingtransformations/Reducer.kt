package com.wgslfuzz.semanticspreservingtransformations

import com.wgslfuzz.core.AstNode
import com.wgslfuzz.core.Attribute
import com.wgslfuzz.core.AugmentedStatement
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.LhsExpression
import com.wgslfuzz.core.ParsedShaderJob
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.clone
import com.wgslfuzz.core.traverse

typealias InterestingnessTest = (candidate: ParsedShaderJob) -> Boolean

private class CandidateDeadCodeFragment(
    val enclosingCompound: Statement.Compound,
    val offset: Int,
)

private fun findDeadCodeFragments(shaderJob: ParsedShaderJob): List<CandidateDeadCodeFragment> {
    fun finder(node: AstNode, fragments: MutableList<CandidateDeadCodeFragment>) {
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
    traverse(::finder, shaderJob.tu, fragments)
    return fragments
}

private fun removeDeadCodeFragments(shaderJob: ParsedShaderJob, fragments: List<CandidateDeadCodeFragment>): ParsedShaderJob {
    val fragmentsByCompound: Map<Statement.Compound, List<CandidateDeadCodeFragment>> = fragments.groupBy { it.enclosingCompound }
    ParsedShaderJob(
        shaderJob.tu.clone {
            if (it in fragmentsByCompound.keys) {
                TODO()
            } else {
                null
            }
        },
        shaderJob.uniformValues,
    )
}

private fun reduceDeadCodeFragments(shaderJob: ParsedShaderJob, interestingnessTest: InterestingnessTest): Boolean {
    var fragments = findDeadCodeFragments(shaderJob)
    if (fragments.isEmpty()) {
        return false
    }
    var granularity = fragments.size
    while (granularity > 0) {
        var offset = fragments.size - granularity
        while (offset + granularity > 0) {
            TODO()
            offset -= granularity
        }
        granularity /= 2
    }
    TODO()
}

fun reduce(shaderJob: ParsedShaderJob, interestingnessTest: InterestingnessTest) {
    var continueReducing = true
    while (continueReducing) {
        continueReducing = false
        continueReducing = continueReducing or reduceDeadCodeFragments(shaderJob, interestingnessTest)
    }
}