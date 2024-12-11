package com.wgslfuzz

class TranslationUnit {
    val globalDecls = mutableListOf<GlobalDecl>()

}

sealed interface GlobalDecl {
    class Value(var name: String) : GlobalDecl

    class Variable(var name: String) : GlobalDecl

    class Function(var name: String) : GlobalDecl

    class Struct(var name: String) : GlobalDecl

    class TypeAlias(var name: String) : GlobalDecl

    class ConstAssert : GlobalDecl

    class Empty : GlobalDecl
}
