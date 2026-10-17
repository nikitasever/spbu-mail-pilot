#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=native-ui/app/build/deferred-mail-tests
mkdir -p "$out"
classpath=$(printf '%s:' lib/*.jar)
javac -encoding UTF-8 -cp "${classpath%:}" -sourcepath src:native-ui/app/src/main/java -d "$out" native-ui/tests/android/text/Html.java native-ui/tests/NativeDeferredMailTest.java native-ui/tests/NativeMailExtrasTest.java tests/ReplyReminderTest.java tests/MailSnoozeTest.java
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeDeferredMailTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeMailExtrasTest
java -cp "$out:lib/*" ru.local.spbumailpilot.ReplyReminderTest
java -cp "$out:lib/*" ru.local.spbumailpilot.MailSnoozeTest
node test-mail-snooze.cjs
node test-reply-reminder.cjs
