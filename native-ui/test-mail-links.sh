#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=native-ui/app/build/link-tests
mkdir -p "$out"
classpath=$(printf '%s:' lib/*.jar)
javac -encoding UTF-8 -cp "${classpath%:}" -sourcepath src:native-ui/app/src/main/java -d "$out" native-ui/tests/NativeMailLinksTest.java tests/MailNoticeTargetTest.java native-ui/tests/NativeReadGateTest.java
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeMailLinksTest
java -cp "$out:lib/*" ru.local.spbumailpilot.MailNoticeTargetTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeReadGateTest
node test-calendar-mail-links.cjs
node test-workspace.cjs
