/*
 * Copyright 2025 The wgsl-fuzz Project Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// The AST interface and class hierarchy makes use of sealed interfaces and classes to allow
// convenient pattern matching on AST node types. The AST deliberately has a number of node types
// that have no state, for example "discard" and "break" statements, and we *do* want to regard
// distinct occurrences of these kinds nodes as distinct objects, because we might want to
// transform some but not all of them. Hence, we do not want to turn them into objects, as would
// normally be recommended.
@file:Suppress("CanSealedSubClassBeObject")

package com.wgslfuzz.core

// AST nodes depend on a number of enum classes. The enum classes that are *only* used by AST
// nodes appear here. Enum classes that are also used by types appear in separate files.

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

enum class BuiltinValue {
    VERTEX_INDEX,
    INSTANCE_INDEX,
    CLIP_DISTANCES,
    POSITION,
    FRONT_FACING,
    FRAG_DEPTH,
    SAMPLE_INDEX,
    SAMPLE_MASK,
    LOCAL_INVOCATION_ID,
    LOCAL_INVOCATION_INDEX,
    GLOBAL_INVOCATION_ID,
    WORKGROUP_ID,
    NUM_WORKGROUPS,
    SUBGROUP_INVOCATION_ID,
    SUBGROUP_SIZE,
}

enum class SeverityControl {
    ERROR,
    WARNING,
    INFO,
    OFF,
}

enum class DiagnosticRule {
    DERIVATIVE_UNIFORMITY,
    SUBGROUP_UNIFORMITY,
}

enum class InterpolateType {
    PERSPECTIVE,
    LINEAR,
    FLAT,
}

enum class InterpolateSampling {
    CENTER,
    CENTROID,
    SAMPLE,
    FIRST,
    EITHER,
}

/**
 * Every AST node (indirectly) implements this interface.
 */
sealed interface AstNode

/**
 * A translation unit corresponds to a fully parsed WGSL program.
 */
class TranslationUnit(
    val directives: List<Directive>,
    val globalDecls: List<GlobalDecl>,
) : AstNode

/**
 * Many AST nodes have associated attributes, some of which take arguments.
 */
sealed interface Attribute : AstNode {
    class Align(
        val expression: Expression,
    ) : Attribute

    class Binding(
        val expression: Expression,
    ) : Attribute

    class BlendSrc(
        val expression: Expression,
    ) : Attribute

    class Builtin(
        val name: BuiltinValue,
    ) : Attribute

    class Compute : Attribute

    class Const : Attribute

    class Diagnostic(
        val severityControl: SeverityControl,
        val diagnosticRule: DiagnosticRule,
    ) : Attribute

    class Fragment : Attribute

    class Group(
        val expression: Expression,
    ) : Attribute

    class Id(
        val expression: Expression,
    ) : Attribute

    class Interpolate(
        val interpolateType: InterpolateType,
        val interpolateSampling: InterpolateSampling? = null,
    ) : Attribute

    class Invariant : Attribute

    class Location(
        val expression: Expression,
    ) : Attribute

    class MustUse : Attribute

    class Size(
        val expression: Expression,
    ) : Attribute

    class Vertex : Attribute

    class WorkgroupSize(
        val sizeX: Expression,
        val sizeY: Expression? = null,
        val sizeZ: Expression? = null,
    ) : Attribute

    // Extensions:

    class InputAttachmentIndex(
        val expression: Expression,
    ) : Attribute
}

/**
 * At present, the details of directives are not represented in the AST; a directive is simply
 * stored as a string. If it should prove useful to inspect the internals of directives this would
 * need to be changed.
 */
class Directive(
    val text: String,
) : AstNode

/**
 * Global declarations are the top level declarations in a translation unit.
 */
sealed interface GlobalDecl : AstNode {
    class Constant(
        val name: String,
        val type: TypeDecl? = null,
        val initializer: Expression,
    ) : GlobalDecl

    class Override(
        val attributes: List<Attribute> = emptyList(),
        val name: String,
        val type: TypeDecl? = null,
        val initializer: Expression? = null,
    ) : GlobalDecl

    class Variable(
        val attributes: List<Attribute> = emptyList(),
        val name: String,
        val addressSpace: AddressSpace? = null,
        val accessMode: AccessMode? = null,
        val type: TypeDecl? = null,
        val initializer: Expression? = null,
    ) : GlobalDecl

    class Function(
        val attributes: List<Attribute> = emptyList(),
        val name: String,
        val parameters: List<ParameterDecl>,
        val returnAttributes: List<Attribute> = emptyList(),
        val returnType: TypeDecl? = null,
        val body: Statement.Compound,
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

/**
 * These kinds of nodes represent type declarations in the AST. These are deliberately kept
 * entirely separate from the interfaces and classes that represent the types associated with
 * AST nodes after resolving a translation unit.
 */
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
        val elementCount: Expression? = null,
    ) : TypeDecl

    class NamedType(
        val name: String,
    ) : TypeDecl

    class Pointer(
        val addressSpace: AddressSpace,
        val pointeeType: TypeDecl,
        val accessMode: AccessMode? = null,
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

/**
 * Expressions in the AST, capturing all sorts of expressions except those that occur on the
 * left-hand-sides of assignments, which are captured by the separate LhsExpression type hierarchy.
 */
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
        val templateParameter: TypeDecl? = null,
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
        val elementType: TypeDecl.ScalarTypeDecl? = null,
        args: List<Expression>,
    ) : ValueConstructor(vectorTypeName, args)

    class Vec2ValueConstructor(
        elementType: TypeDecl.ScalarTypeDecl? = null,
        args: List<Expression>,
    ) : VectorValueConstructor("vec2", elementType, args)

    class Vec3ValueConstructor(
        elementType: TypeDecl.ScalarTypeDecl? = null,
        args: List<Expression>,
    ) : VectorValueConstructor("vec3", elementType, args)

    class Vec4ValueConstructor(
        elementType: TypeDecl.ScalarTypeDecl? = null,
        args: List<Expression>,
    ) : VectorValueConstructor("vec4", elementType, args)

    sealed class MatrixValueConstructor(
        matrixTypeName: String,
        val elementType: TypeDecl.FloatTypeDecl? = null,
        args: List<Expression>,
    ) : ValueConstructor(matrixTypeName, args)

    class Mat2x2ValueConstructor(
        elementType: TypeDecl.FloatTypeDecl? = null,
        args: List<Expression>,
    ) : MatrixValueConstructor("mat2x2", elementType, args)

    class Mat2x3ValueConstructor(
        elementType: TypeDecl.FloatTypeDecl? = null,
        args: List<Expression>,
    ) : MatrixValueConstructor("mat2x3", elementType, args)

    class Mat2x4ValueConstructor(
        elementType: TypeDecl.FloatTypeDecl? = null,
        args: List<Expression>,
    ) : MatrixValueConstructor("mat2x4", elementType, args)

    class Mat3x2ValueConstructor(
        elementType: TypeDecl.FloatTypeDecl? = null,
        args: List<Expression>,
    ) : MatrixValueConstructor("mat3x2", elementType, args)

    class Mat3x3ValueConstructor(
        elementType: TypeDecl.FloatTypeDecl? = null,
        args: List<Expression>,
    ) : MatrixValueConstructor("mat3x3", elementType, args)

    class Mat3x4ValueConstructor(
        elementType: TypeDecl.FloatTypeDecl? = null,
        args: List<Expression>,
    ) : MatrixValueConstructor("mat3x4", elementType, args)

    class Mat4x2ValueConstructor(
        elementType: TypeDecl.FloatTypeDecl? = null,
        args: List<Expression>,
    ) : MatrixValueConstructor("mat4x2", elementType, args)

    class Mat4x3ValueConstructor(
        elementType: TypeDecl.FloatTypeDecl? = null,
        args: List<Expression>,
    ) : MatrixValueConstructor("mat4x3", elementType, args)

    class Mat4x4ValueConstructor(
        elementType: TypeDecl.FloatTypeDecl? = null,
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
        val elementType: TypeDecl? = null,
        val elementCount: Expression? = null,
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

/**
 * A separate type hierarchy representing expressions that occur on the left-hand-sides of
 * assignments. Even though there is some duplication between left-hand-side expressions and
 * regular expressions, there are benefits to keeping them separate: it makes it impossible to
 * construct ASTs that would exhibit certain kinds of impermissible uses of expressions in
 * 'left-hand-side' contexts.
 */
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

/**
 * Statements in the AST.
 */
sealed interface Statement : AstNode {
    /**
     * Certain kinds of statement are permissible as else branches in 'if' statements;
     * this interface is used to flag the relevant statements.
     */
    sealed interface ElseBranch : Statement

    /**
     * Certain kinds of statement are permissible as 'for' loop initializers;
     * this interface is used to flag the relevant statements.
     */
    sealed interface ForInit : Statement

    /**
     * Certain kinds of statement are permissible as the update component of a 'for' loop;
     * this interface is used to flag the relevant statements.
     */
    sealed interface ForUpdate : Statement

    class Empty : Statement

    class Break : Statement

    class Continue : Statement

    class Discard : Statement

    class Return(
        val expression: Expression? = null,
    ) : Statement

    class Assignment(
        val lhsExpression: LhsExpression? = null,
        val assignmentOperator: AssignmentOperator,
        val rhs: Expression,
    ) : ForInit,
        ForUpdate

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

    class Compound(
        val statements: List<Statement>,
    ) : ElseBranch

    class If(
        val attributes: List<Attribute> = emptyList(),
        val condition: Expression,
        val thenBranch: Compound,
        val elseBranch: ElseBranch? = null,
    ) : ElseBranch

    class Switch(
        val attributesAtStart: List<Attribute> = emptyList(),
        val expression: Expression,
        val attributesBeforeBody: List<Attribute> = emptyList(),
        val clauses: List<SwitchClause>,
    ) : Statement

    class Loop(
        val attributesAtStart: List<Attribute> = emptyList(),
        val attributesBeforeBody: List<Attribute> = emptyList(),
        val body: Compound,
        val continuingStatement: ContinuingStatement? = null,
    ) : Statement

    class For(
        val attributes: List<Attribute> = emptyList(),
        val init: ForInit? = null,
        val condition: Expression? = null,
        val update: ForUpdate? = null,
        val body: Compound,
    ) : Statement

    class While(
        val attributes: List<Attribute> = emptyList(),
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
        val type: TypeDecl? = null,
        val initializer: Expression,
    ) : ForInit

    class Variable(
        val name: String,
        val addressSpace: AddressSpace? = null,
        val accessMode: AccessMode? = null,
        val type: TypeDecl? = null,
        val initializer: Expression? = null,
    ) : ForInit
}

/**
 * This represents the 'continuing' part of a [Statement.Loop] statement. It is named
 * 'ContinuingStatement' to match the language of the WGSL specification. However, the AST is
 * designed such that this class does not implement the [Statement] interface, to make it
 * impossible to create ASTs in which 'continuing' statements occur in places where they are not
 * allowed.
 */
class ContinuingStatement(
    val attributes: List<Attribute> = emptyList(),
    val statements: Statement.Compound,
    val breakIfExpr: Expression? = null,
) : AstNode

/**
 * Represents a clause in a [Statement.Switch] statement.
 *
 * @param caseSelectors represents the expressions used to select this switch case.
 *     A null entry represents the 'default' case selector. This tends to occur on its own, in
 *     which case the list will simply contain null, but it can also occur in a case selector
 *     sequence.
 * @param compoundStatement represents the body of the switch clause.
 */
class SwitchClause(
    val caseSelectors: List<Expression?>,
    val compoundStatement: Statement.Compound,
) : AstNode

/**
 * A formal parameter to a function.
 */
class ParameterDecl(
    val attributes: List<Attribute> = emptyList(),
    val name: String,
    val typeDecl: TypeDecl,
) : AstNode

/**
 * A member in a struct declaration.
 */
class StructMember(
    val attributes: List<Attribute> = emptyList(),
    val name: String,
    val type: TypeDecl,
) : AstNode
