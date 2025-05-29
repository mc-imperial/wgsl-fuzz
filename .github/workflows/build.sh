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

python3 check_headers.py

pushd external
  # Get Dawn so that its WGSL tests can be used
  git clone --depth 1 https://dawn.googlesource.com/dawn
popd

mkdir "${HOME}/bin"
pushd "${HOME}/bin"
  # Install ktlint.
  curl -fsSL -o ktlint.zip "https://github.com/pinterest/ktlint/releases/download/1.5.0/ktlint-1.5.0.zip"
  unzip ktlint.zip
  ls
popd

pushd src
  "${HOME}/bin/ktlint-1.5.0/bin/ktlint" **/*.kt
popd

gradle build
