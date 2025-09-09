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
}
