package com.wgslfuzz

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
import java.io.FileInputStream
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
        GlobalDecl.ConstAssert(ctx.fullText)

    override fun visitGlobal_value_decl(ctx: WGSLParser.Global_value_declContext): GlobalDecl.Value =
        GlobalDecl.Value(name = ctx.ident_with_optional_type().IDENT().text)

    override fun visitGlobal_variable_decl(ctx: WGSLParser.Global_variable_declContext): GlobalDecl.Variable =
        GlobalDecl.Variable(
            attributes =
                ctx
                    .attribute()
                    .map {
                        it.fullText
                    }.toMutableList(),
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
                    ?.fullText,
            accessMode =
                ctx
                    .variable_decl()
                    .variable_qualifier()
                    ?.access_mode()
                    ?.fullText,
            type =
                ctx
                    .variable_decl()
                    .ident_with_optional_type()
                    .type_decl()
                    ?.fullText,
            initializer = ctx.expression()?.fullText,
        )

    override fun visitFunction_decl(ctx: WGSLParser.Function_declContext): GlobalDecl.Function =
        GlobalDecl.Function(
            attributes =
                ctx
                    .attribute()
                    .map {
                        it.fullText
                    }.toMutableList(),
            name = ctx.function_header().IDENT().text,
            parameters =
                ctx
                    .function_header()
                    .param_list()
                    ?.param()
                    ?.map {
                        it.fullText
                    }?.toMutableList() ?: mutableListOf(),
            returnType = ctx.function_header().type_decl()?.fullText,
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
        Statement.Return(ctx.expression()?.fullText)

    override fun visitIf_statement(ctx: WGSLParser.If_statementContext): Statement.If = TODO()

    override fun visitSwitch_statement(ctx: WGSLParser.Switch_statementContext): Statement.Switch = TODO()

    override fun visitLoop_statement(ctx: WGSLParser.Loop_statementContext): Statement.Loop = Statement.Loop(ctx.fullText)

    override fun visitFor_statement(ctx: WGSLParser.For_statementContext): Statement.For = TODO()

    override fun visitWhile_statement(ctx: WGSLParser.While_statementContext): Statement.While = TODO()

    override fun visitFunc_call_statement(ctx: WGSLParser.Func_call_statementContext): Statement.FunctionCall =
        Statement.FunctionCall(ctx.fullText)

    override fun visitVariable_or_value_statement(ctx: WGSLParser.Variable_or_value_statementContext): Statement {
        if (ctx.variable_decl() != null) {
            return Statement.Variable(
                ctx.fullText,
            )
        } else {
            return Statement.Value(ctx.fullText)
        }
    }

    override fun visitBreak_statement(ctx: WGSLParser.Break_statementContext): Statement.Break = Statement.Break()

    override fun visitContinue_statement(ctx: WGSLParser.Continue_statementContext): Statement.Continue = Statement.Continue()

    override fun visitAssignment_statement(ctx: WGSLParser.Assignment_statementContext): Statement.Assignment =
        Statement.Assignment(ctx.fullText)

    override fun visitIncrement_statement(ctx: WGSLParser.Increment_statementContext): Statement.Increment = TODO()

    override fun visitDecrement_statement(ctx: WGSLParser.Decrement_statementContext): Statement.Decrement = TODO()

    override fun visitDiscard_statement(ctx: WGSLParser.Discard_statementContext): Statement.Discard = Statement.Discard()

    override fun visitConst_assert_statement(ctx: WGSLParser.Const_assert_statementContext): Statement.ConstAssert = TODO()

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
                                    it
                                        .attribute()
                                        .map {
                                            it.fullText
                                        }.toMutableList(),
                                name = it.IDENT().text,
                                type = it.type_decl().fullText,
                            )
                        }.toMutableList(),
            )
        return result
    }

    override fun visitType_alias_decl(ctx: WGSLParser.Type_alias_declContext): GlobalDecl.TypeAlias = GlobalDecl.TypeAlias(ctx.IDENT().text)

    // This allows superclass visitors to be used for production rules where we want every alternative to be visited,
    // such as for statements
    override fun aggregateResult(
        aggregate: Any?,
        nextResult: Any?,
    ): Any = nextResult ?: aggregate!!
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

fun parseFromInputStream(
    inputStream: InputStream,
    errorListener: ANTLRErrorListener,
): TranslationUnit {
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

fun parseFromString(
    wgslString: String,
    errorListener: ANTLRErrorListener,
): TranslationUnit =
    parseFromInputStream(
        inputStream = wgslString.byteInputStream(),
        errorListener = errorListener,
    )

fun parseFromFile(
    filename: String,
    errorListener: ANTLRErrorListener,
): TranslationUnit = parseFromInputStream(inputStream = FileInputStream(filename), errorListener = errorListener)
