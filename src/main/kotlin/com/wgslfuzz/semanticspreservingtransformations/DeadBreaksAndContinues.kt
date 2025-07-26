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
import com.wgslfuzz.core.AugmentedStatement
import com.wgslfuzz.core.ContinuingStatement
import com.wgslfuzz.core.Scope
import com.wgslfuzz.core.ShaderJob
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.clone
import com.wgslfuzz.core.traverse
import java.util.LinkedList

// There is so much in common with the logic for injecting dead breaks and dead continues that this file captures it a
// common way, using a boolean to control whether breaks or continues are being injected.
//
// Although there is also a lot in common with the way returns and discards are injected, there are enough differences
// to make it unnatural to try to share major parts of code with these passes.

/**
 * An injection occurs at some offset from a compound statement. This type is used capture those compound statements
 * into which injections should occur (the map keys) and the indices at which injections should occur for each such
 * compound statement (the values).
 */
private typealias DeadBreakContinueInjections = MutableMap<Statement.Compound, Set<Int>>

private class InjectDeadBreaksContinues(
    private val shaderJob: ShaderJob,
    private val fuzzerSettings: FuzzerSettings,
    // This controls whether breaks or continues are injected
    private val injectBreaks: Boolean,
) {
    // Records relevant enclosing AST nodes that determine whether a jump is possible. This is necessary because e.g.
    // a break can only occur from inside a loop or switch, a continue can only occur from inside a loop, and neither
    // should appear at the top level of a continuing statement.
    private val enclosingConstructsStack: MutableList<AstNode> = LinkedList()

    /**
     * This is a replacement function used when cloning the AST. It returns null to indicate that no replacement is
     * required (in which case the given AST node is cloned in the standard way). It looks out for compound statements
     * that must be replaced in a special way so that they feature code injections.
     */
    private fun injectDeadJumps(
        node: AstNode?,
        injections: DeadBreakContinueInjections,
    ): AstNode? =
        // Check whether this node is a key to the injections map. If not, there is nothing to do and null can be
        // returned, but otherwise a new compound statement needs to be returned that reflects the injections.
        injections[node]?.let { indices ->
            // Because node is a key to the map, it _must_ be a compound statement.
            val compound = node as Statement.Compound
            // This represents the body of the new compound statement to be returned. It will feature clones of all
            // statements in the original compound, interspersed with injected statements.
            val newBody = mutableListOf<Statement>()
            // Iterate through all indices in the compound statement, plus one more to reflect the end of the compound
            // statement.
            for (index in 0..compound.statements.size) {
                if (index in indices) {
                    // An injection is required at this index of the original compound, so add a newly-created dead
                    // break or continue to the new body.
                    newBody.add(
                        createDeadStatement(
                            scope = shaderJob.environment.scopeAtIndex(compound, index),
                        ),
                    )
                }
                if (index < compound.statements.size) {
                    // To preserve the statements in the original compound statement, add a clone. Because the statement
                    // being cloned might feature sub-statements that require injections, the current replacement
                    // function is again used as the replacement function for cloning.
                    newBody.add(
                        compound.statements[index].clone {
                            injectDeadJumps(it, injections)
                        },
                    )
                }
            }
            Statement.Compound(newBody, compound.metadata)
        }

    private fun createDeadStatement(scope: Scope): AugmentedStatement.DeadCodeFragment {
        val deadStatement =
            Statement.Compound(
                listOf(
                    if (injectBreaks) {
                        Statement.Break()
                    } else {
                        Statement.Continue()
                    },
                ),
            )
        // TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/98) These choices are currently weighted such that there
        //  is the same probability of choosing if (false) { dead } vs. if (true) { } else { dead }. This weighting
        //  could instead be controlled via fuzzer settings.
        val choices =
            mutableListOf(
                1 to {
                    createIfFalseThenDeadStatement(
                        falseCondition = generateFalseByConstructionExpression(fuzzerSettings, shaderJob, scope),
                        deadStatement = deadStatement,
                        includeEmptyElseBranch = fuzzerSettings.randomBool(),
                    )
                },
                1 to {
                    createIfTrueElseDeadStatement(
                        trueCondition = generateTrueByConstructionExpression(fuzzerSettings, shaderJob, scope),
                        deadStatement = deadStatement,
                    )
                },
            )
        return choose(fuzzerSettings, choices)
    }

    /**
     * Traversal action function for randomly deciding where to inject.
     */
    private fun selectInjectionPoints(
        node: AstNode,
        injectionPoints: DeadBreakContinueInjections,
    ) {
        if (isRelevantEnclosingConstruct(node)) {
            // Push node on to constructs stack
            enclosingConstructsStack.addFirst(node)
        }
        traverse(::selectInjectionPoints, node, injectionPoints)
        if (isRelevantEnclosingConstruct(node)) {
            enclosingConstructsStack.removeFirst()
        }
        // Ensure that dead break / continue statements are only injected under a suitable immediately-enclosing
        // construct (e.g. a loop), which must not be a continuing statement.
        if (node is Statement.Compound &&
            enclosingConstructsStack.isNotEmpty() &&
            enclosingConstructsStack.first() !is ContinuingStatement
        ) {
            injectionPoints[node] =
                (0..node.statements.size)
                    .filter {
                        randomChoiceToInject()
                    }.toSet()
        }
    }

    private fun randomChoiceToInject(): Boolean =
        if (injectBreaks) {
            fuzzerSettings.injectDeadBreak()
        } else {
            fuzzerSettings.injectDeadContinue()
        }

    private fun isRelevantEnclosingConstruct(node: AstNode): Boolean {
        // We need to keep track of loops (of all kinds) and continuing statements for injection of both breaks and
        // continues.
        if (node is ContinuingStatement ||
            node is Statement.Loop ||
            node is Statement.For ||
            node is Statement.While
        ) {
            return true
        }
        // For breaks, switch statements are also relevant.
        if (injectBreaks && node is Statement.Switch) {
            return true
        }
        return false
    }

    fun apply(): ShaderJob {
        val injections: DeadBreakContinueInjections = mutableMapOf()
        traverse(::selectInjectionPoints, shaderJob.tu, injections)
        return ShaderJob(
            tu = shaderJob.tu.clone { injectDeadJumps(it, injections) },
            pipelineState = shaderJob.pipelineState,
        )
    }
}

fun addDeadBreaks(
    shaderJob: ShaderJob,
    fuzzerSettings: FuzzerSettings,
): ShaderJob =
    InjectDeadBreaksContinues(
        shaderJob = shaderJob,
        fuzzerSettings = fuzzerSettings,
        injectBreaks = true,
    ).apply()

fun addDeadContinues(
    shaderJob: ShaderJob,
    fuzzerSettings: FuzzerSettings,
): ShaderJob =
    InjectDeadBreaksContinues(
        shaderJob = shaderJob,
        fuzzerSettings = fuzzerSettings,
        injectBreaks = false,
    ).apply()
