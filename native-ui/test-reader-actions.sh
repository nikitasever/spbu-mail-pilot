#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=native-ui/app/build/native-reader-action-tests
mkdir -p "$out"
classpath=$(printf '%s:' lib/*.jar)
javac -encoding UTF-8 -cp "${classpath%:}" -sourcepath src:native-ui/app/src/main/java -d "$out" native-ui/tests/NativeReaderTargetsTest.java native-ui/tests/NativeExportPlanTest.java tests/BulkAttachmentsTest.java
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeReaderTargetsTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeExportPlanTest
java -cp "$out:lib/*" ru.local.spbumailpilot.BulkAttachmentsTest
