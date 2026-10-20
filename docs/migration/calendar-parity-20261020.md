# Календарь Compose: паритет с проектным интерфейсом

Основа сравнения: ui/calendar/render.mjs и даты/границы календаря прежнего WebView.
Визуальная оболочка Gemini сохранена; источники, редактор и ссылки используют существующие модели.

| Возможность | Владелец |
| --- | --- |
| День, неделя с понедельника, месяц, 14 дней повестки | NativeAgenda.first/end/days/shift/months |
| Сетка, шторка дня по нажатию, день/повестка и поиск по периоду | NativeCalendarScreen / NativeCalendarDaySheet |
| Today/выбор даты, даты без смещения часового пояса | NativeCalendarModel.select/today; Material DatePicker, UTC-датировка |
| Новая задача на выбранную дату и возврат | NativeWorkspaceModel.openCalendarNewTask, прежний calendar-return route |
| Сохранение/отмена и уведомления задач | Существующий NativeWorkspace/CAS/NoticeCenter; до Save записи нет |
| Отключённые задачи | NativeAgenda.combine, настройка showTasks; создание блокируется и моделью |
| Явное подключение и обновление источников | NativeCalendarModel, CalendarBook; refresh не добавляет источники |
| Связи с письмами, свои/серверные события | Существующие NativeMailLinks, NativeCalendarCard, редактор |

Проверки: NativeAgendaTest, NativeCalendarLinksTest, NativePreviewTest.calendar*,
включая calendarTaskDraftUsesSelectedDateAndReturnsWithoutSaving.
Host-превью не подтверждают работу живого сервера или поведение на физическом телефоне.
Server events остаются read-only: серверное редактирование не было возможностью прежнего проекта.
