package com.wgslfuzz

fun <T> traverse(
    action: (node: AstNode, traversalState: T) -> Unit,
    node: AstNode,
    traversalState: T,
) {
    val actionWithState: (child: AstNode) -> Unit = { child -> action(child, traversalState) }
    when (node) {
        CaseSelectors.DefaultAlone -> {}
        GlobalDecl.Empty -> {}
        Statement.Break -> {}
        Statement.Continue -> {}
        Statement.Discard -> {}
        Statement.Empty -> {}
        is Attribute -> {}
        is Directive -> {}
        is Expression.BoolLiteral -> {}
        is Expression.FloatLiteral -> {}
        is Expression.Identifier -> {}
        is Expression.IntLiteral -> {}
        is LhsExpression.Identifier -> {}

        is CaseSelectors.ExpressionsOrDefault -> {
            node.expressions.forEach {
                it?.let(actionWithState)
            }
        }
        is ContinuingStatement -> {
            node.attributes.forEach(actionWithState)
            node.statements.forEach(actionWithState)
            node.breakIfExpr?.let(actionWithState)
        }
        is Expression.Binary -> {
            actionWithState(node.lhs)
            actionWithState(node.rhs)
        }
        is Expression.FunctionCall -> {
            node.templateParameter?.let(actionWithState)
            node.args.forEach(actionWithState)
        }
        is Expression.IndexLookup -> {
            actionWithState(node.target)
            actionWithState(node.index)
        }
        is Expression.MemberLookup -> {
            actionWithState(node.receiver)
        }
        is Expression.Paren -> {
            actionWithState(node.target)
        }
        is Expression.Unary -> {
            actionWithState(node.target)
        }
        is Expression.ValueConstructor -> {
            node.args.forEach(actionWithState)
        }
        is GlobalDecl.ConstAssert -> {
            actionWithState(node.expression)
        }
        is GlobalDecl.Constant -> {
            node.type?.let(actionWithState)
            actionWithState(node.initializer)
        }
        is GlobalDecl.Function -> {
            node.attributes.forEach(actionWithState)
            node.parameters.forEach(actionWithState)
            node.returnType?.let(actionWithState)
            actionWithState(node.body)
        }
        is GlobalDecl.Override -> {
            node.attributes.forEach(actionWithState)
            node.type?.let(actionWithState)
            node.initializer?.let(actionWithState)
        }
        is GlobalDecl.Struct -> {
            node.members.forEach(actionWithState)
        }
        is GlobalDecl.TypeAlias -> {
            actionWithState(node.type)
        }
        is GlobalDecl.Variable -> {
            node.attributes.forEach(actionWithState)
            node.type?.let(actionWithState)
            node.initializer?.let(actionWithState)
        }
        is LhsExpression.AddressOf -> {
            actionWithState(node.target)
        }
        is LhsExpression.IndexLookup -> {
            actionWithState(node.target)
            actionWithState(node.index)
        }
        is LhsExpression.Dereference -> {
            actionWithState(node.target)
        }
        is LhsExpression.MemberLookup -> {
            actionWithState(node.receiver)
        }
        is LhsExpression.Paren -> {
            actionWithState(node.target)
        }
        is ParameterDecl -> {
            node.attributes.forEach(actionWithState)
            actionWithState(node.typeDecl)
        }
        is Statement.ConstAssert -> {
            actionWithState(node.expression)
        }
        is Statement.Compound -> {
            node.statements.forEach(actionWithState)
        }
        is Statement.If -> {
            node.attributes.forEach(actionWithState)
            actionWithState(node.condition)
            actionWithState(node.thenBranch)
            node.elseBranch?.let(actionWithState)
        }
        is Statement.For -> {
            node.attributes.forEach(actionWithState)
            node.init?.let(actionWithState)
            node.condition?.let(actionWithState)
            node.update?.let(actionWithState)
            actionWithState(node.body)
        }
        is Statement.Assignment -> {
            node.lhsExpression?.let(actionWithState)
            actionWithState(node.rhs)
        }
        is Statement.Decrement -> {
            actionWithState(node.target)
        }
        is Statement.FunctionCall -> {
            TODO()
        }
        is Statement.Increment -> {
            actionWithState(node.target)
        }
        is Statement.Value -> {
            node.type?.let(actionWithState)
            node.initializer.let(actionWithState)
        }
        is Statement.Variable -> {
            node.type?.let(actionWithState)
            node.initializer?.let(actionWithState)
        }
        is Statement.Loop -> {
            TODO()
        }
        is Statement.Return -> {
            node.expr?.let(actionWithState)
        }
        is Statement.Switch -> {
            TODO()
        }
        is Statement.While -> {
            node.attributes.forEach(actionWithState)
            actionWithState(node.expression)
            actionWithState(node.body)
        }
        is StructMember -> {
            node.attributes.forEach(actionWithState)
            actionWithState(node.type)
        }
        is SwitchClause -> {
            TODO()
        }
        is TranslationUnit -> {
            node.directives.forEach(actionWithState)
            node.globalDecls.forEach(actionWithState)
        }
        is TypeDecl.Array -> {
            actionWithState(node.elementType)
            node.elementCount?.let(actionWithState)
        }
        is TypeDecl.MatrixTypeDecl -> {
            TODO()
        }
        is TypeDecl.NamedType -> {
            node.templateArgs.forEach(actionWithState)
        }
        is TypeDecl.Pointer -> {
            actionWithState(node.pointeeType)
        }
        is TypeDecl.ScalarTypeDecl -> {
            // Nothing to do
        }
        is TypeDecl.VectorTypeDecl -> {
            actionWithState(node.elementType)
        }
    }
}
