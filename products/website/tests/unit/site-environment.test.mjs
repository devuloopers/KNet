import { describe, expect, it } from "vitest";
import {
  siteEnvironment,
  metadataFor,
} from "../../scripts/site-environment.mjs";

describe("public metadata boundary", () => {
  it("defaults to an unindexable preview without an invented domain", () => {
    const configuration = siteEnvironment({});
    const metadata = metadataFor(configuration);
    expect(configuration.indexable).toBe(false);
    expect(metadata.head).toContain("noindex, nofollow");
    expect(metadata.head).not.toMatch(/canonical|localhost/);
    expect(metadata.robots).toContain("Disallow: /");
    expect(metadata.sitemap).toBeNull();
  });
  it("keeps previews unindexable even when a public origin is supplied", () => {
    expect(
      metadataFor(
        siteEnvironment({ KNET_SITE_ORIGIN: "https://preview.example.com" }),
      ).sitemap,
    ).toBeNull();
  });
  it("requires a public origin before producing production output", () => {
    expect(() => siteEnvironment({ KNET_SITE_ENV: "production" })).toThrow(
      "required",
    );
  });
  it.each([
    "http://knet.example.com",
    "https://localhost",
    "https://127.0.0.1",
    "https://192.168.0.1",
    "https://knet.local",
    "https://app.localhost",
    "https://knet.test",
    "https://[::1]",
    "https://knet.example.com:8080",
    "https://user:password@knet.example.com",
    "https://knet.example.com/site",
    "https://knet.example.com/?token=value",
    "https://knet.example.com/#fragment",
    "not a URL",
    "javascript:alert(1)",
  ])("rejects invalid, private or non-origin input: %s", (origin) => {
    expect(() =>
      siteEnvironment({
        KNET_SITE_ENV: "production",
        KNET_SITE_ORIGIN: origin,
      }),
    ).toThrow();
  });
  it("rejects an unknown stage rather than accidentally indexing it", () => {
    expect(() => siteEnvironment({ KNET_SITE_ENV: "prod" })).toThrow();
  });
  it("normalizes production URLs and keeps all social/crawl addresses consistent", () => {
    const configuration = siteEnvironment({
      KNET_SITE_ENV: "production",
      KNET_SITE_ORIGIN: "https://KNET.example.com/",
    });
    const metadata = metadataFor(configuration);
    expect(configuration.origin).toBe("https://knet.example.com");
    expect(metadata.head).toContain('href="https://knet.example.com/"');
    expect(metadata.head).toContain(
      "https://knet.example.com/social-preview.webp",
    );
    expect(metadata.robots).toContain(
      "Sitemap: https://knet.example.com/sitemap.xml",
    );
    expect(metadata.sitemap).toContain("<loc>https://knet.example.com/</loc>");
    expect(metadata.head).not.toContain("noindex");
  });
});
