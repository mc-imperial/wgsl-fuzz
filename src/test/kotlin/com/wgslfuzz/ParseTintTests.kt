package com.wgslfuzz

import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

class ParseTintTests {
    // // This test, commented out by default, is useful if you want to check a specific WGSL test case during debugging.
    // @Test
    // fun checkSpecificWgslTest() {
    //    checkWgslTest("/home/afd/temp/shader.wgsl")
    // }

    @Test
    fun parseTintTests() {
        // This long-running test checks that all the Tint tests that ship with Dawn can be parsed successfully.

        // These are outputs that have expected diagnostic messages and thus cannot be parsed.
        val diagnosticChecks =
            setOf(
                "external/dawn/test/tint/diagnostic_filtering/compound_statement_attribute.wgsl.expected.wgsl",
                "external/dawn/test/tint/diagnostic_filtering/function_attribute.wgsl.expected.wgsl",
                "external/dawn/test/tint/diagnostic_filtering/case_body_attribute.wgsl.expected.wgsl",
                "external/dawn/test/tint/diagnostic_filtering/switch_statement_attribute.wgsl.expected.wgsl",
                "external/dawn/test/tint/diagnostic_filtering/if_body_attribute.wgsl.expected.wgsl",
                "external/dawn/test/tint/diagnostic_filtering/directive.wgsl.expected.wgsl",
                "external/dawn/test/tint/diagnostic_filtering/function_body_attribute.wgsl.expected.wgsl",
                "external/dawn/test/tint/diagnostic_filtering/while_loop_body_attribute.wgsl.expected.wgsl",
                "external/dawn/test/tint/diagnostic_filtering/if_statement_attribute.wgsl.expected.wgsl",
                "external/dawn/test/tint/diagnostic_filtering/loop_body_attribute.wgsl.expected.wgsl",
                "external/dawn/test/tint/diagnostic_filtering/loop_continuing_attribute.wgsl.expected.wgsl",
                "external/dawn/test/tint/diagnostic_filtering/switch_body_attribute.wgsl.expected.wgsl",
                "external/dawn/test/tint/diagnostic_filtering/while_loop_attribute.wgsl.expected.wgsl",
                "external/dawn/test/tint/diagnostic_filtering/default_case_body_attribute.wgsl.expected.wgsl",
                "external/dawn/test/tint/diagnostic_filtering/for_loop_body_attribute.wgsl.expected.wgsl",
                "external/dawn/test/tint/diagnostic_filtering/else_if_body_attribute.wgsl.expected.wgsl",
                "external/dawn/test/tint/diagnostic_filtering/for_loop_attribute.wgsl.expected.wgsl",
                "external/dawn/test/tint/diagnostic_filtering/else_body_attribute.wgsl.expected.wgsl",
                "external/dawn/test/tint/diagnostic_filtering/loop_attribute.wgsl.expected.wgsl",
                "external/dawn/test/tint/bug/tint/2202.wgsl.expected.wgsl",
                "external/dawn/test/tint/bug/tint/2201.wgsl.expected.wgsl",
                "external/dawn/test/tint/bug/tint/1474-b.wgsl.expected.wgsl",
                "external/dawn/test/tint/bug/chromium/1395241.wgsl.expected.wgsl",
            ).map { it.replace("/", File.separator) }

        val tooHard =
            setOf(
                // This has an expression "b--b", which I think should be parsed as "b - - b"
                "external/dawn/test/tint/statements/decrement/split.wgsl",
                // This uses "mat2x2" as an identifier, which WGSL does allow but our grammar does not.
                "external/dawn/test/tint/bug/chromium/40943165.wgsl",
                "external/dawn/test/tint/bug/chromium/40943165.wgsl.expected.wgsl",
                // These feature array sizes based on an override declaration with no initializer.
                // TODO: These could be supported by having a feature for specifying the values of overrides.
                "external/dawn/test/tint/bug/tint/1737.wgsl",
                "external/dawn/test/tint/bug/tint/1737.wgsl.expected.wgsl",
                "external/dawn/test/tint/builtins/workgroupUniformLoad/array_overridable_count_aliased.wgsl.expected.wgsl",
                "external/dawn/test/tint/builtins/workgroupUniformLoad/array_overridable_count_aliased.wgsl",
                "external/dawn/test/tint/builtins/workgroupUniformLoad/array_overridable_count.wgsl.expected.wgsl",
                "external/dawn/test/tint/builtins/workgroupUniformLoad/array_overridable_count.wgsl",
            ).map { it.replace("/", File.separator) }
        val skipList = diagnosticChecks + tooHard

        var counter = 0

        File("external/dawn/test/tint").walk().forEach {
            if (it.extension != "wgsl" || it.path in skipList) {
                return@forEach
            }
            val text = File(it.path).readText()
            if (text.contains("enable chromium_experimental") ||
                text.contains("chromium_internal_graphite") ||
                text.contains("chromium_internal_input_attachments") ||
                text.contains("enable subgroups")
            ) {
                return@forEach
            }
            if (counter.mod(1000) == 0) {
                println("Done $counter tests")
            }
            counter++
            checkWgslTest(it.path)
        }
    }

    private fun checkWgslTest(wgslTestFilename: String) {
        val errorListener = LoggingParseErrorListener()
        val byteOutputStream = ByteArrayOutputStream()
        try {
            val tu = parseFromFile(filename = wgslTestFilename, errorListener = errorListener, timeoutMilliseconds = 100000)
            resolve(tu)
            val tuCloned = tu.clone()
            AstWriter(PrintStream(byteOutputStream)).emit(tuCloned)
            parseFromString(wgslString = byteOutputStream.toString(), errorListener = errorListener, timeoutMilliseconds = 100000)
        } catch (e: Exception) {
            println(wgslTestFilename)
            println(errorListener.loggedMessages)
            println(byteOutputStream.toString())
            throw e
        }
    }
}
