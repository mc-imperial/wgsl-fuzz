package com.wgslfuzz

import com.wgslfuzz.WGSLParser.AttributeContext
import com.wgslfuzz.WGSLParser.Translation_unitContext
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

private class AstBuilder : WGSLBaseVisitor<Any>() {
    override fun visitTranslation_unit(ctx: Translation_unitContext): TranslationUnit =
        TranslationUnit(
            globalDecls =
                ctx
                    .global_decl()
                    .map {
                        visitGlobal_decl(it) as GlobalDecl
                    }.toMutableList(),
        )

    override fun visitEmpty_global_decl(ctx: WGSLParser.Empty_global_declContext): GlobalDecl.Empty = GlobalDecl.Empty()

    override fun visitConst_assert_decl(ctx: WGSLParser.Const_assert_declContext): GlobalDecl.ConstAssert =
        GlobalDecl.ConstAssert(placeholder = Placeholder(ctx.fullText))

    override fun visitGlobal_value_decl(ctx: WGSLParser.Global_value_declContext): GlobalDecl.Value =
        GlobalDecl.Value(placeholder = Placeholder(ctx.fullText))

    override fun visitGlobal_variable_decl(ctx: WGSLParser.Global_variable_declContext): GlobalDecl.Variable =
        GlobalDecl.Variable(
            attributes = gatherAttributes(ctx.attribute()),
            name =
                ctx
                    .variable_decl()
                    .ident_with_optional_type()
                    .IDENT()
                    .text,
            addressSpace =
                ctx
                    .variable_decl()
                    .variable_qualifier()
                    ?.address_space()
                    ?.let { Placeholder(it.fullText) },
            accessMode =
                ctx
                    .variable_decl()
                    .variable_qualifier()
                    ?.access_mode()
                    ?.let { Placeholder(it.fullText) },
            type =
                ctx
                    .variable_decl()
                    .ident_with_optional_type()
                    .type_decl()
                    ?.let { Placeholder(it.fullText) },
            initializer = ctx.expression()?.let { Placeholder(it.fullText) },
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
                        Placeholder(it.fullText)
                    }?.toMutableList() ?: mutableListOf(),
            returnType =
                ctx.function_header().type_decl()?.let {
                    visitType_decl(it)
                },
            body = visitCompound_statement(ctx.compound_statement()),
        )

    override fun visitCompound_statement(ctx: WGSLParser.Compound_statementContext): Statement.Compound =
        Statement.Compound(
            statements =
                ctx
                    .statement()
                    .map {
                        visitStatement(it) as Statement
                    }.toMutableList(),
        )

    override fun visitReturn_statement(ctx: WGSLParser.Return_statementContext): Statement.Return =
        Statement.Return(ctx.expression()?.let { Placeholder(it.fullText) })

    override fun visitIf_statement(ctx: WGSLParser.If_statementContext): Statement.If = Statement.If(Placeholder(ctx.fullText))

    override fun visitSwitch_statement(ctx: WGSLParser.Switch_statementContext): Statement.Switch =
        Statement.Switch(Placeholder(ctx.fullText))

    override fun visitLoop_statement(ctx: WGSLParser.Loop_statementContext): Statement.Loop = Statement.Loop(Placeholder(ctx.fullText))

    override fun visitFor_statement(ctx: WGSLParser.For_statementContext): Statement.For = Statement.For(Placeholder(ctx.fullText))

    override fun visitWhile_statement(ctx: WGSLParser.While_statementContext): Statement.While =
        Statement.While(
            attributes = gatherAttributes(ctx.attribute()),
            expression = visitExpression(ctx.expression()) as Expression,
            body = visitCompound_statement(ctx.compound_statement()),
        )

    override fun visitFunc_call_statement(ctx: WGSLParser.Func_call_statementContext): Statement.FunctionCall =
        Statement.FunctionCall(Placeholder(ctx.fullText))

    override fun visitVariable_statement(ctx: WGSLParser.Variable_statementContext): Statement =
        Statement.Variable(
            qualifier =
                ctx.variable_decl().variable_qualifier()?.let {
                    Placeholder(it.fullText)
                },
            name =
                ctx
                    .variable_decl()
                    .ident_with_optional_type()
                    .IDENT()
                    .text,
            type =
                ctx.variable_decl().ident_with_optional_type().type_decl()?.let {
                    visitType_decl(it)
                },
            initializer = ctx.expression()?.let { Placeholder(it.fullText) },
        )

    override fun visitValue_statement(ctx: WGSLParser.Value_statementContext): Statement = Statement.Value(Placeholder(ctx.fullText))

    override fun visitBreak_statement(ctx: WGSLParser.Break_statementContext): Statement.Break = Statement.Break

    override fun visitContinue_statement(ctx: WGSLParser.Continue_statementContext): Statement.Continue = Statement.Continue

    override fun visitAssignment_statement(ctx: WGSLParser.Assignment_statementContext): Statement.Assignment =
        Statement.Assignment(Placeholder(ctx.fullText))

    override fun visitIncrement_statement(ctx: WGSLParser.Increment_statementContext): Statement.Increment =
        Statement.Increment(Placeholder(ctx.fullText))

    override fun visitDecrement_statement(ctx: WGSLParser.Decrement_statementContext): Statement.Decrement =
        Statement.Decrement(Placeholder(ctx.fullText))

    override fun visitDiscard_statement(ctx: WGSLParser.Discard_statementContext): Statement.Discard = Statement.Discard()

    override fun visitConst_assert_statement(ctx: WGSLParser.Const_assert_statementContext): Statement.ConstAssert =
        Statement.ConstAssert(Placeholder(ctx.fullText))

    override fun visitEmpty_statement(ctx: WGSLParser.Empty_statementContext): Statement.Empty = Statement.Empty

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
                                type = Placeholder(it.type_decl().fullText),
                            )
                        }.toMutableList(),
            )
        return result
    }

    override fun visitType_alias_decl(ctx: WGSLParser.Type_alias_declContext): GlobalDecl.TypeAlias =
        GlobalDecl.TypeAlias(Placeholder(ctx.fullText))

    override fun visitType_decl_without_ident(ctx: WGSLParser.Type_decl_without_identContext): TypeDecl {
        if (ctx.BOOL() != null) {
            return TypeDecl.Bool
        }
        if (ctx.INT32() != null) {
            return TypeDecl.I32
        }
        if (ctx.UINT32() != null) {
            return TypeDecl.U32
        }
        if (ctx.FLOAT32() != null) {
            return TypeDecl.F32
        }
        return TypeDecl.Placeholder(ctx.fullText)
    }

    override fun visitType_decl(ctx: WGSLParser.Type_declContext): TypeDecl {
        if (ctx.type_decl_without_ident() != null) {
            return visitType_decl_without_ident(ctx.type_decl_without_ident())
        }
        return TypeDecl.Placeholder(ctx.fullText)
    }

    override fun visitShort_circuit_or_expression(ctx: WGSLParser.Short_circuit_or_expressionContext): Expression =
        Expression.Placeholder(ctx.fullText)

    override fun visitShort_circuit_and_expression(ctx: WGSLParser.Short_circuit_and_expressionContext): Expression =
        Expression.Placeholder(ctx.fullText)

    override fun visitBinary_and_expression(ctx: WGSLParser.Binary_and_expressionContext): Expression = Expression.Placeholder(ctx.fullText)

    override fun visitBinary_or_expression(ctx: WGSLParser.Binary_or_expressionContext): Expression = Expression.Placeholder(ctx.fullText)

    override fun visitBinary_xor_expression(ctx: WGSLParser.Binary_xor_expressionContext): Expression = Expression.Placeholder(ctx.fullText)

    override fun visitSingular_expression(ctx: WGSLParser.Singular_expressionContext): Expression {
        // singular_expression: primary_expression postfix_expression?;

        // primary_expression: IDENT
        //          | callable_val argument_expression_list
        //          | const_literal
        //          | PAREN_LEFT expression PAREN_RIGHT;

        // postfix_expression: BRACKET_LEFT expression BRACKET_RIGHT postfix_expression?
        //                  | PERIOD IDENT postfix_expression?;
        return Expression.Placeholder(ctx.fullText)
    }

    override fun visitUnary_expression(ctx: WGSLParser.Unary_expressionContext): Expression {
        // unary_expression: singular_expression
        //                | MINUS unary_expression
        //                | BANG unary_expression
        //                | TILDE unary_expression
        //                | STAR unary_expression
        //                | AND unary_expression;
        return Expression.Placeholder(ctx.fullText)
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
        val rhs = visitRelational_expression(ctx.relational_expression())
        if (ctx.short_circuit_or_expression() != null) {
            return Expression.Binary(
                operator = BinaryOperator.SHORT_CIRCUIT_OR,
                lhs = visitShort_circuit_or_expression(ctx.short_circuit_or_expression()),
                rhs,
            )
        }
        if (ctx.short_circuit_and_expression() != null) {
            return Expression.Binary(
                operator = BinaryOperator.SHORT_CIRCUIT_AND,
                lhs = visitShort_circuit_and_expression(ctx.short_circuit_and_expression()),
                rhs,
            )
        }
        if (ctx.binary_and_expression() != null) {
            return Expression.Binary(
                operator = BinaryOperator.BINARY_AND,
                lhs = visitBinary_and_expression(ctx.binary_and_expression()),
                rhs,
            )
        }
        if (ctx.binary_or_expression() != null) {
            return Expression.Binary(
                operator = BinaryOperator.BINARY_OR,
                lhs = visitBinary_or_expression(ctx.binary_or_expression()),
                rhs,
            )
        }
        if (ctx.binary_and_expression() != null) {
            return Expression.Binary(
                operator = BinaryOperator.BINARY_XOR,
                lhs = visitBinary_xor_expression(ctx.binary_xor_expression()),
                rhs,
            )
        }
        return rhs
    }

    // This allows superclass visitors to be used for production rules where we want every alternative to be visited,
    // such as for statements
    override fun aggregateResult(
        aggregate: Any?,
        nextResult: Any?,
    ): Any = nextResult ?: aggregate!!

    private fun gatherAttributes(attributes: List<AttributeContext>) =
        attributes
            .map {
                Placeholder(it.fullText)
            }.toMutableList()
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
): TranslationUnit {
    val inputStream: InputStream = wgslString.byteInputStream()
    val antlrTranslationUnit: Translation_unitContext =
        try {
            tryFastParse(
                inputStream = inputStream,
                parseTreeListener = TimeoutParseTreeListener(System.currentTimeMillis() + 10000),
            )
        } catch (exception: ParseCancellationException) {
            inputStream.reset()
            slowParse(
                inputStream = inputStream,
                parseTreeListener = TimeoutParseTreeListener(System.currentTimeMillis() + 10000),
                errorListener = errorListener,
            )
        }
    return AstBuilder().visitTranslation_unit(
        antlrTranslationUnit,
    )
}

fun parseFromFile(
    filename: String,
    errorListener: ANTLRErrorListener,
): TranslationUnit = parseFromString(wgslString = File(filename).readText(), errorListener = errorListener)
