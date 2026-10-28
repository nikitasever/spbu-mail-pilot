#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export PATH=/workspace/scratch/java-tools:$PATH
out=native-ui/app/build/contact-edit-tests
mkdir -p "$out"
classpath=$(printf '%s:' lib/*.jar)
javac -encoding UTF-8 -cp "${classpath%:}" -sourcepath src:native-ui/app/src/main/java -d "$out" native-ui/tests/NativeContactEditsTest.java native-ui/tests/NativeContactImportTest.java native-ui/tests/NativeSystemContactsBatchTest.java
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeContactEditsTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeContactImportTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeSystemContactsBatchTest
node native-ui/tests/contacts-edit-comparison.cjs "$out"
node test-contacts.cjs
node test-reliability-contacts.cjs
