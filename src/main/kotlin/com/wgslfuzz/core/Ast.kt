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

import kotlinx.serialization.Serializable
import java.io.PrintStream

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
    PRIMITIVE_INDEX,
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
@Serializable
sealed interface AstNode {
    val metadata: Set<Metadata>
}

/**
 * A translation unit corresponds to a fully parsed WGSL program.
 */
@Serializable
class TranslationUnit(
    val directives: List<Directive>,
    val globalDecls: List<GlobalDecl>,
    override val metadata: Set<Metadata> = emptySet(),
) : AstNode

/**
 * Many AST nodes have associated attributes, some of which take arguments.
 */
@Serializable
sealed interface Attribute : AstNode {
    @Serializable
    class Align(
        val expression: Expression,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Attribute

    @Serializable
    class Binding(
        val expression: Expression,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Attribute

    @Serializable
    class BlendSrc(
        val expression: Expression,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Attribute

    @Serializable
    class Builtin(
        val name: BuiltinValue,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Attribute

    @Serializable
    class Compute(
        override val metadata: Set<Metadata> = emptySet(),
    ) : Attribute

    @Serializable
    class Const(
        override val metadata: Set<Metadata> = emptySet(),
    ) : Attribute

    @Serializable
    class Diagnostic(
        val severityControl: SeverityControl,
        val diagnosticRule: DiagnosticRule,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Attribute

    @Serializable
    class Fragment(
        override val metadata: Set<Metadata> = emptySet(),
    ) : Attribute

    @Serializable
    class Group(
        val expression: Expression,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Attribute

    @Serializable
    class Id(
        val expression: Expression,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Attribute

    @Serializable
    class Interpolate(
        val interpolateType: InterpolateType,
        val interpolateSampling: InterpolateSampling? = null,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Attribute

    @Serializable
    class Invariant(
        override val metadata: Set<Metadata> = emptySet(),
    ) : Attribute

    @Serializable
    class Location(
        val expression: Expression,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Attribute

    @Serializable
    class MustUse(
        override val metadata: Set<Metadata> = emptySet(),
    ) : Attribute

    @Serializable
    class Size(
        val expression: Expression,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Attribute

    @Serializable
    class Vertex(
        override val metadata: Set<Metadata> = emptySet(),
    ) : Attribute

    @Serializable
    class WorkgroupSize(
        val sizeX: Expression,
        val sizeY: Expression? = null,
        val sizeZ: Expression? = null,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Attribute

    // Extensions:

    @Serializable
    class InputAttachmentIndex(
        val expression: Expression,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Attribute
}

/**
 * At present, the details of directives are not represented in the AST; a directive is simply
 * stored as a string. If it should prove useful to inspect the internals of directives this would
 * need to be changed.
 */
@Serializable
class Directive(
    val text: String,
    override val metadata: Set<Metadata> = emptySet(),
) : AstNode

/**
 * Global declarations are the top level declarations in a translation unit.
 */
@Serializable
sealed interface GlobalDecl : AstNode {
    @Serializable
    class Constant(
        val name: String,
        val typeDecl: TypeDecl? = null,
        val initializer: Expression,
        override val metadata: Set<Metadata> = emptySet(),
    ) : GlobalDecl

    @Serializable
    class Override(
        val attributes: List<Attribute> = emptyList(),
        val name: String,
        val typeDecl: TypeDecl? = null,
        val initializer: Expression? = null,
        override val metadata: Set<Metadata> = emptySet(),
    ) : GlobalDecl

    @Serializable
    class Variable(
        val attributes: List<Attribute> = emptyList(),
        val name: String,
        val addressSpace: AddressSpace? = null,
        val accessMode: AccessMode? = null,
        val typeDecl: TypeDecl? = null,
        val initializer: Expression? = null,
        override val metadata: Set<Metadata> = emptySet(),
    ) : GlobalDecl

    @Serializable
    class Function(
        val attributes: List<Attribute> = emptyList(),
        val name: String,
        val parameters: List<ParameterDecl>,
        val returnAttributes: List<Attribute> = emptyList(),
        val returnType: TypeDecl? = null,
        val body: Statement.Compound,
        override val metadata: Set<Metadata> = emptySet(),
    ) : GlobalDecl

    @Serializable
    class Struct(
        val name: String,
        val members: List<StructMember>,
        override val metadata: Set<Metadata> = emptySet(),
    ) : GlobalDecl

    @Serializable
    class TypeAlias(
        val name: String,
        val typeDecl: TypeDecl,
        override val metadata: Set<Metadata> = emptySet(),
    ) : GlobalDecl

    @Serializable
    class ConstAssert(
        val expression: Expression,
        override val metadata: Set<Metadata> = emptySet(),
    ) : GlobalDecl

    @Serializable
    class Empty(
        override val metadata: Set<Metadata> = emptySet(),
    ) : GlobalDecl
}

/**
 * These kinds of nodes represent type declarations in the AST. These are deliberately kept
 * entirely separate from the interfaces and classes that represent the types associated with
 * AST nodes after resolving a translation unit.
 */
@Serializable
sealed interface TypeDecl : AstNode {
    @Serializable
    sealed interface ScalarTypeDecl : TypeDecl {
        val name: String
    }

    @Serializable
    class Bool(
        override val metadata: Set<Metadata> = emptySet(),
    ) : ScalarTypeDecl {
        override val name: String
            get() = "bool"
    }

    @Serializable
    class I32(
        override val metadata: Set<Metadata> = emptySet(),
    ) : ScalarTypeDecl {
        override val name: String
            get() = "i32"
    }

    @Serializable
    class U32(
        override val metadata: Set<Metadata> = emptySet(),
    ) : ScalarTypeDecl {
        override val name: String
            get() = "u32"
    }

    @Serializable
    sealed interface FloatTypeDecl : ScalarTypeDecl

    @Serializable
    class F32(
        override val metadata: Set<Metadata> = emptySet(),
    ) : FloatTypeDecl {
        override val name: String
            get() = "f32"
    }

    @Serializable
    class F16(
        override val metadata: Set<Metadata> = emptySet(),
    ) : FloatTypeDecl {
        override val name: String
            get() = "f16"
    }

    @Serializable
    sealed interface VectorTypeDecl : TypeDecl {
        val elementType: ScalarTypeDecl
        val name: String
    }

    @Serializable
    class Vec2(
        override val elementType: ScalarTypeDecl,
        override val metadata: Set<Metadata> = emptySet(),
    ) : VectorTypeDecl {
        override val name: String
            get() = "vec2"
    }

    @Serializable
    class Vec3(
        override val elementType: ScalarTypeDecl,
        override val metadata: Set<Metadata> = emptySet(),
    ) : VectorTypeDecl {
        override val name: String
            get() = "vec3"
    }

    @Serializable
    class Vec4(
        override val elementType: ScalarTypeDecl,
        override val metadata: Set<Metadata> = emptySet(),
    ) : VectorTypeDecl {
        override val name: String
            get() = "vec4"
    }

    @Serializable
    sealed interface MatrixTypeDecl : TypeDecl {
        val elementType: FloatTypeDecl
        val name: String
    }

    @Serializable
    class Mat2x2(
        override val elementType: FloatTypeDecl,
        override val metadata: Set<Metadata> = emptySet(),
    ) : MatrixTypeDecl {
        override val name: String
            get() = "mat2x2"
    }

    @Serializable
    class Mat2x3(
        override val elementType: FloatTypeDecl,
        override val metadata: Set<Metadata> = emptySet(),
    ) : MatrixTypeDecl {
        override val name: String
            get() = "mat2x3"
    }

    @Serializable
    class Mat2x4(
        override val elementType: FloatTypeDecl,
        override val metadata: Set<Metadata> = emptySet(),
    ) : MatrixTypeDecl {
        override val name: String
            get() = "mat2x4"
    }

    @Serializable
    class Mat3x2(
        override val elementType: FloatTypeDecl,
        override val metadata: Set<Metadata> = emptySet(),
    ) : MatrixTypeDecl {
        override val name: String
            get() = "mat3x2"
    }

    @Serializable
    class Mat3x3(
        override val elementType: FloatTypeDecl,
        override val metadata: Set<Metadata> = emptySet(),
    ) : MatrixTypeDecl {
        override val name: String
            get() = "mat3x3"
    }

    @Serializable
    class Mat3x4(
        override val elementType: FloatTypeDecl,
        override val metadata: Set<Metadata> = emptySet(),
    ) : MatrixTypeDecl {
        override val name: String
            get() = "mat3x4"
    }

    @Serializable
    class Mat4x2(
        override val elementType: FloatTypeDecl,
        override val metadata: Set<Metadata> = emptySet(),
    ) : MatrixTypeDecl {
        override val name: String
            get() = "mat4x2"
    }

    @Serializable
    class Mat4x3(
        override val elementType: FloatTypeDecl,
        override val metadata: Set<Metadata> = emptySet(),
    ) : MatrixTypeDecl {
        override val name: String
            get() = "mat4x3"
    }

    @Serializable
    class Mat4x4(
        override val elementType: FloatTypeDecl,
        override val metadata: Set<Metadata> = emptySet(),
    ) : MatrixTypeDecl {
        override val name: String
            get() = "mat4x4"
    }

    @Serializable
    class Array(
        val elementType: TypeDecl,
        val elementCount: Expression? = null,
        override val metadata: Set<Metadata> = emptySet(),
    ) : TypeDecl

    @Serializable
    class NamedType(
        val name: String,
        override val metadata: Set<Metadata> = emptySet(),
    ) : TypeDecl

    @Serializable
    class Pointer(
        val addressSpace: AddressSpace,
        val pointeeType: TypeDecl,
        val accessMode: AccessMode? = null,
        override val metadata: Set<Metadata> = emptySet(),
    ) : TypeDecl

    @Serializable
    class Atomic(
        val targetType: TypeDecl,
        override val metadata: Set<Metadata> = emptySet(),
    ) : TypeDecl

    @Serializable
    class SamplerRegular(
        override val metadata: Set<Metadata> = emptySet(),
    ) : TypeDecl

    @Serializable
    class SamplerComparison(
        override val metadata: Set<Metadata> = emptySet(),
    ) : TypeDecl

    // Sampled Texture Types

    @Serializable
    class TextureSampled1D(
        val sampledType: TypeDecl,
        override val metadata: Set<Metadata> = emptySet(),
    ) : TypeDecl

    @Serializable
    class TextureSampled2D(
        val sampledType: TypeDecl,
        override val metadata: Set<Metadata> = emptySet(),
    ) : TypeDecl

    @Serializable
    class TextureSampled2DArray(
        val sampledType: TypeDecl,
        override val metadata: Set<Metadata> = emptySet(),
    ) : TypeDecl

    @Serializable
    class TextureSampled3D(
        val sampledType: TypeDecl,
        override val metadata: Set<Metadata> = emptySet(),
    ) : TypeDecl

    @Serializable
    class TextureSampledCube(
        val sampledType: TypeDecl,
        override val metadata: Set<Metadata> = emptySet(),
    ) : TypeDecl

    @Serializable
    class TextureSampledCubeArray(
        val sampledType: TypeDecl,
        override val metadata: Set<Metadata> = emptySet(),
    ) : TypeDecl

    // Multisampled Texture Types

    @Serializable
    class TextureMultisampled2d(
        val sampledType: TypeDecl,
        override val metadata: Set<Metadata> = emptySet(),
    ) : TypeDecl

    @Serializable
    class TextureDepthMultisampled2D(
        override val metadata: Set<Metadata> = emptySet(),
    ) : TypeDecl

    // External Sampled Texture Types

    @Serializable
    class TextureExternal(
        override val metadata: Set<Metadata> = emptySet(),
    ) : TypeDecl

    // Storage Texture Types

    @Serializable
    class TextureStorage1D(
        val format: TexelFormat,
        val accessMode: AccessMode,
        override val metadata: Set<Metadata> = emptySet(),
    ) : TypeDecl

    @Serializable
    class TextureStorage2D(
        val format: TexelFormat,
        val accessMode: AccessMode,
        override val metadata: Set<Metadata> = emptySet(),
    ) : TypeDecl

    @Serializable
    class TextureStorage2DArray(
        val format: TexelFormat,
        val accessMode: AccessMode,
        override val metadata: Set<Metadata> = emptySet(),
    ) : TypeDecl

    @Serializable
    class TextureStorage3D(
        val format: TexelFormat,
        val accessMode: AccessMode,
        override val metadata: Set<Metadata> = emptySet(),
    ) : TypeDecl

    // Depth Texture Types

    @Serializable
    class TextureDepth2D(
        override val metadata: Set<Metadata> = emptySet(),
    ) : TypeDecl

    @Serializable
    class TextureDepth2DArray(
        override val metadata: Set<Metadata> = emptySet(),
    ) : TypeDecl

    @Serializable
    class TextureDepthCube(
        override val metadata: Set<Metadata> = emptySet(),
    ) : TypeDecl

    @Serializable
    class TextureDepthCubeArray(
        override val metadata: Set<Metadata> = emptySet(),
    ) : TypeDecl
}

/**
 * Expressions in the AST, capturing all sorts of expressions except those that occur on the
 * left-hand-sides of assignments, which are captured by the separate LhsExpression type hierarchy.
 */
@Serializable
sealed interface Expression : AstNode {
    @Serializable
    class BoolLiteral(
        val text: String,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Expression

    @Serializable
    class FloatLiteral(
        val text: String,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Expression

    @Serializable
    class IntLiteral(
        val text: String,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Expression

    @Serializable
    class Identifier(
        val name: String,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Expression

    @Serializable
    class Paren(
        val target: Expression,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Expression

    @Serializable
    class Unary(
        val operator: UnaryOperator,
        val target: Expression,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Expression

    @Serializable
    class Binary(
        val operator: BinaryOperator,
        val lhs: Expression,
        val rhs: Expression,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Expression

    @Serializable
    class FunctionCall(
        val callee: String,
        val templateParameter: TypeDecl? = null,
        val args: List<Expression>,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Expression

    @Serializable
    sealed interface ValueConstructor : Expression {
        val constructorName: String
        val args: List<Expression>
    }

    @Serializable
    sealed interface ScalarValueConstructor : ValueConstructor

    @Serializable
    class BoolValueConstructor(
        override val args: List<Expression>,
        override val metadata: Set<Metadata> = emptySet(),
    ) : ScalarValueConstructor {
        override val constructorName: String
            get() = "bool"
    }

    @Serializable
    class I32ValueConstructor(
        override val args: List<Expression>,
        override val metadata: Set<Metadata> = emptySet(),
    ) : ScalarValueConstructor {
        override val constructorName: String
            get() = "i32"
    }

    @Serializable
    class U32ValueConstructor(
        override val args: List<Expression>,
        override val metadata: Set<Metadata> = emptySet(),
    ) : ScalarValueConstructor {
        override val constructorName: String
            get() = "u32"
    }

    @Serializable
    class F16ValueConstructor(
        override val args: List<Expression>,
        override val metadata: Set<Metadata> = emptySet(),
    ) : ScalarValueConstructor {
        override val constructorName: String
            get() = "f16"
    }

    @Serializable
    class F32ValueConstructor(
        override val args: List<Expression>,
        override val metadata: Set<Metadata> = emptySet(),
    ) : ScalarValueConstructor {
        override val constructorName: String
            get() = "f32"
    }

    @Serializable
    sealed interface VectorValueConstructor : ValueConstructor {
        val elementType: TypeDecl.ScalarTypeDecl?
    }

    @Serializable
    class Vec2ValueConstructor(
        override val elementType: TypeDecl.ScalarTypeDecl? = null,
        override val args: List<Expression>,
        override val metadata: Set<Metadata> = emptySet(),
    ) : VectorValueConstructor {
        override val constructorName: String
            get() = "vec2"
    }

    @Serializable
    class Vec3ValueConstructor(
        override val elementType: TypeDecl.ScalarTypeDecl? = null,
        override val args: List<Expression>,
        override val metadata: Set<Metadata> = emptySet(),
    ) : VectorValueConstructor {
        override val constructorName: String
            get() = "vec3"
    }

    @Serializable
    class Vec4ValueConstructor(
        override val elementType: TypeDecl.ScalarTypeDecl? = null,
        override val args: List<Expression>,
        override val metadata: Set<Metadata> = emptySet(),
    ) : VectorValueConstructor {
        override val constructorName: String
            get() = "vec4"
    }

    @Serializable
    sealed interface MatrixValueConstructor : ValueConstructor {
        val elementType: TypeDecl.FloatTypeDecl?
    }

    @Serializable
    class Mat2x2ValueConstructor(
        override val elementType: TypeDecl.FloatTypeDecl? = null,
        override val args: List<Expression>,
        override val metadata: Set<Metadata> = emptySet(),
    ) : MatrixValueConstructor {
        override val constructorName: String
            get() = "mat2x2"
    }

    @Serializable
    class Mat2x3ValueConstructor(
        override val elementType: TypeDecl.FloatTypeDecl? = null,
        override val args: List<Expression>,
        override val metadata: Set<Metadata> = emptySet(),
    ) : MatrixValueConstructor {
        override val constructorName: String
            get() = "mat2x3"
    }

    @Serializable
    class Mat2x4ValueConstructor(
        override val elementType: TypeDecl.FloatTypeDecl? = null,
        override val args: List<Expression>,
        override val metadata: Set<Metadata> = emptySet(),
    ) : MatrixValueConstructor {
        override val constructorName: String
            get() = "mat2x4"
    }

    @Serializable
    class Mat3x2ValueConstructor(
        override val elementType: TypeDecl.FloatTypeDecl? = null,
        override val args: List<Expression>,
        override val metadata: Set<Metadata> = emptySet(),
    ) : MatrixValueConstructor {
        override val constructorName: String
            get() = "mat3x2"
    }

    @Serializable
    class Mat3x3ValueConstructor(
        override val elementType: TypeDecl.FloatTypeDecl? = null,
        override val args: List<Expression>,
        override val metadata: Set<Metadata> = emptySet(),
    ) : MatrixValueConstructor {
        override val constructorName: String
            get() = "mat3x3"
    }

    @Serializable
    class Mat3x4ValueConstructor(
        override val elementType: TypeDecl.FloatTypeDecl? = null,
        override val args: List<Expression>,
        override val metadata: Set<Metadata> = emptySet(),
    ) : MatrixValueConstructor {
        override val constructorName: String
            get() = "mat3x4"
    }

    @Serializable
    class Mat4x2ValueConstructor(
        override val elementType: TypeDecl.FloatTypeDecl? = null,
        override val args: List<Expression>,
        override val metadata: Set<Metadata> = emptySet(),
    ) : MatrixValueConstructor {
        override val constructorName: String
            get() = "mat4x2"
    }

    @Serializable
    class Mat4x3ValueConstructor(
        override val elementType: TypeDecl.FloatTypeDecl? = null,
        override val args: List<Expression>,
        override val metadata: Set<Metadata> = emptySet(),
    ) : MatrixValueConstructor {
        override val constructorName: String
            get() = "mat4x3"
    }

    @Serializable
    class Mat4x4ValueConstructor(
        override val elementType: TypeDecl.FloatTypeDecl? = null,
        override val args: List<Expression>,
        override val metadata: Set<Metadata> = emptySet(),
    ) : MatrixValueConstructor {
        override val constructorName: String
            get() = "mat4x4"
    }

    @Serializable
    class StructValueConstructor(
        override val constructorName: String,
        override val args: List<Expression>,
        override val metadata: Set<Metadata> = emptySet(),
    ) : ValueConstructor

    @Serializable
    class TypeAliasValueConstructor(
        override val constructorName: String,
        override val args: List<Expression>,
        override val metadata: Set<Metadata> = emptySet(),
    ) : ValueConstructor

    @Serializable
    class ArrayValueConstructor(
        val elementType: TypeDecl? = null,
        val elementCount: Expression? = null,
        override val args: List<Expression>,
        override val metadata: Set<Metadata> = emptySet(),
    ) : ValueConstructor {
        override val constructorName: String
            get() = "array"
    }

    @Serializable
    class MemberLookup(
        val receiver: Expression,
        val memberName: String,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Expression

    @Serializable
    class IndexLookup(
        val target: Expression,
        val index: Expression,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Expression
}

/**
 * A separate type hierarchy representing expressions that occur on the left-hand-sides of
 * assignments. Even though there is some duplication between left-hand-side expressions and
 * regular expressions, there are benefits to keeping them separate: it makes it impossible to
 * construct ASTs that would exhibit certain kinds of impermissible uses of expressions in
 * 'left-hand-side' contexts.
 */
@Serializable
sealed interface LhsExpression : AstNode {
    @Serializable
    class Identifier(
        val name: String,
        override val metadata: Set<Metadata> = emptySet(),
    ) : LhsExpression

    @Serializable
    class Paren(
        val target: LhsExpression,
        override val metadata: Set<Metadata> = emptySet(),
    ) : LhsExpression

    @Serializable
    class MemberLookup(
        val receiver: LhsExpression,
        val memberName: String,
        override val metadata: Set<Metadata> = emptySet(),
    ) : LhsExpression

    @Serializable
    class IndexLookup(
        val target: LhsExpression,
        val index: Expression,
        override val metadata: Set<Metadata> = emptySet(),
    ) : LhsExpression

    @Serializable
    class Dereference(
        val target: LhsExpression,
        override val metadata: Set<Metadata> = emptySet(),
    ) : LhsExpression

    @Serializable
    class AddressOf(
        val target: LhsExpression,
        override val metadata: Set<Metadata> = emptySet(),
    ) : LhsExpression
}

/**
 * Statements in the AST.
 */
@Serializable
sealed interface Statement : AstNode {
    /**
     * Certain kinds of statement are permissible as else branches in 'if' statements;
     * this interface is used to flag the relevant statements.
     */
    @Serializable
    sealed interface ElseBranch : Statement

    /**
     * Certain kinds of statement are permissible as 'for' loop initializers;
     * this interface is used to flag the relevant statements.
     */
    @Serializable
    sealed interface ForInit : Statement

    /**
     * Certain kinds of statement are permissible as the update component of a 'for' loop;
     * this interface is used to flag the relevant statements.
     */
    @Serializable
    sealed interface ForUpdate : Statement

    @Serializable
    class Empty(
        override val metadata: Set<Metadata> = emptySet(),
    ) : Statement

    @Serializable
    class Break(
        override val metadata: Set<Metadata> = emptySet(),
    ) : Statement

    @Serializable
    class Continue(
        override val metadata: Set<Metadata> = emptySet(),
    ) : Statement

    @Serializable
    class Discard(
        override val metadata: Set<Metadata> = emptySet(),
    ) : Statement

    @Serializable
    class Return(
        val expression: Expression? = null,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Statement

    @Serializable
    class Assignment(
        val lhsExpression: LhsExpression? = null,
        val assignmentOperator: AssignmentOperator,
        val rhs: Expression,
        override val metadata: Set<Metadata> = emptySet(),
    ) : ForInit,
        ForUpdate

    @Serializable
    class Increment(
        val target: LhsExpression,
        override val metadata: Set<Metadata> = emptySet(),
    ) : ForInit,
        ForUpdate

    @Serializable
    class Decrement(
        val target: LhsExpression,
        override val metadata: Set<Metadata> = emptySet(),
    ) : ForInit,
        ForUpdate

    @Serializable
    class ConstAssert(
        val expression: Expression,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Statement

    @Serializable
    class Compound(
        val statements: List<Statement>,
        override val metadata: Set<Metadata> = emptySet(),
    ) : ElseBranch

    @Serializable
    class If(
        val attributes: List<Attribute> = emptyList(),
        val condition: Expression,
        val thenBranch: Compound,
        val elseBranch: ElseBranch? = null,
        override val metadata: Set<Metadata> = emptySet(),
    ) : ElseBranch

    @Serializable
    class Switch(
        val attributesAtStart: List<Attribute> = emptyList(),
        val expression: Expression,
        val attributesBeforeBody: List<Attribute> = emptyList(),
        val clauses: List<SwitchClause>,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Statement

    @Serializable
    class Loop(
        val attributesAtStart: List<Attribute> = emptyList(),
        val attributesBeforeBody: List<Attribute> = emptyList(),
        val body: Compound,
        val continuingStatement: ContinuingStatement? = null,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Statement

    @Serializable
    class For(
        val attributes: List<Attribute> = emptyList(),
        val init: ForInit? = null,
        val condition: Expression? = null,
        val update: ForUpdate? = null,
        val body: Compound,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Statement

    @Serializable
    class While(
        val attributes: List<Attribute> = emptyList(),
        val condition: Expression,
        val body: Compound,
        override val metadata: Set<Metadata> = emptySet(),
    ) : Statement

    @Serializable
    class FunctionCall(
        val callee: String,
        val args: List<Expression>,
        override val metadata: Set<Metadata> = emptySet(),
    ) : ForInit,
        ForUpdate

    @Serializable
    class Value(
        val isConst: Boolean,
        val name: String,
        val typeDecl: TypeDecl? = null,
        val initializer: Expression,
        override val metadata: Set<Metadata> = emptySet(),
    ) : ForInit

    @Serializable
    class Variable(
        val name: String,
        val addressSpace: AddressSpace? = null,
        val accessMode: AccessMode? = null,
        val typeDecl: TypeDecl? = null,
        val initializer: Expression? = null,
        override val metadata: Set<Metadata> = emptySet(),
    ) : ForInit
}

/**
 * This represents the 'continuing' part of a [Statement.Loop] statement. It is named
 * 'ContinuingStatement' to match the language of the WGSL specification. However, the AST is
 * designed such that this class does not implement the [Statement] interface, to make it
 * impossible to create ASTs in which 'continuing' statements occur in places where they are not
 * allowed.
 */
@Serializable
class ContinuingStatement(
    val attributes: List<Attribute> = emptyList(),
    val statements: Statement.Compound,
    val breakIfExpr: Expression? = null,
    override val metadata: Set<Metadata> = emptySet(),
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
@Serializable
class SwitchClause(
    val caseSelectors: List<Expression?>,
    val compoundStatement: Statement.Compound,
    override val metadata: Set<Metadata> = emptySet(),
) : AstNode {
    init {
        require(caseSelectors.isNotEmpty()) { "caseSelectors cannot be empty" }
    }
}

/**
 * A formal parameter to a function.
 */
@Serializable
class ParameterDecl(
    val attributes: List<Attribute> = emptyList(),
    val name: String,
    val typeDecl: TypeDecl,
    override val metadata: Set<Metadata> = emptySet(),
) : AstNode

/**
 * A member in a struct declaration.
 */
@Serializable
class StructMember(
    val attributes: List<Attribute> = emptyList(),
    val name: String,
    val typeDecl: TypeDecl,
    override val metadata: Set<Metadata> = emptySet(),
) : AstNode

@Serializable
sealed interface Metadata

sealed interface MetadataWithCommentary : Metadata {
    fun emitCommentary(
        out: PrintStream,
        emitIndent: () -> Unit,
    )
}

// The following interfaces and classes extend the AstNode hierarchy with additional nodes that *augment* the AST to
// encode transformations that have been applied to a translation unit.
//
// The motivation for this is that if such transformations trigger bugs it would be useful to be able to *undo* them in
// a principled way to home in on a minimal sequence of transformations that suffices to trigger a bug.
//
// Because Kotlin requires all classes that implement a sealed interface to reside in the same package as that
// interface, the interfaces and classes that define new AST nodes for transformations should appear in this file in the
// "core" package. However, details of how transformations are actually applied should be separated into other packages.

/**
 * An umbrella for all nodes representing augmentations to an AST.
 */
@Serializable
sealed interface AugmentedAstNode : AstNode

/**
 * Augmented statements, such as dead code fragments.
 */
@Serializable
sealed interface AugmentedStatement :
    AugmentedAstNode,
    Statement {
    sealed interface ControlFlowTransformationNode {
        val id: Int
    }

    /**
     * ControlFlowWrapper wraps a child compound with semantics preserving transformation that runs the wrapped code
     * exactly once.
     * Examples of transformations:
     * - `if (true) { <original code> }`
     *   where true can be any expression that evaluates to true
     * - `if (false) { <random code> } else { <original code> }`
     *   where false can be any expression that evaluates to false
     * - `for (var i = 0; i < 1; i++) { <original code> }`
     *   the loop control code could be anything that cause the loop to execute once
     *
     * @param statement is the statement that contain the control flow wrapping code and original statements.
     * @param id is the `id` of control flow wrapper. `id` matches to a corresponding child compound which contains the
     * original statements that are being wrapped.
     */
    @Serializable
    class ControlFlowWrapper(
        val statement: Statement,
        // ControlFlowWrapper has a child compound found within statement that has metadata containing this id.
        // This compound contains the original set of statements of the transformation.
        // The ids purpose is to associate the two together using a unique identifier.
        override val id: Int,
        override val metadata: Set<Metadata> = emptySet(),
    ) : AugmentedStatement,
        ControlFlowTransformationNode

    @Serializable
    class ControlFlowWrapHelperStatement(
        val statement: Statement,
        override val id: Int,
        override val metadata: Set<Metadata> = emptySet(),
    ) : AugmentedStatement,
        ControlFlowTransformationNode

    /**
     * This is a wrapper that works with ControlFlowWrapper to enable wrapping of return statements.
     * ControlFlowWrapReturn has an id property that connects it to a ControlFlowWrapper. If the ControlFlowWrapper is
     * removed then the corresponding ControlFlowWrapReturn should be removed. The reason it exists is that if a
     * return is wrapped then another return must be inserted to make all code paths contain a return otherwise a
     * compiler error will occur. The compiler error that would occur otherwise is: missing return at end of function
     *
     * Example:
     * Original Code
     * let x = 0;
     * return x;
     *
     * Control Flow Wrapped Code
     * let x = 0;
     * if (x == 0) {
     *   /* Control flow wrapped */
     *   return x;
     * }
     * return 19023 // ControlFlowWrapReturn
     */
    @Serializable
    class ControlFlowWrapReturn(
        val statement: Statement.Return,
        override val id: Int,
        override val metadata: Set<Metadata> = emptySet(),
    ) : AugmentedStatement,
        ControlFlowTransformationNode
}
