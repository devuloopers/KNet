/** Validates only public build metadata. Deployment credentials never enter Vite configuration. */
export function siteEnvironment(environment) {
  const stage = environment.KNET_SITE_ENV || "preview";
  if (!["preview", "production"].includes(stage))
    throw new Error("KNET_SITE_ENV must be preview or production.");
  const supplied = environment.KNET_SITE_ORIGIN?.trim();
  if (!supplied) {
    if (stage === "production")
      throw new Error("KNET_SITE_ORIGIN is required for a production website.");
    return { stage, origin: null, indexable: false };
  }
  let url;
  try {
    url = new URL(supplied);
  } catch {
    throw new Error("KNET_SITE_ORIGIN must be a valid HTTPS origin.");
  }
  if (
    url.protocol !== "https:" ||
    url.username ||
    url.password ||
    url.pathname !== "/" ||
    url.search ||
    url.hash ||
    url.port
  ) {
    throw new Error(
      "KNET_SITE_ORIGIN must be an HTTPS origin without credentials, port, path, query or fragment.",
    );
  }
  const host = url.hostname.toLowerCase();
  if (
    !host.includes(".") ||
    /^\d+\.\d+\.\d+\.\d+$/.test(host) ||
    host.endsWith(".localhost") ||
    host.endsWith(".local") ||
    host.endsWith(".test") ||
    host.includes(":")
  ) {
    throw new Error("KNET_SITE_ORIGIN must use a public DNS hostname.");
  }
  return { stage, origin: url.origin, indexable: stage === "production" };
}

/** Produces crawl/social metadata only for explicitly configured public production output. */
export function metadataFor(configuration) {
  if (!configuration.indexable)
    return {
      head: '<meta name="robots" content="noindex, nofollow" />',
      robots: "User-agent: *\nDisallow: /\n",
      sitemap: null,
    };
  const origin = configuration.origin;
  const title = "KNet — Network Inspector &amp; API Studio";
  const description =
    "Inspect traffic. Replay requests. Test real conditions. A local-first network inspector and API studio.";
  return {
    head: `<meta name="robots" content="index, follow" />
<link rel="canonical" href="${origin}/" />
<meta property="og:type" content="website" />
<meta property="og:site_name" content="KNet" />
<meta property="og:title" content="${title}" />
<meta property="og:description" content="${description}" />
<meta property="og:url" content="${origin}/" />
<meta property="og:image" content="${origin}/social-preview.webp" />
<meta property="og:image:alt" content="KNet Network Inspector and API Studio" />
<meta name="twitter:card" content="summary_large_image" />
<meta name="twitter:title" content="${title}" />
<meta name="twitter:description" content="${description}" />
<meta name="twitter:image" content="${origin}/social-preview.webp" />`,
    robots: `User-agent: *\nAllow: /\nSitemap: ${origin}/sitemap.xml\n`,
    sitemap: `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"><url><loc>${origin}/</loc></url></urlset>\n`,
  };
}
