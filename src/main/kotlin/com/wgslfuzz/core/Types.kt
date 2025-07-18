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

/**
 * Interfaces and classes representing the types associated with AST nodes after resolving has taken place.
 * This hierarchy of interfaces and classes is deliberately entirely separate from those that represent type declarations
 * in an AST.
 *
 * By design, all concrete classes representing types are data classes (or data objects).
 */

sealed interface Type {
    /**
     * Numeric types, and vectors and matrices of numeric elements, can be abstract. This method provides a convenient
     * way to check whether a type is abstract; by default types are not.
     */
    fun isAbstract(): Boolean = false

    fun alignOf(): Int

    data class Reference(
        val storeType: Type,
        val addressSpace: AddressSpace,
        val accessMode: AccessMode,
    ) : Type {
        override fun alignOf(): Int {
            TODO("Not yet implemented")
        }
    }

    sealed interface Scalar : Type

    data object Bool : Scalar {
        override fun alignOf(): Int = 4
    }

    sealed interface Integer : Scalar

    data object AbstractInteger : Integer {
        override fun isAbstract(): Boolean = true

        override fun alignOf(): Int =
            throw UnsupportedOperationException(
                "AbstractInteger does not have a meaningful alignment",
            )
    }

    data object I32 : Integer {
        override fun alignOf(): Int = 4
    }

    data object U32 : Integer {
        override fun alignOf(): Int = 4
    }

    sealed interface Float : Scalar

    data object AbstractFloat : Float {
        override fun isAbstract(): Boolean = true

        override fun alignOf(): Int =
            throw UnsupportedOperationException(
                "AbstractFloat does not have a meaningful alignment",
            )
    }

    data object F16 : Float {
        override fun alignOf(): Int = 2
    }

    data object F32 : Float {
        override fun alignOf(): Int = 4
    }

    data class Vector(
        val width: Int,
        val elementType: Scalar,
    ) : Type {
        override fun isAbstract(): Boolean = elementType.isAbstract()

        override fun alignOf(): Int =
            when (this.width) {
                2 ->
                    when (this.elementType) {
                        F16 -> 4
                        Bool, I32, U32, F32 -> 8
                        AbstractFloat -> throw UnsupportedOperationException(
                            "AbstractFloat does not have a meaningful alignment",
                        )

                        AbstractInteger -> throw UnsupportedOperationException(
                            "AbstractInteger does not have a meaningful alignment",
                        )
                    }

                3 ->
                    when (this.elementType) {
                        F16 -> 8
                        Bool, I32, U32, F32 -> 16
                        AbstractFloat -> throw UnsupportedOperationException(
                            "AbstractFloat does not have a meaningful alignment",
                        )

                        AbstractInteger -> throw UnsupportedOperationException(
                            "AbstractInteger does not have a meaningful alignment",
                        )
                    }

                4 ->
                    when (this.elementType) {
                        F16 -> 8
                        Bool, I32, U32, F32 -> 16
                        AbstractFloat -> throw UnsupportedOperationException(
                            "AbstractFloat does not have a meaningful alignment",
                        )

                        AbstractInteger -> throw UnsupportedOperationException(
                            "AbstractInteger does not have a meaningful alignment",
                        )
                    }

                else -> throw IllegalArgumentException("Bad vector size.")
            }
    }

    data class Matrix(
        val numCols: Int,
        val numRows: Int,
        val elementType: Float,
    ) : Type {
        override fun isAbstract(): Boolean = elementType.isAbstract()

        override fun alignOf(): Int = Vector(numRows, elementType).alignOf()
    }

    /**
     * Array types. An array has an element type and an element count. The latter can be emitted in certain scenarios.
     * Two array type should certainly be considered equal if they match on element type, and both have a matching
     * element count. If they do *not* have an element count then it not entirely clear that they should be considered
     * equal. This is something that might need to be revisited.
     */
    data class Array(
        val elementType: Type,
        val elementCount: Int?,
    ) : Type {
        override fun isAbstract(): Boolean = elementType.isAbstract()

        override fun alignOf(): Int = elementType.alignOf()
    }

    data class Pointer(
        val pointeeType: Type,
        val addressSpace: AddressSpace,
        val accessMode: AccessMode,
    ) : Type {
        override fun alignOf(): Int {
            TODO("Not yet implemented")
        }
    }

    data class Struct(
        val name: String,
        val members: List<Pair<String, Type>>,
    ) : Type {
        override fun alignOf(): Int = members.maxOf { it.second.alignOf() }
    }

    sealed interface Atomic : Type {
        val targetType: Integer
    }

    data object AtomicI32 : Atomic {
        override val targetType: I32 = I32

        override fun alignOf(): Int = 4
    }

    data object AtomicU32 : Atomic {
        override val targetType: U32 = U32

        override fun alignOf(): Int = 4
    }

    sealed interface Texture : Type {
        /**
         * Sampled Texture Types
         */
        sealed interface Sampled : Texture {
            val sampledType: Scalar

            override fun alignOf(): Int {
                TODO("Not yet implemented")
            }
        }

        data class Sampled1D(
            override val sampledType: Scalar,
        ) : Sampled

        data class Sampled2D(
            override val sampledType: Scalar,
        ) : Sampled

        data class Sampled2DArray(
            override val sampledType: Scalar,
        ) : Sampled

        data class Sampled3D(
            override val sampledType: Scalar,
        ) : Sampled

        data class SampledCube(
            override val sampledType: Scalar,
        ) : Sampled

        data class SampledCubeArray(
            override val sampledType: Scalar,
        ) : Sampled

        /**
         * Multisampled Texture Types
         */
        data class Multisampled2d(
            val sampledType: Scalar,
        ) : Texture {
            override fun alignOf(): Int {
                TODO("Not yet implemented")
            }
        }

        data object DepthMultisampled2D : Texture {
            override fun alignOf(): Int {
                TODO("Not yet implemented")
            }
        }

        /**
         * External Sampled Texture Types
         */
        data object External : Texture {
            override fun alignOf(): Int {
                TODO("Not yet implemented")
            }
        }

        /**
         * Storage Texture Types
         */
        sealed interface Storage : Texture {
            val format: TexelFormat
            val accessMode: AccessMode
        }

        data class Storage1D(
            override val format: TexelFormat,
            override val accessMode: AccessMode,
        ) : Storage {
            override fun alignOf(): Int {
                TODO("Not yet implemented")
            }
        }

        data class Storage2D(
            override val format: TexelFormat,
            override val accessMode: AccessMode,
        ) : Storage {
            override fun alignOf(): Int {
                TODO("Not yet implemented")
            }
        }

        data class Storage2DArray(
            override val format: TexelFormat,
            override val accessMode: AccessMode,
        ) : Storage {
            override fun alignOf(): Int {
                TODO("Not yet implemented")
            }
        }

        data class Storage3D(
            override val format: TexelFormat,
            override val accessMode: AccessMode,
        ) : Storage {
            override fun alignOf(): Int {
                TODO("Not yet implemented")
            }
        }

        /**
         * Depth Texture Types
         */
        sealed interface Depth : Texture

        data object Depth2D : Depth {
            override fun alignOf(): Int {
                TODO("Not yet implemented")
            }
        }

        data object Depth2DArray : Depth {
            override fun alignOf(): Int {
                TODO("Not yet implemented")
            }
        }

        data object DepthCube : Depth {
            override fun alignOf(): Int {
                TODO("Not yet implemented")
            }
        }

        data object DepthCubeArray : Depth {
            override fun alignOf(): Int {
                TODO("Not yet implemented")
            }
        }
    }

    sealed interface Sampler : Type

    data object SamplerRegular : Sampler {
        override fun alignOf(): Int {
            TODO("Not yet implemented")
        }
    }

    data object SamplerComparison : Sampler {
        override fun alignOf(): Int {
            TODO("Not yet implemented")
        }
    }
}

/**
 * An expression in WGSL cannot have [FunctionType] as its type. For this reason, [FunctionType] is deliberately not
 * part of the [Type] hierarchy.
 */
data class FunctionType(
    val argTypes: List<Type>,
    val returnType: Type?,
)

// The following are builtin structures described in the WGSL specification, representing the result types of various
// builtin functions.

val FrexpResultF16 =
    Type.Struct(
        name = "__frexp_result_f16",
        members = listOf("fract" to Type.F16, "exp" to Type.I32),
    )

val FrexpResultVec2F16 =
    Type.Struct(
        name = "__frexp_result_vec2_f16",
        members = listOf("fract" to Type.Vector(2, Type.F16), "exp" to Type.Vector(2, Type.I32)),
    )

val FrexpResultVec3F16 =
    Type.Struct(
        name = "__frexp_result_vec3_f16",
        members = listOf("fract" to Type.Vector(3, Type.F16), "exp" to Type.Vector(3, Type.I32)),
    )

val FrexpResultVec4F16 =
    Type.Struct(
        name = "__frexp_result_vec4_f16",
        members = listOf("fract" to Type.Vector(4, Type.F16), "exp" to Type.Vector(4, Type.I32)),
    )

val FrexpResultF32 =
    Type.Struct(
        name = "__frexp_result_f32",
        members = listOf("fract" to Type.F32, "exp" to Type.I32),
    )

val FrexpResultVec2F32 =
    Type.Struct(
        name = "__frexp_result_vec2_f32",
        members = listOf("fract" to Type.Vector(2, Type.F32), "exp" to Type.Vector(2, Type.I32)),
    )

val FrexpResultVec3F32 =
    Type.Struct(
        name = "__frexp_result_vec3_f32",
        members = listOf("fract" to Type.Vector(3, Type.F32), "exp" to Type.Vector(3, Type.I32)),
    )

val FrexpResultVec4F32 =
    Type.Struct(
        name = "__frexp_result_vec4_f32",
        members = listOf("fract" to Type.Vector(4, Type.F32), "exp" to Type.Vector(4, Type.I32)),
    )

val FrexpResultAbstract =
    Type.Struct(
        name = "__frexp_result_abstract",
        members = listOf("fract" to Type.AbstractFloat, "exp" to Type.AbstractInteger),
    )

val FrexpResultVec2Abstract =
    Type.Struct(
        name = "__frexp_result_vec2_abstract",
        members = listOf("fract" to Type.Vector(2, Type.AbstractFloat), "exp" to Type.Vector(2, Type.AbstractInteger)),
    )

val FrexpResultVec3Abstract =
    Type.Struct(
        name = "__frexp_result_vec3_abstract",
        members = listOf("fract" to Type.Vector(3, Type.AbstractFloat), "exp" to Type.Vector(3, Type.AbstractInteger)),
    )

val FrexpResultVec4Abstract =
    Type.Struct(
        name = "__frexp_result_vec4_abstract",
        members = listOf("fract" to Type.Vector(4, Type.AbstractFloat), "exp" to Type.Vector(4, Type.AbstractInteger)),
    )

val ModfResultF16 =
    Type.Struct(
        name = "__modf_result_f16",
        members = listOf("fract" to Type.F16, "whole" to Type.F16),
    )

val ModfResultVec2F16 =
    Type.Struct(
        name = "__modf_result_vec2_f16",
        members = listOf("fract" to Type.Vector(2, Type.F16), "whole" to Type.Vector(2, Type.F16)),
    )

val ModfResultVec3F16 =
    Type.Struct(
        name = "__modf_result_vec3_f16",
        members = listOf("fract" to Type.Vector(3, Type.F16), "whole" to Type.Vector(3, Type.F16)),
    )

val ModfResultVec4F16 =
    Type.Struct(
        name = "__modf_result_vec4_f16",
        members = listOf("fract" to Type.Vector(4, Type.F16), "whole" to Type.Vector(4, Type.F16)),
    )

val ModfResultF32 =
    Type.Struct(
        name = "__modf_result_f32",
        members = listOf("fract" to Type.F32, "whole" to Type.F32),
    )

val ModfResultVec2F32 =
    Type.Struct(
        name = "__modf_result_vec2_f32",
        members = listOf("fract" to Type.Vector(2, Type.F32), "whole" to Type.Vector(2, Type.F32)),
    )

val ModfResultVec3F32 =
    Type.Struct(
        name = "__modf_result_vec3_f32",
        members = listOf("fract" to Type.Vector(3, Type.F32), "whole" to Type.Vector(3, Type.F32)),
    )

val ModfResultVec4F32 =
    Type.Struct(
        name = "__modf_result_vec4_f32",
        members = listOf("fract" to Type.Vector(4, Type.F32), "whole" to Type.Vector(4, Type.F32)),
    )

val ModfResultAbstract =
    Type.Struct(
        name = "__modf_result_abstract",
        members = listOf("fract" to Type.AbstractFloat, "whole" to Type.AbstractFloat),
    )

val ModfResultVec2Abstract =
    Type.Struct(
        name = "__modf_result_vec2_abstract",
        members = listOf("fract" to Type.Vector(2, Type.AbstractFloat), "whole" to Type.Vector(2, Type.AbstractFloat)),
    )

val ModfResultVec3Abstract =
    Type.Struct(
        name = "__modf_result_vec3_abstract",
        members = listOf("fract" to Type.Vector(3, Type.AbstractFloat), "whole" to Type.Vector(3, Type.AbstractFloat)),
    )

val ModfResultVec4Abstract =
    Type.Struct(
        name = "__modf_result_vec4_abstract",
        members = listOf("fract" to Type.Vector(4, Type.AbstractFloat), "whole" to Type.Vector(4, Type.AbstractFloat)),
    )

val AtomicCompareExchangeResultI32 =
    Type.Struct(
        name = "__atomic_compare_exchange_result_I32",
        members = listOf("old_value" to Type.I32, "exchanged" to Type.Bool),
    )

val AtomicCompareExchangeResultU32 =
    Type.Struct(
        name = "__atomic_compare_exchange_result_U32",
        members = listOf("old_value" to Type.U32, "exchanged" to Type.Bool),
    )
