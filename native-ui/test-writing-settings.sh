#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=native-ui/app/build/writing-settings-tests
mkdir -p "$out"
classpath=$(printf '%s:' lib/*.jar)
javac -encoding UTF-8 -cp "${classpath%:}" -sourcepath src:native-ui/app/src/main/java -d "$out" native-ui/tests/NativeWritingSettingsTest.java native-ui/tests/NativeWritingToolsTest.java native-ui/tests/NativeWritingAiTest.java
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeWritingSettingsTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeWritingToolsTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeWritingAiTest
