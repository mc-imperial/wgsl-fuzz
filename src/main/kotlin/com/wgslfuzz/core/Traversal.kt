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

fun <T> traverse(
    action: (node: AstNode, traversalState: T) -> Unit,
    node: AstNode,
    traversalState: T,
) {
    val actionWithState: (child: AstNode) -> Unit = { child -> action(child, traversalState) }
    when (node) {
        is Attribute.Builtin -> {}
        is Attribute.Compute -> {}
        is Attribute.Const -> {}
        is Attribute.Diagnostic -> {}
        is Attribute.Fragment -> {}
        is Attribute.Interpolate -> {}
        is Attribute.Invariant -> {}
        is Attribute.MustUse -> {}
        is Attribute.Vertex -> {}
        is GlobalDecl.Empty -> {}
        is Statement.Break -> {}
        is Statement.Continue -> {}
        is Statement.Discard -> {}
        is Statement.Empty -> {}
        is Directive -> {}
        is Expression.BoolLiteral -> {}
        is Expression.FloatLiteral -> {}
        is Expression.Identifier -> {}
        is Expression.IntLiteral -> {}
        is LhsExpression.Identifier -> {}

        is Attribute.Align -> actionWithState(node.expression)
        is Attribute.Binding -> actionWithState(node.expression)
        is Attribute.BlendSrc -> actionWithState(node.expression)
        is Attribute.Group -> actionWithState(node.expression)
        is Attribute.Id -> actionWithState(node.expression)
        is Attribute.InputAttachmentIndex -> actionWithState(node.expression)
        is Attribute.Location -> actionWithState(node.expression)
        is Attribute.Size -> actionWithState(node.expression)
        is Attribute.WorkgroupSize -> {
            actionWithState(node.sizeX)
            node.sizeY?.let(actionWithState)
            node.sizeZ?.let(actionWithState)
        }

        is ContinuingStatement -> {
            node.attributes.forEach(actionWithState)
            actionWithState(node.statements)
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
        is AugmentedExpression.FalseByConstruction -> {
            actionWithState(node.falseExpression)
        }
        is AugmentedExpression.TrueByConstruction -> {
            actionWithState(node.trueExpression)
        }
        is AugmentedExpression.AddZero -> {
            actionWithState(node.originalExpression)
            actionWithState(node.zeroExpression)
        }
        is AugmentedExpression.DivOne -> {
            actionWithState(node.originalExpression)
            actionWithState(node.oneExpression)
        }
        is AugmentedExpression.MulOne -> {
            actionWithState(node.originalExpression)
            actionWithState(node.oneExpression)
        }
        is AugmentedExpression.SubZero -> {
            actionWithState(node.originalExpression)
            actionWithState(node.zeroExpression)
        }
        is AugmentedExpression.KnownValue -> {
            actionWithState(node.knownValue)
            actionWithState(node.expression)
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
            node.returnAttributes.forEach(actionWithState)
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
            actionWithState(node.body)
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
        is AugmentedStatement.DeadCodeFragment -> {
            actionWithState(node.statement)
        }
        is StructMember -> {
            node.attributes.forEach(actionWithState)
            actionWithState(node.type)
        }
        is SwitchClause -> {
            node.caseSelectors.forEach {
                it?.let(actionWithState)
            }
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

fun nodesPreOrder(rootNode: AstNode): List<AstNode> {
    fun collectNodes(
        node: AstNode,
        nodes: MutableList<AstNode>,
    ) {
        nodes.add(node)
        traverse(::collectNodes, node, nodes)
    }
    val result = mutableListOf<AstNode>()
    collectNodes(rootNode, result)
    return result
}

fun nodesPostOrder(rootNode: AstNode): List<AstNode> {
    fun collectNodes(
        node: AstNode,
        nodes: MutableList<AstNode>,
    ) {
        traverse(::collectNodes, node, nodes)
        nodes.add(node)
    }
    val result = mutableListOf<AstNode>()
    collectNodes(rootNode, result)
    return result
}
