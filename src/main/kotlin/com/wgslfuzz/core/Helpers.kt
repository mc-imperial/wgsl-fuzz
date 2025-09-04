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

// This file is a suitable place for helper methods that are generally useful but that do not have a more obvious home.

val builtinFunctionNames =
    setOf(
        "array",
        "bool",
        "f16",
        "f32",
        "i32",
        "mat2x2",
        "mat2x3",
        "mat2x4",
        "mat3x2",
        "mat3x3",
        "mat3x4",
        "mat4x2",
        "mat4x3",
        "mat4x4",
        "Structures",
        "u32",
        "vec2",
        "vec3",
        "vec4",
        "vec2f",
        "vec3f",
        "vec4f",
        "bitcast",
        "all",
        "any",
        "select",
        "arrayLength",
        "abs",
        "acos",
        "acosh",
        "asin",
        "asinh",
        "atan",
        "atanh",
        "atan2",
        "ceil",
        "clamp",
        "cos",
        "cosh",
        "countLeadingZeros",
        "countOneBits",
        "countTrailingZeros",
        "cross",
        "degrees",
        "determinant",
        "distance",
        "dot",
        "dot4U8Packed",
        "dot4I8Packed",
        "exp",
        "exp2",
        "extractBits",
        "faceForward",
        "firstLeadingBit",
        "firstTrailingBit",
        "floor",
        "fma",
        "fract",
        "frexp",
        "insertBits",
        "inverseSqrt",
        "ldexp",
        "length",
        "log",
        "log2",
        "max",
        "min",
        "mix",
        "modf",
        "normalize",
        "pow",
        "quantizeToF16",
        "radians",
        "reflect",
        "refract",
        "reverseBits",
        "round",
        "saturate",
        "sign",
        "sin",
        "sinh",
        "smoothstep",
        "sqrt",
        "step",
        "tan",
        "tanh",
        "transpose",
        "trunc",
        "dpdx",
        "dpdxCoarse",
        "dpdxFine",
        "dpdy",
        "dpdyCoarse",
        "dpdyFine",
        "fwidth",
        "fwidthCoarse",
        "fwidthFine",
        "textureDimensions",
        "textureGather",
        "textureGatherCompare",
        "textureLoad",
        "textureNumLayers",
        "textureNumLevels",
        "textureNumSamples",
        "textureSample",
        "textureSampleBias",
        "textureSampleCompare",
        "textureSampleCompareLevel",
        "textureSampleGrad",
        "textureSampleLevel",
        "textureSampleBaseClampToEdge",
        "textureStore",
        "atomicLoad",
        "atomicStore",
        "atomicAdd",
        "atomicSub",
        "atomicMax",
        "atomicMin",
        "atomicAnd",
        "atomicOr",
        "atomicXor",
        "atomicExchange",
        "atomicCompareExchangeWeak",
        "pack4x8snorm",
        "pack4x8unorm",
        "pack4xI8",
        "pack4xU8",
        "pack4xI8Clamp",
        "pack4xU8Clamp",
        "pack2x16snorm",
        "pack2x16unorm",
        "pack2x16float",
        "unpack4x8snorm",
        "unpack4x8unorm",
        "unpack4xI8",
        "unpack4xU8",
        "unpack2x16snorm",
        "unpack2x16unorm",
        "unpack2x16float",
        "storageBarrier",
        "textureBarrier",
        "workgroupBarrier",
        "workgroupUniformLoad",
        "subgroupAdd",
        "subgroupExclusiveAdd",
        "subgroupInclusiveAdd",
        "subgroupAll",
        "subgroupAnd",
        "subgroupAny",
        "subgroupBallot",
        "subgroupBroadcast",
        "subgroupBroadcastFirst",
        "subgroupElect",
        "subgroupMax",
        "subgroupMin",
        "subgroupMul",
        "subgroupExclusiveMul",
        "subgroupInclusiveMul",
        "subgroupOr",
        "subgroupShuffle",
        "subgroupShuffleDown",
        "subgroupShuffleUp",
        "subgroupShuffleXor",
        "subgroupXor",
        "quadBroadcast",
        "quadSwapDiagonal",
        "quadSwapX",
        "quadSwapY",
    )

// TODO(Complete this list)
val builtinNamedTypes =
    listOf(
        "vec2f",
        "vec3f",
        "vec4f",
    )

/**
 * A shader module declares "uniform" variables to allow data to be passed into a pipeline invocation. Uniforms can be
 * dynamic between pipeline invocations, but are constant during a particular pipeline invocation.
 *
 * On the assumption that a translation unit declares a uniform bound to group [group] and binding [binding], this
 * function yields that associated global variable declaration.
 *
 * @receiver the translation unit being queried
 * @param group the group associated with the uniform declaration
 * @param binding the binding associated with the uniform declaration
 * @return the global variable declaration for the uniform
 * @throws [NoSuchElementException] if no matching global variable exists
 */
fun TranslationUnit.getUniformDeclaration(
    group: Int,
    binding: Int,
): GlobalDecl.Variable {
    val uniformDeclaration =
        globalDecls.filterIsInstance<GlobalDecl.Variable>().first {
            (
                it.attributes
                    .filterIsInstance<Attribute.Group>()
                    .first()
                    .expression as Expression.IntLiteral
            ).text.toInt() == group &&
                (
                    it.attributes
                        .filterIsInstance<Attribute.Binding>()
                        .first()
                        .expression as Expression.IntLiteral
                ).text.toInt() == binding
        }
    return uniformDeclaration
}

fun isStatementFunctionCallBuiltin(functionCall: Statement.FunctionCall): Boolean {
    // This set should contain the names of all builtin functions that do not have the 'must_use' attribute
    val statementFunctionCallBuiltins =
        hashSetOf(
            "atomicStore",
            "storageBarrier",
            "textureBarrier",
            "textureStore",
            "workgroupBarrier",
        )
    return functionCall.callee in statementFunctionCallBuiltins
}
