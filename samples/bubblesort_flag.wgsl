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
 * Reimplementation in WGSL of the GLSL shader from https://github.com/google/graphicsfuzz
 * Original shader: https://github.com/google/graphicsfuzz/blob/master/shaders/src/main/glsl/samples/320es/stable_bubblesort_flag.frag
 */

struct Uniforms {
  injectionSwitch: vec2f,
  resolution: vec2<i32>,
}

@group(0)
@binding(0)
var<uniform> ub: Uniforms;

@vertex
fn vertexMain(@location(0) pos: vec2f) -> @builtin(position) vec4f {
  return vec4f(pos, 0, 1);
}

fn checkSwap(gl_FragCoord: vec4f, a: f32, b: f32) -> bool
{
    return select((a < b), (a > b), gl_FragCoord.y < f32(ub.resolution.y) / 2.0);
}

@fragment
fn fragmentMain(
  @builtin(position) gl_FragCoord: vec4f,
) -> @location(0) vec4f {
    var data: array<f32, 10>;
    for(var i: i32 = 0; i < 10; i++) {
        data[i] = f32(10 - i);
    }
    for(var i: i32 = 0; i < 9; i++) {
        for(var j: i32 = 0; j < 10; j++) {
            if(j < i + 1) {
                continue;
            }
            let doSwap = checkSwap(gl_FragCoord, data[i], data[j]);
            if(doSwap) {
                let temp = data[i];
                data[i] = data[j];
                data[j] = temp;
            }
        }
    }

    if(gl_FragCoord.x < f32(ub.resolution.x) / 2.0) {
        return vec4(data[0] / 10.0, data[5] / 10.0, data[9] / 10.0, 1.0);
    }
    else {
        return vec4(data[5] / 10.0, data[9] / 10.0, data[0] / 10.0, 1.0);
    }
}
