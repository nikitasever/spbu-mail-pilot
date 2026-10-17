#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=native-ui/app/build/move-tests
mkdir -p "$out"
classpath=$(printf '%s:' lib/*.jar)
javac -encoding UTF-8 -cp "${classpath%:}" -sourcepath src:native-ui/app/src/main/java -d "$out" native-ui/tests/android/text/Html.java tests/MailMoveTest.java native-ui/tests/NativeMailMovesTest.java
java -cp "$out:lib/*" ru.local.spbumailpilot.MailMoveTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeMailMovesTest
node test-inbox-system.cjs
