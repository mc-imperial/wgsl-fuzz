import { program } from "commander";
import puppeteer from "puppeteer-core";
import * as path from "node:path";
import * as fs from "node:fs";

program
  .requiredOption("--chrome <path>", "The path of chrome to use")
  .option("--chromeArgs <args...>", "Args to pass to chrome")
  .option("--jobFile <path>", "File path to the shader job html")
  .option("--jobDir <path>", "File path to a folder containing shader job html")
  .requiredOption("--outputDir <path>", "Path to output captured images");

program.parse(process.argv);

const options = program.opts();

const browser = await puppeteer.launch({
  executablePath: options.chrome,
  args: options.chromeArgs || [],
  // headless: false,
});
const page = await browser.newPage();

page.on("console", async (message) => {
  for (const log of message.args()) {
    console.log(
      "================== Web browser console message ======================"
    );
    console.log(await log.jsonValue());
    console.log(
      "====================================================================="
    );
  }
});

if (options.jobFile !== undefined) {
  const jobFileAbsolutePath = path.resolve(options.jobFile);
  await capturePage(jobFileAbsolutePath, options.outputDir);
}

if (options.jobDir !== undefined) {
  const jobDirAbsolutePath = path.resolve(options.jobDir);
  for (const file of fs.readdirSync(jobDirAbsolutePath)) {
    const fullPath = path.join(jobDirAbsolutePath, file);
    if (fs.statSync(fullPath).isFile() && path.extname(fullPath) === ".html") {
      await capturePage(fullPath, options.outputDir);
    }
  }
}

await browser.close();

async function capturePage(pagePath, outputDir) {
  const fileName = path.basename(pagePath, path.extname(pagePath));

  await page.goto("file://" + pagePath);

  const canvas = await page.waitForSelector("#thecanvas", { timeout: 1000 });

  await new Promise((resolve) => setTimeout(resolve, 500)); // Wait for half a second to render

  await canvas.screenshot({
    path: outputDir + "/" + fileName + ".png",
  });
}
