package com.wgslfuzz

import com.wgslfuzz.WGSLParser.Translation_unitContext
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.atn.*
import org.antlr.v4.runtime.dfa.DFA
import org.antlr.v4.runtime.misc.ParseCancellationException
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.ParseTreeListener
import org.antlr.v4.runtime.tree.TerminalNode
import java.io.FileInputStream
import java.io.InputStream
import java.util.BitSet

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
    override fun visitTranslation_unit(ctx: Translation_unitContext): TranslationUnit {
        val result = TranslationUnit()
        for (globalDecl in ctx.global_decl()) {
            result.globalDecls.add(visitGlobal_decl(globalDecl))
        }
        return result
    }

    override fun visitGlobal_decl(ctx: WGSLParser.Global_declContext): GlobalDecl =
        if (ctx.global_value_decl() != null) {
            visitGlobal_value_decl(ctx.global_value_decl())
        } else if (ctx.global_variable_decl() != null) {
            visitGlobal_variable_decl(ctx.global_variable_decl())
        } else if (ctx.function_decl() != null) {
            visitFunction_decl(ctx.function_decl())
        } else if (ctx.struct_decl() != null) {
            visitStruct_decl(ctx.struct_decl())
        } else if (ctx.type_alias_decl() != null) {
            visitType_alias_decl(ctx.type_alias_decl())
        } else if (ctx.const_assert_statement() != null) {
            GlobalDecl.ConstAssert()
        } else {
            GlobalDecl.Empty()
        }

    override fun visitGlobal_value_decl(ctx: WGSLParser.Global_value_declContext): GlobalDecl.Value {
        val name =
            if (ctx.IDENT() != null) {
                ctx.IDENT().text
            } else {
                ctx.variable_ident_decl().IDENT().text
            }
        return GlobalDecl.Value(name = name)
    }

    override fun visitGlobal_variable_decl(ctx: WGSLParser.Global_variable_declContext): GlobalDecl.Variable {
        val name =
            if (ctx.variable_decl().IDENT() != null) {
                ctx.variable_decl().IDENT().text
            } else {
                ctx
                    .variable_decl()
                    .variable_ident_decl()
                    .IDENT()
                    .text
            }
        return GlobalDecl.Variable(name = name)
    }

    override fun visitFunction_decl(ctx: WGSLParser.Function_declContext): GlobalDecl.Function =
        GlobalDecl.Function(ctx.function_header().IDENT().text)

    override fun visitStruct_decl(ctx: WGSLParser.Struct_declContext): GlobalDecl.Struct = GlobalDecl.Struct(ctx.IDENT().text)

    override fun visitType_alias_decl(ctx: WGSLParser.Type_alias_declContext): GlobalDecl.TypeAlias = GlobalDecl.TypeAlias(ctx.IDENT().text)
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
    filename: String,
    parseTreeListener: ParseTreeListener,
): Translation_unitContext {
    val parser: WGSLParser =
        getParser(
            inputStream = FileInputStream(filename),
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
    filename: String,
    parseTreeListener: ParseTreeListener,
    errorListener: ANTLRErrorListener,
): Translation_unitContext {
    val parser: WGSLParser =
        getParser(
            inputStream = FileInputStream(filename),
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

fun parse(
    filename: String,
    errorListener: ANTLRErrorListener,
): TranslationUnit {
    val antlrTranslationUnit: Translation_unitContext =
        try {
            tryFastParse(
                filename = filename,
                parseTreeListener = TimeoutParseTreeListener(System.currentTimeMillis() + 10000),
            )
        } catch (exception: ParseCancellationException) {
            slowParse(
                filename = filename,
                parseTreeListener = TimeoutParseTreeListener(System.currentTimeMillis() + 10000),
                errorListener = errorListener,
            )
        }
    return AstBuilder().visitTranslation_unit(
        antlrTranslationUnit,
    )
}
