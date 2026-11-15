import { copyFile, mkdir, readFile, writeFile, rm } from "node:fs/promises";
import { createHash } from "node:crypto";
import sharp from "sharp";

const root = new URL("../", import.meta.url);
const brand = new URL("../../../desktopBrand/", import.meta.url);
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

// The screenshot is a deterministic render of actual Compose components with sample data only.
const source = await readFile(new URL("artwork/traffic-preview.png", root));
const preview = { avif: [], webp: [], default: "" };
for (const width of [800, 1200, 1600]) {
  for (const format of ["avif", "webp"]) {
    const resized = sharp(source).resize({ width });
    const buffer = await (
      format === "avif"
        ? resized.avif({ quality: 65, effort: 5 })
        : resized.webp({ quality: 88 })
    ).toBuffer();
    const hash = createHash("sha256").update(buffer).digest("hex").slice(0, 12);
    const path = `/generated/traffic-${width}-${hash}.${format}`;
    await writeFile(new URL(`public${path}`, root), buffer);
    preview[format].push(`${path} ${width}w`);
    if (width === 1200 && format === "webp") preview.default = path;
  }
}
await writeFile(
  new URL(".generated/preview.ts", root),
  `// Generated from artwork/traffic-preview.png.\nexport const preview = ${JSON.stringify({ ...preview, avif: preview.avif.join(", "), webp: preview.webp.join(", ") }, null, 2)} as const;\n`,
);
