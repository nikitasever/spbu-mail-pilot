#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=native-ui/app/build/mail-event-tests
mkdir -p "$out"
classpath=$(printf '%s:' lib/*.jar)
javac -encoding UTF-8 -cp "${classpath%:}" -sourcepath src:native-ui/app/src/main/java -d "$out" native-ui/tests/NativeMailEventsTest.java native-ui/tests/NativeWorkspaceTest.java native-ui/tests/NativeLocalEventsTest.java
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeMailEventsTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeWorkspaceTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeLocalEventsTest
node test-mail-event-create.cjs
