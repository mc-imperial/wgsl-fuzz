package com.wgslfuzz

// A placeholder in the AST for something that has not been elaborated yet.
class Placeholder(
    val text: String,
)

class TranslationUnit(
    val globalDecls: MutableList<GlobalDecl>,
)

sealed interface Statement {
    class Empty : Statement

    class Return(
        var expr: Placeholder?,
    ) : Statement

    class If(
        var placeholder: Placeholder,
    ) : Statement

    class Switch(
        var placeholder: Placeholder,
    ) : Statement

    class Loop(
        var placeholder: Placeholder,
    ) : Statement

    class For(
        var placeholder: Placeholder,
    ) : Statement

    class While(
        var placeholder: Placeholder,
    ) : Statement

    class FunctionCall(
        var placeholder: Placeholder,
    ) : Statement

    class Value(
        var placeholder: Placeholder,
    ) : Statement

    class Variable(
        var placeholder: Placeholder,
    ) : Statement

    class Break : Statement

    class Continue : Statement

    class Assignment(
        var placeholder: Placeholder,
    ) : Statement

    class Compound(
        val statements: MutableList<Statement>,
    ) : Statement

    class Increment(
        var placeholder: Placeholder,
    ) : Statement

    class Decrement(
        var placeholder: Placeholder,
    ) : Statement

    class Discard : Statement

    class ConstAssert(
        var placeholder: Placeholder,
    ) : Statement
}

sealed interface GlobalDecl {
    class Value(
        var placeholder: Placeholder,
    ) : GlobalDecl

    class Variable(
        val attributes: MutableList<Placeholder>,
        var name: String,
        var addressSpace: Placeholder? = null,
        var accessMode: Placeholder? = null,
        var type: Placeholder? = null,
        var initializer: Placeholder? = null,
    ) : GlobalDecl

    class Function(
        val attributes: MutableList<Placeholder>,
        var name: String,
        val parameters: MutableList<Placeholder>,
        var returnType: Placeholder? = null,
        var body: Statement.Compound,
    ) : GlobalDecl

    class Struct(
        var name: String,
        val members: MutableList<StructMember>,
    ) : GlobalDecl

    class TypeAlias(
        var placeholder: Placeholder,
    ) : GlobalDecl

    class ConstAssert(
        var placeholder: Placeholder,
    ) : GlobalDecl

    class Empty : GlobalDecl
}

class StructMember(
    val attributes: MutableList<Placeholder>,
    var name: String,
    var type: Placeholder,
)
