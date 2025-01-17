package com.wgslfuzz

import com.wgslfuzz.WGSLParser.AttributeContext
import com.wgslfuzz.WGSLParser.Postfix_expressionContext
import com.wgslfuzz.WGSLParser.Translation_unitContext
import com.wgslfuzz.WGSLParser.Type_decl_template_argContext
import org.antlr.v4.runtime.ANTLRErrorListener
import org.antlr.v4.runtime.BailErrorStrategy
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.atn.ATNConfigSet
import org.antlr.v4.runtime.atn.LexerATNSimulator
import org.antlr.v4.runtime.atn.ParserATNSimulator
import org.antlr.v4.runtime.atn.PredictionContextCache
import org.antlr.v4.runtime.atn.PredictionMode
import org.antlr.v4.runtime.dfa.DFA
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.misc.ParseCancellationException
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.ParseTreeListener
import org.antlr.v4.runtime.tree.TerminalNode
import java.io.File
import java.io.InputStream
import java.util.BitSet

private val ParserRuleContext.fullText: String
    get() =
        if (start == null || stop == null || start.startIndex < 0 || stop.stopIndex < 0) {
            text
        } else {
            start.inputStream.getText(Interval.of(start.startIndex, stop.stopIndex))
        }

class LoggingParseErrorListener : ANTLRErrorListener {
    val loggedMessages: String
        get() = _loggedMessages.toString()

    private val _loggedMessages = StringBuilder()

    override fun syntaxError(
        recognizer: Recognizer<*, *>?,
        offendingSymbol: Any?,
        line: Int,
        charPositionInLine: Int,
        msg: String?,
        e: RecognitionException?,
    ) {
        _loggedMessages.append("[$line, $charPositionInLine]: $msg\n")
    }

    override fun reportAmbiguity(
        parser: Parser?,
        dfa: DFA?,
        startIndex: Int,
        stopIndex: Int,
        exact: Boolean,
        ambigAlts: BitSet?,
        configs: ATNConfigSet?,
    ) {
    }

    override fun reportAttemptingFullContext(
        parser: Parser?,
        dfa: DFA?,
        startIndex: Int,
        stopIndex: Int,
        conflictingAlts: BitSet?,
        configs: ATNConfigSet?,
    ) {
    }

    override fun reportContextSensitivity(
        parser: Parser?,
        dfa: DFA?,
        startIndex: Int,
        stopIndex: Int,
        prediction: Int,
        configs: ATNConfigSet?,
    ) {
    }
}

private class TimeoutParseTreeListener(
    private val timeToStop: Long,
) : ParseTreeListener {
    private fun checkTime() {
        if (System.currentTimeMillis() > timeToStop) {
            throw RuntimeException("Parsing timed out.")
        }
    }

    override fun visitTerminal(terminalNode: TerminalNode) = checkTime()

    override fun visitErrorNode(errorNode: ErrorNode) = checkTime()

    override fun enterEveryRule(parserRuleContext: ParserRuleContext) = checkTime()

    override fun exitEveryRule(parserRuleContext: ParserRuleContext) = checkTime()
}

private class AstBuilder(
    private val moduleScopeNames: ModuleScopeNames,
) : WGSLBaseVisitor<Any>() {
    override fun visitTranslation_unit(ctx: Translation_unitContext): TranslationUnit =
        TranslationUnit(
            directives =
                ctx
                    .global_directive()
                    .map {
                        Directive(it.fullText)
                    },
            globalDecls =
                ctx
                    .global_decl()
                    .map(::visitGlobal_decl),
        )

    override fun visitEmpty_global_decl(ctx: WGSLParser.Empty_global_declContext): GlobalDecl.Empty = GlobalDecl.Empty()

    override fun visitConst_assert_decl(ctx: WGSLParser.Const_assert_declContext): GlobalDecl.ConstAssert =
        GlobalDecl.ConstAssert(
            expression = visitExpression(ctx.const_assert_statement().expression()),
        )

    override fun visitGlobal_value_decl(ctx: WGSLParser.Global_value_declContext): GlobalDecl {
        if (ctx.CONST() != null) {
            return GlobalDecl.Constant(
                name = ctx.ident_with_optional_type().IDENT().text,
                type = ctx.ident_with_optional_type().type_decl()?.let(::visitType_decl),
                initializer = visitExpression(ctx.expression()),
            )
        }
        return GlobalDecl.Override(
            attributes = gatherAttributes(ctx.attribute()),
            name = ctx.ident_with_optional_type().IDENT().text,
            type = ctx.ident_with_optional_type().type_decl()?.let(::visitType_decl),
            initializer = ctx.expression()?.let(::visitExpression),
        )
    }

    override fun visitGlobal_variable_decl(ctx: WGSLParser.Global_variable_declContext): GlobalDecl.Variable =
        GlobalDecl.Variable(
            attributes = gatherAttributes(ctx.attribute()),
            name =
                ctx
                    .ident_with_optional_type()
                    .IDENT()
                    .text,
            addressSpace =
                ctx
                    .variable_qualifier()
                    ?.address_space
                    ?.text
                    ?.let(::handleAddressSpace),
            accessMode =
                ctx
                    .variable_qualifier()
                    ?.access_mode
                    ?.text
                    ?.let(::handleAccessMode),
            type =
                ctx
                    .ident_with_optional_type()
                    .type_decl()
                    ?.let(::visitType_decl),
            initializer = ctx.expression()?.let(::visitExpression),
        )

    override fun visitFunction_decl(ctx: WGSLParser.Function_declContext): GlobalDecl.Function =
        GlobalDecl.Function(
            attributes = gatherAttributes(ctx.attribute()),
            name = ctx.function_header().IDENT().text,
            parameters =
                ctx
                    .function_header()
                    .param_list()
                    ?.param()
                    ?.map {
                        ParameterDecl(
                            attributes = gatherAttributes(it.attribute()),
                            name = it.IDENT().text,
                            typeDecl = visitType_decl(it.type_decl()),
                        )
                    } ?: emptyList(),
            returnType =
                ctx.function_header().type_decl()?.let(::visitType_decl),
            body =
                ctx
                    .compound_statement()
                    .statement()
                    .map(::visitStatement),
        )

    override fun visitGlobal_decl(ctx: WGSLParser.Global_declContext): GlobalDecl = super.visitGlobal_decl(ctx) as GlobalDecl

    override fun visitCompound_statement(ctx: WGSLParser.Compound_statementContext): Statement.Compound =
        Statement.Compound(
            statements =
                ctx
                    .statement()
                    .map(::visitStatement),
        )

    override fun visitReturn_statement(ctx: WGSLParser.Return_statementContext): Statement.Return =
        Statement.Return(
            ctx.expression()?.let(::visitExpression),
        )

    override fun visitIf_statement(ctx: WGSLParser.If_statementContext): Statement.If =
        Statement.If(
            attributes = gatherAttributes(ctx.attribute()),
            condition = visitExpression(ctx.expression()),
            thenBranch = visitCompound_statement(ctx.compound_statement()),
            elseBranch =
                ctx.else_statement()?.let {
                    if (it.if_statement() != null) {
                        visitIf_statement(it.if_statement())
                    } else {
                        visitCompound_statement(it.compound_statement())
                    }
                },
        )

    override fun visitSwitch_statement(ctx: WGSLParser.Switch_statementContext): Statement.Switch =
        Statement.Switch(
            attributesAtStart = gatherAttributes(ctx.attributes_at_start),
            expression = visitExpression(ctx.expression()),
            attributesBeforeBody = gatherAttributes(ctx.attributes_before_body),
            clauses =
                ctx
                    .switch_clause()
                    .map {
                        if (it.DEFAULT() != null) {
                            SwitchClause(
                                caseSelectors = CaseSelectors.DefaultAlone(),
                                compoundStatement = visitCompound_statement(it.compound_statement()),
                            )
                        } else {
                            SwitchClause(
                                caseSelectors =
                                    CaseSelectors.ExpressionsOrDefault(
                                        expressions =
                                            it
                                                .case_selectors()
                                                .expression_or_default()
                                                .map { expressionOrDefault ->
                                                    if (expressionOrDefault.DEFAULT() != null) {
                                                        // Null is used to represent occurrences of default in a sequence of selector expressions
                                                        null
                                                    } else {
                                                        visitExpression(expressionOrDefault.expression())
                                                    }
                                                },
                                    ),
                                compoundStatement = visitCompound_statement(it.compound_statement()),
                            )
                        }
                    },
        )

    override fun visitLoop_statement(ctx: WGSLParser.Loop_statementContext): Statement.Loop =
        Statement.Loop(
            attributesAtStart = gatherAttributes(ctx.attributes_at_start),
            attributesBeforeBody = gatherAttributes(ctx.attributes_before_body),
            body = ctx.statement().map(::visitStatement),
            continuingStatement =
                ctx.continuing_statement()?.let {
                    ContinuingStatement(
                        attributes = gatherAttributes(it.continuing_compound_statement().attribute()),
                        statements =
                            it
                                .continuing_compound_statement()
                                .statement()
                                .map { statement ->
                                    visitStatement(statement)
                                },
                        breakIfExpr =
                            it.continuing_compound_statement().break_if_statement()?.let { breakIfStatement ->
                                visitExpression(breakIfStatement.expression())
                            },
                    )
                },
        )

    override fun visitFor_statement(ctx: WGSLParser.For_statementContext): Statement.For =
        Statement.For(
            attributes = gatherAttributes(ctx.attribute()),
            init =
                ctx.for_header().for_init()?.let {
                    if (it.variable_statement() != null) {
                        visitVariable_statement(it.variable_statement())
                    } else if (it.value_statement() != null) {
                        visitValue_statement(it.value_statement())
                    } else if (it.increment_statement() != null) {
                        visitIncrement_statement(it.increment_statement())
                    } else if (it.decrement_statement() != null) {
                        visitDecrement_statement(it.decrement_statement())
                    } else if (it.assignment_statement() != null) {
                        visitAssignment_statement(it.assignment_statement())
                    } else if (it.func_call_statement() != null) {
                        visitFunc_call_statement(it.func_call_statement())
                    } else {
                        throw UnsupportedOperationException("Unsupported 'for' init statement.")
                    }
                },
            condition = ctx.for_header().expression()?.let(::visitExpression),
            update =
                ctx.for_header().for_update()?.let {
                    if (it.increment_statement() != null) {
                        visitIncrement_statement(it.increment_statement())
                    } else if (it.decrement_statement() != null) {
                        visitDecrement_statement(it.decrement_statement())
                    } else if (it.assignment_statement() != null) {
                        visitAssignment_statement(it.assignment_statement())
                    } else if (it.func_call_statement() != null) {
                        visitFunc_call_statement(it.func_call_statement())
                    } else {
                        throw UnsupportedOperationException("Unsupported 'for' update statement.")
                    }
                },
            body =
                ctx
                    .compound_statement()
                    .statement()
                    .map(::visitStatement),
        )

    override fun visitWhile_statement(ctx: WGSLParser.While_statementContext): Statement.While =
        Statement.While(
            attributes = gatherAttributes(ctx.attribute()),
            condition = visitExpression(ctx.expression()),
            body = visitCompound_statement(ctx.compound_statement()),
        )

    override fun visitFunc_call_statement(ctx: WGSLParser.Func_call_statementContext): Statement.FunctionCall =
        Statement.FunctionCall(
            callee = ctx.IDENT().text,
            args =
                ctx
                    .argument_expression_list()
                    .expression()
                    .map(::visitExpression),
        )

    override fun visitVariable_statement(ctx: WGSLParser.Variable_statementContext): Statement.Variable =
        Statement.Variable(
            name =
                ctx
                    .ident_with_optional_type()
                    .IDENT()
                    .text,
            addressSpace =
                ctx
                    .variable_qualifier()
                    ?.address_space
                    ?.text
                    ?.let(::handleAddressSpace),
            accessMode =
                ctx
                    .variable_qualifier()
                    ?.access_mode
                    ?.text
                    ?.let(::handleAccessMode),
            type =
                ctx
                    .ident_with_optional_type()
                    .type_decl()
                    ?.let(::visitType_decl),
            initializer = ctx.expression()?.let(::visitExpression),
        )

    override fun visitValue_statement(ctx: WGSLParser.Value_statementContext): Statement.Value =
        Statement.Value(
            isConst = ctx.CONST() != null,
            name = ctx.ident_with_optional_type().IDENT().text,
            type =
                ctx
                    .ident_with_optional_type()
                    .type_decl()
                    ?.let(::visitType_decl),
            initializer = visitExpression(ctx.expression()),
        )

    override fun visitBreak_statement(ctx: WGSLParser.Break_statementContext): Statement.Break = Statement.Break()

    override fun visitContinue_statement(ctx: WGSLParser.Continue_statementContext): Statement.Continue = Statement.Continue()

    override fun visitStatement(ctx: WGSLParser.StatementContext): Statement = super.visitStatement(ctx) as Statement

    override fun visitLhs_expression(ctx: WGSLParser.Lhs_expressionContext): LhsExpression {
        val target = visitCore_lhs_expression(ctx.core_lhs_expression())
        val targetWithPostfix = processLhsExpressionPostfix(target, ctx.postfix_expression())
        return if (ctx.STAR().isNotEmpty()) {
            LhsExpression.Dereference(targetWithPostfix)
        } else if (ctx.AND().isNotEmpty()) {
            LhsExpression.AddressOf(targetWithPostfix)
        } else {
            targetWithPostfix
        }
    }

    override fun visitCore_lhs_expression(ctx: WGSLParser.Core_lhs_expressionContext): LhsExpression =
        if (ctx.IDENT() != null) {
            LhsExpression.Identifier(ctx.IDENT().text)
        } else if (ctx.PAREN_LEFT() != null) {
            LhsExpression.Paren(visitLhs_expression(ctx.lhs_expression()))
        } else {
            throw UnsupportedOperationException("Unknown kind of LHS expression.")
        }

    private fun processLhsExpressionPostfix(
        target: LhsExpression,
        ctx: Postfix_expressionContext?,
    ): LhsExpression {
        if (ctx == null) {
            return target
        }
        if (ctx.IDENT() != null) {
            return processLhsExpressionPostfix(
                LhsExpression.MemberLookup(target, ctx.IDENT().text),
                ctx.postfix_expression(),
            )
        }
        if (ctx.BRACKET_LEFT() != null) {
            return processLhsExpressionPostfix(
                LhsExpression.IndexLookup(target, visitExpression(ctx.expression())),
                ctx.postfix_expression(),
            )
        }
        throw UnsupportedOperationException("Unknown postfix expression.")
    }

    override fun visitAssignment_statement(ctx: WGSLParser.Assignment_statementContext): Statement.Assignment {
        val lhs: LhsExpression? =
            ctx.lhs_expression()?.let {
                visitLhs_expression(ctx.lhs_expression())
            }
        val assignmentOperator =
            if (ctx.EQUAL() != null) {
                AssignmentOperator.EQUAL
            } else if (ctx.compound_assignment_operator().PLUS_EQUAL() != null) {
                AssignmentOperator.PLUS_EQUAL
            } else if (ctx.compound_assignment_operator().MINUS_EQUAL() != null) {
                AssignmentOperator.MINUS_EQUAL
            } else if (ctx.compound_assignment_operator().TIMES_EQUAL() != null) {
                AssignmentOperator.TIMES_EQUAL
            } else if (ctx.compound_assignment_operator().DIVISION_EQUAL() != null) {
                AssignmentOperator.DIVIDE_EQUAL
            } else if (ctx.compound_assignment_operator().MODULO_EQUAL() != null) {
                AssignmentOperator.MODULO_EQUAL
            } else if (ctx.compound_assignment_operator().AND_EQUAL() != null) {
                AssignmentOperator.AND_EQUAL
            } else if (ctx.compound_assignment_operator().OR_EQUAL() != null) {
                AssignmentOperator.OR_EQUAL
            } else if (ctx.compound_assignment_operator().XOR_EQUAL() != null) {
                AssignmentOperator.XOR_EQUAL
            } else if (ctx.compound_assignment_operator().SHIFT_LEFT_EQUAL() != null) {
                AssignmentOperator.SHIFT_LEFT_EQUAL
            } else if (ctx.compound_assignment_operator().SHIFT_RIGHT_EQUAL() != null) {
                AssignmentOperator.SHIFT_RIGHT_EQUAL
            } else {
                throw UnsupportedOperationException("Unknown assignment operator.")
            }
        val rhs = visitExpression(ctx.expression())
        return Statement.Assignment(lhsExpression = lhs, assignmentOperator = assignmentOperator, rhs = rhs)
    }

    override fun visitIncrement_statement(ctx: WGSLParser.Increment_statementContext): Statement.Increment =
        Statement.Increment(visitLhs_expression(ctx.lhs_expression()))

    override fun visitDecrement_statement(ctx: WGSLParser.Decrement_statementContext): Statement.Decrement =
        Statement.Decrement(visitLhs_expression(ctx.lhs_expression()))

    override fun visitDiscard_statement(ctx: WGSLParser.Discard_statementContext): Statement.Discard = Statement.Discard()

    override fun visitConst_assert_statement(ctx: WGSLParser.Const_assert_statementContext): Statement.ConstAssert =
        Statement.ConstAssert(visitExpression(ctx.expression()))

    override fun visitEmpty_statement(ctx: WGSLParser.Empty_statementContext): Statement.Empty = Statement.Empty()

    override fun visitStruct_decl(ctx: WGSLParser.Struct_declContext): GlobalDecl.Struct {
        val result =
            GlobalDecl.Struct(
                name = ctx.IDENT().text,
                members =
                    ctx
                        .struct_body_decl()
                        .struct_member()
                        .map {
                            StructMember(
                                attributes =
                                    gatherAttributes(it.attribute()),
                                name = it.IDENT().text,
                                type = visitType_decl(it.type_decl()),
                            )
                        },
            )
        return result
    }

    override fun visitType_alias_decl(ctx: WGSLParser.Type_alias_declContext): GlobalDecl.TypeAlias =
        GlobalDecl.TypeAlias(
            name = ctx.IDENT().text,
            type = visitType_decl(ctx.type_decl()),
        )

    override fun visitType_decl_without_ident(ctx: WGSLParser.Type_decl_without_identContext): TypeDecl {
        if (ctx.BOOL() != null) {
            return TypeDecl.Bool()
        }
        if (ctx.INT32() != null) {
            return TypeDecl.I32()
        }
        if (ctx.UINT32() != null) {
            return TypeDecl.U32()
        }
        if (ctx.FLOAT16() != null) {
            return TypeDecl.F16()
        }
        if (ctx.FLOAT32() != null) {
            return TypeDecl.F32()
        }
        if (ctx.vec_prefix() != null) {
            val elementType: TypeDecl =
                ctx.type_decl()?.let(::visitType_decl)
                    ?: throw UnsupportedOperationException("A vector type must specify an element type.")
            if (elementType !is TypeDecl.ScalarTypeDecl) {
                throw UnsupportedOperationException("Element type of vector must be scalar.")
            }
            val vecPrefix = ctx.vec_prefix()!!
            if (vecPrefix.VEC2() != null) {
                return TypeDecl.Vec2(elementType)
            }
            if (vecPrefix.VEC3() != null) {
                return TypeDecl.Vec3(elementType)
            }
            if (vecPrefix.VEC4() != null) {
                return TypeDecl.Vec4(elementType)
            }
            throw UnsupportedOperationException("Unknown vector type.")
        }
        if (ctx.mat_prefix() != null) {
            val elementType: TypeDecl =
                ctx.type_decl()?.let(::visitType_decl)
                    ?: throw UnsupportedOperationException("A matrix type must have an element type.")
            if (elementType !is TypeDecl.FloatTypeDecl) {
                throw UnsupportedOperationException("Element type of matrix must be float.")
            }
            val matPrefix = ctx.mat_prefix()!!
            if (matPrefix.MAT2X2() != null) {
                return TypeDecl.Mat2x2(elementType)
            }
            if (matPrefix.MAT2X3() != null) {
                return TypeDecl.Mat2x3(elementType)
            }
            if (matPrefix.MAT2X4() != null) {
                return TypeDecl.Mat2x4(elementType)
            }
            if (matPrefix.MAT3X2() != null) {
                return TypeDecl.Mat3x2(elementType)
            }
            if (matPrefix.MAT3X3() != null) {
                return TypeDecl.Mat3x3(elementType)
            }
            if (matPrefix.MAT3X4() != null) {
                return TypeDecl.Mat3x4(elementType)
            }
            if (matPrefix.MAT4X2() != null) {
                return TypeDecl.Mat4x2(elementType)
            }
            if (matPrefix.MAT4X3() != null) {
                return TypeDecl.Mat4x3(elementType)
            }
            if (matPrefix.MAT4X4() != null) {
                return TypeDecl.Mat4x4(elementType)
            }
            throw UnsupportedOperationException("Unknown matrix type.")
        }
        if (ctx.array_type_decl() != null) {
            with(ctx.array_type_decl()!!) {
                val elementType =
                    type_decl()?.let(::visitType_decl) ?: throw UnsupportedOperationException("An array type must have an element type.")
                return TypeDecl.Array(
                    elementType = elementType,
                    elementCount =
                        element_count_expression()?.let {
                            visitExpression(it.expression())
                        },
                )
            }
        }
        if (ctx.PTR() != null) {
            return TypeDecl.Pointer(
                addressSpace = handleAddressSpace(ctx.address_space.text),
                pointeeType = visitType_decl(ctx.type_decl()),
                accessMode = ctx.access_mode?.text?.let(::handleAccessMode),
            )
        }
        throw UnsupportedOperationException("Unknown type declaration")
    }

    override fun visitType_decl(ctx: WGSLParser.Type_declContext): TypeDecl =
        if (ctx.type_decl_without_ident() != null) {
            visitType_decl_without_ident(ctx.type_decl_without_ident())
        } else {
            when (ctx.IDENT().text) {
                "atomic" -> TypeDecl.Atomic(getSingleTypeDeclTemplateArg(ctx.type_decl_template_arg()))
                "sampler" -> {
                    if (ctx.type_decl_template_arg().isNotEmpty()) {
                        throw RuntimeException("sampler does not take template parameters.")
                    }
                    TypeDecl.SamplerRegular()
                }
                "sampler_comparison" -> {
                    if (ctx.type_decl_template_arg().isNotEmpty()) {
                        throw RuntimeException("sampler_comparison does not take template parameters.")
                    }
                    TypeDecl.SamplerComparison()
                }
                "texture_1d" -> TypeDecl.TextureSampled1D(getSingleTypeDeclTemplateArg(ctx.type_decl_template_arg()))
                "texture_2d" -> TypeDecl.TextureSampled2D(getSingleTypeDeclTemplateArg(ctx.type_decl_template_arg()))
                "texture_2d_array" -> TypeDecl.TextureSampled2DArray(getSingleTypeDeclTemplateArg(ctx.type_decl_template_arg()))
                "texture_3d" -> TypeDecl.TextureSampled3D(getSingleTypeDeclTemplateArg(ctx.type_decl_template_arg()))
                "texture_cube" -> TypeDecl.TextureSampledCube(getSingleTypeDeclTemplateArg(ctx.type_decl_template_arg()))
                "texture_cube_array" -> TypeDecl.TextureSampledCubeArray(getSingleTypeDeclTemplateArg(ctx.type_decl_template_arg()))
                "texture_multisampled_2d" -> TypeDecl.TextureMultisampled2d(getSingleTypeDeclTemplateArg(ctx.type_decl_template_arg()))
                "texture_depth_multisampled_2d" -> {
                    if (ctx.type_decl_template_arg().isNotEmpty()) {
                        throw RuntimeException("texture_depth_multisampled_2d does not take template parameters.")
                    }
                    TypeDecl.TextureDepthMultisampled2D()
                }
                "texture_external" -> {
                    if (ctx.type_decl_template_arg().isNotEmpty()) {
                        throw RuntimeException("texture_external does not take template parameters.")
                    }
                    TypeDecl.TextureExternal()
                }
                "texture_storage_1d" ->
                    TypeDecl.TextureStorage1D(
                        format = getTextureStorageFormatTemplateArg(ctx.type_decl_template_arg()),
                        accessMode = getTextureStorageAccessModeTemplateArg(ctx.type_decl_template_arg()),
                    )
                "texture_storage_2d" ->
                    TypeDecl.TextureStorage2D(
                        format = getTextureStorageFormatTemplateArg(ctx.type_decl_template_arg()),
                        accessMode = getTextureStorageAccessModeTemplateArg(ctx.type_decl_template_arg()),
                    )
                "texture_storage_2d_array" ->
                    TypeDecl.TextureStorage2DArray(
                        format = getTextureStorageFormatTemplateArg(ctx.type_decl_template_arg()),
                        accessMode = getTextureStorageAccessModeTemplateArg(ctx.type_decl_template_arg()),
                    )
                "texture_storage_3d" ->
                    TypeDecl.TextureStorage3D(
                        format = getTextureStorageFormatTemplateArg(ctx.type_decl_template_arg()),
                        accessMode = getTextureStorageAccessModeTemplateArg(ctx.type_decl_template_arg()),
                    )
                "texture_depth_2d" -> {
                    if (ctx.type_decl_template_arg().isNotEmpty()) {
                        throw RuntimeException("texture_depth_2d does not take template parameters.")
                    }
                    TypeDecl.TextureDepth2D()
                }
                "texture_depth_2d_array" -> {
                    if (ctx.type_decl_template_arg().isNotEmpty()) {
                        throw RuntimeException("texture_depth_2d_array does not take template parameters.")
                    }
                    TypeDecl.TextureDepth2DArray()
                }
                "texture_depth_cube" -> {
                    if (ctx.type_decl_template_arg().isNotEmpty()) {
                        throw RuntimeException("texture_depth_cube does not take template parameters.")
                    }
                    TypeDecl.TextureDepthCube()
                }
                "texture_depth_cube_array" -> {
                    if (ctx.type_decl_template_arg().isNotEmpty()) {
                        throw RuntimeException("texture_depth_cube_array does not take template parameters.")
                    }
                    TypeDecl.TextureDepthCubeArray()
                }
                else -> {
                    if (ctx.type_decl_template_arg().isNotEmpty()) {
                        throw RuntimeException("Named type should not have template arguments.")
                    }
                    TypeDecl.NamedType(
                        name = ctx.IDENT().text,
                    )
                }
            }
        }

    override fun visitShort_circuit_or_expression(ctx: WGSLParser.Short_circuit_or_expressionContext): Expression {
        val rhs = visitRelational_expression(ctx.relational_expression())
        if (ctx.short_circuit_or_expression() != null) {
            return Expression.Binary(
                BinaryOperator.SHORT_CIRCUIT_OR,
                visitShort_circuit_or_expression(ctx.short_circuit_or_expression()),
                rhs,
            )
        }
        return rhs
    }

    override fun visitShort_circuit_and_expression(ctx: WGSLParser.Short_circuit_and_expressionContext): Expression {
        val rhs = visitRelational_expression(ctx.relational_expression())
        if (ctx.short_circuit_and_expression() != null) {
            return Expression.Binary(
                BinaryOperator.SHORT_CIRCUIT_AND,
                visitShort_circuit_and_expression(ctx.short_circuit_and_expression()),
                rhs,
            )
        }
        return rhs
    }

    override fun visitBinary_and_expression(ctx: WGSLParser.Binary_and_expressionContext): Expression {
        val rhs = visitUnary_expression(ctx.unary_expression())
        if (ctx.binary_and_expression() != null) {
            return Expression.Binary(BinaryOperator.BINARY_AND, visitBinary_and_expression(ctx.binary_and_expression()), rhs)
        }
        return rhs
    }

    override fun visitBinary_or_expression(ctx: WGSLParser.Binary_or_expressionContext): Expression {
        val rhs = visitUnary_expression(ctx.unary_expression())
        if (ctx.binary_or_expression() != null) {
            return Expression.Binary(BinaryOperator.BINARY_OR, visitBinary_or_expression(ctx.binary_or_expression()), rhs)
        }
        return rhs
    }

    override fun visitBinary_xor_expression(ctx: WGSLParser.Binary_xor_expressionContext): Expression {
        val rhs = visitUnary_expression(ctx.unary_expression())
        if (ctx.binary_xor_expression() != null) {
            return Expression.Binary(BinaryOperator.BINARY_XOR, visitBinary_xor_expression(ctx.binary_xor_expression()), rhs)
        }
        return rhs
    }

    override fun visitPrimary_expression(ctx: WGSLParser.Primary_expressionContext): Expression {
        if (ctx.IDENT() != null) {
            return Expression.Identifier(ctx.IDENT().text)
        }
        if (ctx.const_literal() != null) {
            return if (ctx.const_literal().bool_literal() != null) {
                Expression.BoolLiteral(
                    ctx
                        .const_literal()
                        .bool_literal()
                        .BOOL_LITERAL()
                        .text,
                )
            } else if (ctx.const_literal().int_literal() != null) {
                Expression.IntLiteral(
                    ctx
                        .const_literal()
                        .int_literal()
                        .INT_LITERAL()
                        .text,
                )
            } else if (ctx.const_literal().float_literal() != null) {
                Expression.FloatLiteral(
                    ctx
                        .const_literal()
                        .float_literal()
                        .FLOAT_LITERAL()
                        .text,
                )
            } else {
                throw UnsupportedOperationException("Unknown const literal")
            }
        }
        if (ctx.PAREN_LEFT() != null) {
            return Expression.Paren(visitExpression(ctx.expression()))
        }
        if (ctx.callable_val() != null) {
            return handleCallableValueExpression(
                ctx.callable_val(),
                ctx
                    .argument_expression_list()
                    .expression()
                    .map(::visitExpression),
            )
        }
        throw UnsupportedOperationException("Unknown primary expression.")
    }

    override fun visitSingular_expression(ctx: WGSLParser.Singular_expressionContext): Expression =
        handlePostfixExpression(
            visitPrimary_expression(ctx.primary_expression()),
            ctx.postfix_expression(),
        )

    override fun visitUnary_expression(ctx: WGSLParser.Unary_expressionContext): Expression {
        if (ctx.singular_expression() != null) {
            return visitSingular_expression(ctx.singular_expression())
        }
        val target = visitUnary_expression(ctx.unary_expression())
        val operator =
            if (ctx.MINUS() != null) {
                UnaryOperator.MINUS
            } else if (ctx.BANG() != null) {
                UnaryOperator.LOGICAL_NOT
            } else if (ctx.TILDE() != null) {
                UnaryOperator.BINARY_NOT
            } else if (ctx.STAR() != null) {
                UnaryOperator.DEREFERENCE
            } else if (ctx.AND() != null) {
                UnaryOperator.ADDRESS_OF
            } else {
                throw UnsupportedOperationException("Unknown unary operator")
            }
        return Expression.Unary(operator, target)
    }

    override fun visitMultiplicative_expression(ctx: WGSLParser.Multiplicative_expressionContext): Expression {
        val rhs = visitUnary_expression(ctx.unary_expression())
        if (ctx.STAR() != null) {
            return Expression.Binary(
                operator = BinaryOperator.TIMES,
                lhs = visitMultiplicative_expression(ctx.multiplicative_expression()),
                rhs = rhs,
            )
        }
        if (ctx.FORWARD_SLASH() != null) {
            return Expression.Binary(
                operator = BinaryOperator.DIVIDE,
                lhs = visitMultiplicative_expression(ctx.multiplicative_expression()),
                rhs = rhs,
            )
        }
        if (ctx.MODULO() != null) {
            return Expression.Binary(
                operator = BinaryOperator.MODULO,
                lhs = visitMultiplicative_expression(ctx.multiplicative_expression()),
                rhs = rhs,
            )
        }
        return rhs
    }

    override fun visitAdditive_expression(ctx: WGSLParser.Additive_expressionContext): Expression {
        val rhs = visitMultiplicative_expression(ctx.multiplicative_expression())
        if (ctx.PLUS() != null) {
            return Expression.Binary(operator = BinaryOperator.PLUS, lhs = visitAdditive_expression(ctx.additive_expression()), rhs = rhs)
        }
        if (ctx.MINUS() != null) {
            return Expression.Binary(operator = BinaryOperator.MINUS, lhs = visitAdditive_expression(ctx.additive_expression()), rhs = rhs)
        }
        return rhs
    }

    override fun visitShift_expression(ctx: WGSLParser.Shift_expressionContext): Expression {
        val rhs = visitAdditive_expression(ctx.additive_expression())
        if (ctx.GREATER_THAN().size == 2) {
            return Expression.Binary(
                operator = BinaryOperator.SHIFT_RIGHT,
                lhs = visitShift_expression(ctx.shift_expression()),
                rhs = rhs,
            )
        }
        if (ctx.LESS_THAN().size == 2) {
            return Expression.Binary(
                operator = BinaryOperator.SHIFT_LEFT,
                lhs = visitShift_expression(ctx.shift_expression()),
                rhs = rhs,
            )
        }
        assert(ctx.GREATER_THAN().isEmpty() && ctx.LESS_THAN().isEmpty())
        return rhs
    }

    override fun visitRelational_expression(ctx: WGSLParser.Relational_expressionContext): Expression {
        val lhs = visitShift_expression(ctx.shift_expression(0))
        if (ctx.shift_expression().size == 1) {
            return lhs
        }
        val rhs = visitShift_expression(ctx.shift_expression(1))
        val operator =
            if (ctx.LESS_THAN() != null) {
                BinaryOperator.LESS_THAN
            } else if (ctx.GREATER_THAN() != null) {
                BinaryOperator.GREATER_THAN
            } else if (ctx.LESS_THAN_EQUAL() != null) {
                BinaryOperator.LESS_THAN_EQUAL
            } else if (ctx.GREATER_THAN_EQUAL() != null) {
                BinaryOperator.GREATER_THAN_EQUAL
            } else if (ctx.EQUAL_EQUAL() != null) {
                BinaryOperator.EQUAL_EQUAL
            } else if (ctx.NOT_EQUAL() != null) {
                BinaryOperator.NOT_EQUAL
            } else {
                throw UnsupportedOperationException("Unknown relational operator")
            }
        return Expression.Binary(operator, lhs, rhs)
    }

    override fun visitExpression(ctx: WGSLParser.ExpressionContext): Expression {
        if (ctx.relational_expression() != null) {
            val relationalExpr = visitRelational_expression(ctx.relational_expression())
            if (ctx.short_circuit_or_expression() != null) {
                return Expression.Binary(
                    operator = BinaryOperator.SHORT_CIRCUIT_OR,
                    lhs = visitShort_circuit_or_expression(ctx.short_circuit_or_expression()),
                    rhs = relationalExpr,
                )
            }
            if (ctx.short_circuit_and_expression() != null) {
                return Expression.Binary(
                    operator = BinaryOperator.SHORT_CIRCUIT_AND,
                    lhs = visitShort_circuit_and_expression(ctx.short_circuit_and_expression()),
                    rhs = relationalExpr,
                )
            }
            return relationalExpr
        }
        val unaryExpr = visitUnary_expression(ctx.unary_expression())
        if (ctx.binary_and_expression() != null) {
            return Expression.Binary(
                operator = BinaryOperator.BINARY_AND,
                lhs = visitBinary_and_expression(ctx.binary_and_expression()),
                rhs = unaryExpr,
            )
        }
        if (ctx.binary_or_expression() != null) {
            return Expression.Binary(
                operator = BinaryOperator.BINARY_OR,
                lhs = visitBinary_or_expression(ctx.binary_or_expression()),
                rhs = unaryExpr,
            )
        }
        assert(ctx.binary_xor_expression() != null)
        return Expression.Binary(
            operator = BinaryOperator.BINARY_XOR,
            lhs = visitBinary_xor_expression(ctx.binary_xor_expression()),
            rhs = unaryExpr,
        )
    }

    // This allows superclass visitors to be used for production rules where we want every alternative to be visited,
    // such as for statements
    override fun aggregateResult(
        aggregate: Any?,
        nextResult: Any?,
    ): Any = nextResult ?: aggregate!!

    private fun handleCallableValueExpression(
        ctx: WGSLParser.Callable_valContext,
        args: List<Expression>,
    ): Expression {
        if (ctx.IDENT() != null) {
            when (val name = ctx.IDENT().text) {
                in moduleScopeNames.structNames -> {
                    assert(ctx.type_decl() == null)
                    return Expression.StructValueConstructor(name, args)
                }
                in moduleScopeNames.typeAliasNames -> {
                    assert(ctx.type_decl() == null)
                    return Expression.TypeAliasValueConstructor(name, args)
                }
                else -> {
                    return Expression.FunctionCall(
                        name,
                        ctx.type_decl()?.let(::visitType_decl),
                        args,
                    )
                }
            }
        }
        with(ctx.type_decl_without_ident()) {
            if (vec_prefix() != null) {
                val typeDecl = type_decl()?.let(::visitType_decl)
                if (typeDecl !is TypeDecl.ScalarTypeDecl?) {
                    throw UnsupportedOperationException("A vector must have a scalar element type.")
                }
                if (vec_prefix().VEC2() != null) {
                    return Expression.Vec2ValueConstructor(typeDecl, args)
                }
                if (vec_prefix().VEC3() != null) {
                    return Expression.Vec3ValueConstructor(typeDecl, args)
                }
                if (vec_prefix().VEC4() != null) {
                    return Expression.Vec4ValueConstructor(typeDecl, args)
                }
                throw UnsupportedOperationException("Unknown vector type.")
            } else if (mat_prefix() != null) {
                val typeDecl = type_decl()?.let(::visitType_decl)
                if (typeDecl !is TypeDecl.FloatTypeDecl?) {
                    throw UnsupportedOperationException("A matrix must have a float element type.")
                }
                if (mat_prefix().MAT2X2() != null) {
                    return Expression.Mat2x2ValueConstructor(typeDecl, args)
                }
                if (mat_prefix().MAT2X3() != null) {
                    return Expression.Mat2x3ValueConstructor(typeDecl, args)
                }
                if (mat_prefix().MAT2X4() != null) {
                    return Expression.Mat2x4ValueConstructor(typeDecl, args)
                }
                if (mat_prefix().MAT3X2() != null) {
                    return Expression.Mat3x2ValueConstructor(typeDecl, args)
                }
                if (mat_prefix().MAT3X3() != null) {
                    return Expression.Mat3x3ValueConstructor(typeDecl, args)
                }
                if (mat_prefix().MAT3X4() != null) {
                    return Expression.Mat3x4ValueConstructor(typeDecl, args)
                }
                if (mat_prefix().MAT4X2() != null) {
                    return Expression.Mat4x2ValueConstructor(typeDecl, args)
                }
                if (mat_prefix().MAT4X3() != null) {
                    return Expression.Mat4x3ValueConstructor(typeDecl, args)
                }
                if (mat_prefix().MAT4X4() != null) {
                    return Expression.Mat4x4ValueConstructor(typeDecl, args)
                }
                throw UnsupportedOperationException("Unknown matrix type.")
            } else if (BOOL() != null) {
                return Expression.BoolValueConstructor(args)
            } else if (FLOAT16() != null) {
                return Expression.F16ValueConstructor(args)
            } else if (FLOAT32() != null) {
                return Expression.F32ValueConstructor(args)
            } else if (INT32() != null) {
                return Expression.I32ValueConstructor(args)
            } else if (UINT32() != null) {
                return Expression.U32ValueConstructor(args)
            } else if (array_type_decl() != null) {
                // Case left to deal with:
                // array_type_decl;
                return Expression.ArrayValueConstructor(
                    array_type_decl().type_decl()?.let(::visitType_decl),
                    array_type_decl().element_count_expression()?.expression()?.let(::visitExpression),
                    args,
                )
            } else {
                throw UnsupportedOperationException("Unknown callable value expression.")
            }
        }
    }

    private fun getSingleTypeDeclTemplateArg(ctxs: List<Type_decl_template_argContext>): TypeDecl {
        if (ctxs.size != 1) {
            throw RuntimeException("Expected a single template parameter.")
        }
        val ctx = ctxs[0]
        return if (ctx.FLOAT16() != null) {
            TypeDecl.F16()
        } else if (ctx.FLOAT32() != null) {
            TypeDecl.F32()
        } else if (ctx.INT32() != null) {
            TypeDecl.I32()
        } else if (ctx.UINT32() != null) {
            TypeDecl.U32()
        } else {
            TypeDecl.NamedType(
                name = ctx.IDENT().text,
            )
        }
    }

    private fun getTextureStorageFormatTemplateArg(ctxs: List<Type_decl_template_argContext>): TexelFormat {
        if (ctxs.size != 2) {
            throw RuntimeException("Expected a two template parameters.")
        }
        val formatArg = ctxs[0]
        if (formatArg.IDENT() == null) {
            throw RuntimeException("No texel format argument found.")
        }
        return when (val texelFormat = formatArg.IDENT().text) {
            "rgba8unorm" -> TexelFormat.RGBA8UNORM
            "rgba8snorm" -> TexelFormat.RGBA8SNORM
            "rgba8uint" -> TexelFormat.RGBA8UINT
            "rgba8sint" -> TexelFormat.RGBA8SINT
            "rgba16uint" -> TexelFormat.RGBA16UINT
            "rgba16sint" -> TexelFormat.RGBA16SINT
            "rgba16float" -> TexelFormat.RGBA16FLOAT
            "r32uint" -> TexelFormat.R32UINT
            "r32sint" -> TexelFormat.R32SINT
            "r32float" -> TexelFormat.R32FLOAT
            "rg32uint" -> TexelFormat.RG32UINT
            "rg32sint" -> TexelFormat.RG32SINT
            "rg32float" -> TexelFormat.RG32FLOAT
            "rgba32uint" -> TexelFormat.RGBA32UINT
            "rgba32sint" -> TexelFormat.RGBA32SINT
            "rgba32float" -> TexelFormat.RGBA32FLOAT
            "bgra8unorm" -> TexelFormat.BGRA8UNORM
            else -> throw RuntimeException("Unknown texel format $texelFormat")
        }
    }

    private fun getTextureStorageAccessModeTemplateArg(ctxs: List<Type_decl_template_argContext>): AccessMode {
        if (ctxs.size != 2) {
            throw RuntimeException("Expected a two template parameters.")
        }
        val accessModeArg = ctxs[1]
        if (accessModeArg.IDENT() == null) {
            throw RuntimeException("No access mode argument found.")
        }
        return when (val accessModeKind = accessModeArg.IDENT().text) {
            "read" -> AccessMode.READ
            "write" -> AccessMode.WRITE
            "read_write" -> AccessMode.READ_WRITE
            else -> throw RuntimeException("Unknown access mode $accessModeKind")
        }
    }

    private fun handleAccessMode(it: String) =
        when (it) {
            "read" -> AccessMode.READ
            "write" -> AccessMode.WRITE
            "read_write" -> AccessMode.READ_WRITE
            else -> throw UnsupportedOperationException("Unknown access mode")
        }

    private fun handleAddressSpace(it: String) =
        when (it) {
            "function" -> AddressSpace.FUNCTION
            "private" -> AddressSpace.PRIVATE
            "workgroup" -> AddressSpace.WORKGROUP
            "uniform" -> AddressSpace.UNIFORM
            "storage" -> AddressSpace.STORAGE
            "handle" -> AddressSpace.HANDLE
            else -> throw UnsupportedOperationException("Unknown address space")
        }

    private fun handlePostfixExpression(
        expression: Expression,
        ctx: Postfix_expressionContext?,
    ): Expression {
        if (ctx == null) {
            return expression
        }
        if (ctx.PERIOD() != null) {
            return handlePostfixExpression(
                Expression.MemberLookup(
                    receiver = expression,
                    memberName = ctx.IDENT().text,
                ),
                ctx.postfix_expression(),
            )
        }
        return handlePostfixExpression(
            Expression.IndexLookup(
                target = expression,
                index = visitExpression(ctx.expression()),
            ),
            ctx.postfix_expression(),
        )
    }

    private fun gatherAttributes(attributes: List<AttributeContext>): List<Attribute> =
        attributes
            .map {
                val attributeTokenName =
                    if (it.attr_name() == null) {
                        it.IDENT().text
                    } else if (it.attr_name().attr_keyword() != null && it.attr_name().attr_keyword().CONST() != null) {
                        it
                            .attr_name()
                            .attr_keyword()
                            .CONST()
                            .text
                    } else if (it.attr_name().attr_keyword() != null && it.attr_name().attr_keyword().DIAGNOSTIC() != null) {
                        it
                            .attr_name()
                            .attr_keyword()
                            .DIAGNOSTIC()
                            .text
                    } else {
                        it.attr_name().IDENT().text
                    }
                val kind: AttributeKind =
                    when (attributeTokenName) {
                        "align" -> AttributeKind.ALIGN
                        "binding" -> AttributeKind.BINDING
                        "builtin" -> AttributeKind.BUILTIN
                        "compute" -> AttributeKind.COMPUTE
                        "const" -> AttributeKind.CONST
                        "diagnostic" -> AttributeKind.DIAGNOSTIC
                        "fragment" -> AttributeKind.FRAGMENT
                        "group" -> AttributeKind.GROUP
                        "id" -> AttributeKind.ID
                        "interpolate" -> AttributeKind.INTERPOLATE
                        "invariant" -> AttributeKind.INVARIANT
                        "location" -> AttributeKind.LOCATION
                        "blend_src" -> AttributeKind.BLEND_SRC
                        "must_use" -> AttributeKind.MUST_USE
                        "size" -> AttributeKind.SIZE
                        "vertex" -> AttributeKind.VERTEX
                        "workgroup_size" -> AttributeKind.WORKGROUP_SIZE
                        "input_attachment_index" -> AttributeKind.INPUT_ATTACHMENT_INDEX
                        else -> throw UnsupportedOperationException("Unknown attribute kind: $attributeTokenName")
                    }
                Attribute(
                    kind = kind,
                    args =
                        it
                            .expression()
                            ?.map { expression ->
                                visitExpression(expression)
                            } ?: emptyList(),
                )
            }
}

private class ModuleScopeNames(
    val structNames: Set<String>,
    val typeAliasNames: Set<String>,
)

private class ModuleScopeNameCollector : WGSLBaseVisitor<Unit>() {
    val names: ModuleScopeNames
        get() = ModuleScopeNames(structNames, typeAliasNames)

    private val structNames: MutableSet<String> = mutableSetOf()
    private val typeAliasNames: MutableSet<String> = mutableSetOf()

    override fun visitStruct_decl(ctx: WGSLParser.Struct_declContext) {
        structNames.add(ctx.IDENT().text)
    }

    override fun visitType_alias_decl(ctx: WGSLParser.Type_alias_declContext) {
        typeAliasNames.add(ctx.IDENT().text)
    }
}

private fun getParser(
    inputStream: InputStream,
    errorListener: ANTLRErrorListener? = null,
    parseTreeListener: ParseTreeListener? = null,
): WGSLParser {
    val charStream = CharStreams.fromStream(inputStream)
    val lexer = WGSLLexer(charStream)
    val cache = PredictionContextCache()
    lexer.interpreter =
        LexerATNSimulator(
            lexer,
            lexer.atn,
            lexer.interpreter.decisionToDFA,
            cache,
        )
    val tokens = CommonTokenStream(lexer)
    val parser = WGSLParser(tokens)
    if (errorListener != null) {
        parser.removeErrorListeners()
        parser.addErrorListener(errorListener)
    }
    if (parseTreeListener != null) {
        parser.addParseListener(parseTreeListener)
    }
    parser.interpreter =
        ParserATNSimulator(
            parser,
            parser.atn,
            parser.interpreter.decisionToDFA,
            cache,
        )
    return parser
}

private fun tryFastParse(
    inputStream: InputStream,
    parseTreeListener: ParseTreeListener,
): Translation_unitContext {
    val parser: WGSLParser =
        getParser(
            inputStream = inputStream,
            parseTreeListener = parseTreeListener,
        )
    parser.removeErrorListeners()
    parser.errorHandler = BailErrorStrategy()
    parser.interpreter.predictionMode = PredictionMode.SLL
    val result: Translation_unitContext = parser.translation_unit()
    parser.interpreter.clearDFA()
    return result
}

private fun slowParse(
    inputStream: InputStream,
    parseTreeListener: ParseTreeListener,
    errorListener: ANTLRErrorListener,
): Translation_unitContext {
    val parser: WGSLParser =
        getParser(
            inputStream = inputStream,
            parseTreeListener = parseTreeListener,
            errorListener = errorListener,
        )
    try {
        val tu: Translation_unitContext = parser.translation_unit()
        if (parser.numberOfSyntaxErrors > 0) {
            throw RuntimeException("Syntax errors during parse")
        }
        return tu
    } finally {
        parser.interpreter.clearDFA()
    }
}

fun parseFromString(
    wgslString: String,
    errorListener: ANTLRErrorListener,
    timeoutMilliseconds: Int = 10000,
): TranslationUnit {
    val inputStream: InputStream = wgslString.byteInputStream()
    val antlrTranslationUnit: Translation_unitContext =
        try {
            tryFastParse(
                inputStream = inputStream,
                parseTreeListener = TimeoutParseTreeListener(System.currentTimeMillis() + timeoutMilliseconds),
            )
        } catch (exception: ParseCancellationException) {
            inputStream.reset()
            slowParse(
                inputStream = inputStream,
                parseTreeListener = TimeoutParseTreeListener(System.currentTimeMillis() + timeoutMilliseconds),
                errorListener = errorListener,
            )
        }
    val nameCollector = ModuleScopeNameCollector()
    nameCollector.visitTranslation_unit(antlrTranslationUnit)
    return AstBuilder(nameCollector.names).visitTranslation_unit(
        antlrTranslationUnit,
    )
}

fun parseFromFile(
    filename: String,
    errorListener: ANTLRErrorListener,
    timeoutMilliseconds: Int = 10000,
): TranslationUnit =
    parseFromString(wgslString = File(filename).readText(), errorListener = errorListener, timeoutMilliseconds = timeoutMilliseconds)
