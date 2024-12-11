package com.wgslfuzz

class AstPrinter {

    fun print(decl: GlobalDecl) {
        when (decl) {
            is GlobalDecl.Value -> {
                println("constant ${decl.name}")
            }
            is GlobalDecl.Variable -> {
                println("variable ${decl.name}")
            }
            is GlobalDecl.Function -> {
                println("function ${decl.name}")
            }
            is GlobalDecl.Struct -> {
                println("struct ${decl.name}")
            }
            is GlobalDecl.TypeAlias -> {
                println("type alias ${decl.name}")
            }
            is GlobalDecl.ConstAssert -> {
                println("const assert")
            }
            is GlobalDecl.Empty -> {
                println(";")
            }
        }
    }

    fun print(tu: TranslationUnit) {
        for (decl in tu.globalDecls) {
            print(decl)
        }
    }
}
