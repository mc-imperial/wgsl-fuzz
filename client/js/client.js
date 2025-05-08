        const serverUrl = 'http://localhost:8080/json';

        async function startSessionWithServer() {
            var clientId = await fetch(serverUrl, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
		    type: "Register",
		    nickname: document.getElementById('nicknameInput').value
   		       })
                })
                .then(response => response.json())
                .then(jsonData => jsonData.clientId)
                .catch(err => showResponse('POST Error', err));

            console.log(clientId);

            var pollCount = 0;
            while (true) {
                await new Promise(r => setTimeout(r, 1000));                
                console.log("Polling: " + clientId + " " + pollCount);

                var msg = await fetch(serverUrl, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                type: "GetJob",
                clientId: clientId
                      })
                    })
                    .then(response => response.json())
                    .catch(err => showResponse('POST Error', err));
 
                if (msg.type == "Stop") {
                    console.log(clientId + " is stopping.");
                    break;
                }
                else if (msg.type == "RenderJob") {
                    var job = msg.job;
                    console.log(clientId + " got job")
                    var jobResult = await executeJob(job)
                    var ack = await fetch(serverUrl, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                    type: "JobResult",
                    clientId: clientId,
                    result: jobResult
                          })
                        })
                        .then(response => response.json())
                        .catch(err => showResponse('POST Error', err));
                    console.log(JSON.stringify(ack));
                }
                else {
                    console.log(clientId + " got unknown response: " + JSON.stringify(job, null, 2))
                }
            }
        }

        function canvasToPngJson(canvas) {
            const dataUrl = canvas.toDataURL('image/png'); // Returns "data:image/png;base64,..."
            const base64Data = dataUrl.split(',')[1]; // Strip metadata prefix
            return JSON.stringify({
                type: "image/png",
                encoding: "base64",
                data: base64Data
            });
        }

        function renderImage(job, device, canvas, canvasFormat, context) {
            const vertices = new Float32Array([
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
            
            device.queue.writeBuffer(vertexBuffer, /*bufferOffset=*/0, vertices);
      
            const vertexBufferLayout = {
              arrayStride: 8,
              attributes: [{
                format: "float32x2",
                offset: 0,
                shaderLocation: 0, // 'position' in vertex shader
              }],
            };
      
            const shaderModule = device.createShaderModule({
              label: "Shader",
              code: job.shaderText,
            });

            const uniformBufferBindingsOnDeviceByGroup = new Map()
            var maxBindGroup = 0;
            for (const uniformBufferForJob of job.uniformBuffers) {
                console.log("Processing a uniform buffer.");
                const length = uniformBufferForJob.data.length;
                const group = uniformBufferForJob.group;
                const binding = uniformBufferForJob.binding;
                maxBindGroup = Math.max(group, maxBindGroup);
                const uniformArray = new Uint8Array(Math.max(length, 16)); // 16 is minimum binding size - is this fixed?
                for (let i = 0; i < length; i++) {
                    uniformArray[i] = uniformBufferForJob.data[i];
                }
                console.log("Byte length: " + uniformArray.byteLength);
                const uniformBufferOnDevice = device.createBuffer({
                    label: "Uniform buffer, group " + group + ", binding " + binding,
                    size: uniformArray.byteLength,
                    usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST,
                });
                device.queue.writeBuffer(uniformBufferOnDevice, 0, uniformArray);
                if (!uniformBufferBindingsOnDeviceByGroup.has(group)) {
                    uniformBufferBindingsOnDeviceByGroup.set(group, []);
                    console.log("Added a uniform buffer at " + group + " so size of map is now: " + uniformBufferBindingsOnDeviceByGroup.size)
                }
                uniformBufferBindingsOnDeviceByGroup.get(group).push({
                    binding: binding,
                    resource: {
                        buffer: uniformBufferOnDevice
                    }
                });
            }
            console.log("Number of bind groups: " + uniformBufferBindingsOnDeviceByGroup.size)
      
            const pipeline = device.createRenderPipeline({
              label: "Pipeline",
              layout: "auto",
              vertex: {
                module: shaderModule,
                entryPoint: "vertexMain",
                buffers: [vertexBufferLayout]
              },
              fragment: {
                module: shaderModule,
                entryPoint: "fragmentMain",
                targets: [{
                  format: canvasFormat
                }]
              }
            });
      
            const encoder = device.createCommandEncoder();
      
            const pass = encoder.beginRenderPass({
              colorAttachments: [{
                view: context.getCurrentTexture().createView(),
                loadOp: "clear",
                clearValue: { r: 0.5, g: 0, b: 0.5, a: 1.0 },          
                storeOp: "store",
              }]
            });
      
            pass.setPipeline(pipeline);
            pass.setVertexBuffer(0, vertexBuffer);

            for (let i = 0; i <= maxBindGroup; i++) {
                if (!uniformBufferBindingsOnDeviceByGroup.has(i)) {
                    continue;
                }
                console.log("Getting bind group ready for group " + i);
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

            return canvasToPngJson(canvas)
        }

        function clearCanvas(device, context) {
            const encoder = device.createCommandEncoder();
            const pass = encoder.beginRenderPass({
              colorAttachments: [{
                view: context.getCurrentTexture().createView(),
                loadOp: "clear",
                clearValue: { r: 0, g: 0, b: 0.4, a: 1 },          
                storeOp: "store",
              }]
            });
            pass.end();
            device.queue.submit([encoder.finish()]);


        }

        async function executeJob(job) {
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


            jobResult = renderImage(job, device, canvas, canvasFormat, context);

            await new Promise(r => setTimeout(r, 1000));                

            clearCanvas(device, context);

            return jobResult;

        }

        function showResponse(title, data) {
            console.log(title + 'Response: ' + JSON.stringify(data, null, 2));
        }
