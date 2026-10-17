#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=native-ui/app/build/mail-extra-tests
mkdir -p "$out"
classpath=$(printf '%s:' lib/*.jar)
javac -encoding UTF-8 -cp "${classpath%:}" -sourcepath src:native-ui/app/src/main/java -d "$out" native-ui/tests/NativeMailExtrasTest.java tests/MailSnoozeTest.java native-ui/tests/NativeMailMovesTest.java
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeMailExtrasTest
java -cp "$out:lib/*" ru.local.spbumailpilot.MailSnoozeTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeMailMovesTest
node test-inbox-system.cjs
node test-workspace.cjs
