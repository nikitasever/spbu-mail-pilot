#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=native-ui/app/build/native-mail-thread-tests
mkdir -p "$out"
classpath=$(printf '%s:' lib/*.jar)
javac -encoding UTF-8 -cp "${classpath%:}" -sourcepath src:native-ui/app/src/main/java -d "$out" native-ui/tests/NativeMailThreadsTest.java native-ui/tests/NativeMailThreadComparison.java native-ui/tests/NativeMailThreadScopeTest.java
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeMailThreadsTest
node native-ui/tests/mail-thread-comparison.cjs
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeMailThreadComparison native-ui/tests/mail-thread-fixtures.json
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeMailThreadScopeTest native-ui/tests/mail-thread-scope-fixtures.json
node test-mail-threads.cjs
