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
    @size(16) value: i32
}

struct Uniforms {
    values: array<Number, ARRAY_LENGTH>
}

@group(0) @binding(0) var<uniform> ub: Uniforms;

var<private> endOfHeap: i32 = 0;
var<private> heap: array<i32, ARRAY_LENGTH>;

fn leftChildIndex(index: i32) -> i32 {
    return 2 * index + 1;
}

fn rightChildIndex(index: i32) -> i32 {
    return 2 * index + 2;
}

fn isLeaf(index: i32) -> bool {
    return leftChildIndex(index) >= endOfHeap;
}

fn swap(i: i32, j: i32) {
    let temp = heap[i];
    heap[i] = heap[j];
    heap[j] = temp;
}

fn fixMaxHeap(initialIndex: i32) {
    var index = initialIndex;
    while (index != -1) {
        if (isLeaf(index)) {
            index = -1;
        } else {
            var largerSubHeapIndex = -1;
            if (heap[leftChildIndex(index)] > heap[rightChildIndex(index)]) {
                largerSubHeapIndex = leftChildIndex(index);
            } else {
                largerSubHeapIndex = rightChildIndex(index);
            }

            if (heap[largerSubHeapIndex] > heap[index]) {
                swap(largerSubHeapIndex, index);
                index = largerSubHeapIndex;
            } else {
                index = -1;
            }
        }
    }
}

fn maxHeapify() {
    for (var i = endOfHeap - 1; i >= 0; i--) {
        fixMaxHeap(i);
    }
}

fn validHeapElement(index: i32) -> bool {
    return (leftChildIndex(index) >= endOfHeap || heap[index] >= heap[leftChildIndex(index)]) &&
           (rightChildIndex(index) >= endOfHeap || heap[index] >= heap[rightChildIndex(index)]);
}

fn contains(x: i32) -> bool {
    for (var i = 0; i < ARRAY_LENGTH; i++) {
        if (ub.values[i].value == x) {
            return true;
        }
    }
    return false;
}

@vertex
fn vertexMain(@location(0) pos: vec2f) -> @builtin(position) vec4f {
  return vec4f(pos, 0, 1);
}

@fragment
fn fragmentMain() -> @location(0) vec4f {
    for (var i = 0; i < ARRAY_LENGTH; i++) {

        heap[i] = ub.values[i].value;
    }
    endOfHeap = ARRAY_LENGTH;

    maxHeapify();

    for (var i = 0; i < endOfHeap; i++) {
        if (!validHeapElement(i)) {
            return vec4(0, 0, 1, 1);
        }
    }

    for (var i = 0; i < endOfHeap; i++) {
        if ((!contains(heap[i])) || heap[i] > 50) {
            return vec4(0, 0, 1, 1);
        }
    }

    return vec4(1, 0, 0, 1);
}
