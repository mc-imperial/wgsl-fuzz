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

package com.wgslfuzz.uniformityanalysis

import com.wgslfuzz.core.AstNode
import com.wgslfuzz.core.Attribute
import com.wgslfuzz.core.BinaryOperator
import com.wgslfuzz.core.BuiltinValue
import com.wgslfuzz.core.ContinuingStatement
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.GlobalDecl
import com.wgslfuzz.core.LhsExpression
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.TranslationUnit
import com.wgslfuzz.core.TypeDecl
import com.wgslfuzz.core.traverse

fun checkProgramIsFeatherweight(translationUnit: TranslationUnit) {
    fun featherweightCheck(
        node: AstNode,
        noState: Unit,
    ) {
        traverse(::featherweightCheck, node, noState)
        when (node) {
            is ContinuingStatement -> {}
            is Expression.Binary -> {
                when (node.operator) {
                    BinaryOperator.SHORT_CIRCUIT_OR, BinaryOperator.SHORT_CIRCUIT_AND ->
                        check(
                            false,
                            { "Short circuit operators are not supported." },
                        )
                    else -> {}
                }
            }
            is Expression.BoolLiteral -> {}
            is Expression.FloatLiteral -> {}
            is Expression.Identifier -> {}
            is Expression.IntLiteral -> {}
            is Expression.Paren -> {}
            is Expression.Unary -> {}
            is LhsExpression.Identifier -> {}
            is Statement.Break -> {}
            is Statement.Continue -> {}
            is Statement.Compound -> {}
            is Statement.If -> {}
            is Statement.Empty -> {}
            is Statement.Assignment -> {}
            is Statement.FunctionCall -> {}
            is Statement.Loop -> {}
            is Statement.Return -> {}
            else -> check(false, { "Unsupported kind of AST node." })
        }
    }

    check(translationUnit.directives.isEmpty())
    for (functionDecl in translationUnit.globalDecls) {
        check(functionDecl is GlobalDecl.Function)
        check(functionDecl.returnAttributes.isEmpty())
        if (functionDecl.name == "main") {
            check(functionDecl.attributes.size == 2) { "Incorrect number of attributes on 'main'" }
            check(functionDecl.attributes[0] is Attribute.Compute) { "First 'main' attribute must be @compute" }
            check(functionDecl.attributes[1] is Attribute.WorkgroupSize) { "Second 'main' attribute must be workgroup size" }
            check(functionDecl.returnType == null)
            check(functionDecl.parameters.size == 1)
            val parameterDecl = functionDecl.parameters[0]
            check(parameterDecl.name == "lid")
            check(parameterDecl.attributes.size == 1)
            val builtinAttribute = parameterDecl.attributes[0]
            check(builtinAttribute is Attribute.Builtin)
            check(builtinAttribute.name == BuiltinValue.LOCAL_INVOCATION_INDEX)
            check(parameterDecl.typeDecl is TypeDecl.U32)
        } else {
            check(functionDecl.attributes.isEmpty())
            check(functionDecl.returnAttributes.isEmpty())
            check(functionDecl.returnType is TypeDecl.ScalarTypeDecl, { "Function return type must be scalar." })
            for (parameter in functionDecl.parameters) {
                check(parameter.attributes.isEmpty())
                check(parameter.typeDecl is TypeDecl.ScalarTypeDecl)
            }
        }
        val bodyStatements = functionDecl.body.statements
        val localVariableDecls =
            bodyStatements.takeWhile {
                it is Statement.Variable
            }
        for (localVariableDecl in localVariableDecls) {
            check(localVariableDecl is Statement.Variable)
            check(localVariableDecl.typeDecl is TypeDecl.ScalarTypeDecl) { "All type declarations must be scalar" }
            check(localVariableDecl.addressSpace == null)
            check(localVariableDecl.accessMode == null)
            check(localVariableDecl.initializer == null)
        }
        val remainderOfBody =
            bodyStatements.dropWhile {
                it is Statement.Variable
            }
        remainderOfBody.forEach {
            featherweightCheck(it, Unit)
        }
    }
}
