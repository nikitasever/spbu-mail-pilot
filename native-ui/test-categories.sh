#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=native-ui/app/build/category-tests
mkdir -p "$out"
classpath=$(printf '%s:' lib/*.jar)
javac -encoding UTF-8 -cp "${classpath%:}" -sourcepath src:native-ui/app/src/main/java -d "$out" native-ui/tests/NativeCategoryRulesTest.java tests/MailCategoriesTest.java native-ui/tests/NativeWorkspaceTest.java
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeCategoryRulesTest
java -cp "$out:lib/*" ru.local.spbumailpilot.MailCategoriesTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeWorkspaceTest
node test-inbox-system.cjs
