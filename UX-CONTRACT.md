# Контракт первого пакета исправлений

Визуальные источники: [DESIGN.md](DESIGN.md), assets/app.css и NativeUi.java. Сохраняется существующий Android/WebView стек, без внедрения новой библиотеки компонентов.

## Canonical UI Map

| Capability | Canonical owner | Source of truth | Allowed variants | Verification |
|---|---|---|---|---|
| Select/Listbox | native select + change delegation in assets/app.js | existing platform-owned selection; icon choice uses cards | native for theme/folder, cards for icon | test-icon-choice.cjs; test-actions.cjs |
| Date | CalDav.java + calendarTimeRange in assets/app.js | dates/timezone from calendar model | date-only, all-day exclusive end, timed interval, task deadline | test-calendar.cjs; test-calendar-display.cjs |
| Form | renderForm + delegated submit in assets/app.js | existing bridge/data contracts | compose/task/event/contact | test-actions.cjs; test-calendar.cjs; test-contacts.cjs |
| Scrollbar | assets/app.css and Android WebView | existing document scroller, calendar list own overflow | document, day-popup-list | test-calendar-sheet-layout.cjs; test-contacts-layout.cjs |
| Toast | shared say() in assets/app.js | inline/status delivery of native outcomes | status, form error, quick-dialog status | test-icon-choice.cjs; test-actions.cjs |
| CRUD | updateLocal/keepDraft + Pilot bridge | existing persistence and server boundaries | local forms, server read flags, read-only calendar/directory | test-workspace.cjs; test-contacts.cjs; test-calendar.cjs |

Это карта существующих владельцев и ограниченного изменения, не заявление полного соответствия всем требованиям доступности. Известные старые недостатки и их исправление вне первого пакета описаны в предыдущем аудите. Общая scrollbar-тема пока остаётся браузерной; авторские hover-стили и поведение всех старых форм здесь не мигрируются.

## Поведение изменённых элементов

- hasAttachments: true/false из MIMEметаданных; отсутствующее поле означает неизвестное. Список не вызывает body() для получения значка; для старого bridge допустим fallback по уже открытым файлам. Явный false имеет приоритет над декоративными inlineизображениями в кэше.
- Диапазон времени события используется в дне и поиске. All-day DTEND исключителен. Задача показывает срок, а не интервал. LOCATION не преобразуется в выдуманное название аудитории.
- Смена иконки: запрос native bridge, обновление aria-pressed после успеха, текущий выбор сохраняется при ошибке и доступна повторная попытка.
- Кнопка нового контакта — обычный поток. Последний адрес и пагинация должны быть доступны при узком экране и увеличенном тексте.
- Причина повреждённого From неизвестна; fixtures проверяют корректные B/Qкодировки. Усечение текста не оставляет половину корректной UTF-16пары.

## Проверка

npm test покрывает существующие сценарии и три новые регрессии. npm run test:layout включает контакт320px/текст200% и календарный день20событий. Javaтесты запускает build.py --test; --check-only заканчивает после компиляции и тестов, без подписи и выпуска. PRworkflow выполняет проверки без секретов. Реальный SMTP/IMAP/AndroidWebView проверяется отдельно; фиктивные данные не доказывают совместимость с каждым сервером.

## Почта 0.41

- Превью: IntersectionObserver, видимые строки +120px, последовательные пачки до5, один префикс текста512символов/plain или4096/html, максимум180символов на экране. Вложения и forwarded RFC822 не читаются. IMAPREAD_ONLY/PEEK, без SEEN. Кэш500значений в памяти текущего аккаунта, без записи текстов в localStorage. Ответ обновляет текст строки, не перерисовывает форму. Ошибка оставляет адрес отправителя. В компактном режиме новые превью не загружаются.
- Фильтры взаимоисключающие: Все/Непрочитанные/С вложениями. Область — загруженные письма, явно подписана. Кнопка следующей страницы доступна даже при отсутствии совпадений. Фильтр сбрасывается при смене папки. Неизвестная MIMEструктура не выдаётся за наличие вложений.
- Черновики свёрнуты по умолчанию; summary44px, значок18px.
- Изменённые задачи/заметки/события/контакты: Back, Отмена, смена раздела/аккаунта и уведомление требуют подтверждения отказа от изменений. Отмена подтверждения сохраняет форму. Неизменённая форма закрывается сразу. Compose продолжает автоматически сохраняться. Это защита навигации, не восстановления после уничтожения процесса Android.
- При U+FFFD в имени открытое письмо показывает предупреждение и оригинальный From из MIMEзаголовка, безопасно как текст. Автоматическая перекодировка не применяется, причина не заявлена исправленной.
- Проверки: test-mail-improvements.cjs, MailPreviewTest.java, test-mail-improvements-layout.cjs плюс прежние наборы.
