import { execFileSync } from "node:child_process";
import assert from "node:assert/strict";
import { setTimeout } from "node:timers/promises";
import { mkdir } from "node:fs/promises";
import { chromium } from "@playwright/test";

const image = process.env.KNET_WEBSITE_IMAGE || "knet-website:verification";
const docker = (args) =>
  execFileSync("docker", args, { encoding: "utf8", timeout: 600_000 }).trim();
if (process.env.KNET_CONTAINER_SKIP_BUILD !== "true") {
  docker([
    "build",
    "-f",
    new URL("../Dockerfile", import.meta.url).pathname,
    "-t",
    image,
    "--build-arg",
    `KNET_SITE_ENV=${process.env.KNET_SITE_ENV || "preview"}`,
    "--build-arg",
    `KNET_SITE_ORIGIN=${process.env.KNET_SITE_ORIGIN || ""}`,
    "--build-arg",
    `KNET_ARTWORK_REQUIRE_FRESH=${process.env.KNET_ARTWORK_REQUIRE_FRESH || "false"}`,
    "--build-arg",
    `KNET_ARTWORK_SOURCE_SHA=${process.env.KNET_ARTWORK_SOURCE_SHA || ""}`,
    "--build-arg",
    `KNET_ARTWORK_SOURCE_DIGEST=${process.env.KNET_ARTWORK_SOURCE_DIGEST || ""}`,
    "../..",
  ]);
}
docker(["run", "--rm", image, "-t"]);
const container = docker([
  "run",
  "--detach",
  "--rm",
  "--read-only",
  "--tmpfs",
  "/tmp:rw,noexec,nosuid,size=16m",
  "--cap-drop=ALL",
  "--security-opt=no-new-privileges",
  "--publish",
  "127.0.0.1::8080",
  image,
]);
try {
  const port = docker(["port", container, "8080/tcp"]).split(":").at(-1);
  const base = `http://127.0.0.1:${port}`;
  let healthy = false;
  for (let attempt = 0; attempt < 50; attempt++) {
    try {
      healthy = (await fetch(`${base}/healthz`)).ok;
    } catch {
      /* Nginx may still be binding. */
    }
    if (healthy) break;
    await setTimeout(200);
  }
  assert.ok(healthy, "The isolated production container must become healthy.");
  assert.equal(docker(["exec", container, "id", "-u"]), "101");
  const response = await fetch(base);
  const html = await response.text();
  assert.equal(response.status, 200);
  assert.match(html, /<main id="main">/);
  assert.match(html, /Download on GitHub/);
  assert.match(response.headers.get("content-type"), /text\/html/);
  assert.equal(response.headers.get("x-content-type-options"), "nosniff");
  assert.equal(response.headers.get("x-frame-options"), "DENY");
  assert.match(
    response.headers.get("content-security-policy"),
    /frame-ancestors 'none'/,
  );
  assert.equal(response.headers.get("cache-control"), "no-cache");
  const css = html.match(/href="(\/assets\/[^"]+\.css)"/)[1];
  const script = html.match(/src="(\/assets\/[^" ]+\.js)"/)[1];
  assert.match(
    response.headers.get("content-security-policy"),
    /script-src 'self'/,
  );
  assert.equal(
    (html.match(/<script\b/g) || []).length,
    1,
    "Only the local tabs/motion enhancement ships.",
  );
  for (const [path, type] of [
    [css, /text\/css/],
    [script, /(javascript|ecmascript)/],
  ]) {
    const asset = await fetch(`${base}${path}`, {
      headers: { "Accept-Encoding": "gzip" },
    });
    assert.equal(asset.status, 200);
    assert.match(asset.headers.get("content-type"), type);
    assert.match(asset.headers.get("cache-control"), /immutable/);
    assert.equal(asset.headers.get("content-encoding"), "gzip");
    assert.equal(asset.headers.get("x-content-type-options"), "nosniff");
  }
  const hero = html.match(/src="(\/generated\/[^"]+\.webp)"/)[1];
  const picture = await fetch(`${base}${hero}`);
  assert.match(picture.headers.get("content-type"), /image\/webp/);
  assert.match(picture.headers.get("cache-control"), /immutable/);
  for (const path of [
    "/missing-page",
    "/assets/missing.js",
    "/generated/missing.webp",
    "/api",
    "/ws",
    "/.env",
  ]) {
    assert.equal(
      (await fetch(`${base}${path}`)).status,
      404,
      `Unexpected public resource: ${path}`,
    );
  }
  assert.match(await (await fetch(`${base}/robots.txt`)).text(), /User-agent:/);
  // Check the actual static page against the CSP/cache/static-hosting boundary too.
  const browser = await chromium.launch();
  try {
    const page = await browser.newPage({
      viewport: { width: 1280, height: 900 },
    });
    await page.emulateMedia({ reducedMotion: "no-preference" });
    const errors = [];
    page.on("pageerror", (error) => errors.push(error.message));
    page.on("console", (message) => {
      if (message.type() === "error") errors.push(message.text());
    });
    await page.goto(base);
    await page
      .getByRole("img", { name: /KNet Desktop showing/ })
      .evaluate((image) => image.decode());
    assert.equal(
      await page
        .getByRole("link", { name: "Download on GitHub" })
        .last()
        .getAttribute("href"),
      "https://github.com/devuloopers/KNet/releases",
    );
    assert.equal(
      await page.getByRole("tabpanel").getAttribute("aria-labelledby"),
      "tab-traffic",
    );
    const nativeMotion = await page.evaluate(() => {
      document.querySelector('[data-feature-tab="network-conditions"]').click();
      const selected = document.querySelector(
        '[data-feature-panel="network-conditions"]',
      );
      const previous = document.querySelector('[data-feature-panel="traffic"]');
      return (
        selected.getAnimations().length > 0 &&
        previous.inert &&
        previous.getAttribute("aria-hidden") === "true"
      );
    });
    assert.ok(
      nativeMotion,
      "Native tab motion must execute under the production CSP.",
    );
    assert.equal(await page.getByRole("tabpanel").count(), 1);
    await page.getByRole("tab", { name: "API Studio", exact: true }).click();
    // Capture every static section, without preserving partially animated/offscreen reveal states.
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.waitForFunction(
      () =>
        !document.documentElement.hasAttribute("data-motion-enabled") &&
        document.getAnimations().length === 0,
    );
    await mkdir("test-results/container", { recursive: true });
    await page.screenshot({
      path: "test-results/container/homepage-desktop.png",
      fullPage: true,
    });
    await page.setViewportSize({ width: 375, height: 812 });
    await page.screenshot({
      path: "test-results/container/homepage-mobile.png",
      fullPage: true,
    });
    assert.equal(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
      true,
    );
    assert.deepEqual(
      errors,
      [],
      "Production CSP and static rendering must not emit browser errors.",
    );
  } finally {
    await browser.close();
  }
  console.log(
    "Production container checks passed: non-root/read-only runtime, health, headers, compression, caching, six 404 boundaries and accessible static-page tabs under CSP.",
  );
} finally {
  docker(["rm", "--force", container]);
}
