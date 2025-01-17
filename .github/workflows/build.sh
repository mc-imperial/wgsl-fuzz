#!/usr/bin/env bash

set -x
set -e
set -u

help | head

uname

mvn package

exit 1
