# Проверки завершения известных пробелов Compose

17 октября 2026. Базовый импорт: `071785c`. Новые изменения находятся в истории этой же ветки.

## Изменения

- NativeExportPlan.library проверяет выбор текущей папки/UIDVALIDITY и точные MIME-ссылки разных писем,
  не допускает неизвестные, повторные, смешанные записи и более 500 файлов. NativeExportModel переиспользует
  BulkAttachments/SAF и NativeExportPicker; повторный план содержит только ошибки, без успешных файлов.
- NativeFolderChoices — общий компонент списка папок. NativeFilesModel хранит собственные папку/курсор/
  выбор и отдельное отменяемое получение имён, не меняя почтовую папку.
- NativeRelatedSearchModel добавляет независимые контакты и календарь к существующему поиску почты.
  Поля и серверный курсор почты принадлежат NativeInboxModel/NativeInboxSearch. Справочник имеет
  ограниченную пагинацию; локальные контакты/история и календарные задачи не требуют сети. Серверный
  календарь ограничен 12 месяцами; по умолчанию текущий и следующий месяц по Москве. Переход к событию
  очищает прежний календарный фильтр. Панель результатов ограничена по высоте, прокручивается и имеет
  доступные состояния сворачивания; реальная геометрия/TalkBack ещё требуют устройства.
- MailWatch/ImapWatchConnection переиспользованы для видимого онлайн-маршрута. Первая проверка и реальные
  изменения обновляют только свободный список; формы, чтение, поиск, цепочки и выбор защищены индикатором.
  Поздние callbacks проверяют экземпляр watcher, аккаунт, папку, AppLock/foreground. После настроек
  перечитываются актуальные реквизиты; при уходе соединение закрывается.
- NativeCallsScreen/Model и общий NativeDialer сохраняют прежние ручной ACTION_DIAL/историю последних
  50 переданных номеров. CallHistory обновляет только свою коллекцию свежего JSON под общим lock;
  проверяются аккаунт, номер, предел состояния и commit. Подтверждённый переход в «Телефон» не отменяется
  из-за последующей ошибки истории. Никаких реальных звонков в тестах не было.
- NativeCopyAge/CopyAgeText отображают фактическое cachedAt списка/тела и обновляются раз в минуту
  только в foreground, без запросов к почте и пересоздания поля поиска.

## Фактические проверки

```sh
# Android SDK 34, Build Tools 35.0.0, JDK 21 в этой среде; обычно достаточно JDK 17+.
cd native-ui
./gradlew -PrenderUi :app:compileDebugKotlin :app:compileDebugJavaWithJavac :app:testDebugUnitTest \
  --tests '*.NativeCalendarLinksTest' --tests '*.NativeReaderDetailsTest' \
  --tests '*.NativeThreadNavigationTest' --tests '*.NativeAccountsStorageTest' \
  --tests '*.NativeNavigationTest' --tests '*.NativeStartupTest' \
  --tests '*.NativeInboxThreadsTest' --tests '*.NativeWatchRefreshTest' --tests '*PrivacyTest'
# Из корня
python build.py --test --check-only
bash native-ui/test-compose.sh
bash native-ui/test-global-search.sh
bash native-ui/test-reader-actions.sh
bash native-ui/test-file-library.sh
```

Выполняются именно указанные JUnit, без NativePreviewTest и snapshot/render задач. `-PrenderUi`
только подключает существующий test source set/зависимости; сообщение Paparazzi report само по себе
не означает, что делались скриншоты. Текущий набор: 60 JUnit в 15 классах, без ошибок.

Host-проверки покрывают общий поиск, диапазоны/переход года/ё/е/историю и телефон, скрытые href,
500 выбранных ссылок, stale/foreign/duplicate rejection, failed-only retry, сохранность исходных JSON,
минутные/часовые/дневные границы возраста копии, отмену/изоляцию watcher. В build.py проходят 56 Java
entry points и прежние JS/style-проверки (`npm test`). Некоторые тесты в разных сценариях повторяются;
эти числа нельзя суммировать как независимые проверки функций.

DESIGN.md lint: 0 errors, 6 существующих warnings. Проверяются ссылки документации и git diff --check.
Статический premium-аудитор навыка в этой среде недоступен после обновления набора навыков;
вместо него не заявляется фиктивная проверка Compose. Новые controls используют Material 3 и существующую тему.

## Ограничения

Не запускались установка, устройство/эмулятор, рендеры, accessibility UI test, реальная почта/SMTP,
системные контакты, SAF или набор номера. Нет доказательства воспроизводимого соответствия ранее
переданного app-debug.apk новым исходникам. APK/версия/релиз не создавались.
Проверки socket/JSON policies и компиляция не доказывают отсутствие всех жизненных цикловых гонок.
Полная приёмка перечислена в [remaining-work.md](remaining-work.md).


## Подписанный кандидат 0.74-rc.1 (89)

По последующему запросу пользователя 17 октября опубликован предварительный выпуск из `28f7654`:
[релиз](https://github.com/nikitasever/spbu-mail-pilot/releases/tag/v0.74-rc.1),
[успешная CI-сборка](https://github.com/nikitasever/spbu-mail-pilot/actions/runs/39055897055).
CI повторил legacy compatibility, native host suites, выбранные JUnit и assembleRelease.
Локальный assembleRelease также прошёл; скачанный опубликованный APK проверен apksigner/aapt/sha256sum.

- versionName `0.74-rc.1`, versionCode `89`, minSdk 26, targetSdk 34; release без debuggable.
- SHA-256 APK: `1db6516edc51789dfddb4933df77e62f88a6a2942a9c354874f6e36bbc6dd811`.
- SHA-256 сертификата: `197ecdabd94c7217afa45df6f02300f1f0ff12474b3146fc4018a3adbb8ce7b6`;
  совпадает со скачанным опубликованным APK 0.73 из репозитория обновлений.
- Рабочий update channel/main не менялись. Старый и Compose launcher доступны одновременно.
- Установка и приёмка на телефоне не выполнялись; ограничения выше остаются в силе.
