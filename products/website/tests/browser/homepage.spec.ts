import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { site } from "../../src/content/site";

for (const width of [320, 375, 768, 1024, 1099, 1280, 1440, 1920]) {
  test(`layout remains contained and product artwork loads at ${width}px`, async ({
    page,
  }, info) => {
    await page.setViewportSize({ width, height: 1000 });
    const errors: string[] = [];
    page.on("pageerror", (error) => errors.push(error.message));
    await page.goto("/");
    await expect(page.getByRole("heading", { level: 1 })).toHaveText(
      "Understand traffic.Control every request.",
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
        link?.startsWith("#") ||
        link === site.repository ||
        link?.startsWith(site.repository + "/") ||
        link?.startsWith(site.repository + "#"),
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
    page.getByRole("heading", {
      name: "Test real network conditions.",
      exact: true,
    }),
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
  expect(
    addresses.some((url) => {
      const path = new URL(url).pathname;
      return path === "/api" || path.startsWith("/api/") || path === "/ws";
    }),
  ).toBe(false);
});

test("pre-rendered HTML, image variants, budgets and real 404s are present", async ({
  request,
}) => {
  const response = await request.get("/");
  expect(await response.text()).toContain('<main id="main">');
  expect(await response.text()).not.toContain("<!--app-html-->");
  expect(await response.text()).toContain("image/avif");
  const html = await response.text();
  expect((html.match(/<script\b/g) || []).length).toBe(1);
  expect(html).toMatch(/src="\/assets\/[^" ]+\.js"/);
  expect(html).not.toMatch(/\.wasm|react-dom|kotlin-runtime/);
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

test("browser title uses the exact regular hyphen", async ({ page }) => {
  await page.goto("/");
  await expect(page).toHaveTitle("KNet - Network Inspector & API Studio");
});

test("all five feature panels activate with accurate selected state and deferred local images", async ({
  page,
}) => {
  await page.goto("/");
  const labels = [
    "Traffic",
    "API Studio",
    "Intercepts",
    "Network Conditions",
    "Protected Traffic",
  ];
  await expect(
    page.getByRole("tab", { name: "Traffic", exact: true }),
  ).toHaveAttribute("aria-selected", "true");
  await expect(
    page.locator('[data-feature-panel="api-studio"] [data-deferred-image]'),
  ).not.toHaveAttribute("data-image-ready");
  for (const label of labels) {
    const tab = page.getByRole("tab", { name: label, exact: true });
    await tab.click();
    await expect(tab).toHaveAttribute("aria-selected", "true");
    const panel = page.getByRole("tabpanel");
    await expect(panel).toHaveCount(1);
    await expect(panel).toHaveAccessibleName(label);
    const image = panel.getByRole("img");
    await image.scrollIntoViewIfNeeded();
    await expect
      .poll(() =>
        image.evaluate((node) => (node as HTMLImageElement).naturalWidth),
      )
      .toBeGreaterThan(0);
    await expect(tab).toBeFocused();
  }
});

test("manual keyboard tab activation wraps without moving the selected panel prematurely", async ({
  page,
}) => {
  await page.goto("/");
  const studio = page.getByRole("tab", { name: "API Studio", exact: true });
  await studio.click();
  await page.keyboard.press("ArrowRight");
  const intercepts = page.getByRole("tab", { name: "Intercepts", exact: true });
  await expect(intercepts).toBeFocused();
  await expect(studio).toHaveAttribute("aria-selected", "true");
  await page.keyboard.press("Enter");
  await expect(intercepts).toHaveAttribute("aria-selected", "true");
  await page.keyboard.press("End");
  await expect(
    page.getByRole("tab", { name: "Protected Traffic", exact: true }),
  ).toBeFocused();
  await page.keyboard.press("ArrowRight");
  await expect(
    page.getByRole("tab", { name: "Traffic", exact: true }),
  ).toBeFocused();
  await page.keyboard.press(" ");
  await expect(page.getByRole("tabpanel")).toHaveAccessibleName("Traffic");
  await page.keyboard.press("Home");
  await page.keyboard.press("ArrowLeft");
  await expect(
    page.getByRole("tab", { name: "Protected Traffic", exact: true }),
  ).toBeFocused();
});

test("feature permalinks and hash changes select the intended content", async ({
  page,
}) => {
  await page.goto("/#feature-network-conditions");
  await expect(page.getByRole("tabpanel")).toHaveAccessibleName(
    "Network Conditions",
  );
  await page.evaluate(() => {
    location.hash = "feature-protected-traffic";
  });
  await expect(page.getByRole("tabpanel")).toHaveAccessibleName(
    "Protected Traffic",
  );
  await page.evaluate(() => {
    location.hash = "companion";
  });
  await expect(page.getByRole("tabpanel")).toHaveAccessibleName(
    "Protected Traffic",
  );
});

for (const width of [375, 768, 1280]) {
  test(`repeated panel changes keep stable geometry at ${width}px`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 950 });
    await page.goto("/");
    const heights: number[] = [];
    const imageOffsets: number[] = [];
    for (const label of [
      "API Studio",
      "Traffic",
      "Protected Traffic",
      "Network Conditions",
      "Intercepts",
      "API Studio",
    ]) {
      await page.getByRole("tab", { name: label, exact: true }).click();
      const bounds = await page.getByRole("tabpanel").boundingBox();
      heights.push(bounds!.height);
      const image = await page
        .getByRole("tabpanel")
        .locator(".feature-image")
        .boundingBox();
      imageOffsets.push(image!.y - bounds!.y);
    }
    expect(Math.max(...heights) - Math.min(...heights)).toBeLessThanOrEqual(2);
    expect(
      Math.max(...imageOffsets) - Math.min(...imageOffsets),
    ).toBeLessThanOrEqual(2);
  });
}

for (const size of [
  { width: 1100, height: 650 },
  { width: 1280, height: 720 },
  { width: 1600, height: 650 },
  { width: 1920, height: 1080 },
]) {
  test(`complete desktop hero fits initially at ${size.width}x${size.height}`, async ({
    page,
  }) => {
    await page.setViewportSize(size);
    await page.goto("/");
    const bounds = await page
      .getByRole("img", { name: /KNet Desktop showing/ })
      .boundingBox();
    expect(bounds!.y).toBeGreaterThanOrEqual(0);
    expect(bounds!.y + bounds!.height).toBeLessThanOrEqual(size.height + 1);
  });
}

test("no-script feature links expose all five captures and their descriptions", async ({
  browser,
}) => {
  const context = await browser.newContext({
    javaScriptEnabled: false,
    viewport: { width: 375, height: 812 },
  });
  try {
    const page = await context.newPage();
    await page.goto("http://127.0.0.1:4174/");
    await expect(page.getByRole("tab")).toHaveCount(0);
    await expect(page.locator("[data-feature-panel]")).toHaveCount(5);
    await page.locator('[data-feature-tab="protected-traffic"]').click();
    const panel = page.locator("#feature-protected-traffic");
    await expect(panel).toBeVisible();
    const image = panel.getByRole("img");
    await image.scrollIntoViewIfNeeded();
    await expect
      .poll(() =>
        image.evaluate((node) => (node as HTMLImageElement).naturalWidth),
      )
      .toBeGreaterThan(0);
  } finally {
    await context.close();
  }
});

test("touch selection works at narrow width without page overflow", async ({
  browser,
}) => {
  const context = await browser.newContext({
    hasTouch: true,
    viewport: { width: 375, height: 812 },
  });
  try {
    const page = await context.newPage();
    await page.goto("http://127.0.0.1:4174/");
    await page
      .getByRole("tab", { name: "Protected Traffic", exact: true })
      .tap();
    await expect(page.getByRole("tabpanel")).toHaveAccessibleName(
      "Protected Traffic",
    );
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
  } finally {
    await context.close();
  }
});

test("every selected panel passes accessibility checks", async ({ page }) => {
  await page.goto("/");
  for (const label of [
    "Traffic",
    "API Studio",
    "Intercepts",
    "Network Conditions",
    "Protected Traffic",
  ]) {
    await page.getByRole("tab", { name: label, exact: true }).click();
    const results = await new AxeBuilder({ page })
      .include("[data-feature-tour]")
      .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
      .analyze();
    expect(results.violations).toEqual([]);
  }
});

/** The full bitmap must fit its frame without hidden edges or empty letterbox bands. */
async function expectCompleteFeatureImage(
  image: import("@playwright/test").Locator,
) {
  // Resource completion also works with scripting disabled in Firefox, where decode() can stay pending.
  await expect
    .poll(() =>
      image.evaluate(
        (node) =>
          (node as HTMLImageElement).complete &&
          (node as HTMLImageElement).naturalWidth > 0,
      ),
    )
    .toBe(true);
  const geometry = await image.evaluate((node) => {
    const element = node as HTMLImageElement;
    const frame = element.closest(".feature-image")!;
    const imageBounds = element.getBoundingClientRect();
    const frameBounds = frame.getBoundingClientRect();
    const style = getComputedStyle(element);
    return {
      image: {
        top: imageBounds.top,
        bottom: imageBounds.bottom,
        left: imageBounds.left,
        right: imageBounds.right,
      },
      frame: {
        top: frameBounds.top,
        bottom: frameBounds.bottom,
        left: frameBounds.left,
        right: frameBounds.right,
      },
      borderWidth: parseFloat(getComputedStyle(frame).borderLeftWidth),
      borderHeight: parseFloat(getComputedStyle(frame).borderTopWidth),
      fit: style.objectFit,
      naturalWidth: element.naturalWidth,
      naturalHeight: element.naturalHeight,
    };
  });
  expect(geometry.naturalWidth).toBeGreaterThan(0);
  expect(geometry.naturalHeight).toBeGreaterThan(0);
  expect(geometry.fit).toBe("contain");
  expect(geometry.image.top).toBeGreaterThanOrEqual(geometry.frame.top);
  expect(geometry.image.bottom).toBeLessThanOrEqual(geometry.frame.bottom);
  expect(geometry.image.left).toBeGreaterThanOrEqual(geometry.frame.left);
  expect(geometry.image.right).toBeLessThanOrEqual(geometry.frame.right);
  // Check both the box and the actual contained bitmap: a wide box with contain still has side bands.
  const imageWidth = geometry.image.right - geometry.image.left;
  const imageHeight = geometry.image.bottom - geometry.image.top;
  const scale = Math.min(
    imageWidth / geometry.naturalWidth,
    imageHeight / geometry.naturalHeight,
  );
  expect(imageWidth - geometry.naturalWidth * scale).toBeLessThanOrEqual(2);
  expect(imageHeight - geometry.naturalHeight * scale).toBeLessThanOrEqual(2);
  expect(
    geometry.frame.right - geometry.frame.left - imageWidth,
  ).toBeLessThanOrEqual(geometry.borderWidth * 2 + 1);
  expect(
    geometry.frame.bottom - geometry.frame.top - imageHeight,
  ).toBeLessThanOrEqual(geometry.borderHeight * 2 + 1);
  return geometry;
}

for (const viewport of [
  { width: 1280, height: 600 },
  { width: 1600, height: 670 },
  { width: 1920, height: 800 },
  { width: 1440, height: 1000 },
  { width: 375, height: 812 },
  { width: 768, height: 950 },
  { width: 1099, height: 800 },
]) {
  test(`all feature screenshots retain their full edges at ${viewport.width}x${viewport.height}`, async ({
    page,
  }) => {
    await page.setViewportSize(viewport);
    await page.goto("/#features");
    const heights: number[] = [];
    for (const label of [
      "Traffic",
      "API Studio",
      "Intercepts",
      "Network Conditions",
      "Protected Traffic",
    ]) {
      await page.getByRole("tab", { name: label, exact: true }).click();
      const geometry = await expectCompleteFeatureImage(
        page.getByRole("tabpanel").getByRole("img"),
      );
      heights.push((await page.getByRole("tabpanel").boundingBox())!.height);
      if (viewport.width >= 1100) {
        expect(geometry.frame.top).toBeGreaterThanOrEqual(0);
        expect(geometry.frame.bottom).toBeLessThanOrEqual(viewport.height);
        expect(geometry.frame.bottom - geometry.frame.top).toBeGreaterThan(200);
      }
    }
    expect(Math.max(...heights) - Math.min(...heights)).toBeLessThanOrEqual(2);
  });
}

test("selected screenshot stays complete through window resizing and direct navigation", async ({
  page,
}) => {
  await page.setViewportSize({ width: 1600, height: 1000 });
  await page.goto("/#feature-network-conditions");
  const image = page.getByRole("tabpanel").getByRole("img");
  await image.evaluate((node) =>
    node.setAttribute("data-mounted-fit-check", "true"),
  );
  for (const viewport of [
    { width: 1600, height: 650 },
    { width: 375, height: 812 },
    { width: 1440, height: 900 },
  ]) {
    await page.setViewportSize(viewport);
    await page
      .locator("#features")
      .evaluate((node) => node.scrollIntoView({ block: "start" }));
    await expect(page.getByRole("tabpanel")).toHaveAccessibleName(
      "Network Conditions",
    );
    await expect(image).toHaveAttribute("data-mounted-fit-check", "true");
    const geometry = await expectCompleteFeatureImage(image);
    if (viewport.width >= 1100)
      expect(geometry.frame.bottom).toBeLessThanOrEqual(viewport.height);
  }
});

test("feature tabs use hero-sized titles and complete desktop screenshots below the tab row", async ({
  page,
}) => {
  await page.setViewportSize({ width: 1600, height: 670 });
  await page.goto("/#features");
  const heroFontSize = await page
    .locator("h1")
    .evaluate((node) => parseFloat(getComputedStyle(node).fontSize));
  for (const label of [
    "Traffic",
    "API Studio",
    "Intercepts",
    "Network Conditions",
    "Protected Traffic",
  ]) {
    await page.getByRole("tab", { name: label, exact: true }).click();
    const panel = page.getByRole("tabpanel");
    const title = panel.getByRole("heading");
    expect(
      await title.evaluate((node) =>
        parseFloat(getComputedStyle(node).fontSize),
      ),
    ).toBe(heroFontSize);
    const geometry = await expectCompleteFeatureImage(panel.getByRole("img"));
    expect(geometry.frame.right - geometry.frame.left).toBeGreaterThan(760);
    const titleBounds = (await title.boundingBox())!;
    const tabsBounds = (await page.getByRole("tablist").boundingBox())!;
    expect(titleBounds.x + titleBounds.width).toBeLessThan(geometry.frame.left);
    expect(tabsBounds.y + tabsBounds.height).toBeLessThan(geometry.frame.top);
    expect(geometry.frame.bottom).toBeLessThanOrEqual(670);
  }
});

test("no-script captures also preserve both aspect ratios inside their frames", async ({
  browser,
}) => {
  const context = await browser.newContext({
    javaScriptEnabled: false,
    viewport: { width: 1280, height: 720 },
  });
  try {
    const page = await context.newPage();
    await page.goto("http://127.0.0.1:4174/#features");
    for (const panel of await page.locator("[data-feature-panel]").all()) {
      const image = panel.getByRole("img");
      await image.scrollIntoViewIfNeeded();
      await expectCompleteFeatureImage(image);
    }
  } finally {
    await context.close();
  }
});
