import json
import sys

if len(sys.argv) != 3:
    print("Usage: " + sys.argv[0] + " <job JSON file> <shader text file>")
    sys.exit(1)

jsonJob = json.loads(open(sys.argv[1], 'r').read())
shaderText = open(sys.argv[2], 'r').read()
jsonJob['shaderText'] = shaderText
open(sys.argv[1], 'w').write(json.dumps(jsonJob, indent=4))
