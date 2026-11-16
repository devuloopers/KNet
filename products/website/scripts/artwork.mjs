import { createHash } from "node:crypto";
import { readFile, lstat } from "node:fs/promises";

/** One required source inventory is shared by image preparation, build checks and the Compose runner. */
export const artworkIds = [
  "traffic",
  "multi-device",
  "api-studio",
  "intercepts",
  "network-conditions",
  "protected-traffic",
  "companion-pairing",
  "companion-connected",
];

/** Verifies every PNG's bytes/dimensions and the release's expected source identity before optimization. */
export async function readArtwork(directory, environment = process.env) {
  if (
    ![undefined, "false", "true"].includes(
      environment.KNET_ARTWORK_REQUIRE_FRESH,
    )
  )
    throw new Error("KNET_ARTWORK_REQUIRE_FRESH must be true or false.");
  const manifest = JSON.parse(
    await readFile(new URL("manifest.json", directory), "utf8"),
  );
  if (
    manifest.version !== 1 ||
    !/^[a-f0-9]{40}$/.test(manifest.sourceCommit) ||
    !/^[a-f0-9]{64}$/.test(manifest.sourceDigest)
  )
    throw new Error(
      "Product artwork provenance is missing or invalid. Run tools/website/render_artwork.py.",
    );
  if (
    manifest.renderConfiguration?.version !== 1 ||
    manifest.renderConfiguration?.theme !== "KNet dark"
  )
    throw new Error("Unsupported product artwork rendering configuration.");
  if (environment.KNET_ARTWORK_REQUIRE_FRESH === "true") {
    if (
      !environment.KNET_ARTWORK_SOURCE_SHA ||
      !environment.KNET_ARTWORK_SOURCE_DIGEST ||
      manifest.sourceCommit !== environment.KNET_ARTWORK_SOURCE_SHA ||
      manifest.sourceDigest !== environment.KNET_ARTWORK_SOURCE_DIGEST
    )
      throw new Error(
        "Release artwork does not match the verified source identity.",
      );
  }
  if (
    !Array.isArray(manifest.images) ||
    manifest.images.length !== artworkIds.length ||
    artworkIds.some(
      (id) => manifest.images.filter((image) => image.id === id).length !== 1,
    )
  )
    throw new Error("The complete product artwork inventory is required.");
  const images = {};
  for (const entry of manifest.images) {
    if (
      entry.file !== `${entry.id}-preview.png` ||
      !Number.isInteger(entry.width) ||
      !Number.isInteger(entry.height) ||
      entry.width < 1 ||
      entry.height < 1 ||
      entry.width > 4096 ||
      entry.height > 4096
    )
      throw new Error("Invalid product artwork path or dimensions.");
    const path = new URL(entry.file, directory);
    if ((await lstat(path)).isSymbolicLink())
      throw new Error("Product artwork cannot be a symbolic link.");
    const bytes = await readFile(path);
    if (
      bytes.length < 10_000 ||
      bytes.subarray(0, 8).toString("hex") !== "89504e470d0a1a0a" ||
      bytes.readUInt32BE(16) !== entry.width ||
      bytes.readUInt32BE(20) !== entry.height ||
      createHash("sha256").update(bytes).digest("hex") !== entry.sha256
    )
      throw new Error(
        `Product artwork is corrupt or changed: ${entry.id}. Regenerate artwork.`,
      );
    images[entry.id] = { ...entry, bytes };
  }
  return { manifest, images };
}
