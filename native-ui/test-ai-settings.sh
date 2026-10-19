#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=native-ui/app/build/native-ai-settings-tests
mkdir -p "$out"
classpath=$(printf '%s:' lib/*.jar)
javac -encoding UTF-8 -cp "${classpath%:}" -sourcepath src:native-ui/app/src/main/java -d "$out" native-ui/tests/NativeAiSettingsTest.java native-ui/tests/GeminiCancellationTest.java tests/AiPolicyTest.java tests/GeminiDigestTest.java tests/DigestBatchTest.java native-ui/tests/NativeDigestPlanTest.java
for test in NativeAiSettingsTest GeminiCancellationTest AiPolicyTest GeminiDigestTest DigestBatchTest NativeDigestPlanTest; do
    java -cp "$out:lib/*" "ru.local.spbumailpilot.$test"
done
