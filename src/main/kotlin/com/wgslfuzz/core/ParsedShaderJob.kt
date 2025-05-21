package com.wgslfuzz.core

class ParsedShaderJob(
    val tu: TranslationUnit,
    val uniformValues: Map<Int, Map<Int, Expression>>,
) {
    val environment: ResolvedEnvironment = resolve(tu)
}

fun parseShaderJob(shaderJob: ShaderJob): ParsedShaderJob {
    val tu: TranslationUnit = parseFromString(shaderJob.shaderText, LoggingParseErrorListener())
    val environment: ResolvedEnvironment = resolve(tu)
    val uniformValues: MutableMap<Int, MutableMap<Int, Expression>> = mutableMapOf()
    for (uniformBuffer in shaderJob.uniformBuffers) {
        val group: Int = uniformBuffer.group
        val binding: Int = uniformBuffer.binding
        val bufferBytes: List<UByte> = uniformBuffer.data.map(Int::toUByte)

        val uniformDeclaration = getUniformDeclaration(tu, group, binding)
        val structType =
            (
                environment.globalScope
                    .getEntry((uniformDeclaration.type as TypeDecl.NamedType).name) as ScopeEntry.Struct
            ).type

        val (literalExpr, newBufferByteIndex) =
            literalExprFromBytes(
                structType,
                bufferBytes,
                0,
            )
        assert(newBufferByteIndex == bufferBytes.size)
        uniformValues.getOrPut(group, { mutableMapOf() })[binding] = literalExpr
    }
    return ParsedShaderJob(tu, uniformValues)
}

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
                    structName = type.name,
                    args = memberExpressions,
                ),
                currentBufferByteIndex,
            )
        }
        is Type.I32 -> {
            return Pair(
                Expression.IntLiteral(
                    text = wordFromBytes(bufferBytes, bufferByteIndex).toString(),
                ),
                bufferByteIndex + 4,
            )
        }
        is Type.F32 -> {
            return Pair(
                Expression.FloatLiteral(
                    text = Float.fromBits(wordFromBytes(bufferBytes, bufferByteIndex)).toString(),
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
        else -> TODO("Type $type not yet supported in uniform buffers.")
    }
}

private fun wordFromBytes(
    bufferBytes: List<UByte>,
    bufferByteIndex: Int,
): Int =
    (bufferBytes[bufferByteIndex + 0].toInt() and 0xFF shl 0) or
        (bufferBytes[bufferByteIndex + 1].toInt() and 0xFF shl 8) or
        (bufferBytes[bufferByteIndex + 2].toInt() and 0xFF shl 16) or
        (bufferBytes[bufferByteIndex + 3].toInt() and 0xFF shl 24)
