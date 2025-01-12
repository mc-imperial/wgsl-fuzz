package com.wgslfuzz

fun <T> traverse(
    action: (node: AstNode, traversalState: T) -> Unit,
    node: AstNode,
    traversalState: T,
) {
    val actionWithState: (child: AstNode) -> Unit = { child -> action(child, traversalState) }
    when (node) {
        is CaseSelectors.DefaultAlone -> {}
        is GlobalDecl.Empty -> {}
        is Statement.Break -> {}
        is Statement.Continue -> {}
        is Statement.Discard -> {}
        is Statement.Empty -> {}
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
            when (node) {
                is Expression.VectorValueConstructor -> node.elementType?.let(actionWithState)
                is Expression.MatrixValueConstructor -> node.elementType?.let(actionWithState)
                is Expression.ArrayValueConstructor -> {
                    node.elementType?.let(actionWithState)
                    node.elementCount?.let(actionWithState)
                }
                is Expression.ScalarValueConstructor -> {
                    // Nothing to do
                }
                is Expression.StructValueConstructor -> {
                    // Nothing to do
                }
                is Expression.TypeAliasValueConstructor -> {
                    // Nothing to do
                }
            }
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
            node.body.forEach(actionWithState)
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
            node.body.forEach(actionWithState)
        }
        is Statement.Assignment -> {
            node.lhsExpression?.let(actionWithState)
            actionWithState(node.rhs)
        }
        is Statement.Decrement -> {
            actionWithState(node.target)
        }
        is Statement.FunctionCall -> {
            node.args.forEach(actionWithState)
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
            node.attributesAtStart.forEach(actionWithState)
            node.attributesBeforeBody.forEach(actionWithState)
            node.body.forEach(actionWithState)
            node.continuingStatement?.let(actionWithState)
        }
        is Statement.Return -> {
            node.expression?.let(actionWithState)
        }
        is Statement.Switch -> {
            node.attributesAtStart.forEach(actionWithState)
            actionWithState(node.expression)
            node.attributesBeforeBody.forEach(actionWithState)
            node.clauses.forEach(actionWithState)
        }
        is Statement.While -> {
            node.attributes.forEach(actionWithState)
            actionWithState(node.condition)
            actionWithState(node.body)
        }
        is StructMember -> {
            node.attributes.forEach(actionWithState)
            actionWithState(node.type)
        }
        is SwitchClause -> {
            actionWithState(node.caseSelectors)
            actionWithState(node.compoundStatement)
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
            actionWithState(node.elementType)
        }
        is TypeDecl.NamedType -> {
            // Nothing to do
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
        is TypeDecl.Atomic -> {
            actionWithState(node.targetType)
        }
        is TypeDecl.SamplerComparison -> {
            // Nothing to do
        }
        is TypeDecl.SamplerRegular -> {
            // Nothing to do
        }
        is TypeDecl.TextureDepth2D -> {
            // Nothing to do
        }
        is TypeDecl.TextureDepth2DArray -> {
            // Nothing to do
        }
        is TypeDecl.TextureDepthCube -> {
            // Nothing to do
        }
        is TypeDecl.TextureDepthCubeArray -> {
            // Nothing to do
        }
        is TypeDecl.TextureDepthMultisampled2D -> {
            // Nothing to do
        }
        is TypeDecl.TextureExternal -> {
            // Nothing to do
        }
        is TypeDecl.TextureMultisampled2d -> {
            actionWithState(node.sampledType)
        }
        is TypeDecl.TextureSampled1D -> {
            actionWithState(node.sampledType)
        }
        is TypeDecl.TextureSampled2D -> {
            actionWithState(node.sampledType)
        }
        is TypeDecl.TextureSampled2DArray -> {
            actionWithState(node.sampledType)
        }
        is TypeDecl.TextureSampled3D -> {
            actionWithState(node.sampledType)
        }
        is TypeDecl.TextureSampledCube -> {
            actionWithState(node.sampledType)
        }
        is TypeDecl.TextureSampledCubeArray -> {
            actionWithState(node.sampledType)
        }
        is TypeDecl.TextureStorage1D -> {
            // Nothing to do
        }
        is TypeDecl.TextureStorage2D -> {
            // Nothing to do
        }
        is TypeDecl.TextureStorage2DArray -> {
            // Nothing to do
        }
        is TypeDecl.TextureStorage3D -> {
            // Nothing to do
        }
    }
}
