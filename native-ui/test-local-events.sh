#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=native-ui/app/build/event-tests
mkdir -p "$out"
classpath=$(printf '%s:' lib/*.jar)
javac -encoding UTF-8 -cp "${classpath%:}" -sourcepath src:native-ui/app/src/main/java -d "$out" native-ui/tests/NativeLocalEventsTest.java native-ui/tests/NativeEventEquivalence.java native-ui/tests/NativeWorkspaceTest.java
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeWorkspaceTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeLocalEventsTest
node native-ui/tests/event-equivalence.cjs native-ui/tests/event-cases.json "$out/legacy.json"
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeEventEquivalence native-ui/tests/event-cases.json "$out/native.json"
python - "$out" <<'PY'
import json,sys,pathlib
folder=pathlib.Path(sys.argv[1]);old=json.loads((folder/'legacy.json').read_text());new=json.loads((folder/'native.json').read_text())
assert len(old)==len(new)==10
assert old==new, 'Legacy/native events differ'
(folder/'result.json').write_text(json.dumps({'casesExecuted':len(old),'matching':len(old),'differences':0,'scope':'event/link/task/note/unknown JSON fields; UI lifecycle and Android commit not compared','masks':[]},indent=2)+'\n')
print('PASS: 10 legacy/native event comparisons; no masks or skipped cases')
PY
