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

    fun emit(assignmentStatement: Statement.Assignment) {
        with(assignmentStatement) {
            out.print("${placeholder.text};")
        }
    }

    fun emit(compoundStatement: Statement.Compound) {
        with(compoundStatement) {
            out.print("{\n")
            increaseIndent()
            statements.forEach(::emit)
            decreaseIndent()
            out.print("}\n")
        }
    }

    fun emit(constAssertStatement: Statement.ConstAssert) {
        with(constAssertStatement) {
            out.print("${placeholder.text};")
        }
    }

    fun emit(decrementStatement: Statement.Decrement) {
        with(decrementStatement) {
            out.print("${placeholder.text};")
        }
    }

    fun emit(forStatement: Statement.For) {
        with(forStatement) {
            out.print(placeholder.text)
        }
    }

    fun emit(functionCallStatement: Statement.FunctionCall) {
        with(functionCallStatement) {
            out.print("${placeholder.text};")
        }
    }

    fun emit(ifStatement: Statement.If) {
        with(ifStatement) {
            out.print(placeholder.text)
        }
    }

    fun emit(incrementStatement: Statement.Increment) {
        with(incrementStatement) {
            out.print("${placeholder.text};")
        }
    }

    fun emit(loopStatement: Statement.Loop) {
        with(loopStatement) {
            out.print(placeholder.text)
        }
    }

    fun emit(returnStatement: Statement.Return) {
        with(returnStatement) {
            out.print("return")
            returnStatement.expr?.let {
                out.print(" ${expr?.text}")
            }
            out.print(";")
        }
    }

    fun emit(switchStatement: Statement.Switch) {
        with(switchStatement) {
            out.print(placeholder.text)
        }
    }

    fun emit(valueStatement: Statement.Value) {
        with(valueStatement) {
            out.print("${placeholder.text};")
        }
    }

    fun emit(variableStatement: Statement.Variable) {
        with(variableStatement) {
            out.print("${placeholder.text};")
        }
    }

    fun emit(whileStatement: Statement.While) {
        with(whileStatement) {
            out.print(placeholder.text)
        }
    }

    fun emit(statement: Statement) {
        emitIndent()
        when (statement) {
            is Statement.Assignment -> emit(statement)
            is Statement.Break -> out.print("break;")
            is Statement.Compound -> emit(statement)
            is Statement.ConstAssert -> emit(statement)
            is Statement.Continue -> out.print("continue;")
            is Statement.Decrement -> emit(statement)
            is Statement.Discard -> out.print("discard;")
            is Statement.Empty -> out.print(";")
            is Statement.For -> emit(statement)
            is Statement.FunctionCall -> emit(statement)
            is Statement.If -> emit(statement)
            is Statement.Increment -> emit(statement)
            is Statement.Loop -> emit(statement)
            is Statement.Return -> emit(statement)
            is Statement.Switch -> emit(statement)
            is Statement.Value -> emit(statement)
            is Statement.Variable -> emit(statement)
            is Statement.While -> emit(statement)
        }
        out.println()
    }

    fun emit(struct: GlobalDecl.Struct) {
        with(struct) {
            out.print("struct $name {\n")
            increaseIndent()
            for (member in members) {
                for (attribute in member.attributes) {
                    emitIndent()
                    out.print(attribute.text)
                    out.println()
                }
                emitIndent()
                out.print("${member.name} : ${member.type.text},\n")
            }
            decreaseIndent()
            out.print("}\n")
        }
    }

    fun emit(globalVarDecl: GlobalDecl.Variable) {
        with(globalVarDecl) {
            for (attribute in attributes) {
                out.print("${attribute.text}\n")
            }
            out.print("var")
            if (addressSpace != null) {
                out.print("<${addressSpace!!.text}")
                if (accessMode != null) {
                    out.print(", ${accessMode!!.text}")
                }
                out.print(">")
            }
            out.print(" $name")
            if (type != null) {
                out.print(" : ${type!!.text}")
            }
            if (initializer != null) {
                out.print(" = ${initializer!!.text}")
            }
            out.print(";\n")
        }
    }

    fun emit(functionDecl: GlobalDecl.Function) {
        with(functionDecl) {
            for (attribute in attributes) {
                out.print("${attribute.text}\n")
            }
            out.print("fn $name(")
            out.print(")")
            returnType?.let {
                out.print(" -> ${returnType!!.text}")
            }
            out.print("\n")
            emit(body)
        }
    }

    fun emit(decl: GlobalDecl) {
        when (decl) {
            is GlobalDecl.Value -> {
                out.println("${decl.placeholder.text};")
            }
            is GlobalDecl.Variable -> emit(decl)
            is GlobalDecl.Function -> emit(decl)
            is GlobalDecl.Struct -> emit(decl)
            is GlobalDecl.TypeAlias -> {
                out.println("${decl.placeholder.text};")
            }
            is GlobalDecl.ConstAssert -> {
                out.println("${decl.placeholder.text};")
            }
            is GlobalDecl.Empty -> {
                out.println(";")
            }
        }
    }

    fun emit(tu: TranslationUnit) {
        tu.globalDecls.forEachIndexed { index, decl ->
            emit(decl)
            if (index < tu.globalDecls.size - 1) {
                out.println()
            }
        }
    }
}
