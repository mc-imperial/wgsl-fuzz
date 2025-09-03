package com.wgslfuzz.core

import kotlin.test.Test
import kotlin.test.assertEquals

class IntLiteralToBytesTest {
    @Test
    fun `Test positive i32 1`() {
        val intExpression = Expression.IntLiteral(2.toString())

        assertEquals(listOf(2, 0, 0, 0), intLiteralToBytes(intExpression))
    }

    @Test
    fun `Test positive i32 2`() {
        val intExpression = Expression.IntLiteral(255.toString())

        assertEquals(listOf(255, 0, 0, 0), intLiteralToBytes(intExpression))
    }

    @Test
    fun `Test positive i32 3`() {
        val intExpression = Expression.IntLiteral(10000000.toString())

        assertEquals(listOf(0x80, 0x96, 0x98, 0x00), intLiteralToBytes(intExpression))
    }

    @Test
    fun `Test large positive i32`() {
        val intExpression = Expression.IntLiteral(Int.MAX_VALUE.toString())

        assertEquals(listOf(255, 255, 255, 127), intLiteralToBytes(intExpression))
    }

    @Test
    fun `Test negative i32 1`() {
        val intExpression = Expression.IntLiteral((-1).toString())

        assertEquals(listOf(255, 255, 255, 255), intLiteralToBytes(intExpression))
    }

    @Test
    fun `Test negative i32 2`() {
        val intExpression = Expression.IntLiteral((-53).toString())

        assertEquals(listOf(203, 255, 255, 255), intLiteralToBytes(intExpression))
    }

    @Test
    fun `Test negative i32 3`() {
        val intExpression = Expression.IntLiteral((-100000000).toString())

        assertEquals(listOf(0x00, 0x1f, 0x0a, 0xfa), intLiteralToBytes(intExpression))
    }

    @Test
    fun `Test large negative i32`() {
        val intExpression = Expression.IntLiteral(Int.MIN_VALUE.toString())

        assertEquals(listOf(0, 0, 0, 128), intLiteralToBytes(intExpression))
    }
}
