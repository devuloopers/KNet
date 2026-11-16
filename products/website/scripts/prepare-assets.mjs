import { copyFile, mkdir, readFile, writeFile, rm } from "node:fs/promises";
import { createHash } from "node:crypto";
import sharp from "sharp";
import { readArtwork } from "./artwork.mjs";

const root = new URL("../", import.meta.url);
const brand = new URL("../../../desktopBrand/", import.meta.url);
// Validate before touching generated files so a rejected source cannot destroy the prior local preview.
const { manifest, images } = await readArtwork(new URL("artwork/", root));
await rm(new URL(".generated/preview.ts", root), { force: true });
await rm(new URL("public/generated", root), { recursive: true, force: true });
for (const directory of ["public/brand", "public/generated", ".generated"])
  await mkdir(new URL(directory, root), { recursive: true });
await copyFile(
  new URL("svg/knet-icon-dark.svg", brand),
  new URL("public/brand/knet-icon.svg", root),
);
await copyFile(
  new URL("svg/knet-icon-dark.svg", brand),
  new URL("public/favicon.svg", root),
);
await copyFile(
  new URL("kit/icon-180.png", brand),
  new URL("public/apple-touch-icon.png", root),
);
await sharp(await readFile(new URL("kit/og-image.png", brand)))
  .resize({ width: 1200 })
  .webp({ quality: 85 })
  .toFile(new URL("public/social-preview.webp", root).pathname);

// Source PNGs come from actual Compose components with authored demonstration state.
const artwork = {};
for (const [id, source] of Object.entries(images)) {
  const variants = { avif: [], webp: [] };
  let defaultPath = "";
  const widths =
    source.width < 1000 ? [420, 840] : [640, 1000, 1200, source.width];
  for (const width of [...new Set(widths)].filter(
    (width) => width <= source.width,
  )) {
    for (const format of ["avif", "webp"]) {
      const resized = sharp(source.bytes).resize({
        width,
        withoutEnlargement: true,
      });
      const buffer = await (
        format === "avif"
          ? resized.avif({ quality: 65, effort: 4 })
          : resized.webp({ quality: 88 })
      ).toBuffer();
      const hash = createHash("sha256")
        .update(buffer)
        .digest("hex")
        .slice(0, 12);
      const path = `/generated/${id}-${width}-${hash}.${format}`;
      await writeFile(new URL(`public${path}`, root), buffer);
      variants[format].push(`${path} ${width}w`);
      if (format === "webp" && width === (source.width < 1000 ? 840 : 1200))
        defaultPath = path;
    }
  }
  artwork[id] = {
    avif: variants.avif.join(", "),
    webp: variants.webp.join(", "),
    default: defaultPath,
    width: source.width,
    height: source.height,
  };
}
await writeFile(
  new URL(".generated/artwork.ts", root),
  `// Generated from verified actual Compose artwork.\nexport const artwork = ${JSON.stringify(artwork, null, 2)} as const;\n`,
);
// External CSS reserves each source ratio before deferred loading without weakening the inline-style CSP.
await writeFile(
  new URL(".generated/artwork.css", root),
  Object.entries(artwork)
    .map(
      ([id, source]) =>
        `[data-artwork="${id}"] { --artwork-ratio: ${source.width / source.height}; }`,
    )
    .join("\n"),
);
// Keep source provenance with the exact static/container output used for promotion.
await writeFile(
  new URL("public/generated/artwork-manifest.json", root),
  `${JSON.stringify(manifest, null, 2)}\n`,
);
