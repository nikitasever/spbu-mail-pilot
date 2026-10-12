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

## Календарь 0.42

- Режимы Месяц/Неделя/Повестка сохранены для текущего аккаунта. aria-pressed отражает выбор; ошибка сохранения не переключает режим.
- Неделя начинается в понедельник, показывает семь дат и сгруппированные события; пустые дни не растягивают список. Повестка показывает непустые дни ближайших14дней от выбранной даты. Заголовок явно указывает диапазон. Сегодня возвращает текущий период, стрелки перемещают месяц/7/14дней. Даты рассчитываются вUTC, события остаются поМоскве.
- Открытие шторки дня не сдвигает недельный/14дневный диапазон. Задачи/свои события доступны для изменения, серверные события только для чтения. Поиск ограничен выбранным периодом; многодневный экземпляр встречается в поиске один раз.
- Неделя/Повестка на границе месяцев загружают оба месяца последовательно: native bridge отменяет предыдущий параллельный запрос. Смена режима использует уже загруженные месяцы, максимум6записей в памяти. Поколение запроса защищает от устаревших ответов. Один экземпляр из двухмесяцев объединяется по календарю/id/датам/времени. All-day DTEND исключителен.
- При обновлении показывается загрузка вместо ложного отсутствия дел; ошибки сохраняют доступные данные. Добавление/отключение источника инвалидирует кэшмесяцев.
- Выходные получили лёгкий нейтральный фон. Основная тема, цвета событий и нижняя навигация сохранены.
- Проверки: test-calendar-views.cjs (переход2026→2027, последовательность, дубли, поиск, отмена/ошибка, сохранение); test-calendar-views-layout.cjs (320/393px/landscape, увеличенный текст, длинные описания, последняя запись, редактор и шторка).

## Надёжность и контакты 0.43

- Задача/заметка/локальное событие: подтверждение с явными кнопками Удалить/Отмена. Отмена ничего не меняет; сохранение локального состояния по-прежнему откатывается при ошибке.
- Серверный черновик: подтверждение, только конкретный UID в Drafts при совпадении UIDVALIDITY, собственного key/revision/DRAFT и аккаунта. UIDPLUS обязателен для scoped UID EXPUNGE; без него отказ до изменения флагов. Широкий EXPUNGE запрещён. Missing UID — успешная повторная попытка.
- UI сохраняет локальную копию и файлы до remoteACK. Удаление блокирует редактирование/autosync этого draft; ошибка сервера оставляет retry. Ошибка локального save после remoteACK оставляет статус в памяти, запрещает autosync и позволяет повторить локальное удаление без нового запроса.
- Маркер неподтверждённой загрузки сохраняется ДО сетевого sync. Lost ACK, включая новую ревизию при наличии старой ссылки, требует повторного sync перед удалением. Старые неподтверждённые копии без маркера, созданные предыдущими версиями, не могут быть достоверно распознаны автоматически.
- Контакт открывается кнопкой имени/адреса в существующем модальном окне. Полный адрес/известный номер, Написать, Позвонить (только с номером), Копировать адрес, Изменить/Сохранить на телефоне. Clipboardbridge валидирует адрес; копирование не использует AIbridge. Должность/подразделение не выдумываются при отсутствии данных. Back/Escape/закрытие возвращает к списку; имя и адрес безопасно выводятся как текст.
- Проверки: test-reliability-contacts.cjs, ServerDraftRemovalTest.java, test-reliability-contacts-layout.cjs(320/393px/landscape/текст200%). Публикация в updates покрыта16mockedstdllibтестами.

## Цепочки 0.44

- Связи только по валидным RFCMessage-ID/References/In-Reply-To, не по теме. Message-ID должен быть однозначен; идентификаторы ASCIIдо998символов, максимум100на заголовок, исходная строка до32768символов. Регистр сохраняется. Папка и UIDVALIDITY входят в область связывания.
- Новые заголовки загружаются пакетно с metadataписьма, без body/attachments/SEEN. Отсутствие/ошибка optionalheaders не скрывает список.
- Цепочки включены по умолчанию; настройка per-account, ошибка save откатывает выбор. Группируется загруженный список текущей папки/результаты текущего поиска. Более ранние письма добавляются обычной пагинацией этой папки.
- Карточка использует последнее загруженное письмо как заголовок и считает unread/attachment по всем членам. Фильтры оставляют цепочку целиком, если любой член соответствует. Счётчик явно говорит загруженные письма, не полный размер переписки.
- Экран цепочки показывает старые письма первыми; открытие экрана не вызывает body и не меняет SEEN. Только выбор конкретного письма делает это. Back возвращает к цепочке, затем к списку. Уведомление/смена папки/раздела очищает предыдущую цепочку.
- Метка применяется только к отдельному письму: longpressгруппы предлагает сначала открыть письмо, без неявного bulkдействия.
- Ответ переносит References исходного письма, native добавляет непосредственный parent, сохраняет root и последние ancestors(максимум100), складывает строку заголовка и отклоняет инъекции. SMTPи серверный draft используют одинаковую логику; References влияют на revision/fingerprint черновика. Legacydrafts без References сохраняют прежний single-parentзаголовок.
- Проверки: MailThreadHeadersTest.java, test-mail-threads.cjs, test-mail-threads-layout.cjs(320/393px/landscape/крупныйтекст), плюс существующие Java/JS/Chromiumнаборы.

## Поправка ответа 0.45

Письмо может иметь References, но не иметь корректного собственного Message-ID. В таком случае replycomposer сохраняет адрес/тему/текст, но не переносит References без parent: отправка остаётся возможной. Не выдумывается идентификатор исходного письма. Regressionfixture проверяет этот случай.

## Отмена отправки 0.46

- По умолчанию после submit сохранённый черновик ждёт10секунд перед вызовом native send. Поле recipients/title/body/references/attachments фиксируется snapshotом; readonlyполя и блокировка действий исключают изменение ожидающего письма. Вложения/серверный draft должны закончить текущую передачу до submit.
- Явная кнопка Отменить отправку и AndroidBack во время ожидания возвращают composer без SMTPзапроса; сохранённая копия остаётся. Native onPause вызывает cancellationhook. Documenthidden и проверка native appVisible на deadline закрывают гонку сворачивания. Native send отказывает закрытому/неактивному окну до запуска worker. Уже начатая отправка продолжается как раньше и не объявляется отменённой.
- Таймер использует monotonic performance.now; fallbackDateтолько для bridge/browserбезPerformance. Каждыйtick изменяет только текст времени, сохраняя UndoDOM/focus/press. Один таймер/защита личности snapshotа исключают старые callbackи и повторные submit.
- Это задержка до SMTP, не отзыв доставленного письма и не фоновая/запланированная отправка. Убийство процесса до expiry оставляет черновик без отправки. При ошибке native send черновик сохранён; очистка только после sent:true.
- Настройка per-account Отмена отправки(10секунд) включена по умолчанию; optout посылает немедленно. Для старого bridge без appVisible сохраняется прежняя немедленная отправка.
- Проверки: test-undo-send.cjs(9/10секунд, undo/Back, clockjump, doublesubmit, stale timer, nativefail/retry, successcleanup, pause/deadlinevisibility, optout), test-undo-send-layout.cjs(realChromium/clock, keyboardundo/focus,320/393px/текст200%).

## Моя задача из календаря 0.47

- Общее серверное событие не создаёт уведомлений само. «Это моя задача» открывает локальный taskcomposer с title/date/time/location исходного события. Только Save создаёт personaltask/напоминание; Cancel ничего не помечает. Событие остаётся read-only.
- calendarSource сохраняет calendarId/id/calendarTitle экземпляра. Повторный выбор открывает существующую личную задачу; completedзадача остаётся выполненной. Удаление личной копии снимает пометку. Это личный срок: изменения внешнего события не переопределяют его автоматически.
- Reminder per-task: auto/15/30/60/1440/none. Autoсохраняет старое поведение: за час до времени, без времени9:00МСК. Для явного lead безtime deadline9:00МСК. Unknownвариант нормализуетсяauto. Noneисключён изalarm/sentset, поэтому старое уведомление отменяется при обновлении.
- Сохранение задачи пересчитывает существующийNativeNoticeCenter scheduler. Sentkey legacy сохранён дляauto и эквивалентного60минут сtime, предотвращая повторные уведомления после upgrade. Изменениеlead меняетключ; completed/deleted/globallydisabled не напоминаются.
- Источники других людей не получают personalalarm без явного подтверждения. Androidразрешение и глобальные уведомления задач должны быть включены; используется прежняя системная alarmмодель.
- Проверки: ReminderPolicyTest.java(customlead/previousday/dateonly/none/legacykeys), test-calendar-my-task.cjs(ownership/cancel/rollback/dedupe/completion), realChromiumtaskReminderpicker320/393px/крупныйтекст.

## Подписи и шаблоны 0.48

- Источник изоляции аккаунтов: MainActivity.attachLogin/Bridge.loadState/saveState, отдельные SharedPreferences по host/port/user. signatureText/signatureAuto и templates принадлежат текущему account-state, не общему глобальному профилю.
- Подпись: plain text до4000символов, настройки через shared quick-popup/field/area/say/save. Новое письмо/ответ/письмо контакту получает подпись один раз при создании; edit-draft её не добавляет и настройка не переписывает existingdrafts. Автоматическая вставка отключается; пустой text отключает. Ручная вставка в конец не дублирует неизменённый suffix. Разделитель -- и две пустые строки сохраняются в native send snapshot; textarea.value восстанавливается из модели, чтобы HTMLparser не терял начальный перевод строки.
- Шаблоны: settings/manage и composer/insert используют один quick-popup. Новый/Изменить открывают отдельный editor, Save возвращает список, Cancel/Back/Escape/backdrop защищают введённые изменения app-owned confirmation. Confirmation initially focuses safe action. Deletion requires ownconfirmation; failure keeps editor/confirmation, rollback leaves saveddata unchanged. Saved edit keeps id/order. Новые шаблоны ограничены100, старые данные не обрезаются.
- Вставка в сохранённый selection/caret, остальной текст/адресаты/вложения/References сохраняются. Blanklines отделяют соседний текст, исходный текст шаблона не изменяется. Composerlimit100000 проверяется до изменения; failedsave возвращает предыдущую body и оставляет popup для retry. В settings нет insert вне composer. После вставки focus возвращается body; после closing — toolbarbutton.
- Notificationnavigation с dirtyquickeditor отклоняется и показывает discardchoice, ничего не теряется. Autosend не вызывается ни одним writingtool; существующая undoSend-модель сохраняется. Plaintext escaped в UI и без HTMLконверсии переносится nativeSMTP.
- Theme/spacing/fonts/canonicaldialog остаются прежними. Writingpopup reuses scrollowner quick-popup, controls wrap at320px, touchbuttons44px, labelled inputs/errors и novalidate.
- Проверки: test-writing-tools.cjs (accountisolation/CRUD/rollback/caret/limit/unsavedguard/no rewriting), test-writing-tools-layout.cjs (realChromium320/393px, light/dark/enlargedtext, keyboard/Escape/focus, savefailure и native-sendfixture).

## Навигация по контактам 0.49

- Domainsource: UniversityDirectory.people и Bridge.contactsPage возвращают страницы sender/email без обещания полноты/глобальной сортировки. Буквенный index охватывает только contactPeople текущей адресной книги и поиска после объединения local/server/history и emaildedupe. Другие буквы появляются после явной догрузки, indexclick не выполняет запрос и не загружает весь каталог. Scope/count подписаны.
- contactLetter/contactSort — sharedgroupingowner: NFC, trim/leadingquotes, первая Unicodeбуква, русские группы первыми, Ё в Е, затем латиница, остальные в «Другие имена». У латинских групп есть подпись; имя/адрес не переписываются. Groupcomparator гарантирует один заголовок на букву, сортировка имён локальная ru/numeric/base.
- Sharedbtn creates44pxlettercontrols in a sticky horizontal native-scrollstrip. Document remains the list scroller; index owns horizontal overflow only. Jump adjusts scroll-margin by actual stripheight, scrolls/focuses heading without hiding it beneath the strip. Card/copy/mail/menu/pagination/Add retain existing semantics.
- Поиск: sharedinputdelegation, debounce300ms, no remotequery during composition, explicit Enter immediately cancels timer, stalegeneration ignored. Clearbutton is labelled, shown only withquery; clears immediately, reloads currentbook and restoresinputfocus without replacinginputnode. Query/book reset updatesletters, selection, count and pagination; intermediateempty pages keep More and errors keep Retry. No contacts stored/downloaded just for indexing.
- Canonical owners stay assets/app.js sharedrender/btn/say/contactPeople/loadContacts plus assets/app.css, no newcomponentlibrary or select. Nativecontact/form/save/cardflow unchanged. Themevalues unchanged.
- Tests: test-contact-index.cjs(Unicode/Russian/Latin/quotes/dedup/pagination/loaded-onlyjump/searchclear/IME/Enter/stale/error), test-contact-index-layout.cjs(realChromium320/393px/landscape/enlargedtext, light/dark, headingvisibility/focus, keyboard/cardreturn/clear).
