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

import com.wgslfuzz.core.AugmentedExpression
import com.wgslfuzz.core.BinaryOperator
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.ResolvedEnvironment
import com.wgslfuzz.core.Scope
import com.wgslfuzz.core.ScopeEntry
import com.wgslfuzz.core.ShaderJob
import com.wgslfuzz.core.Type
import com.wgslfuzz.core.TypeDecl
import com.wgslfuzz.core.UnaryOperator
import com.wgslfuzz.core.clone
import com.wgslfuzz.core.evaluateToInt
import com.wgslfuzz.core.getUniformDeclaration
import java.util.Random
import kotlin.math.max

private const val LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE: Int = 16777216

interface FuzzerSettings {
    val maxDepth: Int
        get() = 5

    // Yields a random integer in the range [0, limit)
    fun randomInt(limit: Int): Int

    fun randomBool(): Boolean

    fun <T> randomElement(list: List<T>) = list[randomInt(list.size)]

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

    fun injectDeadBreak(): Boolean = randomInt(100) < 50

    fun injectDeadContinue(): Boolean = randomInt(100) < 50

    fun injectDeadDiscard(): Boolean = randomInt(100) < 50

    fun injectDeadReturn(): Boolean = randomInt(100) < 50

    fun applyIdentityOperation(): Boolean = randomInt(100) < 50
}

class DefaultFuzzerSettings(
    private val generator: Random,
) : FuzzerSettings {
    override fun randomInt(limit: Int): Int = generator.nextInt(limit)

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

fun randomUniformScalarWithValue(
    shaderJob: ShaderJob,
    fuzzerSettings: FuzzerSettings,
): Triple<Expression, Expression, Type> {
    val groups =
        shaderJob.pipelineState
            .getUniformGroups()
            .toList()
            .sorted()
    val group = fuzzerSettings.randomElement(groups)
    val bindings =
        shaderJob.pipelineState
            .getUniformBindingsForGroup(group)
            .toList()
            .sorted()
    val binding = fuzzerSettings.randomElement(bindings)
    val uniformDeclaration = shaderJob.tu.getUniformDeclaration(group, binding)

    var currentType: Type =
        uniformDeclaration.typeDecl?.toType(shaderJob.environment)
            ?: throw IllegalStateException("Uniform should have type")

    var currentUniformExpr: Expression = Expression.Identifier(uniformDeclaration.name)
    var currentValueExpr: Expression = shaderJob.pipelineState.getUniformValue(group, binding)

    while (true) {
        when (currentType) {
            is Type.I32 -> break
            is Type.F32 -> break
            is Type.Vector -> {
                val randomVectorIndex = fuzzerSettings.randomInt(currentType.width)
                currentType = currentType.elementType
                currentUniformExpr = Expression.IndexLookup(currentUniformExpr, Expression.IntLiteral(randomVectorIndex.toString()))
                currentValueExpr = (currentValueExpr as Expression.VectorValueConstructor).args[randomVectorIndex]
            }
            is Type.Struct -> {
                val randomMemberIndex = fuzzerSettings.randomInt(currentType.members.size)
                val randomMember = currentType.members[randomMemberIndex]
                currentType = randomMember.second
                currentUniformExpr = Expression.MemberLookup(currentUniformExpr, randomMember.first)
                currentValueExpr = (currentValueExpr as Expression.StructValueConstructor).args[randomMemberIndex]
            }
            is Type.Array -> {
                val randomElementIndex = fuzzerSettings.randomInt(currentType.elementCount!!)
                currentType = currentType.elementType
                currentUniformExpr =
                    Expression.IndexLookup(
                        currentUniformExpr,
                        Expression.IntLiteral(randomElementIndex.toString()),
                    )
                currentValueExpr = (currentValueExpr as Expression.ArrayValueConstructor).args[randomElementIndex]
            }
            else -> TODO()
        }
    }
    return Triple(currentUniformExpr, currentValueExpr, currentType)
}

private fun TypeDecl.toType(resolvedEnvironment: ResolvedEnvironment): Type =
    when (this) {
        is TypeDecl.Array ->
            Type.Array(
                elementType = this.elementType.toType(resolvedEnvironment),
                elementCount =
                    this.elementCount?.let { evaluateToInt(it, resolvedEnvironment.globalScope, resolvedEnvironment) }
                        ?: throw IllegalArgumentException("Array must have a known length"),
            )

        is TypeDecl.NamedType -> {
            val scopeEntry = resolvedEnvironment.globalScope.getEntry(this.name)
            when (scopeEntry) {
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

private fun generateFalseByConstructionExpression(
    depth: Int,
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
): AugmentedExpression.FalseByConstruction {
    if (depth >= fuzzerSettings.maxDepth) {
        return AugmentedExpression.FalseByConstruction(
            Expression.BoolLiteral("false"),
        )
    }
    val choices: List<Pair<Int, () -> AugmentedExpression.FalseByConstruction>> =
        listOf(
            // A plain "false"
            fuzzerSettings.falseByConstructionWeights.plainFalse(depth) to {
                AugmentedExpression.FalseByConstruction(
                    Expression.BoolLiteral("false"),
                )
            },
            // A false expression && an arbitrary expression
            fuzzerSettings.falseByConstructionWeights.falseAndArbitrary(depth) to {
                AugmentedExpression.FalseByConstruction(
                    Expression.Binary(
                        operator = BinaryOperator.SHORT_CIRCUIT_AND,
                        generateFalseByConstructionExpression(depth + 1, fuzzerSettings, shaderJob, scope),
                        generateArbitraryExpression(
                            depth = depth + 1,
                            type = Type.Bool,
                            sideEffectsAllowed = true,
                            fuzzerSettings = fuzzerSettings,
                            shaderJob = shaderJob,
                            scope = scope,
                        ),
                    ),
                )
            },
            // An arbitrary expression && a false expression
            fuzzerSettings.falseByConstructionWeights.arbitraryAndFalse(depth) to {
                AugmentedExpression.FalseByConstruction(
                    Expression.Binary(
                        operator = BinaryOperator.SHORT_CIRCUIT_AND,
                        generateArbitraryExpression(
                            depth = depth + 1,
                            type = Type.Bool,
                            sideEffectsAllowed = false, // No side effects as this will be executable
                            fuzzerSettings = fuzzerSettings,
                            shaderJob = shaderJob,
                            scope = scope,
                        ),
                        generateFalseByConstructionExpression(depth + 1, fuzzerSettings, shaderJob, scope),
                    ),
                )
            },
            // ! true expression
            fuzzerSettings.falseByConstructionWeights.notTrue(depth) to {
                AugmentedExpression.FalseByConstruction(
                    Expression.Unary(
                        operator = UnaryOperator.LOGICAL_NOT,
                        generateTrueByConstructionExpression(depth + 1, fuzzerSettings, shaderJob, scope),
                    ),
                )
            },
            fuzzerSettings.falseByConstructionWeights.opaqueFalseFromUniformValues(depth) to {
                val (uniformScalarExpr, literalExpr) = randomUniformScalarWithValue(shaderJob, fuzzerSettings)
                // Choose a random suitable operator.
                // No need for custom weights for this choice.
                val operators = listOf(BinaryOperator.NOT_EQUAL, BinaryOperator.LESS_THAN, BinaryOperator.GREATER_THAN)
                AugmentedExpression.FalseByConstruction(
                    // Choose randomly on which side of the expression the uniform access should appear.
                    binaryExpressionRandomOperandOrder(
                        fuzzerSettings,
                        fuzzerSettings.randomElement(operators),
                        uniformScalarExpr,
                        literalExpr,
                    ),
                )
            },
        )
    return choose(fuzzerSettings, choices)
}

private fun generateTrueByConstructionExpression(
    depth: Int,
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
): AugmentedExpression.TrueByConstruction {
    if (depth >= fuzzerSettings.maxDepth) {
        return AugmentedExpression.TrueByConstruction(
            Expression.BoolLiteral("true"),
        )
    }
    val choices: List<Pair<Int, () -> AugmentedExpression.TrueByConstruction>> =
        listOf(
            // A plain "true"
            fuzzerSettings.trueByConstructionWeights.plainTrue(depth) to {
                AugmentedExpression.TrueByConstruction(
                    Expression.BoolLiteral("true"),
                )
            },
            // A true expression || an arbitrary expression
            fuzzerSettings.trueByConstructionWeights.trueOrArbitrary(depth) to {
                AugmentedExpression.TrueByConstruction(
                    Expression.Binary(
                        operator = BinaryOperator.SHORT_CIRCUIT_OR,
                        generateTrueByConstructionExpression(depth + 1, fuzzerSettings, shaderJob, scope),
                        generateArbitraryExpression(
                            depth = depth + 1,
                            type = Type.Bool,
                            sideEffectsAllowed = true,
                            fuzzerSettings = fuzzerSettings,
                            shaderJob = shaderJob,
                            scope = scope,
                        ),
                    ),
                )
            },
            // An arbitrary expression || a true expression
            fuzzerSettings.trueByConstructionWeights.arbitraryOrTrue(depth) to {
                AugmentedExpression.TrueByConstruction(
                    Expression.Binary(
                        operator = BinaryOperator.SHORT_CIRCUIT_OR,
                        generateArbitraryExpression(
                            depth = depth + 1,
                            type = Type.Bool,
                            sideEffectsAllowed = false, // No side effects as this will be executable
                            fuzzerSettings = fuzzerSettings,
                            shaderJob = shaderJob,
                            scope = scope,
                        ),
                        generateTrueByConstructionExpression(depth + 1, fuzzerSettings, shaderJob, scope),
                    ),
                )
            },
            // ! false expression
            fuzzerSettings.trueByConstructionWeights.notFalse(depth) to {
                AugmentedExpression.TrueByConstruction(
                    Expression.Unary(
                        operator = UnaryOperator.LOGICAL_NOT,
                        generateFalseByConstructionExpression(depth + 1, fuzzerSettings, shaderJob, scope),
                    ),
                )
            },
            fuzzerSettings.trueByConstructionWeights.opaqueTrueFromUniformValues(depth) to {
                val (uniformScalarExpr, literalExpr) = randomUniformScalarWithValue(shaderJob, fuzzerSettings)
                // Choose randomly on which side of the expression the uniform access should appear.
                // No need for a custom weight for this choice.
                val (lhs, rhs) =
                    if (fuzzerSettings.randomBool()) {
                        Pair(uniformScalarExpr, literalExpr)
                    } else {
                        Pair(literalExpr, uniformScalarExpr)
                    }
                // Choose a random suitable operator.
                // No need for custom weights for this choice.
                val operators = listOf(BinaryOperator.EQUAL_EQUAL, BinaryOperator.LESS_THAN_EQUAL, BinaryOperator.GREATER_THAN_EQUAL)
                AugmentedExpression.TrueByConstruction(
                    Expression.Binary(
                        fuzzerSettings.randomElement(operators),
                        lhs,
                        rhs,
                    ),
                )
            },
        )
    return choose(fuzzerSettings, choices)
}

fun generateFalseByConstructionExpression(
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
): AugmentedExpression.FalseByConstruction = generateFalseByConstructionExpression(0, fuzzerSettings, shaderJob, scope)

fun generateTrueByConstructionExpression(
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
): AugmentedExpression.TrueByConstruction = generateTrueByConstructionExpression(0, fuzzerSettings, shaderJob, scope)

fun generateArbitraryExpression(
    depth: Int,
    type: Type,
    sideEffectsAllowed: Boolean,
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
): Expression {
    // TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/42): Support arbitrary expression generation
    return constantWithSameValueEverywhere(1, type)
}

fun generateKnownValueExpression(
    depth: Int,
    knownValue: Expression,
    type: Type,
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
): AugmentedExpression.KnownValue {
    if (depth >= fuzzerSettings.maxDepth) {
        return AugmentedExpression.KnownValue(
            knownValue = knownValue.clone(),
            expression = knownValue.clone(),
        )
    }
    if (type !is Type.Scalar) {
        TODO("Need to support non-scalar known values, e.g. vectors and matrices.")
    }
    val knownValueAsInt: Int =
        getNumericValueFromConstant(
            knownValue,
        )
    if (knownValueAsInt !in 0..LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE) {
        throw UnsupportedOperationException("Known values are currently only supported within a limited range.")
    }
    val literalSuffix =
        when (type) {
            is Type.I32 -> "i"
            is Type.U32 -> "u"
            is Type.AbstractInteger -> ""
            is Type.F32 -> "f"
            is Type.AbstractFloat -> ""
            else -> throw RuntimeException("Unsupported type.")
        }

    val choices: MutableList<Pair<Int, () -> AugmentedExpression.KnownValue>> =
        mutableListOf(
            fuzzerSettings.knownValueWeights.plainKnownValue(depth) to {
                AugmentedExpression.KnownValue(
                    knownValue = knownValue.clone(),
                    expression = knownValue.clone(),
                )
            },
            fuzzerSettings.knownValueWeights.sumOfKnownValues(depth) to {
                val randomValue = fuzzerSettings.randomInt(knownValueAsInt + 1)
                assert(randomValue <= knownValueAsInt)
                val difference: Int = knownValueAsInt - randomValue
                assert(difference in 0..knownValueAsInt)
                val randomValueText = "$randomValue$literalSuffix"
                val differenceText = "$difference$literalSuffix"
                val randomValueKnownExpression =
                    if (type is Type.Integer) {
                        Expression.IntLiteral(randomValueText)
                    } else {
                        Expression.FloatLiteral(randomValueText)
                    }
                val differenceKnownExpression =
                    if (type is Type.Integer) {
                        Expression.IntLiteral(differenceText)
                    } else {
                        Expression.FloatLiteral(differenceText)
                    }
                AugmentedExpression.KnownValue(
                    knownValue = knownValue.clone(),
                    expression =
                        binaryExpressionRandomOperandOrder(
                            fuzzerSettings,
                            BinaryOperator.PLUS,
                            generateKnownValueExpression(
                                depth = depth + 1,
                                knownValue = randomValueKnownExpression,
                                type = type,
                                fuzzerSettings = fuzzerSettings,
                                shaderJob = shaderJob,
                            ),
                            generateKnownValueExpression(
                                depth = depth + 1,
                                knownValue = differenceKnownExpression,
                                type = type,
                                fuzzerSettings = fuzzerSettings,
                                shaderJob = shaderJob,
                            ),
                        ),
                )
            },
            fuzzerSettings.knownValueWeights.differenceOfKnownValues(depth) to {
                val randomValue = fuzzerSettings.randomInt(LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE - knownValueAsInt + 1)
                val sum: Int = knownValueAsInt + randomValue
                assert(sum in 0..LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE)
                val randomValueText = "$randomValue$literalSuffix"
                val sumText = "$sum$literalSuffix"
                val randomValueKnownExpression =
                    if (type is Type.Integer) {
                        Expression.IntLiteral(randomValueText)
                    } else {
                        Expression.FloatLiteral(randomValueText)
                    }
                val sumKnownExpression =
                    if (type is Type.Integer) {
                        Expression.IntLiteral(sumText)
                    } else {
                        Expression.FloatLiteral(sumText)
                    }
                AugmentedExpression.KnownValue(
                    knownValue = knownValue.clone(),
                    expression =
                        Expression.Binary(
                            BinaryOperator.MINUS,
                            generateKnownValueExpression(
                                depth = depth + 1,
                                knownValue = sumKnownExpression,
                                type = type,
                                fuzzerSettings = fuzzerSettings,
                                shaderJob = shaderJob,
                            ),
                            generateKnownValueExpression(
                                depth = depth + 1,
                                knownValue = randomValueKnownExpression,
                                type = type,
                                fuzzerSettings = fuzzerSettings,
                                shaderJob = shaderJob,
                            ),
                        ),
                )
            },
            fuzzerSettings.knownValueWeights.productOfKnownValues(depth) to {
                val randomValue = max(1, fuzzerSettings.randomInt(max(1, knownValueAsInt / 2)))
                val quotient: Int = knownValueAsInt / randomValue
                val remainder: Int = knownValueAsInt % randomValue

                val randomValueText = "$randomValue$literalSuffix"
                val quotientText = "$quotient$literalSuffix"
                val remainderText = "$remainder$literalSuffix"
                val randomValueKnownExpression =
                    if (type is Type.Integer) {
                        Expression.IntLiteral(randomValueText)
                    } else {
                        Expression.FloatLiteral(randomValueText)
                    }
                val quotientKnownExpression =
                    if (type is Type.Integer) {
                        Expression.IntLiteral(quotientText)
                    } else {
                        Expression.FloatLiteral(quotientText)
                    }
                val remainderKnownExpression =
                    if (type is Type.Integer) {
                        Expression.IntLiteral(remainderText)
                    } else {
                        Expression.FloatLiteral(remainderText)
                    }
                var resultExpression =
                    binaryExpressionRandomOperandOrder(
                        fuzzerSettings,
                        BinaryOperator.TIMES,
                        generateKnownValueExpression(
                            depth = depth + 1,
                            knownValue = randomValueKnownExpression,
                            type = type,
                            fuzzerSettings = fuzzerSettings,
                            shaderJob = shaderJob,
                        ),
                        generateKnownValueExpression(
                            depth = depth + 1,
                            knownValue = quotientKnownExpression,
                            type = type,
                            fuzzerSettings = fuzzerSettings,
                            shaderJob = shaderJob,
                        ),
                    )
                if (remainder != 0 || fuzzerSettings.randomBool()) {
                    resultExpression =
                        binaryExpressionRandomOperandOrder(
                            fuzzerSettings,
                            BinaryOperator.PLUS,
                            resultExpression,
                            generateKnownValueExpression(
                                depth = depth + 1,
                                knownValue = remainderKnownExpression,
                                type = type,
                                fuzzerSettings = fuzzerSettings,
                                shaderJob = shaderJob,
                            ),
                        )
                }
                AugmentedExpression.KnownValue(
                    knownValue = knownValue.clone(),
                    expression = resultExpression,
                )
            },
        )
    if (!type.isAbstract()) {
        // Deriving a known value from a uniform only works with concrete types.
        choices.add(
            fuzzerSettings.knownValueWeights.knownValueDerivedFromUniform(depth) to {
                val (uniformScalar, valueOfUniform, scalarType) = randomUniformScalarWithValue(shaderJob, fuzzerSettings)
                val valueOfUniformAsInt: Int =
                    getNumericValueFromConstant(
                        valueOfUniform,
                    )
                val uniformScalarWithCastIfNeeded =
                    if (type is Type.U32) {
                        Expression.U32ValueConstructor(listOf(uniformScalar))
                    } else if (scalarType is Type.Integer && type is Type.Float) {
                        Expression.F32ValueConstructor(listOf(uniformScalar))
                    } else if (scalarType is Type.Float && type is Type.Integer) {
                        Expression.I32ValueConstructor(listOf(uniformScalar))
                    } else {
                        uniformScalar
                    }
                val expression =
                    if (valueOfUniformAsInt == knownValueAsInt) {
                        uniformScalarWithCastIfNeeded
                    } else if (valueOfUniformAsInt > knownValueAsInt) {
                        val difference = valueOfUniformAsInt - knownValueAsInt
                        val differenceText = "$difference$literalSuffix"
                        val differenceKnownExpression =
                            if (type is Type.Integer) {
                                Expression.IntLiteral(differenceText)
                            } else {
                                Expression.FloatLiteral(differenceText)
                            }
                        Expression.Binary(
                            BinaryOperator.MINUS,
                            uniformScalarWithCastIfNeeded,
                            generateKnownValueExpression(
                                depth = depth + 1,
                                knownValue = differenceKnownExpression,
                                fuzzerSettings = fuzzerSettings,
                                shaderJob = shaderJob,
                                type = type,
                            ),
                        )
                    } else {
                        val difference = knownValueAsInt - valueOfUniformAsInt
                        val differenceText = "$difference$literalSuffix"
                        val differenceKnownExpression =
                            if (type is Type.Integer) {
                                Expression.IntLiteral(differenceText)
                            } else {
                                Expression.FloatLiteral(differenceText)
                            }
                        binaryExpressionRandomOperandOrder(
                            fuzzerSettings,
                            BinaryOperator.PLUS,
                            uniformScalarWithCastIfNeeded,
                            generateKnownValueExpression(
                                depth = depth + 1,
                                knownValue = differenceKnownExpression,
                                fuzzerSettings = fuzzerSettings,
                                shaderJob = shaderJob,
                                type = type,
                            ),
                        )
                    }
                AugmentedExpression.KnownValue(
                    knownValue = knownValue.clone(),
                    expression = expression,
                )
            },
        )
    }
    return choose(fuzzerSettings, choices)
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

private fun getNumericValueFromConstant(constantExpression: Expression): Int {
    when (constantExpression) {
        is Expression.FloatLiteral -> {
            val resultAsDouble = constantExpression.text.trimEnd('f', 'h').toDouble()
            val resultAsInt = resultAsDouble.toInt()
            if (resultAsDouble != resultAsInt.toDouble()) {
                throw RuntimeException("Only integer-valued doubles are supported in known value expressions.")
            }
            return resultAsInt
        }
        is Expression.IntLiteral -> {
            return constantExpression.text.trimEnd('i', 'u').toInt()
        }
        else -> throw UnsupportedOperationException("Cannot get numeric value from $constantExpression")
    }
}

private fun binaryExpressionRandomOperandOrder(
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
