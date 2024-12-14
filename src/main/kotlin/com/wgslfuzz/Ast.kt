package com.wgslfuzz

// A placeholder in the AST for something that has not been elaborated yet.
class Placeholder(
    val text: String,
) {
    // // Uncomment this to detect placeholders so that they can be eliminated.
    // init {
    //     assert(false)
    // }
}

class Directive(
    var text: String,
)

class TranslationUnit(
    val directives: MutableList<Directive>,
    val globalDecls: MutableList<GlobalDecl>,
)

enum class AccessMode {
    READ,
    WRITE,
    READ_WRITE,
}

enum class AddressSpace {
    FUNCTION,
    PRIVATE,
    WORKGROUP,
    UNIFORM,
    STORAGE,
    HANDLE,
}

enum class AttributeKind {
    ALIGN,
    BINDING,
    BUILTIN,
    COMPUTE,
    CONST,
    DIAGNOSTIC,
    FRAGMENT,
    GROUP,
    ID,
    INTERPOLATE,
    INVARIANT,
    LOCATION,
    BLEND_SRC,
    MUST_USE,
    SIZE,
    VERTEX,
    WORKGROUP_SIZE,

    // Extensions:
    INPUT_ATTACHMENT_INDEX,
}

enum class AssignmentOperator {
    EQUAL,
    PLUS_EQUAL,
    MINUS_EQUAL,
    TIMES_EQUAL,
    DIVIDE_EQUAL,
    MODULO_EQUAL,
    AND_EQUAL,
    OR_EQUAL,
    XOR_EQUAL,
    SHIFT_LEFT_EQUAL,
    SHIFT_RIGHT_EQUAL,
}

enum class UnaryOperator {
    MINUS,
    LOGICAL_NOT,
    BINARY_NOT,
    DEREFERENCE,
    ADDRESS_OF,
}

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

class Attribute(
    var kind: AttributeKind,
    val args: MutableList<Expression>,
)

sealed interface LhsExpression {
    class Identifier(
        var name: String,
    ) : LhsExpression

    class Paren(
        var target: LhsExpression,
    ) : LhsExpression

    class MemberLookup(
        var target: LhsExpression,
        var member: String,
    ) : LhsExpression

    class ArrayIndex(
        var target: LhsExpression,
        var indexExpression: Expression,
    ) : LhsExpression

    class Dereference(
        var target: LhsExpression,
    ) : LhsExpression

    class AddressOf(
        var target: LhsExpression,
    ) : LhsExpression
}

sealed interface Expression {
    class BoolLiteral(
        var text: String,
    ) : Expression

    class FloatLiteral(
        var text: String,
    ) : Expression

    class IntLiteral(
        var text: String,
    ) : Expression

    class Identifier(
        var name: String,
    ) : Expression

    class Paren(
        var target: Expression,
    ) : Expression

    class Unary(
        var operator: UnaryOperator,
        var target: Expression,
    ) : Expression

    class Binary(
        var operator: BinaryOperator,
        var lhs: Expression,
        var rhs: Expression,
    ) : Expression

    class FunctionCall(
        var callee: String,
        var templateParameter: TypeDecl?,
        val args: MutableList<Expression>,
    ) : Expression

    abstract class ValueConstructor(
        var typeName: String,
        val args: MutableList<Expression>,
    ) : Expression

    abstract class ScalarValueConstructor(
        scalarTypeName: String,
        args: MutableList<Expression>,
    ) : ValueConstructor(scalarTypeName, args)

    class BoolValueConstructor(
        args: MutableList<Expression>,
    ) : ScalarValueConstructor("bool", args)

    class I32ValueConstructor(
        args: MutableList<Expression>,
    ) : ScalarValueConstructor("i32", args)

    class U32ValueConstructor(
        args: MutableList<Expression>,
    ) : ScalarValueConstructor("u32", args)

    class F16ValueConstructor(
        args: MutableList<Expression>,
    ) : ScalarValueConstructor("f16", args)

    class F32ValueConstructor(
        args: MutableList<Expression>,
    ) : ScalarValueConstructor("f32", args)

    abstract class VectorValueConstructor(
        vectorTypeName: String,
        var elementType: TypeDecl.ScalarTypeDecl?,
        args: MutableList<Expression>,
    ) : ValueConstructor(vectorTypeName, args)

    class Vec2ValueConstructor(
        elementType: TypeDecl.ScalarTypeDecl?,
        args: MutableList<Expression>,
    ) : VectorValueConstructor("vec2", elementType, args)

    class Vec3ValueConstructor(
        elementType: TypeDecl.ScalarTypeDecl?,
        args: MutableList<Expression>,
    ) : VectorValueConstructor("vec3", elementType, args)

    class Vec4ValueConstructor(
        elementType: TypeDecl.ScalarTypeDecl?,
        args: MutableList<Expression>,
    ) : VectorValueConstructor("vec4", elementType, args)

    abstract class MatrixValueConstructor(
        matrixTypeName: String,
        var elementType: TypeDecl.FloatTypeDecl?,
        args: MutableList<Expression>,
    ) : ValueConstructor(matrixTypeName, args)

    class Mat2x2ValueConstructor(
        elementType: TypeDecl.FloatTypeDecl?,
        args: MutableList<Expression>,
    ) : MatrixValueConstructor("mat2x2", elementType, args)

    class Mat2x3ValueConstructor(
        elementType: TypeDecl.FloatTypeDecl?,
        args: MutableList<Expression>,
    ) : MatrixValueConstructor("mat2x3", elementType, args)

    class Mat2x4ValueConstructor(
        elementType: TypeDecl.FloatTypeDecl?,
        args: MutableList<Expression>,
    ) : MatrixValueConstructor("mat2x4", elementType, args)

    class Mat3x2ValueConstructor(
        elementType: TypeDecl.FloatTypeDecl?,
        args: MutableList<Expression>,
    ) : MatrixValueConstructor("mat3x2", elementType, args)

    class Mat3x3ValueConstructor(
        elementType: TypeDecl.FloatTypeDecl?,
        args: MutableList<Expression>,
    ) : MatrixValueConstructor("mat3x3", elementType, args)

    class Mat3x4ValueConstructor(
        elementType: TypeDecl.FloatTypeDecl?,
        args: MutableList<Expression>,
    ) : MatrixValueConstructor("mat3x4", elementType, args)

    class Mat4x2ValueConstructor(
        elementType: TypeDecl.FloatTypeDecl?,
        args: MutableList<Expression>,
    ) : MatrixValueConstructor("mat4x2", elementType, args)

    class Mat4x3ValueConstructor(
        elementType: TypeDecl.FloatTypeDecl?,
        args: MutableList<Expression>,
    ) : MatrixValueConstructor("mat4x3", elementType, args)

    class Mat4x4ValueConstructor(
        elementType: TypeDecl.FloatTypeDecl?,
        args: MutableList<Expression>,
    ) : MatrixValueConstructor("mat4x4", elementType, args)

    class StructValueConstructor(
        structName: String,
        args: MutableList<Expression>,
    ) : ValueConstructor(structName, args)

    class TypeAliasValueConstructor(
        aliasName: String,
        args: MutableList<Expression>,
    ) : ValueConstructor(aliasName, args)

    class ArrayValueConstructor(
        var elementType: TypeDecl?,
        var elementCount: Expression?,
        args: MutableList<Expression>,
    ) : ValueConstructor("array", args)

    class MemberLookup(
        var receiver: Expression,
        var memberName: String,
    ) : Expression

    class IndexLookup(
        var target: Expression,
        var index: Expression,
    ) : Expression

    class Placeholder(
        text: String,
    ) : Expression {
        val placeholder = com.wgslfuzz.Placeholder(text)
    }
}

sealed interface TypeDecl {
    abstract class ScalarTypeDecl(
        val name: String,
    ) : TypeDecl

    data object Bool : ScalarTypeDecl("bool")

    data object I32 : ScalarTypeDecl("i32")

    data object U32 : ScalarTypeDecl("u32")

    abstract class FloatTypeDecl(
        name: String,
    ) : ScalarTypeDecl(name)

    data object F32 : FloatTypeDecl("f32")

    data object F16 : FloatTypeDecl("f16")

    abstract class VectorTypeDecl(
        var elementType: ScalarTypeDecl,
    ) : TypeDecl {
        abstract val name: String
    }

    class Vec2(
        elementType: ScalarTypeDecl,
    ) : VectorTypeDecl(elementType) {
        override val name: String
            get() = "vec2"
    }

    class Vec3(
        elementType: ScalarTypeDecl,
    ) : VectorTypeDecl(elementType) {
        override val name: String
            get() = "vec3"
    }

    class Vec4(
        elementType: ScalarTypeDecl,
    ) : VectorTypeDecl(elementType) {
        override val name: String
            get() = "vec4"
    }

    abstract class MatrixTypeDecl(
        var elementType: FloatTypeDecl,
    ) : TypeDecl {
        abstract val name: String
    }

    class Mat2x2(
        elementType: FloatTypeDecl,
    ) : MatrixTypeDecl(elementType) {
        override val name: String
            get() = "mat2x2"
    }

    class Mat2x3(
        elementType: FloatTypeDecl,
    ) : MatrixTypeDecl(elementType) {
        override val name: String
            get() = "mat2x3"
    }

    class Mat2x4(
        elementType: FloatTypeDecl,
    ) : MatrixTypeDecl(elementType) {
        override val name: String
            get() = "mat2x4"
    }

    class Mat3x2(
        elementType: FloatTypeDecl,
    ) : MatrixTypeDecl(elementType) {
        override val name: String
            get() = "mat3x2"
    }

    class Mat3x3(
        elementType: FloatTypeDecl,
    ) : MatrixTypeDecl(elementType) {
        override val name: String
            get() = "mat3x3"
    }

    class Mat3x4(
        elementType: FloatTypeDecl,
    ) : MatrixTypeDecl(elementType) {
        override val name: String
            get() = "mat3x4"
    }

    class Mat4x2(
        elementType: FloatTypeDecl,
    ) : MatrixTypeDecl(elementType) {
        override val name: String
            get() = "mat4x2"
    }

    class Mat4x3(
        elementType: FloatTypeDecl,
    ) : MatrixTypeDecl(elementType) {
        override val name: String
            get() = "mat4x3"
    }

    class Mat4x4(
        elementType: FloatTypeDecl,
    ) : MatrixTypeDecl(elementType) {
        override val name: String
            get() = "mat4x4"
    }

    class Array(
        var elementType: TypeDecl,
        var elementCount: Expression?,
    ) : TypeDecl

    class NamedType(
        var name: String,
        val templateArgs: MutableList<TypeDecl>,
    ) : TypeDecl

    class Placeholder(
        text: String,
    ) : TypeDecl {
        val placeholder = com.wgslfuzz.Placeholder(text)
    }
}

class ContinuingStatement(
    val attributes: MutableList<Attribute>,
    val statements: MutableList<Statement>,
    var breakIfExpr: Expression?,
)

sealed interface CaseSelectors {
    data object DefaultAlone : CaseSelectors

    class ExpressionsOrDefault(
        // Null represents default, which can occur in a sequence of case selector expressions
        val expressions: MutableList<Expression?>,
    ) : CaseSelectors
}

class SwitchClause(
    var caseSelectors: CaseSelectors,
    var compoundStatement: Statement.Compound,
)

class VariableDecl(
    var name: String,
    var addressSpace: AddressSpace?,
    var accessMode: AccessMode?,
    var type: TypeDecl?,
    var initializer: Expression?,
)

sealed interface Statement {
    class Return(
        var expr: Expression?,
    ) : Statement

    sealed interface ElseBranch : Statement

    class If(
        val attributes: MutableList<Attribute>,
        var condition: Expression,
        var thenBranch: Compound,
        var elseBranch: ElseBranch?,
    ) : ElseBranch

    class Switch(
        val attributesAtStart: MutableList<Attribute>,
        var expression: Expression,
        val attributesBeforeBody: MutableList<Attribute>,
        val clauses: MutableList<SwitchClause>,
    ) : Statement

    // loop_statement: BRACE_LEFT statement* continuing_statement? BRACE_RIGHT;
    class Loop(
        val attributesAtStart: MutableList<Attribute>,
        val attributesBeforeBody: MutableList<Attribute>,
        val statements: MutableList<Statement>,
        var continuingStatement: ContinuingStatement?,
    ) : Statement

    sealed interface ForInit : Statement

    sealed interface ForUpdate : Statement

    class For(
        val attributes: MutableList<Attribute>,
        var init: ForInit?,
        var condition: Expression?,
        var update: ForUpdate?,
        val body: Compound,
    ) : Statement

    class While(
        val attributes: MutableList<Attribute>,
        var expression: Expression,
        var body: Compound,
    ) : Statement

    class FunctionCall(
        var placeholder: Placeholder,
    ) : ForInit,
        ForUpdate

    class Value(
        var placeholder: Placeholder,
    ) : ForInit

    class Variable(
        var variableDecl: VariableDecl,
    ) : ForInit

    class Assignment(
        var lhsExpression: LhsExpression?,
        var assignmentOperator: AssignmentOperator,
        var rhs: Expression,
    ) : ForInit,
        ForUpdate

    class Compound(
        val statements: MutableList<Statement>,
    ) : ElseBranch

    class Increment(
        var target: LhsExpression,
    ) : ForInit,
        ForUpdate

    class Decrement(
        var target: LhsExpression,
    ) : ForInit,
        ForUpdate

    class ConstAssert(
        var placeholder: Placeholder,
    ) : Statement

    data object Empty : Statement

    data object Break : Statement

    data object Continue : Statement

    data object Discard : Statement
}

class ParameterDecl(
    val attributes: MutableList<Attribute>,
    var name: String,
    var typeDecl: TypeDecl,
)

sealed interface GlobalDecl {
    class Value(
        var placeholder: Placeholder,
    ) : GlobalDecl

    class Variable(
        val attributes: MutableList<Attribute>,
        var variableDecl: VariableDecl,
    ) : GlobalDecl

    class Function(
        val attributes: MutableList<Attribute>,
        var name: String,
        val parameters: MutableList<ParameterDecl>,
        var returnType: TypeDecl?,
        var body: Statement.Compound,
    ) : GlobalDecl

    class Struct(
        var name: String,
        val members: MutableList<StructMember>,
    ) : GlobalDecl

    class TypeAlias(
        var name: String,
        var type: TypeDecl,
    ) : GlobalDecl

    class ConstAssert(
        var placeholder: Placeholder,
    ) : GlobalDecl

    data object Empty : GlobalDecl
}

class StructMember(
    val attributes: MutableList<Attribute>,
    var name: String,
    var type: TypeDecl,
)
