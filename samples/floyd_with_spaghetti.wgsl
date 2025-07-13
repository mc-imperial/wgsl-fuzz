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
 * This sample shader is based on Floyd's algorithm which finds the shortest distance between all pairs in a graph
 * It is a relatively simple algorithm with three nested for loops. To make an interesting spaghetti code sample
 * I have decided to mangle the standard implementation.
 * Implementation details:
 * 256 has been used as my infinity
 */

/*
 * The graph that is inputted into the input uniform.
 * All node labels are prefixed with N.
 * All arc weights have no prefix.
 *        6
 * N0 ---------- N1
 * |             | \  1
 * |             |   \
 * |4            |2    N3
 * |             |   /
 * |      5      | /  4
 * N4 ---------- N3
 */


struct Number {
   value: i32,
   unusedPadding: vec2<i32>
}

const NUM_NODES = 5;

@group(0) @binding(0) var<uniform> input: array<array<Number, NUM_NODES>, NUM_NODES>;

/*
 * This is the correct matrix output of the algorithm passed in as a uniform.
 * [0, 6, 7, 8, 4]
 * [6, 0, 1, 2, 7]
 * [7, 1, 0, 3, 8]
 * [8, 2, 3, 0, 5]
 * [4, 7, 8, 5, 0]
 */
@group(0) @binding(1) var<uniform> correctOutput: array<array<Number, NUM_NODES>, NUM_NODES>;

var<private> shortestPaths: array<array<i32, NUM_NODES>, NUM_NODES>;

fn runFloyd() {
  var k = 0;
  loop {

    for (var i = 0; i < NUM_NODES; i++) {

      var j = -1;
      loop {
        j++;
        if (j >= NUM_NODES) {
          break;
        }

        // Perform min between shortestPaths[i][j] and shortestPaths[i][k] + shortestPaths[k][j]
        if (shortestPaths[i][j] < shortestPaths[i][k] + shortestPaths[k][j]) {
          continue;
        }
        shortestPaths[i][j] = shortestPaths[i][k] + shortestPaths[k][j];
      }

    }

    k++;
    if (k >= NUM_NODES) {
      return;
    }
  }
}

@vertex
fn vertexMain(@location(0) pos: vec2f) -> @builtin(position) vec4f {
  for (var i = 0; i < NUM_NODES; i++) {
    for (var j = 0; j < NUM_NODES; j++) {
      shortestPaths[i][j] = input[i][j].value;
    }
  }

  runFloyd();

  var scaleFactor = 1.0;
  for (var i = 0; i < NUM_NODES; i++) {
    for (var j = 0; j < NUM_NODES; j++) {
      if (shortestPaths[i][j] != correctOutput[i][j].value) {
        scaleFactor *= 0.75;
      }
    }
  }

  return vec4(pos * scaleFactor, 0, 1);
}

@fragment
fn fragmentMain() -> @location(0) vec4f {
    return vec4(1, 0, 0, 1);
}
