# Проверка объединённого ZIP Gemini

Исторический результат первичного импорта. Android SDK впоследствии подготовлен, компиляция и выбранные JUnit проходят; актуально: [completion-checks.md](completion-checks.md).

Дата: 17 октября 2026. Архив: `spbu-mail-pilot-compose-source.zip`.
SHA-256: `f6fbb3da10089da27f2917f7cf28e72fdde70b1becd6fc6985f47aba221c2ce1`.

## Что найдено в исходниках

- NativeMailSearch.Criteria и offline-фильтр включены; поиск теперь не зависит от отсутствующего класса.
- Завершение loadInbox явно задаёт busy=false, очищает selected/body/selectedRefs и pageExpired.
- Введён SelectionScope; ключ критериев используется для состояния списка/цепочек.
- Обновление сохранённого пароля использует полный CAS Login и updatePassword без изменения activeId/SMTP.
- CalendarState редактирует toString и очищает connectPassword в pause; есть отдельный event picker,
  проверки допустимости commit и сравнение выбранной связи при удалении.
- Prefetch имеет отдельный закрываемый gate, OfflineMailStore перечитывает общий актуальный snapshot под lock.
- HTML href извлекаются в MIME-ядре и передаются UI; есть новые отличающие это поведение helper-тесты.
  Заголовки читаются отдельным ограниченным путём; прежнее предупреждение incomplete сохранено.
- Новые Java/Kotlin файлы не содержат повреждённого текста из старого package3. Замещающий Unicode-символ
  в assets/app.js относится к сгенерированному старому bundle; его соответствие исходникам проверено npm test.

Это подтверждает наличие исправлений при чтении кода и целевых host-проверках. Полный аудит Kotlin
состояний/сокетов/Android commit и полевые сценарии ещё не завершены.

## Исправлено при импорте

1. **Граница сборок.** MailReader ссылался на NativeReaderTargets, лежавший исключительно в native-ui.
   Обычный build.py компилирует только src и не мог разрешить этот класс. Перенесён без смены поведения
   в src/ru/local/spbumailpilot. test-advanced-search.sh теперь намеренно использует sourcepath только src.
   До переноса javac обнаруживал missing NativeReaderTargets; после переноса три поисковых Java suite проходят.
2. **Wrapper.** README ссылался на ./gradlew, которого не было в архиве. Добавлены официальные файлы
   Gradle v8.9.0; SHA-256 wrapper jar сверён с services.gradle.org, distribution checksum закреплён в properties.
3. Убрана возможность случайно коммитить Python bytecode; нормализованы CRLF/лишние пустые окончания
   в затронутых файлах. APK и секреты в git не добавляются.
4. Исправлены устаревшие документы о якобы отсутствующих исходниках и неверные ссылки на имена тестов.

## Фактически выполнено здесь

- `bash native-ui/test-compose.sh` — PASS (35 Java test entry points).
- `bash native-ui/test-advanced-search.sh` — PASS (3 suite), в том числе после переноса helper и ограничения sourcepath.
- `bash native-ui/test-accounts-settings.sh` — PASS (7 Java suite).
- `bash native-ui/test-contacts-edit.sh` — PASS (Java policy + сравнение со старым UI + JS).
- `bash native-ui/test-mail-links.sh` — PASS (Java + JS).
- `bash native-ui/test-reader-actions.sh` — PASS (3 Java suite).
- `npm test` — PASS, включая проверку соответствия bundle исходникам и style tests.
- Целостность ZIP, отсутствие опасных путей/симлинков и явных файлов ключей; SHA-256 официального wrapper.

Host: Java 21 compiler module; зависимости восстановлены по имеющемуся SHA-256 lock-файлу.
Скрипты запускались через bash. Это не полная Gradle-компиляция Android.
Android SDK в текущей среде не установлен: compileDebugKotlin/compileDebugJavaWithJavac и JUnit previewTest
не запускались. Рендеры, APK, реальная почта/контакты, устройство/эмулятор и выпуск не выполнялись.
Загруженный пользователем APK отдельно статически проверялся; его точное соответствие ZIP исходникам
не доказано воспроизводимой сборкой.
