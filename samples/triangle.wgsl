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
 * Original shader: https://github.com/google/graphicsfuzz/blob/master/shaders/src/main/glsl/samples/320es/stable_triangle.frag
 */

@group(0) @binding(0) var<uniform> resolution: vec2<i32>;

fn cross2d(a: vec2f, b: vec2f) -> f32 {
  return a.x * b.y - b.x * a.y;
}

fn pointInTriangle(p: vec2f, a: vec2f, b: vec2f, c: vec2f) -> bool {
  let pab = cross2d(vec2f(p.x - a.x, p.y - a.y), vec2f(b.x - a.x, b.y - a.y));
  let pbc = cross2d(vec2f(p.x - b.x, p.y - b.y), vec2f(c.x - b.x, c.y - b.y));
  if (!((pab <  0.0 && pbc <  0.0) ||
        (pab >= 0.0 && pbc >= 0.0))) {
    return false;
  }
  let pca = cross2d(vec2f(p.x - c.x, p.y - c.y), vec2f(a.x - c.x, a.y - c.y));
  if (!((pab <  0.0 && pca <  0.0) ||
        (pab >= 0.0 && pca >= 0.0))) {
    return false;
  }
  return true;
}

@vertex
fn vertexMain(@location(0) pos: vec2f) -> @builtin(position) vec4f {
  return vec4f(pos, 0, 1);
}

@fragment
fn fragmentMain(@builtin(position) gl_FragCoord: vec4f) -> @location(0) vec4f {
  let pos = gl_FragCoord.xy / vec2f(resolution);
  if (pointInTriangle(pos, vec2(0.7, 0.3), vec2(0.5, 0.9), vec2(0.1, 0.4))) {
    return vec4(1, 0, 0, 1);
  } else {
    return vec4(0, 0, 0, 1);
  }
}
