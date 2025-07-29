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

@group(0) @binding(0) var<uniform> expectedResult: u32;

fn myReverseBits(input: u32) -> u32 {
  var currentValue: u32 = input;
  var output: u32 = 0;
  var i = 0;
  loop {
    output = (output << 1) | (currentValue & 1);
    currentValue = currentValue >> 1;

    i++;
    if (i == 32) {
      return output;
    }
  }
}

@vertex
fn vertexMain(@location(0) pos: vec2f) -> @builtin(position) vec4f {
  var scaleFactor = 0.5;

  let input: u32 = 321094389;
  if (myReverseBits(input) != expectedResult) {
    scaleFactor = 1;
  }

  return vec4f(pos * scaleFactor, 0, 1);
}

@fragment
fn fragmentMain() -> @location(0) vec4f {
    return vec4(1, 0, 0, 1);
}
