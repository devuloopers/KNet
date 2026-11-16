import { beforeEach, afterEach, describe, expect, it } from "vitest";
import {
  mkdtemp,
  readFile,
  writeFile,
  copyFile,
  rm,
  symlink,
} from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { pathToFileURL } from "node:url";
import { readArtwork, artworkIds } from "../../scripts/artwork.mjs";

let directory, manifest;
const original = new URL("../../artwork/", import.meta.url);
const path = () => new URL("manifest.json", directory);
const save = () => writeFile(path(), JSON.stringify(manifest));

beforeEach(async () => {
  const temporary = await mkdtemp(join(tmpdir(), "knet-artwork-"));
  directory = pathToFileURL(temporary + "/");
  manifest = JSON.parse(
    await readFile(new URL("manifest.json", original), "utf8"),
  );
  for (const entry of manifest.images)
    await copyFile(
      new URL(entry.file, original),
      new URL(entry.file, directory),
    );
  await save();
});
afterEach(async () => {
  await rm(directory, { recursive: true, force: true });
});

describe("actual Compose image freshness boundary", () => {
  it("rejects an unknown freshness mode", async () => {
    await expect(
      readArtwork(directory, { KNET_ARTWORK_REQUIRE_FRESH: "tru" }),
    ).rejects.toThrow("true or false");
  });
  it("accepts the complete safe image set", async () => {
    expect(
      Object.keys((await readArtwork(directory, {})).images).sort(),
    ).toEqual([...artworkIds].sort());
  });
  it("requires an explicit verified source in release mode", async () => {
    await expect(
      readArtwork(directory, { KNET_ARTWORK_REQUIRE_FRESH: "true" }),
    ).rejects.toThrow("source identity");
  });
  it("accepts a release only with both exact identities", async () => {
    await expect(
      readArtwork(directory, {
        KNET_ARTWORK_REQUIRE_FRESH: "true",
        KNET_ARTWORK_SOURCE_SHA: manifest.sourceCommit,
        KNET_ARTWORK_SOURCE_DIGEST: manifest.sourceDigest,
      }),
    ).resolves.toHaveProperty("images.traffic");
  });
  it.each(["KNET_ARTWORK_SOURCE_SHA", "KNET_ARTWORK_SOURCE_DIGEST"])(
    "rejects stale %s",
    async (key) => {
      const environment = {
        KNET_ARTWORK_REQUIRE_FRESH: "true",
        KNET_ARTWORK_SOURCE_SHA: manifest.sourceCommit,
        KNET_ARTWORK_SOURCE_DIGEST: manifest.sourceDigest,
        [key]: "0".repeat(64),
      };
      await expect(readArtwork(directory, environment)).rejects.toThrow(
        "source identity",
      );
    },
  );
  it("rejects a missing required scene", async () => {
    manifest.images.pop();
    await save();
    await expect(readArtwork(directory, {})).rejects.toThrow("inventory");
  });
  it("rejects duplicate scene substitution", async () => {
    manifest.images[1] = manifest.images[0];
    await save();
    await expect(readArtwork(directory, {})).rejects.toThrow("inventory");
  });
  it("rejects bytes modified after generation", async () => {
    await writeFile(
      new URL(manifest.images[0].file, directory),
      Buffer.alloc(12_000),
    );
    await expect(readArtwork(directory, {})).rejects.toThrow(
      "corrupt or changed",
    );
  });
  it("rejects missing image files", async () => {
    await rm(new URL(manifest.images[0].file, directory));
    await expect(readArtwork(directory, {})).rejects.toThrow();
  });
  it("rejects traversal filenames", async () => {
    manifest.images[0].file = "../private.png";
    await save();
    await expect(readArtwork(directory, {})).rejects.toThrow(
      "path or dimensions",
    );
  });
  it("rejects mismatched PNG dimensions", async () => {
    manifest.images[0].width += 1;
    await save();
    await expect(readArtwork(directory, {})).rejects.toThrow(
      "corrupt or changed",
    );
  });
  it("rejects symbolic links", async () => {
    const entry = manifest.images[0];
    await rm(new URL(entry.file, directory));
    await symlink(
      new URL(entry.file, original),
      new URL(entry.file, directory),
    );
    await expect(readArtwork(directory, {})).rejects.toThrow("symbolic link");
  });
  it("rejects unknown rendering configuration", async () => {
    manifest.renderConfiguration.version = 2;
    await save();
    await expect(readArtwork(directory, {})).rejects.toThrow("configuration");
  });
});
