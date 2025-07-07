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
    let coord = gl_FragCoord.xy * (1.0 / 256.0);

    if (coord.x > 0.4) {
        if (coord.y < 0.6) {
            let icoord = vec2<u32>(((coord - vec2(0.4, 0.0)) * vec2(1.0 / 0.4, 1.0 / 0.6)) * 256.0);

            let res1 = u32(((icoord.x * icoord.y) >> (icoord.x & u32(31))) & u32(0xffffffff));
            let res2 = u32(((icoord.x * icoord.y) >> (icoord.x & u32(31))) & u32(0xffffffff));

            var res3 = 0;
            if ((res2 & u32(0x80000000)) != 0) {
                res3 = 1;
            }

            var res4 = 0;
            if ((res1 & 1) != 0) {
                res4 = 1;
            }

            let res = f32(res3 ^ res4);

            return vec4(res, res, res, 1);
        } else {
            let icoord = vec2<u32>(((coord - vec2(0.4, 0.6)) * vec2(1.0 / 0.4, 1.0 / 0.4)) * 256);

            let res3 = i32(((icoord.x >> 5) & 1) ^ ((icoord.y & 32) >> 5));
            let res2 = i32(((icoord.y * icoord.y) >> 10) & 1);
            let res1 = i32(((icoord.x * icoord.y) >> 9) & 1);

            return vec4(f32(res1 ^ res2), f32(res2 & res3), f32(res1 | res3), 1);
        }
    } else {
        let icoord = vec2<u32>(((coord - vec2(0.4, 0.0)) * vec2(1.0 / 0.6, 1.0)) * 256);

        let v = i32((icoord.x ^ icoord.y) * icoord.y);

        let res1 = ((v >> 10) & 1) != 0;
        let res2 = ((v >> 11) & 4) != 0;
        let res3 = ((v >> 12) & 8) != 0;

        return vec4(toNumber(res1), toNumber(res2), toNumber(res3), 1);
    }
}