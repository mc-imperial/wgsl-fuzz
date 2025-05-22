const MAX_LOG_LINES = 100;
const logLines = [];
const logContainer = document.getElementById("output");

function log(msg) {
  logLines.push(msg);
  if (logLines.length > MAX_LOG_LINES) {
    logLines.shift(); // Drop oldest
  }
  logContainer.textContent = logLines.join("\n");
}
