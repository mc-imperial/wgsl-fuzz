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

package com.wgslfuzz.core

// This file is a suitable place for helper methods that are generally useful but that do not have a more obvious home.

/**
 * A shader module declares "uniform" variables to allow data to be passed into a pipeline invocation. Uniforms can be
 * dynamic between pipeline invocations, but are constant during a particular pipeline invocation.
 *
 * On the assumption that a translation unit declares a uniform bound to group [group] and binding [binding], this
 * function yields that associated global variable declaration.
 *
 * @receiver the translation unit being queried
 * @param group the group associated with the uniform declaration
 * @param binding the binding associated with the uniform declaration
 * @return the global variable declaration for the uniform
 * @throws [NoSuchElementException] if no matching global variable exists
 */
fun TranslationUnit.getUniformDeclaration(
    group: Int,
    binding: Int,
): GlobalDecl.Variable {
    val uniformDeclaration =
        globalDecls.filterIsInstance<GlobalDecl.Variable>().first {
            (
                it.attributes
                    .filterIsInstance<Attribute.Group>()
                    .first()
                    .expression as Expression.IntLiteral
            ).text.toInt() == group &&
                (
                    it.attributes
                        .filterIsInstance<Attribute.Binding>()
                        .first()
                        .expression as Expression.IntLiteral
                ).text.toInt() == binding
        }
    return uniformDeclaration
}

fun isStatementFunctionCallBuiltin(functionCall: Statement.FunctionCall): Boolean {
    // This set should contain the names of all builtin functions that do not have the 'must_use' attribute
    val statementFunctionCallBuiltins =
        hashSetOf(
            "atomicStore",
            "storageBarrier",
            "textureBarrier",
            "textureStore",
            "workgroupBarrier",
        )
    return functionCall.callee in statementFunctionCallBuiltins
}
