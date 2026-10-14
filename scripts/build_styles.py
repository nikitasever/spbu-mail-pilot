"""Build the offline stylesheet without changing the order of existing rules."""
import argparse
import json
import os
from pathlib import Path
import sys
import tempfile


def bundle_styles(directory):
    directory = Path(directory).resolve()
    names = json.loads((directory / 'order.json').read_text(encoding='utf-8'))
    if not isinstance(names, list) or not names:
        raise ValueError('Style order must be a non-empty list')
    if any(not isinstance(name, str) or not name.endswith('.css')
           or '/' in name or '\\' in name or name.startswith('.') for name in names):
        raise ValueError('Style entries must be plain CSS filenames')
    if len(set(names)) != len(names):
        raise ValueError('Duplicate stylesheet in order.json')
    actual = {str(path.relative_to(directory)) for path in directory.rglob('*.css')}
    if actual != set(names):
        raise ValueError('Every CSS source must appear exactly once in order.json')
    chunks = []
    for name in names:
        path = (directory / name).resolve()
        if path.parent != directory:
            raise ValueError('Stylesheet escapes its source directory: ' + name)
        data = path.read_bytes()
        data.decode('utf-8')
        if not data.endswith(b'\n'):
            raise ValueError('Stylesheet must end with a newline: ' + name)
        chunks.append(data)
    return b''.join(chunks)


def build_styles(root, check=False):
    root = Path(root)
    data = bundle_styles(root / 'ui/styles')
    target = root / 'assets/app.css'
    matches = target.is_file() and target.read_bytes() == data
    if check:
        if not matches:
            raise ValueError('assets/app.css is stale; run python scripts/build_styles.py')
        return False
    if matches:
        return False
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=target.parent, prefix='.styles-', suffix='.tmp', delete=False) as handle:
            temporary = Path(handle.name)
            handle.write(data)
        os.replace(temporary, target)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Reject a stale generated asset without rewriting it')
    args = parser.parse_args()
    try:
        changed = build_styles(Path(__file__).resolve().parents[1], args.check)
    except (OSError, ValueError, UnicodeError) as error:
        print('Style build failed: ' + str(error), file=sys.stderr)
        return 1
    print('Styles checked' if args.check else 'Styles generated' if changed else 'Styles unchanged')
    return 0


if __name__ == '__main__':
    sys.exit(main())
