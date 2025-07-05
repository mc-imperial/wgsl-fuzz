/*
 * Copyright 2025 The wgsl-fuzz Project Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wgslfuzz.tools

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import java.io.File

fun main(args: Array<String>) {
    val parser = ArgParser("Tool for running shaders job via a server")

    val wgslFilePath by parser
        .option(
            ArgType.String,
            fullName = "wgslFile",
            description = "Wgsl file for page",
        ).required()

    val uniformJsonPath by parser
        .option(
            ArgType.String,
            fullName = "uniformJson",
            description = "Json file contain uniform data",
        ).required()

    val outputFilePath by parser
        .option(
            ArgType.String,
            fullName = "output",
            description = "File to write output html to",
        ).default("index.html")

    val watch by parser
        .option(
            ArgType.Boolean,
            fullName = "watch",
            description = "Set a file watcher so if either wgslFile or uniformJson changes output html updates",
        ).default(false)

    parser.parse(args)

    var wgslCode = File(wgslFilePath).readText()
    var uniformJson = File(uniformJsonPath).readText()
    outputHtml(wgslCode, uniformJson, outputFilePath)

    if (watch) {
        while (true) {
            Thread.sleep(500) // 0.5 seconds
            val newWgslCode = File(wgslFilePath).readText()
            val newUniformJson = File(uniformJsonPath).readText()
            if (wgslCode != newWgslCode || uniformJson != newUniformJson) {
                wgslCode = newWgslCode
                uniformJson = newUniformJson
                outputHtml(wgslCode, uniformJson, outputFilePath)
            }
        }
    }
}

fun outputHtml(
    wgslCode: String,
    uniformJson: String,
    outputFilePath: String,
) {
    File(outputFilePath)
        .writeText(
            renderStandAloneTemplate(
                wgslCode,
                uniformJson,
            ),
        )
}

fun renderStandAloneTemplate(
    wgslCode: String,
    uniformJson: String,
): String =
    generateHtml(
        """
const shaderWgsl = `
$wgslCode
`;

const uniformBuffers = $uniformJson;

$JAVASCRIPT_TEMPLATE

main();
        """,
    )

fun String.indent(n: Int): String = this.replace("\n", "\n" + " ".repeat(n))

const val JAVASCRIPT_TEMPLATE =
    """
function clearCanvas(device, context) {
  const encoder = device.createCommandEncoder();
  const pass = encoder.beginRenderPass({
    colorAttachments: [
      {
        view: context.getCurrentTexture().createView(),
        loadOp: "clear",
        clearValue: { r: 0, g: 0, b: 0.4, a: 1 },
        storeOp: "store",
      },
    ],
  });
  pass.end();
  device.queue.submit([encoder.finish()]);
}

function renderImage(device, canvas, canvasFormat, context) {
  // prettier-ignore
  const vertices = new Float32Array([
  //   X,    Y,
    -1.0, -1.0, // Triangle 1
    1.0, -1.0,
    1.0,  1.0,

    -1.0, -1.0, // Triangle 2
    1.0,  1.0,
    -1.0,  1.0,
  ]);

  const vertexBuffer = device.createBuffer({
    label: "Vertices",
    size: vertices.byteLength,
    usage: GPUBufferUsage.VERTEX | GPUBufferUsage.COPY_DST,
  });

  device.queue.writeBuffer(vertexBuffer, /*bufferOffset=*/ 0, vertices);

  const vertexBufferLayout = {
    arrayStride: 8,
    attributes: [
      {
        format: "float32x2",
        offset: 0,
        shaderLocation: 0, // Position in vertex shader
      },
    ],
  };

  const shaderModule = device.createShaderModule({
    label: "Shader",
    code: shaderWgsl,
  });

  const uniformBufferBindingsOnDeviceByGroup = new Map(); // Map of group number to (List of bindings for that group)
  var maxBindGroup = 0;
  for (const uniformBuffer of uniformBuffers) {
    const length = uniformBuffer.data.length;
    const group = uniformBuffer.group;
    const binding = uniformBuffer.binding;
    maxBindGroup = Math.max(group, maxBindGroup);
    const uniformArray = new Uint8Array(Math.max(length, 16)); // 16 is minimum binding size - is this fixed?
    for (let i = 0; i < length; i++) {
      uniformArray[i] = uniformBuffer.data[i];
    }
    const uniformBufferOnDevice = device.createBuffer({
      label: "Uniform buffer, group " + group + ", binding " + binding,
      size: uniformArray.byteLength,
      usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST,
    });
    device.queue.writeBuffer(uniformBufferOnDevice, 0, uniformArray);
    if (!uniformBufferBindingsOnDeviceByGroup.has(group)) {
      uniformBufferBindingsOnDeviceByGroup.set(group, []);
    }
    uniformBufferBindingsOnDeviceByGroup.get(group).push({
      binding: binding,
      resource: {
        buffer: uniformBufferOnDevice,
      },
    });
  }

  const pipeline = device.createRenderPipeline({
    label: "Pipeline",
    layout: "auto",
    vertex: {
      module: shaderModule,
      entryPoint: "vertexMain",
      buffers: [vertexBufferLayout],
    },
    fragment: {
      module: shaderModule,
      entryPoint: "fragmentMain",
      targets: [
        {
          format: canvasFormat,
        },
      ],
    },
  });

  const encoder = device.createCommandEncoder();

  const pass = encoder.beginRenderPass({
    colorAttachments: [
      {
        view: context.getCurrentTexture().createView(),
        loadOp: "clear",
        clearValue: { r: 0.5, g: 0, b: 0.5, a: 1.0 },
        storeOp: "store",
      },
    ],
  });

  pass.setPipeline(pipeline);
  pass.setVertexBuffer(0, vertexBuffer);

  for (let i = 0; i <= maxBindGroup; i++) {
    if (!uniformBufferBindingsOnDeviceByGroup.has(i)) {
      continue;
    }
    const entries = uniformBufferBindingsOnDeviceByGroup.get(i);
    const bindGroup = device.createBindGroup({
      label: "Bind group " + i,
      layout: pipeline.getBindGroupLayout(i),
      entries: entries,
    });
    pass.setBindGroup(i, bindGroup);
  }

  pass.draw(vertices.length / 2);
  pass.end();

  device.queue.submit([encoder.finish()]);
}

async function main() {
  const canvas = document.querySelector("canvas");

  if (!navigator.gpu) {
    throw new Error("WebGPU not supported on this browser.");
  }

  const adapter = await navigator.gpu.requestAdapter();
  if (!adapter) {
    throw new Error("No appropriate GPUAdapter found.");
  }

  const device = await adapter.requestDevice();

  const context = canvas.getContext("webgpu");
  const canvasFormat = navigator.gpu.getPreferredCanvasFormat();
  context.configure({
    device: device,
    format: canvasFormat,
  });

  clearCanvas(device, context);

  renderImage(device, canvas, canvasFormat, context);
}
    """

fun generateHtml(javascript: String): String =
    """
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>wgsl-fuzz Standalone Shader</title>
    <style>
      body {
        font-family: system-ui, sans-serif;
        padding: 2rem;
        background-color: #fafafa;
        color: #333;
        line-height: 1.6;
      }

      h1 {
        font-size: 1.8rem;
        margin-bottom: 1rem;
      }

      canvas {
        display: block;
        border: 1px solid #ccc;
        margin-top: 1rem;
      }

      pre#output {
        margin-top: 1.5rem;
        padding: 1rem;
        background-color: #f0f0f0;
        border-radius: 6px;
        white-space: pre-wrap;
        word-break: break-word;
      }
    </style>
  </head>
  <body>
    <main>
      <h1>wgsl-fuzz Standalone Shader</h1>
      <canvas
        id="thecanvas"
        width="256"
        height="256"
        aria-label="WebGPU rendering canvas"
      ></canvas>
    </main>
    <script>
      ${javascript.indent(6)}
    </script>
  </body>
</html>

    """
