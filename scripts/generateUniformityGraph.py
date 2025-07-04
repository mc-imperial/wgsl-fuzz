#!/usr/bin/env python3

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

import re
import os
import sys
import subprocess
import argparse
import tempfile
from pathlib import Path


def extract_digraph(compiler_output):
    depth = 0
    in_graph = False
    graph_start = re.compile(r'^digraph\s+G\s*\{')

    result = ""

    for line in compiler_output.splitlines():
        if not in_graph and graph_start.match(line):
            in_graph = True

        if in_graph:
            result += line

        depth += line.count('{') - line.count('}')

        if depth == 0:
            break

    return result


def main():
    # TODO(JLJ): Add help messages
    parser = argparse.ArgumentParser(
        description="Generate the uniformity graph for a WGSL shader using tint."
    )
    parser.add_argument(
        'filename',
        help="WGSL shader to be compiled using tint."
    )
    parser.add_argument(
        '-o',
        help="Output filename for the PNG image. The default is the shader name with a .png extension."
    )

    args = parser.parse_args()

    process = subprocess.Popen(f"tint {args.filename}", shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

    stdout, stderr = process.communicate()

    graph = extract_digraph(stdout)

    if graph == "":
        print("Could not find a digraph in the compiler output. Check that 'TINT_DUMP_UNIFORMITY_GRAPH' is set to 1 in 'src/tint/lang/wgsl/resolver/uniformity.cc'")
        exit(1)

    with tempfile.NamedTemporaryFile(delete=True, mode='w') as tmp_dot_file:
        tmp_dot_file.write(graph)
        tmp_dot_file.flush()

        output_name = args.o if args.o else Path(args.filename).with_suffix(".png").name
        process = subprocess.run(f"dot {tmp_dot_file.name} -Tpng -o {Path.cwd()}/{output_name}", shell=True)

if __name__ == '__main__':
    main()
