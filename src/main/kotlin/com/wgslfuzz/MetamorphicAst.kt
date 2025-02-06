package com.wgslfuzz

class ObfuscatedConstant(
    val obfuscated: Expression,
    val constant: Expression) : MetamorphicExpression

interface MetamorphicExpression : Expression {
    class ZeroPlusExpression(val original: Expression, val zero: ObfuscatedConstant) : MetamorphicExpression
    class ExpressionPlusZero(val original: Expression, val zero: ObfuscatedConstant) : MetamorphicExpression

    class ZeroMinusNegativeExpression(
        val original: Expression,
        val zero: ObfuscatedConstant,
        val minusOne: ObfuscatedConstant) : MetamorphicExpression
    class ExpressionMinusZero(val original: Expression, val zero: ObfuscatedConstant) : MetamorphicExpression

    class OneTimesExpression(val original: Expression, val one: ObfuscatedConstant) : MetamorphicExpression
    class ExpressionTimesOne(val original: Expression, val one: ObfuscatedConstant) : MetamorphicExpression

    class ExpressionDivideOne(val original: Expression, val one: ObfuscatedConstant) : MetamorphicExpression
}

interface MetamorphicStatement : Statement {
    class IfTrue(
        val trueCondition: ObfuscatedConstant,
        val executableThenBranch: Statement,
        val deadElseBranch: Statement? = null,
    )

    class IfFalse(
        val falseCondition: ObfuscatedConstant,
        val deadThenBranch: Statement,
        val executableElseBranch: Statement? = null,
    )

}
