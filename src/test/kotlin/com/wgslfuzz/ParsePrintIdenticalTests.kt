package com.wgslfuzz

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class ParsePrintIdenticalTests {
    @Test
    fun graphicsFuzzTest() {
        val input =
            """
            struct buf0 {
              injected : i32,
            }

            @group(0, )
            @binding(0, )
            var<uniform> x_9 : buf0;

            var<private> x_GLF_color : vec4<f32>;

            fn main_1()
            {
              var idx : i32;
              var m43 : mat4x3<f32>;
              var ll_1 : i32;
              var GLF_live6rows : i32;
              var z : i32;
              var ll_2 : i32;
              var ctr : i32;
              var tempm43 : mat4x3<f32>;
              var ll_3 : i32;
              var c : i32;
              var d : i32;
              var GLF_live6sums : array<f32, 9u>;
              idx = 0;
              m43 = mat4x3<f32>(vec3<f32>(1.0, 0.0, 0.0, ), vec3<f32>(0.0, 1.0, 0.0, ), vec3<f32>(0.0, 0.0, 1.0, ), vec3<f32>(0.0, 0.0, 0.0, ), );
              ll_1 = 0;
              GLF_live6rows = 2;
              loop
              {
                let x_18 : i32 = ll_1;
                let x_19 : i32 = x_9.injected;
                if ((x_18 >= x_19)) {
                  x_GLF_color = vec4<f32>(1.0, 0.0, 0.0, 1.0, );
                  break;
                }
                let x_20 : i32 = ll_1;
                ll_1 = (x_20 + 1);
                let x_22 : i32 = x_9.injected;
                z = x_22;
                ll_2 = 0;
                ctr = 0;
                loop
                {
                  let x_23 : i32 = ctr;
                  if ((x_23 < 1)) {
                  } else {
                    break;
                  }
                  let x_24 : i32 = ll_2;
                  let x_25 : i32 = x_9.injected;
                  if ((x_24 >= x_25)) {
                    break;
                  }
                  let x_26 : i32 = ll_2;
                  ll_2 = (x_26 + 1);
                  let x_98 : mat4x3<f32> = m43;
                  tempm43 = x_98;
                  ll_3 = 0;
                  c = 0;
                  loop
                  {
                    let x_28 : i32 = z;
                    if ((1 < x_28)) {
                    } else {
                      break;
                    }
                    d = 0;
                    let x_29 : i32 = c;
                    let x_30 : i32 = c;
                    let x_31 : i32 = c;
                    let x_32 : i32 = d;
                    let x_33 : i32 = d;
                    let x_34 : i32 = d;
                    tempm43[select(0, x_31, ((x_29 >= 0) & (x_30 < 4)), )][select(0, x_34, ((x_32 >= 0) & (x_33 < 3)), )] = 1.0;
                    continuing
                    {
                      let x_35 : i32 = c;
                      c = (x_35 + 1);
                    }
                  }
                  let x_37 : i32 = idx;
                  let x_38 : i32 = idx;
                  let x_39 : i32 = idx;
                  let x_117 : i32 = select(0, x_39, ((x_37 >= 0) & (x_38 < 9)));
                  let x_40 : i32 = ctr;
                  let x_119 : f32 = m43[x_40].y;
                  let x_121 : f32 = GLF_live6sums[x_117];
                  GLF_live6sums[x_117] = (x_121 + x_119);
                  continuing
                  {
                    let x_41 : i32 = ctr;
                    ctr = (x_41 + 1);
                  }
                }
                let x_43 : i32 = idx;
                idx = (x_43 + 1);
              }
              return;
            }

            struct main_out {
              @location(0, )
              x_GLF_color_1 : vec4<f32>,
            }

            @fragment
            fn main() -> main_out
            {
              main_1();
              return main_out(x_GLF_color, );
            }

            """.trimIndent()

        checkParsePrintIdentical(input)
    }

    @Test
    fun whileTest() {
        val input =
            """
            fn f() -> i32
            {
              var i : i32;
              while (i < 4)
              {
                i = i + 1;
              }
              return i;
            }

            """.trimIndent()
        checkParsePrintIdentical(input)
    }

    @Test
    fun continueInSwitchWithBreakif() {
        val input =
            """
            @compute
            @workgroup_size(1, )
            fn f()
            {
              var i : i32 = 0;
              loop
              {
                switch (i)
                {
                  case 0, 
                  {
                    continue;
                  }
                  default
                  {
                    break;
                  }
                }
                continuing
                {
                  i = i + 1;
                  break if i >= 4;
                }
              }
            }

            """.trimIndent()
        checkParsePrintIdentical(input)
    }

    @Test
    fun aliasTest() {
        val input =
            """
            struct S {
              m : T,
            }

            alias T = i32;

            @fragment
            fn f()
            {
              var v : S;
            }

            """.trimIndent()

        checkParsePrintIdentical(input)
    }

    @Test
    fun dualSourceBlendingTest() {
        val input =
            """
            enable dual_source_blending;

            struct FragInput {
              @location(0, )
              a : vec4<f32>,
              @location(1, )
              b : vec4<f32>,
            }

            struct FragOutput {
              @location(0, )
              @blend_src(0, )
              color : vec4<f32>,
              @location(0, )
              @blend_src(1, )
              blend : vec4<f32>,
            }

            @fragment
            fn frag_main(
              in : FragInput,
            ) -> FragOutput
            {
              var output : FragOutput;
              output.color = in.a;
              output.blend = in.b;
              return output;
            }

            """.trimIndent()

        checkParsePrintIdentical(input)
    }

    private fun checkParsePrintIdentical(input: String) {
        val errorListener = LoggingParseErrorListener()

        val tu =
            parseFromString(
                wgslString = input,
                errorListener = errorListener,
            )
        val outputStream = ByteArrayOutputStream()
        AstWriter(
            out = PrintStream(outputStream),
            indentValue = 2,
        ).emit(tu)
        assertEquals(input, outputStream.toString())
    }
}
