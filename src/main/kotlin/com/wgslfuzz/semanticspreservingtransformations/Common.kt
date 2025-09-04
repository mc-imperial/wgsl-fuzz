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
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.GlobalDecl
import com.wgslfuzz.core.LhsExpression
import com.wgslfuzz.core.ParameterDecl
import com.wgslfuzz.core.Scope
import com.wgslfuzz.core.ScopeEntry
import com.wgslfuzz.core.ShaderJob
import com.wgslfuzz.core.Statement
import com.wgslfuzz.core.StructMember
import com.wgslfuzz.core.Type
import com.wgslfuzz.core.TypeDecl
import com.wgslfuzz.core.asStoreTypeIfReference
import com.wgslfuzz.core.builtinFunctionNames
import com.wgslfuzz.core.builtinNamedTypes
import com.wgslfuzz.core.clone

const val LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE: Int = 16777216

fun Scope.containsVariableOfType(type: Type): Boolean =
    this
        .getAllEntries()
        .any {
            it is ScopeEntry.TypedDecl &&
                it !is ScopeEntry.TypeAlias &&
                it.type.asStoreTypeIfReference() == type
        }

fun ScopeEntry.TypedDecl.toExpression(): Expression =
    Expression.Identifier(
        name = declName,
    )

fun constantWithSameValueEverywhere(
    value: Int,
    type: Type,
): Expression =
    when (type) {
        is Type.Array ->
            if (type.elementCount == null) {
                throw IllegalArgumentException("Cannot generate an array of unknown length")
            } else {
                Expression.ArrayValueConstructor(
                    elementType = null,
                    elementCount = Expression.IntLiteral(type.elementCount.toString()),
                    args = List(type.elementCount) { constantWithSameValueEverywhere(value, type.elementType) },
                )
            }
        is Type.Matrix -> TODO("Matrix constants need to be supported.")
        Type.Bool ->
            if (value == 0) {
                Expression.BoolLiteral("false")
            } else {
                Expression.BoolLiteral("true")
            }
        Type.AbstractFloat -> Expression.FloatLiteral("$value.0")
        Type.F16 -> Expression.FloatLiteral("$value.0h")
        Type.F32 -> Expression.FloatLiteral("$value.0f")
        Type.AbstractInteger -> Expression.IntLiteral("$value")
        Type.I32 -> Expression.IntLiteral("${value}i")
        Type.U32 -> Expression.IntLiteral("${value}u")
        is Type.Struct ->
            Expression.StructValueConstructor(
                constructorName = type.name,
                args = type.members.map { constantWithSameValueEverywhere(value, it.second) },
            )
        is Type.Vector ->
            when (type.width) {
                2 ->
                    Expression.Vec2ValueConstructor(
                        args = (0..1).map { constantWithSameValueEverywhere(value, type.elementType) },
                    )
                3 ->
                    Expression.Vec3ValueConstructor(
                        args = (0..2).map { constantWithSameValueEverywhere(value, type.elementType) },
                    )
                4 ->
                    Expression.Vec4ValueConstructor(
                        args = (0..3).map { constantWithSameValueEverywhere(value, type.elementType) },
                    )
                else -> throw RuntimeException("Bad vector width: ${type.width}")
            }
        else -> throw UnsupportedOperationException("Constant construction not supported for type $type")
    }

fun getValueAsDoubleFromConstant(constantExpression: Expression): Double =
    when (constantExpression) {
        is Expression.FloatLiteral -> constantExpression.text.trimEnd('f', 'h').toDouble()
        is Expression.IntLiteral -> constantExpression.text.trimEnd('i', 'u').toDouble()
        else -> throw UnsupportedOperationException("Cannot get numeric value from $constantExpression")
    }

fun ShaderJob.renameEverything(fuzzerSettings: FuzzerSettings): ShaderJob {
    val oldNamesToNewNames = mutableMapOf<String, String>()

    fun getNewName(name: String): String = oldNamesToNewNames.getOrPut(name, { "${name}_${fuzzerSettings.getUniqueId()}" })

    fun rename(node: AstNode): AstNode? =
        when (node) {
            is Expression.FunctionCall ->
                Expression.FunctionCall(
                    callee = if (node.callee in builtinFunctionNames) node.callee else getNewName(node.callee),
                    templateParameter = node.templateParameter?.clone(::rename),
                    args = node.args.clone(::rename),
                )
            is Expression.Identifier ->
                Expression.Identifier(
                    name = getNewName(node.name),
                )
            is Expression.MemberLookup ->
                Expression.MemberLookup(
                    receiver = node.receiver.clone(::rename),
                    memberName =
                        if (this.environment.typeOf(node.receiver) is Type.Vector) {
                            node.memberName
                        } else {
                            getNewName(node.memberName)
                        },
                )
            is Expression.StructValueConstructor ->
                Expression.StructValueConstructor(
                    constructorName = getNewName(node.constructorName),
                    args = node.args.clone(::rename),
                )
            is Expression.TypeAliasValueConstructor ->
                Expression.TypeAliasValueConstructor(
                    constructorName = getNewName(node.constructorName),
                    args = node.args.clone(::rename),
                )
            is GlobalDecl.Constant ->
                GlobalDecl.Constant(
                    name = getNewName(node.name),
                    typeDecl = node.typeDecl?.clone(::rename),
                    initializer = node.initializer.clone(::rename),
                )
            is GlobalDecl.Function ->
                GlobalDecl.Function(
                    attributes = node.attributes.clone(::rename),
                    name = if (node.name in builtinFunctionNames) node.name else getNewName(node.name),
                    parameters = node.parameters.clone(::rename),
                    returnAttributes = node.returnAttributes.clone(::rename),
                    returnType = node.returnType?.clone(::rename),
                    body = node.body.clone(::rename),
                )
            is GlobalDecl.Override ->
                GlobalDecl.Override(
                    attributes = node.attributes.clone(::rename),
                    name = getNewName(node.name),
                    typeDecl = node.typeDecl?.clone(::rename),
                    initializer = node.initializer?.clone(::rename),
                )
            is GlobalDecl.Struct ->
                GlobalDecl.Struct(
                    name = getNewName(node.name),
                    members = node.members.clone(::rename),
                )
            is GlobalDecl.TypeAlias ->
                GlobalDecl.TypeAlias(
                    name = getNewName(node.name),
                    typeDecl = node.typeDecl.clone(::rename),
                )
            is GlobalDecl.Variable ->
                GlobalDecl.Variable(
                    attributes = node.attributes.clone(::rename),
                    name = getNewName(node.name),
                    addressSpace = node.addressSpace,
                    accessMode = node.accessMode,
                    typeDecl = node.typeDecl?.clone(::rename),
                    initializer = node.initializer?.clone(::rename),
                )
            is LhsExpression.Identifier ->
                LhsExpression.Identifier(
                    name = getNewName(node.name),
                )
            is LhsExpression.MemberLookup ->
                LhsExpression.MemberLookup(
                    receiver = node.receiver.clone(::rename),
                    memberName = getNewName(node.memberName),
                )
            is ParameterDecl ->
                ParameterDecl(
                    attributes = node.attributes.clone(::rename),
                    name = getNewName(node.name),
                    typeDecl = node.typeDecl.clone(::rename),
                )
            is Statement.Value ->
                Statement.Value(
                    isConst = node.isConst,
                    name = getNewName(node.name),
                    typeDecl = node.typeDecl?.clone(::rename),
                    initializer = node.initializer.clone(::rename),
                )
            is Statement.Variable ->
                Statement.Variable(
                    name = getNewName(node.name),
                    addressSpace = node.addressSpace,
                    accessMode = node.accessMode,
                    typeDecl = node.typeDecl?.clone(::rename),
                    initializer = node.initializer?.clone(::rename),
                )
            is StructMember ->
                StructMember(
                    attributes = node.attributes.clone(::rename),
                    name = getNewName(node.name),
                    typeDecl = node.typeDecl.clone(::rename),
                )
            is TypeDecl.NamedType ->
                TypeDecl.NamedType(
                    name = if (node.name in builtinNamedTypes) node.name else getNewName(node.name),
                )
            else -> null
        }

    val newTu = this.tu.clone(::rename)

    return ShaderJob(
        tu = newTu,
        pipelineState = this.pipelineState,
    )
}
