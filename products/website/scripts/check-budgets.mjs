import { readdir, readFile, stat, writeFile } from "node:fs/promises";
import { gzipSync } from "node:zlib";

let compressedCode = 0;
for (const file of await readdir("dist/assets")) {
  if (/\.(js|css)$/.test(file))
    compressedCode += gzipSync(
      await readFile(`dist/assets/${file}`),
    ).byteLength;
}
const hero = (await readdir("dist/generated")).find((file) =>
  /^traffic-1200-.*\.webp$/.test(file),
);
if (!hero) throw new Error("The default product image is missing.");
const heroBytes = (await stat(`dist/generated/${hero}`)).size;
if (compressedCode > 150 * 1024 || heroBytes > 400 * 1024)
  throw new Error("The website exceeds its documented asset budget.");
const report = {
  compressedJavaScriptAndCssBytes: compressedCode,
  defaultHeroBytes: heroBytes,
  codeBudgetBytes: 150 * 1024,
  heroBudgetBytes: 400 * 1024,
};
await writeFile(
  "dist/asset-budget.json",
  `${JSON.stringify(report, null, 2)}\n`,
);
console.log(JSON.stringify(report));
