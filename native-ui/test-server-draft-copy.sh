#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=native-ui/app/build/native-server-draft-copy-tests
mkdir -p "$out"
classpath=$(printf '%s:' lib/*.jar)
javac -encoding UTF-8 -cp "${classpath%:}" -sourcepath src:native-ui/app/src/main/java -d "$out" native-ui/tests/android/text/Html.java tests/ReadFlagTest.java native-ui/tests/ServerDraftReadTest.java native-ui/tests/NativeServerDraftCopyPlanTest.java
java -cp "$out:lib/*" ru.local.spbumailpilot.ServerDraftReadTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeServerDraftCopyPlanTest
