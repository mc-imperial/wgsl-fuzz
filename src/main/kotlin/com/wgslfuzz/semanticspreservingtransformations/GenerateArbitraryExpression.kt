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

import com.wgslfuzz.core.AccessMode
import com.wgslfuzz.core.BinaryOperator
import com.wgslfuzz.core.Expression
import com.wgslfuzz.core.Scope
import com.wgslfuzz.core.ScopeEntry
import com.wgslfuzz.core.ShaderJob
import com.wgslfuzz.core.Type
import com.wgslfuzz.core.UnaryOperator
import com.wgslfuzz.core.asStoreTypeIfReference
import kotlin.random.Random

private const val I32_LOWEST_VALUE: Long = -0x80000000
private const val I32_HIGHEST_VALUE: Long = 0x7fffffff
private const val U32_LOWEST_VALUE: Long = 0
private const val U32_HIGHEST_VALUE: Long = 0xffffffff

fun generateArbitraryExpression(
    depth: Int,
    type: Type,
    sideEffectsAllowed: Boolean,
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
): Expression =
    when (type) {
        is Type.Bool -> generateArbitraryBool(depth, sideEffectsAllowed, fuzzerSettings, shaderJob, scope)
        is Type.I32, is Type.U32 -> generateArbitraryInt(depth, sideEffectsAllowed, fuzzerSettings, shaderJob, scope, type)
        // TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/42): Support arbitrary expression generation
        else -> constantWithSameValueEverywhere(1, type)
    }

private fun generateArbitraryBool(
    depth: Int,
    sideEffectsAllowed: Boolean,
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
): Expression {
    val nonRecursiveChoices: List<Pair<Int, () -> Expression>> =
        listOfNotNull(
            fuzzerSettings.arbitraryBooleanExpressionWeights.literal(depth) to {
                Expression.BoolLiteral(
                    text = fuzzerSettings.randomElement(listOf("true", "false")),
                )
            },
            randomVariableFromScope(scope, Type.Bool, fuzzerSettings)?.let {
                fuzzerSettings.arbitraryBooleanExpressionWeights.variableFromScope(depth) to { it }
            },
        )

    fun arbitraryIntComparison(operator: BinaryOperator): Expression {
        val type = fuzzerSettings.randomElement(listOf(Type.I32, Type.U32))
        return Expression.Paren(
            Expression.Binary(
                operator = operator,
                lhs =
                    generateArbitraryInt(
                        depth = depth + 1,
                        sideEffectsAllowed,
                        fuzzerSettings,
                        shaderJob,
                        scope,
                        type,
                    ),
                rhs =
                    generateArbitraryInt(
                        depth = depth + 1,
                        sideEffectsAllowed,
                        fuzzerSettings,
                        shaderJob,
                        scope,
                        type,
                    ),
            ),
        )
    }

    val recursiveChoices: List<Pair<Int, () -> Expression>> =
        listOf(
            fuzzerSettings.arbitraryBooleanExpressionWeights.not(depth) to {
                Expression.Paren(
                    Expression.Unary(
                        operator = UnaryOperator.LOGICAL_NOT,
                        target =
                            generateArbitraryBool(
                                depth = depth + 1,
                                sideEffectsAllowed,
                                fuzzerSettings,
                                shaderJob,
                                scope,
                            ),
                    ),
                )
            },
            fuzzerSettings.arbitraryBooleanExpressionWeights.or(depth) to {
                Expression.Paren(
                    Expression.Binary(
                        operator = BinaryOperator.SHORT_CIRCUIT_OR,
                        lhs =
                            generateArbitraryBool(
                                depth = depth + 1,
                                sideEffectsAllowed,
                                fuzzerSettings,
                                shaderJob,
                                scope,
                            ),
                        rhs =
                            generateArbitraryBool(
                                depth = depth + 1,
                                sideEffectsAllowed,
                                fuzzerSettings,
                                shaderJob,
                                scope,
                            ),
                    ),
                )
            },
            fuzzerSettings.arbitraryBooleanExpressionWeights.and(depth) to {
                Expression.Paren(
                    Expression.Binary(
                        operator = BinaryOperator.SHORT_CIRCUIT_AND,
                        lhs =
                            generateArbitraryBool(
                                depth = depth + 1,
                                sideEffectsAllowed,
                                fuzzerSettings,
                                shaderJob,
                                scope,
                            ),
                        rhs =
                            generateArbitraryBool(
                                depth = depth + 1,
                                sideEffectsAllowed,
                                fuzzerSettings,
                                shaderJob,
                                scope,
                            ),
                    ),
                )
            },
            fuzzerSettings.arbitraryBooleanExpressionWeights.lessThan(depth) to {
                arbitraryIntComparison(BinaryOperator.LESS_THAN)
            },
            fuzzerSettings.arbitraryBooleanExpressionWeights.greaterThan(depth) to {
                arbitraryIntComparison(BinaryOperator.GREATER_THAN)
            },
            fuzzerSettings.arbitraryBooleanExpressionWeights.lessThanOrEqual(depth) to {
                arbitraryIntComparison(BinaryOperator.LESS_THAN_EQUAL)
            },
            fuzzerSettings.arbitraryBooleanExpressionWeights.greaterThanOrEqual(depth) to {
                arbitraryIntComparison(BinaryOperator.GREATER_THAN_EQUAL)
            },
            fuzzerSettings.arbitraryBooleanExpressionWeights.equal(depth) to {
                arbitraryIntComparison(BinaryOperator.EQUAL_EQUAL)
            },
            fuzzerSettings.arbitraryBooleanExpressionWeights.notEqual(depth) to {
                arbitraryIntComparison(BinaryOperator.NOT_EQUAL)
            },
        )

    return if (fuzzerSettings.goDeeper(depth)) {
        choose(fuzzerSettings, recursiveChoices + nonRecursiveChoices)
    } else {
        choose(fuzzerSettings, nonRecursiveChoices)
    }
}

private fun generateArbitraryInt(
    depth: Int,
    sideEffectsAllowed: Boolean,
    fuzzerSettings: FuzzerSettings,
    shaderJob: ShaderJob,
    scope: Scope,
    outputType: Type.Integer,
): Expression {
    require(outputType !is Type.AbstractInteger) { "outputType must be a concrete type" }

    val literalSuffix =
        when (outputType) {
            Type.I32 -> "i"
            Type.U32 -> "u"
            Type.AbstractInteger -> throw RuntimeException("Cannot get here require above should guard against this")
        }

    val outputTypeNumRange =
        when (outputType) {
            is Type.I32 -> I32_LOWEST_VALUE..I32_HIGHEST_VALUE
            is Type.U32 -> U32_LOWEST_VALUE..U32_HIGHEST_VALUE
            Type.AbstractInteger -> throw RuntimeException("Cannot get here require above should guard against this")
        }

    val nonRecursiveChoices: List<Pair<Int, () -> Expression>> =
        listOfNotNull(
            fuzzerSettings.arbitraryIntExpressionWeights.literal(depth) to {
                Expression.IntLiteral(
                    fuzzerSettings
                        .randomElementFromRange(outputTypeNumRange)
                        .toString() + literalSuffix,
                )
            },
            randomVariableFromScope(scope, outputType, fuzzerSettings)?.let {
                fuzzerSettings.arbitraryIntExpressionWeights.variableFromScope(depth) to { it }
            },
        )

    fun generateArbitraryIntHelper(type: Type.Integer) =
        generateArbitraryInt(
            depth = depth + 1,
            sideEffectsAllowed,
            fuzzerSettings,
            shaderJob,
            scope,
            type,
        )

    fun arbitraryBinaryOperation(operator: BinaryOperator) =
        Expression.Paren(
            Expression.Binary(
                operator = operator,
                lhs = generateArbitraryIntHelper(outputType),
                rhs = generateArbitraryIntHelper(outputType),
            ),
        )

    // Overflow characteristics of i32 and u32 are define here: https://www.w3.org/TR/WGSL/#integer-types
    // Since generating an arbitrary expression do not care what they are just that they exist.
    val recursiveChoices: List<Pair<Int, () -> Expression>> =
        listOfNotNull(
            fuzzerSettings.arbitraryIntExpressionWeights.swapIntType(depth) to {
                when (outputType) {
                    Type.AbstractInteger -> throw RuntimeException("outputType cannot be AbstractInteger")
                    Type.I32 -> Expression.I32ValueConstructor(listOf(generateArbitraryIntHelper(Type.U32)))
                    Type.U32 -> Expression.U32ValueConstructor(listOf(generateArbitraryIntHelper(Type.I32)))
                }
            },
            fuzzerSettings.arbitraryIntExpressionWeights.binaryOr(depth) to {
                arbitraryBinaryOperation(BinaryOperator.BINARY_OR)
            },
            fuzzerSettings.arbitraryIntExpressionWeights.binaryAnd(depth) to {
                arbitraryBinaryOperation(BinaryOperator.BINARY_AND)
            },
            fuzzerSettings.arbitraryIntExpressionWeights.binaryXor(depth) to {
                arbitraryBinaryOperation(BinaryOperator.BINARY_XOR)
            },
            if (outputType is Type.I32) {
                fuzzerSettings.arbitraryIntExpressionWeights.negate(depth) to {
                    Expression.Paren(
                        Expression.Unary(
                            operator = UnaryOperator.MINUS,
                            target = generateArbitraryIntHelper(outputType),
                        ),
                    )
                }
            } else {
                null
            },
            fuzzerSettings.arbitraryIntExpressionWeights.addition(depth) to {
                arbitraryBinaryOperation(BinaryOperator.PLUS)
            },
            fuzzerSettings.arbitraryIntExpressionWeights.subtraction(depth) to {
                arbitraryBinaryOperation(BinaryOperator.MINUS)
            },
            fuzzerSettings.arbitraryIntExpressionWeights.multiplication(depth) to {
                arbitraryBinaryOperation(BinaryOperator.TIMES)
            },
            fuzzerSettings.arbitraryIntExpressionWeights.division(depth) to {
                Expression.Paren(
                    Expression.Binary(
                        operator = BinaryOperator.DIVIDE,
                        lhs = generateArbitraryIntHelper(outputType),
                        rhs =
                            generateKnownValueExpression(
                                depth = depth + 1,
                                knownValue =
                                    Expression.IntLiteral(
                                        fuzzerSettings
                                            .randomElementFromRange(1..LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE)
                                            .toString() + literalSuffix,
                                    ),
                                type = outputType,
                                fuzzerSettings = fuzzerSettings,
                                shaderJob = shaderJob,
                                scope = scope,
                            ),
                    ),
                )
            },
            fuzzerSettings.arbitraryIntExpressionWeights.modulo(depth) to {
                Expression.Paren(
                    Expression.Binary(
                        operator = BinaryOperator.MODULO,
                        lhs = generateArbitraryIntHelper(outputType),
                        rhs =
                            generateKnownValueExpression(
                                depth = depth + 1,
                                knownValue =
                                    Expression.IntLiteral(
                                        fuzzerSettings
                                            .randomElementFromRange(1..LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE)
                                            .toString() + literalSuffix,
                                    ),
                                type = outputType,
                                fuzzerSettings = fuzzerSettings,
                                shaderJob = shaderJob,
                                scope = scope,
                            ),
                    ),
                )
            },
            fuzzerSettings.arbitraryIntExpressionWeights.abs(depth) to {
                Expression.FunctionCall(
                    callee = "abs",
                    templateParameter = null,
                    args = listOf(generateArbitraryIntHelper(outputType)),
                )
            },
            fuzzerSettings.arbitraryIntExpressionWeights.clamp(depth) to {
                val maxValue = fuzzerSettings.randomElementFromRange(0..LARGEST_INTEGER_IN_PRECISE_FLOAT_RANGE)
                val max =
                    generateKnownValueExpression(
                        depth = depth + 1,
                        knownValue = Expression.IntLiteral(maxValue.toString() + literalSuffix),
                        type = outputType,
                        fuzzerSettings = fuzzerSettings,
                        shaderJob = shaderJob,
                        scope = scope,
                    )
                val min =
                    generateKnownValueExpression(
                        depth = depth + 1,
                        knownValue =
                            Expression.IntLiteral(
                                fuzzerSettings
                                    .randomElementFromRange(0..<maxValue)
                                    .toString() + literalSuffix,
                            ),
                        type = outputType,
                        fuzzerSettings = fuzzerSettings,
                        shaderJob = shaderJob,
                        scope = scope,
                    )

                Expression.FunctionCall(
                    callee = "clamp",
                    templateParameter = null,
                    args = listOf(generateArbitraryIntHelper(outputType), min, max),
                )
            },
            fuzzerSettings.arbitraryIntExpressionWeights.countLeadingZeros(depth) to {
                Expression.FunctionCall(
                    callee = "countLeadingZeros",
                    templateParameter = null,
                    args = listOf(generateArbitraryIntHelper(outputType)),
                )
            },
            fuzzerSettings.arbitraryIntExpressionWeights.countOneBits(depth) to {
                Expression.FunctionCall(
                    callee = "countOneBits",
                    templateParameter = null,
                    args = listOf(generateArbitraryIntHelper(outputType)),
                )
            },
            fuzzerSettings.arbitraryIntExpressionWeights.countTrailingZeros(depth) to {
                Expression.FunctionCall(
                    callee = "countTrailingZeros",
                    templateParameter = null,
                    args = listOf(generateArbitraryIntHelper(outputType)),
                )
            },
            if (outputType is Type.U32) {
                fuzzerSettings.arbitraryIntExpressionWeights.dot4U8Packed(depth) to {
                    Expression.FunctionCall(
                        callee = "dot4U8Packed",
                        templateParameter = null,
                        args = listOf(generateArbitraryIntHelper(Type.U32), generateArbitraryIntHelper(Type.U32)),
                    )
                }
            } else {
                null
            },
            if (outputType is Type.I32) {
                fuzzerSettings.arbitraryIntExpressionWeights.dot4I8Packed(depth) to {
                    Expression.FunctionCall(
                        callee = "dot4I8Packed",
                        templateParameter = null,
                        args = listOf(generateArbitraryIntHelper(Type.U32), generateArbitraryIntHelper(Type.U32)),
                    )
                }
            } else {
                null
            },
            fuzzerSettings.arbitraryIntExpressionWeights.extractBits(depth) to {
                // sign extends if i32 and does not sign extend for u32
                val bitWidth = 32
                val countValue = fuzzerSettings.randomElementFromRange(1..bitWidth)
                val count =
                    generateKnownValueExpression(
                        depth = depth + 1,
                        knownValue =
                            Expression.IntLiteral(
                                countValue.toString() + "u",
                            ),
                        type = Type.U32,
                        fuzzerSettings = fuzzerSettings,
                        shaderJob = shaderJob,
                        scope = scope,
                    )
                val offset =
                    generateKnownValueExpression(
                        depth = depth + 1,
                        knownValue =
                            Expression.IntLiteral(
                                fuzzerSettings
                                    .randomElementFromRange(0..bitWidth - countValue)
                                    .toString() + "u",
                            ),
                        type = Type.U32,
                        fuzzerSettings = fuzzerSettings,
                        shaderJob = shaderJob,
                        scope = scope,
                    )
                Expression.FunctionCall(
                    callee = "extractBits",
                    templateParameter = null,
                    args =
                        listOf(
                            generateArbitraryIntHelper(outputType),
                            offset,
                            count,
                        ),
                )
            },
            fuzzerSettings.arbitraryIntExpressionWeights.firstLeadingBit(depth) to {
                Expression.FunctionCall(
                    callee = "firstLeadingBit",
                    templateParameter = null,
                    args = listOf(generateArbitraryIntHelper(outputType)),
                )
            },
            fuzzerSettings.arbitraryIntExpressionWeights.firstTrailingBit(depth) to {
                Expression.FunctionCall(
                    callee = "firstTrailingBit",
                    templateParameter = null,
                    args = listOf(generateArbitraryIntHelper(outputType)),
                )
            },
            fuzzerSettings.arbitraryIntExpressionWeights.insertBits(depth) to {
                val bitWidth = 32
                val countValue = fuzzerSettings.randomElementFromRange(1..bitWidth)
                val count =
                    generateKnownValueExpression(
                        depth = depth + 1,
                        knownValue =
                            Expression.IntLiteral(
                                countValue.toString() + "u",
                            ),
                        type = Type.U32,
                        fuzzerSettings = fuzzerSettings,
                        shaderJob = shaderJob,
                        scope = scope,
                    )
                val offset =
                    generateKnownValueExpression(
                        depth = depth + 1,
                        knownValue =
                            Expression.IntLiteral(
                                fuzzerSettings
                                    .randomElementFromRange(0..bitWidth - countValue)
                                    .toString() + "u",
                            ),
                        type = Type.U32,
                        fuzzerSettings = fuzzerSettings,
                        shaderJob = shaderJob,
                        scope = scope,
                    )
                Expression.FunctionCall(
                    callee = "insertBits",
                    templateParameter = null,
                    args =
                        listOf(
                            generateArbitraryIntHelper(outputType), // e: T
                            generateArbitraryIntHelper(outputType), // newBits: T
                            offset,
                            count,
                        ),
                )
            },
            fuzzerSettings.arbitraryIntExpressionWeights.max(depth) to {
                Expression.FunctionCall(
                    callee = "max",
                    templateParameter = null,
                    args =
                        listOf(
                            generateArbitraryIntHelper(outputType),
                            generateArbitraryIntHelper(outputType),
                        ),
                )
            },
            fuzzerSettings.arbitraryIntExpressionWeights.min(depth) to {
                Expression.FunctionCall(
                    callee = "min",
                    templateParameter = null,
                    args =
                        listOf(
                            generateArbitraryIntHelper(outputType),
                            generateArbitraryIntHelper(outputType),
                        ),
                )
            },
            fuzzerSettings.arbitraryIntExpressionWeights.reverseBits(depth) to {
                Expression.FunctionCall(
                    callee = "reverseBits",
                    templateParameter = null,
                    args = listOf(generateArbitraryIntHelper(outputType)),
                )
            },
            if (outputType is Type.I32) {
                fuzzerSettings.arbitraryIntExpressionWeights.sign(depth) to {
                    Expression.FunctionCall(
                        callee = "sign",
                        templateParameter = null,
                        args = listOf(generateArbitraryIntHelper(outputType)),
                    )
                }
            } else {
                null
            },
        )

    return if (fuzzerSettings.goDeeper(depth)) {
        choose(fuzzerSettings, recursiveChoices + nonRecursiveChoices)
    } else {
        choose(fuzzerSettings, nonRecursiveChoices)
    }
}

private fun randomVariableFromScope(
    scope: Scope,
    type: Type,
    fuzzerSettings: FuzzerSettings,
): Expression? {
    val pointerTypesInScope =
        scope
            .getAllEntries()
            .filterIsInstance<ScopeEntry.TypedDecl>()
            .flatMap { typeDecl ->
                if (typeDecl is ScopeEntry.TypeAlias) {
                    emptyList()
                } else {
                    typeDecl.type
                        .componentTypes()
                        .filterIsInstance<Type.Pointer>()
                        .map { it.pointeeType::class }
                }
            }

    val scopeEntries =
        scope
            .getAllEntries()
            .filterIsInstance<ScopeEntry.TypedDecl>()
            .filter { scopeEntry ->
                // Cannot create a valid identifier from TypeAlias or Struct scope entry
                scopeEntry !is ScopeEntry.TypeAlias &&
                    scopeEntry !is ScopeEntry.Struct &&
                    // Remove types that have pointers to them in scope
                    // If not removed. It can in some cases cause the compiler to fail due to aliasing
                    // Only necessary since counting sort uses pointers
                    !scopeEntry.type.componentTypes().any { pointerTypesInScope.contains(it::class) } &&
                    // Only allow scope entries that are either the correct type or contain the correct type via indexing
                    scopeEntry.type
                        .asStoreTypeIfReference()
                        .componentTypes {
                            when (it) {
                                // Remove all runtime sized arrays since cannot pull random element out of them
                                is Type.Array -> it.elementCount != null
                                // Remove all pointers that cannot be read from
                                is Type.Pointer -> it.accessMode == AccessMode.READ || it.accessMode == AccessMode.READ_WRITE
                                else -> true
                            }
                        }.contains(type)
            }

    if (scopeEntries.isEmpty()) return null

    val scopeEntry = fuzzerSettings.randomElement(scopeEntries)

    val scopeEntryIdentifierExpression =
        Expression.Identifier(
            name = scopeEntry.declName,
        )

    return getRandomVariableExpressionOfType(scopeEntryIdentifierExpression, scopeEntry.type, type, fuzzerSettings)
        ?: throw AssertionError("Could not find element of type: $type in ${scopeEntry.type}")
}

private fun getRandomVariableExpressionOfType(
    expression: Expression,
    expressionType: Type,
    requiredType: Type,
    fuzzerSettings: FuzzerSettings,
): Expression? {
    if (requiredType == expressionType) {
        return expression
    }

    return when (expressionType) {
        is Type.Array -> {
            val arrayIndex =
                expressionType.elementCount?.let { Expression.IntLiteral(fuzzerSettings.randomInt(it).toString()) }
                    ?: return null

            getRandomVariableExpressionOfType(
                expression =
                    Expression.IndexLookup(
                        target = expression,
                        index = arrayIndex,
                    ),
                expressionType = expressionType.elementType,
                requiredType = requiredType,
                fuzzerSettings = fuzzerSettings,
            )
        }
        is Type.Vector -> {
            getRandomVariableExpressionOfType(
                expression =
                    Expression.IndexLookup(
                        target = expression,
                        index = Expression.IntLiteral(fuzzerSettings.randomInt(expressionType.width).toString()),
                    ),
                expressionType = expressionType.elementType,
                requiredType = requiredType,
                fuzzerSettings = fuzzerSettings,
            )
        }
        is Type.Matrix -> {
            val columnIndex = Expression.IntLiteral(fuzzerSettings.randomInt(expressionType.numCols).toString())
            val rowIndex = Expression.IntLiteral(fuzzerSettings.randomInt(expressionType.numRows).toString())

            getRandomVariableExpressionOfType(
                expression =
                    Expression.IndexLookup(
                        target = expression,
                        index = columnIndex,
                    ),
                expressionType =
                    Type.Vector(
                        width = expressionType.numRows,
                        elementType = expressionType.elementType,
                    ),
                requiredType = requiredType,
                fuzzerSettings = fuzzerSettings,
            )
        }
        is Type.Struct -> {
            val shuffledMembers = expressionType.members.shuffled()

            for ((memberName, memberType) in shuffledMembers) {
                val randomExpression =
                    getRandomVariableExpressionOfType(
                        expression =
                            Expression.MemberLookup(
                                receiver = expression,
                                memberName = memberName,
                            ),
                        expressionType = memberType,
                        requiredType = requiredType,
                        fuzzerSettings = fuzzerSettings,
                    )

                if (randomExpression != null) {
                    return randomExpression
                }
            }

            null
        }
        is Type.Reference -> {
            getRandomVariableExpressionOfType(
                expression = expression,
                expressionType = expressionType.storeType,
                requiredType = requiredType,
                fuzzerSettings = fuzzerSettings,
            )
        }

        is Type.Pointer -> {
            getRandomVariableExpressionOfType(
                expression =
                    Expression.Unary(
                        operator = UnaryOperator.DEREFERENCE,
                        target = expression,
                    ),
                expressionType = expressionType.pointeeType,
                requiredType = requiredType,
                fuzzerSettings = fuzzerSettings,
            )
        }

        else -> null
    }
}

private fun Type.componentTypes(predicate: (Type) -> Boolean = { true }): Set<Type> {
    val componentTypes = mutableSetOf<Type>()

    fun addComponentTypes(type: Type) {
        if (!predicate(type)) return
        componentTypes.add(type)
        when (type) {
            is Type.Array -> {
                addComponentTypes(type.elementType)
            }
            is Type.Matrix -> {
                addComponentTypes(
                    Type.Vector(
                        width = type.numRows,
                        elementType = type.elementType,
                    ),
                )
            }
            is Type.Vector -> {
                addComponentTypes(type.elementType)
            }
            is Type.Struct -> {
                type.members.forEach { addComponentTypes(it.second) }
            }
            is Type.Pointer -> {
                addComponentTypes(type.pointeeType)
            }
            is Type.Reference -> {
                addComponentTypes(type.storeType)
            }

            Type.AtomicI32 -> TODO()
            Type.AtomicU32 -> TODO()

            else -> {}
        }
    }

    addComponentTypes(this)

    return componentTypes
}

private fun FuzzerSettings.randomElementFromRange(range: IntRange): Int = range.random(Random(this.randomInt(Int.MAX_VALUE)))

private fun FuzzerSettings.randomElementFromRange(range: LongRange): Long = range.random(Random(this.randomInt(Int.MAX_VALUE)))
