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
 * Modifications were made to have numbers array passed in as a uniform
 * Original shader: https://github.com/google/graphicsfuzz/blob/master/shaders/src/main/glsl/samples/320es/stable_quicksort.frag
 */

const ARRAY_LENGTH = 10;

struct Number {
  padding: vec3<i32>,
  value: i32,
}

@group(0) @binding(0) var<uniform> resolution: vec2<i32>;
@group(0) @binding(1) var<uniform> numbersInput: array<Number, ARRAY_LENGTH>;

var<private> numbers: array<i32, ARRAY_LENGTH>;

fn swap(i: i32, j: i32) {
  let temp = numbers[i];
  numbers[i] = numbers[j];
  numbers[j] = temp;
}

fn performPartition(l: i32, h: i32) -> i32 {
  let pivot = numbers[h];
  var i = l - 1;

  for (var j = l; j < h; j++) {
    if (numbers[j] <= pivot) {
      i++;
      swap(i, j);
    }
  }

  i++;
  swap(i, h);

  return i;
}

fn quicksort() {
  let l = 0;
  let h = ARRAY_LENGTH - 1;
  var stack: array<i32, 10>;

  var top = 0;
  stack[top] = l;
  top++;
  stack[top] = h;

  while (top >= 0) {
    let h = stack[top];
    top--;
    let l = stack[top];
    top--;

    let p = performPartition(l, h);

    if (p - 1 > l) {
      top++;
      stack[top] = l;
      top++;
      stack[top] = p - 1;
    }
    if (p + 1 < h) {
      top++;
      stack[top] = p + 1;
      top++;
      stack[top] = h;
    }
  }
}

@vertex
fn vertexMain(@location(0) pos: vec2f) -> @builtin(position) vec4f {
  return vec4f(pos, 0, 1);
}

@fragment
fn fragmentMain(@builtin(position) gl_FragCoord: vec4f) -> @location(0) vec4f {
  for (var i = 0; i < ARRAY_LENGTH; i++) {
    numbers[i] = numbersInput[i].value;
  }

  quicksort();

  for (var i = 0; i < ARRAY_LENGTH; i++) {
    if (numbers[i] != numbersInput[ARRAY_LENGTH - i - 1].value) {
      discard;
    }
  }

  let uv = gl_FragCoord.xy / vec2f(resolution);

  var color = vec3(1.0, 2.0, 3.0);

  color.x += f32(numbers[0]);
  if (uv.x > (1.0 / 4.0)) {
    color.x += f32(numbers[1]);
  }
  if (uv.x > (2.0 / 4.0)) {
    color.y += f32(numbers[2]);
  }
  if(uv.x > (3.0 / 4.0)) {
    color.z += f32(numbers[3]);
  }
  color.y += f32(numbers[4]);
  if (uv.y > (1.0 / 4.0)) {
    color.x += f32(numbers[5]);
  }
  if (uv.y > (2.0 / 4.0)) {
    color.y += f32(numbers[6]);
  }
  if(uv.y > (3.0 / 4.0)) {
    color.z += f32(numbers[7]);
  }
  color.z += f32(numbers[8]);
  if (abs(uv.x - uv.y) < 0.25) {
    color.x += f32(numbers[9]);
  }

  return vec4f(normalize(color), 1.0);
}
