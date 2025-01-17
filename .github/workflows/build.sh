#!/usr/bin/env bash

set -x
set -e
set -u

help | head

uname

echo "Starting CI"

echo "Exiting with fake error"

exit 1
