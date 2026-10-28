#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=native-ui/app/build/global-search-tests
mkdir -p "$out"
classpath=$(printf '%s:' lib/*.jar)
javac -encoding UTF-8 -cp "${classpath%:}" -sourcepath src:native-ui/app/src/main/java -d "$out" native-ui/tests/NativeRelatedSearchTest.java native-ui/tests/NativeCopyAgeTest.java tests/CallHistoryTest.java
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeRelatedSearchTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeCopyAgeTest
java -cp "$out:lib/*" ru.local.spbumailpilot.CallHistoryTest
