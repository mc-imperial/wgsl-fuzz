package com.wgslfuzz.core

fun getUniformDeclaration(
    tu: TranslationUnit,
    group: Int,
    binding: Int,
): GlobalDecl.Variable {
    val uniformDeclaration =
        tu.globalDecls.filterIsInstance<GlobalDecl.Variable>().first {
            (
                it.attributes
                    .first { attribute ->
                        attribute.kind == AttributeKind.GROUP
                    }.args[0] as Expression.IntLiteral
            ).text.toInt() == group &&
                (
                    it.attributes
                        .first { attribute ->
                            attribute.kind == AttributeKind.BINDING
                        }.args[0] as Expression.IntLiteral
                ).text.toInt() == binding
        }
    return uniformDeclaration
}
