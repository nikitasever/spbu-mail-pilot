#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=native-ui/app/build/policy-tests
mkdir -p "$out"
classpath=$(printf '%s:' lib/*.jar)
javac -encoding UTF-8 -cp "${classpath%:}" -sourcepath src:native-ui/app/src/main/java -d "$out" native-ui/tests/android/text/Html.java native-ui/tests/NativeComposerTest.java native-ui/tests/NativeAttachmentsTest.java native-ui/tests/NativeReadGateTest.java tests/MailNoticeTargetTest.java native-ui/tests/NativeMailPageTest.java native-ui/tests/NativeRecipientsTest.java tests/UniversityDirectoryTest.java tests/DirectoryAutocompleteTest.java native-ui/tests/NativeWritingToolsTest.java native-ui/tests/NativeReplyTest.java tests/ReadFlagTest.java native-ui/tests/NativeForwardTest.java native-ui/tests/NativeDraftDiscardTest.java native-ui/tests/NativeSeenFlagsTest.java native-ui/tests/NativeFoldersTest.java native-ui/tests/NativeMailSearchTest.java native-ui/tests/NativeContactsTest.java native-ui/tests/NativeAgendaTest.java native-ui/tests/NativeWorkspaceTest.java native-ui/tests/NativeLocalEventsTest.java native-ui/tests/NativeMailLinksTest.java native-ui/tests/NativeMailEventsTest.java native-ui/tests/NativeMailMovesTest.java native-ui/tests/NativeCategoryRulesTest.java native-ui/tests/NativeMailExtrasTest.java native-ui/tests/NativeDeferredMailTest.java native-ui/tests/NativeContactEditsTest.java native-ui/tests/NativeContactImportTest.java native-ui/tests/NativeWritingSettingsTest.java native-ui/tests/NativeOpenTargetTest.java native-ui/tests/NativeMailThreadsTest.java native-ui/tests/NativeReaderTargetsTest.java native-ui/tests/NativeExportPlanTest.java native-ui/tests/NativeAccountPolicyTest.java native-ui/tests/NativeGeneralSettingsTest.java
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeComposerTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeAttachmentsTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeReadGateTest
java -cp "$out:lib/*" ru.local.spbumailpilot.MailNoticeTargetTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeMailPageTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeRecipientsTest
java -cp "$out:lib/*" ru.local.spbumailpilot.UniversityDirectoryTest
java -cp "$out:lib/*" ru.local.spbumailpilot.DirectoryAutocompleteTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeWritingToolsTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeReplyTest
java -cp "$out:lib/*" ru.local.spbumailpilot.ReadFlagTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeForwardTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeDraftDiscardTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeSeenFlagsTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeFoldersTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeMailSearchTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeContactsTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeAgendaTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeWorkspaceTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeLocalEventsTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeMailLinksTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeMailEventsTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeMailMovesTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeCategoryRulesTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeMailExtrasTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeDeferredMailTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeContactEditsTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeContactImportTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeWritingSettingsTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeOpenTargetTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeMailThreadsTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeReaderTargetsTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeExportPlanTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeAccountPolicyTest
java -cp "$out:lib/*" ru.local.spbumailpilot.NativeGeneralSettingsTest
