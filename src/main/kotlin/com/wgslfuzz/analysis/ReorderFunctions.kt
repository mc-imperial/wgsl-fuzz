package com.wgslfuzz.analysis

import com.wgslfuzz.core.AstNode
import com.wgslfuzz.core.AugmentedStatement
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.GlobalDecl
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.TranslationUnit
import com.wgslfuzz.core.traverse

private fun functionCallMap(tu: TranslationUnit): Map<String, Set<String>> {
    val result = mutableMapOf<String, Set<String>>()

    fun calledFunctions(node: AstNode, mutableSet: MutableSet<String>) = when (node) {
        is Statement.FunctionCall -> {
            mutableSet.add(node.callee)
        }
        is Expression.FunctionCall -> {
            mutableSet.add(node.callee)
        }
        else -> traverse(::calledFunctions, node, mutableSet)
    }


    for (globalDecl in tu.globalDecls) {
        if (globalDecl is GlobalDecl.Function) {
            val functionCalls = mutableSetOf<String>()
            traverse(::calledFunctions, globalDecl, functionCalls)
            result[globalDecl.name] = functionCalls.toSet()
        }
    }

    return result
}

fun TranslationUnit.reorderFunctions(): TranslationUnit {
    val functionCallMap = functionCallMap(this)
    val functionComparator = Comparator { node1: GlobalDecl, node2: GlobalDecl ->
        if (node1 is GlobalDecl.Function && node2 is GlobalDecl.Function) {
            if (functionCallMap[node1.name]!!.contains(node2.name)) 1 else -1
        } else {
            0
        }
    }
    return TranslationUnit(this.directives,this.globalDecls.sortedWith(functionComparator))
}