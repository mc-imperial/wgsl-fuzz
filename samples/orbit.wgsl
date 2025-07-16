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
 * Original shader: https://github.com/google/graphicsfuzz/blob/master/shaders/src/main/glsl/samples/320es/stable_orbit.frag
 */

@group(0) @binding(0) var<uniform> resolution: vec2<i32>;
@group(0) @binding(1) var<uniform> pal: array<vec4<i32>, 16>;

const correctPal = array(vec4(0.0, 0.0, 0.0, 1.0),
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
                         vec4(1.0, 1.0, 1.0, 1.0));

fn iter(pInitial: vec2<i32>) -> vec2<i32> {
  var p: vec2<i32> = pInitial;
  if (p.x > 0) {
    p.y--;
  }
  if (p.x < 0) {
    p.y++;
  }
  p.x += p.y / 2;
  return p;
}

@vertex
fn vertexMain(@location(0) pos: vec2f) -> @builtin(position) vec4f {
  return vec4f(pos, 0, 1);
}

@fragment
fn fragmentMain(@builtin(position) gl_FragCoord: vec4f) -> @location(0) vec4f {
  let palAdjustmentVector = vec4(0.5, 0.5, 0.5, 0.5);
  for (var i = 0; i < 16; i++) {
    let comparisonResult = (vec4<f32>(pal[i]) * palAdjustmentVector) != correctPal[i];
    if (comparisonResult.x && comparisonResult.y && comparisonResult.z && comparisonResult.w) {
      discard;
    }
  }

  let pos = gl_FragCoord.xy / vec2<f32>(resolution);

  let ipos = vec2<i32>(i32(pos.x * 8.0), i32(pos.y * 8.0));

  let v: i32 = (ipos.x & 5) | (ipos.y & 10);
  let w: i32 = (ipos.y & 5) | (ipos.x & 10);

  var p: vec2<i32> = vec2<i32>(v * 8 + w, 0);

  for (var i = 0; i < 100; i++) {
    p = iter(p);
  }

  if (p.x < 0) {
    p.x = -p.x;
  }

  while (p.x > 15) {
    p.x -= 16;
  }

  return vec4<f32>(pal[p.x]) * palAdjustmentVector;
}
