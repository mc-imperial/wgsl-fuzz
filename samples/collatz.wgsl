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
* Original shader: https://github.com/google/graphicsfuzz/blob/master/shaders/src/main/glsl/samples/320es/stable_collatz.frag
*/

@group(0) @binding(0) var<uniform> resolution: vec2<i32>;

const pal = array(
    vec4(0.0, 0.0, 0.0, 1.0),
    vec4(0.5, 0.0, 0.0, 1.0),
    vec4(0.0, 0.5, 0.0, 1.0),
    vec4(0.5, 0.5, 0.0, 1.0),
    vec4(0.0, 0.0, 0.5, 1.0),
    vec4(0.5, 0.0, 0.5, 1.0),
    vec4(0.0, 0.5, 0.5, 1.0),
    vec4(0.5, 0.5, 0.5, 1.0),

    vec4(0.0, 0.0, 0.0, 1.0),
    vec4(1.0, 0.0, 0.0, 1.0),
    vec4(0.0, 1.0, 0.0, 1.0),
    vec4(1.0, 1.0, 0.0, 1.0),
    vec4(0.0, 0.0, 1.0, 1.0),
    vec4(1.0, 0.0, 1.0, 1.0),
    vec4(0.0, 1.0, 1.0, 1.0),
    vec4(1.0, 1.0, 1.0, 1.0)
);

fn collatz(vInitial: i32) -> i32 {
    var v = vInitial;
    var count: i32 = 0;
    while (v > 1) {
        if (v % 2 == 0) {
            v = v / 2;
        } else {
            v = 3 * v + 1;
        }
        count++;
    }
    return count;
}

@vertex
fn vertexMain(@location(0) pos: vec2f) -> @builtin(position) vec4f {
  return vec4f(pos, 0, 1);
}

@fragment
fn fragmentMain(@builtin(position) gl_FragCoord: vec4f) -> @location(0) vec4f {
  var lin: vec2<f32> = vec2(gl_FragCoord.x / f32(resolution.x), gl_FragCoord.y / f32(resolution.y));

  lin = floor(lin * 8.0);

  var v: i32 = i32(lin.x) * 8 + i32(lin.y);
  return pal[collatz(v) % 16];
}