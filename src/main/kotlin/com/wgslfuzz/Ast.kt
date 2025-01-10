package com.wgslfuzz

sealed interface AstNode

class Directive(
    var text: String,
) : AstNode

class TranslationUnit(
    val directives: MutableList<Directive>,
    val globalDecls: MutableList<GlobalDecl>,
) : AstNode

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
) : AstNode

sealed interface LhsExpression : AstNode {
    class Identifier(
        var name: String,
    ) : LhsExpression

    class Paren(
        var target: LhsExpression,
    ) : LhsExpression

    class MemberLookup(
        var receiver: LhsExpression,
        var memberName: String,
    ) : LhsExpression

    class IndexLookup(
        var target: LhsExpression,
        var index: Expression,
    ) : LhsExpression

    class Dereference(
        var target: LhsExpression,
    ) : LhsExpression

    class AddressOf(
        var target: LhsExpression,
    ) : LhsExpression
}

sealed interface Expression : AstNode {
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

    sealed class ValueConstructor(
        var typeName: String,
        val args: MutableList<Expression>,
    ) : Expression

    sealed class ScalarValueConstructor(
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

    sealed class VectorValueConstructor(
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

    sealed class MatrixValueConstructor(
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
}

sealed interface TypeDecl : AstNode {
    sealed class ScalarTypeDecl(
        val name: String,
    ) : TypeDecl

    class Bool : ScalarTypeDecl("bool")

    class I32 : ScalarTypeDecl("i32")

    class U32 : ScalarTypeDecl("u32")

    sealed class FloatTypeDecl(
        name: String,
    ) : ScalarTypeDecl(name)

    class F32 : FloatTypeDecl("f32")

    class F16 : FloatTypeDecl("f16")

    sealed class VectorTypeDecl(
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

    sealed class MatrixTypeDecl(
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
    ) : TypeDecl

    class Pointer(
        var addressSpace: AddressSpace,
        var pointeeType: TypeDecl,
        var accessMode: AccessMode?,
    ) : TypeDecl

    class Atomic(
        var targetType: TypeDecl,
    ) : TypeDecl

    class SamplerRegular : TypeDecl

    class SamplerComparison : TypeDecl

    // Sampled Texture Types

    class TextureSampled1D(
        var sampledType: TypeDecl,
    ) : TypeDecl

    class TextureSampled2D(
        var sampledType: TypeDecl,
    ) : TypeDecl

    class TextureSampled2DArray(
        var sampledType: TypeDecl,
    ) : TypeDecl

    class TextureSampled3D(
        var sampledType: TypeDecl,
    ) : TypeDecl

    class TextureSampledCube(
        var sampledType: TypeDecl,
    ) : TypeDecl

    class TextureSampledCubeArray(
        var sampledType: TypeDecl,
    ) : TypeDecl

    // Multisampled Texture Types

    class TextureMultisampled2d(
        var sampledType: TypeDecl,
    ) : TypeDecl

    class TextureDepthMultisampled2D : TypeDecl

    // External Sampled Texture Types

    class TextureExternal : TypeDecl

    // Storage Texture Types

    data class TextureStorage1D(
        var format: TexelFormat,
        var accessMode: AccessMode,
    ) : TypeDecl

    data class TextureStorage2D(
        var format: TexelFormat,
        var accessMode: AccessMode,
    ) : TypeDecl

    data class TextureStorage2DArray(
        var format: TexelFormat,
        var accessMode: AccessMode,
    ) : TypeDecl

    data class TextureStorage3D(
        var format: TexelFormat,
        var accessMode: AccessMode,
    ) : TypeDecl

    // Depth Texture Types

    class TextureDepth2D : TypeDecl

    class TextureDepth2DArray : TypeDecl

    class TextureDepthCube : TypeDecl

    class TextureDepthCubeArray : TypeDecl
}

class ContinuingStatement(
    val attributes: MutableList<Attribute>,
    val statements: MutableList<Statement>,
    var breakIfExpr: Expression?,
) : AstNode

sealed interface CaseSelectors : AstNode {
    class DefaultAlone : CaseSelectors

    class ExpressionsOrDefault(
        // Null represents default, which can occur in a sequence of case selector expressions
        val expressions: MutableList<Expression?>,
    ) : CaseSelectors
}

class SwitchClause(
    var caseSelectors: CaseSelectors,
    var compoundStatement: Statement.Compound,
) : AstNode

sealed interface Statement : AstNode {
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
        // For scoping reasons the body is represented as a statement list, rather than a compound statement,
        // because the scope of the body includes declarations occurring in the header.
        val body: MutableList<Statement>,
    ) : Statement

    class While(
        val attributes: MutableList<Attribute>,
        var expression: Expression,
        var body: Compound,
    ) : Statement

    class FunctionCall(
        var callee: String,
        val args: MutableList<Expression>,
    ) : ForInit,
        ForUpdate

    class Value(
        var isConst: Boolean,
        var name: String,
        var type: TypeDecl?,
        var initializer: Expression,
    ) : ForInit

    class Variable(
        var name: String,
        var addressSpace: AddressSpace?,
        var accessMode: AccessMode?,
        var type: TypeDecl?,
        var initializer: Expression?,
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
        var expression: Expression,
    ) : Statement

    class Empty : Statement

    class Break : Statement

    class Continue : Statement

    class Discard : Statement
}

class ParameterDecl(
    val attributes: MutableList<Attribute>,
    var name: String,
    var typeDecl: TypeDecl,
) : AstNode

sealed interface GlobalDecl : AstNode {
    class Constant(
        var name: String,
        var type: TypeDecl?,
        var initializer: Expression,
    ) : GlobalDecl

    class Override(
        val attributes: MutableList<Attribute>,
        var name: String,
        var type: TypeDecl?,
        var initializer: Expression?,
    ) : GlobalDecl

    class Variable(
        val attributes: MutableList<Attribute>,
        var name: String,
        var addressSpace: AddressSpace?,
        var accessMode: AccessMode?,
        var type: TypeDecl?,
        var initializer: Expression?,
    ) : GlobalDecl

    class Function(
        val attributes: MutableList<Attribute>,
        var name: String,
        val parameters: MutableList<ParameterDecl>,
        var returnType: TypeDecl?,
        val body: MutableList<Statement>,
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
        var expression: Expression,
    ) : GlobalDecl

    class Empty : GlobalDecl
}

class StructMember(
    val attributes: MutableList<Attribute>,
    var name: String,
    var type: TypeDecl,
) : AstNode
