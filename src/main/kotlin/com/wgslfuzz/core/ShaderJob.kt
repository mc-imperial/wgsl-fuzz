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

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * This class provides information about a uniform buffers in terms of the bytes it should contain at
 * runtime - i.e., the data that should be put into the buffer by a WebGPU API call.
 */
@Serializable
data class UniformBufferInfoByteLevel(
    val group: Int,
    val binding: Int,
    val data: List<Int>, // Each integer represents a byte
)

@Serializable
class PipelineState(
    private val uniformValues: Map<Int, Map<Int, Expression>>,
) {
    fun getUniformGroups(): Set<Int> = uniformValues.keys

    fun getUniformBindingsForGroup(group: Int): Set<Int> = uniformValues[group]!!.keys

    fun getUniformValue(
        group: Int,
        binding: Int,
    ): Expression = uniformValues[group]!![binding]!!.clone()
}

@Serializable
class ShaderJob(
    val tu: TranslationUnit,
    val pipelineState: PipelineState,
) {
    init {
        val seen = mutableSetOf<AstNode>()
        for (node in nodesPreOrder(tu)) {
            assert(node !in seen)
            seen.add(node)
        }
    }

    @Transient
    val environment: ResolvedEnvironment = resolve(tu)

    fun getByteLevelContentsForUniformBuffers(): List<UniformBufferInfoByteLevel> {
        val result = mutableListOf<UniformBufferInfoByteLevel>()
        for (group in pipelineState.getUniformGroups().sorted()) {
            for (binding in pipelineState.getUniformBindingsForGroup(group).sorted()) {
                result.add(
                    UniformBufferInfoByteLevel(
                        group = group,
                        binding = binding,
                        data =
                            getBytesForExpression(
                                type = getUniformStoreType(environment, tu.getUniformDeclaration(group, binding)),
                                value = pipelineState.getUniformValue(group, binding),
                                offset = 0,
                            ),
                    ),
                )
            }
        }
        return result
    }

    private fun getBytesForExpression(
        type: Type,
        value: Expression,
        offset: Int,
    ): List<Int> =
        when (type) {
            is Type.Struct -> {
                val result = mutableListOf<Int>()
                val structValue = value as Expression.StructValueConstructor
                for (i in 0..<type.members.size) {
                    result +=
                        getBytesForExpression(
                            type = type.members[i].second,
                            value = structValue.args[i],
                            offset = result.size,
                        )
                }
                result
            }
            is Type.Vector -> {
                val vectorValue = value as Expression.VectorValueConstructor
                if (type.elementType !in setOf(Type.I32, Type.U32, Type.F32)) {
                    TODO("Unsupported vector type: $type")
                }
                val result = mutableListOf<Int>()
                when (type.width) {
                    2 -> {
                        while (((offset + result.size) % 8) != 0) {
                            result.add(0)
                        }
                    }
                    3, 4 -> {
                        while (((offset + result.size) % 16) != 0) {
                            result.add(0)
                        }
                    }
                }
                for (i in 0..<type.width) {
                    result +=
                        getBytesForExpression(
                            type = type.elementType,
                            value = vectorValue.args[i],
                            offset = result.size,
                        )
                }
                result
            }
            is Type.F32 -> {
                val floatValue = value as Expression.FloatLiteral
                val parsedFloat: Float = floatValue.text.toFloat()
                val byteArray =
                    ByteBuffer
                        .allocate(4)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putFloat(parsedFloat)
                        .array()
                byteArray.map { i -> i.toInt() and 0xFF }
            }
            is Type.I32, is Type.U32 -> intLiteralToBytes(value)
            is Type.Array -> {
                require(type.elementCount != null) { "An array uniform must have a fixed length" }
                val result = mutableListOf<Int>()
                val structValue = value as Expression.ArrayValueConstructor
                for (i in 0..<type.elementCount) {
                    result +=
                        getBytesForExpression(
                            type = type.elementType,
                            value = structValue.args[i],
                            offset = result.size,
                        )
                }
                result
            }
            else -> TODO("Support for $type not implemented yet")
        }
}

private fun intLiteralToBytes(value: Expression): List<Int> {
    val intValue = value as Expression.IntLiteral
    val parsedInt: Int =
        intValue.text
            .removeSuffix("i")
            .removeSuffix("u")
            .toInt()
    val byteArray =
        ByteBuffer
            .allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(parsedInt)
            .array()
    return byteArray.map { i -> i.toInt() }
}

fun createShaderJob(
    shaderText: String,
    uniformBuffers: List<UniformBufferInfoByteLevel>,
): ShaderJob {
    val tu: TranslationUnit = parseFromString(shaderText, LoggingParseErrorListener())
    val environment: ResolvedEnvironment = resolve(tu)
    val uniformValues: MutableMap<Int, MutableMap<Int, Expression>> = mutableMapOf()
    for (uniformBuffer in uniformBuffers) {
        val group: Int = uniformBuffer.group
        val binding: Int = uniformBuffer.binding
        val bufferBytes: List<UByte> = uniformBuffer.data.map(Int::toUByte)

        val uniformType: Type = getUniformStoreType(environment, tu.getUniformDeclaration(group, binding))

        val (literalExpr, newBufferByteIndex) =
            literalExprFromBytes(
                uniformType,
                bufferBytes,
                0,
            )
        assert(newBufferByteIndex == bufferBytes.size)
        uniformValues.getOrPut(group, { mutableMapOf() })[binding] = literalExpr
    }
    return ShaderJob(tu, PipelineState(uniformValues))
}

private fun getUniformStoreType(
    environment: ResolvedEnvironment,
    uniformDeclaration: GlobalDecl.Variable,
) = if (uniformDeclaration.typeDecl is TypeDecl.NamedType) {
    getUniformStoreTypeByName(environment, uniformDeclaration.typeDecl.name)
} else {
    getUniformStoreTypeByName(environment, uniformDeclaration.name)
}

private fun getUniformStoreTypeByName(
    environment: ResolvedEnvironment,
    uniformName: String,
) = (environment.globalScope.getEntry(uniformName) as ScopeEntry.TypedDecl).type.asStoreTypeIfReference()

private fun literalExprFromBytes(
    type: Type,
    bufferBytes: List<UByte>,
    bufferByteIndex: Int,
): Pair<Expression, Int> {
    assert(bufferByteIndex % 4 == 0)
    when (type) {
        is Type.Struct -> {
            val memberExpressions = mutableListOf<Expression>()
            var currentBufferByteIndex = bufferByteIndex
            for (member in type.members) {
                val (memberExpression, updatedIndex) =
                    literalExprFromBytes(
                        member.second,
                        bufferBytes,
                        currentBufferByteIndex,
                    )
                memberExpressions.add(memberExpression)
                currentBufferByteIndex = updatedIndex
            }

            return Pair(
                Expression.StructValueConstructor(
                    constructorName = type.name,
                    args = memberExpressions,
                ),
                currentBufferByteIndex,
            )
        }
        is Type.I32, is Type.U32 -> {
            return Pair(
                Expression.IntLiteral(
                    text =
                        wordFromBytes(bufferBytes, bufferByteIndex).toString() +
                            if (type is Type.I32) {
                                "i"
                            } else {
                                "u"
                            },
                ),
                bufferByteIndex + 4,
            )
        }
        is Type.F32 -> {
            return Pair(
                Expression.FloatLiteral(
                    text =
                        BigDecimal
                            .valueOf(
                                Float
                                    .fromBits(
                                        wordFromBytes(
                                            bufferBytes,
                                            bufferByteIndex,
                                        ),
                                    ).toDouble(),
                            ).toPlainString() + "f",
                ),
                bufferByteIndex + 4,
            )
        }
        is Type.Vector -> {
            when (type.width) {
                2 -> {
                    var currentIndex = bufferByteIndex
                    if (currentIndex % 8 != 0) {
                        currentIndex += 4
                    }
                    assert(currentIndex % 8 == 0)
                    val (literalX, indexAfterX) = literalExprFromBytes(type.elementType, bufferBytes, currentIndex)
                    val (literalY, indexAfterY) = literalExprFromBytes(type.elementType, bufferBytes, indexAfterX)
                    return Pair(
                        Expression.Vec2ValueConstructor(
                            args = listOf(literalX, literalY),
                        ),
                        indexAfterY,
                    )
                }
                3 -> {
                    var currentIndex = bufferByteIndex
                    while (currentIndex % 16 != 0) {
                        currentIndex += 4
                    }
                    val (literalX, indexAfterX) = literalExprFromBytes(type.elementType, bufferBytes, currentIndex)
                    val (literalY, indexAfterY) = literalExprFromBytes(type.elementType, bufferBytes, indexAfterX)
                    val (literalZ, indexAfterZ) = literalExprFromBytes(type.elementType, bufferBytes, indexAfterY)
                    return Pair(
                        Expression.Vec3ValueConstructor(
                            args = listOf(literalX, literalY, literalZ),
                        ),
                        indexAfterZ,
                    )
                }
                4 -> {
                    var currentIndex = bufferByteIndex
                    while (currentIndex % 16 != 0) {
                        currentIndex += 4
                    }
                    val (literalX, indexAfterX) = literalExprFromBytes(type.elementType, bufferBytes, currentIndex)
                    val (literalY, indexAfterY) = literalExprFromBytes(type.elementType, bufferBytes, indexAfterX)
                    val (literalZ, indexAfterZ) = literalExprFromBytes(type.elementType, bufferBytes, indexAfterY)
                    val (literalW, indexAfterW) = literalExprFromBytes(type.elementType, bufferBytes, indexAfterZ)
                    return Pair(
                        Expression.Vec4ValueConstructor(
                            args = listOf(literalX, literalY, literalZ, literalW),
                        ),
                        indexAfterW,
                    )
                }
                else -> throw UnsupportedOperationException("Bad vector size.")
            }
        }
        is Type.Array -> {
            check(type.elementCount != null) { "Cannot have a runtime sized uniform array" }
            var currentIndex = bufferByteIndex
            while (currentIndex % type.alignOf() != 0) {
                currentIndex++
            }
            val args: MutableList<Expression> = mutableListOf()
            repeat(type.elementCount) {
                val (literal, indexAfter) = literalExprFromBytes(type.elementType, bufferBytes, currentIndex)
                args.add(literal)
                currentIndex = indexAfter
            }

            return Pair(
                Expression.ArrayValueConstructor(
                    elementType = type.elementType.toTypeDecl(),
                    elementCount = Expression.IntLiteral(type.elementCount.toString()),
                    args = args,
                ),
                second = currentIndex,
            )
        }
        else -> TODO("Type $type not yet supported in uniform buffers.")
    }
}

private fun Type.Scalar.toTypeDecl(): TypeDecl.ScalarTypeDecl =
    when (this) {
        Type.Bool -> TypeDecl.Bool()
        Type.I32 -> TypeDecl.I32()
        Type.U32 -> TypeDecl.U32()
        Type.F16 -> TypeDecl.F16()
        Type.F32 -> TypeDecl.F32()
        Type.AbstractFloat -> throw UnsupportedOperationException("AbstractFloat cannot converted to TypeDecl")
        Type.AbstractInteger -> throw UnsupportedOperationException("AbstractInteger cannot converted to TypeDecl")
    }

private fun Type.toTypeDecl(): TypeDecl =
    when (this) {
        is Type.Scalar -> this.toTypeDecl()
        is Type.Vector ->
            when (this.width) {
                2 ->
                    TypeDecl.Vec2(
                        this.elementType.toTypeDecl(),
                    )
                3 ->
                    TypeDecl.Vec3(
                        this.elementType.toTypeDecl(),
                    )
                4 ->
                    TypeDecl.Vec4(
                        this.elementType.toTypeDecl(),
                    )
                else -> throw IllegalArgumentException("Bad vector size.")
            }
        is Type.Array ->
            TypeDecl.Array(
                elementType = this.elementType.toTypeDecl(),
                elementCount = this.elementCount?.let { Expression.IntLiteral(it.toString()) },
            )
        is Type.Struct ->
            TypeDecl.NamedType(
                name = this.name,
            )
        else -> TODO()
    }

private fun wordFromBytes(
    bufferBytes: List<UByte>,
    bufferByteIndex: Int,
): Int =
    (bufferBytes[bufferByteIndex + 0].toInt() and 0xFF shl 0) or
        (bufferBytes[bufferByteIndex + 1].toInt() and 0xFF shl 8) or
        (bufferBytes[bufferByteIndex + 2].toInt() and 0xFF shl 16) or
        (bufferBytes[bufferByteIndex + 3].toInt() and 0xFF shl 24)
