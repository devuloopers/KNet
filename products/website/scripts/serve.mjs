import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { resolve, sep } from "node:path";

const root = resolve("dist");
const types = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css",
  ".js": "application/javascript",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".webp": "image/webp",
  ".avif": "image/avif",
  ".xml": "application/xml",
  ".txt": "text/plain",
  ".json": "application/json",
};
const server = createServer(async (request, response) => {
  const pathname = new URL(request.url, "http://localhost").pathname;
  if (pathname === "/healthz") {
    response.writeHead(200, { "Content-Type": "text/plain" });
    response.end("ok\n");
    return;
  }
  let file;
  try {
    file = resolve(
      root,
      `.${decodeURIComponent(pathname === "/" ? "/index.html" : pathname)}`,
    );
  } catch {
    response.writeHead(400);
    response.end();
    return;
  }
  if (!file.startsWith(root + sep)) {
    response.writeHead(404);
    response.end();
    return;
  }
  try {
    const body = await readFile(file);
    const extension = file.slice(file.lastIndexOf("."));
    response.writeHead(200, {
      "Content-Type": types[extension] || "application/octet-stream",
      "Cache-Control": "no-store",
    });
    response.end(body);
  } catch {
    response.writeHead(404, { "Content-Type": "text/html" });
    response.end(await readFile(resolve(root, "404.html")));
  }
});
server.listen(Number(process.env.PORT || 4174), "127.0.0.1", () =>
  console.log("KNet website preview ready."),
);
for (const signal of ["SIGINT", "SIGTERM"])
  process.on(signal, () => server.close());
