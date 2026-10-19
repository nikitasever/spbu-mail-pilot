"""Optional equivalence canary; compiles a mutant in a temporary directory, never edits app source."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
source = root / 'native-ui/app/src/main/java/ru/local/spbumailpilot/NativeWorkspace.java'
original = source.read_bytes()
needle = '.put("eventTitle",replacement.getString("title"))'
assert needle in original.decode()
classpath = ':'.join(str(p) for p in (root / 'lib').glob('*.jar'))
output = root / 'native-ui/app/build/event-tests'
legacy = json.loads((output / 'legacy.json').read_text())
with tempfile.TemporaryDirectory(prefix='event-canary-') as folder:
    folder = Path(folder)
    (folder / 'NativeWorkspace.java').write_text(original.decode().replace(needle, '.put("eventTitle",link.optString("eventTitle"))'))
    subprocess.run([shutil.which('javac') or 'javac', '-encoding', 'UTF-8', '-cp', classpath, '-sourcepath', 'src:native-ui/app/src/main/java', '-d', str(folder), str(folder / 'NativeWorkspace.java'), 'native-ui/tests/NativeEventEquivalence.java'], cwd=root, check=True)
    subprocess.run(['java', '-cp', str(folder) + ':' + classpath, 'ru.local.spbumailpilot.NativeEventEquivalence', 'native-ui/tests/event-cases.json', str(folder / 'native.json')], cwd=root, check=True)
    changed = json.loads((folder / 'native.json').read_text())
    assert len(legacy) == len(changed) == 10
    failures = [a['name'] for a, b in zip(legacy, changed) if a != b]
    assert failures, 'Comparison failed to detect the canary'
assert source.read_bytes() == original
result = dict(mutation='Retain stale eventTitle in linked metadata', casesExecuted=len(changed), differencesDetected=len(failures), failingCases=failures, workingSourceUnmodified=True, workingSourceSha256=hashlib.sha256(original).hexdigest())
(output / 'canary.json').write_text(json.dumps(result, indent=2) + '\n')
print('PASS canary detected:', ', '.join(failures), '; working source unchanged')
