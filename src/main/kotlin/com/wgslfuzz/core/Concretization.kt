package com.wgslfuzz.core

fun findCommonType(
    expressions: List<Expression>,
    resolvedEnvironment: ResolvedEnvironment,
): Type =
    findCommonType(
        expressions.map {
            resolvedEnvironment.typeOf(it)
        },
    )

// Throws an exception if the types in the given list do not share a common concretization.
// Otherwise, returns the common concretization, unless all the types are the same abstract type in which case that type
// is returned.
fun findCommonType(types: List<Type>): Type {
    var result = types[0]
    for (type in types.drop(1)) {
        if (result != type) {
            if (result.isAbstractionOf(type)) {
                result = type
            } else if (!type.isAbstractionOf(result)) {
                throw RuntimeException("No common type found")
            }
        }
    }
    return result
}

fun Type.isAbstractionOf(maybeConcretizedVersion: Type): Boolean =
    if (this == maybeConcretizedVersion || (this is Type.Reference && maybeConcretizedVersion.isAbstractionOf(this.storeType))) {
        true
    } else {
        when (this) {
            Type.AbstractFloat -> maybeConcretizedVersion in setOf(Type.F16, Type.F32)
            Type.AbstractInteger ->
                maybeConcretizedVersion in
                    setOf(
                        Type.I32,
                        Type.U32,
                        Type.AbstractFloat,
                        Type.F32,
                        Type.F16,
                    )

            is Type.Vector ->
                maybeConcretizedVersion is Type.Vector &&
                    width == maybeConcretizedVersion.width &&
                    elementType.isAbstractionOf(maybeConcretizedVersion.elementType)

            is Type.Matrix ->
                maybeConcretizedVersion is Type.Matrix &&
                    numRows == maybeConcretizedVersion.numRows &&
                    numCols == maybeConcretizedVersion.numCols &&
                    elementType.isAbstractionOf(maybeConcretizedVersion.elementType)

            is Type.Array ->
                maybeConcretizedVersion is Type.Array &&
                    elementCount == maybeConcretizedVersion.elementCount &&
                    elementType.isAbstractionOf(maybeConcretizedVersion.elementType)

            else -> false
        }
    }

fun defaultConcretizationOf(type: Type): Type =
    when (type) {
        is Type.AbstractInteger -> Type.I32
        is Type.AbstractFloat -> Type.F32
        is Type.Vector -> Type.Vector(type.width, defaultConcretizationOf(type.elementType) as Type.Scalar)
        is Type.Matrix ->
            Type.Matrix(
                type.numCols,
                type.numRows,
                defaultConcretizationOf(type.elementType) as Type.Float,
            )
        is Type.Array ->
            Type.Array(
                elementType = defaultConcretizationOf(type.elementType),
                elementCount = type.elementCount,
            )
        else -> type
    }
