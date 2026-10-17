# Native Android — Kotlin + Jetpack Compose

Разработческая миграция поверх общего Java-ядра. WebView сохранён для постепенного перехода.
Полный перечень и оставшиеся работы: [docs/migration](../docs/migration/README.md).
Текущие результаты компиляции и тестов: [completion-checks](../docs/migration/completion-checks.md).

Требуются JDK 17+, Android SDK 34, Build Tools 35.0.0; Gradle 8.9 закреплён wrapper и SHA-256.
Из корня восстановить зависимости (их SHA-256 проверяется):

```sh
python -c "import build; build.dependencies()"
cd native-ui
./gradlew :app:compileDebugKotlin :app:compileDebugJavaWithJavac
```

Это компиляция исходников без упаковки APK. `ANDROID_HOME` указывает на установленный SDK.
Перед обычными изменениями UI проверить актуальность assets штатными npm build/test командами.

Host-проверки из корня:

```sh
npm ci --ignore-scripts
bash native-ui/test-compose.sh
bash native-ui/test-global-search.sh
bash native-ui/test-advanced-search.sh
bash native-ui/test-accounts-settings.sh
bash native-ui/test-contacts-edit.sh
npm test
```

Для контактов требуется npm ci до JS-части тестов. Java host-сценарии используют lib/*.jar.
JUnit из `src/previewTest` подключаются свойством `-PrenderUi`; запуск тестов отдельно от рендера:
`./gradlew -PrenderUi :app:testDebugUnitTest --tests "*.NativeCalendarLinksTest" --tests "*.NativeReaderDetailsTest"`. Наличие теста не означает успешный прогон на устройстве.

Генератор Gradle использует основной AndroidManifest.xml и добавляет отдельный launcher
«Почта · Compose», versionName получает суффикс `-native-dev`. Production manifest и versionCode
не повышаются. В dev APK доступны старый и Compose-интерфейс.

В исходниках есть входящие/чтение/поиск, редактор, локальные и серверные копии черновиков,
планируемая отправка, контакты/импорт, календарь/связи, файлы/SAF, аккаунты/настройки и AppLock.
Подробные ограничения и непроверенные сценарии перечислены в [remaining-work](../docs/migration/remaining-work.md).

APK пока не пересобирать на каждый шаг. Debug-подпись отличается от рабочего клиента с тем же
applicationId: не удалять рабочий клиент ради установки и не обещать обновление без потери данных.
Ключи, реальные аккаунты, личные данные и локальные SDK-настройки не включать в репозиторий.
