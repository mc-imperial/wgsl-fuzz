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

@group(0) @binding(0) var<uniform> resolution: vec2<i32>;

fn computeValue(limit: f32, thirtyTwo: f32) -> f32 {
    var result: f32 = -0.5;

    for (var i = 1; i < 800; i++) {
        if ((i % 32) == 0) {
            result += 0.4;
        } else {
            if (modf(f32(i) / round(thirtyTwo)).fract <= 0.01) {
                result += 100.0;
            }
        }

        if (f32(i) >= limit) {
            return result;
        }
    }
    return result;
}

@vertex
fn vertexMain(@location(0) pos: vec2f) -> @builtin(position) vec4f {
  return vec4f(pos, 0, 1);
}

@fragment
fn fragmentMain(@builtin(position) gl_FragCoord: vec4f) -> @location(0) vec4f {
    var c = vec3(7.0, 8.0, 9.0);

    let thirtyTwo = round(f32(resolution.x) / 8.0);

    c.x = computeValue(gl_FragCoord.x, thirtyTwo);
    c.y = computeValue(gl_FragCoord.y, thirtyTwo);
    c.z = c.x + c.y;

    for (var i = 0; i < 3; i++) {
        if (c[i] >= 1.0) {
            c[i] = c[i] * c[i];
        }
    }

    return vec4(normalize(abs(c)), 1.0);
}
