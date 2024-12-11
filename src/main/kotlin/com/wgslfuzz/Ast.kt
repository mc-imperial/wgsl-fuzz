package com.wgslfuzz

class TranslationUnit(
    val globalDecls: MutableList<GlobalDecl>,
)

sealed interface Statement {
    class Empty : Statement

    class Return(
        var expr: String?,
    ) : Statement

    class If(
        var text: String,
    ) : Statement

    class Switch(
        var text: String,
    ) : Statement

    class Loop(
        var text: String,
    ) : Statement

    class For(
        var text: String,
    ) : Statement

    class While(
        var text: String,
    ) : Statement

    class FunctionCall(
        var text: String,
    ) : Statement

    class Value(
        var text: String,
    ) : Statement

    class Variable(
        var text: String,
    ) : Statement

    class Break : Statement

    class Continue : Statement

    class Assignment(
        var text: String,
    ) : Statement

    class Compound(
        val statements: MutableList<Statement>,
    ) : Statement

    class Increment(
        var text: String,
    ) : Statement

    class Decrement(
        var text: String,
    ) : Statement

    class Discard : Statement

    class ConstAssert(
        var text: String,
    ) : Statement
}

sealed interface GlobalDecl {
    class Value(
        var name: String,
    ) : GlobalDecl

    class Variable(
        val attributes: MutableList<String>,
        var name: String,
        var addressSpace: String? = null,
        var accessMode: String? = null,
        var type: String? = null,
        var initializer: String? = null,
    ) : GlobalDecl

    class Function(
        val attributes: MutableList<String>,
        var name: String,
        val parameters: MutableList<String>,
        var returnType: String? = null,
        var body: Statement.Compound,
    ) : GlobalDecl

    class Struct(
        var name: String,
        val members: MutableList<StructMember>,
    ) : GlobalDecl

    class TypeAlias(
        var name: String,
    ) : GlobalDecl

    class ConstAssert(
        var text: String,
    ) : GlobalDecl

    class Empty : GlobalDecl
}

class StructMember(
    val attributes: MutableList<String>,
    var name: String,
    var type: String,
)
