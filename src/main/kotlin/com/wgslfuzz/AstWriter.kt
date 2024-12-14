package com.wgslfuzz

import java.io.PrintStream

class AstWriter(
    private val out: PrintStream = System.out,
    private val indentValue: Int = 4,
) {
    private var currentIndentLevel = 0

    private fun emitIndent() =
        repeat((0..<currentIndentLevel).count()) {
            out.print(" ")
        }

    private fun increaseIndent() {
        currentIndentLevel += indentValue
    }

    private fun decreaseIndent() {
        currentIndentLevel -= indentValue
    }

    fun emit(accessMode: AccessMode) {
        out.print(
            when (accessMode) {
                AccessMode.READ -> "read"
                AccessMode.WRITE -> "write"
                AccessMode.READ_WRITE -> "read_write"
            },
        )
    }

    fun emit(addressSpace: AddressSpace) {
        out.print(
            when (addressSpace) {
                AddressSpace.FUNCTION -> "function"
                AddressSpace.PRIVATE -> "private"
                AddressSpace.WORKGROUP -> "workgroup"
                AddressSpace.UNIFORM -> "uniform"
                AddressSpace.STORAGE -> "storage"
                AddressSpace.HANDLE -> "handle"
            },
        )
    }

    fun emit(attributeKind: AttributeKind) {
        out.print(
            when (attributeKind) {
                AttributeKind.ALIGN -> "align"
                AttributeKind.BINDING -> "binding"
                AttributeKind.BUILTIN -> "builtin"
                AttributeKind.COMPUTE -> "compute"
                AttributeKind.CONST -> "const"
                AttributeKind.DIAGNOSTIC -> "diagnostic"
                AttributeKind.FRAGMENT -> "fragment"
                AttributeKind.GROUP -> "group"
                AttributeKind.ID -> "id"
                AttributeKind.INTERPOLATE -> "interpolate"
                AttributeKind.INVARIANT -> "invariant"
                AttributeKind.LOCATION -> "location"
                AttributeKind.BLEND_SRC -> "blend_src"
                AttributeKind.MUST_USE -> "must_use"
                AttributeKind.SIZE -> "size"
                AttributeKind.VERTEX -> "vertex"
                AttributeKind.WORKGROUP_SIZE -> "workgroup_size"
                AttributeKind.INPUT_ATTACHMENT_INDEX -> "input_attachment_index"
            },
        )
    }

    fun emit(attributes: List<Attribute>) {
        attributes.forEach {
            emit(it)
        }
    }

    fun emit(attribute: Attribute) {
        with(attribute) {
            emitIndent()
            out.print("@")
            emit(kind)
            if (args.isNotEmpty()) {
                out.print("(")
                args.forEach {
                    emit(it)
                    out.print(", ")
                }
                out.print(")")
            }
            out.println()
        }
    }

    fun emit(assignmentOperator: AssignmentOperator) {
        when (assignmentOperator) {
            AssignmentOperator.EQUAL -> out.print("=")
            AssignmentOperator.PLUS_EQUAL -> out.print("+=")
            AssignmentOperator.MINUS_EQUAL -> out.print("-=")
            AssignmentOperator.TIMES_EQUAL -> out.print("*=")
            AssignmentOperator.DIVIDE_EQUAL -> out.print("/=")
            AssignmentOperator.MODULO_EQUAL -> out.print("%=")
            AssignmentOperator.AND_EQUAL -> out.print("&=")
            AssignmentOperator.OR_EQUAL -> out.print("|=")
            AssignmentOperator.XOR_EQUAL -> out.print("^=")
            AssignmentOperator.SHIFT_LEFT_EQUAL -> out.print("<<=")
            AssignmentOperator.SHIFT_RIGHT_EQUAL -> out.print(">>=")
        }
    }

    fun emit(operator: UnaryOperator) {
        when (operator) {
            UnaryOperator.MINUS -> out.print("-")
            UnaryOperator.LOGICAL_NOT -> out.print("!")
            UnaryOperator.BINARY_NOT -> out.print("~")
            UnaryOperator.DEREFERENCE -> out.print("*")
            UnaryOperator.ADDRESS_OF -> out.print("&")
        }
    }

    fun emit(operator: BinaryOperator) {
        when (operator) {
            BinaryOperator.SHORT_CIRCUIT_OR -> out.print("||")
            BinaryOperator.SHORT_CIRCUIT_AND -> out.print("&&")
            BinaryOperator.BINARY_OR -> out.print("|")
            BinaryOperator.BINARY_AND -> out.print("&")
            BinaryOperator.BINARY_XOR -> out.print("^")
            BinaryOperator.LESS_THAN -> out.print("<")
            BinaryOperator.GREATER_THAN -> out.print(">")
            BinaryOperator.LESS_THAN_EQUAL -> out.print("<=")
            BinaryOperator.GREATER_THAN_EQUAL -> out.print(">=")
            BinaryOperator.EQUAL_EQUAL -> out.print("==")
            BinaryOperator.NOT_EQUAL -> out.print("!=")
            BinaryOperator.SHIFT_LEFT -> out.print("<<")
            BinaryOperator.SHIFT_RIGHT -> out.print(">>")
            BinaryOperator.PLUS -> out.print("+")
            BinaryOperator.MINUS -> out.print("-")
            BinaryOperator.TIMES -> out.print("*")
            BinaryOperator.DIVIDE -> out.print("/")
            BinaryOperator.MODULO -> out.print("%")
        }
    }

    fun emit(lhsExpression: LhsExpression) {
        when (lhsExpression) {
            is LhsExpression.AddressOf -> {
                out.print("&")
                emit(lhsExpression.target)
            }
            is LhsExpression.ArrayIndex -> {
                emit(lhsExpression.target)
                out.print("[")
                emit(lhsExpression.indexExpression)
                out.print("]")
            }
            is LhsExpression.Identifier -> out.print(lhsExpression.name)
            is LhsExpression.MemberLookup -> {
                emit(lhsExpression.target)
                out.print(".${lhsExpression.member}")
            }
            is LhsExpression.Paren -> {
                out.print("(")
                emit(lhsExpression.target)
                out.print(")")
            }
            is LhsExpression.Dereference -> {
                out.print("*")
                emit(lhsExpression.target)
            }
        }
    }

    fun emit(expression: Expression) {
        when (expression) {
            is Expression.Binary -> {
                emit(expression.lhs)
                out.print(" ")
                emit(expression.operator)
                out.print(" ")
                emit(expression.rhs)
            }
            is Expression.Unary -> {
                emit(expression.operator)
                emit(expression.target)
            }
            is Expression.BoolLiteral -> out.print(expression.text)
            is Expression.FloatLiteral -> out.print(expression.text)
            is Expression.IntLiteral -> out.print(expression.text)
            is Expression.Identifier -> out.print(expression.name)
            is Expression.Paren -> {
                out.print("(")
                emit(expression.target)
                out.print(")")
            }
            is Expression.ValueConstructor -> {
                out.print(expression.typeName)
                if (expression is Expression.VectorValueConstructor) {
                    expression.elementType?.let {
                        out.print("<")
                        emit(it)
                        out.print(">")
                    }
                } else if (expression is Expression.MatrixValueConstructor) {
                    expression.elementType?.let {
                        out.print("<")
                        emit(it)
                        out.print(">")
                    }
                } else if (expression is Expression.ArrayValueConstructor) {
                    expression.elementType?.let {
                        out.print("<")
                        emit(it)
                        expression.elementCount?.let { itInner ->
                            out.print(", ")
                            emit(itInner)
                        }
                        out.print(">")
                    }
                }
                out.print("(")
                expression.args.forEach {
                    emit(it)
                    out.print(", ")
                }
                out.print(")")
            }
            is Expression.FunctionCall -> {
                out.print(expression.callee)
                expression.templateParameter?.let {
                    out.print("<")
                    emit(it)
                    out.print(">")
                }
                out.print("(")
                expression.args.forEach {
                    emit(it)
                    out.print(", ")
                }
                out.print(")")
            }
            is Expression.IndexLookup -> {
                emit(expression.target)
                out.print("[")
                emit(expression.index)
                out.print("]")
            }
            is Expression.MemberLookup -> {
                emit(expression.receiver)
                out.print(".${expression.memberName}")
            }
        }
    }

    fun emit(typeDecl: TypeDecl) {
        when (typeDecl) {
            is TypeDecl.ScalarTypeDecl -> out.print(typeDecl.name)
            is TypeDecl.MatrixTypeDecl -> {
                out.print(typeDecl.name)
                typeDecl.elementType?.let {
                    out.print("<")
                    emit(it)
                    out.print(">")
                }
            }
            is TypeDecl.VectorTypeDecl -> {
                out.print(typeDecl.name)
                typeDecl.elementType?.let {
                    out.print("<")
                    emit(it)
                    out.print(">")
                }
            }
            is TypeDecl.NamedType -> {
                out.print(typeDecl.name)
                if (typeDecl.templateArgs.isNotEmpty()) {
                    out.print("<")
                    typeDecl.templateArgs.forEach {
                        emit(it)
                        out.print(", ")
                    }
                    out.print(">")
                }
            }
            is TypeDecl.Array -> {
                out.print("array")
                typeDecl.elementType?.let {
                    out.print("<")
                    emit(it)
                    typeDecl.elementCount?.let { innerIt ->
                        out.print(", ")
                        emit(innerIt)
                    }
                    out.print(">")
                }
            }
            is TypeDecl.Pointer -> {
                out.print("ptr<")
                emit(typeDecl.addressSpace)
                out.print(", ")
                emit(typeDecl.targetType)
                typeDecl.accessMode?.let {
                    out.print(", ")
                    emit(it)
                }
                out.print(">")
            }
        }
    }

    fun emit(variableDecl: VariableDecl) {
        with(variableDecl) {
            out.print("var")
            addressSpace?.let {
                out.print("<")
                emit(it)
                accessMode?.let { itInner ->
                    out.print(", ")
                    emit(itInner)
                }
                out.print(">")
            }
            out.print(" $name")
            type?.let {
                out.print(" : ")
                emit(it)
            }
            initializer?.let {
                out.print(" = ")
                emit(it)
            }
        }
    }

    fun emit(
        assignmentStatement: Statement.Assignment,
        inForLoopHeader: Boolean,
    ) {
        with(assignmentStatement) {
            if (!inForLoopHeader) {
                emitIndent()
            }
            assignmentStatement.lhsExpression?.let {
                emit(it)
            } ?: run {
                out.print("_")
            }
            out.print(" ")
            emit(assignmentStatement.assignmentOperator)
            out.print(" ")
            emit(assignmentStatement.rhs)
            if (!inForLoopHeader) {
                out.print(";\n")
            }
        }
    }

    fun emit(compoundStatement: Statement.Compound) {
        with(compoundStatement) {
            emitIndent()
            out.print("{\n")
            increaseIndent()
            statements.forEach(::emit)
            decreaseIndent()
            emitIndent()
            out.print("}\n")
        }
    }

    fun emit(constAssertStatement: Statement.ConstAssert) {
        with(constAssertStatement) {
            out.print("const_assert ")
            emit(expression)
            out.print(";\n")
        }
    }

    fun emit(
        decrementStatement: Statement.Decrement,
        inForLoopHeader: Boolean,
    ) {
        with(decrementStatement) {
            if (!inForLoopHeader) {
                emitIndent()
            }
            emit(target)
            out.print("--")
            if (!inForLoopHeader) {
                out.print(";\n")
            }
        }
    }

    fun emit(forStatement: Statement.For) {
        with(forStatement) {
            emit(attributes)
            emitIndent()
            out.print("for (")
            init?.let { emit(it, true) }
            out.print("; ")
            condition?.let(::emit)
            out.print("; ")
            update?.let { emit(it, true) }
            out.print(")\n")
            emit(body)
        }
    }

    fun emit(
        functionCallStatement: Statement.FunctionCall,
        inForLoopHeader: Boolean,
    ) {
        with(functionCallStatement) {
            if (!inForLoopHeader) {
                emitIndent()
            }
            out.print(callee)
            out.print("(")
            args.forEach {
                emit(it)
                out.print(", ")
            }
            out.print(")")
            if (!inForLoopHeader) {
                out.print(";\n")
            }
        }
    }

    fun emit(ifStatement: Statement.If) {
        with(ifStatement) {
            emit(attributes)
            emitIndent()
            out.print("if ")
            emit(condition)
            out.print("\n")
            emit(thenBranch)
            elseBranch?.let {
                emitIndent()
                out.print("else\n")
                emit(it)
            }
        }
    }

    fun emit(
        incrementStatement: Statement.Increment,
        inForLoopHeader: Boolean,
    ) {
        with(incrementStatement) {
            if (!inForLoopHeader) {
                emitIndent()
            }
            emit(target)
            out.print("++")
            if (!inForLoopHeader) {
                out.print(";\n")
            }
        }
    }

    fun emit(loopStatement: Statement.Loop) {
        with(loopStatement) {
            emit(loopStatement.attributesAtStart)
            emitIndent()
            out.print("loop\n")
            emit(loopStatement.attributesBeforeBody)
            emitIndent()
            out.print("{\n")
            increaseIndent()
            statements.forEach {
                emit(it)
            }
            continuingStatement?.let {
                emitIndent()
                out.print("continuing\n")
                emit(it.attributes)
                emitIndent()
                out.print("{\n")
                increaseIndent()
                it.statements.forEach { statement ->
                    emit(statement)
                }
                it.breakIfExpr?.let { breakIfExpr ->
                    emitIndent()
                    out.print("break if ")
                    emit(breakIfExpr)
                    out.print(";\n")
                }
                decreaseIndent()
                emitIndent()
                out.print("}\n")
            }
            decreaseIndent()
            emitIndent()
            out.print("}\n")
        }
    }

    fun emit(returnStatement: Statement.Return) {
        with(returnStatement) {
            emitIndent()
            out.print("return")
            expr?.let {
                out.print(" ")
                emit(it)
            }
            out.print(";\n")
        }
    }

    fun emit(switchStatement: Statement.Switch) {
        with(switchStatement) {
            emit(attributesAtStart)
            emitIndent()
            out.print("switch ")
            emit(expression)
            out.print("\n")
            emit(attributesBeforeBody)
            emitIndent()
            out.print("{\n")
            increaseIndent()
            clauses.forEach {
                emitIndent()
                when (it.caseSelectors) {
                    CaseSelectors.DefaultAlone -> out.print("default\n")
                    is CaseSelectors.ExpressionsOrDefault -> {
                        out.print("case ")
                        (it.caseSelectors as CaseSelectors.ExpressionsOrDefault).expressions.forEach { expression ->
                            expression?.let { emit(expression) } ?: run {
                                out.print("default")
                            }
                            out.print(", ")
                        }
                        out.print("\n")
                    }
                }
                emit(it.compoundStatement)
            }
            decreaseIndent()
            emitIndent()
            out.print("}\n")
        }
    }

    fun emit(
        valueStatement: Statement.Value,
        inForLoopHeader: Boolean,
    ) {
        with(valueStatement) {
            if (!inForLoopHeader) {
                emitIndent()
            }
            out.print(
                if (isConst) {
                    "const"
                } else {
                    "let"
                },
            )
            out.print(" $name")
            type?.let {
                out.print(" : ")
                emit(it)
            }
            out.print(" = ")
            emit(initializer)
            if (!inForLoopHeader) {
                out.print(";\n")
            }
        }
    }

    fun emit(
        variableStatement: Statement.Variable,
        inForLoopHeader: Boolean,
    ) {
        with(variableStatement) {
            if (!inForLoopHeader) {
                emitIndent()
            }
            emit(variableDecl)
            if (!inForLoopHeader) {
                out.print(";\n")
            }
        }
    }

    fun emit(whileStatement: Statement.While) {
        with(whileStatement) {
            emit(attributes)
            emitIndent()
            out.print("while ")
            emit(expression)
            out.print("\n")
            emit(body)
        }
    }

    fun emit(
        statement: Statement,
        inForLoopHeader: Boolean = false,
    ) {
        when (statement) {
            is Statement.Assignment -> emit(statement, inForLoopHeader)
            is Statement.Break -> {
                emitIndent()
                out.print("break;\n")
            }
            is Statement.Compound -> emit(statement)
            is Statement.ConstAssert -> emit(statement)
            is Statement.Continue -> {
                emitIndent()
                out.print("continue;\n")
            }
            is Statement.Decrement -> emit(statement, inForLoopHeader)
            is Statement.Discard -> {
                emitIndent()
                out.print("discard;\n")
            }
            is Statement.Empty -> {
                emitIndent()
                out.print(";\n")
            }
            is Statement.For -> emit(statement)
            is Statement.FunctionCall -> emit(statement, inForLoopHeader)
            is Statement.If -> emit(statement)
            is Statement.Increment -> emit(statement, inForLoopHeader)
            is Statement.Loop -> emit(statement)
            is Statement.Return -> emit(statement)
            is Statement.Switch -> emit(statement)
            is Statement.Value -> emit(statement, inForLoopHeader)
            is Statement.Variable -> emit(statement, inForLoopHeader)
            is Statement.While -> emit(statement)
        }
    }

    fun emit(struct: GlobalDecl.Struct) {
        with(struct) {
            out.print("struct $name {\n")
            increaseIndent()
            for (member in members) {
                emit(member.attributes)
                emitIndent()
                out.print("${member.name} : ")
                emit(member.type)
                out.print(",\n")
            }
            decreaseIndent()
            out.print("}\n")
        }
    }

    fun emit(globalConstantDecl: GlobalDecl.Constant) {
        with(globalConstantDecl) {
            out.print("const $name ")
            type?.let {
                out.print(": ")
                emit(it)
            }
            out.print(" = ")
            emit(initializer)
            out.print(";\n")
        }
    }

    fun emit(globalOverrideDecl: GlobalDecl.Override) {
        with(globalOverrideDecl) {
            emit(attributes)
            out.print("override $name ")
            type?.let {
                out.print(": ")
                emit(it)
            }
            initializer?.let {
                out.print(" = ")
                emit(it)
            }
            out.print(";\n")
        }
    }

    fun emit(globalVarDecl: GlobalDecl.Variable) {
        with(globalVarDecl) {
            emit(attributes)
            emit(variableDecl)
            out.print(";\n")
        }
    }

    fun emit(functionDecl: GlobalDecl.Function) {
        with(functionDecl) {
            emit(attributes)
            out.print("fn $name(")
            if (parameters.isNotEmpty()) {
                out.print("\n")
                increaseIndent()
                parameters.forEach {
                    emit(it.attributes)
                    emitIndent()
                    out.print("${it.name} : ")
                    emit(it.typeDecl)
                    out.print(",\n")
                }
                decreaseIndent()
            }
            out.print(")")
            returnType?.let {
                out.print(" -> ")
                emit(returnType!!)
            }
            out.print("\n")
            emit(body)
        }
    }

    fun emit(decl: GlobalDecl) {
        when (decl) {
            is GlobalDecl.Constant -> emit(decl)
            is GlobalDecl.Override -> emit(decl)
            is GlobalDecl.Variable -> emit(decl)
            is GlobalDecl.Function -> emit(decl)
            is GlobalDecl.Struct -> emit(decl)
            is GlobalDecl.TypeAlias -> {
                out.print("alias ${decl.name} = ")
                emit(decl.type)
                out.print(";\n")
            }
            is GlobalDecl.ConstAssert -> {
                out.print("const_assert ")
                emit(decl.expression)
                out.println(";\n")
            }
            is GlobalDecl.Empty -> {
                out.println(";\n")
            }
        }
    }

    fun emit(tu: TranslationUnit) {
        tu.directives.forEach {
            out.print("${it.text}\n\n")
        }
        tu.globalDecls.forEachIndexed { index, decl ->
            emit(decl)
            if (index < tu.globalDecls.size - 1) {
                out.println()
            }
        }
    }
}
