#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=native-ui/app/build/native-accounts-settings-tests
mkdir -p "$out"
classpath=$(printf '%s:' lib/*.jar)
javac -encoding UTF-8 -cp "${classpath%:}" -sourcepath src:native-ui/app/src/main/java -d "$out" native-ui/tests/NativeAccountPolicyTest.java native-ui/tests/NativeGeneralSettingsTest.java tests/SavedAccountsTest.java tests/AppLockSessionTest.java tests/AppearancePolicyTest.java native-ui/tests/LauncherIconPolicyTest.java native-ui/tests/OfflineCacheFilesTest.java
for test in NativeAccountPolicyTest NativeGeneralSettingsTest SavedAccountsTest AppLockSessionTest AppearancePolicyTest LauncherIconPolicyTest OfflineCacheFilesTest; do
    java -cp "$out:lib/*" "ru.local.spbumailpilot.$test"
done
