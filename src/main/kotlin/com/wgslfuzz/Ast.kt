package com.wgslfuzz

// A placeholder in the AST for something that has not been elaborated yet.
class Placeholder(
    val text: String,
) {
    // // Uncomment this to detect placholders so that they can be eliminated.
    // init {
    //    assert(false)
    // }
}

class TranslationUnit(
    val globalDecls: MutableList<GlobalDecl>,
)

enum class BinaryOperator {
    SHORT_CIRCUIT_OR,
    SHORT_CIRCUIT_AND,
    BINARY_OR,
    BINARY_AND,
    BINARY_XOR,
    LESS_THAN,
    GREATER_THAN,
    LESS_THAN_EQUAL,
    GREATER_THAN_EQUAL,
    EQUAL_EQUAL,
    NOT_EQUAL,
    SHIFT_LEFT,
    SHIFT_RIGHT,
    PLUS,
    MINUS,
    TIMES,
    DIVIDE,
    MODULO,
}

sealed interface Expression {
    class Binary(
        var operator: BinaryOperator,
        var lhs: Expression,
        var rhs: Expression,
    ) : Expression

    class Placeholder(
        text: String,
    ) : Expression {
        val placeholder = com.wgslfuzz.Placeholder(text)
    }
}

sealed interface TypeDecl {
    sealed interface BasicTypeDecl : TypeDecl

    data object Bool : BasicTypeDecl

    data object I32 : BasicTypeDecl

    data object U32 : BasicTypeDecl

    data object F32 : BasicTypeDecl

    class Placeholder(
        text: String,
    ) : TypeDecl {
        val placeholder = com.wgslfuzz.Placeholder(text)
    }
}

sealed interface Statement {
    data object Empty : Statement

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
        val attributes: MutableList<Placeholder>,
        var expression: Expression,
        var body: Compound,
    ) : Statement

    class FunctionCall(
        var placeholder: Placeholder,
    ) : Statement

    class Value(
        var placeholder: Placeholder,
    ) : Statement

    class Variable(
        var qualifier: Placeholder?,
        var name: String,
        var type: TypeDecl?,
        var initializer: Placeholder?,
    ) : Statement

    data object Break : Statement

    data object Continue : Statement

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
        var returnType: TypeDecl? = null,
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
