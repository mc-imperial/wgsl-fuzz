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

import com.wgslfuzz.core.BinaryOperator
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.ResolvedEnvironment
import com.wgslfuzz.core.Scope
import com.wgslfuzz.core.ScopeEntry
import com.wgslfuzz.core.Type
import com.wgslfuzz.core.TypeDecl
import com.wgslfuzz.core.asStoreTypeIfReference
import com.wgslfuzz.core.evaluateToInt
import java.util.Random

const val LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE: Int = 16777216

interface FuzzerSettings {
    fun goDeeper(currentDepth: Int): Boolean = randomDouble() < 4.0 / (currentDepth.toDouble() + 2.0) && currentDepth < 18

    // Get a unique identifiers for transformation such as `ControlFlowWrapper`
    fun getUniqueId(): Int

    // Yields a random integer in the range [0, limit)
    fun randomInt(limit: Int): Int

    // Yield a random double in the range [0, 1]
    fun randomDouble(): Double

    fun randomBool(): Boolean

    fun <T> randomElement(list: List<T>): T {
        require(list.isNotEmpty()) { "Cannot get random element of an empty list" }
        return list[randomInt(list.size)]
    }

    fun <T> randomElement(vararg elements: T): T = randomElement(elements.toList())

    data class FalseByConstructionWeights(
        val plainFalse: (depth: Int) -> Int = { 1 },
        val falseAndArbitrary: (depth: Int) -> Int = { 3 },
        val arbitraryAndFalse: (depth: Int) -> Int = { 3 },
        val notTrue: (depth: Int) -> Int = { 3 },
        val opaqueFalseFromUniformValues: (depth: Int) -> Int = { 6 },
    )

    data class TrueByConstructionWeights(
        val plainTrue: (depth: Int) -> Int = { 1 },
        val trueOrArbitrary: (depth: Int) -> Int = { 3 },
        val arbitraryOrTrue: (depth: Int) -> Int = { 3 },
        val notFalse: (depth: Int) -> Int = { 3 },
        val opaqueTrueFromUniformValues: (depth: Int) -> Int = { 6 },
    )

    val falseByConstructionWeights: FalseByConstructionWeights
        get() = FalseByConstructionWeights()

    val trueByConstructionWeights: TrueByConstructionWeights
        get() = TrueByConstructionWeights()

    data class ScalarIdentityOperationWeights(
        val addZeroLeft: Int = 1,
        val addZeroRight: Int = 1,
        val subZero: Int = 2,
        val mulOneLeft: Int = 1,
        val mulOneRight: Int = 1,
        val divOne: Int = 2,
    )

    val scalarIdentityOperationWeights: ScalarIdentityOperationWeights
        get() = ScalarIdentityOperationWeights()

    data class KnownValueWeights(
        val plainKnownValue: (depth: Int) -> Int = { 1 },
        val sumOfKnownValues: (depth: Int) -> Int = { 2 },
        val differenceOfKnownValues: (depth: Int) -> Int = { 2 },
        val productOfKnownValues: (depth: Int) -> Int = { 2 },
        val knownValueDerivedFromUniform: (depth: Int) -> Int = { 6 },
    )

    val knownValueWeights: KnownValueWeights
        get() = KnownValueWeights()

    data class ControlFlowWrappingWeights(
        val ifTrueWrapping: Int = 1,
        val ifFalseWrapping: Int = 1,
        val singleIterForLoop: Int = 1,
    )

    val controlFlowWrappingWeights: ControlFlowWrappingWeights
        get() = ControlFlowWrappingWeights()

    data class ArbitraryBooleanExpressionWeights(
        val not: (depth: Int) -> Int = { 1 },
        val or: (depth: Int) -> Int = { 2 },
        val and: (depth: Int) -> Int = { 2 },
        val lessThan: (depth: Int) -> Int = { 1 },
        val greaterThan: (depth: Int) -> Int = { 1 },
        val lessThanOrEqual: (depth: Int) -> Int = { 1 },
        val greaterThanOrEqual: (depth: Int) -> Int = { 1 },
        val equal: (depth: Int) -> Int = { 1 },
        val notEqual: (depth: Int) -> Int = { 1 },
        val variableFromScope: (depth: Int) -> Int = { 1 },
        val literal: (depth: Int) -> Int = { 1 },
    )

    val arbitraryBooleanExpressionWeights: ArbitraryBooleanExpressionWeights
        get() = ArbitraryBooleanExpressionWeights()

    data class ArbitraryIntExpressionWeights(
        val swapIntType: (depth: Int) -> Int = { 1 },
        val binaryOr: (depth: Int) -> Int = { 1 },
        val binaryAnd: (depth: Int) -> Int = { 1 },
        val binaryXor: (depth: Int) -> Int = { 1 },
        val negate: (depth: Int) -> Int = { 1 },
        val addition: (depth: Int) -> Int = { 1 },
        val subtraction: (depth: Int) -> Int = { 1 },
        val multiplication: (depth: Int) -> Int = { 1 },
        val division: (depth: Int) -> Int = { 1 },
        val modulo: (depth: Int) -> Int = { 1 },
        val abs: (depth: Int) -> Int = { 1 },
        val clamp: (depth: Int) -> Int = { 1 },
        val countLeadingZeros: (depth: Int) -> Int = { 1 },
        val countOneBits: (depth: Int) -> Int = { 1 },
        val countTrailingZeros: (depth: Int) -> Int = { 1 },
        val dot4U8Packed: (depth: Int) -> Int = { 1 },
        val dot4I8Packed: (depth: Int) -> Int = { 1 },
        val extractBits: (depth: Int) -> Int = { 1 },
        val firstLeadingBit: (depth: Int) -> Int = { 1 },
        val firstTrailingBit: (depth: Int) -> Int = { 1 },
        val insertBits: (depth: Int) -> Int = { 1 },
        val max: (depth: Int) -> Int = { 1 },
        val min: (depth: Int) -> Int = { 1 },
        val reverseBits: (depth: Int) -> Int = { 1 },
        val sign: (depth: Int) -> Int = { 1 },
        val variableFromScope: (depth: Int) -> Int = { 1 },
        val literal: (depth: Int) -> Int = { 1 },
    )

    val arbitraryIntExpressionWeights: ArbitraryIntExpressionWeights
        get() = ArbitraryIntExpressionWeights()

    data class ArbitraryElseBranchWeights(
        val empty: (depth: Int) -> Int = { 1 },
        val ifStatement: (depth: Int) -> Int = { 1 },
        val compound: (depth: Int) -> Int = { 1 },
    )

    val arbitraryElseBranchWeights: ArbitraryElseBranchWeights
        get() = ArbitraryElseBranchWeights()

    val randomArbitraryCompoundLength: (depth: Int) -> Int
        get() = { randomInt(10) }

    fun injectDeadBreak(): Boolean = randomInt(100) < 50

    fun injectDeadContinue(): Boolean = randomInt(100) < 50

    fun injectDeadDiscard(): Boolean = randomInt(100) < 50

    fun injectDeadReturn(): Boolean = randomInt(100) < 50

    fun applyIdentityOperation(): Boolean = randomInt(100) < 50

    fun controlFlowWrap(): Boolean = randomInt(100) < 50
}

class DefaultFuzzerSettings(
    private val generator: Random,
) : FuzzerSettings {
    private var nextId: Int = 0

    override fun getUniqueId(): Int {
        nextId++
        return nextId
    }

    override fun randomInt(limit: Int): Int = generator.nextInt(limit)

    override fun randomDouble(): Double = generator.nextDouble()

    override fun randomBool(): Boolean = generator.nextBoolean()
}

fun <T> choose(
    fuzzerSettings: FuzzerSettings,
    choices: List<Pair<Int, () -> T>>,
): T {
    val functions = mutableListOf<() -> T>()
    for (choice in choices) {
        (0..<choice.first).forEach { _ ->
            functions.add(choice.second)
        }
    }
    return fuzzerSettings.randomElement(functions)()
}

fun isVariableOfTypeInScope(
    scope: Scope,
    type: Type,
): Boolean =
    scope
        .getAllEntries()
        .any {
            it is ScopeEntry.TypedDecl &&
                it !is ScopeEntry.TypeAlias &&
                it.type.asStoreTypeIfReference() == type
        }

fun randomVariableFromScope(
    scope: Scope,
    type: Type,
    fuzzerSettings: FuzzerSettings,
): Expression? {
    val scopeEntries =
        scope.getAllEntries().filter {
            it is ScopeEntry.TypedDecl &&
                it !is ScopeEntry.TypeAlias &&
                it.type.asStoreTypeIfReference() == type
        }

    if (scopeEntries.isEmpty()) return null

    return scopeEntryTypedDeclToExpression(
        fuzzerSettings.randomElement(
            scopeEntries,
        ) as ScopeEntry.TypedDecl,
    )
}

fun scopeEntryTypedDeclToExpression(scopeEntry: ScopeEntry.TypedDecl): Expression =
    Expression.Identifier(
        name = scopeEntry.declName,
    )

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

fun binaryExpressionRandomOperandOrder(
    fuzzerSettings: FuzzerSettings,
    operator: BinaryOperator,
    operand1: Expression,
    operand2: Expression,
): Expression =
    if (fuzzerSettings.randomBool()) {
        Expression.Binary(operator, operand1, operand2)
    } else {
        Expression.Binary(operator, operand2, operand1)
    }
