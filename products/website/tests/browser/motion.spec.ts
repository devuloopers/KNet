import { expect, test, type Page } from "@playwright/test";

// Functional snapshots use reduced motion; these cases exercise real native browser effects.
test.use({ reducedMotion: "no-preference" });

/** Avoid smooth-anchor travel while measuring the separate tab/reveal effects. */
async function openFeatures(page: Page) {
  await page.goto("/#features");
  await page
    .locator("#features")
    .evaluate((node) =>
      node.scrollIntoView({ behavior: "instant", block: "start" }),
    );
  await settle(page);
}

/** Completed/cancelled native effects must not remain running after an interaction. */
async function settle(page: Page) {
  await expect
    .poll(() =>
      page.evaluate(
        () =>
          document
            .getAnimations()
            .filter((animation) => animation.playState === "running").length,
      ),
    )
    .toBe(0);
}

test("tab crossfade switches accessibility immediately without shifting the layout", async ({
  page,
}) => {
  await openFeatures(page);
  const transition = await page.evaluate(() => {
    const previous = document.querySelector<HTMLElement>(
      '[data-feature-panel="traffic"]',
    )!;
    const next = document.querySelector<HTMLElement>(
      '[data-feature-panel="api-studio"]',
    )!;
    const height = previous.getBoundingClientRect().height;
    document
      .querySelector<HTMLAnchorElement>('[data-feature-tab="api-studio"]')!
      .click();
    return {
      previousInert: previous.inert,
      previousHidden: previous.getAttribute("aria-hidden"),
      outgoing: previous.hasAttribute("data-exiting"),
      incoming: next.getAnimations().length,
      previousOpacity: Number(getComputedStyle(previous).opacity),
      nextOpacity: Number(getComputedStyle(next).opacity),
      height,
      nextHeight: next.getBoundingClientRect().height,
    };
  });
  expect(transition.previousInert).toBe(true);
  expect(transition.previousHidden).toBe("true");
  expect(transition.outgoing).toBe(true);
  expect(transition.incoming).toBe(1);
  expect(transition.previousOpacity).toBeGreaterThan(0);
  expect(transition.nextOpacity).toBeLessThan(1);
  expect(transition.nextHeight).toBeCloseTo(transition.height, 1);
  await expect(page.getByRole("tabpanel")).toHaveAccessibleName("API Studio");
  await settle(page);
  await expect(page.locator("[data-exiting]")).toHaveCount(0);
  await expect(page.locator('[data-feature-panel="traffic"]')).toBeHidden();
  expect(
    await page
      .getByRole("tabpanel")
      .evaluate((node) => getComputedStyle(node).opacity),
  ).toBe("1");
});

test("rapid changes and repeated clicks leave one selected panel and no stale transition", async ({
  page,
}) => {
  await openFeatures(page);
  const maximum = await page.evaluate(() => {
    let maximum = 0;
    for (const id of [
      "api-studio",
      "intercepts",
      "traffic",
      "protected-traffic",
      "network-conditions",
      "api-studio",
      "traffic",
      "traffic",
    ]) {
      document
        .querySelector<HTMLAnchorElement>(`[data-feature-tab="${id}"]`)!
        .click();
      maximum = Math.max(
        maximum,
        [
          ...document.querySelectorAll<HTMLElement>("[data-feature-panel]"),
        ].reduce((count, panel) => count + panel.getAnimations().length, 0),
      );
    }
    return maximum;
  });
  expect(maximum).toBeLessThanOrEqual(2);
  await settle(page);
  await expect(page.getByRole("tabpanel")).toHaveAccessibleName("Traffic");
  await expect(page.locator("[data-exiting]")).toHaveCount(0);
  await expect(
    page.locator('[data-feature-panel][aria-hidden="false"]'),
  ).toHaveCount(1);
  await expect(page.locator("[data-feature-panel][inert]")).toHaveCount(4);
});

test("offscreen sections reveal only once and release their animations", async ({
  page,
}) => {
  await page.goto("/");
  const closing = page.locator(".closing");
  await expect(closing).toHaveAttribute("data-reveal-pending", "");
  await closing.evaluate((node) => {
    const element = node as HTMLElement;
    const original = element.animate;
    let count = 0;
    element.animate = function (keyframes, options) {
      element.dataset.testRevealCount = String(++count);
      return original.call(element, keyframes, options);
    };
    element.scrollIntoView({ behavior: "instant", block: "center" });
  });
  await expect(closing).toHaveAttribute("data-revealed", "");
  await expect(closing).toHaveAttribute("data-test-reveal-count", "1");
  await settle(page);
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: "instant" }));
  await closing.evaluate((node) =>
    node.scrollIntoView({ behavior: "instant", block: "center" }),
  );
  await settle(page);
  await expect(closing).toHaveAttribute("data-test-reveal-count", "1");
  expect(await closing.evaluate((node) => getComputedStyle(node).opacity)).toBe(
    "1",
  );
});

test("keyboard focus immediately reveals offscreen controls", async ({
  page,
}) => {
  await page.goto("/");
  const closing = page.locator(".closing");
  await expect(closing).toHaveAttribute("data-reveal-pending", "");
  await closing.getByRole("link", { name: "Download on GitHub" }).focus();
  await expect(closing).not.toHaveAttribute("data-reveal-pending");
  expect(await closing.evaluate((node) => getComputedStyle(node).opacity)).toBe(
    "1",
  );
});

test("reduced motion works initially and cancels in-flight effects when changed", async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/");
  await expect(page.locator("html")).not.toHaveAttribute("data-motion-enabled");
  await expect(page.locator("[data-reveal-pending]")).toHaveCount(0);
  await page.emulateMedia({ reducedMotion: "no-preference" });
  await expect(page.locator("html")).toHaveAttribute("data-motion-enabled", "");
  await page.evaluate(() =>
    document
      .querySelector<HTMLAnchorElement>('[data-feature-tab="api-studio"]')!
      .click(),
  );
  await page.emulateMedia({ reducedMotion: "reduce" });
  await settle(page);
  await expect(
    page.locator("[data-exiting], [data-reveal-pending]"),
  ).toHaveCount(0);
  await expect(page.getByRole("tabpanel")).toHaveAccessibleName("API Studio");
  expect(
    await page
      .locator("html")
      .evaluate((node) => getComputedStyle(node).scrollBehavior),
  ).toBe("auto");
  expect(
    await page
      .getByRole("tabpanel")
      .evaluate((node) => getComputedStyle(node).opacity),
  ).toBe("1");
  await page.emulateMedia({ reducedMotion: "no-preference" });
  await expect(page.locator("html")).toHaveAttribute("data-motion-enabled", "");
  await expect(page.locator("[data-reveal-pending]")).toHaveCount(0);
});

test("missing animation and observer APIs retain visible functional content", async ({
  page,
}) => {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.addInitScript(() => {
    Object.defineProperty(window, "IntersectionObserver", { value: undefined });
    Object.defineProperty(Element.prototype, "animate", { value: undefined });
  });
  await page.goto("/");
  await expect(page.getByRole("tabpanel")).toHaveAccessibleName("Traffic");
  await expect(page.locator("[data-reveal-pending]")).toHaveCount(0);
  await page.getByRole("tab", { name: "API Studio", exact: true }).click();
  await expect(page.getByRole("tabpanel")).toHaveAccessibleName("API Studio");
  await expect(page.locator("[data-exiting]")).toHaveCount(0);
  expect(errors).toEqual([]);
});

test("navigation anchors scroll smoothly to the requested section", async ({
  page,
}) => {
  await page.goto("/");
  const positions = await page.evaluate(async () => {
    const target = document.getElementById("features")!;
    document
      .querySelector<HTMLAnchorElement>('.site-header a[href="#features"]')!
      .click();
    const positions: number[] = [];
    let stableFrames = 0;
    for (let frame = 0; frame < 180; frame++) {
      await new Promise(requestAnimationFrame);
      stableFrames =
        Math.abs(window.scrollY - (positions.at(-1) ?? 0)) < 0.1
          ? stableFrames + 1
          : 0;
      positions.push(window.scrollY);
      if (
        stableFrames >= 3 &&
        Math.abs(target.getBoundingClientRect().top - 24) < 2
      )
        break;
    }
    return { positions, finalTop: target.getBoundingClientRect().top };
  });
  expect(
    positions.positions.some(
      (position) => position > 0 && position < positions.positions.at(-1)! - 2,
    ),
  ).toBe(true);
  expect(positions.finalTop).toBeCloseTo(24, 0);
  await expect(page).toHaveURL(/#features$/);
});

test("interactive hover feedback settles after the pointer leaves", async ({
  page,
}) => {
  await page.goto("/");
  const button = page.locator(".hero .download-link");
  await button.hover();
  await expect
    .poll(() =>
      button.evaluate((node) => {
        const transform = getComputedStyle(node).transform;
        return transform === "none" ? 0 : new DOMMatrix(transform).m42;
      }),
    )
    .toBeLessThan(-1.5);
  await page.mouse.move(0, 0);
  await settle(page);
  expect(
    await button.evaluate((node) => getComputedStyle(node).transform),
  ).toBe("none");
});
