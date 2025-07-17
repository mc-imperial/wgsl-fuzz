package com.wgslfuzz.uniformityanalysis

import com.wgslfuzz.analysis.desugar
import com.wgslfuzz.analysis.reorderFunctions
import com.wgslfuzz.core.AstWriter
import com.wgslfuzz.core.LoggingParseErrorListener
import com.wgslfuzz.core.ResolvedEnvironment
import com.wgslfuzz.core.parseFromFile
import com.wgslfuzz.core.resolve
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.File
import java.io.PrintStream
import java.lang.IllegalArgumentException
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class UniformityWGSLAnalysisCTSTests {
    fun checkNonUniform(shader: String) {
        val tu = parseFromFile(shader, LoggingParseErrorListener()).desugar().reorderFunctions()
        AstWriter(
            out = System.out,
            indentValue = 2,
        ).emit(tu)
        val environment = resolve(tu)

        val message =
            assertFailsWith<kotlin.IllegalArgumentException> {
                runWGSLUniformityGraphAnalysis(tu, environment)
            }
        assertContains("Uniformity Error", message.message.toString())
    }

    fun checkUniform(shader: String) {
        val tu = parseFromFile(shader, LoggingParseErrorListener()).desugar().reorderFunctions()
        val environment = resolve(tu)
        assertDoesNotThrow { runWGSLUniformityGraphAnalysis(tu, environment) }
    }

    @Test
    fun checkSpecificCTSTest() {
        checkNonUniform("external/cts-uniformity-tests/non-uniform/shader_76.wgsl")
    }

    @TestFactory
    fun analyseTest(): List<DynamicTest> {
        // TODO(JLJ): This is hacked together, make it far nicer

        val incorrect =
            setOf(
                // These are ill typed, they use a f32 to index an array
                "non-uniform/shader_1045.wgsl",
                "non-uniform/shader_2722.wgsl",
                "non-uniform/shader_2708.wgsl",
                "non-uniform/shader_2714.wgsl",
                "non-uniform/shader_5592.wgsl",
                "non-uniform/shader_5577.wgsl",
                "non-uniform/shader_3697.wgsl",
                "non-uniform/shader_4631.wgsl",
                "non-uniform/shader_3696.wgsl",
                "non-uniform/shader_4640.wgsl",
                "non-uniform/shader_2747.wgsl",
                "non-uniform/shader_1043.wgsl",
                "non-uniform/shader_4613.wgsl",
                "non-uniform/shader_4610.wgsl",
                "non-uniform/shader_2746.wgsl",
                "non-uniform/shader_5595.wgsl",
                "non-uniform/shader_2724.wgsl",
                "non-uniform/shader_2734.wgsl",
                "non-uniform/shader_4605.wgsl",
                "non-uniform/shader_5556.wgsl",
                "non-uniform/shader_4627.wgsl",
                "non-uniform/shader_1056.wgsl",
                "non-uniform/shader_1055.wgsl",
                "non-uniform/shader_3659.wgsl",
                "non-uniform/shader_2725.wgsl",
                "non-uniform/shader_5579.wgsl",
                "non-uniform/shader_1044.wgsl",
                "non-uniform/shader_4632.wgsl",
                "non-uniform/shader_3656.wgsl",
                "non-uniform/shader_2740.wgsl",
                "non-uniform/shader_3689.wgsl",
                "non-uniform/shader_5588.wgsl",
                "non-uniform/shader_4644.wgsl",
                "non-uniform/shader_2184.wgsl",
                "non-uniform/shader_5571.wgsl",
                "non-uniform/shader_4628.wgsl",
                "non-uniform/shader_1057.wgsl",
                "non-uniform/shader_5566.wgsl",
                "non-uniform/shader_5573.wgsl",
                "non-uniform/shader_2719.wgsl",
                "non-uniform/shader_1477.wgsl",
                "non-uniform/shader_2698.wgsl",
                "non-uniform/shader_5555.wgsl",
                "non-uniform/shader_5584.wgsl",
                "non-uniform/shader_442.wgsl",
                "non-uniform/shader_1480.wgsl",
                "non-uniform/shader_3695.wgsl",
                "non-uniform/shader_2730.wgsl",
                "non-uniform/shader_2706.wgsl",
                "non-uniform/shader_3690.wgsl",
                "non-uniform/shader_3674.wgsl",
                "non-uniform/shader_4647.wgsl",
                "non-uniform/shader_5561.wgsl",
                "non-uniform/shader_3671.wgsl",
                "non-uniform/shader_2182.wgsl",
                "non-uniform/shader_5569.wgsl",
                "non-uniform/shader_5560.wgsl",
                "non-uniform/shader_4629.wgsl",
                "non-uniform/shader_5580.wgsl",
                "non-uniform/shader_1484.wgsl",
                "non-uniform/shader_4609.wgsl",
                "non-uniform/shader_1048.wgsl",
                "non-uniform/shader_3687.wgsl",
                "non-uniform/shader_4624.wgsl",
                "non-uniform/shader_2742.wgsl",
                "non-uniform/shader_444.wgsl",
                "non-uniform/shader_1042.wgsl",
                "non-uniform/shader_2729.wgsl",
                "non-uniform/shader_5568.wgsl",
                "non-uniform/shader_2739.wgsl",
                "non-uniform/shader_2717.wgsl",
                "non-uniform/shader_440.wgsl",
                "non-uniform/shader_4617.wgsl",
                "non-uniform/shader_4634.wgsl",
                "non-uniform/shader_3651.wgsl",
                "non-uniform/shader_1052.wgsl",
                "non-uniform/shader_4645.wgsl",
                "non-uniform/shader_5551.wgsl",
                "non-uniform/shader_441.wgsl",
                "non-uniform/shader_4633.wgsl",
                "non-uniform/shader_4611.wgsl",
                "non-uniform/shader_5549.wgsl",
                "non-uniform/shader_2720.wgsl",
                "non-uniform/shader_450.wgsl",
                "non-uniform/shader_3649.wgsl",
                "non-uniform/shader_1047.wgsl",
                "non-uniform/shader_5553.wgsl",
                "non-uniform/shader_3685.wgsl",
                "non-uniform/shader_4603.wgsl",
                "non-uniform/shader_447.wgsl",
                "non-uniform/shader_2178.wgsl",
                "non-uniform/shader_4602.wgsl",
                "non-uniform/shader_436.wgsl",
                "non-uniform/shader_5589.wgsl",
                "non-uniform/shader_5554.wgsl",
                "non-uniform/shader_5583.wgsl",
                "non-uniform/shader_1481.wgsl",
                "non-uniform/shader_1482.wgsl",
                "non-uniform/shader_2745.wgsl",
                "non-uniform/shader_4643.wgsl",
                "non-uniform/shader_4604.wgsl",
                "non-uniform/shader_2712.wgsl",
                "non-uniform/shader_2180.wgsl",
                "non-uniform/shader_4635.wgsl",
                "non-uniform/shader_2718.wgsl",
                "non-uniform/shader_4612.wgsl",
                "non-uniform/shader_2188.wgsl",
                "non-uniform/shader_3661.wgsl",
                "non-uniform/shader_1488.wgsl",
                "non-uniform/shader_5596.wgsl",
                "non-uniform/shader_448.wgsl",
                "non-uniform/shader_5574.wgsl",
                "non-uniform/shader_2191.wgsl",
                "non-uniform/shader_1053.wgsl",
                "non-uniform/shader_2711.wgsl",
                "non-uniform/shader_3670.wgsl",
                "non-uniform/shader_2727.wgsl",
                "non-uniform/shader_3653.wgsl",
                "non-uniform/shader_2733.wgsl",
                "non-uniform/shader_1050.wgsl",
                "non-uniform/shader_435.wgsl",
                "non-uniform/shader_443.wgsl",
                "non-uniform/shader_3679.wgsl",
                "non-uniform/shader_2185.wgsl",
                "non-uniform/shader_2738.wgsl",
                "non-uniform/shader_4608.wgsl",
                "non-uniform/shader_2723.wgsl",
                "non-uniform/shader_3658.wgsl",
                "non-uniform/shader_3663.wgsl",
                "non-uniform/shader_3684.wgsl",
                "non-uniform/shader_5548.wgsl",
                "non-uniform/shader_2721.wgsl",
                "non-uniform/shader_5590.wgsl",
                "non-uniform/shader_4630.wgsl",
                "non-uniform/shader_3683.wgsl",
                "non-uniform/shader_2707.wgsl",
                "non-uniform/shader_4616.wgsl",
                "non-uniform/shader_3682.wgsl",
                "non-uniform/shader_5552.wgsl",
                "non-uniform/shader_2699.wgsl",
                "non-uniform/shader_1487.wgsl",
                "non-uniform/shader_2705.wgsl",
                "non-uniform/shader_5594.wgsl",
                "non-uniform/shader_5564.wgsl",
                "non-uniform/shader_5559.wgsl",
                "non-uniform/shader_2732.wgsl",
                "non-uniform/shader_1051.wgsl",
                "non-uniform/shader_4620.wgsl",
                "non-uniform/shader_4599.wgsl",
                "non-uniform/shader_2179.wgsl",
                "non-uniform/shader_4641.wgsl",
                "non-uniform/shader_5572.wgsl",
                "non-uniform/shader_3686.wgsl",
                "non-uniform/shader_1489.wgsl",
                "non-uniform/shader_2193.wgsl",
                "non-uniform/shader_439.wgsl",
                "non-uniform/shader_3693.wgsl",
                "non-uniform/shader_5586.wgsl",
                "non-uniform/shader_2703.wgsl",
                "non-uniform/shader_2736.wgsl",
                "non-uniform/shader_2709.wgsl",
                "non-uniform/shader_2737.wgsl",
                "non-uniform/shader_4600.wgsl",
                "non-uniform/shader_5567.wgsl",
                "non-uniform/shader_3650.wgsl",
                "non-uniform/shader_4637.wgsl",
                "non-uniform/shader_5578.wgsl",
                "non-uniform/shader_4625.wgsl",
                "non-uniform/shader_3676.wgsl",
                "non-uniform/shader_4638.wgsl",
                "non-uniform/shader_3655.wgsl",
                "non-uniform/shader_3680.wgsl",
                "non-uniform/shader_3668.wgsl",
                "non-uniform/shader_4621.wgsl",
                "non-uniform/shader_2744.wgsl",
                "non-uniform/shader_2192.wgsl",
                "non-uniform/shader_3657.wgsl",
                "non-uniform/shader_3673.wgsl",
                "non-uniform/shader_4623.wgsl",
                "non-uniform/shader_1475.wgsl",
                "non-uniform/shader_2735.wgsl",
                "non-uniform/shader_5562.wgsl",
                "non-uniform/shader_4636.wgsl",
                "non-uniform/shader_2741.wgsl",
                "non-uniform/shader_3652.wgsl",
                "non-uniform/shader_4606.wgsl",
                "non-uniform/shader_5597.wgsl",
                "non-uniform/shader_3665.wgsl",
                "non-uniform/shader_1474.wgsl",
                "non-uniform/shader_3666.wgsl",
                "non-uniform/shader_5585.wgsl",
                "non-uniform/shader_2187.wgsl",
                "non-uniform/shader_5563.wgsl",
                "non-uniform/shader_4614.wgsl",
                "non-uniform/shader_4626.wgsl",
                "non-uniform/shader_1485.wgsl",
                "non-uniform/shader_1483.wgsl",
                "non-uniform/shader_2189.wgsl",
                "non-uniform/shader_2190.wgsl",
                "non-uniform/shader_2704.wgsl",
                "non-uniform/shader_2181.wgsl",
                "non-uniform/shader_4615.wgsl",
                "non-uniform/shader_3694.wgsl",
                "non-uniform/shader_2702.wgsl",
                "non-uniform/shader_449.wgsl",
                "non-uniform/shader_3662.wgsl",
                "non-uniform/shader_5581.wgsl",
                "non-uniform/shader_5587.wgsl",
                "non-uniform/shader_5576.wgsl",
                "non-uniform/shader_4607.wgsl",
                "non-uniform/shader_3691.wgsl",
                "non-uniform/shader_3669.wgsl",
                "non-uniform/shader_4642.wgsl",
                "non-uniform/shader_5550.wgsl",
                "non-uniform/shader_1054.wgsl",
                "non-uniform/shader_3654.wgsl",
                "non-uniform/shader_3664.wgsl",
                "non-uniform/shader_437.wgsl",
                "non-uniform/shader_2728.wgsl",
                "non-uniform/shader_3672.wgsl",
                "non-uniform/shader_2743.wgsl",
                "non-uniform/shader_2701.wgsl",
                "non-uniform/shader_5557.wgsl",
                "non-uniform/shader_5591.wgsl",
                "non-uniform/shader_4618.wgsl",
                "non-uniform/shader_1049.wgsl",
                "non-uniform/shader_3648.wgsl",
                "non-uniform/shader_4622.wgsl",
                "non-uniform/shader_1476.wgsl",
                "non-uniform/shader_2715.wgsl",
                "non-uniform/shader_5582.wgsl",
                "non-uniform/shader_4639.wgsl",
                "non-uniform/shader_446.wgsl",
                "non-uniform/shader_4598.wgsl",
                "non-uniform/shader_2186.wgsl",
                "non-uniform/shader_3688.wgsl",
                "non-uniform/shader_3675.wgsl",
                "non-uniform/shader_1478.wgsl",
                "non-uniform/shader_3692.wgsl",
                "non-uniform/shader_3678.wgsl",
                "non-uniform/shader_3660.wgsl",
                "non-uniform/shader_445.wgsl",
                "non-uniform/shader_5570.wgsl",
                "non-uniform/shader_4646.wgsl",
                "non-uniform/shader_3677.wgsl",
                "non-uniform/shader_3681.wgsl",
                "non-uniform/shader_5593.wgsl",
                "non-uniform/shader_4619.wgsl",
                "non-uniform/shader_2716.wgsl",
                "non-uniform/shader_3667.wgsl",
                "non-uniform/shader_2731.wgsl",
                "non-uniform/shader_1046.wgsl",
                "non-uniform/shader_2183.wgsl",
                "non-uniform/shader_5565.wgsl",
                "non-uniform/shader_2713.wgsl",
                "non-uniform/shader_5575.wgsl",
                "non-uniform/shader_2700.wgsl",
                "non-uniform/shader_2726.wgsl",
                "non-uniform/shader_1479.wgsl",
                "non-uniform/shader_438.wgsl",
                "non-uniform/shader_2710.wgsl",
                "non-uniform/shader_4601.wgsl",
                "non-uniform/shader_5558.wgsl",
                "non-uniform/shader_1486.wgsl",

            ).map { "external/cts-uniformity-tests/$it".replace("/", File.separator) }

        for (test in incorrect) {
            val tu = parseFromFile(File(test).path, LoggingParseErrorListener()).desugar().reorderFunctions()
            val except = assertFailsWith<IllegalArgumentException> {
                resolve(tu)
            }
            assert(except.message != null)
            assertContains("Array index expression must be of type i32 or u32.", except.message!!)
        }

        var counter = 0
        val result = mutableListOf<DynamicTest>()

        File("external/cts-uniformity-tests").walk().forEach {
            if (it.extension != "wgsl" || it.path in incorrect) {
                return@forEach
            }
            if (it.path.contains("non-uniform")) {
                result.add(dynamicTest(it.path) { checkNonUniform(it.path) })
            } else {
                result.add(dynamicTest(it.path) { checkUniform(it.path) })
            }
            if (counter.mod(1000) == 0) {
                println("Done $counter tests")
            }
            counter++
        }

        return result
    }
}
