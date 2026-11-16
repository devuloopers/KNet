"""Source freshness regressions use a temporary Git checkout and synthetic image bytes only."""
import json
from pathlib import Path
import struct
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import render_artwork as artwork


class ArtworkFreshnessTests(unittest.TestCase):
    """Reject old source identity, missing scenes and changed output before release packaging."""

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True)
        self.source = self.root / 'ui/core/Example.kt'
        self.source.parent.mkdir(parents=True)
        self.source.write_text('original component')
        subprocess.run(['git', '-C', str(self.root), 'add', '.'], check=True)
        subprocess.run(['git', '-C', str(self.root), '-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.com',
                        'commit', '-qm', 'fixture'], check=True)
        self.entry = {'id': 'traffic', 'module': ':ui:desktop:app', 'width': 100, 'height': 80}
        inventory = self.root / 'tools/website/scenarios.json'
        inventory.parent.mkdir(parents=True)
        inventory.write_text(json.dumps({'version': 1, 'scenarios': [self.entry]}))
        directory = self.root / artwork.ARTWORK
        directory.mkdir(parents=True)
        self.image = directory / 'traffic-preview.png'
        # The unit boundary inspects PNG header/dimensions/digest; the real encoder/Sharp are integration gates.
        self.image.write_bytes(b'\x89PNG\r\n\x1a\n' + b'\x00\x00\x00\rIHDR' + struct.pack('>II', 100, 80) + b'\x00' * 12_000)
        self.manifest = {'version': 1, 'sourceCommit': artwork.git(self.root, 'rev-parse', 'HEAD').decode().strip(),
                         'sourceDigest': artwork.source_digest(self.root), 'renderConfiguration': artwork.RENDER_CONFIGURATION,
                         'images': [artwork.output_record(self.root, self.entry)]}
        self.save()

    def save(self):
        (self.root / artwork.ARTWORK / 'manifest.json').write_text(json.dumps(self.manifest))

    def test_current_complete_output_passes(self):
        self.assertEqual(artwork.verify(self.root), self.manifest)

    def test_changed_component_invalidates_artwork(self):
        self.source.write_text('changed component')
        with self.assertRaisesRegex(ValueError, 'inputs changed'):
            artwork.verify(self.root)

    def test_new_theme_file_invalidates_artwork(self):
        (self.source.parent / 'Theme.kt').write_text('new theme')
        with self.assertRaisesRegex(ValueError, 'inputs changed'):
            artwork.verify(self.root)

    def test_deleted_component_invalidates_artwork(self):
        self.source.unlink()
        with self.assertRaisesRegex(ValueError, 'inputs changed'):
            artwork.verify(self.root)

    def test_website_only_copy_does_not_invalidate_compose_images(self):
        copy = self.root / 'products/website/copy.txt'
        copy.write_text('updated marketing copy')
        self.assertEqual(artwork.verify(self.root), self.manifest)

    def test_previous_commit_is_rejected(self):
        self.manifest['sourceCommit'] = '0' * 40
        self.save()
        with self.assertRaisesRegex(ValueError, 'current source commit'):
            artwork.verify(self.root)

    def test_png_changed_after_render_is_rejected(self):
        self.image.write_bytes(self.image.read_bytes() + b'changed')
        with self.assertRaisesRegex(ValueError, 'PNG bytes changed'):
            artwork.verify(self.root)

    def test_missing_scene_is_rejected(self):
        self.image.unlink()
        with self.assertRaises(FileNotFoundError):
            artwork.verify(self.root)

    def test_wrong_dimensions_are_rejected(self):
        self.entry['width'] = 101
        with self.assertRaisesRegex(ValueError, 'dimensions'):
            artwork.output_record(self.root, self.entry)

    def test_render_configuration_change_is_rejected(self):
        self.manifest['renderConfiguration'] = {'version': 0}
        self.save()
        with self.assertRaisesRegex(ValueError, 'configuration is stale'):
            artwork.verify(self.root)

    def test_output_symlink_is_rejected(self):
        safe = self.image.with_suffix('.backup')
        self.image.rename(safe)
        self.image.symlink_to(safe)
        with self.assertRaisesRegex(ValueError, 'symbolic link'):
            artwork.verify(self.root)

    def test_renderer_failure_does_not_publish_a_new_manifest(self):
        before = (self.root / artwork.ARTWORK / 'manifest.json').read_bytes()
        with patch.object(artwork, 'ROOT', self.root), patch('sys.argv', ['render_artwork.py']), \
                patch.object(artwork, 'run_renderer', side_effect=subprocess.CalledProcessError(1, ['gradle'])):
            with self.assertRaises(subprocess.CalledProcessError):
                artwork.main()
        self.assertEqual((self.root / artwork.ARTWORK / 'manifest.json').read_bytes(), before)

    def test_source_change_during_render_does_not_publish_a_manifest(self):
        before = (self.root / artwork.ARTWORK / 'manifest.json').read_bytes()
        with patch.object(artwork, 'ROOT', self.root), patch('sys.argv', ['render_artwork.py']), \
                patch.object(artwork, 'run_renderer', side_effect=lambda *args, **kwargs: self.source.write_text('concurrent edit')):
            with self.assertRaisesRegex(ValueError, 'during generation'):
                artwork.main()
        self.assertEqual((self.root / artwork.ARTWORK / 'manifest.json').read_bytes(), before)

    def test_old_baseline_cannot_replace_a_capture_missing_from_the_new_run(self):
        before = (self.root / artwork.ARTWORK / 'manifest.json').read_bytes()
        # A successful process with no new output must fail, despite a complete older baseline on disk.
        with patch.object(artwork, 'ROOT', self.root), patch('sys.argv', ['render_artwork.py']), \
                patch.object(artwork, 'run_renderer', return_value=None):
            with self.assertRaises(FileNotFoundError):
                artwork.main()
        self.assertEqual((self.root / artwork.ARTWORK / 'manifest.json').read_bytes(), before)
        self.assertTrue(self.image.exists())

    def test_workflow_paths_cover_every_render_input_for_pushes_and_prs(self):
        workflow = (artwork.ROOT / '.github/workflows/website-deployment.yml').read_text()
        for source in artwork.INPUTS:
            pattern = source + '/**' if '.' not in source.split('/')[-1] and source not in ('gradlew',) else source
            self.assertEqual(workflow.count("- '" + pattern + "'"), 2, source)

    def test_duplicate_or_traversal_scenario_is_rejected(self):
        path = self.root / 'tools/website/scenarios.json'
        for entries in [[self.entry, self.entry], [{**self.entry, 'id': '../private'}]]:
            path.write_text(json.dumps({'version': 1, 'scenarios': entries}))
            with self.assertRaises(ValueError):
                artwork.scenarios(self.root)


if __name__ == '__main__':
    unittest.main()
