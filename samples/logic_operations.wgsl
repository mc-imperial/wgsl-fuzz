/*
 * Copyright 2025 The wgsl-fuzz Project Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Reimplemenation in WGSL of the GLSL shader from https://github.com/google/graphicsfuzz
 * Original shader: https://github.com/google/graphicsfuzz/blob/master/shaders/src/main/glsl/samples/320es/stable_logicops.frag
 */

 @group(0) @binding(0) var<uniform> pointFour: f32;
 @group(0) @binding(1) var<uniform> pointSix: f32;
 @group(0) @binding(2) var<uniform> num256: i32;

fn toNumber(b: bool) -> f32 {
    if (b) {
        return 1;
    }
    return 0;
}

@vertex
fn vertexMain(@location(0) pos: vec2f) -> @builtin(position) vec4f {
  return vec4f(pos, 0, 1);
}

@fragment
fn fragmentMain(@builtin(position) gl_FragCoord: vec4f) -> @location(0) vec4f {
    if (pointFour != 0.4 || pointSix != 0.6 || num256 != 256) {
      return vec4(0, 1, 0, 1);
    }

    let coord = gl_FragCoord.xy * (1.0 / f32(num256));

    if (coord.x > pointFour) {
        if (coord.y < pointSix) {
            let icoord = vec2<u32>(
              ((coord - vec2(pointFour, 0.0)) * vec2(1.0 / pointFour, 1.0 / pointSix)) * f32(num256)
            );

            let res1 = u32(((icoord.x * icoord.y) >> (icoord.x & u32(31))) & u32(0xffffffff));
            let res2 = u32(((icoord.x * icoord.y) << (icoord.x & u32(31))) & u32(0xffffffff));

            var res3: u32 = 0;
            if ((res2 & u32(0x80000000)) != u32(0)) {
                res3 = 1;
            }

            var res4: u32 = 0;
            if ((res1 & u32(1)) != 0) {
                res4 = 1;
            }

            let res = f32(res3 ^ res4);

            return vec4(res, res, res, 1);
        } else {
            let icoord = vec2<i32>(
              ((coord - vec2(pointFour, pointSix)) * vec2(1.0 / pointFour, 1.0 / pointFour)) * f32(num256)
            );

            let res3 = i32(((icoord.x >> 5) & 1) ^ ((icoord.y & 32) >> 5));
            let res2 = i32(((icoord.y * icoord.y) >> 10) & 1);
            let res1 = i32(((icoord.x * icoord.y) >> 9) & 1);

            return vec4(f32(res1 ^ res2), f32(res2 & res3), f32(res1 | res3), 1);
        }
    } else {
        let icoord = vec2<i32>(((coord - vec2(pointFour, 0.0)) * vec2(1.0 / pointSix, 1.0)) * f32(num256));

        let v = i32((icoord.x ^ icoord.y) * icoord.y);

        let res1 = ((v >> 10) & 1) != 0;
        let res2 = ((v >> 11) & 4) != 0;
        let res3 = ((v >> 12) & 8) != 0;

        return vec4(toNumber(res1), toNumber(res2), toNumber(res3), 1);
    }
}
