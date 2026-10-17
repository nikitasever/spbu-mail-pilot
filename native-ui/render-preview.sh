#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./gradlew -PrenderUi :app:recordPaparazziDebug --tests ru.local.spbumailpilot.NativePreviewTest
