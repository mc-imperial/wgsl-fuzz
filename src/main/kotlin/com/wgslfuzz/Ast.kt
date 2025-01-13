package com.wgslfuzz

sealed interface AstNode

class Directive(
    val text: String,
) : AstNode

class TranslationUnit(
    val directives: List<Directive>,
    val globalDecls: List<GlobalDecl>,
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
    val kind: AttributeKind,
    val args: List<Expression>,
) : AstNode

sealed interface LhsExpression : AstNode {
    class Identifier(
        val name: String,
    ) : LhsExpression

    class Paren(
        val target: LhsExpression,
    ) : LhsExpression

    class MemberLookup(
        val receiver: LhsExpression,
        val memberName: String,
    ) : LhsExpression

    class IndexLookup(
        val target: LhsExpression,
        val index: Expression,
    ) : LhsExpression

    class Dereference(
        val target: LhsExpression,
    ) : LhsExpression

    class AddressOf(
        val target: LhsExpression,
    ) : LhsExpression
}

sealed interface Expression : AstNode {
    class BoolLiteral(
        val text: String,
    ) : Expression

    class FloatLiteral(
        val text: String,
    ) : Expression

    class IntLiteral(
        val text: String,
    ) : Expression

    class Identifier(
        val name: String,
    ) : Expression

    class Paren(
        val target: Expression,
    ) : Expression

    class Unary(
        val operator: UnaryOperator,
        val target: Expression,
    ) : Expression

    class Binary(
        val operator: BinaryOperator,
        val lhs: Expression,
        val rhs: Expression,
    ) : Expression

    class FunctionCall(
        val callee: String,
        val templateParameter: TypeDecl?,
        val args: List<Expression>,
    ) : Expression

    sealed class ValueConstructor(
        val typeName: String,
        val args: List<Expression>,
    ) : Expression

    sealed class ScalarValueConstructor(
        scalarTypeName: String,
        args: List<Expression>,
    ) : ValueConstructor(scalarTypeName, args)

    class BoolValueConstructor(
        args: List<Expression>,
    ) : ScalarValueConstructor("bool", args)

    class I32ValueConstructor(
        args: List<Expression>,
    ) : ScalarValueConstructor("i32", args)

    class U32ValueConstructor(
        args: List<Expression>,
    ) : ScalarValueConstructor("u32", args)

    class F16ValueConstructor(
        args: List<Expression>,
    ) : ScalarValueConstructor("f16", args)

    class F32ValueConstructor(
        args: List<Expression>,
    ) : ScalarValueConstructor("f32", args)

    sealed class VectorValueConstructor(
        vectorTypeName: String,
        val elementType: TypeDecl.ScalarTypeDecl?,
        args: List<Expression>,
    ) : ValueConstructor(vectorTypeName, args)

    class Vec2ValueConstructor(
        elementType: TypeDecl.ScalarTypeDecl?,
        args: List<Expression>,
    ) : VectorValueConstructor("vec2", elementType, args)

    class Vec3ValueConstructor(
        elementType: TypeDecl.ScalarTypeDecl?,
        args: List<Expression>,
    ) : VectorValueConstructor("vec3", elementType, args)

    class Vec4ValueConstructor(
        elementType: TypeDecl.ScalarTypeDecl?,
        args: List<Expression>,
    ) : VectorValueConstructor("vec4", elementType, args)

    sealed class MatrixValueConstructor(
        matrixTypeName: String,
        val elementType: TypeDecl.FloatTypeDecl?,
        args: List<Expression>,
    ) : ValueConstructor(matrixTypeName, args)

    class Mat2x2ValueConstructor(
        elementType: TypeDecl.FloatTypeDecl?,
        args: List<Expression>,
    ) : MatrixValueConstructor("mat2x2", elementType, args)

    class Mat2x3ValueConstructor(
        elementType: TypeDecl.FloatTypeDecl?,
        args: List<Expression>,
    ) : MatrixValueConstructor("mat2x3", elementType, args)

    class Mat2x4ValueConstructor(
        elementType: TypeDecl.FloatTypeDecl?,
        args: List<Expression>,
    ) : MatrixValueConstructor("mat2x4", elementType, args)

    class Mat3x2ValueConstructor(
        elementType: TypeDecl.FloatTypeDecl?,
        args: List<Expression>,
    ) : MatrixValueConstructor("mat3x2", elementType, args)

    class Mat3x3ValueConstructor(
        elementType: TypeDecl.FloatTypeDecl?,
        args: List<Expression>,
    ) : MatrixValueConstructor("mat3x3", elementType, args)

    class Mat3x4ValueConstructor(
        elementType: TypeDecl.FloatTypeDecl?,
        args: List<Expression>,
    ) : MatrixValueConstructor("mat3x4", elementType, args)

    class Mat4x2ValueConstructor(
        elementType: TypeDecl.FloatTypeDecl?,
        args: List<Expression>,
    ) : MatrixValueConstructor("mat4x2", elementType, args)

    class Mat4x3ValueConstructor(
        elementType: TypeDecl.FloatTypeDecl?,
        args: List<Expression>,
    ) : MatrixValueConstructor("mat4x3", elementType, args)

    class Mat4x4ValueConstructor(
        elementType: TypeDecl.FloatTypeDecl?,
        args: List<Expression>,
    ) : MatrixValueConstructor("mat4x4", elementType, args)

    class StructValueConstructor(
        structName: String,
        args: List<Expression>,
    ) : ValueConstructor(structName, args)

    class TypeAliasValueConstructor(
        aliasName: String,
        args: List<Expression>,
    ) : ValueConstructor(aliasName, args)

    class ArrayValueConstructor(
        val elementType: TypeDecl?,
        val elementCount: Expression?,
        args: List<Expression>,
    ) : ValueConstructor("array", args)

    class MemberLookup(
        val receiver: Expression,
        val memberName: String,
    ) : Expression

    class IndexLookup(
        val target: Expression,
        val index: Expression,
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
        val elementType: ScalarTypeDecl,
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
        val elementType: FloatTypeDecl,
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
        val elementType: TypeDecl,
        val elementCount: Expression?,
    ) : TypeDecl

    class NamedType(
        val name: String,
    ) : TypeDecl

    class Pointer(
        val addressSpace: AddressSpace,
        val pointeeType: TypeDecl,
        val accessMode: AccessMode?,
    ) : TypeDecl

    class Atomic(
        val targetType: TypeDecl,
    ) : TypeDecl

    class SamplerRegular : TypeDecl

    class SamplerComparison : TypeDecl

    // Sampled Texture Types

    class TextureSampled1D(
        val sampledType: TypeDecl,
    ) : TypeDecl

    class TextureSampled2D(
        val sampledType: TypeDecl,
    ) : TypeDecl

    class TextureSampled2DArray(
        val sampledType: TypeDecl,
    ) : TypeDecl

    class TextureSampled3D(
        val sampledType: TypeDecl,
    ) : TypeDecl

    class TextureSampledCube(
        val sampledType: TypeDecl,
    ) : TypeDecl

    class TextureSampledCubeArray(
        val sampledType: TypeDecl,
    ) : TypeDecl

    // Multisampled Texture Types

    class TextureMultisampled2d(
        val sampledType: TypeDecl,
    ) : TypeDecl

    class TextureDepthMultisampled2D : TypeDecl

    // External Sampled Texture Types

    class TextureExternal : TypeDecl

    // Storage Texture Types

    class TextureStorage1D(
        val format: TexelFormat,
        val accessMode: AccessMode,
    ) : TypeDecl

    class TextureStorage2D(
        val format: TexelFormat,
        val accessMode: AccessMode,
    ) : TypeDecl

    class TextureStorage2DArray(
        val format: TexelFormat,
        val accessMode: AccessMode,
    ) : TypeDecl

    class TextureStorage3D(
        val format: TexelFormat,
        val accessMode: AccessMode,
    ) : TypeDecl

    // Depth Texture Types

    class TextureDepth2D : TypeDecl

    class TextureDepth2DArray : TypeDecl

    class TextureDepthCube : TypeDecl

    class TextureDepthCubeArray : TypeDecl
}

class ContinuingStatement(
    val attributes: List<Attribute>,
    val statements: List<Statement>,
    val breakIfExpr: Expression?,
) : AstNode

sealed interface CaseSelectors : AstNode {
    class DefaultAlone : CaseSelectors

    class ExpressionsOrDefault(
        // Null represents default, which can occur in a sequence of case selector expressions
        val expressions: List<Expression?>,
    ) : CaseSelectors
}

class SwitchClause(
    val caseSelectors: CaseSelectors,
    val compoundStatement: Statement.Compound,
) : AstNode

sealed interface Statement : AstNode {
    class Return(
        val expression: Expression?,
    ) : Statement

    sealed interface ElseBranch : Statement

    class If(
        val attributes: List<Attribute>,
        val condition: Expression,
        val thenBranch: Compound,
        val elseBranch: ElseBranch?,
    ) : ElseBranch

    class Switch(
        val attributesAtStart: List<Attribute>,
        val expression: Expression,
        val attributesBeforeBody: List<Attribute>,
        val clauses: List<SwitchClause>,
    ) : Statement

    class Loop(
        val attributesAtStart: List<Attribute>,
        val attributesBeforeBody: List<Attribute>,
        // A list of statements is used, rather than a compound statement, because the continuing statement
        // (if present) is part of the body of the loop.
        val body: List<Statement>,
        val continuingStatement: ContinuingStatement?,
    ) : Statement

    sealed interface ForInit : Statement

    sealed interface ForUpdate : Statement

    class For(
        val attributes: List<Attribute>,
        val init: ForInit?,
        val condition: Expression?,
        val update: ForUpdate?,
        // For scoping reasons the body is represented as a statement list, rather than a compound statement,
        // because the scope of the body includes declarations occurring in the header.
        val body: List<Statement>,
    ) : Statement

    class While(
        val attributes: List<Attribute>,
        val condition: Expression,
        val body: Compound,
    ) : Statement

    class FunctionCall(
        val callee: String,
        val args: List<Expression>,
    ) : ForInit,
        ForUpdate

    class Value(
        val isConst: Boolean,
        val name: String,
        val type: TypeDecl?,
        val initializer: Expression,
    ) : ForInit

    class Variable(
        val name: String,
        val addressSpace: AddressSpace?,
        val accessMode: AccessMode?,
        val type: TypeDecl?,
        val initializer: Expression?,
    ) : ForInit

    class Assignment(
        val lhsExpression: LhsExpression?,
        val assignmentOperator: AssignmentOperator,
        val rhs: Expression,
    ) : ForInit,
        ForUpdate

    class Compound(
        val statements: List<Statement>,
    ) : ElseBranch

    class Increment(
        val target: LhsExpression,
    ) : ForInit,
        ForUpdate

    class Decrement(
        val target: LhsExpression,
    ) : ForInit,
        ForUpdate

    class ConstAssert(
        val expression: Expression,
    ) : Statement

    class Empty : Statement

    class Break : Statement

    class Continue : Statement

    class Discard : Statement
}

class ParameterDecl(
    val attributes: List<Attribute>,
    val name: String,
    val typeDecl: TypeDecl,
) : AstNode

sealed interface GlobalDecl : AstNode {
    class Constant(
        val name: String,
        val type: TypeDecl?,
        val initializer: Expression,
    ) : GlobalDecl

    class Override(
        val attributes: List<Attribute>,
        val name: String,
        val type: TypeDecl?,
        val initializer: Expression?,
    ) : GlobalDecl

    class Variable(
        val attributes: List<Attribute>,
        val name: String,
        val addressSpace: AddressSpace?,
        val accessMode: AccessMode?,
        val type: TypeDecl?,
        val initializer: Expression?,
    ) : GlobalDecl

    class Function(
        val attributes: List<Attribute>,
        val name: String,
        val parameters: List<ParameterDecl>,
        val returnType: TypeDecl?,
        val body: List<Statement>,
    ) : GlobalDecl

    class Struct(
        val name: String,
        val members: List<StructMember>,
    ) : GlobalDecl

    class TypeAlias(
        val name: String,
        val type: TypeDecl,
    ) : GlobalDecl

    class ConstAssert(
        val expression: Expression,
    ) : GlobalDecl

    class Empty : GlobalDecl
}

class StructMember(
    val attributes: List<Attribute>,
    val name: String,
    val type: TypeDecl,
) : AstNode
