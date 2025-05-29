package com.wgslfuzz.semanticspreservingtransformations

import com.wgslfuzz.core.AstNode
import com.wgslfuzz.core.AugmentedStatement
import com.wgslfuzz.core.ContinuingStatement
import com.wgslfuzz.core.ParsedShaderJob
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.clone
import com.wgslfuzz.core.traverse
import java.util.LinkedList

private typealias DeadBreakInjections = MutableMap<Statement.Compound, Set<Int>>

private class InjectDeadBreaks(
    private val parsedShaderJob: ParsedShaderJob,
    private val fuzzerSettings: FuzzerSettings,
) {
    // Records all enclosing loops (of all kinds), switch statements and continuing statements during traversal.
    // This is necessary because a break can only occur from inside a loop or switch, but should not occur from
    // inside a continuing statement (unless inside a loop or switch within that statement).
    private val enclosingConstructsStack: MutableList<AstNode> = LinkedList()

    private fun injectDeadBreaks(
        node: AstNode?,
        injections: DeadBreakInjections,
    ): AstNode? =
        injections[node]?.let { injectionPoints ->
            val compound = node as Statement.Compound
            val newBody = mutableListOf<Statement>()
            compound.statements.forEachIndexed { index, statement ->
                if (index in injectionPoints) {
                    newBody.add(
                        createDeadBreak(),
                    )
                }
                newBody.add(
                    statement.clone {
                        injectDeadBreaks(it, injections)
                    },
                )
            }
            if (compound.statements.size in injectionPoints) {
                newBody.add(
                    createDeadBreak(),
                )
            }
            Statement.Compound(newBody)
        }

    private fun createDeadBreak() =
        AugmentedStatement.DeadCodeFragment(
            Statement.If(
                condition =
                    generateFalseByConstructionExpression(fuzzerSettings, parsedShaderJob),
                thenBranch =
                    Statement.Compound(
                        listOf(Statement.Break()),
                    ),
            ),
        )

    private fun selectInjectionPoints(
        node: AstNode,
        injectionPoints: DeadBreakInjections,
    ) {
        if (node is ContinuingStatement ||
            node is Statement.Loop ||
            node is Statement.For ||
            node is Statement.While ||
            node is Statement.Switch
        ) {
            // Push node on to constructs stack
            enclosingConstructsStack.addFirst(node)
        }
        traverse(::selectInjectionPoints, node, injectionPoints)
        if (node is ContinuingStatement ||
            node is Statement.Loop ||
            node is Statement.For ||
            node is Statement.While ||
            node is Statement.Switch
        ) {
            enclosingConstructsStack.removeFirst()
        }
        if (node is Statement.Compound &&
            enclosingConstructsStack.isNotEmpty() &&
            enclosingConstructsStack.first() !is ContinuingStatement
        ) {
            injectionPoints[node] =
                (0..node.statements.size)
                    .filter {
                        fuzzerSettings.injectDeadBreak()
                    }.toSet()
        }
    }

    fun apply(): ParsedShaderJob {
        val injections: DeadBreakInjections = mutableMapOf()
        traverse(::selectInjectionPoints, parsedShaderJob.tu, injections)
        return ParsedShaderJob(
            tu = parsedShaderJob.tu.clone { injectDeadBreaks(it, injections) },
            uniformValues = parsedShaderJob.uniformValues,
        )
    }
}

fun addDeadBreaks(
    parsedShaderJob: ParsedShaderJob,
    fuzzerSettings: FuzzerSettings,
): ParsedShaderJob =
    InjectDeadBreaks(
        parsedShaderJob,
        fuzzerSettings,
    ).apply()
