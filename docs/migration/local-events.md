# Локальные события: WebView → Compose

Рабочий блок существующего утверждённого переноса, без нового выпуска.
Применён локальный Codex-адаптер code-modernization; исходный Claude Code workflow
и его агентный конвейер не запускались. Не заявляется независимый upstream PROVEN.

## Контракт и источник

| Правило | Прежний код | Нативная реализация |
|---|---|---|
| EVENT-01: обязательны название/дата; время можно очистить | ui/app.mjs:608, ui/forms/render.mjs:3 | NativeWorkspace.validate(events), NativeWorkspaceScreen |
| EVENT-02: создание берёт выбранный день; событие не создаёт задачу | ui/app.mjs:680 | NativeWorkspaceModel.openEvent/edit, CalendarScreen |
| EVENT-03: изменение обновляет eventTitle/eventDate только своих связей | ui/app.mjs:608 | NativeWorkspace.change, eventMailLinks |
| EVENT-04: удаление убирает событие/его связи; письма и чужие связи остаются | ui/app.mjs:682 | NativeWorkspace.change(events, expected, null) |
| EVENT-05: отмена/отказ сохранения не меняют сохранённые данные | ui/app.mjs:608,682 | NativeWorkspaceModel, успешный commit перед закрытием |
| EVENT-06: серверные события не редактируются | ui/calendar/item.mjs | CalendarScreen показывает edit только kind=event; загрузка exact ID из локального state.events |

Имеющаяся защита CAS усиляет старый сценарий: устаревший редактор не перезаписывает
изменённую запись. Общий SharedStateMerge сохраняет защиту от старого WebView.
Все изменения события и связей входят в один JSON commit под ReplyReminderCenter.class.

Осознанные ограничения/отличия: нативный календарь уже ограничен 1970–2100, поэтому
редактор явно показывает и проверяет этот диапазон. Есть строгая проверка реальной
даты и HH:mm; time optional. Импорт, серверные записи, multi-day/recurrence редактор
и создание/изменение связей с письмами ещё не перенесены. Существующие дополнительные
поля и точные mail refs при редактировании сохраняются.

## Доказательство в пределах блока

`native-ui/test-local-events.sh` запускает NativeWorkspaceTest (регрессия общего
хранилища), NativeLocalEventsTest и 10 сравнений. `event-equivalence.cjs` выполняет
**настоящий assets/app.js** через linkedom; часы зафиксированы на 15 октября 2026,
данные вымышлены. Java исполняет NativeWorkspace.change над теми же входами.
Сравниваются events/eventMailLinks/tasks/notes/unknown структурно, без масок:
название/дата/очистка времени/Unicode и markup/пустые поля/удаление/отмена/отказы.
Всего 10 выполнено, 10 совпало. Это сравнение состояния, не окон Android или диска.
Результаты в native-ui/app/build/event-tests/{legacy,native,result}.json.

`python native-ui/tests/event-canary.py` компилирует намеренно неверное обновление
кэшированного названия связи в **отдельном временном каталоге**. Сравнение выявляет
2 расхождения (edit-title, unicode-markup). Рабочие исходники не меняются, каталог
автоматически удаляется. Результат — event-tests/canary.json. Отдельно прошли
существующие test-calendar.cjs, test-calendar-mail-links.cjs, test-mail-event-create.cjs.

Paparazzi: два новых реальных Compose-компонента — локальный календарь и редактор
события. APK собирается. Activity/клавиатура/SharedPreferences на устройстве,
AppLock-переходы и реальные CalDAV не проверялись: устройства/эмулятора нет.
