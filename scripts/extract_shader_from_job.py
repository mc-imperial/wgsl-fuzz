import json
import sys

if len(sys.argv) != 2:
    print("Usage: " + sys.argv[0] + " <job JSON file>")
    sys.exit(1)

print(json.loads(open(sys.argv[1], 'r').read())["shaderText"])
