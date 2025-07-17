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
 * Modified so that dp is passed in as an uniform
 * Original shader: https://github.com/google/graphicsfuzz/blob/master/shaders/src/main/glsl/samples/320es/stable_pillars.frag
 */

const DP_LENGTH = 256;

@group(0) @binding(0) var<uniform> resolution: vec2<i32>;
@group(0) @binding(1) var<uniform> dpInput: array<vec4<i32>, DP_LENGTH / 8>;

var<private> dp: array<i32, DP_LENGTH>;

fn trace(posInitial: vec2i) -> vec4f {
  var pos: vec2i = posInitial;
   while (pos.y != 256) {
    if (pos.x < dp[pos.y] + 15 && pos.x > dp[pos.y] - 15) {
      let p = (15.0 - abs(f32(pos.x - dp[pos.y]))) / 15.0;
      return vec4(p, p, p, 1);
    }
    pos.y++;
   }
   return vec4(0, 0, 0, 1);
}

@vertex
fn vertexMain(@location(0) pos: vec2f) -> @builtin(position) vec4f {
  return vec4f(pos, 0, 1);
}

fn getBottomHalf(num: i32) -> i32 {
  return num & 0xFFFF;
}

fn getTopHalf(num: i32) -> i32 {
  return (num >> 16) & 0xFFFF;
}

@fragment
fn fragmentMain(@builtin(position) gl_FragCoord: vec4f) -> @location(0) vec4f {
  for (var i = 0; i < DP_LENGTH / 8; i++) {
    let index = 8 * i;

    dp[index] = getBottomHalf(dpInput[i].x);
    dp[index + 1] = getTopHalf(dpInput[i].x);

    dp[index + 2] = getBottomHalf(dpInput[i].y);
    dp[index + 3] = getTopHalf(dpInput[i].y);

    dp[index + 4] = getBottomHalf(dpInput[i].z);
    dp[index + 5] = getTopHalf(dpInput[i].z);

    dp[index + 6] = getBottomHalf(dpInput[i].w);
    dp[index + 7] = getTopHalf(dpInput[i].w);
  }

  let pos = gl_FragCoord.xy / vec2<f32>(resolution);
  let ipos: vec2i = vec2i(pos * vec2(256, 256));

  return trace(ipos);
}
