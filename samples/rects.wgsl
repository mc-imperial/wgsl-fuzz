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
 * Modifications were made to have pal array passed in as a uniform
 * Original shader: https://github.com/google/graphicsfuzz/blob/master/shaders/src/main/glsl/samples/320es/stable_rects.frag
 */

@group(0) @binding(0) var<uniform> resolution: vec2<i32>;
@group(0) @binding(1) var<uniform> pal: array<vec4<i32>, 16>;

const palAdjustmentVector = vec4(0.5, 0.5, 0.5, 0.5);

const picdata = array<vec4<f32>, 8>(vec4(4, 4, 20, 4),
                                    vec4(4, 4, 4, 20),
                                    vec4(4, 20, 20, 4),
                                    vec4(20, 4, 4, 8),
                                    vec4(8, 6, 4, 2),
                                    vec4(2, 12, 2, 4),
                                    vec4(16, 2, 4, 4),
                                    vec4(12, 22, 4, 4));

var<private> index: i32;
var<private> state: array<i32, 16>;

fn collision(pos: vec2f, quad: vec4f) -> bool {
  if (pos.x < quad.x) {
    return false;
  }
  if (pos.y < quad.y) {
    return false;
  }
  if (pos.x > quad.x + quad.z) {
    return false;
  }
  if (pos.y > quad.y + quad.w) {
    return false;
  }
  return true;
}

fn matchFunc(pos: vec2f) -> vec4f {
  var res = vec4(0.5, 0.5, 1, 1);
  for (var i = 0; i < 8; i++) {
    if (collision(pos, picdata[i])) {
      res = vec4f(pal[(i32(picdata[i].x) * i32(picdata[i].y) + i * 9 + 11) % 16]) * palAdjustmentVector;
    }
  }
  return res;
}

@vertex
fn vertexMain(@location(0) pos: vec2f) -> @builtin(position) vec4f {
  return vec4f(pos, 0, 1);
}

@fragment
fn fragmentMain(@builtin(position) gl_FragCoord: vec4f) -> @location(0) vec4f {
  let lin1 = gl_FragCoord.xy / vec2<f32>(resolution);
  let lin2 = floor(lin1 * 32);
  return matchFunc(lin2);
}
