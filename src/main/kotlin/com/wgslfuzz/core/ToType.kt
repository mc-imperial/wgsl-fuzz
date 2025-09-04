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

fun TypeDecl.toType(
    scope: Scope,
    resolvedEnvironment: ResolvedEnvironment,
): Type =
    when (this) {
        is TypeDecl.Array ->
            Type.Array(
                elementType = this.elementType.toType(scope, resolvedEnvironment),
                elementCount =
                    this.elementCount?.let {
                        evaluateToInt(it, scope, resolvedEnvironment)
                    },
            )
        is TypeDecl.NamedType -> {
            when (val scopeEntry = scope.getEntry(this.name)) {
                is ScopeEntry.TypeAlias -> {
                    scopeEntry.type
                }
                is ScopeEntry.Struct -> {
                    scopeEntry.type
                }
                null -> {
                    when (this.name) {
                        "vec2f" -> Type.Vector(2, Type.F32)
                        "vec3f" -> Type.Vector(3, Type.F32)
                        "vec4f" -> Type.Vector(4, Type.F32)
                        "vec2i" -> Type.Vector(2, Type.I32)
                        "vec3i" -> Type.Vector(3, Type.I32)
                        "vec4i" -> Type.Vector(4, Type.I32)
                        "vec2u" -> Type.Vector(2, Type.U32)
                        "vec3u" -> Type.Vector(3, Type.U32)
                        "vec4u" -> Type.Vector(4, Type.U32)
                        "mat2x2f" -> Type.Matrix(2, 2, Type.F32)
                        "mat2x3f" -> Type.Matrix(2, 3, Type.F32)
                        "mat2x4f" -> Type.Matrix(2, 4, Type.F32)
                        "mat3x2f" -> Type.Matrix(3, 2, Type.F32)
                        "mat3x3f" -> Type.Matrix(3, 3, Type.F32)
                        "mat3x4f" -> Type.Matrix(3, 4, Type.F32)
                        "mat4x2f" -> Type.Matrix(4, 2, Type.F32)
                        "mat4x3f" -> Type.Matrix(4, 3, Type.F32)
                        "mat4x4f" -> Type.Matrix(4, 4, Type.F32)
                        "mat2x2h" -> Type.Matrix(2, 2, Type.F16)
                        "mat2x3h" -> Type.Matrix(2, 3, Type.F16)
                        "mat2x4h" -> Type.Matrix(2, 4, Type.F16)
                        "mat3x2h" -> Type.Matrix(3, 2, Type.F16)
                        "mat3x3h" -> Type.Matrix(3, 3, Type.F16)
                        "mat3x4h" -> Type.Matrix(3, 4, Type.F16)
                        "mat4x2h" -> Type.Matrix(4, 2, Type.F16)
                        "mat4x3h" -> Type.Matrix(4, 3, Type.F16)
                        "mat4x4h" -> Type.Matrix(4, 4, Type.F16)
                        else -> throw UnsupportedOperationException("Unknown typed declaration ${this.name}")
                    }
                }
                else -> {
                    throw IllegalArgumentException(
                        "Non-type declaration associated with ${this.name}, which is used where a type is required",
                    )
                }
            }
        }
        is TypeDecl.Pointer ->
            Type.Pointer(
                pointeeType =
                    this.pointeeType.toType(
                        scope,
                        resolvedEnvironment,
                    ),
                addressSpace = this.addressSpace,
                accessMode = this.accessMode ?: AccessMode.READ_WRITE,
            )
        is TypeDecl.ScalarTypeDecl -> this.toType()
        is TypeDecl.VectorTypeDecl -> this.toType()
        is TypeDecl.MatrixTypeDecl -> this.toType()
        is TypeDecl.Atomic -> {
            when (val targetType = this.targetType.toType(scope, resolvedEnvironment)) {
                Type.I32 -> Type.AtomicI32
                Type.U32 -> Type.AtomicU32
                else -> throw IllegalArgumentException("Inappropriate target type $targetType for atomic type")
            }
        }
        is TypeDecl.SamplerComparison -> Type.SamplerComparison
        is TypeDecl.SamplerRegular -> Type.SamplerRegular
        is TypeDecl.TextureDepth2D -> Type.Texture.Depth2D
        is TypeDecl.TextureDepth2DArray -> Type.Texture.Depth2DArray
        is TypeDecl.TextureDepthCube -> Type.Texture.DepthCube
        is TypeDecl.TextureDepthCubeArray -> Type.Texture.DepthCubeArray
        is TypeDecl.TextureDepthMultisampled2D -> Type.Texture.DepthMultisampled2D
        is TypeDecl.TextureExternal -> Type.Texture.External
        is TypeDecl.TextureMultisampled2d -> {
            when (val sampledType = this.sampledType.toType(scope, resolvedEnvironment)) {
                is Type.Scalar -> Type.Texture.Multisampled2d(sampledType)
                else -> throw IllegalArgumentException("texture_multisampled_2d requires a scalar sampler type")
            }
        }
        is TypeDecl.TextureSampled1D -> {
            when (val sampledType = this.sampledType.toType(scope, resolvedEnvironment)) {
                is Type.Scalar -> Type.Texture.Sampled1D(sampledType)
                else -> throw IllegalArgumentException("texture_1d requires a scalar sampler type")
            }
        }
        is TypeDecl.TextureSampled2D -> {
            when (val sampledType = this.sampledType.toType(scope, resolvedEnvironment)) {
                is Type.Scalar -> Type.Texture.Sampled2D(sampledType)
                else -> throw IllegalArgumentException("texture_2d requires a scalar sampler type")
            }
        }
        is TypeDecl.TextureSampled2DArray -> {
            when (val sampledType = this.sampledType.toType(scope, resolvedEnvironment)) {
                is Type.Scalar -> Type.Texture.Sampled2DArray(sampledType)
                else -> throw IllegalArgumentException("texture_2d_array requires a scalar sampler type")
            }
        }
        is TypeDecl.TextureSampled3D -> {
            when (val sampledType = this.sampledType.toType(scope, resolvedEnvironment)) {
                is Type.Scalar -> Type.Texture.Sampled3D(sampledType)
                else -> throw IllegalArgumentException("texture_3d requires a scalar sampler type")
            }
        }
        is TypeDecl.TextureSampledCube -> {
            when (val sampledType = this.sampledType.toType(scope, resolvedEnvironment)) {
                is Type.Scalar -> Type.Texture.SampledCube(sampledType)
                else -> throw IllegalArgumentException("texture_cube requires a scalar sampler type")
            }
        }
        is TypeDecl.TextureSampledCubeArray -> {
            when (val sampledType = this.sampledType.toType(scope, resolvedEnvironment)) {
                is Type.Scalar -> Type.Texture.SampledCubeArray(sampledType)
                else -> throw IllegalArgumentException("texture_cube_array requires a scalar sampler type")
            }
        }
        is TypeDecl.TextureStorage1D -> Type.Texture.Storage1D(this.format, this.accessMode)
        is TypeDecl.TextureStorage2D -> Type.Texture.Storage2D(this.format, this.accessMode)
        is TypeDecl.TextureStorage2DArray -> Type.Texture.Storage2DArray(this.format, this.accessMode)
        is TypeDecl.TextureStorage3D -> Type.Texture.Storage3D(this.format, this.accessMode)
    }

fun TypeDecl.FloatTypeDecl.toType(): Type.Float =
    when (this) {
        is TypeDecl.F16 -> Type.F16
        is TypeDecl.F32 -> Type.F32
    }

fun TypeDecl.ScalarTypeDecl.toType(): Type.Scalar =
    when (this) {
        is TypeDecl.FloatTypeDecl -> this.toType()
        is TypeDecl.Bool -> Type.Bool
        is TypeDecl.I32 -> Type.I32
        is TypeDecl.U32 -> Type.U32
    }

fun TypeDecl.VectorTypeDecl.toType(): Type.Vector {
    val elementType = elementType.toType()
    return when (this) {
        is TypeDecl.Vec2 -> Type.Vector(2, elementType)
        is TypeDecl.Vec3 -> Type.Vector(3, elementType)
        is TypeDecl.Vec4 -> Type.Vector(4, elementType)
    }
}

fun TypeDecl.MatrixTypeDecl.toType(): Type.Matrix {
    val elementType = elementType.toType()
    return when (this) {
        is TypeDecl.Mat2x2 -> {
            Type.Matrix(2, 2, elementType)
        }

        is TypeDecl.Mat2x3 -> {
            Type.Matrix(2, 3, elementType)
        }

        is TypeDecl.Mat2x4 -> {
            Type.Matrix(2, 4, elementType)
        }

        is TypeDecl.Mat3x2 -> {
            Type.Matrix(3, 2, elementType)
        }

        is TypeDecl.Mat3x3 -> {
            Type.Matrix(3, 3, elementType)
        }

        is TypeDecl.Mat3x4 -> {
            Type.Matrix(3, 4, elementType)
        }

        is TypeDecl.Mat4x2 -> {
            Type.Matrix(4, 2, elementType)
        }

        is TypeDecl.Mat4x3 -> {
            Type.Matrix(4, 3, elementType)
        }

        is TypeDecl.Mat4x4 -> {
            Type.Matrix(4, 4, elementType)
        }
    }
}

fun Expression.ArrayValueConstructor.toType(
    scope: Scope,
    resolvedEnvironment: ResolvedEnvironment,
): Type.Array {
    val elementType: Type =
        elementType?.toType(scope, resolvedEnvironment) ?: if (args.isEmpty()) {
            throw RuntimeException("Cannot work out element type of empty array constructor")
        } else {
            findCommonType(args, resolvedEnvironment)
        }
    val elementCount: Int =
        elementCount?.let {
            evaluateToInt(elementCount, scope, resolvedEnvironment)
        } ?: args.size
    return Type.Array(elementType, elementCount)
}

fun Expression.FunctionCall.toType(
    scope: Scope,
    resolvedEnvironment: ResolvedEnvironment,
): Type =
    when (val scopeEntry = scope.getEntry(callee)) {
        null ->
            when (val calleeName = callee) {
                // 1-argument functions with return type same as argument type, allowing for the case where both are
                // abstract.
                // TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/37) go through these and check which ones support
                //  abstract types. Those that need concretisation should be moved to the next case.
                "abs", "acos", "acosh", "asin", "asinh", "atan", "atanh", "ceil", "cos", "cosh", "degrees", "dpdx",
                "dpdxCoarse", "dpdxFine", "dpdy", "dpdyCoarse", "dpdyFine", "exp", "exp2", "floor", "fract", "fwidth",
                "fwidthCoarse", "fwidthFine", "inverseSqrt", "log", "log2", "normalize", "quantizeToF16", "radians",
                "round", "saturate", "sign", "sin", "sinh", "sqrt", "tan", "tanh", "trunc",
                -> {
                    if (args.size != 1) {
                        throw RuntimeException("$calleeName requires one argument")
                    } else {
                        resolvedEnvironment.typeOf(args[0])
                    }
                }
                // 1-argument homogeneous functions with return type same as concretisation of argument type
                "reverseBits",
                -> {
                    if (args.size != 1) {
                        throw RuntimeException("$calleeName requires one argument")
                    } else {
                        defaultConcretizationOf(resolvedEnvironment.typeOf(args[0]))
                    }
                }
                // 2-argument homogeneous functions with return type same as argument type
                // TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/37): go through these and check which ones support
                //  abstract types. Those that don't need concretisation
                "atan2", "max", "min", "pow", "reflect", "step" ->
                    if (args.size != 2) {
                        throw RuntimeException("$calleeName requires two arguments")
                    } else {
                        findCommonType(args, resolvedEnvironment)
                    }
                // 3-argument homogeneous functions with return type same as argument type
                "clamp", "faceForward", "fma", "smoothstep" ->
                    if (args.size != 3) {
                        throw RuntimeException("$calleeName requires three arguments")
                    } else {
                        findCommonType(args, resolvedEnvironment)
                    }
                "all", "any" -> Type.Bool
                "arrayLength" -> Type.U32
                "atomicAdd", "atomicSub", "atomicMax", "atomicMin", "atomicAnd", "atomicOr", "atomicXor", "atomicExchange" -> {
                    if (args.size != 2) {
                        throw RuntimeException("$calleeName builtin takes two arguments")
                    }
                    val argType = resolvedEnvironment.typeOf(args[0])
                    if (argType !is Type.Pointer || argType.pointeeType !is Type.Atomic) {
                        throw RuntimeException("$calleeName requires a pointer to an atomic integer")
                    }
                    argType.pointeeType.targetType
                }
                "atomicCompareExchangeWeak" -> {
                    if (args.size != 3) {
                        throw RuntimeException("atomicCompareExchangeWeak builtin takes three arguments")
                    }
                    val argType = resolvedEnvironment.typeOf(args[0])
                    if (argType !is Type.Pointer || argType.pointeeType !is Type.Atomic) {
                        throw RuntimeException("atomicCompareExchangeWeak requires a pointer to an atomic integer")
                    }
                    when (argType.pointeeType.targetType) {
                        Type.I32 -> AtomicCompareExchangeResultI32
                        Type.U32 -> AtomicCompareExchangeResultU32
                        Type.AbstractInteger -> throw RuntimeException("An atomic integer should not have an abstract target type")
                    }
                }
                "atomicLoad" -> {
                    if (args.size != 1) {
                        throw RuntimeException("atomicLoad builtin takes one argument")
                    }
                    val argType = resolvedEnvironment.typeOf(args[0])
                    if (argType !is Type.Pointer || argType.pointeeType !is Type.Atomic) {
                        throw RuntimeException("atomicLoad requires a pointer to an atomic integer")
                    }
                    argType.pointeeType.targetType
                }
                "bitcast" -> {
                    if (templateParameter == null) {
                        throw RuntimeException("bitcast requires a template parameter for the target type")
                    }
                    templateParameter.toType(scope, resolvedEnvironment)
                }
                "countLeadingZeros", "countOneBits", "countTrailingZeros" -> {
                    if (args.size != 1) {
                        throw RuntimeException("$calleeName requires one argument")
                    } else {
                        defaultConcretizationOf(resolvedEnvironment.typeOf(args[0]))
                    }
                }
                "cross" -> {
                    if (args.size != 2) {
                        throw RuntimeException("cross builtin takes two arguments")
                    }
                    val arg1Type = resolvedEnvironment.typeOf(args[0]).asStoreTypeIfReference()
                    val arg2Type = resolvedEnvironment.typeOf(args[1]).asStoreTypeIfReference()
                    if (arg1Type !is Type.Vector || arg2Type !is Type.Vector) {
                        throw RuntimeException("cross builtin requires vector arguments")
                    }
                    if (arg1Type.width != 3 || arg2Type.width != 3) {
                        throw RuntimeException("cross builtin requires vec3 arguments")
                    }
                    if (arg1Type.elementType !is Type.Float || arg2Type.elementType !is Type.Float) {
                        throw RuntimeException("cross builtin only works with float vectors")
                    }
                    if (arg1Type.elementType != arg2Type.elementType) {
                        throw RuntimeException("Mismatched vector elemenet types for cross builtin")
                    }
                    arg1Type
                }
                "determinant" -> {
                    if (args.size != 1) {
                        throw RuntimeException("determinant builtin function requires one argument")
                    }
                    val argType = resolvedEnvironment.typeOf(args[0]).asStoreTypeIfReference()
                    if (argType !is Type.Matrix) {
                        throw RuntimeException("determinant builtin function requires a matrix argument")
                    }
                    if (argType.numRows != argType.numCols) {
                        throw RuntimeException("determinant builtin function requires a square matrix argument")
                    }
                    argType.elementType
                }
                "distance" -> {
                    if (args.size != 2) {
                        throw RuntimeException("$calleeName requires two arguments")
                    }
                    val commonType = findCommonType(args, resolvedEnvironment).asStoreTypeIfReference()
                    if (commonType is Type.Vector) {
                        commonType.elementType
                    } else {
                        commonType
                    }
                }
                "dot" -> {
                    if (args.size != 2) {
                        throw RuntimeException("dot requires two arguments")
                    }
                    val commonType = findCommonType(args, resolvedEnvironment).asStoreTypeIfReference()
                    if (commonType is Type.Vector) {
                        commonType.elementType
                    } else {
                        throw RuntimeException("dot requires vector arguments")
                    }
                }
                "dot4U8Packed" -> Type.U32
                "dot4I8Packed" -> Type.I32
                "extractBits" -> {
                    if (args.size != 3) {
                        throw RuntimeException("extractBits expects three arguments")
                    }
                    defaultConcretizationOf(resolvedEnvironment.typeOf(args[0]))
                }
                "firstLeadingBit", "firstTrailingBit" ->
                    if (args.size != 1) {
                        throw RuntimeException("$calleeName requires one argument")
                    } else {
                        defaultConcretizationOf(resolvedEnvironment.typeOf(args[0]))
                    }
                "frexp" -> {
                    if (args.size != 1) {
                        throw RuntimeException("frexp requires one argument")
                    }
                    when (val argType = resolvedEnvironment.typeOf(args[0]).asStoreTypeIfReference()) {
                        Type.F16 -> FrexpResultF16
                        Type.F32 -> FrexpResultF32
                        Type.AbstractFloat, Type.AbstractInteger -> FrexpResultAbstract
                        is Type.Vector -> {
                            when (argType.elementType) {
                                Type.F16 -> {
                                    when (argType.width) {
                                        2 -> FrexpResultVec2F16
                                        3 -> FrexpResultVec3F16
                                        4 -> FrexpResultVec4F16
                                        else -> throw RuntimeException("Bad vector size")
                                    }
                                }
                                Type.F32 -> {
                                    when (argType.width) {
                                        2 -> FrexpResultVec2F32
                                        3 -> FrexpResultVec3F32
                                        4 -> FrexpResultVec4F32
                                        else -> throw RuntimeException("Bad vector size")
                                    }
                                }
                                Type.AbstractFloat -> {
                                    when (argType.width) {
                                        2 -> FrexpResultVec2Abstract
                                        3 -> FrexpResultVec3Abstract
                                        4 -> FrexpResultVec4Abstract
                                        else -> throw RuntimeException("Bad vector size")
                                    }
                                }
                                else -> throw RuntimeException("Unexpected vector element type of frexp vector argument")
                            }
                        }
                        else -> throw RuntimeException("Unexpected type of frexp argument")
                    }
                }
                "insertBits" -> {
                    if (args.size != 4) {
                        throw RuntimeException("$calleeName requires three arguments")
                    } else {
                        defaultConcretizationOf(findCommonType(args.dropLast(2), resolvedEnvironment))
                    }
                }
                "ldexp" -> {
                    if (args.size != 2) {
                        throw RuntimeException("$calleeName requires two arguments")
                    }
                    val arg1Type = resolvedEnvironment.typeOf(args[0])
                    val arg2Type = resolvedEnvironment.typeOf(args[1])
                    if (arg1Type.isAbstract() && !arg2Type.isAbstract()) {
                        defaultConcretizationOf(arg1Type)
                    } else {
                        arg1Type
                    }
                }
                "length" -> {
                    if (args.size != 1) {
                        throw RuntimeException("length requires one argument")
                    }
                    when (val argType = resolvedEnvironment.typeOf(args[0]).asStoreTypeIfReference()) {
                        is Type.Float -> argType
                        is Type.Vector -> argType.elementType
                        else -> throw RuntimeException("Unsupported argument type for length builtin function")
                    }
                }
                "mat2x2f" -> Type.Matrix(2, 2, Type.F32)
                "mat2x3f" -> Type.Matrix(2, 3, Type.F32)
                "mat2x4f" -> Type.Matrix(2, 4, Type.F32)
                "mat3x2f" -> Type.Matrix(3, 2, Type.F32)
                "mat3x3f" -> Type.Matrix(3, 3, Type.F32)
                "mat3x4f" -> Type.Matrix(3, 4, Type.F32)
                "mat4x2f" -> Type.Matrix(4, 2, Type.F32)
                "mat4x3f" -> Type.Matrix(4, 3, Type.F32)
                "mat4x4f" -> Type.Matrix(4, 4, Type.F32)
                "mat2x2h" -> Type.Matrix(2, 2, Type.F16)
                "mat2x3h" -> Type.Matrix(2, 3, Type.F16)
                "mat2x4h" -> Type.Matrix(2, 4, Type.F16)
                "mat3x2h" -> Type.Matrix(3, 2, Type.F16)
                "mat3x3h" -> Type.Matrix(3, 3, Type.F16)
                "mat3x4h" -> Type.Matrix(3, 4, Type.F16)
                "mat4x2h" -> Type.Matrix(4, 2, Type.F16)
                "mat4x3h" -> Type.Matrix(4, 3, Type.F16)
                "mat4x4h" -> Type.Matrix(4, 4, Type.F16)
                // Possible issue with these functions when called in its (vector, vector, scalar) form:
                // the first two vectors might be abstract and the scalar concrete. In this case a concrete type should
                // be returned (and won't be here).
                "mix", "refract" -> findCommonType(args.dropLast(1), resolvedEnvironment)
                "modf" -> {
                    if (args.size != 1) {
                        throw RuntimeException("modf requires one argument")
                    }
                    when (val argType = resolvedEnvironment.typeOf(args[0]).asStoreTypeIfReference()) {
                        Type.F16 -> ModfResultF16
                        Type.F32 -> ModfResultF32
                        Type.AbstractFloat -> ModfResultAbstract
                        is Type.Vector -> {
                            when (argType.elementType) {
                                Type.F16 -> {
                                    when (argType.width) {
                                        2 -> ModfResultVec2F16
                                        3 -> ModfResultVec3F16
                                        4 -> ModfResultVec4F16
                                        else -> throw RuntimeException("Bad vector size")
                                    }
                                }
                                Type.F32 -> {
                                    when (argType.width) {
                                        2 -> ModfResultVec2F32
                                        3 -> ModfResultVec3F32
                                        4 -> ModfResultVec4F32
                                        else -> throw RuntimeException("Bad vector size")
                                    }
                                }
                                Type.AbstractFloat -> {
                                    when (argType.width) {
                                        2 -> ModfResultVec2Abstract
                                        3 -> ModfResultVec3Abstract
                                        4 -> ModfResultVec4Abstract
                                        else -> throw RuntimeException("Bad vector size")
                                    }
                                }
                                else -> throw RuntimeException("Unexpected vector element type of modf vector argument")
                            }
                        }
                        else -> throw RuntimeException("Unexpected type of modf argument")
                    }
                }
                "pack4x8snorm", "pack4x8unorm", "pack4xI8", "pack4xU8", "pack4xI8Clamp", "pack4xU8Clamp",
                "pack2x16snorm", "pack2x16unorm", "pack2x16float",
                -> Type.U32
                "select" -> {
                    if (args.size != 3) {
                        throw RuntimeException("select requires three arguments")
                    } else {
                        findCommonType(args.dropLast(1), resolvedEnvironment)
                    }
                }
                "textureDimensions" -> {
                    if (args.size !in 1..2) {
                        throw RuntimeException("textureDimensions requires two arguments")
                    }
                    val textureType = resolvedEnvironment.typeOf(args[0]).asStoreTypeIfReference()
                    if (textureType !is Type.Texture) {
                        throw RuntimeException("Type of first argument to textureDimensions must be a texture")
                    }
                    if (args.size == 1) {
                        when (textureType) {
                            Type.Texture.Depth2D -> Type.Vector(2, Type.U32)
                            Type.Texture.Depth2DArray -> Type.Vector(2, Type.U32)
                            Type.Texture.DepthCube -> Type.Vector(2, Type.U32)
                            Type.Texture.DepthCubeArray -> Type.Vector(2, Type.U32)
                            Type.Texture.DepthMultisampled2D -> Type.Vector(2, Type.U32)
                            Type.Texture.External -> Type.Vector(2, Type.U32)
                            is Type.Texture.Multisampled2d -> Type.Vector(2, Type.U32)
                            is Type.Texture.Sampled1D -> Type.U32
                            is Type.Texture.Sampled2D -> Type.Vector(2, Type.U32)
                            is Type.Texture.Sampled2DArray -> Type.Vector(2, Type.U32)
                            is Type.Texture.Sampled3D -> Type.Vector(3, Type.U32)
                            is Type.Texture.SampledCube -> Type.Vector(2, Type.U32)
                            is Type.Texture.SampledCubeArray -> Type.Vector(2, Type.U32)
                            is Type.Texture.Storage1D -> Type.U32
                            is Type.Texture.Storage2D -> Type.Vector(2, Type.U32)
                            is Type.Texture.Storage2DArray -> Type.Vector(2, Type.U32)
                            is Type.Texture.Storage3D -> Type.Vector(2, Type.U32)
                        }
                    } else {
                        assert(args.size == 2)
                        when (textureType) {
                            Type.Texture.Depth2D -> Type.Vector(2, Type.U32)
                            Type.Texture.Depth2DArray -> Type.Vector(2, Type.U32)
                            Type.Texture.DepthCube -> Type.Vector(2, Type.U32)
                            Type.Texture.DepthCubeArray -> Type.Vector(2, Type.U32)
                            is Type.Texture.Sampled1D -> Type.U32
                            is Type.Texture.Sampled2D -> Type.Vector(2, Type.U32)
                            is Type.Texture.Sampled2DArray -> Type.Vector(2, Type.U32)
                            is Type.Texture.Sampled3D -> Type.Vector(3, Type.U32)
                            is Type.Texture.SampledCube -> Type.Vector(2, Type.U32)
                            is Type.Texture.SampledCubeArray -> Type.Vector(2, Type.U32)
                            else -> throw RuntimeException("Unsuitable texture argument for textureDimensions with level")
                        }
                    }
                }
                "textureGather" -> {
                    if (args.size < 2) {
                        throw RuntimeException("$calleeName requires at least 2 arguments")
                    }
                    when (resolvedEnvironment.typeOf(args[0]).asStoreTypeIfReference()) {
                        Type.Texture.Depth2D, Type.Texture.DepthCube, Type.Texture.Depth2DArray, Type.Texture.DepthCubeArray ->
                            Type.Vector(
                                4,
                                Type.F32,
                            )
                        else -> {
                            when (
                                val arg2Type =
                                    resolvedEnvironment
                                        .typeOf(
                                            args[1],
                                        ).asStoreTypeIfReference()
                            ) {
                                is Type.Texture.Sampled -> Type.Vector(4, arg2Type.sampledType)
                                else -> throw RuntimeException("$calleeName requires a suitable texture as its first or second argument")
                            }
                        }
                    }
                }
                "textureGatherCompare" -> Type.Vector(4, Type.F32)
                "textureLoad" -> {
                    if (args.isEmpty()) {
                        throw RuntimeException("textureLoad requires a first argument of texture type")
                    }
                    val textureArg = resolvedEnvironment.typeOf(args[0]).asStoreTypeIfReference()
                    if (textureArg !is Type.Texture) {
                        throw RuntimeException("textureLoad requires a first argument of texture type")
                    }
                    when (textureArg) {
                        Type.Texture.Depth2D, Type.Texture.Depth2DArray, Type.Texture.DepthMultisampled2D -> Type.F32
                        Type.Texture.External -> Type.Vector(4, Type.F32)
                        is Type.Texture.Multisampled2d -> Type.Vector(4, textureArg.sampledType)
                        is Type.Texture.Sampled1D -> Type.Vector(4, textureArg.sampledType)
                        is Type.Texture.Sampled2D -> Type.Vector(4, textureArg.sampledType)
                        is Type.Texture.Sampled2DArray -> Type.Vector(4, textureArg.sampledType)
                        is Type.Texture.Sampled3D -> Type.Vector(4, textureArg.sampledType)
                        is Type.Texture.Storage1D -> Type.Vector(4, textureArg.format.toVectorElementType())
                        is Type.Texture.Storage2D -> Type.Vector(4, textureArg.format.toVectorElementType())
                        is Type.Texture.Storage2DArray -> Type.Vector(4, textureArg.format.toVectorElementType())
                        is Type.Texture.Storage3D -> Type.Vector(4, textureArg.format.toVectorElementType())
                        else -> throw RuntimeException("textureLoad does not work on cube textures")
                    }
                }
                "textureNumLayers", "textureNumLevels", "textureNumSamples" -> Type.U32
                "textureSample", "textureSampleLevel" -> {
                    if (args.isEmpty()) {
                        throw RuntimeException("Not enough arguments provided to $calleeName")
                    } else {
                        when (
                            val textureType =
                                resolvedEnvironment.typeOf(args[0]).asStoreTypeIfReference()
                        ) {
                            is Type.Texture.Sampled ->
                                if (textureType.sampledType is Type.F32) {
                                    Type.Vector(4, Type.F32)
                                } else {
                                    throw RuntimeException("Incorrect sample type used with $calleeName")
                                }
                            Type.Texture.Depth2D, Type.Texture.Depth2DArray, Type.Texture.DepthCube, Type.Texture.DepthCubeArray -> Type.F32
                            else -> throw RuntimeException("First argument to $calleeName must be a suitable texture")
                        }
                    }
                }
                "textureSampleBaseClampToEdge", "textureSampleBias" -> Type.Vector(4, Type.F32)
                "textureSampleCompare", "textureSampleCompareLevel" -> Type.F32
                "textureSampleGrad" -> Type.Vector(4, Type.F32)
                "transpose" -> {
                    if (args.size != 1) {
                        throw RuntimeException("$calleeName requires one argument")
                    }
                    val arg1Type = resolvedEnvironment.typeOf(args[0]).asStoreTypeIfReference()
                    if (arg1Type is Type.Matrix) {
                        Type.Matrix(
                            numCols = arg1Type.numRows,
                            numRows = arg1Type.numCols,
                            elementType = arg1Type.elementType,
                        )
                    } else {
                        throw RuntimeException("$calleeName requires a matrix argument")
                    }
                }
                "unpack4x8snorm", "unpack4x8unorm", "unpack2x16snorm", "unpack2x16unorm", "unpack2x16float" ->
                    Type.Vector(
                        4,
                        Type.F32,
                    )
                "unpack4xI8" -> Type.Vector(4, Type.I32)
                "unpack4xU8" -> Type.Vector(4, Type.U32)
                "vec2i" -> Type.Vector(2, Type.I32)
                "vec3i" -> Type.Vector(3, Type.I32)
                "vec4i" -> Type.Vector(4, Type.I32)
                "vec2u" -> Type.Vector(2, Type.U32)
                "vec3u" -> Type.Vector(3, Type.U32)
                "vec4u" -> Type.Vector(4, Type.U32)
                "vec2f" -> Type.Vector(2, Type.F32)
                "vec3f" -> Type.Vector(3, Type.F32)
                "vec4f" -> Type.Vector(4, Type.F32)
                "vec2h" -> Type.Vector(2, Type.F16)
                "vec3h" -> Type.Vector(3, Type.F16)
                "vec4h" -> Type.Vector(4, Type.F16)
                "workgroupUniformLoad" -> {
                    if (args.size != 1) {
                        throw RuntimeException("workgroupUniformLoad requires one argument")
                    }
                    val argType = resolvedEnvironment.typeOf(args[0])
                    if (argType !is Type.Pointer) {
                        throw RuntimeException("workgroupUniformLoad requires a pointer argument")
                    }
                    argType.pointeeType
                }
                "subgroupAdd", "subgroupExclusiveAdd", "subgroupInclusiveAdd", "subgroupBroadcastFirst", "subgroupMax",
                "subgroupMin", "subgroupMul", "subgroupExclusiveMul", "subgroupInclusiveMul", "quadSwapDiagonal",
                "quadSwapX", "quadSwapY",
                -> {
                    if (args.size != 1) {
                        throw RuntimeException(
                            "$callee requires one argument of concrete numeric scalar or numeric vector type.",
                        )
                    }
                    // NOTE: The spec says the first argument T is 'concrete numeric scalar or numeric vector'.
                    // Since we are not type checking, we don't check if the type is concrete.
                    val argType = resolvedEnvironment.typeOf(args[0]).asStoreTypeIfReference()
                    if (!argType.isNumericScalar() && !argType.isNumericVector()) {
                        throw RuntimeException(
                            "$callee requires one argument of concrete numeric scalar or numeric vector type.",
                        )
                    }

                    argType
                }
                "subgroupAll", "subgroupAny" -> {
                    if (args.size != 1) {
                        throw RuntimeException(
                            "$callee requires one argument of concrete numeric scalar or numeric vector type.",
                        )
                    }
                    val argType = resolvedEnvironment.typeOf(args[0]).asStoreTypeIfReference()
                    if (argType !is Type.Bool) {
                        throw RuntimeException("$callee requires one argument of bool type")
                    }

                    argType
                }
                "subgroupAnd", "subgroupOr", "subgroupXor" -> {
                    if (args.size != 1) {
                        throw RuntimeException("$callee requires one of type i32, u32, vecN<i32>, or vecN<u32>")
                    }
                    val argType = resolvedEnvironment.typeOf(args[0]).asStoreTypeIfReference()
                    if (argType !is Type.I32 &&
                        argType !is Type.U32 &&
                        !(argType is Type.Vector && (argType.elementType is Type.I32 || argType.elementType is Type.U32))
                    ) {
                        throw RuntimeException("$callee requires one of type i32, u32, vecN<i32>, or vecN<u32>")
                    }

                    argType
                }
                "subgroupBallot" -> {
                    if (args.size != 1) {
                        throw RuntimeException("$callee requires one of type bool")
                    }
                    val argType = resolvedEnvironment.typeOf(args[0]).asStoreTypeIfReference()
                    if (argType !is Type.Bool) {
                        throw RuntimeException("$callee requires one of type bool")
                    }

                    Type.Vector(width = 4, elementType = Type.U32)
                }
                "subgroupElect" -> {
                    if (args.isNotEmpty()) {
                        throw RuntimeException("$callee takes no arguments")
                    }
                    Type.Bool
                }
                "subgroupBroadcast", "subgroupShuffle", "quadBroadcast" -> {
                    if (args.size != 2) {
                        throw RuntimeException("$callee requires two arguments")
                    }

                    val arg1Type = resolvedEnvironment.typeOf(args[0]).asStoreTypeIfReference()
                    val arg2Type = resolvedEnvironment.typeOf(args[1]).asStoreTypeIfReference()

                    // NOTE: The spec says the first argument T is 'concrete numeric scalar or numeric vector'.
                    // Since we are not type checking, we don't check if the type is concrete.
                    if (!arg1Type.isNumericScalar() && !arg1Type.isNumericVector()) {
                        throw RuntimeException(
                            "The first argument to $callee must be a concrete numeric scalar or numeric vector",
                        )
                    }
                    if (arg2Type !is Type.U32 && arg2Type !is Type.I32) {
                        throw RuntimeException("The second argument to $callee must be i32 or u32")
                    }
                    // TODO: This doesn't check that the second argument is a const expr in the correct range (which differs by function)

                    arg1Type
                }
                "subgroupShuffleDown", "subgroupShuffleUp", "subgroupShuffleXor" -> {
                    if (args.size != 2) {
                        throw RuntimeException("$callee requires two arguments")
                    }

                    val arg1Type = resolvedEnvironment.typeOf(args[0]).asStoreTypeIfReference()
                    val arg2Type = resolvedEnvironment.typeOf(args[1]).asStoreTypeIfReference()

                    // NOTE: The spec says the first argument T is 'concrete numeric scalar or numeric vector'.
                    // Since we are not type checking, we don't check if the type is concrete.
                    if (!arg1Type.isNumericScalar() && !arg1Type.isNumericVector()) {
                        throw RuntimeException(
                            "The first argument to $callee must be a concrete numeric scalar or numeric vector",
                        )
                    }
                    if (arg2Type !is Type.U32) {
                        throw RuntimeException("The second argument to $callee must be u32")
                    }
                    // TODO(JLJ): This doesn't check that the second argument is a const expr in the correct range

                    arg1Type
                }
                else -> TODO("Unsupported builtin function $calleeName")
            }
        is ScopeEntry.Function ->
            scopeEntry.type.returnType
                ?: throw RuntimeException(
                    "Call expression used with function $callee, which does not return a value",
                )
        else -> throw RuntimeException("Function call attempted on unknown callee $callee")
    }

fun Expression.toType(
    scope: Scope,
    resolvedEnvironment: ResolvedEnvironment,
): Type =
    when (this) {
        is Expression.FunctionCall -> this.toType(scope, resolvedEnvironment)
        is Expression.IndexLookup -> this.toType(resolvedEnvironment)
        is Expression.MemberLookup -> this.toType(resolvedEnvironment)
        is Expression.FloatLiteral ->
            if (this.text.endsWith("f")) {
                Type.F32
            } else if (this.text.endsWith("h")) {
                Type.F16
            } else {
                Type.AbstractFloat
            }
        is Expression.IntLiteral ->
            if (this.text.endsWith("u")) {
                Type.U32
            } else if (this.text.endsWith("i")) {
                Type.I32
            } else {
                Type.AbstractInteger
            }
        is Expression.BoolLiteral ->
            Type.Bool
        is Expression.Binary -> this.toType(resolvedEnvironment)
        is Expression.Unary -> this.toType(resolvedEnvironment)
        is Expression.Paren -> resolvedEnvironment.typeOf(this.target)
        is Expression.Identifier ->
            when (val scopeEntry = scope.getEntry(this.name)) {
                is ScopeEntry.TypedDecl -> scopeEntry.type
                else -> throw IllegalArgumentException("Identifier ${this.name} does not have a typed scope entry")
            }
        is Expression.BoolValueConstructor -> Type.Bool
        is Expression.I32ValueConstructor -> Type.I32
        is Expression.U32ValueConstructor -> Type.U32
        is Expression.F32ValueConstructor -> Type.F32
        is Expression.F16ValueConstructor -> Type.F16
        is Expression.VectorValueConstructor -> this.toType(scope, resolvedEnvironment)
        is Expression.MatrixValueConstructor -> this.toType(scope, resolvedEnvironment)
        is Expression.ArrayValueConstructor -> this.toType(scope, resolvedEnvironment)
        is Expression.StructValueConstructor ->
            when (val scopeEntry = scope.getEntry(this.constructorName)) {
                is ScopeEntry.Struct -> scopeEntry.type
                else -> throw IllegalArgumentException(
                    "Attempt to construct a struct with constructor ${this.constructorName}, which is not a struct type",
                )
            }
        is Expression.TypeAliasValueConstructor ->
            (
                scope.getEntry(
                    this.constructorName,
                ) as ScopeEntry.TypeAlias
            ).type
        is AugmentedExpression.IdentityOperation -> resolvedEnvironment.typeOf(this.originalExpression)
        is AugmentedExpression.ArbitraryExpression -> resolvedEnvironment.typeOf(this.expression)
        is AugmentedExpression.KnownValue -> {
            val knownValueType = resolvedEnvironment.typeOf(this.knownValue).asStoreTypeIfReference()
            val expressionType = resolvedEnvironment.typeOf(this.expression).asStoreTypeIfReference()
            if (knownValueType != expressionType) {
                throw RuntimeException("Types for known value expression and its corresponding obfuscated expression do not match.")
            }
            expressionType
        }
    }

fun Expression.IndexLookup.toType(resolvedEnvironment: ResolvedEnvironment): Type {
    fun resolveDirectIndexType(type: Type): Type =
        when (type) {
            is Type.Matrix -> Type.Vector(width = type.numRows, elementType = type.elementType)
            is Type.Vector -> type.elementType
            // TODO: Throw a shader creation error if the index is a const expr greater than the
            // element count of the array
            is Type.Array -> type.elementType
            else -> throw IllegalArgumentException("Index lookup attempted on unsuitable type $type")
        }

    // Check the type of the index is i32 or u32.
    // TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/94): This is not really strict enough.
    val indexType = resolvedEnvironment.typeOf(index).asStoreTypeIfReference()
    if (!indexType.isAbstractionOf(Type.I32) && !indexType.isAbstractionOf(Type.U32)) {
        throw IllegalArgumentException("Array index expression must be of type i32 or u32.")
    }

    /* When accessing an index through a memory view expression (i.e. through a pointer or reference),
    a reference type is returned. The store type of the reference is the same as the type of the elements in the
    target.

    For example, consider the following code snippet in the body of a function. The type of arr is
    ref<function, array<i32,2>, read_write>. The variable elem then has the type ref<function, i32, read_write>.

        var arr = array<i32, 2>(0,1);
        var elem = arr[0];

    The same thing applies to arrays and matrices. In the following example, the variable mat has the type
    ref<function, mat2x3<i32>, read_write>. The variable row has the type ref<function, vec3<i32>, read_write>.

        var mat = mat2x3<f32>(vec3<f32>(0,1, 2), vec3<f32>(3,4,5));
        var row = mat[0];
     */
    return when (val targetType = resolvedEnvironment.typeOf(target)) {
        // https://www.w3.org/TR/WGSL/#array-access-expr
        is Type.Pointer -> {
            val newStoreType = resolveDirectIndexType(targetType.pointeeType)
            Type.Reference(newStoreType, targetType.addressSpace, targetType.accessMode)
        }
        // https://www.w3.org/TR/WGSL/#array-access-expr
        is Type.Reference -> {
            val newStoreType = resolveDirectIndexType(targetType.storeType)
            Type.Reference(newStoreType, targetType.addressSpace, targetType.accessMode)
        }
        else -> resolveDirectIndexType(targetType)
    }
}

fun Expression.MemberLookup.toType(resolvedEnvironment: ResolvedEnvironment): Type {
    fun resolveDirectMemberLookupType(receiverType: Type): Type =
        when (receiverType) {
            is Type.Struct ->
                receiverType.members
                    .firstOrNull {
                        it.first == memberName
                    }?.second
                    ?: throw IllegalArgumentException(
                        "Struct with type $receiverType does not have a member $memberName",
                    )

            is Type.Vector ->
                // In the following, we could check whether the vector indices exist, e.g. using z on a vec2 is not be allowed.
                if (memberName in setOf("x", "y", "z", "w", "r", "g", "b", "a")) {
                    receiverType.elementType
                } else if (isSwizzle(memberName)) {
                    Type.Vector(memberName.length, receiverType.elementType)
                } else {
                    TODO()
                }

            else -> throw UnsupportedOperationException("Member lookup not implemented for receiver of type $receiverType")
        }

    /* When accessing a member through a memory view expression (i.e. through a pointer or reference),
    a reference type is returned. The store type of the reference is the same as the type of the member in the
    target.

    For example, consider the following code snippet in the body of a function. The type of `t` is
    ref<function, T, read_write>. The variable elem will then have the type ref<function, i32, read_write>.

        struct T {
            a: i32,
        }
        var t: T;
        var elem = t.a;
     */
    return when (val receiverType = resolvedEnvironment.typeOf(receiver)) {
        // https://www.w3.org/TR/WGSL/#component-reference-from-vector-memory-view
        is Type.Pointer -> {
            val newStoreType = resolveDirectMemberLookupType(receiverType.pointeeType)
            Type.Reference(newStoreType, receiverType.addressSpace, receiverType.accessMode)
        }
        // https://www.w3.org/TR/WGSL/#component-reference-from-vector-memory-view
        is Type.Reference -> {
            val newStoreType = resolveDirectMemberLookupType(receiverType.storeType)
            Type.Reference(newStoreType, receiverType.addressSpace, receiverType.accessMode)
        }

        else -> resolveDirectMemberLookupType(receiverType)
    }
}

fun LhsExpression.toType(
    scope: Scope,
    resolvedEnvironment: ResolvedEnvironment,
): Type =
    when (this) {
        is LhsExpression.AddressOf -> {
            val referenceType = resolvedEnvironment.typeOf(this.target)
            if (referenceType !is Type.Reference) {
                throw RuntimeException(
                    "Address-of in LHS expression applied to expression ${this.target} with non-reference " +
                        "type",
                )
            }
            // The AddressOf expression converts a pointer to a reference.
            // https://www.w3.org/TR/WGSL/#address-of-expr
            Type.Pointer(referenceType.storeType, referenceType.addressSpace, referenceType.accessMode)
        }
        is LhsExpression.Dereference -> {
            val pointerType = resolvedEnvironment.typeOf(this.target)
            if (pointerType !is Type.Pointer) {
                throw RuntimeException("Dereference in LHS expression applied to expression ${this.target} with non-pointer type")
            }
            // The Indirection expression converts a reference to a pointer.
            // https://www.w3.org/TR/WGSL/#indirection-expr
            Type.Reference(pointerType.pointeeType, pointerType.addressSpace, pointerType.accessMode)
        }
        is LhsExpression.Identifier -> {
            when (val scopeEntry = scope.getEntry(this.name)) {
                is ScopeEntry.LocalValue -> {
                    assert(scopeEntry.type is Type.Pointer)
                    scopeEntry.type
                }
                is ScopeEntry.Parameter -> {
                    assert(scopeEntry.type is Type.Pointer)
                    scopeEntry.type
                }
                is ScopeEntry.LocalVariable -> {
                    assert(scopeEntry.type !is Type.Pointer)
                    scopeEntry.type
                }
                is ScopeEntry.GlobalVariable -> {
                    assert(scopeEntry.type !is Type.Pointer)
                    scopeEntry.type
                }
                else -> throw RuntimeException("Unsuitable scope entry for identifier occurring in LHS expression")
            }
        }
        is LhsExpression.IndexLookup -> {
            val targetType = resolvedEnvironment.typeOf(this.target)
            val addressSpace: AddressSpace?
            val accessMode: AccessMode?
            val storeType: Type?
            when (targetType) {
                is Type.Reference -> {
                    storeType = targetType.storeType
                    addressSpace = targetType.addressSpace
                    accessMode = targetType.accessMode
                }
                is Type.Pointer -> {
                    storeType = targetType.pointeeType
                    addressSpace = targetType.addressSpace
                    accessMode = targetType.accessMode
                }
                else -> throw RuntimeException(
                    "Index lookup in LHS expression applied to expression ${this.target} with non-reference / pointer type",
                )
            }

            when (storeType) {
                is Type.Vector -> Type.Reference(storeType.elementType, addressSpace, accessMode)
                is Type.Array -> Type.Reference(storeType.elementType, addressSpace, accessMode)
                is Type.Matrix ->
                    Type.Reference(
                        Type.Vector(storeType.numRows, storeType.elementType),
                        addressSpace,
                        accessMode,
                    )
                else -> throw RuntimeException("Index lookup in LHS expression applied to non-indexable reference")
            }
        }
        is LhsExpression.MemberLookup -> {
            val receiverType = resolvedEnvironment.typeOf(this.receiver)
            val addressSpace: AddressSpace?
            val accessMode: AccessMode?
            val storeType: Type?
            when (receiverType) {
                is Type.Reference -> {
                    storeType = receiverType.storeType
                    addressSpace = receiverType.addressSpace
                    accessMode = receiverType.accessMode
                }
                is Type.Pointer -> {
                    storeType = receiverType.pointeeType
                    addressSpace = receiverType.addressSpace
                    accessMode = receiverType.accessMode
                }
                else -> throw RuntimeException(
                    "Member lookup in LHS expression applied to expression ${this.receiver} with non-reference / pointer type",
                )
            }

            when (storeType) {
                is Type.Struct ->
                    Type.Reference(
                        storeType.members
                            .first {
                                it.first == this.memberName
                            }.second,
                        addressSpace,
                        accessMode,
                    )
                is Type.Vector -> Type.Reference(storeType.elementType, addressSpace, accessMode)
                else -> throw RuntimeException("Member lookup in LHS expression applied to non-indexable reference")
            }
        }
        is LhsExpression.Paren -> resolvedEnvironment.typeOf(this.target)
    }

fun Expression.Unary.toType(resolvedEnvironment: ResolvedEnvironment): Type =
    when (operator) {
        UnaryOperator.DEREFERENCE -> {
            val pointerType = resolvedEnvironment.typeOf(target)
            if (pointerType !is Type.Pointer) {
                throw RuntimeException("Dereference applied to expression $this with non-pointer type")
            }
            // The Indirection expression converts a reference to a pointer.
            // https://www.w3.org/TR/WGSL/#indirection-expr
            Type.Reference(pointerType.pointeeType, pointerType.addressSpace, pointerType.accessMode)
        }

        UnaryOperator.ADDRESS_OF -> {
            val referenceType = resolvedEnvironment.typeOf(target)
            if (referenceType !is Type.Reference) {
                throw RuntimeException("Address-of applied to expression $this with non-reference type")
            }
            // The AddressOf expression converts a pointer to a reference.
            // https://www.w3.org/TR/WGSL/#address-of-expr
            Type.Pointer(referenceType.storeType, referenceType.addressSpace, referenceType.accessMode)
        }

        UnaryOperator.LOGICAL_NOT -> {
            val targetType = resolvedEnvironment.typeOf(target)
            if (targetType.asStoreTypeIfReference() != Type.Bool) {
                throw IllegalArgumentException("Logical not applied to expression $this with non-bool type")
            }
            Type.Bool
        }

        UnaryOperator.MINUS, UnaryOperator.BINARY_NOT -> resolvedEnvironment.typeOf(target)
    }

fun Expression.Binary.toType(resolvedEnvironment: ResolvedEnvironment): Type {
    val lhsType = resolvedEnvironment.typeOf(lhs).asStoreTypeIfReference()
    val rhsType = resolvedEnvironment.typeOf(rhs).asStoreTypeIfReference()
    return when (val operator = operator) {
        BinaryOperator.LESS_THAN,
        BinaryOperator.LESS_THAN_EQUAL,
        BinaryOperator.GREATER_THAN,
        BinaryOperator.GREATER_THAN_EQUAL,
        -> {
            if (!rhsType.isAbstractionOf(lhsType) &&
                !lhsType.isAbstractionOf(rhsType)
            ) {
                TODO("$operator not supported for $lhsType and $rhsType")
            }
            if (lhsType is Type.Scalar || (lhsType is Type.Reference && lhsType.storeType is Type.Scalar)) {
                return Type.Bool
            } else if (lhsType is Type.Vector) {
                return Type.Vector(lhsType.width, Type.Bool)
            }
            TODO("$lhsType")
        }

        BinaryOperator.PLUS, BinaryOperator.MINUS, BinaryOperator.DIVIDE, BinaryOperator.MODULO ->
            if (rhsType.isAbstractionOf(lhsType)) {
                lhsType
            } else if (lhsType.isAbstractionOf(rhsType)) {
                rhsType
            } else if (lhsType is Type.Vector && rhsType is Type.Scalar) {
                val lhsElementType = lhsType.elementType
                if (rhsType.isAbstractionOf(lhsElementType)) {
                    lhsType
                } else if (lhsElementType.isAbstractionOf(rhsType)) {
                    Type.Vector(lhsType.width, rhsType)
                } else {
                    TODO("$operator not supported for $lhsType and $rhsType")
                }
            } else if (lhsType is Type.Scalar && rhsType is Type.Vector) {
                val rhsElementType = rhsType.elementType
                if (rhsElementType.isAbstractionOf(lhsType)) {
                    Type.Vector(rhsType.width, lhsType)
                } else if (lhsType.isAbstractionOf(rhsElementType)) {
                    rhsType
                } else {
                    TODO("$operator not supported for $lhsType and $rhsType")
                }
            } else {
                TODO("$operator not supported for $lhsType and $rhsType")
            }

        BinaryOperator.TIMES ->
            if (rhsType.isAbstractionOf(lhsType)) {
                lhsType
            } else if (lhsType.isAbstractionOf(rhsType)) {
                rhsType
            } else if (lhsType is Type.Vector && rhsType is Type.Scalar) {
                val lhsElementType = lhsType.elementType
                if (rhsType.isAbstractionOf(lhsElementType)) {
                    lhsType
                } else if (lhsElementType.isAbstractionOf(rhsType)) {
                    Type.Vector(lhsType.width, rhsType)
                } else {
                    TODO("$operator not supported for $lhsType and $rhsType")
                }
            } else if (lhsType is Type.Scalar && rhsType is Type.Vector) {
                val rhsElementType = rhsType.elementType
                if (rhsElementType.isAbstractionOf(lhsType)) {
                    Type.Vector(rhsType.width, lhsType)
                } else if (lhsType.isAbstractionOf(rhsElementType)) {
                    rhsType
                } else {
                    TODO("$operator not supported for $lhsType and $rhsType")
                }
            } else if (lhsType is Type.Matrix && rhsType is Type.Vector) {
                Type.Vector(
                    lhsType.numRows,
                    findCommonType(listOf(lhsType.elementType, rhsType.elementType)) as Type.Float,
                )
            } else if (lhsType is Type.Vector && rhsType is Type.Matrix) {
                Type.Vector(
                    rhsType.numCols,
                    findCommonType(listOf(lhsType.elementType, rhsType.elementType)) as Type.Float,
                )
            } else if (lhsType is Type.Matrix && rhsType is Type.Matrix) {
                Type.Matrix(
                    rhsType.numCols,
                    lhsType.numRows,
                    findCommonType(listOf(lhsType.elementType, rhsType.elementType)) as Type.Float,
                )
            } else if (lhsType is Type.Scalar && rhsType is Type.Matrix) {
                Type.Matrix(
                    rhsType.numCols,
                    rhsType.numRows,
                    findCommonType(listOf(lhsType, rhsType.elementType)) as Type.Float,
                )
            } else if (lhsType is Type.Matrix && rhsType is Type.Scalar) {
                Type.Matrix(
                    lhsType.numCols,
                    lhsType.numRows,
                    findCommonType(listOf(lhsType.elementType, rhsType)) as Type.Float,
                )
            } else {
                TODO("$operator not supported for $lhsType and $rhsType")
            }

        BinaryOperator.EQUAL_EQUAL, BinaryOperator.NOT_EQUAL ->
            when (lhsType) {
                is Type.Scalar -> Type.Bool
                is Type.Vector -> Type.Vector(width = lhsType.width, elementType = Type.Bool)
                else -> throw RuntimeException("== operator is only supported for scalar and vector types")
            }

        BinaryOperator.SHORT_CIRCUIT_AND, BinaryOperator.SHORT_CIRCUIT_OR ->
            if (lhsType != Type.Bool || rhsType != Type.Bool) {
                throw RuntimeException("Short circuit && and || operators require bool arguments")
            } else {
                Type.Bool
            }

        BinaryOperator.BINARY_AND, BinaryOperator.BINARY_OR, BinaryOperator.BINARY_XOR ->
            if (rhsType.isAbstractionOf(lhsType)) {
                lhsType
            } else if (lhsType.isAbstractionOf(rhsType)) {
                rhsType
            } else {
                throw RuntimeException("Unsupported types for bitwise operation")
            }

        BinaryOperator.SHIFT_LEFT, BinaryOperator.SHIFT_RIGHT -> lhsType
    }
}

fun Expression.MatrixValueConstructor.toType(
    scope: Scope,
    resolvedEnvironment: ResolvedEnvironment,
): Type.Matrix {
    val elementType: Type.Float =
        elementType?.let {
            it.toType(scope, resolvedEnvironment) as Type.Float
        } ?: run {
            var candidateElementType: Type.Scalar? = null
            for (arg in this@toType.args) {
                var elementTypeForArg = resolvedEnvironment.typeOf(arg).asStoreTypeIfReference()
                when (elementTypeForArg) {
                    is Type.Float -> {
                        // Nothing to do
                    }
                    is Type.AbstractInteger -> elementTypeForArg = Type.AbstractFloat
                    is Type.Vector -> {
                        elementTypeForArg = elementTypeForArg.elementType
                    }
                    is Type.Matrix -> {
                        elementTypeForArg = elementTypeForArg.elementType
                    }
                    else -> {
                        throw RuntimeException("A matrix may only be constructed from matrices, vectors and scalars")
                    }
                }
                if (candidateElementType == null || candidateElementType.isAbstractionOf(elementTypeForArg)) {
                    candidateElementType = elementTypeForArg
                } else if (!elementTypeForArg.isAbstractionOf(candidateElementType)) {
                    throw RuntimeException("Matrix constructed from incompatible mix of element types")
                }
            }
            when (candidateElementType) {
                is Type.Float -> candidateElementType
                is Type.AbstractInteger -> Type.AbstractFloat
                else -> throw RuntimeException("Invalid types provided to matrix constructor")
            }
        }
    return when (this) {
        is Expression.Mat2x2ValueConstructor -> Type.Matrix(2, 2, elementType)
        is Expression.Mat2x3ValueConstructor -> Type.Matrix(2, 3, elementType)
        is Expression.Mat2x4ValueConstructor -> Type.Matrix(2, 4, elementType)
        is Expression.Mat3x2ValueConstructor -> Type.Matrix(3, 2, elementType)
        is Expression.Mat3x3ValueConstructor -> Type.Matrix(3, 3, elementType)
        is Expression.Mat3x4ValueConstructor -> Type.Matrix(3, 4, elementType)
        is Expression.Mat4x2ValueConstructor -> Type.Matrix(4, 2, elementType)
        is Expression.Mat4x3ValueConstructor -> Type.Matrix(4, 3, elementType)
        is Expression.Mat4x4ValueConstructor -> Type.Matrix(4, 4, elementType)
    }
}

fun Expression.VectorValueConstructor.toType(
    scope: Scope,
    resolvedEnvironment: ResolvedEnvironment,
): Type.Vector {
    val elementType: Type.Scalar =
        elementType?.let {
            it.toType(scope, resolvedEnvironment) as Type.Scalar
        } ?: if (args.isEmpty()) {
            Type.AbstractInteger
        } else {
            var candidateElementType: Type.Scalar? = null
            for (arg in args) {
                var elementTypeForArg = resolvedEnvironment.typeOf(arg).asStoreTypeIfReference()
                when (elementTypeForArg) {
                    is Type.Scalar -> {
                        // Nothing to do
                    }
                    is Type.Vector -> {
                        elementTypeForArg = elementTypeForArg.elementType
                    }
                    else -> {
                        throw RuntimeException("A vector may only be constructed from vectors and scalars")
                    }
                }
                if (candidateElementType == null || candidateElementType.isAbstractionOf(elementTypeForArg)) {
                    candidateElementType = elementTypeForArg
                } else if (!elementTypeForArg.isAbstractionOf(candidateElementType)) {
                    throw RuntimeException("Vector constructed from incompatible mix of element types")
                }
            }
            candidateElementType!!
        }
    return when (this) {
        is Expression.Vec2ValueConstructor -> Type.Vector(2, elementType)
        is Expression.Vec3ValueConstructor -> Type.Vector(3, elementType)
        is Expression.Vec4ValueConstructor -> Type.Vector(4, elementType)
    }
}

fun TexelFormat.toVectorElementType(): Type.Scalar =
    when (this) {
        TexelFormat.RGBA8UNORM -> Type.F32
        TexelFormat.RGBA8SNORM -> Type.F32
        TexelFormat.RGBA8UINT -> Type.U32
        TexelFormat.RGBA8SINT -> Type.I32
        TexelFormat.RGBA16UNORM -> Type.F32
        TexelFormat.RGBA16SNORM -> Type.F32
        TexelFormat.RGBA16UINT -> Type.U32
        TexelFormat.RGBA16SINT -> Type.I32
        TexelFormat.RGBA16FLOAT -> Type.F32
        TexelFormat.RG8UNORM -> Type.F32
        TexelFormat.RG8SNORM -> Type.F32
        TexelFormat.RG8UINT -> Type.U32
        TexelFormat.RG8SINT -> Type.I32
        TexelFormat.RG16UNORM -> Type.F32
        TexelFormat.RG16SNORM -> Type.F32
        TexelFormat.RG16UINT -> Type.U32
        TexelFormat.RG16SINT -> Type.I32
        TexelFormat.RG16FLOAT -> Type.F32
        TexelFormat.R32UINT -> Type.U32
        TexelFormat.R32SINT -> Type.I32
        TexelFormat.R32FLOAT -> Type.F32
        TexelFormat.RG32UINT -> Type.U32
        TexelFormat.RG32SINT -> Type.I32
        TexelFormat.RG32FLOAT -> Type.F32
        TexelFormat.RGBA32UINT -> Type.U32
        TexelFormat.RGBA32SINT -> Type.I32
        TexelFormat.RGBA32FLOAT -> Type.F32
        TexelFormat.BGRA8UNORM -> Type.F32
        TexelFormat.R8UNORM -> Type.F32
        TexelFormat.R8SNORM -> Type.F32
        TexelFormat.R8UINT -> Type.U32
        TexelFormat.R8SINT -> Type.I32
        TexelFormat.R16UNORM -> Type.F32
        TexelFormat.R16SNORM -> Type.F32
        TexelFormat.R16UINT -> Type.U32
        TexelFormat.R16SINT -> Type.I32
        TexelFormat.R16FLOAT -> Type.F32
        TexelFormat.RGB10A2UNORM -> Type.F32
        TexelFormat.RGB10A2UINT -> Type.U32
        TexelFormat.RG11B10UFLOAT -> Type.F32
    }

fun Type.asStoreTypeIfReference(): Type =
    when (this) {
        is Type.Reference -> this.storeType
        else -> this
    }

fun isSwizzle(memberName: String): Boolean =
    memberName.length in (2..4) &&
        (memberName.all { it in setOf('x', 'y', 'z', 'w') } || memberName.all { it in setOf('r', 'g', 'b', 'a') })
