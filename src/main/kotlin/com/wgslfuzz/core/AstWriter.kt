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

import java.io.PrintStream

private const val DEFAULT_INDENT = 4

/**
 * This class facilitates pretty-printing AST nodes (including a full translation unit) as text, to standard output by
 * default.
 */
class AstWriter(
    private val out: PrintStream = System.out,
    private val indentValue: Int = DEFAULT_INDENT,
    private val emitCommentary: Boolean = false,
    private val emitUniformCommentary: Boolean = false,
    private val shaderJob: ShaderJob? = null,
) {
    private var currentIndentLevel = 0

    fun emit() {
        require(shaderJob != null) { "ShaderJob cannot be null when calling emit on nothing" }
        emit(shaderJob.tu)
    }

    fun emit(node: AstNode) {
        when (node) {
            is TranslationUnit -> emitTranslationUnit(node)
            is Attribute -> emitAttribute(node)
            is ContinuingStatement -> emitContinuingStatement(node)
            is Directive -> emitDirective(node)
            is Expression -> emitExpression(node)
            is GlobalDecl -> emitGlobalDecl(node)
            is LhsExpression -> emitLhsExpression(node)
            is ParameterDecl -> emitParameterDecl(node)
            is Statement -> emitStatement(node)
            is StructMember -> emitStructMember(node)
            is SwitchClause -> emitSwitchClause(node)
            is TypeDecl -> emitTypeDecl(node)
        }
    }

    private fun emitIndent() =
        repeat((0..<currentIndentLevel).count()) {
            out.print(" ")
        }

    private fun increaseIndent() {
        currentIndentLevel += indentValue
    }

    private fun decreaseIndent() {
        currentIndentLevel -= indentValue
    }

    private fun emitAccessMode(accessMode: AccessMode) {
        out.print(
            when (accessMode) {
                AccessMode.READ -> "read"
                AccessMode.WRITE -> "write"
                AccessMode.READ_WRITE -> "read_write"
            },
        )
    }

    private fun emitTexelFormat(texelFormat: TexelFormat) {
        out.print(
            when (texelFormat) {
                TexelFormat.RGBA8UNORM -> "rgba8unorm"
                TexelFormat.RGBA8SNORM -> "rgba8snorm"
                TexelFormat.RGBA8UINT -> "rgba8uint"
                TexelFormat.RGBA8SINT -> "rgba8sint"
                TexelFormat.RGBA16UNORM -> "rgba16unorm"
                TexelFormat.RGBA16SNORM -> "rgba16snorm"
                TexelFormat.RGBA16UINT -> "rgba16uint"
                TexelFormat.RGBA16SINT -> "rgba16sint"
                TexelFormat.RGBA16FLOAT -> "rgba16float"
                TexelFormat.RG8UNORM -> "rg8unorm"
                TexelFormat.RG8SNORM -> "rg8snorm"
                TexelFormat.RG8UINT -> "rg8uint"
                TexelFormat.RG8SINT -> "rg8sint"
                TexelFormat.RG16UNORM -> "rg16unorm"
                TexelFormat.RG16SNORM -> "rg16snorm"
                TexelFormat.RG16UINT -> "rg16uint"
                TexelFormat.RG16SINT -> "rg16sint"
                TexelFormat.RG16FLOAT -> "rg16float"
                TexelFormat.R32UINT -> "r32uint"
                TexelFormat.R32SINT -> "r32sint"
                TexelFormat.R32FLOAT -> "r32float"
                TexelFormat.RG32UINT -> "rg32uint"
                TexelFormat.RG32SINT -> "rg32sint"
                TexelFormat.RG32FLOAT -> "rg32float"
                TexelFormat.RGBA32UINT -> "rgba32uint"
                TexelFormat.RGBA32SINT -> "rgba32sint"
                TexelFormat.RGBA32FLOAT -> "rgba32float"
                TexelFormat.BGRA8UNORM -> "bgra8unorm"
                TexelFormat.R8UNORM -> "r8unorm"
                TexelFormat.R8SNORM -> "r8snorm"
                TexelFormat.R8UINT -> "r8uint"
                TexelFormat.R8SINT -> "r8sint"
                TexelFormat.R16UNORM -> "r16unorm"
                TexelFormat.R16SNORM -> "r16snorm"
                TexelFormat.R16UINT -> "r16uint"
                TexelFormat.R16SINT -> "r16sint"
                TexelFormat.R16FLOAT -> "r16float"
                TexelFormat.RGB10A2UNORM -> "rgb10a2unorm"
                TexelFormat.RGB10A2UINT -> "rgb10a2uint"
                TexelFormat.RG11B10UFLOAT -> "rg11b10ufloat"
            },
        )
    }

    private fun emitAddressSpace(addressSpace: AddressSpace) {
        out.print(
            when (addressSpace) {
                AddressSpace.FUNCTION -> "function"
                AddressSpace.PRIVATE -> "private"
                AddressSpace.WORKGROUP -> "workgroup"
                AddressSpace.UNIFORM -> "uniform"
                AddressSpace.STORAGE -> "storage"
                AddressSpace.HANDLE -> "handle"
            },
        )
    }

    private fun emitAttributes(attributes: List<Attribute>) {
        attributes.forEach {
            emitAttribute(it)
        }
    }

    private fun emitAttribute(attribute: Attribute) {
        emitIndent()
        when (attribute) {
            is Attribute.Align -> {
                out.print("@align(")
                emitExpression(attribute.expression)
                out.print(")")
            }
            is Attribute.Binding -> {
                out.print("@binding(")
                emitExpression(attribute.expression)
                out.print(")")
            }
            is Attribute.BlendSrc -> {
                out.print("@blend_src(")
                emitExpression(attribute.expression)
                out.print(")")
            }
            is Attribute.Builtin -> {
                out.print("@builtin(")
                emitBuiltinValue(attribute.name)
                out.print(")")
            }
            is Attribute.Compute -> out.print("@compute")
            is Attribute.Const -> out.print("@const")
            is Attribute.Diagnostic -> {
                out.print("@diagnostic(")
                emitSeverityControl(attribute.severityControl)
                out.print(", ")
                emitDiagnosticRule(attribute.diagnosticRule)
                out.print(")")
            }
            is Attribute.Fragment -> out.print("@fragment")
            is Attribute.Group -> {
                out.print("@group(")
                emitExpression(attribute.expression)
                out.print(")")
            }
            is Attribute.Id -> {
                out.print("@id(")
                emitExpression(attribute.expression)
                out.print(")")
            }
            is Attribute.InputAttachmentIndex -> {
                out.print("@input_attachment_index(")
                emitExpression(attribute.expression)
                out.print(")")
            }
            is Attribute.Interpolate -> {
                out.print("@interpolate(")
                emitInterpolateType(attribute.interpolateType)
                attribute.interpolateSampling?.let {
                    out.print(", ")
                    emitInterpolateSampling(attribute.interpolateSampling)
                }
                out.print(")")
            }
            is Attribute.Invariant -> out.print("@invariant")
            is Attribute.Location -> {
                out.print("@location(")
                emitExpression(attribute.expression)
                out.print(")")
            }
            is Attribute.MustUse -> out.print("@must_use")
            is Attribute.Size -> {
                out.print("@size(")
                emitExpression(attribute.expression)
                out.print(")")
            }
            is Attribute.Vertex -> out.print("@vertex")
            is Attribute.WorkgroupSize -> {
                out.print("@workgroup_size(")
                emitExpression(attribute.sizeX)
                attribute.sizeY?.let {
                    out.print(", ")
                    emitExpression(it)
                }
                attribute.sizeZ?.let {
                    out.print(", ")
                    emitExpression(it)
                }
                out.print(")")
            }
        }
        out.print("\n")
    }

    private fun emitSeverityControl(severityControl: SeverityControl) =
        when (severityControl) {
            SeverityControl.ERROR -> out.print("error")
            SeverityControl.WARNING -> out.print("warning")
            SeverityControl.INFO -> out.print("info")
            SeverityControl.OFF -> out.print("off")
        }

    private fun emitDiagnosticRule(diagnosticRule: DiagnosticRule) =
        when (diagnosticRule) {
            DiagnosticRule.DERIVATIVE_UNIFORMITY -> out.print("derivative_uniformity")
            DiagnosticRule.SUBGROUP_UNIFORMITY -> out.print("subgroup_uniformity")
        }

    private fun emitInterpolateType(interpolateType: InterpolateType) =
        when (interpolateType) {
            InterpolateType.PERSPECTIVE -> out.print("perspective")
            InterpolateType.LINEAR -> out.print("linear")
            InterpolateType.FLAT -> out.print("flat")
        }

    private fun emitInterpolateSampling(interpolateSampling: InterpolateSampling) =
        when (interpolateSampling) {
            InterpolateSampling.CENTER -> out.print("center")
            InterpolateSampling.CENTROID -> out.print("centroid")
            InterpolateSampling.SAMPLE -> out.print("sample")
            InterpolateSampling.FIRST -> out.print("first")
            InterpolateSampling.EITHER -> out.print("either")
        }

    private fun emitBuiltinValue(builtinValue: BuiltinValue) =
        when (builtinValue) {
            BuiltinValue.VERTEX_INDEX -> out.print("vertex_index")
            BuiltinValue.INSTANCE_INDEX -> out.print("instance_index")
            BuiltinValue.CLIP_DISTANCES -> out.print("clip_distances")
            BuiltinValue.POSITION -> out.print("position")
            BuiltinValue.FRONT_FACING -> out.print("front_facing")
            BuiltinValue.FRAG_DEPTH -> out.print("frag_depth")
            BuiltinValue.SAMPLE_INDEX -> out.print("sample_index")
            BuiltinValue.SAMPLE_MASK -> out.print("sample_mask")
            BuiltinValue.LOCAL_INVOCATION_ID -> out.print("local_invocation_id")
            BuiltinValue.LOCAL_INVOCATION_INDEX -> out.print("local_invocation_index")
            BuiltinValue.GLOBAL_INVOCATION_ID -> out.print("global_invocation_id")
            BuiltinValue.WORKGROUP_ID -> out.print("workgroup_id")
            BuiltinValue.NUM_WORKGROUPS -> out.print("num_workgroups")
            BuiltinValue.SUBGROUP_INVOCATION_ID -> out.print("subgroup_invocation_id")
            BuiltinValue.SUBGROUP_SIZE -> out.print("subgroup_size")
            BuiltinValue.PRIMITIVE_INDEX -> out.print("primitive_index")
        }

    private fun emitAssignmentOperator(assignmentOperator: AssignmentOperator) {
        when (assignmentOperator) {
            AssignmentOperator.EQUAL -> out.print("=")
            AssignmentOperator.PLUS_EQUAL -> out.print("+=")
            AssignmentOperator.MINUS_EQUAL -> out.print("-=")
            AssignmentOperator.TIMES_EQUAL -> out.print("*=")
            AssignmentOperator.DIVIDE_EQUAL -> out.print("/=")
            AssignmentOperator.MODULO_EQUAL -> out.print("%=")
            AssignmentOperator.AND_EQUAL -> out.print("&=")
            AssignmentOperator.OR_EQUAL -> out.print("|=")
            AssignmentOperator.XOR_EQUAL -> out.print("^=")
            AssignmentOperator.SHIFT_LEFT_EQUAL -> out.print("<<=")
            AssignmentOperator.SHIFT_RIGHT_EQUAL -> out.print(">>=")
        }
    }

    private fun emitUnaryOperator(operator: UnaryOperator) {
        when (operator) {
            UnaryOperator.MINUS -> out.print("-")
            UnaryOperator.LOGICAL_NOT -> out.print("!")
            UnaryOperator.BINARY_NOT -> out.print("~")
            UnaryOperator.DEREFERENCE -> out.print("*")
            UnaryOperator.ADDRESS_OF -> out.print("&")
        }
    }

    private fun emitBinaryOperator(operator: BinaryOperator) {
        when (operator) {
            BinaryOperator.SHORT_CIRCUIT_OR -> out.print("||")
            BinaryOperator.SHORT_CIRCUIT_AND -> out.print("&&")
            BinaryOperator.BINARY_OR -> out.print("|")
            BinaryOperator.BINARY_AND -> out.print("&")
            BinaryOperator.BINARY_XOR -> out.print("^")
            BinaryOperator.LESS_THAN -> out.print("<")
            BinaryOperator.GREATER_THAN -> out.print(">")
            BinaryOperator.LESS_THAN_EQUAL -> out.print("<=")
            BinaryOperator.GREATER_THAN_EQUAL -> out.print(">=")
            BinaryOperator.EQUAL_EQUAL -> out.print("==")
            BinaryOperator.NOT_EQUAL -> out.print("!=")
            BinaryOperator.SHIFT_LEFT -> out.print("<<")
            BinaryOperator.SHIFT_RIGHT -> out.print(">>")
            BinaryOperator.PLUS -> out.print("+")
            BinaryOperator.MINUS -> out.print("-")
            BinaryOperator.TIMES -> out.print("*")
            BinaryOperator.DIVIDE -> out.print("/")
            BinaryOperator.MODULO -> out.print("%")
        }
    }

    private fun emitLhsExpression(lhsExpression: LhsExpression) {
        when (lhsExpression) {
            is LhsExpression.AddressOf -> {
                out.print("&")
                emitLhsExpression(lhsExpression.target)
            }
            is LhsExpression.IndexLookup -> {
                emitLhsExpression(lhsExpression.target)
                out.print("[")
                emitExpression(lhsExpression.index)
                out.print("]")
            }
            is LhsExpression.Identifier -> out.print(lhsExpression.name)
            is LhsExpression.MemberLookup -> {
                emitLhsExpression(lhsExpression.receiver)
                out.print(".${lhsExpression.memberName}")
            }
            is LhsExpression.Paren -> {
                out.print("(")
                emitLhsExpression(lhsExpression.target)
                out.print(")")
            }
            is LhsExpression.Dereference -> {
                out.print("*")
                emitLhsExpression(lhsExpression.target)
            }
        }
    }

    private fun emitExpression(expression: Expression) {
        when (expression) {
            is Expression.Binary -> {
                emitExpression(expression.lhs)
                out.print(" ")
                emitBinaryOperator(expression.operator)
                out.print(" ")
                emitExpression(expression.rhs)
            }
            is Expression.Unary -> {
                emitUnaryOperator(expression.operator)
                emitExpression(expression.target)
            }
            is Expression.BoolLiteral -> out.print(expression.text)
            is Expression.FloatLiteral -> out.print(expression.text)
            is Expression.IntLiteral -> out.print(expression.text)
            is Expression.Identifier -> out.print(expression.name)
            is Expression.Paren -> {
                out.print("(")
                emitExpression(expression.target)
                out.print(")")
            }
            is Expression.ValueConstructor -> {
                out.print(expression.constructorName)
                when (expression) {
                    is Expression.VectorValueConstructor -> {
                        expression.elementType?.let {
                            out.print("<")
                            emitTypeDecl(it)
                            out.print(">")
                        }
                    }

                    is Expression.MatrixValueConstructor -> {
                        expression.elementType?.let {
                            out.print("<")
                            emitTypeDecl(it)
                            out.print(">")
                        }
                    }

                    is Expression.ArrayValueConstructor -> {
                        expression.elementType?.let {
                            out.print("<")
                            emitTypeDecl(it)
                            expression.elementCount?.let { itInner ->
                                out.print(", ")
                                emitExpression(itInner)
                            }
                            out.print(">")
                        }
                    }
                    else -> {
                        // No action required: other value constructor expressions do not have template arguments.
                    }
                }
                out.print("(")
                expression.args.forEach {
                    emitExpression(it)
                    out.print(", ")
                }
                out.print(")")
            }
            is Expression.FunctionCall -> {
                out.print(expression.callee)
                expression.templateParameter?.let {
                    out.print("<")
                    emitTypeDecl(it)
                    out.print(">")
                }
                out.print("(")
                expression.args.forEach {
                    emitExpression(it)
                    out.print(", ")
                }
                out.print(")")
            }
            is Expression.IndexLookup -> {
                emitExpression(expression.target)
                out.print("[")
                emitExpression(expression.index)
                out.print("]")
            }
            is Expression.MemberLookup -> {
                emitExpression(expression.receiver)
                out.print(".${expression.memberName}")
            }
            is AugmentedExpression.ArbitraryExpression -> {
                out.print("(")
                if (emitCommentary) {
                    out.print("/* arbitrary expression: */ ")
                }
                emitExpression(expression.expression)
                out.print(")")
            }
            is AugmentedExpression.AddZero -> {
                if (expression.zeroOnLeft) {
                    out.print("(")
                    if (emitCommentary) {
                        out.print("/* add zero on left */ ")
                    }
                    emitExpression(expression.zeroExpression)
                    out.print(" + (")
                    emitExpression(expression.originalExpression)
                    out.print("))")
                } else {
                    out.print("(")
                    if (emitCommentary) {
                        out.print("/* add zero on right */ ")
                    }
                    out.print("(")
                    emitExpression(expression.originalExpression)
                    out.print(") + ")
                    emitExpression(expression.zeroExpression)
                    out.print(")")
                }
            }
            is AugmentedExpression.DivOne -> {
                out.print("(")
                if (emitCommentary) {
                    out.print("/* div by one */ ")
                }
                out.print("(")
                emitExpression(expression.originalExpression)
                out.print(") / ")
                emitExpression(expression.oneExpression)
                out.print(")")
            }
            is AugmentedExpression.MulOne -> {
                if (expression.oneOnLeft) {
                    out.print("(")
                    if (emitCommentary) {
                        out.print("/* mul by one on left */ ")
                    }
                    emitExpression(expression.oneExpression)
                    out.print(" * (")
                    emitExpression(expression.originalExpression)
                    out.print("))")
                } else {
                    out.print("(")
                    if (emitCommentary) {
                        out.print("/* mul by one on right */ ")
                    }
                    out.print("(")
                    emitExpression(expression.originalExpression)
                    out.print(") * ")
                    emitExpression(expression.oneExpression)
                    out.print(")")
                }
            }
            is AugmentedExpression.SubZero -> {
                out.print("(")
                if (emitCommentary) {
                    out.print("/* sub zero */ ")
                }
                out.print("(")
                emitExpression(expression.originalExpression)
                out.print(") - ")
                emitExpression(expression.zeroExpression)
                out.print(")")
            }
            is AugmentedExpression.KnownValue -> {
                out.print("(")
                if (emitCommentary) {
                    out.print("/* known value: ")
                    emitExpression(expression.knownValue)
                    out.print(" */ ")
                }
                emitExpression(expression.expression)
                out.print(")")
            }
        }
    }

    private fun emitTypeDecl(typeDecl: TypeDecl) {
        when (typeDecl) {
            is TypeDecl.ScalarTypeDecl -> out.print(typeDecl.name)
            is TypeDecl.MatrixTypeDecl -> {
                out.print(typeDecl.name)
                out.print("<")
                emitTypeDecl(typeDecl.elementType)
                out.print(">")
            }
            is TypeDecl.VectorTypeDecl -> {
                out.print(typeDecl.name)
                out.print("<")
                emitTypeDecl(typeDecl.elementType)
                out.print(">")
            }
            is TypeDecl.NamedType -> {
                out.print(typeDecl.name)
            }
            is TypeDecl.Array -> {
                out.print("array")
                out.print("<")
                emitTypeDecl(typeDecl.elementType)
                typeDecl.elementCount?.let {
                    out.print(", ")
                    emitExpression(it)
                }
                out.print(">")
            }
            is TypeDecl.Pointer -> {
                out.print("ptr<")
                emitAddressSpace(typeDecl.addressSpace)
                out.print(", ")
                emitTypeDecl(typeDecl.pointeeType)
                typeDecl.accessMode?.let {
                    out.print(", ")
                    emitAccessMode(it)
                }
                out.print(">")
            }

            is TypeDecl.Atomic -> {
                out.print("atomic<")
                emitTypeDecl(typeDecl.targetType)
                out.print(">")
            }
            is TypeDecl.SamplerComparison -> out.print("sampler_comparison")
            is TypeDecl.SamplerRegular -> out.print("sampler")
            is TypeDecl.TextureDepth2D -> out.print("texture_depth_2d")
            is TypeDecl.TextureDepth2DArray -> out.print("texture_depth_2d_array")
            is TypeDecl.TextureDepthCube -> out.print("texture_depth_cube")
            is TypeDecl.TextureDepthCubeArray -> out.print("texture_depth_cube_array")
            is TypeDecl.TextureDepthMultisampled2D -> out.print("texture_depth_multisampled_2d")
            is TypeDecl.TextureExternal -> out.print("texture_external")
            is TypeDecl.TextureMultisampled2d -> {
                out.print("texture_multisampled_2d<")
                emitTypeDecl(typeDecl.sampledType)
                out.print(">")
            }
            is TypeDecl.TextureSampled1D -> {
                out.print("texture_1d<")
                emitTypeDecl(typeDecl.sampledType)
                out.print(">")
            }
            is TypeDecl.TextureSampled2D -> {
                out.print("texture_2d<")
                emitTypeDecl(typeDecl.sampledType)
                out.print(">")
            }
            is TypeDecl.TextureSampled2DArray -> {
                out.print("texture_2d_array<")
                emitTypeDecl(typeDecl.sampledType)
                out.print(">")
            }
            is TypeDecl.TextureSampled3D -> {
                out.print("texture_3d<")
                emitTypeDecl(typeDecl.sampledType)
                out.print(">")
            }
            is TypeDecl.TextureSampledCube -> {
                out.print("texture_cube<")
                emitTypeDecl(typeDecl.sampledType)
                out.print(">")
            }
            is TypeDecl.TextureSampledCubeArray -> {
                out.print("texture_cube_array<")
                emitTypeDecl(typeDecl.sampledType)
                out.print(">")
            }
            is TypeDecl.TextureStorage1D -> {
                out.print("texture_storage_1d<")
                emitTexelFormat(typeDecl.format)
                out.print(", ")
                emitAccessMode(typeDecl.accessMode)
                out.print(">")
            }
            is TypeDecl.TextureStorage2D -> {
                out.print("texture_storage_2d<")
                emitTexelFormat(typeDecl.format)
                out.print(", ")
                emitAccessMode(typeDecl.accessMode)
                out.print(">")
            }
            is TypeDecl.TextureStorage2DArray -> {
                out.print("texture_storage_2d_array<")
                emitTexelFormat(typeDecl.format)
                out.print(", ")
                emitAccessMode(typeDecl.accessMode)
                out.print(">")
            }
            is TypeDecl.TextureStorage3D -> {
                out.print("texture_storage_3d<")
                emitTexelFormat(typeDecl.format)
                out.print(", ")
                emitAccessMode(typeDecl.accessMode)
                out.print(">")
            }
        }
    }

    private fun emitStatementAssignment(
        assignment: Statement.Assignment,
        inForLoopHeader: Boolean,
    ) {
        with(assignment) {
            if (!inForLoopHeader) {
                emitIndent()
            }
            lhsExpression?.let {
                emitLhsExpression(it)
            } ?: run {
                out.print("_")
            }
            out.print(" ")
            emitAssignmentOperator(assignmentOperator)
            out.print(" ")
            emitExpression(rhs)
            if (!inForLoopHeader) {
                out.print(";\n")
            }
        }
    }

    private fun emitStatementCompound(compound: Statement.Compound) {
        with(compound) {
            emitIndent()
            out.print("{\n")
            increaseIndent()
            if (emitCommentary) {
                when (compound.metadata) {
                    is AugmentedMetadata.ControlFlowWrapperMetaData -> {
                        emitIndent()
                        out.print("/* wrapped original statements: */\n")
                    }

                    is AugmentedMetadata.ArbitraryCompoundMetaData -> {
                        emitIndent()
                        out.print("/* arbitrary compound: */\n")
                    }

                    else -> {}
                }
            }
            statements.forEach(::emitStatement)
            decreaseIndent()
            emitIndent()
            out.print("}\n")
        }
    }

    private fun emitStatementConstAssert(constAssert: Statement.ConstAssert) {
        with(constAssert) {
            out.print("const_assert ")
            emitExpression(expression)
            out.print(";\n")
        }
    }

    private fun emitStatementIncrement(
        increment: Statement.Increment,
        inForLoopHeader: Boolean,
    ) {
        with(increment) {
            if (!inForLoopHeader) {
                emitIndent()
            }
            emitLhsExpression(target)
            out.print("++")
            if (!inForLoopHeader) {
                out.print(";\n")
            }
        }
    }

    private fun emitStatementDecrement(
        decrement: Statement.Decrement,
        inForLoopHeader: Boolean,
    ) {
        with(decrement) {
            if (!inForLoopHeader) {
                emitIndent()
            }
            emitLhsExpression(target)
            out.print("--")
            if (!inForLoopHeader) {
                out.print(";\n")
            }
        }
    }

    private fun emitStatementFor(statementFor: Statement.For) {
        with(statementFor) {
            emitAttributes(attributes)
            emitIndent()
            out.print("for (")
            init?.let { emitStatement(it, true) }
            out.print("; ")
            condition?.let(::emitExpression)
            out.print("; ")
            update?.let { emitStatement(it, true) }
            out.print(")\n")
            emitStatementCompound(body)
        }
    }

    private fun emitStatementFunctionCall(
        functionCall: Statement.FunctionCall,
        inForLoopHeader: Boolean,
    ) {
        with(functionCall) {
            if (!inForLoopHeader) {
                emitIndent()
            }
            out.print(callee)
            out.print("(")
            args.forEach {
                emitExpression(it)
                out.print(", ")
            }
            out.print(")")
            if (!inForLoopHeader) {
                out.print(";\n")
            }
        }
    }

    private fun emitStatementIf(statementIf: Statement.If) {
        with(statementIf) {
            emitAttributes(attributes)
            emitIndent()
            out.print("if ")
            emitExpression(condition)
            out.print("\n")
            emitStatementCompound(thenBranch)
            elseBranch?.let {
                emitIndent()
                out.print("else\n")
                emitStatement(it)
            }
        }
    }

    private fun emitStatementLoop(loop: Statement.Loop) {
        with(loop) {
            emitAttributes(attributesAtStart)
            emitIndent()
            out.print("loop\n")
            emitAttributes(attributesBeforeBody)
            emitIndent()
            out.print("{\n")
            increaseIndent()
            body.statements.forEach(::emitStatement)
            continuingStatement?.let(::emitContinuingStatement)
            decreaseIndent()
            emitIndent()
            out.print("}\n")
        }
    }

    private fun emitStatementReturn(statementReturn: Statement.Return) {
        with(statementReturn) {
            emitIndent()
            out.print("return")
            expression?.let {
                out.print(" ")
                emitExpression(it)
            }
            out.print(";\n")
        }
    }

    private fun emitStatementSwitch(switch: Statement.Switch) {
        with(switch) {
            emitAttributes(attributesAtStart)
            emitIndent()
            out.print("switch ")
            emitExpression(expression)
            out.print("\n")
            emitAttributes(attributesBeforeBody)
            emitIndent()
            out.print("{\n")
            increaseIndent()
            clauses.forEach(::emitSwitchClause)
            decreaseIndent()
            emitIndent()
            out.print("}\n")
        }
    }

    private fun emitStatementValue(
        value: Statement.Value,
        inForLoopHeader: Boolean,
    ) {
        with(value) {
            if (!inForLoopHeader) {
                emitIndent()
            }
            out.print(
                if (isConst) {
                    "const"
                } else {
                    "let"
                },
            )
            out.print(" $name")
            typeDecl?.let {
                out.print(" : ")
                emitTypeDecl(it)
            }
            out.print(" = ")
            emitExpression(initializer)
            if (!inForLoopHeader) {
                out.print(";\n")
            }
        }
    }

    private fun emitStatementVariable(
        variable: Statement.Variable,
        inForLoopHeader: Boolean,
    ) {
        with(variable) {
            if (!inForLoopHeader) {
                emitIndent()
            }
            emitVariableDeclaration(addressSpace, accessMode, name, typeDecl, initializer)
            if (!inForLoopHeader) {
                out.print(";\n")
            }
        }
    }

    private fun emitStatementWhile(statementWhile: Statement.While) {
        with(statementWhile) {
            emitAttributes(attributes)
            emitIndent()
            out.print("while ")
            emitExpression(condition)
            out.print("\n")
            emitStatement(body)
        }
    }

    private fun emitMetamorphicStatementDeadCodeFragment(deadCodeFragment: AugmentedStatement.DeadCodeFragment) {
        if (emitCommentary) {
            emitIndent()
            out.print("/* dead code fragment: */\n")
        }
        emitStatement(deadCodeFragment.statement)
    }

    private fun emitMetamorphicStatementControlFlowWrapped(statement: AugmentedStatement.ControlFlowWrapper) {
        if (emitCommentary) {
            emitIndent()
            out.print("/* control flow wrapped: */\n")
        }
        emitStatement(statement.statement)
    }

    private fun emitMetamorphicStatementControlFlowWrapReturn(statement: AugmentedStatement.ControlFlowWrapReturn) {
        if (emitCommentary) {
            emitIndent()
            out.print("/* control flow wrap return: */\n")
        }
        emitStatement(statement.statement)
    }

    private fun emitMetamorphicArbitraryStatement(statement: AugmentedStatement.ArbitraryStatement) {
        if (emitCommentary) {
            emitIndent()
            out.print("/* arbitrary statement */\n")
        }
        emitStatement(statement.statement)
    }

    private fun emitMetamorphicArbitraryElseBranch(statement: AugmentedStatement.ArbitraryElseBranch) {
        if (emitCommentary) {
            emitIndent()
            out.print("/* arbitrary else branch: */\n")
        }
        emitStatement(statement.statement)
    }

    private fun emitStatement(
        statement: Statement,
        inForLoopHeader: Boolean = false,
    ) {
        when (statement) {
            is Statement.Assignment -> emitStatementAssignment(statement, inForLoopHeader)
            is Statement.Break -> {
                emitIndent()
                out.print("break;\n")
            }
            is Statement.Compound -> emitStatementCompound(statement)
            is Statement.ConstAssert -> emitStatementConstAssert(statement)
            is Statement.Continue -> {
                emitIndent()
                out.print("continue;\n")
            }
            is Statement.Decrement -> emitStatementDecrement(statement, inForLoopHeader)
            is Statement.Discard -> {
                emitIndent()
                out.print("discard;\n")
            }
            is Statement.Empty -> {
                emitIndent()
                out.print(";\n")
            }
            is Statement.For -> emitStatementFor(statement)
            is Statement.FunctionCall -> emitStatementFunctionCall(statement, inForLoopHeader)
            is Statement.If -> emitStatementIf(statement)
            is Statement.Increment -> emitStatementIncrement(statement, inForLoopHeader)
            is Statement.Loop -> emitStatementLoop(statement)
            is Statement.Return -> emitStatementReturn(statement)
            is Statement.Switch -> emitStatementSwitch(statement)
            is Statement.Value -> emitStatementValue(statement, inForLoopHeader)
            is Statement.Variable -> emitStatementVariable(statement, inForLoopHeader)
            is Statement.While -> emitStatementWhile(statement)
            is AugmentedStatement.DeadCodeFragment -> emitMetamorphicStatementDeadCodeFragment(statement)
            is AugmentedStatement.ControlFlowWrapper -> emitMetamorphicStatementControlFlowWrapped(statement)
            is AugmentedStatement.ControlFlowWrapReturn -> emitMetamorphicStatementControlFlowWrapReturn(statement)
            is AugmentedStatement.ArbitraryElseBranch -> emitMetamorphicArbitraryElseBranch(statement)
            is AugmentedStatement.ArbitraryStatement -> emitMetamorphicArbitraryStatement(statement)
        }
    }

    private fun emitContinuingStatement(continuingStatement: ContinuingStatement) {
        with(continuingStatement) {
            emitIndent()
            out.print("continuing\n")
            emitAttributes(attributes)
            emitIndent()
            out.print("{\n")
            increaseIndent()
            statements.statements.forEach(::emitStatement)
            breakIfExpr?.let { breakIfExpr ->
                emitIndent()
                out.print("break if ")
                emitExpression(breakIfExpr)
                out.print(";\n")
            }
            decreaseIndent()
            emitIndent()
            out.print("}\n")
        }
    }

    private fun emitSwitchClause(switchClause: SwitchClause) {
        emitIndent()
        if (switchClause.caseSelectors == listOf(null)) {
            out.print("default")
        } else {
            out.print("case ")
            switchClause.caseSelectors.forEach { expression ->
                expression?.let { emitExpression(expression) } ?: run {
                    out.print("default")
                }
                out.print(", ")
            }
        }
        out.print("\n")
        emitStatementCompound(switchClause.compoundStatement)
    }

    private fun emitGlobalDeclStruct(struct: GlobalDecl.Struct) {
        with(struct) {
            out.print("struct $name {\n")
            increaseIndent()
            members.forEach(::emitStructMember)
            decreaseIndent()
            out.print("}\n")
        }
    }

    private fun emitGlobalDeclConstant(constant: GlobalDecl.Constant) {
        with(constant) {
            out.print("const $name ")
            typeDecl?.let {
                out.print(": ")
                emitTypeDecl(it)
            }
            out.print(" = ")
            emitExpression(initializer)
            out.print(";\n")
        }
    }

    private fun emitGlobalDeclOverride(override: GlobalDecl.Override) {
        with(override) {
            emitAttributes(attributes)
            out.print("override $name ")
            typeDecl?.let {
                out.print(": ")
                emitTypeDecl(it)
            }
            initializer?.let {
                out.print(" = ")
                emitExpression(it)
            }
            out.print(";\n")
        }
    }

    private fun emitGlobalDeclVariable(variable: GlobalDecl.Variable) {
        with(variable) {
            if (emitUniformCommentary && addressSpace == AddressSpace.UNIFORM) {
                emitUniformCommentary(variable)
            }
            emitAttributes(attributes)
            emitVariableDeclaration(addressSpace, accessMode, name, typeDecl, initializer)
            out.print(";\n")
        }
    }

    private fun emitUniformCommentary(uniformVariable: GlobalDecl.Variable) {
        // Cannot emit uniform commentary without shader job as information on uniform values are required
        require(
            shaderJob != null,
        ) { "shaderJob is null and so cannot emit uniform commentary without necessary information from shader job" }
        val group =
            (
                uniformVariable.attributes
                    .filterIsInstance<Attribute.Group>()
                    .first()
                    .expression as Expression.IntLiteral
            ).text.toInt()
        val binding =
            (
                uniformVariable.attributes
                    .filterIsInstance<Attribute.Binding>()
                    .first()
                    .expression as Expression.IntLiteral
            ).text.toInt()
        out.print("// Uniform value: ")
        emit(shaderJob.pipelineState.getUniformValue(group, binding))
        out.println()
    }

    private fun emitGlobalDeclFunction(function: GlobalDecl.Function) {
        with(function) {
            emitAttributes(attributes)
            out.print("fn $name(")
            if (parameters.isNotEmpty()) {
                out.print("\n")
                increaseIndent()
                parameters.forEach(::emitParameterDecl)
                decreaseIndent()
            }
            out.print(")")
            returnType?.let {
                out.print(" ->")
                if (attributes.isNotEmpty()) {
                    increaseIndent()
                    out.print("\n")
                    emitIndent()
                    emitAttributes(returnAttributes)
                    emitIndent()
                    decreaseIndent()
                } else {
                    out.print(" ")
                }
                emitTypeDecl(returnType)
            }
            out.print("\n")
            emitStatementCompound(body)
        }
    }

    private fun emitGlobalDeclTypeAlias(typeAlias: GlobalDecl.TypeAlias) {
        out.print("alias ${typeAlias.name} = ")
        emitTypeDecl(typeAlias.typeDecl)
        out.print(";\n")
    }

    private fun emitGlobalDeclConstAssert(constAssert: GlobalDecl.ConstAssert) {
        out.print("const_assert ")
        emitExpression(constAssert.expression)
        out.print(";\n")
    }

    private fun emitGlobalDecl(decl: GlobalDecl) {
        when (decl) {
            is GlobalDecl.Constant -> emitGlobalDeclConstant(decl)
            is GlobalDecl.Override -> emitGlobalDeclOverride(decl)
            is GlobalDecl.Variable -> emitGlobalDeclVariable(decl)
            is GlobalDecl.Function -> emitGlobalDeclFunction(decl)
            is GlobalDecl.Struct -> emitGlobalDeclStruct(decl)
            is GlobalDecl.TypeAlias -> emitGlobalDeclTypeAlias(decl)
            is GlobalDecl.ConstAssert -> emitGlobalDeclConstAssert(decl)
            is GlobalDecl.Empty -> {
                out.print(";\n")
            }
        }
    }

    private fun emitParameterDecl(parameterDecl: ParameterDecl) {
        emitAttributes(parameterDecl.attributes)
        emitIndent()
        out.print("${parameterDecl.name} : ")
        emitTypeDecl(parameterDecl.typeDecl)
        out.print(",\n")
    }

    private fun emitDirective(directive: Directive) {
        out.print("${directive.text}\n")
    }

    private fun emitVariableDeclaration(
        addressSpace: AddressSpace?,
        accessMode: AccessMode?,
        name: String,
        type: TypeDecl?,
        initializer: Expression?,
    ) {
        out.print("var")
        addressSpace?.let {
            out.print("<")
            emitAddressSpace(it)
            accessMode?.let { itInner ->
                out.print(", ")
                emitAccessMode(itInner)
            }
            out.print(">")
        }
        out.print(" $name")
        type?.let {
            out.print(" : ")
            emitTypeDecl(it)
        }
        initializer?.let {
            out.print(" = ")
            emitExpression(it)
        }
    }

    private fun emitStructMember(member: StructMember) {
        emitAttributes(member.attributes)
        emitIndent()
        out.print("${member.name} : ")
        emitTypeDecl(member.typeDecl)
        out.print(",\n")
    }

    private fun emitTranslationUnit(tu: TranslationUnit) {
        tu.directives.forEach {
            emitDirective(it)
            out.print("\n")
        }
        tu.globalDecls.forEachIndexed { index, decl ->
            emitGlobalDecl(decl)
            if (index < tu.globalDecls.size - 1) {
                out.print("\n")
            }
        }
    }
}
