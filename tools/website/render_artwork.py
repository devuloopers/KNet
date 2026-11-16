#!/usr/bin/env python3
"""Render isolated Compose scenarios and bind their outputs to the exact source tree.

Only this explicit command needs the JVM. Normal npm builds verify and optimize existing artwork.
Release CI rerenders, then verifies the source digest again before packaging the same PNGs.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess
import sys
import platform
import tomllib
import tempfile
import shutil

ROOT = Path(__file__).resolve().parents[2]
ARTWORK = Path('products/website/artwork')
INPUTS = ('ui', 'application', 'core', 'engine', 'data', 'desktopBrand', 'companionBrand',
          'tools/website', 'gradle', 'build-logic', 'build.gradle.kts', 'settings.gradle.kts',
          'gradle.properties', 'gradlew', 'gradlew.bat')
RENDER_CONFIGURATION = {'version': 1, 'locale': 'en-US', 'timezone': 'UTC',
                        'theme': 'KNet dark', 'clock': 'fixed', 'fontScale': 1}


def git(root: Path, *arguments: str) -> bytes:
    """Read source identity through Git without changing the checkout."""
    return subprocess.check_output(['git', '-C', str(root), *arguments])


def source_digest(root: Path) -> str:
    """Hash consumed source paths and bytes, including new files and tracked deletions."""
    paths = git(root, 'ls-files', '-z', '--cached', '--others', '--exclude-standard', '--', *INPUTS)
    digest = hashlib.sha256()
    for name in sorted(set(paths.split(b'\0')) - {b''}):
        if b'__pycache__/' in name or name.endswith(b'.pyc'):
            continue
        path = root / os.fsdecode(name)
        if path.is_symlink():
            raise ValueError(f'Render input must not be a symbolic link: {os.fsdecode(name)}')
        digest.update(name + b'\0')
        digest.update(hashlib.sha256(path.read_bytes()).digest() if path.is_file() else b'DELETED')
    return digest.hexdigest()


def scenarios(root: Path) -> list[dict]:
    """Read the closed scenario inventory; reject duplicate identities and unsafe output paths."""
    document = json.loads((root / 'tools/website/scenarios.json').read_text())
    if document.get('version') != 1 or not document.get('scenarios'):
        raise ValueError('Unsupported or empty artwork scenario inventory.')
    entries = document['scenarios']
    ids = [entry['id'] for entry in entries]
    if len(ids) != len(set(ids)):
        raise ValueError('Duplicate artwork scenario identity.')
    for entry in entries:
        if not entry['id'].replace('-', '').isalnum() or not entry['module'].startswith(':ui:'):
            raise ValueError('Invalid artwork scenario identity/module.')
        if not all(isinstance(entry[key], int) and 0 < entry[key] <= 4096 for key in ('width', 'height')):
            raise ValueError('Invalid artwork dimensions.')
    return entries


def output_record(root: Path, entry: dict, directory: Path | None = None) -> dict:
    """Require the renderer's declared PNG dimensions and retain a digest of its exact bytes."""
    filename = entry['id'] + '-preview.png'
    path = (directory if directory is not None else root / ARTWORK) / filename
    if path.is_symlink():
        raise ValueError('Artwork cannot be a symbolic link.')
    content = path.read_bytes()
    if content[:8] != b'\x89PNG\r\n\x1a\n' or content[12:16] != b'IHDR' or len(content) < 10_000:
        raise ValueError(f'Invalid or empty product artwork: {filename}')
    width, height = struct.unpack('>II', content[16:24])
    if (width, height) != (entry['width'], entry['height']):
        raise ValueError(f'Unexpected product artwork dimensions: {filename}')
    return {'id': entry['id'], 'file': filename, 'width': width, 'height': height,
            'sha256': hashlib.sha256(content).hexdigest()}


def verify(root: Path) -> dict:
    """Reject stale/missing source provenance or changed PNGs before any release can advance."""
    manifest = json.loads((root / ARTWORK / 'manifest.json').read_text())
    if manifest.get('version') != 1 or manifest.get('renderConfiguration') != RENDER_CONFIGURATION:
        raise ValueError('Artwork render configuration is stale.')
    if manifest.get('sourceCommit') != git(root, 'rev-parse', 'HEAD').decode().strip():
        raise ValueError('Artwork was not rendered from the current source commit.')
    if manifest.get('sourceDigest') != source_digest(root):
        raise ValueError('Compose/render inputs changed after artwork generation. Regenerate artwork.')
    if manifest.get('images') != [output_record(root, entry) for entry in scenarios(root)]:
        raise ValueError('Artwork inventory or PNG bytes changed after generation.')
    return manifest


def run_renderer(command: list[str], root: Path) -> None:
    """Run the explicit fixture tasks; failures leave the previous manifest untouched."""
    subprocess.run(command, cwd=root, check=True)


def main() -> None:
    """Run all fixture test owners, then atomically publish provenance only after every render passes."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verify', action='store_true', help='Reject stale source/outputs without rendering.')
    options = parser.parse_args()
    if options.verify:
        manifest = verify(ROOT)
    else:
        before = source_digest(ROOT)
        entries = scenarios(ROOT)
        # A new empty directory is essential: an omitted capture must never be satisfied by a baseline PNG.
        with tempfile.TemporaryDirectory(prefix='knet-website-render-') as temporary:
            staging = Path(temporary)
            command = [str(ROOT / 'gradlew')]
            for module in dict.fromkeys(entry['module'] for entry in entries):
                selector = '*WebsiteProductPreviewTest' if module == ':ui:desktop:app' else '*Website*ArtworkTest'
                command += [module + ':jvmTest', '--tests', selector]
            command += ['--init-script', 'tools/website/render-preview.init.gradle', '--no-configuration-cache',
                        '--no-daemon', '--max-workers=2', '--continue',
                        '-PknetWebsiteArtworkDirectory=' + str(staging)]
            run_renderer(command, ROOT)
            if source_digest(ROOT) != before:
                raise ValueError('Render inputs changed during generation. Rerun with a stable source tree.')
            images = [output_record(ROOT, entry, staging) for entry in entries]
            versions = tomllib.loads((ROOT / 'gradle/libs.versions.toml').read_text())['versions']
            java = subprocess.run(['java', '-version'], capture_output=True, text=True, check=True)
            toolchain = {'java': (java.stderr or java.stdout).splitlines()[0],
                         'kotlin': versions['kotlin'], 'compose': versions['composeMultiplatform'],
                         'os': platform.system(), 'architecture': platform.machine()}
            manifest = {'version': 1, 'toolchain': toolchain,
                        'sourceCommit': git(ROOT, 'rev-parse', 'HEAD').decode().strip(),
                        'sourceDigest': before, 'renderConfiguration': RENDER_CONFIGURATION, 'images': images}
            for image in images:
                target_image = ROOT / ARTWORK / image['file']
                temporary_image = target_image.with_suffix('.png.tmp')
                shutil.copyfile(staging / image['file'], temporary_image)
                temporary_image.replace(target_image)
            if source_digest(ROOT) != before:
                raise ValueError('Render inputs changed during publication. Regenerate artwork.')
            target = ROOT / ARTWORK / 'manifest.json'
            temporary_manifest = target.with_suffix('.tmp')
            temporary_manifest.write_text(json.dumps(manifest, indent=2) + '\n')
            temporary_manifest.replace(target)
        verify(ROOT)
    print(f'Verified {len(manifest["images"])} real Compose images from {manifest["sourceCommit"][:12]}.')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, KeyError, subprocess.CalledProcessError) as error:
        print(f'Artwork generation/verification failed: {error}', file=sys.stderr)
        sys.exit(1)
