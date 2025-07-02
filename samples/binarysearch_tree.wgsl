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

struct Uniforms {
    // Can contain up to and including 5 values
    // Values must be between 0 and 20
    // Values cannot be 9, 5, 12, 15, 7
    values: array<i32>,
}

@group(0) @binding(0) var<uniform> ub: Uniforms;

struct BST {
    data: i32,
    leftIndex: i32,
    rightIndex: i32,
}

var<private> tree: array<BST, 10>;

fn makeTreeNode(tree: BST, data: i32) {
    tree.data = data;
    tree.leftIndex = -1;
    tree.rightIndex = -1;
}

fn insert(treeIndex: i32, data: i32) {
    var baseIndex: i32 = 0;
    while (baseIndex <= treeIndex) {
        // If new value is smaller thatn the current node, we known that we will have
        // add this element in the left side.
        if (data <= tree[baseIndex].data) {
            // If a left subtree of the current node is empty, the new node is added as
            // a left subtree of the current node.
            if (tree[baseIndex].leftIndex == -1) {
                tree[baseIndex].leftIndex = treeIndex;
                makeTreeNode(tree[treeIndex], data);
                return;
            } else {
                baseIndex = tree[baseIndex].leftIndex;
                continue;
            }
        } else {
            // If a right subtree of the current node is empty, the new node is added as
            // a right subtree of the current node.
            if (tree[baseIndex].rightIndex == -1) {
                tree[baseIndex].rightIndex = treeIndex;
                makeTreeNode(tree[treeIndex], data);
                return;
            } else {
                baseIndex = tree[baseIndex].rightIndex;
                continue;
            }
        }
    }
}

// Return element data if the given target exists in a tree. Otherwise, we simply return -1.
fn search(target: i32) {
    var currentNode: BST;
    var index: i32 = 0;
    while (index != -1) {
        currentNode = tree[index];
        if (currentNode.data == target) {
            return target;
        }
        if (target > currentNode.data) {
            index = currentNode.rightIndex;
        } else {
            index = currentNode.leftIndex;
        }
    }
    return -1;
}

fn contains(x: i32) -> bool {
    for (var i = 0; i < arrayLength(&ub.values); i++) {
        if (ub.values[i] == x) {
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
    var treeIndex: i32 = 0;

    // Initialize root node.
    makeTreeNode(tree[0], 9);
    // Each time we insert a new node into the tree, we increment one.
    treeIndex++;

    insert(treeIndex, 5);
    treeIndex++;

    insert(treeIndex, 12);
    treeIndex++;

    for (var i = 0; i < arrayLength(&ub.values); i++) {
        insert(treeIndex, ub.values[i]);
        treeIndex++;
    }

    insert(treeIndex, 15);
        treeIndex++;

    insert(treeIndex, 7);
    treeIndex++;

    var count: i32 = 0;
    for (var i = 0; i < 20; i++) {
        let result = search(i);
        switch (i) {
            case 9, 5, 12, 15, 7: {
                if (result == i) {
                    count++;
                }
            }
            default {
                if (result == -1 || (contains(i) && result == i)) {
                    count++;
                }
            }
        }
    }

    if (count == 20) {
        return vec4(1, 0, 0, 1);
    }
    return vec4(0, 0, 1, 1);
}
