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

/**
 * Deeply clones an [AstNode], allowing for given subtrees to be replaced according to a provided replacements function.
 *
 * @receiver an [AstNode] to be cloned
 * @param replacements a partial function on [AstNode]s. If the function provides a non-null result for the node being
 *   cloned, that result will be returned instead of a clone operation occurring. The [replacements] function must be
 *   constructed such that whatever it returns for a given node should have an appropriate type for replacing the node
 *   in the AST.
 */
@Suppress("UNCHECKED_CAST")
fun <T : AstNode> T.clone(replacements: (AstNode) -> AstNode? = { null }): T = cloneHelper(this, replacements) as T

fun <T : AstNode> List<T>.clone(replacements: (AstNode) -> AstNode? = { null }): List<T> = map { it.clone(replacements) }

/**
 * Deeply clones an [AstNode] but does not apply the replacements function to `this` only the children of `this`
 */
@Suppress("UNCHECKED_CAST")
fun <T : AstNode> T.cloneWithoutReplacementOnFirstNode(replacements: (AstNode) -> AstNode?): T = deepCloneHelper(this, replacements) as T

private fun cloneHelper(
    node: AstNode,
    replacements: (AstNode) -> AstNode?,
): AstNode =
    // If the replacements function provides a replacement for this node, return the replacement.
    // No replacement was provided, so proceed to deep-clone the node.
    replacements(node) ?: deepCloneHelper(node, replacements)

private fun deepCloneHelper(
    node: AstNode,
    replacements: (AstNode) -> AstNode?,
) = with(node) {
    when (this) {
        is Attribute.Align -> Attribute.Align(expression.clone(replacements), metadata)
        is Attribute.Binding -> Attribute.Binding(expression.clone(replacements), metadata)
        is Attribute.BlendSrc -> Attribute.BlendSrc(expression.clone(replacements), metadata)
        is Attribute.Builtin -> Attribute.Builtin(name, metadata)
        is Attribute.Compute -> Attribute.Compute(metadata)
        is Attribute.Const -> Attribute.Const(metadata)
        is Attribute.Diagnostic -> Attribute.Diagnostic(severityControl, diagnosticRule, metadata)
        is Attribute.Fragment -> Attribute.Fragment(metadata)
        is Attribute.Group -> Attribute.Group(expression.clone(replacements), metadata)
        is Attribute.Id -> Attribute.Id(expression.clone(replacements), metadata)
        is Attribute.InputAttachmentIndex -> Attribute.InputAttachmentIndex(expression.clone(replacements), metadata)
        is Attribute.Interpolate -> Attribute.Interpolate(interpolateType, interpolateSampling, metadata)
        is Attribute.Invariant -> Attribute.Invariant(metadata)
        is Attribute.Location -> Attribute.Location(expression.clone(replacements), metadata)
        is Attribute.MustUse -> Attribute.MustUse(metadata)
        is Attribute.Size -> Attribute.Size(expression.clone(replacements), metadata)
        is Attribute.Vertex -> Attribute.Vertex(metadata)
        is Attribute.WorkgroupSize ->
            Attribute.WorkgroupSize(
                sizeX.clone(replacements),
                sizeY?.clone(replacements),
                sizeZ?.clone(replacements),
                metadata,
            )
        is ContinuingStatement ->
            ContinuingStatement(
                attributes.clone(replacements),
                statements.clone(replacements),
                breakIfExpr?.clone(replacements),
                metadata,
            )
        is Directive -> Directive(text, metadata)
        is Expression.Binary -> Expression.Binary(operator, lhs.clone(replacements), rhs.clone(replacements), metadata)
        is Expression.BoolLiteral -> Expression.BoolLiteral(text, metadata)
        is Expression.FloatLiteral -> Expression.FloatLiteral(text, metadata)
        is Expression.FunctionCall ->
            Expression.FunctionCall(
                callee,
                templateParameter?.clone(replacements),
                args.clone(replacements),
                metadata,
            )
        is Expression.Identifier -> Expression.Identifier(name, metadata)
        is Expression.IndexLookup -> Expression.IndexLookup(target.clone(replacements), index.clone(replacements), metadata)
        is Expression.IntLiteral -> Expression.IntLiteral(text, metadata)
        is Expression.MemberLookup -> Expression.MemberLookup(receiver.clone(replacements), memberName, metadata)
        is Expression.Paren -> Expression.Paren(target.clone(replacements), metadata)
        is Expression.Unary -> Expression.Unary(operator, target.clone(replacements), metadata)
        is Expression.ArrayValueConstructor ->
            Expression.ArrayValueConstructor(
                elementType?.clone(replacements),
                elementCount?.clone(replacements),
                args.clone(replacements),
                metadata,
            )
        is Expression.Mat2x2ValueConstructor ->
            Expression.Mat2x2ValueConstructor(
                elementType?.clone(replacements),
                args.clone(replacements),
                metadata,
            )
        is Expression.Mat2x3ValueConstructor ->
            Expression.Mat2x3ValueConstructor(
                elementType?.clone(replacements),
                args.clone(replacements),
                metadata,
            )
        is Expression.Mat2x4ValueConstructor ->
            Expression.Mat2x4ValueConstructor(
                elementType?.clone(replacements),
                args.clone(replacements),
                metadata,
            )
        is Expression.Mat3x2ValueConstructor ->
            Expression.Mat3x2ValueConstructor(
                elementType?.clone(replacements),
                args.clone(replacements),
                metadata,
            )
        is Expression.Mat3x3ValueConstructor ->
            Expression.Mat3x3ValueConstructor(
                elementType?.clone(replacements),
                args.clone(replacements),
                metadata,
            )
        is Expression.Mat3x4ValueConstructor ->
            Expression.Mat3x4ValueConstructor(
                elementType?.clone(replacements),
                args.clone(replacements),
                metadata,
            )
        is Expression.Mat4x2ValueConstructor ->
            Expression.Mat4x2ValueConstructor(
                elementType?.clone(replacements),
                args.clone(replacements),
                metadata,
            )
        is Expression.Mat4x3ValueConstructor ->
            Expression.Mat4x3ValueConstructor(
                elementType?.clone(replacements),
                args.clone(replacements),
                metadata,
            )
        is Expression.Mat4x4ValueConstructor ->
            Expression.Mat4x4ValueConstructor(
                elementType?.clone(replacements),
                args.clone(replacements),
                metadata,
            )
        is Expression.BoolValueConstructor -> Expression.BoolValueConstructor(args.clone(replacements), metadata)
        is Expression.F16ValueConstructor -> Expression.F16ValueConstructor(args.clone(replacements), metadata)
        is Expression.F32ValueConstructor -> Expression.F32ValueConstructor(args.clone(replacements), metadata)
        is Expression.I32ValueConstructor -> Expression.I32ValueConstructor(args.clone(replacements), metadata)
        is Expression.U32ValueConstructor -> Expression.U32ValueConstructor(args.clone(replacements), metadata)
        is Expression.StructValueConstructor -> Expression.StructValueConstructor(constructorName, args.clone(replacements), metadata)
        is Expression.TypeAliasValueConstructor ->
            Expression.TypeAliasValueConstructor(
                constructorName,
                args.clone(replacements),
                metadata,
            )
        is Expression.Vec2ValueConstructor ->
            Expression.Vec2ValueConstructor(
                elementType?.clone(replacements),
                args.clone(replacements),
                metadata,
            )
        is Expression.Vec3ValueConstructor ->
            Expression.Vec3ValueConstructor(
                elementType?.clone(replacements),
                args.clone(replacements),
                metadata,
            )
        is Expression.Vec4ValueConstructor ->
            Expression.Vec4ValueConstructor(
                elementType?.clone(replacements),
                args.clone(replacements),
                metadata,
            )
        is GlobalDecl.ConstAssert -> GlobalDecl.ConstAssert(expression.clone(replacements), metadata)
        is GlobalDecl.Constant -> GlobalDecl.Constant(name, typeDecl?.clone(replacements), initializer.clone(replacements), metadata)
        is GlobalDecl.Empty -> GlobalDecl.Empty(metadata)
        is GlobalDecl.Function ->
            GlobalDecl.Function(
                attributes.clone(replacements),
                name,
                parameters.clone(replacements),
                returnAttributes.clone(replacements),
                returnType?.clone(replacements),
                body.clone(replacements),
                metadata,
            )
        is GlobalDecl.Override ->
            GlobalDecl.Override(
                attributes.clone(replacements),
                name,
                typeDecl?.clone(replacements),
                initializer?.clone(replacements),
                metadata,
            )
        is GlobalDecl.Struct -> GlobalDecl.Struct(name, members.clone(replacements), metadata)
        is GlobalDecl.TypeAlias -> GlobalDecl.TypeAlias(name, typeDecl.clone(replacements), metadata)
        is GlobalDecl.Variable ->
            GlobalDecl.Variable(
                attributes.clone(replacements),
                name,
                addressSpace,
                accessMode,
                typeDecl?.clone(replacements),
                initializer?.clone(replacements),
                metadata,
            )
        is LhsExpression.AddressOf -> LhsExpression.AddressOf(target.clone(replacements), metadata)
        is LhsExpression.Dereference -> LhsExpression.Dereference(target.clone(replacements), metadata)
        is LhsExpression.Identifier -> LhsExpression.Identifier(name, metadata)
        is LhsExpression.IndexLookup -> LhsExpression.IndexLookup(target.clone(replacements), index.clone(replacements), metadata)
        is LhsExpression.MemberLookup -> LhsExpression.MemberLookup(receiver.clone(replacements), memberName, metadata)
        is LhsExpression.Paren -> LhsExpression.Paren(target.clone(replacements), metadata)
        is ParameterDecl -> ParameterDecl(attributes.clone(replacements), name, typeDecl.clone(replacements), metadata)
        is Statement.Break -> Statement.Break(metadata)
        is Statement.ConstAssert -> Statement.ConstAssert(expression.clone(replacements), metadata)
        is Statement.Continue -> Statement.Continue(metadata)
        is Statement.Discard -> Statement.Discard(metadata)
        is Statement.Compound -> Statement.Compound(statements.clone(replacements), metadata)
        is Statement.If ->
            Statement.If(
                attributes.clone(replacements),
                condition.clone(replacements),
                thenBranch.clone(replacements),
                elseBranch?.clone(replacements),
                metadata,
            )
        is Statement.Empty -> Statement.Empty(metadata)
        is Statement.For ->
            Statement.For(
                attributes.clone(replacements),
                init?.clone(replacements),
                condition?.clone(replacements),
                update?.clone(replacements),
                body.clone(replacements),
                metadata,
            )
        is Statement.Assignment ->
            Statement.Assignment(
                lhsExpression?.clone(replacements),
                assignmentOperator,
                rhs.clone(replacements),
                metadata,
            )
        is Statement.Decrement -> Statement.Decrement(target.clone(replacements), metadata)
        is Statement.FunctionCall -> Statement.FunctionCall(callee, args.clone(replacements), metadata)
        is Statement.Increment -> Statement.Increment(target.clone(replacements), metadata)
        is Statement.Value -> Statement.Value(isConst, name, typeDecl?.clone(replacements), initializer.clone(replacements), metadata)
        is Statement.Variable ->
            Statement.Variable(
                name,
                addressSpace,
                accessMode,
                typeDecl?.clone(replacements),
                initializer?.clone(replacements),
                metadata,
            )
        is Statement.Loop ->
            Statement.Loop(
                attributesAtStart.clone(replacements),
                attributesBeforeBody.clone(replacements),
                body.clone(replacements),
                continuingStatement?.clone(replacements),
                metadata,
            )
        is Statement.Return -> Statement.Return(expression?.clone(replacements), metadata)
        is Statement.Switch ->
            Statement.Switch(
                attributesAtStart.clone(replacements),
                expression.clone(replacements),
                attributesBeforeBody.clone(replacements),
                clauses.clone(replacements),
                metadata,
            )
        is Statement.While ->
            Statement.While(
                attributes.clone(replacements),
                condition.clone(replacements),
                body.clone(replacements),
                metadata,
            )
        is StructMember -> StructMember(attributes.clone(replacements), name, typeDecl.clone(replacements), metadata)
        is SwitchClause -> SwitchClause(caseSelectors.map { it?.clone(replacements) }, compoundStatement.clone(replacements), metadata)
        is TranslationUnit -> TranslationUnit(directives.clone(replacements), globalDecls.clone(replacements), metadata)
        is TypeDecl.Array -> TypeDecl.Array(elementType.clone(replacements), elementCount?.clone(replacements), metadata)
        is TypeDecl.Atomic -> TypeDecl.Atomic(targetType.clone(replacements), metadata)
        is TypeDecl.Mat2x2 -> TypeDecl.Mat2x2(elementType.clone(replacements), metadata)
        is TypeDecl.Mat2x3 -> TypeDecl.Mat2x3(elementType.clone(replacements), metadata)
        is TypeDecl.Mat2x4 -> TypeDecl.Mat2x4(elementType.clone(replacements), metadata)
        is TypeDecl.Mat3x2 -> TypeDecl.Mat3x2(elementType.clone(replacements), metadata)
        is TypeDecl.Mat3x3 -> TypeDecl.Mat3x3(elementType.clone(replacements), metadata)
        is TypeDecl.Mat3x4 -> TypeDecl.Mat3x4(elementType.clone(replacements), metadata)
        is TypeDecl.Mat4x2 -> TypeDecl.Mat4x2(elementType.clone(replacements), metadata)
        is TypeDecl.Mat4x3 -> TypeDecl.Mat4x3(elementType.clone(replacements), metadata)
        is TypeDecl.Mat4x4 -> TypeDecl.Mat4x4(elementType.clone(replacements), metadata)
        is TypeDecl.NamedType -> TypeDecl.NamedType(name, metadata)
        is TypeDecl.Pointer -> TypeDecl.Pointer(addressSpace, pointeeType.clone(replacements), accessMode, metadata)
        is TypeDecl.SamplerComparison -> TypeDecl.SamplerComparison(metadata)
        is TypeDecl.SamplerRegular -> TypeDecl.SamplerRegular(metadata)
        is TypeDecl.Bool -> TypeDecl.Bool(metadata)
        is TypeDecl.F16 -> TypeDecl.F16(metadata)
        is TypeDecl.F32 -> TypeDecl.F32(metadata)
        is TypeDecl.I32 -> TypeDecl.I32(metadata)
        is TypeDecl.U32 -> TypeDecl.U32(metadata)
        is TypeDecl.TextureDepth2D -> TypeDecl.TextureDepth2D(metadata)
        is TypeDecl.TextureDepth2DArray -> TypeDecl.TextureDepth2DArray(metadata)
        is TypeDecl.TextureDepthCube -> TypeDecl.TextureDepthCube(metadata)
        is TypeDecl.TextureDepthCubeArray -> TypeDecl.TextureDepthCubeArray(metadata)
        is TypeDecl.TextureDepthMultisampled2D -> TypeDecl.TextureDepthMultisampled2D(metadata)
        is TypeDecl.TextureExternal -> TypeDecl.TextureExternal(metadata)
        is TypeDecl.TextureMultisampled2d -> TypeDecl.TextureMultisampled2d(sampledType.clone(replacements), metadata)
        is TypeDecl.TextureSampled1D -> TypeDecl.TextureSampled1D(sampledType.clone(replacements), metadata)
        is TypeDecl.TextureSampled2D -> TypeDecl.TextureSampled2D(sampledType.clone(replacements), metadata)
        is TypeDecl.TextureSampled2DArray -> TypeDecl.TextureSampled2DArray(sampledType.clone(replacements), metadata)
        is TypeDecl.TextureSampled3D -> TypeDecl.TextureSampled3D(sampledType.clone(replacements), metadata)
        is TypeDecl.TextureSampledCube -> TypeDecl.TextureSampledCube(sampledType.clone(replacements), metadata)
        is TypeDecl.TextureSampledCubeArray -> TypeDecl.TextureSampledCubeArray(sampledType.clone(replacements), metadata)
        is TypeDecl.TextureStorage1D -> TypeDecl.TextureStorage1D(format, accessMode, metadata)
        is TypeDecl.TextureStorage2D -> TypeDecl.TextureStorage2D(format, accessMode, metadata)
        is TypeDecl.TextureStorage2DArray -> TypeDecl.TextureStorage2DArray(format, accessMode, metadata)
        is TypeDecl.TextureStorage3D -> TypeDecl.TextureStorage3D(format, accessMode, metadata)
        is TypeDecl.Vec2 -> TypeDecl.Vec2(elementType.clone(replacements), metadata)
        is TypeDecl.Vec3 -> TypeDecl.Vec3(elementType.clone(replacements), metadata)
        is TypeDecl.Vec4 -> TypeDecl.Vec4(elementType.clone(replacements), metadata)
        is AugmentedStatement.ControlFlowWrapper ->
            AugmentedStatement.ControlFlowWrapper(
                statement.clone(replacements),
                id,
                metadata,
            )
        is AugmentedStatement.ControlFlowWrapHelperStatement ->
            AugmentedStatement.ControlFlowWrapHelperStatement(
                statement.clone(replacements),
                id,
                metadata,
            )
        is AugmentedStatement.ControlFlowWrapReturn ->
            AugmentedStatement.ControlFlowWrapReturn(
                statement.clone(replacements),
                id,
                metadata,
            )
    }
}
