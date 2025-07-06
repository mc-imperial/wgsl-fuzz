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

const ARRAY_LENGTH = 16;

const I32_MIN = -2147483648;
const MAX_ELEMENT_UPPER_BOUND = 256;

struct Number {
    @size(16) value: i32
}

// Elements can not be larger than MAX_ELEMENT_UPPER_BOUND
@group(0) @binding(0) var<uniform> ub: array<Number, ARRAY_LENGTH>;

var<private> list: array<i32, ARRAY_LENGTH>;

fn maxElement(arr: ptr<private, array<i32, ARRAY_LENGTH>>) -> i32 {
    var maxElem = I32_MIN;
    for (var i = 0; i < ARRAY_LENGTH; i++) {
        if (arr[i] > maxElem) {
            maxElem = arr[i];
        }
    }

    return maxElem;
}

fn countingSort(arr: ptr<private, array<i32, ARRAY_LENGTH>>) {
    let maxElem = maxElement(arr);

    // Counts gets initialised to a zeroed out array as
    var counts = array<i32, MAX_ELEMENT_UPPER_BOUND>();

    for (var i = 0; i < ARRAY_LENGTH; i++) {
        counts[arr[i]]++;
    }

    var countIndex = 0;
    for (var i = 0; i < ARRAY_LENGTH; i++) {
        while (counts[countIndex] == 0) {
            countIndex++;
        }
        arr[i] = countIndex;
        counts[countIndex]--;
    }
}

@vertex
fn vertexMain(@location(0) pos: vec2f) -> @builtin(position) vec4f {
  return vec4f(pos, 0, 1);
}

@fragment
fn fragmentMain() -> @location(0) vec4f {
    for (var i = 0; i < ARRAY_LENGTH; i++) {
        list[i] = ub[i].value;
    }

    countingSort(&list);

    for (var i = 1; i < ARRAY_LENGTH; i++) {
        if (list[i - 1] > list[i]) {
             return vec4(0, 0, 1, 1);
        }
    }

    return vec4(1, 0, 0, 1);
}
