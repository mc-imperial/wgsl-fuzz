package com.wgslfuzz

sealed interface Type {
    fun isAbstract(): Boolean

    class Reference(
        val storeType: Type,
        val addressSpace: AddressSpace,
        val accessMode: AccessMode,
    ) : Type {
        override fun isAbstract(): Nothing = throw RuntimeException("It does not make sense to ask whether a reference type is abstract.")
    }

    // Scalar types. These are all data objects - i.e., there is only one instance of each of the specific scalar types
    // (Bool, I32, U32, F16, F32, AbstractInt, AbstractFloat). For this reason, equals and hashCode need not be
    // overridden for scalar types.

    sealed interface Scalar : Type

    data object Bool : Scalar {
        override fun isAbstract(): Boolean = false
    }

    sealed interface Integer : Scalar

    data object AbstractInteger : Integer {
        override fun isAbstract(): Boolean = true
    }

    data object I32 : Integer {
        override fun isAbstract(): Boolean = false
    }

    data object U32 : Integer {
        override fun isAbstract(): Boolean = false
    }

    sealed interface Float : Scalar

    data object AbstractFloat : Float {
        override fun isAbstract(): Boolean = true
    }

    data object F16 : Float {
        override fun isAbstract(): Boolean = false
    }

    data object F32 : Float {
        override fun isAbstract(): Boolean = false
    }

    // Vector types. A vector type has an element type and width, and two vector types that match on these are
    // considered equal.

    data class Vector(
        val width: Int,
        val elementType: Scalar,
    ) : Type {
        override fun isAbstract(): Boolean = elementType.isAbstract()
    }

    // Matrix types. A matrix type has an element type, a number of columns and a number of rows. Two matrix types that
    // match on these are considered equal.

    data class Matrix(
        val numCols: Int,
        val numRows: Int,
        val elementType: Float,
    ) : Type {
        override fun isAbstract(): Boolean = elementType.isAbstract()
    }

    // Array types. An array has an element type and an element count. The latter can be emitted in certain scenarios.
    // Two array type should certainly be considered equal if they match on element type, and both have a matching
    // element count. If they do *not* have an element count then it not entirely clear that they should be considered
    // equal. This is something that might need to be revisited.

    data class Array(
        val elementType: Type,
        val elementCount: Int?,
    ) : Type {
        override fun isAbstract(): Boolean = elementType.isAbstract()
    }

    // Pointer types. A pointer has a pointee type, address space and access mode. Two pointer types are equal if they
    // match on all these.
    data class Pointer(
        val pointeeType: Type,
        val addressSpace: AddressSpace,
        val accessMode: AccessMode,
    ) : Type {
        override fun isAbstract(): Boolean = false
    }

    // Struct types. A struct type has a name and a set of typed members. Two struct types are equal if they match on
    // both. Since WGSL does not allow multiple struct types with the same name, it would also be OK to define equality
    // simply based on type names. This could be considered if it proves inefficient to compare entire struct types.

    data class Struct(
        val name: String,
        val members: Map<String, Type>,
    ) : Type {
        override fun isAbstract(): Boolean = false
    }

    // Atomic types - there are two types, AtomicI32 and AtomicU32, both declared as data objects, therefore there is no
    // need to spell out equality on these types.

    sealed interface Atomic : Type {
        val targetType: Integer
    }

    data object AtomicI32 : Atomic {
        override val targetType: I32 = I32

        override fun isAbstract(): Boolean = false
    }

    data object AtomicU32 : Atomic {
        override val targetType: U32 = U32

        override fun isAbstract(): Boolean = false
    }

    sealed interface Texture : Type {
        override fun isAbstract(): Boolean = false

        // Sampled Texture Types
        sealed class Sampled(
            val sampledType: Scalar,
        ) : Texture

        class Sampled1D(
            sampledType: Scalar,
        ) : Sampled(sampledType) {
            override fun equals(other: Any?): Boolean = other is Sampled1D && sampledType == other.sampledType

            override fun hashCode(): Int = sampledType.hashCode()
        }

        class Sampled2D(
            sampledType: Scalar,
        ) : Sampled(sampledType) {
            override fun equals(other: Any?): Boolean = other is Sampled2D && sampledType == other.sampledType

            override fun hashCode(): Int = sampledType.hashCode()
        }

        class Sampled2DArray(
            sampledType: Scalar,
        ) : Sampled(sampledType) {
            override fun equals(other: Any?): Boolean = other is Sampled2DArray && sampledType == other.sampledType

            override fun hashCode(): Int = sampledType.hashCode()
        }

        class Sampled3D(
            sampledType: Scalar,
        ) : Sampled(sampledType) {
            override fun equals(other: Any?): Boolean = other is Sampled3D && sampledType == other.sampledType

            override fun hashCode(): Int = sampledType.hashCode()
        }

        class SampledCube(
            sampledType: Scalar,
        ) : Sampled(sampledType) {
            override fun equals(other: Any?): Boolean = other is SampledCube && sampledType == other.sampledType

            override fun hashCode(): Int = sampledType.hashCode()
        }

        class SampledCubeArray(
            sampledType: Scalar,
        ) : Sampled(sampledType) {
            override fun equals(other: Any?): Boolean = other is SampledCubeArray && sampledType == other.sampledType

            override fun hashCode(): Int = sampledType.hashCode()
        }

        // Multisampled Texture Types
        data class Multisampled2d(
            val sampledType: Scalar,
        ) : Texture

        data object DepthMultisampled2D : Texture

        // External Sampled Texture Types
        data object External : Texture

        // Storage Texture Types
        data class Storage1D(
            val format: TexelFormat,
            val accessMode: AccessMode,
        ) : Texture

        data class Storage2D(
            val format: TexelFormat,
            val accessMode: AccessMode,
        ) : Texture

        data class Storage2DArray(
            val format: TexelFormat,
            val accessMode: AccessMode,
        ) : Texture

        data class Storage3D(
            val format: TexelFormat,
            val accessMode: AccessMode,
        ) : Texture

        // Depth Texture Types
        data object Depth2D : Texture

        data object Depth2DArray : Texture

        data object DepthCube : Texture

        data object DepthCubeArray : Texture
    }

    sealed interface Sampler : Type {
        override fun isAbstract(): Boolean = false
    }

    data object SamplerRegular : Sampler

    data object SamplerComparison : Sampler
}

class FunctionType(
    val argTypes: List<Type>,
    val returnType: Type?,
)

val FrexpResultF16 =
    Type.Struct(
        name = "__frexp_result_f16",
        members = mapOf("fract" to Type.F16, "exp" to Type.I32),
    )

val FrexpResultVec2F16 =
    Type.Struct(
        name = "__frexp_result_vec2_f16",
        members = mapOf("fract" to Type.Vector(2, Type.F16), "exp" to Type.Vector(2, Type.I32)),
    )

val FrexpResultVec3F16 =
    Type.Struct(
        name = "__frexp_result_vec3_f16",
        members = mapOf("fract" to Type.Vector(3, Type.F16), "exp" to Type.Vector(3, Type.I32)),
    )

val FrexpResultVec4F16 =
    Type.Struct(
        name = "__frexp_result_vec4_f16",
        members = mapOf("fract" to Type.Vector(4, Type.F16), "exp" to Type.Vector(4, Type.I32)),
    )

val FrexpResultF32 =
    Type.Struct(
        name = "__frexp_result_f32",
        members = mapOf("fract" to Type.F32, "exp" to Type.I32),
    )

val FrexpResultVec2F32 =
    Type.Struct(
        name = "__frexp_result_vec2_f32",
        members = mapOf("fract" to Type.Vector(2, Type.F32), "exp" to Type.Vector(2, Type.I32)),
    )

val FrexpResultVec3F32 =
    Type.Struct(
        name = "__frexp_result_vec3_f32",
        members = mapOf("fract" to Type.Vector(3, Type.F32), "exp" to Type.Vector(3, Type.I32)),
    )

val FrexpResultVec4F32 =
    Type.Struct(
        name = "__frexp_result_vec4_f32",
        members = mapOf("fract" to Type.Vector(4, Type.F32), "exp" to Type.Vector(4, Type.I32)),
    )

val FrexpResultAbstract =
    Type.Struct(
        name = "__frexp_result_abstract",
        members = mapOf("fract" to Type.AbstractFloat, "exp" to Type.AbstractInteger),
    )

val FrexpResultVec2Abstract =
    Type.Struct(
        name = "__frexp_result_vec2_abstract",
        members = mapOf("fract" to Type.Vector(2, Type.AbstractFloat), "exp" to Type.Vector(2, Type.AbstractInteger)),
    )

val FrexpResultVec3Abstract =
    Type.Struct(
        name = "__frexp_result_vec3_abstract",
        members = mapOf("fract" to Type.Vector(3, Type.AbstractFloat), "exp" to Type.Vector(3, Type.AbstractInteger)),
    )

val FrexpResultVec4Abstract =
    Type.Struct(
        name = "__frexp_result_vec4_abstract",
        members = mapOf("fract" to Type.Vector(4, Type.AbstractFloat), "exp" to Type.Vector(4, Type.AbstractInteger)),
    )

val ModfResultF16 =
    Type.Struct(
        name = "__modf_result_f16",
        members = mapOf("fract" to Type.F16, "whole" to Type.F16),
    )

val ModfResultVec2F16 =
    Type.Struct(
        name = "__modf_result_vec2_f16",
        members = mapOf("fract" to Type.Vector(2, Type.F16), "whole" to Type.Vector(2, Type.F16)),
    )

val ModfResultVec3F16 =
    Type.Struct(
        name = "__modf_result_vec3_f16",
        members = mapOf("fract" to Type.Vector(3, Type.F16), "whole" to Type.Vector(3, Type.F16)),
    )

val ModfResultVec4F16 =
    Type.Struct(
        name = "__modf_result_vec4_f16",
        members = mapOf("fract" to Type.Vector(4, Type.F16), "whole" to Type.Vector(4, Type.F16)),
    )

val ModfResultF32 =
    Type.Struct(
        name = "__modf_result_f32",
        members = mapOf("fract" to Type.F32, "whole" to Type.F32),
    )

val ModfResultVec2F32 =
    Type.Struct(
        name = "__modf_result_vec2_f32",
        members = mapOf("fract" to Type.Vector(2, Type.F32), "whole" to Type.Vector(2, Type.F32)),
    )

val ModfResultVec3F32 =
    Type.Struct(
        name = "__modf_result_vec3_f32",
        members = mapOf("fract" to Type.Vector(3, Type.F32), "whole" to Type.Vector(3, Type.F32)),
    )

val ModfResultVec4F32 =
    Type.Struct(
        name = "__modf_result_vec4_f32",
        members = mapOf("fract" to Type.Vector(4, Type.F32), "whole" to Type.Vector(4, Type.F32)),
    )

val ModfResultAbstract =
    Type.Struct(
        name = "__modf_result_abstract",
        members = mapOf("fract" to Type.AbstractFloat, "whole" to Type.AbstractFloat),
    )

val ModfResultVec2Abstract =
    Type.Struct(
        name = "__modf_result_vec2_abstract",
        members = mapOf("fract" to Type.Vector(2, Type.AbstractFloat), "whole" to Type.Vector(2, Type.AbstractFloat)),
    )

val ModfResultVec3Abstract =
    Type.Struct(
        name = "__modf_result_vec3_abstract",
        members = mapOf("fract" to Type.Vector(3, Type.AbstractFloat), "whole" to Type.Vector(3, Type.AbstractFloat)),
    )

val ModfResultVec4Abstract =
    Type.Struct(
        name = "__modf_result_vec4_abstract",
        members = mapOf("fract" to Type.Vector(4, Type.AbstractFloat), "whole" to Type.Vector(4, Type.AbstractFloat)),
    )

val AtomicCompareExchangeResultI32 =
    Type.Struct(
        name = "__atomic_compare_exchange_result_I32",
        members = mapOf("old_value" to Type.I32, "exchanged" to Type.Bool),
    )

val AtomicCompareExchangeResultU32 =
    Type.Struct(
        name = "__atomic_compare_exchange_result_U32",
        members = mapOf("old_value" to Type.U32, "exchanged" to Type.Bool),
    )
