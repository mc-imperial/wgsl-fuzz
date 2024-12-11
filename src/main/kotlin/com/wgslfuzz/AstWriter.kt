package com.wgslfuzz

import java.io.PrintStream

class AstWriter(
    val out: PrintStream = System.out,
) {
    fun print(decl: GlobalDecl) {
        when (decl) {
            is GlobalDecl.Value -> {
                out.println("constant ${decl.name}")
            }
            is GlobalDecl.Variable -> {
                out.println("variable ${decl.name}")
            }
            is GlobalDecl.Function -> {
                out.println("function ${decl.name}")
            }
            is GlobalDecl.Struct -> {
                out.println("struct ${decl.name}")
            }
            is GlobalDecl.TypeAlias -> {
                out.println("type alias ${decl.name}")
            }
            is GlobalDecl.ConstAssert -> {
                out.println("const assert")
            }
            is GlobalDecl.Empty -> {
                out.println(";")
            }
        }
    }

    fun print(tu: TranslationUnit) {
        for (decl in tu.globalDecls) {
            out.print(decl)
        }
    }
}
