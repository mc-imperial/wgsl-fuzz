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

struct Number {
    padding: vec3<i32>,
    value: i32
}

struct Uniforms {
    values: array<Number, ARRAY_LENGTH>
}

@group(0) @binding(0) var<uniform> ub: Uniforms;

struct LinkedListNode {
  value: i32,
  next: i32,
}

var<private> linkedList: array<LinkedListNode, ARRAY_LENGTH>;
var<private> head: i32;

fn reverseLinkedList() {
  var prevIndex = -1;
  var currIndex = head;
  while (currIndex != -1) {
    var temp = linkedList[currIndex].next;
    linkedList[currIndex].next = prevIndex;
    prevIndex = currIndex;
    currIndex = temp;
  }
  head = prevIndex;
}

@vertex
fn vertexMain(@location(0) pos: vec2f) -> @builtin(position) vec4f {
  var scaleFactor = 0.5;

  for (var i = 0; i < ARRAY_LENGTH; i++) {
    linkedList[i].value = ub.values[i].value;
    linkedList[i].next = i - 1;
  }
  head = ARRAY_LENGTH - 1;

  var currIndex = head;
  for (var i = ARRAY_LENGTH - 1; i >= 0; i--) {
    if (linkedList[currIndex].value != ub.values[i].value) {
      scaleFactor = 1.0;
    }
    currIndex = linkedList[currIndex].next;
  }

  reverseLinkedList();

  currIndex = head;
  for (var i = 0; i < ARRAY_LENGTH; i++) {
    if (linkedList[currIndex].value != ub.values[i].value) {
      scaleFactor = 1.0;
    }
    currIndex = linkedList[currIndex].next;
  }

  return vec4f(pos * scaleFactor, 0, 1);
}

@fragment
fn fragmentMain() -> @location(0) vec4f {
    return vec4(1, 0, 0, 1);
}
