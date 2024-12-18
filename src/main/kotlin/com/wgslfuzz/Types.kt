package com.wgslfuzz

sealed interface Type {
    fun isAbstract(): Boolean

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

    sealed class Vector(
        val elementType: Scalar,
        val width: Int,
    ) : Type {
        override fun isAbstract(): Boolean = elementType.isAbstract()
    }

    class Vec2(
        elementType: Scalar,
    ) : Vector(elementType, 2)

    class Vec3(
        elementType: Scalar,
    ) : Vector(elementType, 3)

    class Vec4(
        elementType: Scalar,
    ) : Vector(elementType, 4)

    sealed class Matrix(
        val elementType: Float,
        val numCols: Int,
        val numRows: Int,
    ) : Type {
        override fun isAbstract(): Boolean = elementType.isAbstract()
    }

    class Mat2x2(
        elementType: Float,
    ) : Matrix(elementType, 2, 2)

    class Mat2x3(
        elementType: Float,
    ) : Matrix(elementType, 2, 3)

    class Mat2x4(
        elementType: Float,
    ) : Matrix(elementType, 2, 4)

    class Mat3x2(
        elementType: Float,
    ) : Matrix(elementType, 3, 2)

    class Mat3x3(
        elementType: Float,
    ) : Matrix(elementType, 3, 3)

    class Mat3x4(
        elementType: Float,
    ) : Matrix(elementType, 3, 4)

    class Mat4x2(
        elementType: Float,
    ) : Matrix(elementType, 4, 2)

    class Mat4x3(
        elementType: Float,
    ) : Matrix(elementType, 4, 3)

    class Mat4x4(
        elementType: Float,
    ) : Matrix(elementType, 4, 4)

    class Array(
        val elementType: Type,
        val elementCount: Int?,
    ) : Type {
        override fun isAbstract(): Boolean = elementType.isAbstract()
    }

    class Pointer(
        val pointeeType: Type,
        val addressSpace: AddressSpace,
        val accessMode: AccessMode,
    ) : Type {
        override fun isAbstract(): Boolean = false
    }

    class Struct(
        val name: String,
        val members: Map<String, Type>,
    ) : Type {
        override fun isAbstract(): Boolean = false
    }

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
}

class FunctionType(
    val argTypes: List<Type>,
    val returnType: Type?,
)
