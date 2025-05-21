package com.wgslfuzz.core

sealed interface MetamorphicExpression : Expression {
    class FalseByConstruction(
        val falseExpression: Expression,
    ) : MetamorphicExpression

    class TrueByConstruction(
        val trueExpression: Expression,
    ) : MetamorphicExpression
}

sealed interface MetamorphicStatement : Statement {
    class DeadCodeFragment(
        val statement: Statement,
    ) : MetamorphicStatement {
        init {
            when (statement) {
                is Statement.If -> {
                    require(statement.condition is MetamorphicExpression.FalseByConstruction)
                }
                is Statement.While -> {
                    require(statement.condition is MetamorphicExpression.FalseByConstruction)
                }
                else -> {
                    throw UnsupportedOperationException("Only 'if' and 'while' currently supported for dead statement.")
                }
            }
        }
    }
}
