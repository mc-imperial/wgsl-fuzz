#!/usr/bin/env bash

set -x
set -e
set -u

help | head

uname

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

mvn package
