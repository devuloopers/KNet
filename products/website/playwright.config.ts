import { defineConfig, devices } from "@playwright/test";

/** Browser checks exercise the pre-rendered production output, not Vite's development page. */
export default defineConfig({
  testDir: "./tests/browser",
  fullyParallel: true,
  retries: process.env.CI ? 1 : 0,
  workers: 2,
  timeout: 60_000,
  reporter: [["list"], ["html", { open: "never" }]],
  // Functional/layout snapshots are deterministic; motion.spec.ts separately exercises live animation.
  use: {
    baseURL: "http://127.0.0.1:4174",
    trace: "retain-on-failure",
    reducedMotion: "reduce",
  },
  webServer: {
    command: "npm run preview",
    url: "http://127.0.0.1:4174/healthz",
    reuseExistingServer: false,
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
    { name: "firefox", use: { ...devices["Desktop Firefox"] } },
    { name: "webkit", use: { ...devices["Desktop Safari"] } },
  ],
});
