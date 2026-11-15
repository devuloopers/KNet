import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { site } from "../../src/content/site";

for (const width of [320, 375, 768, 1280, 1920]) {
  test(`layout remains contained and product artwork loads at ${width}px`, async ({
    page,
  }, info) => {
    await page.setViewportSize({ width, height: 1000 });
    const errors: string[] = [];
    page.on("pageerror", (error) => errors.push(error.message));
    await page.goto("/");
    await expect(page.getByRole("heading", { level: 1 })).toHaveText(
      "Your app’s traffic.Clearly understood.",
    );
    const image = page.getByRole("img", { name: /KNet Desktop showing/ });
    await expect(image).toBeVisible();
    await expect
      .poll(() =>
        image.evaluate((node) => (node as HTMLImageElement).naturalWidth),
      )
      .toBeGreaterThan(0);
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true);
    expect(
      await image.evaluate((node) => {
        const bounds = node.getBoundingClientRect();
        return Math.abs(bounds.width / bounds.height - 5 / 3) < 0.01;
      }),
    ).toBe(true);
    const download = page
      .getByRole("link", { name: site.downloadLabel })
      .filter({ visible: true });
    expect(await download.count()).toBeGreaterThan(0);
    for (const link of await download.all()) {
      const bounds = await link.boundingBox();
      expect(bounds?.x).toBeGreaterThanOrEqual(0);
      expect((bounds?.x || 0) + (bounds?.width || 0)).toBeLessThanOrEqual(
        width,
      );
    }
    expect(errors).toEqual([]);
    if ([375, 1280].includes(width))
      await page.screenshot({
        path: info.outputPath(`homepage-${width}.png`),
        fullPage: true,
      });
  });
}

test("all public actions use repository destinations and download clicks reach Releases", async ({
  page,
}) => {
  await page.goto("/");
  for (const link of await page
    .getByRole("link", { name: site.downloadLabel })
    .all())
    await expect(link).toHaveAttribute("href", site.releases);
  await expect(
    page.getByRole("link", { name: "Docs", exact: true }).first(),
  ).toHaveAttribute("href", site.docs);
  await expect(
    page.getByRole("link", { name: "GitHub", exact: true }).first(),
  ).toHaveAttribute("href", site.repository);
  await expect(
    page.getByRole("link", { name: "Releases", exact: true }),
  ).toHaveAttribute("href", site.releases);
  const links = await page
    .locator("a[href]")
    .evaluateAll((nodes) => nodes.map((node) => node.getAttribute("href")));
  expect(
    links.every(
      (link) =>
        link === "/" ||
        link === "#main" ||
        [site.docs, site.repository, site.releases].includes(
          link as typeof site.docs,
        ),
    ),
  ).toBe(true);
  await page.route("https://github.com/**", (route) =>
    route.fulfill({
      contentType: "text/html",
      body: "<h1>GitHub Releases destination</h1>",
    }),
  );
  await page.getByRole("link", { name: site.downloadLabel }).first().click();
  await expect(page).toHaveURL(site.releases);
});

test("no-JavaScript and third-party outage leave the entire page usable", async ({
  browser,
}) => {
  const context = await browser.newContext({ javaScriptEnabled: false });
  const page = await context.newPage();
  await page.route("**/*", (route) =>
    new URL(route.request().url()).hostname === "127.0.0.1"
      ? route.continue()
      : route.abort(),
  );
  await page.goto("http://127.0.0.1:4174/");
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Network conditions", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: site.downloadLabel }).last(),
  ).toHaveAttribute("href", site.releases);
  expect(
    await page
      .getByRole("img", { name: /KNet Desktop showing/ })
      .evaluate((node) => (node as HTMLImageElement).complete),
  ).toBe(true);
  await context.close();
});

test("keyboard skip link and focus remain visible", async ({
  page,
  browserName,
}) => {
  await page.goto("/");
  // Safari on macOS uses Option-Tab to include links with its default keyboard preferences.
  await page.keyboard.press(
    browserName === "webkit" && process.platform === "darwin"
      ? "Alt+Tab"
      : "Tab",
  );
  await expect(
    page.getByRole("link", { name: "Skip to content" }),
  ).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(page).toHaveURL(/#main$/);
  await page.getByRole("link", { name: site.downloadLabel }).last().focus();
  await expect(
    page.getByRole("link", { name: site.downloadLabel }).last(),
  ).toBeFocused();
  expect(
    await page
      .getByRole("link", { name: site.downloadLabel })
      .last()
      .evaluate((node) => getComputedStyle(node).outlineStyle),
  ).not.toBe("none");
});

test("light system settings and reduced motion preserve KNet styling", async ({
  page,
}) => {
  await page.emulateMedia({ colorScheme: "light", reducedMotion: "reduce" });
  await page.goto("/");
  expect(
    await page
      .locator("html")
      .evaluate((node) => getComputedStyle(node).backgroundColor),
  ).toBe("rgb(11, 16, 22)");
  expect(
    await page
      .getByRole("link", { name: site.downloadLabel })
      .last()
      .evaluate((node) => getComputedStyle(node).transitionDuration),
  ).toBe("0s");
});

test("200 percent zoom equivalent retains readable contained actions", async ({
  page,
}) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.goto("/");
  await page.locator("html").evaluate((node) => {
    node.style.zoom = "2";
  });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await expect(
    page.getByRole("link", { name: site.downloadLabel }).last(),
  ).toBeVisible();
});

test("accessible landmarks and contrast pass automated checks", async ({
  page,
}) => {
  await page.goto("/");
  const results = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
    .analyze();
  expect(results.violations).toEqual([]);
});

test("homepage performs no external network calls and assets all resolve", async ({
  page,
}) => {
  const addresses: string[] = [];
  const failures: string[] = [];
  page.on("request", (request) => addresses.push(request.url()));
  page.on("response", (response) => {
    if (response.status() >= 400) failures.push(response.url());
  });
  await page.goto("/");
  await page.waitForLoadState("networkidle");
  expect(
    addresses.every((url) => url.startsWith("http://127.0.0.1:4174/")),
  ).toBe(true);
  expect(failures).toEqual([]);
  expect(addresses.some((url) => /\/api|\/ws|releases\?/.test(url))).toBe(
    false,
  );
});

test("pre-rendered HTML, image variants, budgets and real 404s are present", async ({
  request,
}) => {
  const response = await request.get("/");
  expect(await response.text()).toContain('<main id="main">');
  expect(await response.text()).not.toContain("<!--app-html-->");
  expect(await response.text()).toContain("image/avif");
  expect(await response.text()).not.toContain("<script");
  const robots = await request.get("/robots.txt");
  expect(await robots.text()).toMatch(/User-agent:/);
  const budget = (await (await request.get("/asset-budget.json")).json()) as {
    compressedJavaScriptAndCssBytes: number;
    defaultHeroBytes: number;
  };
  expect(budget.compressedJavaScriptAndCssBytes).toBeLessThan(150 * 1024);
  expect(budget.defaultHeroBytes).toBeLessThan(400 * 1024);
  expect((await request.get("/missing-page")).status()).toBe(404);
  expect((await request.get("/assets/missing.js")).status()).toBe(404);
});
