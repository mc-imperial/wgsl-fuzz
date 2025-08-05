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

import { executeJob } from "./worker.js";
import { log } from "./logger.js";

const serverBase = `${window.location.protocol}//${window.location.host}`;

window.addEventListener("DOMContentLoaded", () => {
  startSessionWithServer().catch((err) => log(`Fatal error: ${err.message}`));
});

async function startSessionWithServer() {
  const urlParams = new URLSearchParams(window.location.search);
  const name = urlParams.get("name");

  if (!name) {
    log("Missing 'name' parameter in URL.");
    return;
  }

  // The current amount of time for which the worker will wait between poll requests.
  // This will double each time there is no response until a limit is hit.
  const pollTimeoutMin = 1;
  var pollTimeoutMillis = pollTimeoutMin;
  // This is the limit.
  const pollTimeoutMax = 8192;

  while (true) {
    // Ask the server for a job.
    try {
      const response = await fetch(`${serverBase}/worker-request-job`, {
        method: "POST",
        headers: { "Content-Type": "text/plain" },
        body: name,
      });

      const responseContentType = response.headers.get("Content-Type") || "";
      if (!responseContentType.includes("application/json")) {
        log(
          `A non-JSON response was received from the server; something is wrong - ending session.`,
        );
        return;
      }

      const responseJson = await response.json();
      log(`Message type: ${responseJson.type}`);

      switch (responseJson.type) {
        case "NoJob":
          // There was no job, so double the poll timeout, up to the maximum.
          pollTimeoutMillis = Math.min(pollTimeoutMax, pollTimeoutMillis * 2);
          break;

        case "RenderJob":
          // The server was responsive, so reset the poll timeout to its minimum as there may be more jobs
          // after this one.
          pollTimeoutMillis = pollTimeoutMin;
          const renderJobResult = await executeJob(
            responseJson.content.job,
            responseJson.content.repetitions,
          );

          const ack = await fetch(`${serverBase}/worker-job-result`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              type: "RenderJobResult",
              workerName: name,
              jobId: responseJson.content.jobId,
              renderJobResult: renderJobResult,
            }),
          });
          log(JSON.stringify(await ack.json()));
          break;

        default:
          log(`Unknown message received: ${JSON.stringify(jobJson)}`);
          pollTimeoutMillis = Math.min(pollTimeoutMax, pollTimeoutMillis * 2);
          break;
      }
    } catch (err) {
      log(`Error fetching job: ${err.message}`);
      pollTimeoutMillis = Math.min(pollTimeoutMax, pollTimeoutMillis * 2);
    }

    await new Promise((resolve) => setTimeout(resolve, pollTimeoutMillis));
  }
}
