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

## Рубеж 0.50: почта, календарь и несколько ящиков

- UI evidence/owners: сохранённые DESIGN.md, sharedbtn/area/say/render/quick-popup/Pilot. Reader reuses detail/sender-block/body-text/attachment-card, targets44px, lighterpadding/type only in reader. Ответить beforeattachmentlist, secondaryactions afterbody; no removed sender-address/diagnosticFrom/read/export/task capability.
- OfflineMailbox is a bounded metadata/text model, no network/mutation. OfflineMailStore uses separate AndroidKeystore AES-GCMkey, account-boundAAD, AtomicFile in noBackupFilesDir, rollback onfailedpersist; epochs prevent stalewriter recreating deleted copies. Max200headers/folder,10folders,50bodies,4MiB; no attachmentbytes. UIDVALIDITYchange invalidates oldbodies, including latebodywrite; references include folder/validity/UID. Metadata-only cachedsearch is explicit; snapshot pagination cannot mix with livepagination.
- MainActivity cachedsession is available only for remembered account, airplane-mode fastpath; live authenticationfailure does not silently fall back to cache. Offline mode never invokes SMTP/serverdraftsync or changesSEEN; manual reconnect preservescomposer and refreshes stalecachedbodies. Readingcopy labelled/date; missingbody showserror+retry. Cache savefailure does not fail online mail. Clear/forget/reset removes copies, keeps drafts/servermail/calendar. Nativeclear is app-owned confirmation. Copyfields can be read without changing serverflags.
- Notification policy (AccountMailChecks/NoticeCenter/BackgroundMailService): all rememberedaccounts, isolatedcursor/tag/target, individualmailNotifications/backgroundMail, failures do not skip others. Changing activeaccount preservesmail notices, cancels previous active tasknotices; removedaccounts cleanup is scheduled. Sender title, subject text, accountsubtext; generic lockscreen publicVersion/PRIVATE preserved. Control/bidi chars sanitized and Unicodeboundaries respected.
- BulkAttachments/SafAttachmentDestination: user-selectedSAFtree, max500 refs/100MiB each, bounded sequential download/copy, throttled progress, immediatecancellationflag, no overwrite/dedupedrefs, disposabletemporaryfiles. Only closed/renamedcompletefiles count saved; .part onprocessdeath notclaimedcomplete. Failureperfile andfailed-onlyretry; unsupportedrename yields specifichint. Modal stays untilcancelack; Back/Escape cancel, progress preserves focusedcancelDOM; notificationnavigation blocked whilebatchbusy. Rootbatch result aftercleanup releasesbusy beforeUIretry. Scope in Files is currentlyloadedentries, not wholefolder.
- Calendar day adds one-day range/prev-next/search, existingweek/month/agenda remain. Startscreen peraccount, maildefault unless chosen. Event/mail link is explicitlocalrelationship, no task/servereventmutation; datepicker reuses platform-owneddatefield and quick-popup. Save rollback/dedupedref/folderUIDVALIDITY, picker stalegeneration, one modal, unlink preservesobjects. Openingmailfromevent returns to samecalendarcontext on Back, including AndroidBack; notifications clear formercalendarreturnstate.
- Source contracts: MainActivity.attachLogin/Bridge/credentialscope; MailReader.page/body/arrivals; CalendarBook/CalDav occurrenceid/timezone; existingLocalState save/rollback. Fields not supplied by server are not invented.
- Checks: OfflineMailboxTest, MailNotificationTextTest, AccountMailChecksTest, BulkAttachmentsTest; test-milestone.cjs, test-calendar-mail-links.cjs, test-milestone-layout.cjs and allprior Java/JS/browser suites. No actualuser mail used in fixtures. Real-device Keystore/SAF/notification/keyboard/background OSbehavior requires deviceverification separately.

## Размер чтения 0.50.1

Reader-scopedstyles не перекрывают userlargepreference: body17→21px, sender16→18px. Checkbox throughrealSettings is verified in actualChromium with toggling both ways, nohorizontaloverflow. Compactactions retain44pxtargets. Existing 0.50 domains unchanged.

## Прогресс вложений 0.50.2

Shared batch popup for reader and Files: SVG circle with percentage processed/total (including failed files, not a claim of saved bytes), explicit processed counter, current ordinal/name, download/save stage and observed bytes. Unknown byte totals remain unknown; no estimated percent or speed/ETA. Name comes from the requested attachment metadata while the native stream has no name; escaped/textContent only. Completion shows saved/failed separately; cancelled ring retains processed fraction. Stable DOM updates preserve Cancel focus; Back/Escape cancellation and failed-only retry remain unchanged. Display metadata never changes native folder/UIDVALIDITY/UID/path identity. Source: screenshot feedback and existing BulkAttachments.Observer contract.

## Прогресс отдельного файла 0.50.3

AttachmentTransfer counts raw transfer-encoded MIME bytes against Part.getSize before MIME decoding. Download percentage never mixes a decoded-byte numerator with an encoded-byte denominator. A provider that lacks raw access falls back to decoded streaming with unknown total; a mismatched/absent size remains indeterminate. Decoded copy limits and cancellation/partial cleanup remain. Saving uses actual decoded file length as a separate stage. Batch file percentage is separate from processed-file circle. QuickDownload updates the shared inline block in reader/Files, suppresses duplicate clicks, retains navigation and existing Downloads/legacy system export destinations. Callback onFileDownloadProgress is distinct from composer onAttachmentProgress, stale requests ignored. Progress is throttled and account guarded. AttachmentNames decodes complete embedded/adjacent RFC2047 words once, preserves normal names and unsupported tokens, used for metadata and saved filenames. Source: user screenshot and feedback, MIME body size/transfer encoding contracts. Tests cover binary decoding/byte totals, embedded filename words, upload regression, single-file stale callbacks/unknown total/duplicate request, batch percentage and existing browser themes/viewports.

## Черновики 0.50.4

Compact draft rows reuse shared buttons, icons, palette and native details. Full main button opens existing editor; separate44px trash retains existing local/server confirmation and deletion/recovery paths. Subject/recipient/preview/change timestamp/attachment count, all escaped. Dates recorded only on new draft or content fingerprint changes, never invented for untouched legacy drafts; editor reopening does not alter timestamp. Newest dated draft first, source array unmodified. Disclosure open state survives rerender/deletion. No empty draft silently removed, no automatic SMTP or server copy deletion introduced. Source: screenshot feedback, existing keepDraft/fingerprint/deleteDraft contract. Browser checks keyboard editing, long content, narrow themes/font size, count and disclosure after deletion.

## Проверка интерфейса 0.50.5

Existing12 Chromium layout/interaction suites passed before fixes. Confirmed search bugs: mail/file callbacks while search input focused deferred rendering permanently; missing/inaccessible clear controls; mailbox-empty copy used for no search matches or network error. Shared searchClear/paintSearchClear covers mail/files/tasks, appears during typing, accessible labels and44px targets, no duplicate nativeX. Non-quiet mail/file callbacks deferred during input are reconciled on focusout without losing unsubmitted text; generation/route guards prevent stale search responses or clear/switch actions restoring old text. Existing quiet mail update policy unchanged. Network errors have separate empty states and Retry, preserve committed query; no matches remain a normal search state. Source: loadPage/loadFiles input-preservation contract and reproduced delayed callbacks. New real-browser test verifies delay/focusout, clearing/focus, stale callbacks, no matches vs errors/retry, task reset at320px/light/dark/landscape.

## Сохранность редактора 0.50.6

Compose canLeaveForm blocks leaving while the active attachment import owns the draft; text remains editable and local saving unchanged. Back, close, routes, account switch and notification preparation use the shared guard. Task notification now uses the same prepareNotificationNavigation contract as mail: no replacement during send/import/bulk transfer, no premature clearing of unsaved state. Native pending intent is acknowledged only on true; continuePendingNavigation runs after import completion/error/cancel and send outcome/undo, after UI/draft state handling. Network errors retain manual retry, no automatic SMTP dispatch. Source: existing guarded composer, native pending-intent and save/send contracts. Regression tests cover Back/route/close/notifications during import, task notification during SMTP, copied attachment persistence and continuation, network/storage failures and server draft ACK recovery.

## Напоминания 0.50.7

User reminder policy: before a timed task deadline. ReminderPolicy eligibility now bounds delayed delivery by actual timed deadline; date-only task expires at the next Moscow midnight, with existing09:00 auto reminder and24h catch-up limit retained. None/invalid/stale policies stay guarded. Explicit absolute date/time copy replaces incorrect «срок сегодня» when a date-only task reminder fires on a previous day. Existing key deduplication, completion/deletion cancellation, changed-date/time/lead invalidation and personal task source remain unchanged. Tests cover before/at/after timed deadline, date-only midnight, previous-day leads and disabled reminders, plus existing claim/dedupe/rollback/completion/immutable-event UI checks. OS alarm delivery remains subject to Android power restrictions.

## Контакты и переключение ящиков 0.50.8

Contact primary identity remains case-insensitive email. Creating/editing into another saved identity is blocked with accessible email error and retained form; no silent upsert of a different record. Directory card Edit resolves the saved personal record case-insensitively and uses its canonical original email/name/phone; rename removes original identity without leaving duplicate entries. Local delete uses existing app-owned native confirmation, cancel/failed write preserves record; directory unchanged. removeUpload captures login before queueing, so cleanup uses the initiating account upload directory rather than mutable current login. Review confirmed account-specific preference scope and old WebView destruction before saved-account switching, plus directory reply owner/generation guards. Fixtures verify duplicate protection, case-mismatched directory editing, phone/name preservation, delete cancel/rollback and delayed/stale search; actual Chromium checks accessible error/focus, destructive confirmation and long fields at narrow/landscape/large text.

## Офлайн-восстановление 0.50.9

OfflineMailbox indexes explicitly saved bodies into missing cached folder metadata, bounded by existing200/10/50/4MiB limits. Constructor repairs legacy body-only snapshots only from matching folder/UIDVALIDITY/UID references and retains original cachedAt, never claims a fresh network copy. Wrong-validity bodies cannot restore obsolete headers. OfflineMailStore reads committed .bak via AtomicFile.readFully when the base file is absent; unfinished .new is not treated as committed. Size/owner/authenticated-encryption checks remain. Cache constructor reads serialized with persist/delete to prevent interfering with an active AtomicFile transaction. Reconnect refreshes native folder list through generation-guarded refreshFolders while preserving active editor and manual-only sending. Tests use serialized model/encrypted filesystem backup fixtures plus actual-browser reconnect/folder/retained draft; actual Android process death/Keystore remains device verification.

## Надёжность вложений 0.50.10

Known-size MIME raw streams must reach the advertised length before a decoded file is accepted. Early EOF, cancellation after the final read, and input close failure delete the decoded temporary file and propagate failure to existing reconnect/retry logic. AttachmentFiles marks output complete only after output close succeeds. Unknown lengths remain bounded/indeterminate; no invented byte percentage. MIME size is encoded, so MailReader no longer applies the decoded 100 MiB cap to that size; AttachmentTransfer enforces encoded 200 MiB and decoded 100 MiB limits. Fixture tests cover silent short EOF, input close failure, exact-byte retry, cancellation during transfer/save, destination write/close failure, denied folder listing, duplicate names and failed-only retry preserving finished files. Provider refusing cleanup or process death may leave .part; actual Android provider/forced-close behavior remains device verification.

## Отложенные письма 0.51

Explicit local-only snooze: no IMAP move/archive/read mutation or SMTP. Account state snoozedMail contains bounded500 exact folder/UID/UIDVALIDITY references, sender/subject and epoch due time, no message body. Future items excluded before local thread grouping; disclosure section retains saved references even when current folder/page lacks the message. Due items return on render; one bounded wake timer refreshes only the safe inbox or marks updates without disrupting an editor/modal/search. Create/change/cancel writes use existing synchronous save/rollback and status primitive. Moscow fixed+03:00 presets/custom datetime-local (platform-owned picker) share quick-popup with focus/Escape/Back; field errors preserve input and focus. Calendar/reader theme and touch targets reused. Notification channel mail-reminders is independent from new-mail/task switches; Android channel/permission controls delivery. Native planner covers all saved accounts, uses exact references and keyed sent ledger, separates alarm from tasks/mail, reschedules after save/boot/package replacement/resume. Removed accounts cancel their posted reminders. Account switching/unsaved composer notification guards remain; arbitrary valid folders now allowed and URL-encoded into unique PendingIntent data, older INBOX links still decode. Physical OS delivery/Keystore/provider lifecycle remain device verification, no exact-alarm permission or promise.

## Отправка по расписанию 0.52

Explicit schedule consent changes only that immutable snapshot; ordinary offline drafts remain manual-only. Moscow datetime-local belongs to the existing platform-owned picker/quick-popup; validation/inline errors/focus/Back reuse snooze/writing owners. Persisting blocks duplicate submission/navigation until ACK, errors preserve text/time. Separate account queue shows up to150 active/recent records, active count, planned versus actual send time, read-only snapshot preview (full recipients/body/attachment names), unknown/failed results and cancellation/restoration. Completed source IDs prevent ordinary resending; native source uniqueness and guards are authoritative. Queue is private SQLite with AES-GCM encrypted per-account/per-item payload using Keystore key, and private independent attachment copies. SMTP credentials never stored in queue; sender alias frozen, current saved credentials/SMTP connection settings used at dispatch. No automatic send from an unplanned draft.

Atomic SQLite conditional phase gates: pending → preparing → transmitting durably before SMTP envelope/DATA → sent after ACK before IMAP copy/cleanup. Recover preparing as pending; recover transmitting as uncertain, never automatically retry unknown. Safe pre-DATA connection failure retries up to3 attempts with5/10minute backoff; authentication/validation failure stops. Decode failure stops dispatch without erasing encrypted record. Separate persisted network-constrained JobScheduler job + inexact RTC alarm restore on boot/package change/resume; one process runner and source uniqueness block concurrent duplicates. Existing manual send path retains TLS requirements and gets source protection; IMAP copy failure never changes successful SMTP status back to retryable.

Cancel updates durable eligibility before file restoration; transmitting cannot cancel. Failed local/WebView restoration keeps returned copy in encrypted queue until local save ACK. Restored draft has separate ID/tokens, reused local edits preferred over old returned snapshot; uncertain result needs existing app-owned native confirmation before restoration. Account removal pauses pending/preparing and refuses during transmitting; logout does not cancel explicit schedules. Dedicated private Android notification channel opens the correct account queue through existing guarded navigation. Tests exercise durable gate/fake transport, ACK/lost ACK, crypto account/item isolation, SQLite schema/claims, client rollback/recovery, busy/focus/Back and responsive popup. Actual Android SQLite/Keystore/JobScheduler lifecycle and physical background SMTP delivery remain device validation; exact clock-time promise and exact-alarm permission are intentionally absent.

ScheduledFiles removes only generated snapshot directories without a committed account/item row, under the same queue lock as creation. Database lookup failure preserves files; registered pending/failed/uncertain snapshots and unrelated paths stay intact. Pure filesystem regression reproduces interrupted-copy orphan cleanup.

Scheduled queue connections require SQLite synchronous=FULL; copied snapshot files are fsynced and actual lengths recorded before enqueue. Dispatch/restoration validates those lengths, stopping incomplete files before SMTP. These checks do not claim hardware power-loss recovery beyond OS/filesystem guarantees.


## Виджет 0.53

Native home-screen widget follows the active saved account; it is not a unified inbox. Explicit placement on the launcher exposes the displayed account, count and event titles there. Logout/key failure replaces content with a sign-in/unavailable state, removing the active account clears its private count metadata. No credentials, bodies or new event cache are copied to widget storage. Agenda projects local events, unfinished future tasks and committed CalDAV caches for the current/next month, at most3 entries within7 days; malformed/expired entries skipped, multi-day events retained, duplicate calendar occurrences removed. Footer explicitly labels saved events. No extra network/calendar fetch from the widget. INBOX unread count is server STATUS, never the latest50-page count; unknown remains a dash, successful verification has a Moscow date/time. Count refreshed on inbox page/read-status changes and existing background mail checks only while installed. Unread-count failure never fails mail reading or becomes zero. Android30-minute widget ticks plus state/account/calendar mutations/time changes trigger a serial latest-generation render; no exact update-time guarantee.

RemoteViews uses existing native violet/light palette, supports launcher resizing and system font scale, bounds event rows to available space. Mail/calendar tap intents are immutable and distinct per widget/route, accept only two routes through existing draft/transfer/send guards. A stale widget for another active account is refreshed with feedback, never switches accounts silently. No widget-triggered SMTP or new permission. Android resource compilation/model/browser navigation fixtures are automated; actual launcher geometry, process lifetime, accessibility and pending intents require phone validation.

Widget0.53.1 explicitly sets both visible/hidden states on every RemoteViews update: a launcher may reapply onto an existing view, so XML defaults cannot restore labels after a compact render. Compact mode hides redundant title/agenda labels and uses a single-line calendar action to retain space at large system fonts. Upcoming range is today through today+6, excluding day+7.


## PDF 0.54

Open action routes PDFs by extension/MIME into a non-exported native PdfPreviewActivity. Existing downloaded attachment cache/read-only provider is reused, no new document copy or remote viewer service. Own content authority, one decoded token, canonical parent/file existence/nonempty/100MiB checks before native rendering; no arbitrary content URI. External viewer URI grant happens only on its explicit button. Office/other formats retain existing external viewing. Main attachment download captures account owner, cancels on owner change/closing and checks owner/resumed state before opening; a finished background download asks for another tap instead of stealing focus.

Single-worker PdfRenderer owns its file descriptor, opens/closes one page at a time. Generation/closing guards discard stale rendered bitmaps. Bitmap area limited2M pixels/4096px side; display dimensions capped16384px, oversized page geometry produces an error. Zoom100–300% relative to viewport uses the bounded bitmap; high zoom may upscale raster instead of allocating unbounded memory. Changing zoom retains reading position around the viewport center; page/zoom/scroll survive Activity recreation, different page resets scroll. Activity shutdown clears the ImageView and closes the renderer on the same worker after pending rendering. No PDF JavaScript/remote web content.

Shared attachment byte-progress/transfer map reused for Open; busy Open/Download controls prevent duplicate operations, native error restores actions, reader state retained. Native page-jump dialog belongs to existing NativeUi.dialog, explicit range error keeps input/focus. Previous/next and zoom limits have disabled states, text/accessible labels and48dp targets. Landscape uses one scrolling toolbar; portrait two rows. Protected/corrupt/unavailable files keep a readable error and explicit external-viewer action; failed page supports fit-width retry. No real PDF render/device/emulator tested here: Java fixtures cover file isolation/memory geometry/page parsing; real browser fixtures cover loading/progress/duplicate/failure/retry/reader return at narrow/light/dark/large text. Actual PdfRenderer pages, gesture scrolling, font/layout/rotation and external Android viewer require phone validation.


## DOCX 0.55

Non-exported DocxPreviewActivity reuses private attachment-provider/cache validation, NativeUi appearance/header/48dp external action and captured-account Open flow. Local ZIP reader consumes only word/document.xml, no archive extraction or relationship/header/footer/network access. XML2MiB/visible text200k/runs12k/blocks5k/depth64/visited nodes100k bounds; actual inflated bytes checked, not only ZIP metadata. Strict UTF8/UTF16 decoding; DTD/entity declarations rejected before DOM, external resolver throws, no field/script/external relationship evaluation. WordprocessingML transitional/strict namespaces validated. Heading/run styles retained, tracked deletions omitted, tables flattened by rows and image/object placeholders explicit. Unknown/full Word layout is not promised.

Selected text stays native/selectable, no new persistent content copy. One worker builds the bounded preview; closing guards stop/stale results, Activity recreation retains scroll. Text-only explanatory footer and explicit external viewer remain in loading/error states; invalid URI never grants a viewer read capability. Java fixtures exercise actual zipped XML/Unicode/style/table/XXE/deflate/text/depth parsing. Actual Android text selection/layout/rotation remain phone validation; existing browser Open progress/duplicate/failure fixtures cover both file extensions.


## Блокировка приложения0.56

Global app UI lock, default off, controlled by native app-ui-lock preference rather than per-account JSON. Enabling requires secure device credentials and system biometric/PIN confirmation before durable enable; cancellation/write failure leaves it off. Disable requires the current unlocked session and existing app-owned consequence dialog. Native lifecycle callbacks cover Main/PDF/DOCX, opaque non-cancellable scrollable mask and FLAG_SECURE (including app-owned dialogs); login password protection retained when app lock is off. Internal app activity changes/configuration recreation keep an authenticated session; full background relocks. One-use foreground/owner/generation challenges reject stale/cancelled/superseded/destroyed results. Own tracked credential-confirmation roundtrip preserves its challenge while backgrounded, and accepts only the trusted system activity result after the owner starts again. Process restart discards authentication.

Android28+ native BiometricPrompt, Android30+ BIOMETRIC_STRONG with explicit PIN alternative; legacy devices use system credential confirmation. Credential intent resolved explicitly to MATCH_SYSTEM_ONLY OS component. No app PIN/password or biometric templates stored. Removed device screen protection stays locked with a route to Android security settings. Lock setting available on native sign-in screen after authentication as well as WebView Settings. Notification/widget navigation and late attachment opening defer while locked; existing editors are retained. New dialogs/foreground file export/calendar selection/credential setup guard locked state. Background account checks, reminders, scheduled SMTP and authorized ongoing backend work stay independent; widget/notification surfaces are explicitly outside the app UI lock scope.

Java AppLockSession fixtures cover foreground/background/rotation/internal activities, one-use owner/ticket checks, cancellation/stale/superseded success, external device credentials and cold restart. Browser settings/status/retained-editor tests use a mock bridge; they do not simulate authentication. Actual BiometricPrompt/PIN/mask/touch/FLAG_SECURE/Activity lifecycle require phone validation.

Device-credential result codes are durably allocated before launch, never reused after challenge replacement/process restart, and checked against the current request. Framework Activity subclasses support full-int result codes. A foreground return with an abandoned PIN screen allows explicit replacement; late success/cancel from the old screen cannot complete the new challenge.

## Напомнить ответить 0.57

- Отдельная запись replyReminders, максимум500 на ящик. Ничего не отправляет и не скрывает письмо. Точные folder/UID/UIDVALIDITY и Message-ID обязательны; нельзя угадывать ответ по теме. UI повторно использует openSnooze/saveSnooze/renderSnoozePopup с именованным вариантом followUp, штатный datetime-local Москвы, rollback save() и closeQuick.
- Перед напоминанием JobScheduler с сетью проверяет In-Reply-To/References в INBOX, точное совпадение идентификатора и отправитель, отличный от логина и настроенного адреса отправки. Автоответы с такими заголовками считаются ответом. Ответы без заголовков или перемещённые из INBOX не обнаруживаются; это явно сообщено в UI. Интернет/ошибка проверки оставляет статус pending и повторяет проверку с backoff; Android может задержать её.
- Все сохранённые ящики, отдельный канал «Напомнить ответить», не зависит от переключателей новых писем/задач. Удаление ящика/напоминания отменяет связанное уведомление; после сетевого запроса заново проверяются владелец, запись и отмена. Ledger статуса отдельный от общей JSONмодели, чтобы фоновая проверка не перезаписала черновик.
- Найденный ответ и успешное уведомление завершают запись. Список показывает результат; редактирование срока создаёт новую проверку. Перезагрузка/обновление восстанавливают план через NoticeCenter.schedule. Force-stop требует последующего открытия приложения, как прочая фоновая работа Android.

## Единый поиск0.58

- Отдельный маршрут search, current-account scope, без ИИ и объединения разных ящиков. Поле запроса/clear, фильтры папки/отправителя/дат/вложений; авторские поля и ошибки, нативный select/date как существующая карта владельцев. Даты почты — полученные заголовки Москвы, календаря — его модель; контакты не ограничиваются датами.
- Поиск почты на сервере по выбранной папке: SearchQuery.Filters строит IMAP Argument, MailReader.smartPage имеет собственный UIDsnapshot отдельно от обычного inbox. Запрос/From являются строками Argument, управляющие символы запрещены. Широкие SINCE/BEFORE границы служат кандидатами; точное включительное сравнение делается с московской датой в заголовке. Вложения только из BODYSTRUCTURE, неизвестные не считаются true; показаны число проверенных кандидатов, совпадения порции и загрузка следующих50. Пустая отфильтрованная порция не означает конец поиска. Не читаются тела/вложения для отображения результата, Seen не меняется.
- Офлайн: только сохранённые заголовки выбранной папки; отправитель/тема и метаданные. Поиск содержимого офлайн не обещается. Ошибка сети допускает существующий кэш с явной отметкой, ошибка авторизации не маскируется кэшем.
- Календарь: местные события/незавершённые задачи плюс выбранные доступные календари через CalendarBook. Серверный диапазон≤12месяцев, без дат текущий+следующий месяц; более длинный диапазон показывает ошибку только календаря, почта/контакты продолжают поиск; пересечения многодневных событий и повторения не дублируются. Ошибки отдельных источников обозначены; доступные записи сохраняются. Контакты: личные/история получателей плюс разрешённая книга company через contactsPage, минимум2символа, её серверная пагинация сохранена.
- Запрос не сохраняется в localStorage/URL: приватный account-scoped AndroidWebView. IME не запускает запрос посередине композиции; debounce300ms, Enter сразу, clear сразу инвалидирует старые ответы и применяет оставшиеся фильтры. Каждый ответ привязан к поколению; разделы показывают собственные ошибки/повтор и не перерисовывают форму. Возврат из письма сохраняет поисковый экран.
- Проверки: AdvancedSearchTest.java, test-smart-search.cjs и реальный Chromium test-smart-search-layout.cjs; все прежние проверки. Реальные IMAP/LDAP/CalDAV и AndroidWebView требуют проверки на телефоне.

## Исправления плотности и офлайн0.59

- Исходные encoded-words восстанавливаются в MimeWords: строгие B/Qбайты, совместимая кодировка, объединение разрезанных символов без эвристического перекодирования. Самостоятельно валидные слова декодируются отдельно, сохраняя BOM/состояние UTF-16/ISO-2022. From берётся из сырого заголовка лишь при совпадении разобранного адреса с ENVELOPE; адрес никогда не извлекается из уже декодированного имени. Если восстановление не удалось, Native даёт адрес; UI выбирает сохранённое корректное имя контакта/адрес для старых повреждённых копий.
- OfflinePrefetch берёт только первые50 UID/UIDVALIDITYпоследней загруженной страницы папки, в обратном порядке для приоритета новых при лимите кэша. Отдельный однониточный read-only reader, PEEK, без SMTP/SEEN/вложений/RFC822payload. Тексты ограничены24000UTF-16единицами с явной пометкой частичного текста; прежний общий зашифрованный лимит50тел/4МБ сохраняется. Нет гарантии50копий при сетевых сбоях/лимите объёма — итог показывает реальное сохранённое число.
- Загрузка только при включённой offlineMail, активном владельце и видимом Activity; pause/account change/destroy/clear отменяют поколение. Последняя успешная страница может продолжиться на resume; отдельный запрос письма не ждёт очередь предварительной загрузки. Commit проверяет владельца/поколение внутри общего lock хранилища; clear invalidates epoch. Ревизия read/unread сохраняет явную отметку пользователя при запоздалой предварительной копии.
- Черновики — локальный маршрут drafts и уникальный synthetic select value, который не отправляется IMAP. Реальные folder IDs/UIDkeys неизменны; Junk/Outgoing локализованы только для показа. Снимки расписания защищены прежними scheduledProtected, удаление/ACK/rollback сохранены. Автосохранение не заменено ручной кнопкой; failed save сохраняет редактор и показывает явный статус.
- Новый экран письма/редактор открывается сверху; фоновые обновления и повтор body не сбрасывают позицию. Header-collapse не меняет высоту документа, не скрывает открытое меню/видимый фокус; другой маршрут всегда восстанавливает header. Возраст копии обновляется при render/scroll/resume/visibility; абсолютное время в title.
- Календарь: месяц показывает calendarItem выбранного дня под сеткой; popup использует те же данные и убирает дублирующий интерактивный список в фоне. Четыре режима остаются одной строкой, keyboard/scroll доступны.
- Проверки: MimeWordsTest/OfflinePrefetchTest плюс test-polish.cjs/test-polish-layout.cjs и все прежние проверки. Нативная очередь/криптография/реальные IMAP и Androidkeyboard требуют проверки на телефоне.

## Входящие, правила и личный ответ 0.60

- Дата/диалоги/обратная связь/поля/выбор папки остаются прежними общими владельцами: snoozeInput, quick-popup, btn/field, say, authored select. Поиск папки раскрывается и сохраняет незавершённый запрос; общий поиск остаётся отдельным маршрутом. Папка и счётчик перенесены в шапку; legacy ID-стиль account-label явно переопределён для ограниченного места.
- responseReminders — личные напоминания, отдельные от проверки ответа собеседника replyReminders и скрывающего письмо snoozedMail. Используется существующий офлайн alarm MailSnooze/NoticeCenter с независимым ключом personal. До 500 на каждый тип; аккаунт/UID/UIDVALIDITY и отмена сохранены. Ошибка сохранения не закрывает окно. Письмо не читается и не отправляется при установке из списка.
- mailCategories — правила текущего аккаунта. JS и MailCategories.java применяют одинаковые ограниченные literal-условия по первому совпадению. Домены точные, не substring, условия OR; поле контактов содержит точные адреса. Уведомления читают свежие правила после сети, сохраняют watermark даже для исключённых писем, выбирают ссылку только на разрешённое письмо. С выключенными категориями проверяются до 500 последних новых заголовков, без тела.
- Выбор — Set UID-ключей только загруженных представителей цепочек, не всех результатов/членов цепочки; максимум 50. Mark-read последовательный и сообщает частичные ошибки. Свайп открывает действия без немедленной мутации; есть обычная кнопка и клавиатура. Корзина — app-owned dialog с первоначальным фокусом на отмене.
- MainActivity.moveMail/MailReader.moveMail: отдельное действие без readWithReconnect/retry; точный UID и UIDVALIDITY проверяются до и после перехода в READ_WRITE; только MOVE и существующая папка, close(false), восстановление READ_ONLY/validity. При lost ACK не повторяем, предлагаем обновить список. Письма с локальными напоминаниями или календарными связями не перемещаются, пока связь не снята. Это сохраняет их неизменяемые ссылки без угадывания нового UID.
- Правила используют прежний dirty/discard диалог; Escape сохраняет данные до явного отказа. Цвет связанных календарных событий берётся из категории сохранённого письма. Дата-подсказка только для явной валидной будущей DD.MM.YYYY HH:MM; связь/событие сохраняется лишь явным действием пользователя.
- Основание изменений: набросок и запрос владельца в текущей сессии, README; проверки MailCategoriesTest/MailMoveTest/test-inbox-system.cjs/test-inbox-system-layout.cjs и прежние сценарии. Нативные Android-уведомления и реальный IMAP MOVE проверяются отдельно на телефоне.

## Исправления аудита 0.61

- Источник: замечания владельца/Gemini, сверенные с существующим кодом; HTML-макет служит только ориентиром. Нет выдуманных вложений, проверок SPF/DKIM или новых CDN.
- Native modal Drawer перекрывает FAB и блокирует фон; Escape/Android Back возвращают фокус. Footer настроек виден независимо от прокрутки меню. Отступы Android остаются в нативном контейнере.
- MIME metadata обходится независимо от выбора текста. Сохраняются реальные пути AttachmentParts; глубина12/500частей ограничивают сложность. Вложенные RFC822 остаются файлами, их payload не декодируется для списка.
- Reply-All потребляет отдельные To/Cc, исключает ownAddresses; Bcc не используется. Старые копии без этих полей требуют обновления. Пересылка получает ограниченный100МБ read-only/PEEK оригинал, удаляет внешние Bcc/Resent-Bcc и создаёт ручной черновик с .eml. Устаревший callback удаляет свой stagedfile, не заменяет новый редактор. Ошибка сохраняет читаемое письмо.
- Почтовые folderIDs не меняются; серверные и локальные черновики не объединяются фиктивно. Пустая очередь скрыта, явный вход через меню сохранён. Статистика загруженных писем доступна в Drawer.
- Буква серверного справочника создаёт prefix-query и отдельное состояние пагинации; смена запроса отбрасывает поздние ответы. Локальные/исторические контакты переходят по уже загруженным группам. Недельный список прокручивается к выбранному дню после завершения загрузки.
- Основание проверки: MailAttachmentsTest, test-review-fixes.cjs, test-review-fixes-layout.cjs и прежние сценарии. Реальный сервер/Android проверяются отдельно; фикстуры не отправляют писем.

## Чтение и файлы 0.62

- Источник: два макета владельца и разрешение «Давай». Используются renderMail/attachmentCard businessvariant compact, существующие fileOpenButton/fileDownloadButton/downloadTransferMarkup/onFileDownloadProgress/quick-popup/say. Раздел Files сохраняет прежнюю карточку и контракты native download/open/SAF.
- Открытие файла остаётся основным действием строки; download/save/share и полное имя — в native details. Открытость файла и дополнительных действий сохраняется при render. Прогресс вне скрываемого меню; busy/error/unknown total и точные MIMErefs не меняются. Quick-download completion освобождает обе кнопки файла; фокус возвращается инициатору лишь когда пользователь не перешёл на другой контрол.
- Reply-dock повторно использует reply/reply-all/forward-mail/reader-trash. Существующие черновики/ToCc/References/EML/подтверждение корзины/UIDVALIDITY/offlineguards не меняются. При отсутствующем тексте ответ/пересылка отключены, retry доступен. Header сохраняет Back/snooze/read/more; архив остаётся в меню.
- datehint расширяет прежнюю явную будущую дату: однозначное относительное слово только в subject плюс одно HH:MM/HH-MM; source date обязателен, база — Москва исходного письма. Точная дата валидируется round-trip snoozeInput и должна быть в будущем. Чип открывает существующий linkpicker с этой датой, сохранение связи только явной кнопкой. Нет выдуманного AI-анализа и автоматического создания событий.
- Полные sender/email сохранены, category берётся из mailCategory. Body/phones/offlinefiles отображаются только из реальных данных. Главный scroller остаётся document; bottom dock использует существующий --bottom-height/ResizeObserver и резерв reader. На коротком экране проверяется доступность последнего контрола, а не умещение всего раздела.
- Проверки: test-reader-compact.cjs, test-reader-compact-layout.cjs и прежние сценарии. Условная вторичная операция в browserfixture сначала раскрывает её реальный владелец. Дизайн использует те же tokens; изменение компактности касается только reader.

## Создание события из письма0.63

Источник: прямой запрос владельца. Повторно используются calendarLink, renderForm(event), canLeaveForm, save, eventLinkKey, calendarItem и точный mailref. Частный draft._mailEvent живёт только в форме и не сохраняется в событии; createdFromMail содержит account-scoped UIDkey для повторного открытия. Событие и eventMailLinks фиксируются одним saveState; failed save откатывает обе коллекции и сохраняет draft. После успеха выбранная дата в календаре, без автоматического SMTP/задач/изменения серверного события. Дата/время проходят round-trip Москвы, title не пустой, лимит500связей сохранён. Сохранённая связь обновляет заголовок/дату при редактировании из письма; Cancel/Back/dirty-navigation используют прежний guard. Поздний callback linkpicker не заменяет форму. Источники подтверждения: test-mail-event-create.cjs и test-mail-event-create-layout.cjs плюс прежние сценарии.

Удаление локального события удаляет его связи в той же записи состояния; ошибка откатывает обе коллекции. Обычное редактирование события обновляет название и дату существующих связей. Письма не удаляются.

## Входящие по образцу0.64

Источник: скриншот и явный запрос владельца на такое же оформление. Общая blue/neutral палитра заменяет brand только визуально, контракты папок/категорий/UID/цепочек/сохранения/SMTP не меняются. Header age появляется только при реальной офлайн-копии с timestamp; он использует существующие copyAge/updateCopyAge/title. Inbox banner обозначает офлайн, reader оставляет прежнее раскрытие времени копии. Данные не дублируются по высоте. Авторский folder-title/select остаётся владельцем выбора папки.

Avatar email initials используют две части локального адреса или два codepoints одного логина; displaynames сохраняют существующую схему и реальные значения. Badge цепочки перемещён в footer, scoped count/aggregate unread прежние; метаданные вложений конкретного письма не меняются. Compose dock остаётся отдельной непрозрачной полосой; Drawer top-layer и меню строк сохраняют приоритет. Primary contrast проверяется в обеих темах, NativeUi.button и Widget surfaces синхронизированы с CSS. Нет принудительного offline или скрытия настройки задач/категорий. Проверки: test-inbox-reference-layout.cjs и все предыдущие сценарии.

## Палитра 0.65

Фиолетовая тема восстановлена по уточнению владельца. Компактная шапка, превью, счётчики цепочек и резерв под кнопку написания из 0.64 сохраняются. Изменения касаются оформления; отправка, поиск, фильтры и работа офлайн не меняются.

## Уточнение вёрстки 0.66

Разделитель строки письма принадлежит .mail-row-wrap; .mail-row-main содержит кнопку открытия и отдельное меню действий, поэтому действие «В календарь» не смещает меню. Метаданные строки имеют общий отступ. Непрозрачная панель написания сохраняет высоту и резерв списка, границу обозначает --line. Офлайн-плашка прокручивается естественно. .reader-files-jump переводит фокус и прокрутку к доступной секции вложений, не скачивает файлы и не меняет порядок текста и цитат. Переход по контактам использует существующее измерение шапки и буквенной панели, CSS обеспечивает запасной отступ. Фиолетовые токены 0.65 сохранены.

## Визуальная иерархия 0.67

Принят проверенный владельцем вариант оформления после анализа внешнего CSS-предложения. Фиолетовые токены остаются прежними. Входящие: прочитанное имя использует --muted, непрочитанное --ink и усиленное начертание. Видимая граница .mail-row-wrap начинается от колонки текста (74px), обёртка сохраняет высоту и включает действие календаря. Фильтры сохраняют области нажатия 44px. Плоские поля и текст чистого листа относятся только к .compose-form; ошибочные поля сохраняют прежние стили валидации. Подсказки и инструменты используют существующие селекторы. В .day-popup-list карточки событий используют --surface, список остаётся прокручиваемым, подвал доступным. Отправка, отмена, автосохранение, расписание и серверный календарь не меняются. Семантические цвета меток важности сохранены. Нативные диалоги и виджет используют прежнюю фиолетовую тему, новых цветов поверхности нет.

## CSS-модули 0.68

Первый этап механического разделения CSS сохраняет точный итоговый файл и все пользовательские сценарии 0.67. Генерация включена в build.py, проверки npm обнаруживают устаревший assets/app.css и изменения состава модулей. Источники и порядок описаны в docs/ui-architecture.md. JavaScript, Pilot и данные не мигрируют в этом выпуске.

## JavaScript-модули 0.69

Модули ui/core, ui/mail, ui/calendar, ui/contacts и ui/compose владеют общими преобразованиями и выделенными представлениями. Контекст строк писем, чтения и редактора передаётся явно из ui/app.mjs. assets/app.js генерируется scripts/build_ui.cjs в один офлайн-IIFE-бандл. Селекторы, фиолетовые токены, данные и обработчики Pilot сохраняются. Даты и превью читаются из текущего состояния при вызове, контакты для восстановления имени разрешаются лениво. Остальные контроллеры остаются в ui/app.mjs; типизация и смена UI-фреймворка не входят в выпуск. Команды и владельцы описаны в docs/ui-architecture.md.

## Представления почты и календаря 0.70

Разметка списка входящих и цепочек вынесена в ui/mail/inbox.mjs и thread.mjs.
Календарная сетка, выбранный день, карточки событий и результаты поиска находятся
в ui/calendar/render.mjs, item.mjs и search.mjs. Каждый вызов получает текущий
контекст; контроллеры, выбор даты, загрузка, фокус и callbacks Pilot остаются
в ui/app.mjs. Состав DOM, действия, фильтры и порядок событий сохранены.
CSS и фиолетовая тема не изменены. Это продолжение разделения представлений,
без миграции состояния, TypeScript или нового UI-фреймворка.

## Представления контактов, задач и заметок 0.71

Список, карточка и основной экран контактов вынесены в ui/contacts/list.mjs,
card.mjs и render.mjs. Строка задачи и её список находятся в ui/tasks/item.mjs
и render.mjs; заметки — в ui/notes/render.mjs. Данные и функции передаются
при каждом вызове. Загрузка справочника, префиксный поиск, сохранение,
удаление, фокус и обработчики действий остаются в ui/app.mjs.
DOM, порядок задач, фильтры и офлайн-состояния сохранены.
CSS и фиолетовая тема не меняются; APK по-прежнему содержит один локальный бандл.

## Черновики и почтовые диалоги 0.72

Разметка черновика и списка черновиков находится в ui/drafts/item.mjs и render.mjs.
Категории и их диалог вынесены в ui/mail/categories.mjs и category-dialog.mjs.
Очередь расписания, просмотр её копии, диалоги планирования и напоминания находятся
в scheduled-list.mjs, scheduled-preview.mjs, schedule-dialog.mjs и snooze-dialog.mjs.
Текущий контекст и доступные методы Pilot передаются явно. Представления не
отправляют письма и не меняют сохранённые данные; уведомления только читают
доступность через прежний метод. Валидация, защита черновиков, сохранение, отмена,
таймеры и запросы остаются в ui/app.mjs. CSS, DOM-действия и фиолетовая тема сохранены.

## Поиск, настройки, профиль и файлы 0.73

Форма общего поиска и его результаты находятся в ui/search/render.mjs и results.mjs.
Настройки и профиль — в ui/settings/render.mjs и profile.mjs, список файлов —
в ui/files/render.mjs. Текущие критерии, подтверждённый запрос, результаты,
настройки и методы чтения Pilot передаются при каждом вызове. Контроллеры запросов,
запись настроек, загрузка файлов и переходы остаются в ui/app.mjs.
Разметка сохраняет разделение редактируемого запроса и подтверждённых результатов,
пагинацию, ошибки и точные ссылки на письма. Вывод экранов не запускает загрузку
файлов, отправку или нативные диалоги. CSS, действия и фиолетовая тема сохранены.

## В разработке: формы, инструменты письма, сводка и пакетные вложения

Разметка локальных форм находится в ui/forms/render.mjs. Подпись, шаблоны
и подтверждение несохранённых изменений — в ui/compose/writing-dialog.mjs.
Сводка и её результат — в ui/digest/render.mjs и result.mjs; диалог пакетного
скачивания — в ui/files/batch-dialog.mjs. При каждом вызове передаётся текущий
контекст. Представления сохраняют приоритет подтверждения выхода, доступность
действий по состоянию операции, точные ссылки сводки и экранирование текста.
Сохранение, отправка, анализ Gemini, прогресс и отмена скачивания остаются
в контроллерах ui/app.mjs. CSS, DOM-действия и фиолетовая тема сохранены.

Исправление в разработке: текущий московский день вычисляется при выводе почты, календаря и задач, а также при действии «Сегодня». Смена суток, месяца и года не требует перезапуска. Выбранная пользователем дата сохраняется до явного перехода. Часы сами не меняют данные и не инициируют запросы.


## Нативный редактор (разработческая сборка, без выпуска)

Явно разрешённый владельцем переход на Compose идёт параллельно WebView; ограничения
первого пакета о сохранении старого стека не запрещают эту отдельную opt-in сборку.
Владелец формы — NativeComposer/NativeComposerModel; протокол — прежние MailSender,
OutgoingMail, MailThreadHeaders. ViewModel сохраняет ввод при пересоздании; закрытие
сначала сохраняет текст. Отдельный NativeDraftStore шифрует один черновик на аккаунт,
не перезаписывая старые drafts/state или ScheduledMail. Пустая форма нового ответа
не подменяет уже сохранённое письмо. Поля и отправка блокируются при смене аккаунта.

Кнопка отправки сначала проверяет адреса и сохраняет черновик, затем даёт 10 секунд
для отмены. Фон отменяет ожидание; начатый SMTP фиксирует свой результат. Durable
маркер dispatching до SMTP, uncertain при неопределённом результате; после сбоя
процесса/диска автоматического повтора нет. Подтверждение повторной попытки отделено
от кнопки отправки. Провал Sent-copy не допускает повтор SMTP. CAS по ревизии
защищает от устаревшей Activity. Повторная попытка и смена отправителя не происходят
по восстановлению сети или экрана. NativeComposerTest проверяет протокольные и
постоянные состояния; проверки Android UI/Keystore на устройстве остаются обязательны
перед публичным переключением.

Вложения в Compose: OpenMultipleDocuments через ActivityResult API; запуск только
после сохранения текста, callback привязан к аккаунту и id исходного черновика.
Потерянный после уничтожения процесса callback не импортируется в другой черновик.
NativeAttachments проверяет 10 файлов/100 МиБ каждый, токены, пути, дубликаты и
метаданные; AttachmentFiles ограничивает фактический поток. Копии приватные,
независимые от URI; разрешения на постоянный доступ не запрашиваются. Удаление
копии только после фиксации удаления метаданных. Неопределённая отправка сохраняет
файлы. SMTP по-прежнему запускается только кнопкой после интервала отмены.
NativeAttachmentsTest проверяет MIME round-trip, лимиты, неисправный/отменённый поток,
раздельные папки и сохранение копии при неопределённом SMTP. Picker и реальные
content-provider сценарии требуют проверки на устройстве.


Список/чтение Compose: NativeInboxModel владеет данными и запросами вне Activity.
SavedStateHandle хранит только аккаунтный hash/точный MailNoticeTarget и позиции.
Поворот удерживает выполняющийся запрос. Смена аккаунта очищает маршрут/позиции;
пересоздание процесса восстанавливает только доступную копию без IMAP/Seen-записи.
Scroll позиции читателя не перезаписываются нулём, пока тело ещё загружается.
NativeReadGate инвалидирует результаты до закрытия сокета; закрытие JavaMail
запускается вне UI, но не обещает немедленного завершения блокирующего чтения.
Открытие скачанного файла однократное, только в foreground после AppLock.
NativeReadGateTest — проверка координации с фиктивным соединением, не Android UI.


Пагинация Compose — явная «Показать ещё», по 50 заголовков, без автозагрузки тел.
NativeMailPage проверяет folder/UIDVALIDITY/snapshot/total/nextOffset, дедуплицирует
UID и допускает пустую страницу при продвижении удалённых UID. Онлайн-курсор
использует тот же MailReader; другие чтения не заменяют его. Источник списка
(pageOffline) отделён от источника открытого тела. После ошибки/фонового закрытия
сохраняются строки и предлагается явное обновление; свежий снимок не дописывается
к старому. На повороте cursor/rows остаются в ViewModel. После смерти процесса
доступна только прежняя офлайн-копия, без скрытого восстановления через сеть.
NativeMailPageTest проверяет смешение снимков и сохранение старого состояния при
отказе продолжения, не заменяя реальный IMAP-тест на устройстве.


Подсказки адресатов Compose: NativeRecipients — канонический парсер конечного
фрагмента и фильтр дубликатов, OutgoingMail — проверка итоговых адресов. Источники
контактов только активного аккаунта, UniversityDirectory только на поддержанном
сервере. Сеть отделена от локального worker. От двух символов, 300 мс debounce,
20 результатов; незавершённая IME composition и курсор не в конце блокируют поиск.
NativeRecipientEpoch привязывает ответ к конкретному запросу/полю/черновику/аккаунту.
Выбор меняет только последний фрагмент и сохраняется через прежнюю автозапись
нативного черновика. Дубли между To/Cc/Bcc и неподтверждённые адреса не вставляются.
Фон/закрытие/отправка очищают запросы; пользователь видит источник и ошибку/повтор.
Тесты нативной политики и прежнего справочника не подменяют Android IME/focus и
проверку реального сервера на устройстве. Запросы и ответы не логируются.


Письменные инструменты Compose: NativeWritingTools читает существующие шаблоны
и подпись активного аккаунта, не пишет поверх общего state WebView. До100 валидных
шаблонов: id/title/body, прежние лимиты180/100000. ModalBottomSheet имеет loading,
empty, error/retry и явную вставку. NativeComposerModel хранит выделение в памяти,
блокирует вставку на незавершённом IME и сохраняет результат до изменения UI.
При ошибке метаданных/CAS исходное тело на экране не заменяется. Отмена шторки не
меняет тело. Фон закрывает шторку и отменяет позднюю публикацию библиотеки.
Ручная подпись не зависит от auto-флага, не повторяет конечный блок с пустыми
строками, не переписывает старые черновики. Условный шаблон/HTML остаётся буквальным
plain text. CRUD шаблонов и редактор подписи пока принадлежат прежнему клиенту.
NativeWritingToolsTest проверяет текстовую политику, не Android focus/IME.

Ответ всем в Compose: NativeReply использует явные replyToRecipients/toRecipients/
ccRecipients, без объединённого recipients и Bcc. Полный Reply-To сохраняется
MailReader; при отсутствии Reply-To используется From. Исключаются только известные
from/user текущего аккаунта и дубли без учёта регистра. Ошибки/неполные заголовки,
группы и превышение50 адресатов/4000 символов блокируют подготовку без усечения.
To/Cc редактируются до отправки, входящие вложения не копируются, Message-ID и
References проходят прежнюю проверку. Существующий нативный черновик, включая
revision0, не перезаписывается и явно обозначается как возобновлённый.
Явное «Обновить письмо» обходит офлайн-копию только для данного чтения, использует
BODY.PEEK и не пишет общий кэш/Seen. Ошибка сохраняет уже видимый текст. Автозагрузка
ради подготовки ответа не выполняется. NativeReplyTest/ReadFlagTest — фиктивные
папки и MIME без сети; Android-геометрию и реальный IMAP они не проверяют.

Пересылка текста Compose — отдельное явное действие. NativeForward создаёт новый
черновик без To/Cc/Bcc/In-Reply-To/References и без исходных файлов; исходные данные
не меняются. В тело попадают только выбранные поля отправителя/даты/темы и точный
загруженный текст. Управляющие символы в метаданных не создают дополнительные
строки заголовка. Исходная тема с CR/LF отклоняется. Длинный или помеченный как
частичный текст не обрезается повторно, подготовка прекращается с объяснением.
Получатель обязателен перед отправкой и вводится явно. Признак forward-text живёт
в зашифрованном черновике и сохраняет постоянное пояснение об исходных вложениях;
никакие исходные MIME-заголовки не добавляются по этому признаку. Файлы добавляются
прежним picker, доставка — прежним механизмом явной отправки и отмены. Для EML и
полной пересылки с исходными файлами доступен прежний клиент. NativeForwardTest
проверяет настоящий MIME без сети; Android-меню и реальная доставка не проверены.

Удаление нативного черновика требует отдельного явного подтверждения. Model хранит
discardOpen в памяти, блокирует редактирование/отправку/выбор файлов за диалогом и закрывает
его при фоне. Только editing; подтверждение не обходит контроль unknown/dispatched
доставки. Перед действием — foreground/AppLock/аккаунт, затем точные owner/id/revision/
status под gate. NativeDraftStore удаляет только соответствующий файл и проверяет
отсутствие base/.bak/.new. NativeDraftDiscard очищает копии лишь после успеха.
Ошибка удаления не очищает файлы и оставляет UI-текст; ошибка очистки после удаления
даёт отдельное предупреждение, не восстанавливая запись. Чужие аккаунты/черновики
и внешние symlink-цели не удаляются. Сервер, общая WebView state и очередь не меняются.
RestoreOnly восстанавливает существующую запись, не создаёт письмо взамен отсутствующей.
Обычный явный compose создаёт новый черновик. Host-тест проверяет политику/папки,
не Android AtomicFile/Keystore и жизненный цикл диалога.

Seen в Compose меняется только явным действием меню. Model задаёт конкретный
boolean на точной folder/validity/uid активного owner через прежний MailReader.seen.
Foreground/AppLock/аккаунт/поколение проверяются перед записью; UI обновляется
только после совпадающего boolean-ответа. Ошибка не откатывает/не повторяет команду
автоматически: изменение могло примениться, требуется явная проверка. Навигация
и фон отбрасывают позднюю публикацию, но не гарантируют отмены уже принятого STORE.
NativeSeenFlags изолирует подтверждённые флаги в памяти ViewModel; старые page/body
их не отменяют. Свежее онлайн-чтение даёт новое наблюдение, успешное обновление
списка очищает прежние переопределения этой папки. Общий диск-кэш не перезаписывается,
поэтому после смерти процесса сохранённая копия может содержать старую отметку.
Проверка серверного состояния — явное обновление. Обычный BODY.PEEK флаг не меняет.
Host fixtures проверяют подтверждения, ключи и пагинацию; реальный IMAP и Android UI
не считаются проверенными.

Папки Compose: folder — часть списка, pager, позиций и SavedStateHandle. NativeFolders
сохраняет фактическое имя IMAP, только INBOX регистронезависим. Сначала локальный
список, полный серверный LIST — явно. Серверные черновики не объединяются с NativeDraft.
Неправильные/слишком большие списки отклоняются без усечения. Закрытие/фон/смена
аккаунта инвалидируют отдельный folderGate, не затрагивая курсор писем при закрытии
меню. Новая папка сбрасывает rows/body/cursor/позиции до запроса. Первая страница
проверяется на ожидаемый folder; продолжение — дополнительно на owner/folder pager.
Поздние onClick/scroll другой папки игнорируются. Сохранённые folder/mailRoute
принимаются только для совпавшего owner. Отсутствующий кэш не доказывает пустоту
на сервере. Общие cache/state и серверные папки не изменяются. Рендер NativeFolderList
в тестовом контейнере не подменяет проверку Android Dialog, lifecycle и IMAP ACL.

Поиск Compose: query хранится только в retained ViewModel. В SavedStateHandle
нет текста запроса; при восстановлении процесса сбрасываются позиции/reader-route
от утраченной поисковой выборки. Курсор привязан к owner/folder/query, страницы
другого запроса не подмешиваются. Запрос сети — только явной кнопкой; отдельная
кнопка ищет сохранённые sender/email/subject (без обещания офлайн BODY-поиска).
Фильтры unread/attachments применяются к загруженным результатам и явно так
обозначены. Неизвестный hasAttachments не принимается за true.

Контакты Compose: NativeContactsModel владеет отдельной последовательной сессией
UniversityDirectory; открытие читает только локальное состояние. Серверные книги,
поиск, буква и следующая страница запрашиваются явно. Все буквы доступны независимо
от первой страницы. Результаты и cookie scoped к owner/book/query/поколению;
уход в фон/закрытие/смена книги инвалидируют публикацию. Повтор cookie отклоняется,
включая цикл через несколько страниц; ошибки не стирают уже загруженные страницы.
Нативный экран не пишет книги/общий state JSON. «Написать» создаёт черновик с выбранным
адресом только при отсутствии существующего; существующий черновик открывается
с пояснением без изменения адресатов и без автоматической отправки.

Календарь Compose: NativeAgenda считает даты независимо от системного часового
пояса; «Сегодня» — Europe/Moscow. Неделя начинается в понедельник; на границе
месяцев читаются оба месяца CalDAV. Выбор дня меняет список на этот день;
повестка начинается выбранным днём до конца месяца. All-day DTEND и окончание
в полночь исключают конечный день. Завершённые задачи не показаны в календаре.
Повторы/исключения CalDAV раскрывает прежнее ядро. Цвета remote/event/task и
счётчики основаны на доступных данных; при отсутствии кэша есть явное сообщение,
а не обещание пустого сервера. Чтение кэша не выполняет сетевое обновление.
Подключение/редактирование серверных календарей пока остаётся
в прежнем клиенте. Полный перечень: docs/migration/native-feature-status.md.


Задачи/заметки Compose: NativeWorkspaceModel использует тот же account state JSON,
не отдельное хранилище. Save/delete/toggle выполняются под ReplyReminderCenter.class
над свежими данными с точным сравнением ожидаемой записи; отсутствующий/изменённый
record не перезаписывается. Неизвестные поля, mail/calendarSource и остальные коллекции
сохраняются. Только после успешного commit запрашиваются штатные напоминания/виджет.
Удаление и выход из формы подтверждаются; фон закрывает неподтверждённые диалоги.
Ротация удерживает поля в ViewModel, Bundle не содержит текста. После process death
несохранённая форма не обещает восстановления. Ошибка commit/CAS оставляет редактор.
Список отвергает дубли/malformed ID вместо падения LazyColumn или тихой перезаписи.

Bridge.saveState выполняет SharedStateMerge от последнего клиентского load/save
к свежим prefs; клиентская база после success равна incoming, а не merged. Поэтому
повторные сохранения старого клиента не удаляют незамеченные нативные изменения.
Разные object-поля объединяются; несовместимые правки массивов/полей отклоняются
целиком. MainActivity при resume перечитывает внешние данные только вне открытой
формы/модального редактирования. Проверки CAS/слияния — host, Android AtomicFile/
SharedPreferences disk и реальные уведомления требуют устройства.


Свои события Compose: открываются только точным id из account state.events либо
создаются с датой выбранного дня. Название/обязательная дата/необязательное время,
Москва; диапазон 1970–2100 совпадает с NativeAgenda и показан в форме. Календарная
карточка kind=remote не даёт редактирования; CalDAV write не вызывается. Изменения
и каскад eventMailLinks — один CAS/commit. При edit меняются кэшированные title/date
только local:<id>; при delete убираются только эти связи. Исходные письма, задачи,
чужие связи и неизвестные поля сохраняются. Save/delete возвращают в календарь
к дате записи; cancel — к выбранному дню. Обработка возврата проверяет owner,
перечитывает кэш без автоматического сетевого обновления. Ошибка оставляет форму.
Сравнение старого/нового состояния ограничено 10 выполненными fixture cases и не
подменяет проверку Activity, ввода, Keystore/SharedPreferences на устройстве.


Переходы из задач/событий к письму Compose: только точные проверенные ссылочные
owner/folder/uid/validity. Отсутствующая folder → INBOX; пустая/контрольные знаки,
нестроковые uid/validity, явно чужой account отвергаются. NativeMailLinks связывает
событие по прежнему local:<id> / [calendarId,id], без совпадения только названия.
Выбор сверяется со всей сохранённой связью, а не с потенциально повторным id.
Вложенный временный reader не открывается. Его Back закрывает generation и возвращает
исходный InboxState; UI возвращается к задаче/календарю через SaveableStateHolder.
Отдельный reader offset живёт в памяти; исходные route/offset не переписываются.
Обычный background истекает и pager исходного списка. Process death не восстанавливает
временную связь. Файл/ответ/Seen используют прежние проверки; само чтение — BODY.PEEK.
Ошибки загрузки не переводят в другую папку и не запускают поиск похожего письма.

## Нативные аккаунты/настройки (16 октября 2026, без выпуска APK)

- Вход/выбор/выход/удаление и SMTP доступны в Compose. Выход сохраняет секрет и очередь; удаление требует подтверждения последствий, не удаляет серверные письма. Ошибка/частичная очистка не показывается как успех.
- Переключение ящика допускается после закрытия редакторов/завершения операций; создаётся свежая Activity с очисткой прежних ViewModel/маршрутов. Pending-уведомление сохраняет только проверенные ключи, требует явного выбора нужного аккаунта, не переходит в WebView.
- Пароль формы не сохраняется при смерти процесса/повороте, очищается при уходе в фон и исключён из диагностического toString. Новые секреты сохраняются только после успешного IMAP/TLS; SMTP-настройка не означает успешной проверки отправки.
- Настройки аккаунта используют существующие ключи JSON и field-CAS, сохраняя неизвестные/параллельные данные. Тема/масштаб/компактность реально применяются; отключение офлайн-доступа запрещает следующие чтения кэша, не обещает его удаление. Android-разрешения и фоновые ограничения показываются отдельно от пользовательских переключателей.

Нативные дополнительные настройки (16 октября 2026): startScreen применяется один раз при новом запуске; восстановление и пользовательские/notification-переходы приоритетнее. Выбор иконки общий, сохраняет Compose-вход и откатывает частичные component changes. Очистка кэша только текущего ящика, с подтверждением, проверкой AtomicFile sidecars и инвалидированием старых записывающих экземпляров/нативных курсоров; сервер/черновики/вложения/календари не удаляются.

Нативные Gemini-настройки (16 октября 2026): ключ общий, нейроанализ/модель по аккаунту; field-CAS сохраняет неизвестные данные. Форма не выполняет API-запросов и не показывает сохранённый секрет; ввод очищается при onPause и исключён из сохраняемого состояния/диагностики. Удаление с подтверждением, version-CAS секрета и повторная проверка аккаунта/AppLock. Выключение анализа и смена ключа/модели отменяют текущие запросы; недействительное разрешение блокирует отправку. Выбор модели ограничен идентификатором на фиксированном Google endpoint, без redirects. Работоспособность API/Keystore на устройстве не заявляется.

Сводка Compose (16 октября 2026): открытие не читает тела и не вызывает Google. Выбор до 20 загруженных писем одной папки; подготовка BODY.PEEK и отдельное явное подтверждение передачи подготовленного набора. Частичные ошибки и обрезка видимы до анализа; вложения не отправляются. Источники проверяются по точным account/folder/UID/UIDVALIDITY успешно подготовленных писем. Generation/AppLock/аккаунт/настройки/версия ключа защищают запрос и результат; onPause отменяет, автоматических повторов нет. Задержанная отмена захватывает старое соединение и не отменяет новое. Ни SMTP, ни Seen, ни задач/событий из результата автоматически не создаётся.

ИИ редактора Compose (16 октября 2026): проверка текста/предложение ответа открывают редактируемый запрос после сохранения черновика. Только явные копирование/chooser; структурированные адресаты, файлы и ключ не добавляются. Нет API-вызова, автоматической замены письма или SMTP. Перед передачей повторно проверяются аккаунт, AppLock и разрешение анализа; фон инвалидирует действие. Контекст ответа ограничен и обозначен, изменённый запрос закрывается с подтверждением.

Очередь Compose (16 октября 2026): явное планирование по Москве через существующий ScheduledMail. Durable SCHEDULING до вставки; сверка source id перед разрешением повторной отправки. Нативные файлы сохраняются до подтверждения очереди. Отмена и возврат с подтверждением; transmitting не отменяется, uncertain не повторяется автоматически. Возврат не перезаписывает другой черновик и освобождает queue snapshot только после шифрованного native commit. Время доставки зависит от Android/сети.

Серверная копия Compose (16 октября 2026): явная загрузка после локального сохранения и durable маркера; ключ UUID и существующая ревизионная дедупликация ServerDrafts. Неопределённый ACK остаётся неопределённым, без автоповтора. Удаление с подтверждением только exact owned UID/UIDVALIDITY/key/revision и UIDPLUS, без broad EXPUNGE. Локальный текст остаётся. Конфликт записи блокирует изменение/отправку до переоткрытия. Ошибка очистки после SMTP ACK не отменяет успех и не разрешает повтор письма.

Коллекция черновиков Compose (16 октября 2026): до 50 отдельных записей аккаунта, активный ID и per-record revisions в одном зашифрованном AtomicFile. Чтение v1 и атомарная миграция при записи без перезаписи прежнего черновика. Новое письмо/ответ создают новую запись; выбор через список. Переключение и автоматический выбор после удаления увеличивают ревизию. Старое окно не может изменить другой active ID, разрешить его повторную отправку или удалить его файлы. Восстановление Activity ждёт AppLock и проверяет сохранённый ID. Возврат из очереди сохраняет другие черновики. WebView/произвольные серверные черновики пока отдельно.

Копирование прежних черновиков (16 октября 2026): явное подтверждение, новый нативный UUID и отдельные копии файлов; исходный JSON/outgoing/серверные копии не изменяются. Показано предупреждение о двух отправляемых копиях. Snapshot и очередь проверяются до commit; недоступный файл/конфликт не создаёт частичный черновик. ID+fingerprint открывают уже сохранённую копию без перезаписи её правок. Старые server/scheduled identity не переходят на новый UUID. Отмена проверяет поколение, очистка после AtomicFile-сбоя не удаляет записанные ссылки. Автоматической отправки нет.

Открытие серверного черновика (16 октября 2026): явная загрузка папки DRAFT, страницы по 50 UID и редактируемая копия после подтверждения. READ_ONLY/PEEK, полный текст со строгой кодировкой без тихой обрезки, точные folder/UID/UIDVALIDITY, To/Cc/Bcc и все вложения до native commit. Существующие нативные правки не заменяются. HTML/plain conversion и сохранение исходника обозначены; серверный оригинал не переписывается/не удаляется. Пауза/отмена закрывают захваченное соединение вне UI, устаревшая ошибка не отменяет новый запрос. SMTP автоматически не запускается.

## Нативная пересылка исходных файлов

Редактор сохраняет существующую фиолетовую тему и общие Material-компоненты.
«Добавить вложения исходного письма» — отдельная подписанная кнопка с числом
файлов и app-owned подтверждением скачивания. Прогресс и отмена принадлежат
существующему блоку импорта файлов NativeComposer. Все файлы добавляются вместе,
с проверкой точной ссылки и свежего manifest; исходник не изменяется.
Повторное открытие не дублирует файлы. При ошибке текст и ручные вложения остаются.
HTML/inline и оригинал EML не заявляются перенесёнными; SMTP только явно.

## Нативная пересылка оригинала EML

«Переслать оригинал EML» создаёт отдельный пустой по адресатам черновик.
Скачивание через существующий блок импорта NativeComposer требует явного
подтверждения: целое письмо до 100 МиБ, заголовки/HTML/inline/вложения внутри,
верхние Bcc/Resent-Bcc исключены. Комментарий редактируется независимо.
Прогресс/отмена/ошибка и стиль Material используют прежние компоненты и палитру.
Без EML отправка и планирование недоступны; удаление EML снова блокирует их.
Готовность хранится в записи вложения и переживает переназначение токена очередью.
Оригинал не помечается прочитанным и не изменяется; автоматического SMTP нет.

## Нативная библиотека файлов

«Ещё → Файлы писем» использует прежние Material-поверхности, поля и фиолетовую
тему. Подписаны текущая папка, число найденных файлов и просмотренных писем;
пустая страница не означает конец при hasMore. Поиск по имени запускается явно,
очистка доступна сразу, IME-composition не отправляет запрос. Метаданные и EML
индексируются без скачивания бинарного содержимого. Прогресс и отмена принадлежат
отдельному скачиванию, ошибки сохраняют предыдущие результаты.
Открытие/передача используют общий showFile, сохранение — NativeExportModel/SAF,
переход к письму — openLinked с возвратом. Диапазон — одна текущая серверная папка,
без обещания общего офлайн-индекса. Пауза инвалидирует курсор; обновление явно.

## Импорт файла контактов через SAF

В прежнем диалоге импорта появились источники «Текст»/«Файл». Стиль и Material
компоненты сохранены, отдельного экрана/палитры нет. Смена источника сбрасывает
предпросмотр. Показаны новые записи, повторы пакета и уже существующие адреса.
Одна ошибочная строка отклоняет пакет; запись только после явного подтверждения.
Файл — UTF-8 с необязательным BOM, до 200 КиБ; vCard/CSV-кавычки не обещаются.
Имя и содержимое читаются вне UI, чтение можно остановить; пауза инвалидирует
операцию. Picker привязан к account/сессии формы и ждёт onResume/AppLock, URI
остаётся только в памяти. Сохранение — прежний CAS account-state контактов,
без перезаписи справочника. Пустой набор новых записей ничего не записывает.

## Android-контакты, выбор импорта и история получателей

В прежнем диалоге импорта — источники Текст/Файл/Android и ограниченный LazyColumn
кандидатов с выбором. Разрешение READ_CONTACTS запрашивается только кнопкой чтения;
отказ не меняет данные. Показаны дубли/некорректные записи и свободные места;
запись только выбранных новых адресов с прежним account-state CAS.
Текст/SAF сохраняют строгий UTF-8 и пакетную валидацию; Android показывает число
пропущенных невалидных записей. История — отдельная локальная книга recipientBook;
очистка с подтверждением всей области и CAS, без удаления сохранённых контактов.
Поздние callbacks привязаны к аккаунту/форме, чтение вне UI с отменой. Палитра,
Material-компоненты и нативные области нажатия сохраняются.

### Compose: общий поиск, файлы, наблюдение и звонки · 17 октября 2026

| Возможность | Канонический владелец | Область и ограничения |
|---|---|---|
| Выбор папки | NativeFolderChoices + NativeFolders | Один компонент для inbox/MOVE/files; файлы не меняют папку почты |
| Пакетный выбор файлов | NativeFilesModel + NativeExportPlan.library | Только текущие загруженные строки, до 500; сброс при обновлении/поиске/папке/фоне |
| Сохранение и повтор | NativeExportModel + BulkAttachments | Подтверждение и SAF; успехи не повторяются, ошибки повторяются только явно |
| Общий поиск | NativeInboxSearch + NativeRelatedSearchModel | Один явный запрос, независимые состояния почты/контактов/событий; никаких записей в источники |
| Live updates | MailWatch + NativeInboxModel | Отдельное отменяемое соединение; не заменяет письмо, форму, выбранные строки или открытую цепочку |
| Возраст копии | NativeCopyAge + CopyAgeText | Разные времена списка/тела; один минутный таймер только в foreground, без сети |
| Набор номера/история | NativeDialer + CallHistory + NativeCallsModel | ACTION_DIAL; хранится до 50 переданных номеров в JSON текущего аккаунта, это не журнал состоявшихся звонков ОС |

Пакетный план содержит точные folder/uid/UIDVALIDITY/MIME-path и проверяет принадлежность текущему
списку. Неизвестные/повторные/смешанные ссылки отклоняются. Закрытие picker и поздние результаты
используют прежний NativeExportPicker; перенос/скачивание не помечает почту прочитанной.
При отмене готовые документы сохраняются; для не начатых файлов нужен новый выбор. Повтор ошибок
показывает новое подтверждение и запрос папки, не перезаписывая прежние документы.

В общем поиске «Везде» означает почту выбранной папки, контакты и события/задачи. From/вложения/
непрочитанность относятся к почте; даты по Москве относятся к письмам/календарю. Без дат календарь
проверяется за текущий и следующий месяц; серверный диапазон ограничен 12 месяцами. Офлайн-кнопка
не запускает справочник или синхронизацию календаря. Сбой одной секции не уничтожает результаты
других. Новый запрос, смена аккаунта, фон/блокировка инвалидируют поздние публикации. HTTP уже
начатого справочника/CalDAV имеет собственные таймауты; отмена UI не обещает мгновенный обрыв HTTP.

Watch работает только для видимой онлайн-почты. Пока пользователь читает/выбирает/редактирует,
изменения откладываются в индикатор и не заменяют экран. При возвращении из настроек перечитываются
актуальные реквизиты, включая обновлённый пароль. Уход с маршрута/в фон закрывает watcher.

Внешний набор номера предварительно проверяет AppLock и текущий аккаунт. После успешного открытия
«Телефона» запись истории уже подтверждённого действия может завершиться в фоне под общим lock
ReplyReminderCenter; другой аккаунт не получает запись. Ошибка сохранения истории не выдаётся за
отменённый набор и отображается отдельно. Никаких CALL_PHONE/READ_CALL_LOG и автоматических звонков.

## Явная проверка Gemini-прокси

GET фиксированного HTTPS-адреса Google через сохранённый TLS CONNECT-прокси
без ключа Gemini, писем или вложений. Только отдельное нажатие, до 20 секунд,
без redirects и прямого повтора. Доступны отмена и Back; фон/AppLock/смена
аккаунта или версии пароля инвалидируют результат и отменяют захваченный Call.
Отмена старой проверки не закрывает новую. HTTP-ответ проверяет маршрут/TLS,
а не ключ, модель или доступность Gemini API. Ошибки показывают фиксированный
код этапа без сырых исключений, паролей, заголовков или содержимого запроса.
