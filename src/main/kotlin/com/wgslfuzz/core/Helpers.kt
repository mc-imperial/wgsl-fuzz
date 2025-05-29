package com.wgslfuzz.core

// This file is a suitable place for helper methods that are generally useful but that do not have a more obvious home.

/**
 * A shader module declares "uniform" variables to allow data to be passed into a pipeline invocation. Uniforms can be
 * dynamic between pipeline invocations, but are constant during a particular pipeline invocation.
 *
 * On the assumption that a translation unit declares a uniform bound to group [group] and binding [binding], this
 * function yields that associated global variable declaration.
 *
 * @receiver the translation unit being queried
 * @param group the group associated with the uniform declaration
 * @param binding the binding associated with the uniform declaration
 * @return the global variable declaration for the uniform
 * @throws [NoSuchElementException] if no matching global variable exists
 */
fun TranslationUnit.getUniformDeclaration(
    group: Int,
    binding: Int,
): GlobalDecl.Variable =
    globalDecls.filterIsInstance<GlobalDecl.Variable>().first {
        it.hasIntegerAttribute(AttributeKind.GROUP, group) &&
            it.hasIntegerAttribute(AttributeKind.BINDING, binding)
    }

private fun GlobalDecl.Variable.hasIntegerAttribute(
    kind: AttributeKind,
    value: Int,
) = (attributes.first { attribute -> attribute.kind == kind }.args[0] as Expression.IntLiteral).text.toInt() == value
