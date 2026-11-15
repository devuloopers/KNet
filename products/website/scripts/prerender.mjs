import { readFile, writeFile, rm } from "node:fs/promises";
import { loadEnv } from "vite";
import { render } from "../.prerender/entry-server.js";
import { metadataFor, siteEnvironment } from "./site-environment.mjs";

const configuration = siteEnvironment({
  ...loadEnv("production", process.cwd(), "KNET_"),
  ...process.env,
});
const metadata = metadataFor(configuration);
const template = await readFile("dist/index.html", "utf8");
if (
  !template.includes("<!--app-html-->") ||
  !template.includes("<!--site-metadata-->")
)
  throw new Error("The static render placeholders are missing.");
await writeFile(
  "dist/index.html",
  template
    .replace("<!--app-html-->", render())
    .replace("<!--site-metadata-->", metadata.head),
);
await writeFile("dist/robots.txt", metadata.robots);
if (metadata.sitemap) await writeFile("dist/sitemap.xml", metadata.sitemap);
else await rm("dist/sitemap.xml", { force: true });
await writeFile(
  "dist/404.html",
  `<!doctype html><html lang="en"><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="robots" content="noindex"><title>Page not found — KNet</title><link rel="stylesheet" href="${template.match(/href="(\/assets\/[^"]+\.css)"/)[1]}"><body class="not-found"><main><h1>Page not found</h1><p><a href="/">Back to KNet</a></p></main></body></html>`,
);
console.log(`Static homepage rendered (${configuration.stage}).`);
