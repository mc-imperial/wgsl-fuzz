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
@Serializable
sealed interface AstNode

/**
 * A translation unit corresponds to a fully parsed WGSL program.
 */
@Serializable
class TranslationUnit(
    val directives: List<Directive>,
    val globalDecls: List<GlobalDecl>,
) : AstNode

/**
 * Many AST nodes have associated attributes, some of which take arguments.
 */
@Serializable
sealed interface Attribute : AstNode {
    @Serializable
    class Align(
        val expression: Expression,
    ) : Attribute

    @Serializable
    class Binding(
        val expression: Expression,
    ) : Attribute

    @Serializable
    class BlendSrc(
        val expression: Expression,
    ) : Attribute

    @Serializable
    class Builtin(
        val name: BuiltinValue,
    ) : Attribute

    @Serializable
    class Compute : Attribute

    @Serializable
    class Const : Attribute

    @Serializable
    class Diagnostic(
        val severityControl: SeverityControl,
        val diagnosticRule: DiagnosticRule,
    ) : Attribute

    @Serializable
    class Fragment : Attribute

    @Serializable
    class Group(
        val expression: Expression,
    ) : Attribute

    @Serializable
    class Id(
        val expression: Expression,
    ) : Attribute

    @Serializable
    class Interpolate(
        val interpolateType: InterpolateType,
        val interpolateSampling: InterpolateSampling? = null,
    ) : Attribute

    @Serializable
    class Invariant : Attribute

    @Serializable
    class Location(
        val expression: Expression,
    ) : Attribute

    @Serializable
    class MustUse : Attribute

    @Serializable
    class Size(
        val expression: Expression,
    ) : Attribute

    @Serializable
    class Vertex : Attribute

    @Serializable
    class WorkgroupSize(
        val sizeX: Expression,
        val sizeY: Expression? = null,
        val sizeZ: Expression? = null,
    ) : Attribute

    // Extensions:

    @Serializable
    class InputAttachmentIndex(
        val expression: Expression,
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
    ) : GlobalDecl

    @Serializable
    class Override(
        val attributes: List<Attribute> = emptyList(),
        val name: String,
        val typeDecl: TypeDecl? = null,
        val initializer: Expression? = null,
    ) : GlobalDecl

    @Serializable
    class Variable(
        val attributes: List<Attribute> = emptyList(),
        val name: String,
        val addressSpace: AddressSpace? = null,
        val accessMode: AccessMode? = null,
        val typeDecl: TypeDecl? = null,
        val initializer: Expression? = null,
    ) : GlobalDecl

    @Serializable
    class Function(
        val attributes: List<Attribute> = emptyList(),
        val name: String,
        val parameters: List<ParameterDecl>,
        val returnAttributes: List<Attribute> = emptyList(),
        val returnType: TypeDecl? = null,
        val body: Statement.Compound,
    ) : GlobalDecl

    @Serializable
    class Struct(
        val name: String,
        val members: List<StructMember>,
    ) : GlobalDecl

    @Serializable
    class TypeAlias(
        val name: String,
        val typeDecl: TypeDecl,
    ) : GlobalDecl

    @Serializable
    class ConstAssert(
        val expression: Expression,
    ) : GlobalDecl

    @Serializable
    class Empty : GlobalDecl
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
    class Bool : ScalarTypeDecl {
        override val name: String
            get() = "bool"
    }

    @Serializable
    class I32 : ScalarTypeDecl {
        override val name: String
            get() = "i32"
    }

    @Serializable
    class U32 : ScalarTypeDecl {
        override val name: String
            get() = "u32"
    }

    @Serializable
    sealed interface FloatTypeDecl : ScalarTypeDecl

    @Serializable
    class F32 : FloatTypeDecl {
        override val name: String
            get() = "f32"
    }

    @Serializable
    class F16 : FloatTypeDecl {
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
    ) : VectorTypeDecl {
        override val name: String
            get() = "vec2"
    }

    @Serializable
    class Vec3(
        override val elementType: ScalarTypeDecl,
    ) : VectorTypeDecl {
        override val name: String
            get() = "vec3"
    }

    @Serializable
    class Vec4(
        override val elementType: ScalarTypeDecl,
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
    ) : MatrixTypeDecl {
        override val name: String
            get() = "mat2x2"
    }

    @Serializable
    class Mat2x3(
        override val elementType: FloatTypeDecl,
    ) : MatrixTypeDecl {
        override val name: String
            get() = "mat2x3"
    }

    @Serializable
    class Mat2x4(
        override val elementType: FloatTypeDecl,
    ) : MatrixTypeDecl {
        override val name: String
            get() = "mat2x4"
    }

    @Serializable
    class Mat3x2(
        override val elementType: FloatTypeDecl,
    ) : MatrixTypeDecl {
        override val name: String
            get() = "mat3x2"
    }

    @Serializable
    class Mat3x3(
        override val elementType: FloatTypeDecl,
    ) : MatrixTypeDecl {
        override val name: String
            get() = "mat3x3"
    }

    @Serializable
    class Mat3x4(
        override val elementType: FloatTypeDecl,
    ) : MatrixTypeDecl {
        override val name: String
            get() = "mat3x4"
    }

    @Serializable
    class Mat4x2(
        override val elementType: FloatTypeDecl,
    ) : MatrixTypeDecl {
        override val name: String
            get() = "mat4x2"
    }

    @Serializable
    class Mat4x3(
        override val elementType: FloatTypeDecl,
    ) : MatrixTypeDecl {
        override val name: String
            get() = "mat4x3"
    }

    @Serializable
    class Mat4x4(
        override val elementType: FloatTypeDecl,
    ) : MatrixTypeDecl {
        override val name: String
            get() = "mat4x4"
    }

    @Serializable
    class Array(
        val elementType: TypeDecl,
        val elementCount: Expression? = null,
    ) : TypeDecl

    @Serializable
    class NamedType(
        val name: String,
    ) : TypeDecl

    @Serializable
    class Pointer(
        val addressSpace: AddressSpace,
        val pointeeType: TypeDecl,
        val accessMode: AccessMode? = null,
    ) : TypeDecl

    @Serializable
    class Atomic(
        val targetType: TypeDecl,
    ) : TypeDecl

    @Serializable
    class SamplerRegular : TypeDecl

    @Serializable
    class SamplerComparison : TypeDecl

    // Sampled Texture Types

    @Serializable
    class TextureSampled1D(
        val sampledType: TypeDecl,
    ) : TypeDecl

    @Serializable
    class TextureSampled2D(
        val sampledType: TypeDecl,
    ) : TypeDecl

    @Serializable
    class TextureSampled2DArray(
        val sampledType: TypeDecl,
    ) : TypeDecl

    @Serializable
    class TextureSampled3D(
        val sampledType: TypeDecl,
    ) : TypeDecl

    @Serializable
    class TextureSampledCube(
        val sampledType: TypeDecl,
    ) : TypeDecl

    @Serializable
    class TextureSampledCubeArray(
        val sampledType: TypeDecl,
    ) : TypeDecl

    // Multisampled Texture Types

    @Serializable
    class TextureMultisampled2d(
        val sampledType: TypeDecl,
    ) : TypeDecl

    @Serializable
    class TextureDepthMultisampled2D : TypeDecl

    // External Sampled Texture Types

    @Serializable
    class TextureExternal : TypeDecl

    // Storage Texture Types

    @Serializable
    class TextureStorage1D(
        val format: TexelFormat,
        val accessMode: AccessMode,
    ) : TypeDecl

    @Serializable
    class TextureStorage2D(
        val format: TexelFormat,
        val accessMode: AccessMode,
    ) : TypeDecl

    @Serializable
    class TextureStorage2DArray(
        val format: TexelFormat,
        val accessMode: AccessMode,
    ) : TypeDecl

    @Serializable
    class TextureStorage3D(
        val format: TexelFormat,
        val accessMode: AccessMode,
    ) : TypeDecl

    // Depth Texture Types

    @Serializable
    class TextureDepth2D : TypeDecl

    @Serializable
    class TextureDepth2DArray : TypeDecl

    @Serializable
    class TextureDepthCube : TypeDecl

    @Serializable
    class TextureDepthCubeArray : TypeDecl
}

fun TypeDecl.toType(scope: Scope): Type =
    when (this) {
        is TypeDecl.Array ->
            Type.Array(
                elementType = this.elementType.toType(scope),
                // Investigate whether expressions such as 8 * 8 can appear here and thus would require evaluation
                // the element count expression
                elementCount =
                    this.elementCount?.evaluateToInt(scope) ?: throw IllegalArgumentException(
                        "Array must have a known length",
                    ),
            )

        is TypeDecl.NamedType -> {
            val scopeEntry = scope.getEntry(this.name)
            when (scopeEntry) {
                is ScopeEntry.Struct, is ScopeEntry.TypeAlias -> scopeEntry.type
                else -> throw IllegalStateException("Named Type does not correspond to a named type in scope")
            }
        }

        is TypeDecl.Bool -> Type.Bool
        is TypeDecl.F16 -> Type.F16
        is TypeDecl.F32 -> Type.F32
        is TypeDecl.I32 -> Type.I32
        is TypeDecl.U32 -> Type.U32

        is TypeDecl.Vec2 ->
            Type.Vector(
                width = 2,
                elementType =
                    this.elementType.toType(scope) as? Type.Scalar
                        ?: throw IllegalStateException("Invalid vector element type"),
            )
        is TypeDecl.Vec3 ->
            Type.Vector(
                width = 3,
                elementType =
                    this.elementType.toType(scope) as? Type.Scalar
                        ?: throw IllegalStateException("Invalid vector element type"),
            )
        is TypeDecl.Vec4 ->
            Type.Vector(
                width = 4,
                elementType =
                    this.elementType.toType(scope) as? Type.Scalar
                        ?: throw IllegalStateException("Invalid vector element type"),
            )

        else -> TODO()
    }

private fun Expression.evaluateToInt(scope: Scope): Int? =
    when (this) {
        is Expression.IntLiteral -> this.text.toInt()
        is Expression.Identifier -> {
            val scopeEntry = scope.getEntry(this.name)
            when (scopeEntry) {
                is ScopeEntry.GlobalConstant -> scopeEntry.astNode.initializer.evaluateToInt(scope)
                is ScopeEntry.GlobalOverride -> TODO()
                is ScopeEntry.GlobalVariable -> TODO()
                is ScopeEntry.LocalValue -> TODO()
                is ScopeEntry.LocalVariable -> TODO()
                is ScopeEntry.Parameter -> TODO()
                else -> throw IllegalArgumentException("Cannot evaluate $this to int")
            }
        }
        is AugmentedExpression.AddZero -> TODO()
        is AugmentedExpression.DivOne -> TODO()
        is AugmentedExpression.MulOne -> TODO()
        is AugmentedExpression.SubZero -> TODO()
        is AugmentedExpression.KnownValue -> TODO()
        is Expression.FunctionCall -> TODO()
        is Expression.IndexLookup -> TODO()
        is Expression.MemberLookup -> TODO()
        is Expression.Paren -> TODO()
        is Expression.Unary -> TODO()
        is Expression.I32ValueConstructor -> TODO()
        is Expression.U32ValueConstructor -> TODO()

        else -> throw IllegalArgumentException("Cannot evaluate $this to int")
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
    ) : Expression

    @Serializable
    class FloatLiteral(
        val text: String,
    ) : Expression

    @Serializable
    class IntLiteral(
        val text: String,
    ) : Expression

    @Serializable
    class Identifier(
        val name: String,
    ) : Expression

    @Serializable
    class Paren(
        val target: Expression,
    ) : Expression

    @Serializable
    class Unary(
        val operator: UnaryOperator,
        val target: Expression,
    ) : Expression

    @Serializable
    class Binary(
        val operator: BinaryOperator,
        val lhs: Expression,
        val rhs: Expression,
    ) : Expression

    @Serializable
    class FunctionCall(
        val callee: String,
        val templateParameter: TypeDecl? = null,
        val args: List<Expression>,
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
    ) : ScalarValueConstructor {
        override val constructorName: String
            get() = "bool"
    }

    @Serializable
    class I32ValueConstructor(
        override val args: List<Expression>,
    ) : ScalarValueConstructor {
        override val constructorName: String
            get() = "i32"
    }

    @Serializable
    class U32ValueConstructor(
        override val args: List<Expression>,
    ) : ScalarValueConstructor {
        override val constructorName: String
            get() = "u32"
    }

    @Serializable
    class F16ValueConstructor(
        override val args: List<Expression>,
    ) : ScalarValueConstructor {
        override val constructorName: String
            get() = "f16"
    }

    @Serializable
    class F32ValueConstructor(
        override val args: List<Expression>,
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
    ) : VectorValueConstructor {
        override val constructorName: String
            get() = "vec2"
    }

    @Serializable
    class Vec3ValueConstructor(
        override val elementType: TypeDecl.ScalarTypeDecl? = null,
        override val args: List<Expression>,
    ) : VectorValueConstructor {
        override val constructorName: String
            get() = "vec3"
    }

    @Serializable
    class Vec4ValueConstructor(
        override val elementType: TypeDecl.ScalarTypeDecl? = null,
        override val args: List<Expression>,
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
    ) : MatrixValueConstructor {
        override val constructorName: String
            get() = "mat2x2"
    }

    @Serializable
    class Mat2x3ValueConstructor(
        override val elementType: TypeDecl.FloatTypeDecl? = null,
        override val args: List<Expression>,
    ) : MatrixValueConstructor {
        override val constructorName: String
            get() = "mat2x3"
    }

    @Serializable
    class Mat2x4ValueConstructor(
        override val elementType: TypeDecl.FloatTypeDecl? = null,
        override val args: List<Expression>,
    ) : MatrixValueConstructor {
        override val constructorName: String
            get() = "mat2x4"
    }

    @Serializable
    class Mat3x2ValueConstructor(
        override val elementType: TypeDecl.FloatTypeDecl? = null,
        override val args: List<Expression>,
    ) : MatrixValueConstructor {
        override val constructorName: String
            get() = "mat3x2"
    }

    @Serializable
    class Mat3x3ValueConstructor(
        override val elementType: TypeDecl.FloatTypeDecl? = null,
        override val args: List<Expression>,
    ) : MatrixValueConstructor {
        override val constructorName: String
            get() = "mat3x3"
    }

    @Serializable
    class Mat3x4ValueConstructor(
        override val elementType: TypeDecl.FloatTypeDecl? = null,
        override val args: List<Expression>,
    ) : MatrixValueConstructor {
        override val constructorName: String
            get() = "mat3x4"
    }

    @Serializable
    class Mat4x2ValueConstructor(
        override val elementType: TypeDecl.FloatTypeDecl? = null,
        override val args: List<Expression>,
    ) : MatrixValueConstructor {
        override val constructorName: String
            get() = "mat4x2"
    }

    @Serializable
    class Mat4x3ValueConstructor(
        override val elementType: TypeDecl.FloatTypeDecl? = null,
        override val args: List<Expression>,
    ) : MatrixValueConstructor {
        override val constructorName: String
            get() = "mat4x3"
    }

    @Serializable
    class Mat4x4ValueConstructor(
        override val elementType: TypeDecl.FloatTypeDecl? = null,
        override val args: List<Expression>,
    ) : MatrixValueConstructor {
        override val constructorName: String
            get() = "mat4x4"
    }

    @Serializable
    class StructValueConstructor(
        override val constructorName: String,
        override val args: List<Expression>,
    ) : ValueConstructor

    @Serializable
    class TypeAliasValueConstructor(
        override val constructorName: String,
        override val args: List<Expression>,
    ) : ValueConstructor

    @Serializable
    class ArrayValueConstructor(
        val elementType: TypeDecl? = null,
        val elementCount: Expression? = null,
        override val args: List<Expression>,
    ) : ValueConstructor {
        override val constructorName: String
            get() = "array"
    }

    @Serializable
    class MemberLookup(
        val receiver: Expression,
        val memberName: String,
    ) : Expression

    @Serializable
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
@Serializable
sealed interface LhsExpression : AstNode {
    @Serializable
    class Identifier(
        val name: String,
    ) : LhsExpression

    @Serializable
    class Paren(
        val target: LhsExpression,
    ) : LhsExpression

    @Serializable
    class MemberLookup(
        val receiver: LhsExpression,
        val memberName: String,
    ) : LhsExpression

    @Serializable
    class IndexLookup(
        val target: LhsExpression,
        val index: Expression,
    ) : LhsExpression

    @Serializable
    class Dereference(
        val target: LhsExpression,
    ) : LhsExpression

    @Serializable
    class AddressOf(
        val target: LhsExpression,
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
    class Empty : Statement

    @Serializable
    class Break : Statement

    @Serializable
    class Continue : Statement

    @Serializable
    class Discard : Statement

    @Serializable
    class Return(
        val expression: Expression? = null,
    ) : Statement

    @Serializable
    class Assignment(
        val lhsExpression: LhsExpression? = null,
        val assignmentOperator: AssignmentOperator,
        val rhs: Expression,
    ) : ForInit,
        ForUpdate

    @Serializable
    class Increment(
        val target: LhsExpression,
    ) : ForInit,
        ForUpdate

    @Serializable
    class Decrement(
        val target: LhsExpression,
    ) : ForInit,
        ForUpdate

    @Serializable
    class ConstAssert(
        val expression: Expression,
    ) : Statement

    @Serializable
    class Compound(
        val statements: List<Statement>,
    ) : ElseBranch

    @Serializable
    class If(
        val attributes: List<Attribute> = emptyList(),
        val condition: Expression,
        val thenBranch: Compound,
        val elseBranch: ElseBranch? = null,
    ) : ElseBranch

    @Serializable
    class Switch(
        val attributesAtStart: List<Attribute> = emptyList(),
        val expression: Expression,
        val attributesBeforeBody: List<Attribute> = emptyList(),
        val clauses: List<SwitchClause>,
    ) : Statement

    @Serializable
    class Loop(
        val attributesAtStart: List<Attribute> = emptyList(),
        val attributesBeforeBody: List<Attribute> = emptyList(),
        val body: Compound,
        val continuingStatement: ContinuingStatement? = null,
    ) : Statement

    @Serializable
    class For(
        val attributes: List<Attribute> = emptyList(),
        val init: ForInit? = null,
        val condition: Expression? = null,
        val update: ForUpdate? = null,
        val body: Compound,
    ) : Statement

    @Serializable
    class While(
        val attributes: List<Attribute> = emptyList(),
        val condition: Expression,
        val body: Compound,
    ) : Statement

    @Serializable
    class FunctionCall(
        val callee: String,
        val args: List<Expression>,
    ) : ForInit,
        ForUpdate

    @Serializable
    class Value(
        val isConst: Boolean,
        val name: String,
        val typeDecl: TypeDecl? = null,
        val initializer: Expression,
    ) : ForInit

    @Serializable
    class Variable(
        val name: String,
        val addressSpace: AddressSpace? = null,
        val accessMode: AccessMode? = null,
        val typeDecl: TypeDecl? = null,
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
@Serializable
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
@Serializable
class SwitchClause(
    val caseSelectors: List<Expression?>,
    val compoundStatement: Statement.Compound,
) : AstNode

/**
 * A formal parameter to a function.
 */
@Serializable
class ParameterDecl(
    val attributes: List<Attribute> = emptyList(),
    val name: String,
    val typeDecl: TypeDecl,
) : AstNode

/**
 * A member in a struct declaration.
 */
@Serializable
class StructMember(
    val attributes: List<Attribute> = emptyList(),
    val name: String,
    val typeDecl: TypeDecl,
) : AstNode

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
 * Augmented expressions, which may include (for example) transformations applied to existing expressions, or
 * expressions that support transformations applied to statements.
 */
@Serializable
sealed interface AugmentedExpression :
    AugmentedAstNode,
    Expression {
    /**
     * An expression that is guaranteed to evaluate to false, without observable side effects.
     *
     * It is up to the client that creates such an expression to ensure that evaluation to false is guaranteed and that
     * there are no observable side effects. The augmented node merely serves as marker in the AST to indicate that this
     * could be replaced with a literal "false" expression without changing semantics.
     */
    @Serializable
    class FalseByConstruction(
        val falseExpression: Expression,
    ) : AugmentedExpression

    /**
     * Similar to [FalseByConstruction], but for an expression that is guaranteed to evaluate to true.
     */
    @Serializable
    class TrueByConstruction(
        val trueExpression: Expression,
    ) : AugmentedExpression

    @Serializable
    class KnownValue(
        val knownValue: Expression,
        val expression: Expression,
    ) : AugmentedExpression {
        init {
            if (knownValue is Expression.FloatLiteral) {
                val doubleValue = knownValue.text.removeSuffix("f").toDouble()
                val preciseIntegerFloatingPointRange = -16777216.0..16777216.0
                if (doubleValue !in preciseIntegerFloatingPointRange || doubleValue != doubleValue.toInt().toDouble()) {
                    throw UnsupportedOperationException(
                        "A floating-point known value must be representable as an integer; found value $doubleValue.",
                    )
                }
            }
        }
    }

    @Serializable
    sealed interface IdentityOperation : AugmentedExpression {
        val originalExpression: Expression
    }

    @Serializable
    class AddZero(
        override val originalExpression: Expression,
        val zeroExpression: Expression,
        val zeroOnLeft: Boolean,
    ) : IdentityOperation

    @Serializable
    class MulOne(
        override val originalExpression: Expression,
        val oneExpression: Expression,
        val oneOnLeft: Boolean,
    ) : IdentityOperation

    @Serializable
    class SubZero(
        override val originalExpression: Expression,
        val zeroExpression: Expression,
    ) : IdentityOperation

    @Serializable
    class DivOne(
        override val originalExpression: Expression,
        val oneExpression: Expression,
    ) : IdentityOperation
}

/**
 * Augmented statements, such as dead code fragments.
 */
@Serializable
sealed interface AugmentedStatement :
    AugmentedAstNode,
    Statement {
    /**
     * A compound statement guarded by a false-by-construction condition (or equivalent), such as:
     *
     * if (false-by-construction) {
     *   // dead code
     * }
     *
     * or:
     *
     * if (true-by-construction) {
     *
     * } else {
     *   // dead code
     * }
     *
     * or:
     *
     * while (false-by-construction) {
     *   // dead code
     * }
     *
     * It is up to the client that creates this kind of AST node to ensure that the provided statement really does have
     * this property.
     */
    @Serializable
    class DeadCodeFragment(
        val statement: Statement,
    ) : AugmentedStatement
}
