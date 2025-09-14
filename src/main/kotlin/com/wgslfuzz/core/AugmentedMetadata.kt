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
            if (commentary != "") {
                emitIndent()
                out.print("/* $commentary */\n")
            }
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

    @Serializable
    class IfControlFlowWrap(
        override val id: Int,
        private val originalStatementsInThenBranch: Boolean,
    ) : AugmentedMetadata {
        override fun reverse(node: AstNode): ReverseResult {
            val ifStatement =
                node as? Statement.If
                    ?: throw IllegalArgumentException("IfControlFlowWrap metadata attached to node which is not a if statement")

            val originalStatements =
                if (originalStatementsInThenBranch) ifStatement.thenBranch else (ifStatement.elseBranch as Statement.Compound)

            return ReverseResult.ReversedNode(originalStatements)
        }

        override fun emitCommentary(
            out: PrintStream,
            emitIndent: () -> Unit,
        ) {
            emitIndent()
            out.print("/* control flow wrap: */\n")
        }
    }

    @Serializable
    class ForLoopControlFlowWrap(
        override val id: Int,
    ) : AugmentedMetadata {
        override fun reverse(node: AstNode): ReverseResult =
            (node as? Statement.For)
                ?.let { ReverseResult.ReversedNode(it.body) }
                ?: throw IllegalArgumentException("ForLoopControlFlowWrap metadata attached to node which is not a for statement")

        override fun emitCommentary(
            out: PrintStream,
            emitIndent: () -> Unit,
        ) {
            emitIndent()
            out.print("/* control flow wrap: */\n")
        }
    }

    @Serializable
    class WhileLoopControlFlowWrap(
        override val id: Int,
    ) : AugmentedMetadata {
        override fun reverse(node: AstNode): ReverseResult =
            (node as? Statement.While)
                ?.let { ReverseResult.ReversedNode(it.body) }
                ?: throw IllegalArgumentException("WhileLoopControlFlowWrap metadata attached to node which is not a while statement")

        override fun emitCommentary(
            out: PrintStream,
            emitIndent: () -> Unit,
        ) {
            emitIndent()
            out.print("/* control flow wrap: */\n")
        }
    }

    @Serializable
    class SwitchControlFlowWrap(
        override val id: Int,
        private val originalStatementsCaseIndex: Int,
    ) : AugmentedMetadata {
        override fun reverse(node: AstNode): ReverseResult =
            (node as? Statement.Switch)
                ?.clauses[originalStatementsCaseIndex]
                ?.compoundStatement
                ?.let { ReverseResult.ReversedNode(it) }
                ?: throw IllegalArgumentException("SwitchControlFlowWrap metadata attached to node which is not a switch statement")

        override fun emitCommentary(
            out: PrintStream,
            emitIndent: () -> Unit,
        ) {
            emitIndent()
            out.print("/* control flow wrap: */\n")
        }
    }
}

sealed interface ReverseResult {
    data class ReversedNode(
        val node: AstNode,
    ) : ReverseResult

    object DeletedNode : ReverseResult
}

fun ReverseResult.map(transform: (AstNode) -> AstNode) =
    when (this) {
        is ReverseResult.ReversedNode ->
            ReverseResult.ReversedNode(
                transform(this.node),
            )
        ReverseResult.DeletedNode -> ReverseResult.DeletedNode
    }

@Serializable
class AddedIdentifier(
    val name: String,
) : MetadataWithCommentary {
    override fun emitCommentary(
        out: PrintStream,
        emitIndent: () -> Unit,
    ) {
        emitIndent()
        out.print("/* Added identifier */\n")
    }
}
