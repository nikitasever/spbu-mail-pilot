#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=native-ui/app/build/native-navigation-tests
mkdir -p "$out"
classpath=$(printf '%s:' lib/*.jar)
javac -encoding UTF-8 -cp "${classpath%:}" -sourcepath src:native-ui/app/src/main/java -d "$out" native-ui/tests/NativeOpenTargetTest.java tests/MailNoticeTargetTest.java tests/AppLockSessionTest.java
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeOpenTargetTest
java -cp "$out:lib/*" ru.local.spbumailpilot.MailNoticeTargetTest
java -cp "$out:lib/*" ru.local.spbumailpilot.AppLockSessionTest
node test-widget-navigation.cjs
node test-app-lock.cjs
