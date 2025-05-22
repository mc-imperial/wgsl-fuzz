const serverBase = "http://localhost:8080";

window.addEventListener("DOMContentLoaded", () => {
  startSessionWithServer().catch(err => log(`Fatal error: ${err.message}`));
});

async function startSessionWithServer() {
  const urlParams = new URLSearchParams(window.location.search);
  const name = urlParams.get("name");

  if (!name) {
    log("Missing 'name' parameter in URL.");
    return;
  }

  try {
    const res = await fetch(`${serverBase}/register`, {
      method: "POST",
      headers: { "Content-Type": "text/plain" },
      body: name,
    });
    const text = await res.text();
    log(`Register: ${text}`);
  } catch (err) {
    log(`Register request failed: ${err.message}`);
    return;
  }

  // The current amount of time for which the client will wait between poll requests.
  // This will double each time there is no response until a limit is hit.
  const pollTimeoutMin = 1;
  var pollTimeoutMillis = pollTimeoutMin;
  // This is the limit.
  const pollTimeoutMax = 8192;

  while (true) {
    // Ask the server for a job.
    try {
      const res = await fetch(`${serverBase}/job`, {
        method: "POST",
        headers: { "Content-Type": "text/plain" },
        body: name,
      });
      const jobJson = await res.json();

      log(`Message type: ${jobJson.type}`);

      switch (jobJson.type) {
        case "NoJob":
          // There was no job, so double the poll timeout, up to the maximum.
          pollTimeoutMillis = Math.min(pollTimeoutMax, pollTimeoutMillis * 2);
          break;

        case "RenderJob":
          // The server was responsive, so reset the poll timeout to its minimum as there may be more jobs
          // after this one.
          pollTimeoutMillis = pollTimeoutMin;
          const renderJobResult = await executeJob(jobJson.job, jobJson.repetitions);

          const ack = await fetch(`${serverBase}/renderjobresult`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              type: "RenderJobResult",
              clientName: name,
              renderJobResult: renderJobResult,
            }),
          });
          log(await ack.text());
          break;

        default:
          log(`Unknown message type received: ${jobJson.type}`);
          pollTimeoutMillis = Math.min(pollTimeoutMax, pollTimeoutMillis * 2);
          break;
      }
    } catch (err) {
      log(`Error fetching job: ${err.message}`);
      pollTimeoutMillis = Math.min(pollTimeoutMax, pollTimeoutMillis * 2);
    }

    await new Promise(resolve => setTimeout(resolve, pollTimeoutMillis));
  }
}

function canvasToPngJson(canvas) {
  const [_, base64Data] = canvas.toDataURL("image/png").split(",", 2);
  return {
    type: "image/png",
    encoding: "base64",
    data: base64Data,
  };
}

async function renderImage(job, device, canvas, canvasFormat, context) {

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
    renderImageResult.frame = canvasToPngJson(canvas);
  }

  return renderImageResult;
}

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

async function executeJob(job, repetitions) {
  const canvas = document.querySelector("canvas");

  if (!navigator.gpu) {
    throw new Error("WebGPU not supported on this browser.");
  }

  const adapter = await navigator.gpu.requestAdapter();
  if (!adapter) {
    throw new Error("No appropriate GPUAdapter found.");
  }

  const adapterInfo = adapter.info;

  var jobResult = {
    adapterInfo: {
      vendor: adapterInfo.vendor,
      architecture: adapterInfo.architecture,
      device: adapterInfo.device,
      description: adapterInfo.description,
    },
    renderImageResults: [],
  }

  const device = await adapter.requestDevice();

  var uncapturedErrors = []

  device.addEventListener('uncapturederror', (event) => {
    console.error('A WebGPU error was not captured: ', event.error)
    uncapturedErrors.push(event.error.message)
  })

  const context = canvas.getContext("webgpu");
  const canvasFormat = navigator.gpu.getPreferredCanvasFormat();
  context.configure({
    device: device,
    format: canvasFormat,
  });

  for (var i = 0; i < repetitions; i++) {
    clearCanvas(device, context);
    jobResult.renderImageResults.push(await renderImage(job, device, canvas, canvasFormat, context));
  }

  device.destroy()

  return jobResult;
}

function showResponse(title, data) {
  console.log(title + "Response: " + JSON.stringify(data, null, 2));
}

function log(message) {
  document.getElementById("output").textContent += message + "\n";
}
