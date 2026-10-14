import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

spec = importlib.util.spec_from_file_location('build_styles', Path(__file__).resolve().parents[1] / 'scripts/build_styles.py')
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)


class StyleBuildTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.sources = self.root / 'ui/styles'
        self.sources.mkdir(parents=True)
        (self.sources / 'base.css').write_bytes('/* Фиолетовая тема */\n.x{color:red}\n'.encode())
        (self.sources / 'reader.css').write_bytes(b'.x{color:purple}\n')
        self.order(['base.css', 'reader.css'])

    def order(self, names):
        (self.sources / 'order.json').write_text(json.dumps(names), encoding='utf-8')

    def test_order_and_utf8_survive_without_minification(self):
        self.assertTrue(builder.build_styles(self.root))
        asset = self.root / 'assets/app.css'
        self.assertEqual(asset.read_bytes(), (self.sources / 'base.css').read_bytes() + (self.sources / 'reader.css').read_bytes())
        original_time = asset.stat().st_mtime_ns
        self.assertFalse(builder.build_styles(self.root))
        self.assertEqual(original_time, asset.stat().st_mtime_ns)
        self.order(['reader.css', 'base.css'])
        self.assertTrue(builder.build_styles(self.root))
        self.assertTrue(asset.read_bytes().endswith(b'.x{color:red}\n'))

    def test_check_detects_drift_without_rewriting(self):
        builder.build_styles(self.root)
        asset = self.root / 'assets/app.css'
        asset.write_bytes(b'wrong')
        with self.assertRaisesRegex(ValueError, 'stale'):
            builder.build_styles(self.root, check=True)
        self.assertEqual(asset.read_bytes(), b'wrong')

    def test_missing_duplicate_or_unlisted_source_is_rejected(self):
        for names in [['base.css'], ['base.css', 'base.css', 'reader.css'], ['base.css', 'missing.css']]:
            with self.subTest(names=names):
                self.order(names)
                with self.assertRaises(ValueError):
                    builder.build_styles(self.root)
                self.assertFalse((self.root / 'assets/app.css').exists())

    def test_invalid_paths_and_manifest_shapes_are_rejected(self):
        for names in [{}, [], ['../escape.css'], ['..\\escape.css'], [None], ['base.txt']]:
            with self.subTest(names=names):
                self.order(names)
                with self.assertRaises(ValueError):
                    builder.build_styles(self.root)

    def test_invalid_encoding_or_missing_boundary_newline_is_rejected(self):
        for content in [b'\xff\n', b'.x{color:red}']:
            with self.subTest(content=content):
                (self.sources / 'base.css').write_bytes(content)
                with self.assertRaises(ValueError):
                    builder.build_styles(self.root)

    def test_repository_asset_matches_sources(self):
        self.assertFalse(builder.build_styles(Path(__file__).resolve().parents[1], check=True))

    def test_failed_replace_keeps_last_asset_and_removes_temporary_file(self):
        builder.build_styles(self.root)
        target = self.root / 'assets/app.css'
        previous = target.read_bytes()
        (self.sources / 'reader.css').write_bytes(b'.x{color:green}\n')
        with mock.patch.object(builder.os, 'replace', side_effect=OSError('write failed')):
            with self.assertRaises(OSError):
                builder.build_styles(self.root)
        self.assertEqual(target.read_bytes(), previous)
        self.assertEqual(list(target.parent.glob('.styles-*.tmp')), [])

    def test_symlink_cannot_include_files_outside_sources(self):
        outside = self.root / 'outside.css'
        outside.write_bytes(b'.x{color:green}\n')
        entry = self.sources / 'reader.css'
        entry.unlink()
        try:
            entry.symlink_to(outside)
        except (OSError, NotImplementedError):
            self.skipTest('Symlinks unavailable on this platform')
        with self.assertRaisesRegex(ValueError, 'escapes'):
            builder.build_styles(self.root)


if __name__ == '__main__':
    unittest.main()
