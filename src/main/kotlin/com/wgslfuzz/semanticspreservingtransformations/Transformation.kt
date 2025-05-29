package com.wgslfuzz.semanticspreservingtransformations

import com.wgslfuzz.core.ParsedShaderJob

typealias MetamorphicTransformation = (parsedShaderJob: ParsedShaderJob, fuzzerSettings: FuzzerSettings) -> ParsedShaderJob

val metamorphicTransformations: List<MetamorphicTransformation> =
    listOf(
        ::addDeadDiscards,
        ::addDeadBreaks,
        ::addDeadContinues,
        ::addDeadReturns,
    )
