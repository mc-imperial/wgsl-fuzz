#!/usr/bin/env bash

set -x
set -e
set -u

help | head

uname

pushd external
git clone --depth 1 https://dawn.googlesource.com/dawn
popd

mvn package
