package com.wgslfuzz.semanticspreservingtransformations

import com.wgslfuzz.core.AstNode
import com.wgslfuzz.core.AugmentedStatement
import com.wgslfuzz.core.ContinuingStatement
import com.wgslfuzz.core.GlobalDecl
import com.wgslfuzz.core.ParsedShaderJob
import com.wgslfuzz.core.ScopeEntry
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.Type
import com.wgslfuzz.core.clone
import com.wgslfuzz.core.traverse

private typealias DeadReturnInjections = MutableMap<Statement.Compound, Set<Int>>

private class InjectDeadReturns(
    private val parsedShaderJob: ParsedShaderJob,
    private val fuzzerSettings: FuzzerSettings,
) {
    private var enclosingFunctionReturnType: Type? = null

    private fun injectDeadReturns(
        node: AstNode?,
        injections: DeadReturnInjections,
    ): AstNode? =
        if (node is GlobalDecl.Function) {
            assert(enclosingFunctionReturnType == null)
            enclosingFunctionReturnType =
                (parsedShaderJob.environment.globalScope.getEntry(node.name) as ScopeEntry.Function).type.returnType
            val result =
                GlobalDecl.Function(
                    attributes = node.attributes,
                    name = node.name,
                    parameters = node.parameters,
                    returnAttributes = node.returnAttributes,
                    returnType = node.returnType,
                    body = node.body.clone { injectDeadReturns(it, injections) },
                )
            enclosingFunctionReturnType = null
            result
        } else {
            injections[node]?.let { injectionPoints ->
                val compound = node as Statement.Compound
                val newBody = mutableListOf<Statement>()
                compound.statements.forEachIndexed { index, statement ->
                    if (index in injectionPoints) {
                        newBody.add(
                            createDeadReturn(),
                        )
                    }
                    newBody.add(
                        statement.clone {
                            injectDeadReturns(it, injections)
                        },
                    )
                }
                if (compound.statements.size in injectionPoints) {
                    newBody.add(
                        createDeadReturn(),
                    )
                }
                Statement.Compound(newBody)
            }
        }

    private fun createDeadReturn(): AugmentedStatement.DeadCodeFragment =
        AugmentedStatement.DeadCodeFragment(
            Statement.If(
                condition =
                    generateFalseByConstructionExpression(fuzzerSettings, parsedShaderJob),
                thenBranch =
                    Statement.Compound(
                        listOf(
                            Statement.Return(
                                enclosingFunctionReturnType?.let {
                                    generateArbitraryExpression(
                                        depth = 0,
                                        type = it,
                                        sideEffectsAllowed = true,
                                        fuzzerSettings = fuzzerSettings,
                                        parsedShaderJob = parsedShaderJob,
                                    )
                                },
                            ),
                        ),
                    ),
            ),
        )

    private fun selectInjectionPoints(
        node: AstNode,
        injectionPoints: DeadReturnInjections,
    ) {
        if (node is ContinuingStatement) {
            // A return is not allowed from inside a continuing statement, so cut off traversal.
            return
        }
        traverse(::selectInjectionPoints, node, injectionPoints)
        if (node is Statement.Compound) {
            injectionPoints[node] =
                (0..node.statements.size)
                    .filter {
                        fuzzerSettings.injectDeadReturn()
                    }.toSet()
        }
    }

    fun apply(): ParsedShaderJob {
        val injections: DeadReturnInjections = mutableMapOf()
        traverse(::selectInjectionPoints, parsedShaderJob.tu, injections)
        return ParsedShaderJob(
            tu = parsedShaderJob.tu.clone { injectDeadReturns(it, injections) },
            uniformValues = parsedShaderJob.uniformValues,
        )
    }
}

fun addDeadReturns(
    parsedShaderJob: ParsedShaderJob,
    fuzzerSettings: FuzzerSettings,
): ParsedShaderJob =
    InjectDeadReturns(
        parsedShaderJob,
        fuzzerSettings,
    ).apply()
