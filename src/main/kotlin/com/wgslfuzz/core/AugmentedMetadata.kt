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

@Serializable
sealed interface AugmentedMetadata : Metadata {
    @Serializable
    data class ControlFlowWrapperMetaData(
        // id uniquely corresponds to a parent ControlFlowWrapper node.
        // For more information look at the comments of ControlFlowWrapper.
        val id: Int,
    ) : AugmentedMetadata

    /**
     * Metadata held by an arbitrary compound
     * If a Compound has this then it can be removed by the reducer since it is an arbitrarily generated compound
     */
    @Serializable
    object ArbitraryCompoundMetaData : AugmentedMetadata

    @Serializable
    object FunctionForArbitraryCompoundsFromDonorShader : AugmentedMetadata

    @Serializable
    data class BinaryIdentityOperation(
        private val originalOnLeft: Boolean,
        val commentary: String,
    ) : AugmentedMetadata {
        fun originalExpression(expression: Expression.Binary): Expression = if (originalOnLeft) expression.lhs else expression.rhs
    }
}
