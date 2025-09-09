#!/usr/bin/env bash

# Copyright 2025 The wgsl-fuzz Project Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -x
set -e
set -u

help | head

uname

# TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/220)
# TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/237) ignored collatz because of this issue
ignoreList=(
  "mergesort"
  "reverse_linked_list"
  "collatz"
  "logic_operations"
  "counting_sort" # TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/250) Counting sort uses pointers which can cause invalid aliased pointer argument
)

numVariants=1
# TODO(https://github.com/mc-imperial/wgsl-fuzz/issues/217) Determine better Mean Squared Error threshold
mseThreshold=32.0

mkdir generated

for file in ./samples/*.wgsl; do
  name=$(basename "$file" .wgsl)
  if [[ ! " ${ignoreList[@]} " =~ " $name " ]]; then
    outputDir="generated/$name"
    mkdir "$outputDir"
    ./scripts/runGenerator --originalShader "$file" --donorShader "samples/logic_operations.wgsl" --numVariants "$numVariants" --outputDir "$outputDir" --seed 53
  fi
done

echo "Generated: "
ls generated

mkdir shaderHtml

for folder in $(ls generated); do
  mkdir "shaderHtml/$folder"
  ./scripts/standAloneShaderHtml --shaderFolderPath "generated/$folder" --output "shaderHtml/$folder"
done

pushd scripts/renderShaderHtml
  npm install
popd

mkdir resultPng
for folder in $(ls shaderHtml); do
  mkdir "resultPng/$folder"
  node ./scripts/renderShaderHtml/cli.js --chrome "$(which chromium-browser)" --chromeArgs "--enable-unsafe-webgpu" --jobDir "shaderHtml/$folder" --outputDir "resultPng/$folder"
done

./scripts/generateReferenceImages

for file in ./referenceImages/*.png; do
  name=$(basename "$file" .png)
  if [[ ! " ${ignoreList[@]} " =~ " $name " ]]; then
    echo "================ Checking $name ================"
    ./scripts/compareImages --file1Path "$file" --file2Dir "resultPng/$name" --mseThreshold "$mseThreshold"
  fi
done
