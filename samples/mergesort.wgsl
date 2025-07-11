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
 * Modifications were also made to utilise storing intialisation data in a uniform
 * Original shader: https://github.com/google/graphicsfuzz/blob/master/shaders/src/main/glsl/samples/320es/stable_mergesort.frag
 */

const ARRAY_LENGTH = 10;

struct Number {
    padding: vec3<i32>,
    value: i32
}

struct Uniforms {
    values: array<Number, ARRAY_LENGTH>
}

@group(0) @binding(0) var<uniform> ub: Uniforms;

var<private> data: array<i32, ARRAY_LENGTH>;
var<private> temp: array<i32, ARRAY_LENGTH>;

fn merge(fromIndex: i32, midIndex: i32, toIndex: i32) {
  var k = fromIndex;
  var i = fromIndex;
  var j = midIndex + 1;
  while (i <= midIndex && j <= toIndex) {
    if (data[i] < data[j]) {
      temp[k] = data[i];
      k++;
      i++;
    } else {
      temp[k] = data[j];
      k++;
      j++;
    }
  }

  while (i < ARRAY_LENGTH && i <= midIndex) {
    temp[k] = data[i];
    k++;
    i++;
  }

  while (j < ARRAY_LENGTH && j <= toIndex) {
      temp[k] = data[j];
      k++;
      j++;
    }

  for (var i = fromIndex; i <= toIndex; i++) {
    data[i] = temp[i];
  }
}

fn mergeSort() {
  let low: i32 = 0;
  let high: i32 = ARRAY_LENGTH - 1;

  for (var m = 1; m <= high; m = 2 * m) {
    for (var i = low; i < high; i += 2 * m) {
      let fromIndex = i;
      let midIndex = i + m - 1;
      let toIndex = min(i + 2 * m - 1, high);
      merge(fromIndex, midIndex, toIndex);
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
    data[i] = ub.values[i].value;
  }

  for (var i = 0; i < ARRAY_LENGTH; i++) {
    temp[i] = data[i];
  }

  for (var i = 0; i < ARRAY_LENGTH; i++) {
    if (data[i] != 4 - i && temp[i] != 4 - i) {
      return vec4(0, 0, 1, 1);
    }
  }

  mergeSort();

  var grey: f32 = 0;
  if(i32(gl_FragCoord[1]) < 30) {
    grey = 0.5 + f32(data[0]) / 10.0;
  } else {
    if(i32(gl_FragCoord[1]) < 60) {
      grey = 0.5 + f32(data[1]) / 10.0;
    } else {
      if(i32(gl_FragCoord[1]) < 90) {
        grey = 0.5 + f32(data[2]) / 10.0;
      } else {
        if(i32(gl_FragCoord[1]) < 120) {
          grey = 0.5 + f32(data[3]) / 10.0;
        } else {
          if(i32(gl_FragCoord[1]) < 150) {
            discard;
          } else {
            if(i32(gl_FragCoord[1]) < 180) {
              grey = 0.5 + f32(data[5]) / 10.0;
            } else {
              if(i32(gl_FragCoord[1]) < 210) {
                grey = 0.5 + f32(data[6]) / 10.0;
              } else {
                if(i32(gl_FragCoord[1]) < 240) {
                  grey = 0.5 + f32(data[7]) / 10.0;
                } else {
                  if(i32(gl_FragCoord[1]) < 270) {
                    grey = 0.5 + f32(data[8]) / 10.0;
                  } else {
                    discard;
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  return vec4(grey, grey, grey, 1);
}
