package com.wgslfuzz.analysis

import com.wgslfuzz.core.AstNode
import com.wgslfuzz.core.AttributeKind
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

fun runFunctionToShaderStageAnalysis(
    tu: TranslationUnit,
    environment: ResolvedEnvironment,
): Map<String, Set<ShaderStage>> {
    val result: MutableMap<String, MutableSet<ShaderStage>> = mutableMapOf()
    val workQueue: Deque<GlobalDecl.Function> = LinkedList()

    fun traversalAction(
        node: AstNode,
        callerShaderStages: Set<ShaderStage>,
    ) {
        traverse(::traversalAction, node, callerShaderStages)
        when (node) {
            is Statement.FunctionCall -> node.callee
            is Expression.FunctionCall -> node.callee
            else -> null
        }?.let { calleeName ->
            val scopeEntry = environment.globalScope.getEntry(calleeName)
            if (scopeEntry !is ScopeEntry.Function) {
                return
            }
            if (calleeName in result) {
                result[calleeName]!!.addAll(callerShaderStages)
            } else {
                result[calleeName] = callerShaderStages.toMutableSet()
            }
            if (scopeEntry.astNode !in workQueue) {
                workQueue.addLast(scopeEntry.astNode)
            }
        }
    }

    for (function in tu.globalDecls.filterIsInstance<GlobalDecl.Function>()) {
        for (attribute in function.attributes) {
            val stages = mutableSetOf<ShaderStage>()
            if (attribute.kind == AttributeKind.VERTEX) {
                stages.add(ShaderStage.VERTEX)
            }
            if (attribute.kind == AttributeKind.FRAGMENT) {
                stages.add(ShaderStage.FRAGMENT)
            }
            if (attribute.kind == AttributeKind.COMPUTE) {
                stages.add(ShaderStage.COMPUTE)
            }
            if (stages.isNotEmpty()) {
                result[function.name] = stages
                workQueue.addLast(function)
            }
        }
    }

    while (workQueue.isNotEmpty()) {
        val function = workQueue.removeFirst()
        assert(function.name in result)
        traverse(::traversalAction, function, result[function.name]!!.toSet())
    }
    return result
}
