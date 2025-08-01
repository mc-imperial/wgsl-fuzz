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

ignoreList=("./samples/counting_sort.wgsl", "./samples/counting_sort.png")

mkdir generated

for file in ./samples/*.wgsl; do
  if [[ ! " ${ignoreList[@]} " =~ " $file " ]]; then
    name=$(basename "$file" .wgsl)
    outputDir="generated/$name"
    mkdir "$outputDir"
    ./scripts/runGenerator --originalShader "$file" --numVariants 5 --outputDir "$outputDir" --seed 53
  fi
done

echo "Generated: "
ls generated

mkdir shaderHtml

for folder in $(ls generated); do
  mkdir "shaderHtml/$folder"
  ./scripts/standAloneShaderHtml --shaderFolderPath "generated/$folder" --output "shaderHtml/$folder"
done

pushd src/main/checkSemanticsPreserving
  npm install
popd

mkdir resultPng
for folder in $(ls shaderHtml); do
  mkdir "resultPng/$folder"
  node ./src/main/checkSemanticsPreserving/cli.js --chrome "$(which chromium-browser)" --chromeArgs "--enable-unsafe-webgpu" --jobDir "shaderHtml/$folder" --outputDir "resultPng/$folder"
done

./scripts/generateReferenceImages

for file in ./referenceImages/*.png; do
  if [[ ! " ${ignoreList[@]} " =~ " $file " ]]; then
    ./scripts/compareImages --file1Path "$file" --file2Dir "resultPng/$(basename "$file" .png)" --identicalImageCompare
  fi
done
