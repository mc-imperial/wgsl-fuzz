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

private fun <T : AstNode> List<T>.clone(replacements: (AstNode) -> AstNode? = { null }): List<T> = map { it.clone(replacements) }

private fun cloneHelper(
    node: AstNode,
    replacements: (AstNode) -> AstNode?,
): AstNode =
    // If the replacements function provides a replacement for this node, return the replacement.
    replacements(node) ?: with(node) {
        // No replacement was provided, so proceed to deep-clone the node.
        when (this) {
            is Attribute.Align -> Attribute.Align(expression.clone(replacements))
            is Attribute.Binding -> Attribute.Binding(expression.clone(replacements))
            is Attribute.BlendSrc -> Attribute.BlendSrc(expression.clone(replacements))
            is Attribute.Builtin -> Attribute.Builtin(name)
            is Attribute.Compute -> Attribute.Compute()
            is Attribute.Const -> Attribute.Const()
            is Attribute.Diagnostic -> Attribute.Diagnostic(severityControl, diagnosticRule)
            is Attribute.Fragment -> Attribute.Fragment()
            is Attribute.Group -> Attribute.Group(expression.clone(replacements))
            is Attribute.Id -> Attribute.Id(expression.clone(replacements))
            is Attribute.InputAttachmentIndex -> Attribute.InputAttachmentIndex(expression.clone(replacements))
            is Attribute.Interpolate -> Attribute.Interpolate(interpolateType, interpolateSampling)
            is Attribute.Invariant -> Attribute.Invariant()
            is Attribute.Location -> Attribute.Location(expression.clone(replacements))
            is Attribute.MustUse -> Attribute.MustUse()
            is Attribute.Size -> Attribute.Size(expression.clone(replacements))
            is Attribute.Vertex -> Attribute.Vertex()
            is Attribute.WorkgroupSize ->
                Attribute.WorkgroupSize(
                    sizeX.clone(replacements),
                    sizeY?.clone(replacements),
                    sizeZ?.clone(replacements),
                )
            is ContinuingStatement ->
                ContinuingStatement(
                    attributes.clone(replacements),
                    statements.clone(replacements),
                    breakIfExpr?.clone(replacements),
                )
            is Directive -> Directive(text)
            is Expression.Binary -> Expression.Binary(operator, lhs.clone(replacements), rhs.clone(replacements))
            is Expression.BoolLiteral -> Expression.BoolLiteral(text)
            is Expression.FloatLiteral -> Expression.FloatLiteral(text)
            is Expression.FunctionCall -> Expression.FunctionCall(callee, templateParameter?.clone(replacements), args.clone(replacements))
            is Expression.Identifier -> Expression.Identifier(name)
            is Expression.IndexLookup -> Expression.IndexLookup(target.clone(replacements), index.clone(replacements))
            is Expression.IntLiteral -> Expression.IntLiteral(text)
            is Expression.MemberLookup -> Expression.MemberLookup(receiver.clone(replacements), memberName)
            is Expression.Paren -> Expression.Paren(target.clone(replacements))
            is Expression.Unary -> Expression.Unary(operator, target.clone(replacements))
            is Expression.ArrayValueConstructor ->
                Expression.ArrayValueConstructor(
                    elementType?.clone(replacements),
                    elementCount?.clone(replacements),
                    args.clone(replacements),
                )
            is Expression.Mat2x2ValueConstructor ->
                Expression.Mat2x2ValueConstructor(
                    elementType?.clone(replacements),
                    args.clone(replacements),
                )
            is Expression.Mat2x3ValueConstructor ->
                Expression.Mat2x3ValueConstructor(
                    elementType?.clone(replacements),
                    args.clone(replacements),
                )
            is Expression.Mat2x4ValueConstructor ->
                Expression.Mat2x4ValueConstructor(
                    elementType?.clone(replacements),
                    args.clone(replacements),
                )
            is Expression.Mat3x2ValueConstructor ->
                Expression.Mat3x2ValueConstructor(
                    elementType?.clone(replacements),
                    args.clone(replacements),
                )
            is Expression.Mat3x3ValueConstructor ->
                Expression.Mat3x3ValueConstructor(
                    elementType?.clone(replacements),
                    args.clone(replacements),
                )
            is Expression.Mat3x4ValueConstructor ->
                Expression.Mat3x4ValueConstructor(
                    elementType?.clone(replacements),
                    args.clone(replacements),
                )
            is Expression.Mat4x2ValueConstructor ->
                Expression.Mat4x2ValueConstructor(
                    elementType?.clone(replacements),
                    args.clone(replacements),
                )
            is Expression.Mat4x3ValueConstructor ->
                Expression.Mat4x3ValueConstructor(
                    elementType?.clone(replacements),
                    args.clone(replacements),
                )
            is Expression.Mat4x4ValueConstructor ->
                Expression.Mat4x4ValueConstructor(
                    elementType?.clone(replacements),
                    args.clone(replacements),
                )
            is Expression.BoolValueConstructor -> Expression.BoolValueConstructor(args.clone(replacements))
            is Expression.F16ValueConstructor -> Expression.F16ValueConstructor(args.clone(replacements))
            is Expression.F32ValueConstructor -> Expression.F32ValueConstructor(args.clone(replacements))
            is Expression.I32ValueConstructor -> Expression.I32ValueConstructor(args.clone(replacements))
            is Expression.U32ValueConstructor -> Expression.U32ValueConstructor(args.clone(replacements))
            is Expression.StructValueConstructor -> Expression.StructValueConstructor(constructorName, args.clone(replacements))
            is Expression.TypeAliasValueConstructor -> Expression.TypeAliasValueConstructor(constructorName, args.clone(replacements))
            is Expression.Vec2ValueConstructor ->
                Expression.Vec2ValueConstructor(
                    elementType?.clone(replacements),
                    args.clone(replacements),
                )
            is Expression.Vec3ValueConstructor ->
                Expression.Vec3ValueConstructor(
                    elementType?.clone(replacements),
                    args.clone(replacements),
                )
            is Expression.Vec4ValueConstructor ->
                Expression.Vec4ValueConstructor(
                    elementType?.clone(replacements),
                    args.clone(replacements),
                )
            is AugmentedExpression.FalseByConstruction ->
                AugmentedExpression.FalseByConstruction(
                    falseExpression.clone(
                        replacements,
                    ),
                )
            is AugmentedExpression.TrueByConstruction ->
                AugmentedExpression.TrueByConstruction(
                    trueExpression.clone(
                        replacements,
                    ),
                )
            is GlobalDecl.ConstAssert -> GlobalDecl.ConstAssert(expression.clone(replacements))
            is GlobalDecl.Constant -> GlobalDecl.Constant(name, typeDecl?.clone(replacements), initializer.clone(replacements))
            is GlobalDecl.Empty -> GlobalDecl.Empty()
            is GlobalDecl.Function ->
                GlobalDecl.Function(
                    attributes.clone(replacements),
                    name,
                    parameters.clone(replacements),
                    returnAttributes.clone(replacements),
                    returnType?.clone(replacements),
                    body.clone(replacements),
                )
            is GlobalDecl.Override ->
                GlobalDecl.Override(
                    attributes.clone(replacements),
                    name,
                    typeDecl?.clone(replacements),
                    initializer?.clone(replacements),
                )
            is GlobalDecl.Struct -> GlobalDecl.Struct(name, members.clone(replacements))
            is GlobalDecl.TypeAlias -> GlobalDecl.TypeAlias(name, typeDecl.clone(replacements))
            is GlobalDecl.Variable ->
                GlobalDecl.Variable(
                    attributes.clone(replacements),
                    name,
                    addressSpace,
                    accessMode,
                    typeDecl?.clone(replacements),
                    initializer?.clone(replacements),
                )
            is LhsExpression.AddressOf -> LhsExpression.AddressOf(target.clone(replacements))
            is LhsExpression.Dereference -> LhsExpression.Dereference(target.clone(replacements))
            is LhsExpression.Identifier -> LhsExpression.Identifier(name)
            is LhsExpression.IndexLookup -> LhsExpression.IndexLookup(target.clone(replacements), index.clone(replacements))
            is LhsExpression.MemberLookup -> LhsExpression.MemberLookup(receiver.clone(replacements), memberName)
            is LhsExpression.Paren -> LhsExpression.Paren(target.clone(replacements))
            is ParameterDecl -> ParameterDecl(attributes.clone(replacements), name, typeDecl.clone(replacements))
            is Statement.Break -> Statement.Break()
            is Statement.ConstAssert -> Statement.ConstAssert(expression.clone(replacements))
            is Statement.Continue -> Statement.Continue()
            is Statement.Discard -> Statement.Discard()
            is Statement.Compound -> Statement.Compound(statements.clone(replacements))
            is Statement.If ->
                Statement.If(
                    attributes.clone(replacements),
                    condition.clone(replacements),
                    thenBranch.clone(replacements),
                    elseBranch?.clone(replacements),
                )
            is Statement.Empty -> Statement.Empty()
            is Statement.For ->
                Statement.For(
                    attributes.clone(replacements),
                    init?.clone(replacements),
                    condition?.clone(replacements),
                    update?.clone(replacements),
                    body.clone(replacements),
                )
            is Statement.Assignment -> Statement.Assignment(lhsExpression?.clone(replacements), assignmentOperator, rhs.clone(replacements))
            is Statement.Decrement -> Statement.Decrement(target.clone(replacements))
            is Statement.FunctionCall -> Statement.FunctionCall(callee, args.clone(replacements))
            is Statement.Increment -> Statement.Increment(target.clone(replacements))
            is Statement.Value -> Statement.Value(isConst, name, typeDecl?.clone(replacements), initializer.clone(replacements))
            is Statement.Variable ->
                Statement.Variable(
                    name,
                    addressSpace,
                    accessMode,
                    typeDecl?.clone(replacements),
                    initializer?.clone(replacements),
                )
            is Statement.Loop ->
                Statement.Loop(
                    attributesAtStart.clone(replacements),
                    attributesBeforeBody.clone(replacements),
                    body.clone(replacements),
                    continuingStatement?.clone(replacements),
                )
            is Statement.Return -> Statement.Return(expression?.clone(replacements))
            is Statement.Switch ->
                Statement.Switch(
                    attributesAtStart.clone(replacements),
                    expression.clone(replacements),
                    attributesBeforeBody.clone(replacements),
                    clauses.clone(replacements),
                )
            is Statement.While -> Statement.While(attributes.clone(replacements), condition.clone(replacements), body.clone(replacements))
            is AugmentedStatement.DeadCodeFragment ->
                AugmentedStatement.DeadCodeFragment(
                    statement.clone(
                        replacements,
                    ),
                )
            is StructMember -> StructMember(attributes.clone(replacements), name, typeDecl.clone(replacements))
            is SwitchClause -> SwitchClause(caseSelectors.map { it?.clone(replacements) }, compoundStatement.clone(replacements))
            is TranslationUnit -> TranslationUnit(directives.clone(replacements), globalDecls.clone(replacements))
            is TypeDecl.Array -> TypeDecl.Array(elementType.clone(replacements), elementCount?.clone(replacements))
            is TypeDecl.Atomic -> TypeDecl.Atomic(targetType.clone(replacements))
            is TypeDecl.Mat2x2 -> TypeDecl.Mat2x2(elementType.clone(replacements))
            is TypeDecl.Mat2x3 -> TypeDecl.Mat2x3(elementType.clone(replacements))
            is TypeDecl.Mat2x4 -> TypeDecl.Mat2x4(elementType.clone(replacements))
            is TypeDecl.Mat3x2 -> TypeDecl.Mat3x2(elementType.clone(replacements))
            is TypeDecl.Mat3x3 -> TypeDecl.Mat3x3(elementType.clone(replacements))
            is TypeDecl.Mat3x4 -> TypeDecl.Mat3x4(elementType.clone(replacements))
            is TypeDecl.Mat4x2 -> TypeDecl.Mat4x2(elementType.clone(replacements))
            is TypeDecl.Mat4x3 -> TypeDecl.Mat4x3(elementType.clone(replacements))
            is TypeDecl.Mat4x4 -> TypeDecl.Mat4x4(elementType.clone(replacements))
            is TypeDecl.NamedType -> TypeDecl.NamedType(name)
            is TypeDecl.Pointer -> TypeDecl.Pointer(addressSpace, pointeeType.clone(replacements), accessMode)
            is TypeDecl.SamplerComparison -> TypeDecl.SamplerComparison()
            is TypeDecl.SamplerRegular -> TypeDecl.SamplerRegular()
            is TypeDecl.Bool -> TypeDecl.Bool()
            is TypeDecl.F16 -> TypeDecl.F16()
            is TypeDecl.F32 -> TypeDecl.F32()
            is TypeDecl.I32 -> TypeDecl.I32()
            is TypeDecl.U32 -> TypeDecl.U32()
            is TypeDecl.TextureDepth2D -> TypeDecl.TextureDepth2D()
            is TypeDecl.TextureDepth2DArray -> TypeDecl.TextureDepth2DArray()
            is TypeDecl.TextureDepthCube -> TypeDecl.TextureDepthCube()
            is TypeDecl.TextureDepthCubeArray -> TypeDecl.TextureDepthCubeArray()
            is TypeDecl.TextureDepthMultisampled2D -> TypeDecl.TextureDepthMultisampled2D()
            is TypeDecl.TextureExternal -> TypeDecl.TextureExternal()
            is TypeDecl.TextureMultisampled2d -> TypeDecl.TextureMultisampled2d(sampledType.clone(replacements))
            is TypeDecl.TextureSampled1D -> TypeDecl.TextureSampled1D(sampledType.clone(replacements))
            is TypeDecl.TextureSampled2D -> TypeDecl.TextureSampled2D(sampledType.clone(replacements))
            is TypeDecl.TextureSampled2DArray -> TypeDecl.TextureSampled2DArray(sampledType.clone(replacements))
            is TypeDecl.TextureSampled3D -> TypeDecl.TextureSampled3D(sampledType.clone(replacements))
            is TypeDecl.TextureSampledCube -> TypeDecl.TextureSampledCube(sampledType.clone(replacements))
            is TypeDecl.TextureSampledCubeArray -> TypeDecl.TextureSampledCubeArray(sampledType.clone(replacements))
            is TypeDecl.TextureStorage1D -> TypeDecl.TextureStorage1D(format, accessMode)
            is TypeDecl.TextureStorage2D -> TypeDecl.TextureStorage2D(format, accessMode)
            is TypeDecl.TextureStorage2DArray -> TypeDecl.TextureStorage2DArray(format, accessMode)
            is TypeDecl.TextureStorage3D -> TypeDecl.TextureStorage3D(format, accessMode)
            is TypeDecl.Vec2 -> TypeDecl.Vec2(elementType.clone(replacements))
            is TypeDecl.Vec3 -> TypeDecl.Vec3(elementType.clone(replacements))
            is TypeDecl.Vec4 -> TypeDecl.Vec4(elementType.clone(replacements))
            is AugmentedExpression.AddZero ->
                AugmentedExpression.AddZero(
                    originalExpression.clone(replacements),
                    zeroExpression.clone(replacements),
                    zeroOnLeft,
                )
            is AugmentedExpression.DivOne ->
                AugmentedExpression.DivOne(
                    originalExpression.clone(replacements),
                    oneExpression.clone(replacements),
                )
            is AugmentedExpression.MulOne ->
                AugmentedExpression.MulOne(
                    originalExpression.clone(replacements),
                    oneExpression.clone(replacements),
                    oneOnLeft,
                )
            is AugmentedExpression.SubZero ->
                AugmentedExpression.SubZero(
                    originalExpression.clone(replacements),
                    zeroExpression.clone(replacements),
                )
            is AugmentedExpression.KnownValue ->
                AugmentedExpression.KnownValue(
                    knownValue.clone(replacements),
                    expression.clone(replacements),
                )
        }
    }
