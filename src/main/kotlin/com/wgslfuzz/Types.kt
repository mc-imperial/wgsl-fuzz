package com.wgslfuzz

sealed interface Type {
    sealed interface ScalarType : Type

    data object AbstractInt : ScalarType

    data object Bool : ScalarType

    data object I32 : ScalarType

    data object U32 : ScalarType

    sealed interface FloatType : ScalarType

    data object AbstractFloat : FloatType

    data object F16 : FloatType

    data object F32 : FloatType

    sealed class VectorType(
        val elementType: ScalarType,
        val width: Int,
    ) : Type

    class Vec2(
        elementType: ScalarType,
    ) : VectorType(elementType, 2)

    class Vec3(
        elementType: ScalarType,
    ) : VectorType(elementType, 3)

    class Vec4(
        elementType: ScalarType,
    ) : VectorType(elementType, 4)

    sealed class MatrixType(
        val elementType: FloatType,
        val numCols: Int,
        val numRows: Int,
    ) : Type

    class Mat2x2(
        elementType: FloatType,
    ) : MatrixType(elementType, 2, 2)

    class Mat2x3(
        elementType: FloatType,
    ) : MatrixType(elementType, 2, 3)

    class Mat2x4(
        elementType: FloatType,
    ) : MatrixType(elementType, 2, 4)

    class Mat3x2(
        elementType: FloatType,
    ) : MatrixType(elementType, 3, 2)

    class Mat3x3(
        elementType: FloatType,
    ) : MatrixType(elementType, 3, 3)

    class Mat3x4(
        elementType: FloatType,
    ) : MatrixType(elementType, 3, 4)

    class Mat4x2(
        elementType: FloatType,
    ) : MatrixType(elementType, 4, 2)

    class Mat4x3(
        elementType: FloatType,
    ) : MatrixType(elementType, 4, 3)

    class Mat4x4(
        elementType: FloatType,
    ) : MatrixType(elementType, 4, 4)

    class Array(
        val elementType: Type,
        val elementCount: Int?,
    ) : Type
}

class FunctionType(
    val argTypes: List<Type>,
    val returnType: Type?,
)
