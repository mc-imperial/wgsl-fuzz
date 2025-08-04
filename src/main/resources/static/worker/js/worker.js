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

function canvasToPngJson(canvas) {
  const [_, base64Data] = canvas.toDataURL("image/png").split(",", 2);
  return {
    type: "image/png",
    encoding: "base64",
    data: base64Data,
  };
}

async function renderImage(job, device, canvas) {

  const vertices = new Float32Array([
    -1.0,
    -1.0, // Triangle 1
    1.0,
    -1.0,
    1.0,
    1.0,

    -1.0,
    -1.0, // Triangle 2
    1.0,
    1.0,
    -1.0,
    1.0,
  ]);

  const renderTarget = device.createTexture({
    size: [canvas.width, canvas.height],
    format: "rgba8unorm",
    usage: GPUTextureUsage.RENDER_ATTACHMENT | GPUTextureUsage.TEXTURE_BINDING | GPUTextureUsage.COPY_SRC
  });

  const renderTargetView = renderTarget.createView();

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
        shaderLocation: 0, // 'position' in vertex shader
      },
    ],
  };

  device.pushErrorScope("validation");
  device.pushErrorScope("out-of-memory");
  device.pushErrorScope("internal");
  const shaderModule = device.createShaderModule({
    label: "Shader",
    code: job.shaderText,
  });
  const createShaderModuleErrorsInternal = device.popErrorScope()
  const createShaderModuleErrorsOutOfMemory = device.popErrorScope()
  const createShaderModuleErrorsValidation = device.popErrorScope()

  device.pushErrorScope("validation");
  device.pushErrorScope("out-of-memory");
  device.pushErrorScope("internal");
  const uniformBufferBindingsOnDeviceByGroup = new Map();
  var maxBindGroup = 0;
  for (const uniformBufferForJob of job.uniformBuffers) {
    const length = uniformBufferForJob.data.length;
    const group = uniformBufferForJob.group;
    const binding = uniformBufferForJob.binding;
    maxBindGroup = Math.max(group, maxBindGroup);
    const uniformArray = new Uint8Array(Math.max(length, 16)); // 16 is minimum binding size - is this fixed?
    for (let i = 0; i < length; i++) {
      uniformArray[i] = uniformBufferForJob.data[i];
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
          format: "rgba8unorm",
        },
      ],
    },
  });

  const renderToTextureEncoder = device.createCommandEncoder({
          label: "Render to texture encoder",
  });
  
  const pass = renderToTextureEncoder.beginRenderPass({
    label: "Render pass",
    colorAttachments: [
      {
        view: renderTargetView,
        clearValue: { r: 0.5, g: 0, b: 0.5, a: 1.0 },
        loadOp: 'clear',
        storeOp: 'store',
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

  device.queue.submit([renderToTextureEncoder.finish()]);

  const compilationInfo = await shaderModule.getCompilationInfo()

  vertexBuffer.destroy()

  const otherErrorsInternal = device.popErrorScope()
  const otherErrorsOutOfMemory = device.popErrorScope()
  const otherErrorsValidation = device.popErrorScope()

  const renderImageResult = {
    compilationMessages: compilationInfo.messages.map(message => ({
      message: message.message,
      type: message.type.toString(),
      lineNum: message.lineNum,
      linePos: message.linePos,
      offset: message.offset,
      length: message.length,
    }))
  }

  var errorOccurred = false;

  await createShaderModuleErrorsInternal.then((error) => {
    if (error) {
      renderImageResult.createShaderModuleInternalError = error.message;
      errorOccurred = true;
    }
  })

  await createShaderModuleErrorsOutOfMemory.then((error) => {
    if (error) {
      renderImageResult.createShaderModuleOutOfMemoryError = error.message;
      errorOccurred = true;
    }
  })

  await createShaderModuleErrorsValidation.then((error) => {
    if (error) {
      renderImageResult.createShaderModuleValidationError = error.message;
      errorOccurred = true;
    }
  })

  await otherErrorsInternal.then((error) => {
    if (error) {
      renderImageResult.otherInternalError = error.message;
      errorOccurred = true;
    }
  })

  await otherErrorsOutOfMemory.then((error) => {
    if (error) {
      renderImageResult.otherOutOfMemoryError = error.message;
      errorOccurred = true;
    }
  })

  await otherErrorsValidation.then((error) => {
    if (error) {
      renderImageResult.otherValidationError = error.message;
      errorOccurred = true;
    }
  })

  if (!errorOccurred) {
    const copyFromTextureEncoder = device.createCommandEncoder({
            label: "Copy from texture encoder",
    });
     const outputBuffer = device.createBuffer({
      size: canvas.width * canvas.height * 4, // 4 bytes per pixel
      usage: GPUBufferUsage.COPY_DST | GPUBufferUsage.MAP_READ,
    });
    copyFromTextureEncoder.copyTextureToBuffer(
      { texture: renderTarget },
      {
          buffer: outputBuffer,
          bytesPerRow: canvas.width * 4,
          rowsPerImage: canvas.height,
        },
        { width: canvas.width, height: canvas.height, depthOrArrayLayers: 1 }
      );
      device.queue.submit([copyFromTextureEncoder.finish()]);
      await outputBuffer.mapAsync(GPUMapMode.READ);
      const arrayBuffer = outputBuffer.getMappedRange();
      const pixelBytes = new Uint8Array(arrayBuffer.slice(0)); // Copy it out
      outputBuffer.unmap();
  
      const imageData = new ImageData(
        new Uint8ClampedArray(pixelBytes), // must be Uint8ClampedArray
        canvas.width,
        canvas.height
      );
      const context = canvas.getContext('2d');
      context.putImageData(imageData, 0, 0);
      renderImageResult.frame = canvasToPngJson(canvas);
    }
  
    return renderImageResult;
  }

async function executeJob(job, repetitions) {

  const jobResult = {
    fatalErrors: [],
    deviceLostReason: null,
    adapterInfo: null,
    renderImageResults: [],
  };

  const canvas = document.querySelector("canvas");

  if (!navigator.gpu) {
    jobResult.fatalErrors.push("WebGPU not supported on this browser.");
    return jobResult;
  }

  const adapter = await navigator.gpu.requestAdapter();
  if (!adapter) {
    jobResult.fatalErrors.push("No appropriate GPUAdapter found.");
    return jobResult;
  }

  const adapterInfo = adapter.info;
  jobResult.adapterInfo = {
    vendor: adapterInfo.vendor,
    architecture: adapterInfo.architecture,
    device: adapterInfo.device,
    description: adapterInfo.description,
  };

  const device = await adapter.requestDevice();

  const deviceLostPromise = device.lost.then((info) => {
    jobResult.deviceLostReason = info.reason;
    jobResult.fatalErrors.push(`Device lost: ${info.message}`);
  });  

  device.addEventListener('uncapturederror', (event) => {
    console.error('A WebGPU error was not captured: ', event.error)
    jobResult.fatalErrors.push(event.error.message)
  })

  for (let i = 0; i < repetitions; i++) {
    if (jobResult.fatalErrors.length > 0) {
      break;
    }
    try {
      jobResult.renderImageResults.push(await renderImage(job, device, canvas));
    } catch (err) {
      jobResult.fatalErrors.push(err?.message || String(err));      
    }
  }

  await deviceLostPromise.catch(() => {});
  
  if (jobResult.deviceLostReason != 'destroyed') {
    try {
      device.destroy();
    } catch (err) {
      console.warn("Failed to destroy device:", err);
    }
  }

  return jobResult;
}
