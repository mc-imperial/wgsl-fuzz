package com.wgslfuzz.semanticspreservingtransformations

import com.wgslfuzz.analysis.ShaderStage
import com.wgslfuzz.analysis.runFunctionToShaderStageAnalysis
import com.wgslfuzz.core.AstNode
import com.wgslfuzz.core.AugmentedStatement
import com.wgslfuzz.core.ContinuingStatement
import com.wgslfuzz.core.GlobalDecl
import com.wgslfuzz.core.ParsedShaderJob
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.clone
import com.wgslfuzz.core.traverse

private typealias DeadDiscardInjections = MutableMap<Statement.Compound, Set<Int>>

private class InjectDeadDiscards(
    private val parsedShaderJob: ParsedShaderJob,
    private val fuzzerSettings: FuzzerSettings,
) {
    private val functionToShaderStage: Map<String, Set<ShaderStage>> =
        runFunctionToShaderStageAnalysis(parsedShaderJob.tu, parsedShaderJob.environment)

    private fun injectDeadDiscards(
        node: AstNode?,
        injections: DeadDiscardInjections,
    ): AstNode? =
        injections[node]?.let { injectionPoints ->
            val compound = node as Statement.Compound
            val newBody = mutableListOf<Statement>()
            compound.statements.forEachIndexed { index, statement ->
                if (index in injectionPoints) {
                    newBody.add(
                        createDeadDiscard(),
                    )
                }
                newBody.add(
                    statement.clone {
                        injectDeadDiscards(it, injections)
                    },
                )
            }
            if (compound.statements.size in injectionPoints) {
                newBody.add(
                    createDeadDiscard(),
                )
            }
            Statement.Compound(newBody)
        }

    private fun createDeadDiscard() =
        AugmentedStatement.DeadCodeFragment(
            Statement.If(
                condition =
                    generateFalseByConstructionExpression(fuzzerSettings, parsedShaderJob),
                thenBranch =
                    Statement.Compound(
                        listOf(Statement.Discard()),
                    ),
            ),
        )

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

    fun apply(): ParsedShaderJob {
        val injections: DeadDiscardInjections = mutableMapOf()
        traverse(::selectInjectionPoints, parsedShaderJob.tu, injections)
        return ParsedShaderJob(
            tu = parsedShaderJob.tu.clone { injectDeadDiscards(it, injections) },
            uniformValues = parsedShaderJob.uniformValues,
        )
    }
}

fun addDeadDiscards(
    parsedShaderJob: ParsedShaderJob,
    fuzzerSettings: FuzzerSettings,
): ParsedShaderJob =
    InjectDeadDiscards(
        parsedShaderJob,
        fuzzerSettings,
    ).apply()
