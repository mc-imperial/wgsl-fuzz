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
import java.io.PrintStream

@Serializable
sealed interface AugmentedMetadata : MetadataWithCommentary {
    val id: Int

    fun reverse(node: AstNode): ReverseResult

    @Serializable
    class AdditionalParen(
        override val id: Int,
    ) : AugmentedMetadata {
        override fun reverse(node: AstNode) = ReverseResult.ReversedNode((node as Expression.Paren).target)

        override fun emitCommentary(
            out: PrintStream,
            emitIndent: () -> Unit,
        ) {}
    }

    @Serializable
    class ReverseToLhsBinaryOperator(
        override val id: Int,
        private val commentary: String?,
    ) : AugmentedMetadata {
        override fun reverse(node: AstNode): ReverseResult {
            require(node is Expression.Binary)
            return ReverseResult.ReversedNode(node.lhs)
        }

        override fun emitCommentary(
            out: PrintStream,
            emitIndent: () -> Unit,
        ) {
            if (commentary != null) {
                out.print("/* $commentary */ ")
            }
        }
    }

    @Serializable
    class ReverseToRhsBinaryOperator(
        override val id: Int,
        private val commentary: String?,
    ) : AugmentedMetadata {
        override fun reverse(node: AstNode): ReverseResult {
            require(node is Expression.Binary)
            return ReverseResult.ReversedNode(node.rhs)
        }

        override fun emitCommentary(
            out: PrintStream,
            emitIndent: () -> Unit,
        ) {
            if (commentary != null) {
                out.print("/* $commentary */ ")
            }
        }
    }

    @Serializable
    class DeletableStatement(
        override val id: Int,
        private val commentary: String,
    ) : AugmentedMetadata {
        override fun reverse(node: AstNode): ReverseResult = ReverseResult.DeletedNode

        override fun emitCommentary(
            out: PrintStream,
            emitIndent: () -> Unit,
        ) {
            emitIndent()
            out.print("/* $commentary */\n")
        }
    }

    @Serializable
    class EmptiableCompound(
        override val id: Int,
        private val commentary: String,
    ) : AugmentedMetadata {
        override fun reverse(node: AstNode): ReverseResult = ReverseResult.ReversedNode(Statement.Compound(emptyList()))

        override fun emitCommentary(
            out: PrintStream,
            emitIndent: () -> Unit,
        ) {
            emitIndent()
            out.print("/* $commentary */\n")
        }
    }

    @Serializable
    class KnownValue(
        override val id: Int,
        val knownValue: Expression,
    ) : AugmentedMetadata {
        init {
            if (knownValue is Expression.FloatLiteral) {
                val doubleValue = knownValue.text.removeSuffix("f").toDouble()
                val floatValue =
                    knownValue.text
                        .removeSuffix("f")
                        .toFloat()
                        .toDouble()
                if (doubleValue != floatValue) {
                    throw UnsupportedOperationException(
                        "A floating-point known value must be exactly representable; found value $doubleValue which does not match float representation $floatValue.",
                    )
                }
            }
        }

        override fun reverse(node: AstNode): ReverseResult = ReverseResult.ReversedNode(knownValue.clone())

        override fun emitCommentary(
            out: PrintStream,
            emitIndent: () -> Unit,
        ) {
            out.print("/* known value: ")
            out.print(
                when (knownValue) {
                    is Expression.BoolLiteral -> knownValue.text
                    is Expression.IntLiteral -> knownValue.text
                    is Expression.FloatLiteral -> knownValue.text
                    else -> TODO("Emit commentary not implement for $knownValue")
                },
            )
            out.print(" */ ")
        }
    }
}

sealed interface ReverseResult {
    data class ReversedNode(
        val node: AstNode,
    ) : ReverseResult

    object DeletedNode : ReverseResult
}

@Serializable
sealed interface OldAugmentedMetadata : Metadata {
    @Serializable
    data class ControlFlowWrapperMetaData(
        // id uniquely corresponds to a parent ControlFlowWrapper node.
        // For more information look at the comments of ControlFlowWrapper.
        val id: Int,
    ) : OldAugmentedMetadata

    @Serializable
    object FunctionForArbitraryCompoundsFromDonorShader : OldAugmentedMetadata
}
