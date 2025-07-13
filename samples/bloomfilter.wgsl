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

const ARRAY_LENGTH = 5;
const BLOOM_FILTER_NUM_BITS = 512;
const BLOOM_FILTER_ARRAY_LENGTH = BLOOM_FILTER_NUM_BITS / 32;

struct Number {
    padding: vec3<i32>,
    value: i32
}

// Elements cannot be larger than MAX_ELEMENT_UPPER_BOUND
@group(0) @binding(0) var<uniform> ub: array<Number, ARRAY_LENGTH>;

var<private> bloomFilter: array<i32, BLOOM_FILTER_ARRAY_LENGTH>;

// Index is the index of th bit
// If value is true then sets to 1 else 0
fn setBit(index: i32) {
  if (index >= BLOOM_FILTER_NUM_BITS || index < 0) {
    return;
  }

  let elementIndex = index / 32;
  let bitWithinElementIndex = u32(index % 32);

  bloomFilter[elementIndex] = bloomFilter[elementIndex] | (1 << bitWithinElementIndex);
}

fn getBit(index: i32) -> bool {
  if (index >= BLOOM_FILTER_NUM_BITS || index < 0) {
    return false;
  }

  let elementIndex = index / 32;
  let bitWithinElementIndex = u32(index % 32);

  let result = (bloomFilter[elementIndex] >> bitWithinElementIndex) & 1;

  if (result == 1) {
     return true;
  }
  return false;
}

fn hash1(element: i32) -> i32 {
  return (39 * element + 22) % 107;
}

fn hash2(element: i32) -> i32 {
  return (50 * element + 12) % 131;
}

fn hash3(element: i32) -> i32 {
  return (93 * element + 51) % 163;
}

fn insert(element: i32) {
  setBit(hash1(element) % BLOOM_FILTER_NUM_BITS);
  setBit(hash2(element) % BLOOM_FILTER_NUM_BITS);
  setBit(hash3(element) % BLOOM_FILTER_NUM_BITS);
}

fn contain(element: i32) -> bool {
  return getBit(hash1(element) % BLOOM_FILTER_NUM_BITS) &&
         getBit(hash2(element) % BLOOM_FILTER_NUM_BITS) &&
         getBit(hash3(element) % BLOOM_FILTER_NUM_BITS);
}

fn inputListContains(element: i32) -> bool {
  for (var i = 0; i < ARRAY_LENGTH; i++) {
    if (element == ub[i].value) {
      return true;
    }
  }
  return false;
}

@vertex
fn vertexMain(@location(0) pos: vec2f) -> @builtin(position) vec4f {
  var scaleFactor = 1.0;

  for (var i = 0; i < BLOOM_FILTER_ARRAY_LENGTH; i++) {
    bloomFilter[i] = 0;
  }

  for (var i = 0; i < ARRAY_LENGTH; i++) {
    insert(ub[i].value);
  }

  // This check will fail for when i == 4 since bloom filters can give false positives
  for (var i = 0; i < 50; i++) {
    if (inputListContains(i) != contain(i)) {
      scaleFactor = scaleFactor * 0.75;
    }
  }

  return vec4f(pos * scaleFactor, 0, 1);
}

@fragment
fn fragmentMain() -> @location(0) vec4f {
    return vec4(1, 0, 0, 1);
}
