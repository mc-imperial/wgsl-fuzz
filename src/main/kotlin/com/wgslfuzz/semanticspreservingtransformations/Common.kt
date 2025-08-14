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

import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.ResolvedEnvironment
import com.wgslfuzz.core.Scope
import com.wgslfuzz.core.ScopeEntry
import com.wgslfuzz.core.Type
import com.wgslfuzz.core.TypeDecl
import com.wgslfuzz.core.asStoreTypeIfReference
import com.wgslfuzz.core.evaluateToInt

const val LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE: Int = 16777216

fun isVariableOfTypeInScope(
    scope: Scope,
    type: Type,
): Boolean = scope.getAllEntries().any { it.variableOfTypeInScopeEntry(type) }

fun randomVariableFromScope(
    scope: Scope,
    type: Type,
    fuzzerSettings: FuzzerSettings,
): Expression? {
    val scopeEntries = scope.getAllEntries().filter { it.variableOfTypeInScopeEntry(type) }

    if (scopeEntries.isEmpty()) return null

    val scopeEntry = fuzzerSettings.randomElement(scopeEntries) as ScopeEntry.TypedDecl

    val scopeEntryIdentifierExpression =
        Expression.Identifier(
            name = scopeEntry.declName,
        )

    return getRandomExpressionOfType(scopeEntryIdentifierExpression, scopeEntry.type, type, fuzzerSettings)
        ?: throw AssertionError("There should of been an element of type: $type found")
}

private fun getRandomExpressionOfType(
    expression: Expression,
    type: Type,
    requiredType: Type,
    fuzzerSettings: FuzzerSettings,
): Expression? {
    if (requiredType == type) {
        return expression
    }

    return when (type) {
        is Type.Array -> {
            val elementCount =
                type.elementCount?.let { Expression.IntLiteral(fuzzerSettings.randomInt(it).toString()) }
                    ?: return null

            getRandomExpressionOfType(
                expression =
                    Expression.IndexLookup(
                        target = expression,
                        index = elementCount,
                    ),
                type = type.elementType,
                requiredType = requiredType,
                fuzzerSettings = fuzzerSettings,
            )
        }
        is Type.Vector -> {
            getRandomExpressionOfType(
                expression =
                    Expression.IndexLookup(
                        target = expression,
                        index = Expression.IntLiteral(fuzzerSettings.randomInt(type.width).toString()),
                    ),
                type = type.elementType,
                requiredType = requiredType,
                fuzzerSettings = fuzzerSettings,
            )
        }
        is Type.Matrix -> TODO()
        is Type.Struct -> TODO()

        is Type.Pointer -> TODO()
        is Type.Reference -> TODO()

        else -> null
    }
}

private fun ScopeEntry.variableOfTypeInScopeEntry(type: Type) =
    this is ScopeEntry.TypedDecl &&
        this !is ScopeEntry.TypeAlias &&
        this.type
            .asStoreTypeIfReference()
            .subTypes()
            .filter { it !is Type.Array || it.elementCount != null } // As cannot determine at compile time to
            .contains(type)

private fun Type.subTypes(): Set<Type> {
    val subTypes = mutableSetOf<Type>()

    fun addSubTypes(type: Type) {
        subTypes.add(type)
        when (type) {
            is Type.Array -> {
                addSubTypes(type.elementType)
            }
            is Type.Matrix -> {
                addSubTypes(type.elementType)
            }
            is Type.Vector -> {
                addSubTypes(type.elementType)
            }
            is Type.Struct -> {
                type.members.forEach { addSubTypes(it.second) }
            }

            Type.AtomicI32 -> TODO()
            Type.AtomicU32 -> TODO()
            is Type.Pointer -> TODO()
            is Type.Reference -> TODO()

            else -> {}
        }
    }

    addSubTypes(this)

    return subTypes
}

fun TypeDecl.toType(resolvedEnvironment: ResolvedEnvironment): Type =
    when (this) {
        is TypeDecl.Array ->
            Type.Array(
                elementType = this.elementType.toType(resolvedEnvironment),
                elementCount =
                    this.elementCount?.let { evaluateToInt(it, resolvedEnvironment.globalScope, resolvedEnvironment) }
                        ?: throw IllegalArgumentException("Array must have a known length"),
            )

        is TypeDecl.NamedType -> {
            when (val scopeEntry = resolvedEnvironment.globalScope.getEntry(this.name)) {
                is ScopeEntry.Struct, is ScopeEntry.TypeAlias -> scopeEntry.type
                else -> throw IllegalStateException("Named Type does not correspond to a named type in scope")
            }
        }

        is TypeDecl.Bool -> Type.Bool
        is TypeDecl.F16 -> Type.F16
        is TypeDecl.F32 -> Type.F32
        is TypeDecl.I32 -> Type.I32
        is TypeDecl.U32 -> Type.U32

        is TypeDecl.Vec2 ->
            Type.Vector(
                width = 2,
                elementType =
                    this.elementType.toType(resolvedEnvironment) as? Type.Scalar
                        ?: throw IllegalStateException("Invalid vector element type"),
            )
        is TypeDecl.Vec3 ->
            Type.Vector(
                width = 3,
                elementType =
                    this.elementType.toType(resolvedEnvironment) as? Type.Scalar
                        ?: throw IllegalStateException("Invalid vector element type"),
            )
        is TypeDecl.Vec4 ->
            Type.Vector(
                width = 4,
                elementType =
                    this.elementType.toType(resolvedEnvironment) as? Type.Scalar
                        ?: throw IllegalStateException("Invalid vector element type"),
            )

        else -> TODO()
    }

fun constantWithSameValueEverywhere(
    value: Int,
    type: Type,
): Expression =
    when (type) {
        is Type.Array -> TODO("Array constants need to be supported.")
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
        is Type.Struct -> TODO("Struct constants need to be supported.")
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
