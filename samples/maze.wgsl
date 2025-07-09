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
 * Original shader: https://github.com/google/graphicsfuzz/blob/master/shaders/src/main/glsl/samples/320es/stable_maze.frag
 */

@group(0) @binding(0) var<uniform> resolution: vec2<i32>;

var<private> map: array<i32, 16 * 16>;

@vertex
fn vertexMain(@location(0) pos: vec2f) -> @builtin(position) vec4f {
  return vec4f(pos, 0, 1);
}

@fragment
fn fragmentMain(@builtin(position) gl_FragCoord: vec4f) -> @location(0) vec4f {
  let pos = gl_FragCoord.xy / vec2(f32(resolution.x), f32(resolution.y));

  let ipos = vec2(i32(pos.x * 16), i32(pos.y * 16));

  for (var i = 0; i < 16 * 16; i++) {
    map[i] = 0;
  }

  var p = vec2<i32>(0, 0);
  var canWalk = true;
  var v = 0;
  while (canWalk) {
    v++;
    var direction = 0;
    if (p.x > 0 && map[(p.x - 2) + p.y * 16] == 0) {
      direction++;
    }
    if (p.y > 0 && map[p.x + (p.y - 2) * 16] == 0) {
      direction++;
    }
    if (p.x < 14 && map[(p.x + 2) + p.y * 16] == 0) {
      direction++;
    }
    if (p.y < 14 && map[p.x + (p.y + 2) * 16] == 0) {
      direction++;
    }

    if (direction == 0) {
      canWalk = false;

      for (var i = 0; i < 8; i++) {
        for (var j = 0; j < 8; j++) {
          if (map[j * 2 + i * 2 * 16] == 0) {
            p.x = j * 2;
            p.y = i * 2;
            canWalk = true;
          }
        }
      }

      map[p.x + p.y * 16] = 1;
    } else {
      var d = v % direction;

      v += direction;

      if (d >= 0 && p.x > 0 && map[(p.x - 2) + p.y * 16] == 0) {
        d--;
        map[p.x + p.y * 16] = 1;
        map[(p.x - 1) + p.y * 16] = 1;
        map[(p.x - 2) + p.y * 16] = 1;
        p.x -= 2;
      }
      if (d >= 0 && p.y > 0 && map[p.x + (p.y - 2) * 16] == 0) {
        d--;
        map[p.x + p.y * 16] = 1;
        map[p.x + (p.y - 1) * 16] = 1;
        map[p.x + (p.y - 2) * 16] = 1;
        p.y -= 2;
      }
      if (d >= 0 && p.x < 14 && map[(p.x + 2) + p.y * 16] == 0) {
        d--;
        map[p.x + p.y * 16] = 1;
        map[(p.x + 1) + p.y * 16] = 1;
        map[(p.x + 2) + p.y * 16] = 1;
        p.x += 2;
      }
      if (d >= 0 && p.y < 14 && map[p.x + (p.y + 2) * 16] == 0) {
        d--;
        map[p.x + p.y * 16] = 1;
        map[p.x + (p.y + 1) * 16] = 1;
        map[p.x + (p.y + 2) * 16] = 1;
        p.y += 2;
      }
    }
    if (map[ipos.y * 16 + ipos.x] == 1) {
      return vec4(1, 1, 1, 1);
    }
  }
  return vec4(0, 0, 0, 1);
}