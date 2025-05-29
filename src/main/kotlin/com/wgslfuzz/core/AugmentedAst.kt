package com.wgslfuzz.core

// This file extends the AstNode hierarchy with additional nodes that *augment* the AST to encode transformations that
// have been applied to a translation unit.
//
// The motivation for this is that if such transformations trigger bugs it would be useful to be able to *undo* them in
// a principled way to home in on a minimal sequence of transformations that suffices to trigger a bug.
//
// Because Kotlin requires all classes that implement a sealed interface to reside in the same package as that
// interface, the interfaces and classes that define new AST nodes for transformations should appear in this file in the
// "core" package. However, details of how transformations are actually applied should be separated into other packages.

/**
 * An umbrella for all nodes representing augmentations to an AST.
 */
sealed interface AugmentedAstNode : AstNode

/**
 * Augmented expressions, which may include (for example) transformations applied to existing expressions, or
 * expressions that support transformations applied to statements.
 */
sealed interface AugmentedExpression :
    AugmentedAstNode,
    Expression {
    /**
     * An expression that is guaranteed to evaluate to false, without observable side effects.
     *
     * It is up to the client that creates such an expression to ensure that evaluation to false is guaranteed and that
     * there are no observable side effects. The augmented node merely serves as marker in the AST to indicate that this
     * could be replaced with a literal "false" expression without changing semantics.
     */
    class FalseByConstruction(
        val falseExpression: Expression,
    ) : AugmentedExpression

    /**
     * Similar to [FalseByConstruction], but for an expression that is guaranteed to evaluate to true.
     */
    class TrueByConstruction(
        val trueExpression: Expression,
    ) : AugmentedExpression
}

/**
 * Augmented statements, such as dead code fragments.
 */
sealed interface AugmentedStatement :
    AugmentedAstNode,
    Statement {
    /**
     * A statement whose execution is guaranteed to have no observable effect due to the statement being guarded by a
     * false condition, meaning that it is safe to remove the statement entirely, or manipulate the internals of the
     * statement (under the false guard).
     *
     * It is up to the client that creates such a statement to ensure that the statement guard really does evaluate to
     * false. The augmented node merely serves as marker in the AST to indicate that this statement could be removed or
     * manipulated in other ways without changing semantics.
     */
    class DeadCodeFragment(
        val statement: Statement,
    ) : AugmentedStatement {
        init {
            when (statement) {
                is Statement.If -> {
                    require(statement.condition is AugmentedExpression.FalseByConstruction)
                }
                is Statement.While -> {
                    require(statement.condition is AugmentedExpression.FalseByConstruction)
                }
                else -> {
                    throw UnsupportedOperationException("Only 'if' and 'while' currently supported for dead statement.")
                }
            }
        }
    }
}
