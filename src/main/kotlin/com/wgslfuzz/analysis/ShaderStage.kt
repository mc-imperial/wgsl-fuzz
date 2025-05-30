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

package com.wgslfuzz.analysis

import com.wgslfuzz.core.AstNode
import com.wgslfuzz.core.Attribute
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.GlobalDecl
import com.wgslfuzz.core.ResolvedEnvironment
import com.wgslfuzz.core.ScopeEntry
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.TranslationUnit
import com.wgslfuzz.core.traverse
import java.util.Deque
import java.util.LinkedList

enum class ShaderStage {
    VERTEX,
    FRAGMENT,
    COMPUTE,
}

/**
 * Provides a mapping from function name to the shader stages from which that function might be
 * invoked. The keys to this map are functions declared in the translation unit (i.e., builtin
 * functions are not keys).
 *
 * @param tu the translation unit to be analysed
 * @param environment the resolved environment for [tu]
 * @return a map from each function declared in [tu] to the set of [ShaderStage]s from which it
 *     can be (directly or indirectly) invoked.
 */
fun runFunctionToShaderStageAnalysis(
    tu: TranslationUnit,
    environment: ResolvedEnvironment,
): Map<String, Set<ShaderStage>> {
    // The map that will ultimately be returned.
    val result: MutableMap<String, MutableSet<ShaderStage>> = mutableMapOf()

    // The analyses uses BFS, via this work queue of functions to be processed.
    val workQueue: Deque<GlobalDecl.Function> = LinkedList()

    /**
     * Processes [node] in the context that it may have been called from the shader stages in
     * [callerShaderStages].
     */
    fun traversalAction(
        node: AstNode,
        callerShaderStages: Set<ShaderStage>,
    ) {
        // Recursively process children of this node.
        traverse(::traversalAction, node, callerShaderStages)
        // If the node is a function call (which can occur in either statement or expression form)
        // then the called function must be analysed.
        when (node) {
            is Statement.FunctionCall -> node.callee
            is Expression.FunctionCall -> node.callee
            else -> null
        }?.let { calleeName ->
            val scopeEntry = environment.globalScope.getEntry(calleeName)
            if (scopeEntry !is ScopeEntry.Function) {
                return
            }
            // Either add a new result entry for this function, or add the relevant shader stages to
            // an existing result entry for the function.
            if (calleeName in result) {
                result[calleeName]!!.addAll(callerShaderStages)
            } else {
                result[calleeName] = callerShaderStages.toMutableSet()
            }
            // Add the called function to the work queue if not already present.
            if (scopeEntry.astNode !in workQueue) {
                workQueue.addLast(scopeEntry.astNode)
            }
        }
    }

    // Initialize the work queue with all shader stage entry point functions.
    for (function in tu.globalDecls.filterIsInstance<GlobalDecl.Function>()) {
        for (attribute in function.attributes) {
            val stages = mutableSetOf<ShaderStage>()
            if (attribute is Attribute.Vertex) {
                stages.add(ShaderStage.VERTEX)
            }
            if (attribute is Attribute.Fragment) {
                stages.add(ShaderStage.FRAGMENT)
            }
            if (attribute is Attribute.Compute) {
                stages.add(ShaderStage.COMPUTE)
            }
            if (stages.isNotEmpty()) {
                result[function.name] = stages
                workQueue.addLast(function)
            }
        }
    }

    // Perform the BFS.
    while (workQueue.isNotEmpty()) {
        val function = workQueue.removeFirst()
        assert(function.name in result)
        traverse(::traversalAction, function, result[function.name]!!.toSet())
    }
    return result
}
