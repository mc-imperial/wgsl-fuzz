package com.wgslfuzz.core

data class UniformBufferInfo(
    val group: Int,
    val binding: Int,
    val data: List<Int>, // Each integer represents a byte
)

data class ShaderJob(
    val shaderText: String,
    val uniformBuffers: List<UniformBufferInfo>,
)
