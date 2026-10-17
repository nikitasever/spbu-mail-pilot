#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=native-ui/app/build/native-legacy-draft-tests
mkdir -p "$out"
classpath=$(printf '%s:' lib/*.jar)
javac -encoding UTF-8 -cp "${classpath%:}" -sourcepath src:native-ui/app/src/main/java -d "$out" native-ui/tests/NativeLegacyDraftPlanTest.java native-ui/tests/NativeLegacyDraftFilesTest.java
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeLegacyDraftPlanTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeLegacyDraftFilesTest
