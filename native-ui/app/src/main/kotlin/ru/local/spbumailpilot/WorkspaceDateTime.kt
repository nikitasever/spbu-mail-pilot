package ru.local.spbumailpilot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun formatHumanDate(raw: String, includeYear: Boolean = true): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    return runCatching {
        val date = NativeAgenda.date(trimmed)
        val pattern = if (includeYear) "d MMM yyyy" else "d MMM"
        DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag("ru")).format(date)
    }.getOrDefault(trimmed)
}

internal fun formatHumanDateTime(dateRaw: String, timeRaw: String, includeYear: Boolean = false): String {
    val d = formatHumanDate(dateRaw, includeYear)
    val t = timeRaw.trim()
    return when {
        d.isNotEmpty() && t.isNotEmpty() -> "$d · $t"
        d.isNotEmpty() -> d
        t.isNotEmpty() -> t
        else -> ""
    }
}

/** Picker dates are UTC calendar labels, never device-zone instants. Times represent Moscow wall time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkspaceDateTime(
    id: String,
    day: String,
    time: String,
    enabled: Boolean,
    allowEmpty: Boolean,
    field: (String, String) -> Unit,
    allowEmptyTime: Boolean = true
) {
    var dateOpen by remember(id) { mutableStateOf(false) }
    var timeOpen by remember(id) { mutableStateOf(false) }
    val validDay = remember(day) { runCatching { NativeAgenda.date(day) }.isSuccess }
    LaunchedEffect(enabled) {
        if (!enabled) {
            dateOpen = false
            timeOpen = false
        }
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            onClick = { if (enabled) dateOpen = true },
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1C1F2A),
            modifier = Modifier.weight(1.15f)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Дата",
                        fontSize = 11.sp,
                        color = Color(0xFF8E929F)
                    )
                    Text(
                        formatHumanDate(day, includeYear = true).ifBlank { "Выбрать дату" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (day.isBlank()) Color(0xFF8E929F) else Color(0xFFF3F4F6),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Icon(
                    NativeIcons.Calendar,
                    contentDescription = "Выбрать дату",
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFFA78BFA)
                )
            }
        }
        Surface(
            onClick = { if (enabled && validDay) timeOpen = true },
            enabled = enabled && validDay,
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1C1F2A),
            modifier = Modifier.weight(0.85f)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Время (МСК)",
                        fontSize = 11.sp,
                        color = Color(0xFF8E929F)
                    )
                    Text(
                        time.ifBlank { if (allowEmptyTime) "Весь день" else "Выбрать" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (time.isBlank()) Color(0xFF8E929F) else Color(0xFFF3F4F6),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Icon(
                    NativeIcons.Clock,
                    contentDescription = "Выбрать время",
                    modifier = Modifier.size(16.dp),
                    tint = if (validDay) Color(0xFFA78BFA) else Color(0xFF5A5F72)
                )
            }
        }
    }
    if ((allowEmpty && day.isNotEmpty()) || (allowEmptyTime && time.isNotEmpty())) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (allowEmpty && day.isNotEmpty()) {
                TextButton(
                    onClick = { field("time", ""); field("date", "") },
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.heightIn(min = 30.dp)
                ) {
                    Text("Убрать срок", fontSize = 12.sp, color = Color(0xFF8E929F))
                }
            }
            if (allowEmptyTime && time.isNotEmpty()) {
                TextButton(
                    onClick = { field("time", "") },
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.heightIn(min = 30.dp)
                ) {
                    Text("Без времени", fontSize = 12.sp, color = Color(0xFF8E929F))
                }
            }
        }
    }
    if (dateOpen && enabled) {
        val date = runCatching { NativeAgenda.date(day) }.getOrDefault(LocalDate.now(ZoneId.of("Europe/Moscow")))
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = 1970..2100
        )
        DatePickerDialog(
            onDismissRequest = { dateOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let {
                            field("date", Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                        }
                        dateOpen = false
                    },
                    enabled = state.selectedDateMillis != null
                ) { Text("Выбрать") }
            },
            dismissButton = { TextButton(onClick = { dateOpen = false }) { Text("Отмена") } }
        ) { DatePicker(state) }
    }
    if (timeOpen && enabled) {
        val timeValue = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.of(9, 0))
        val state = rememberTimePickerState(initialHour = timeValue.hour, initialMinute = timeValue.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { timeOpen = false },
            title = { Text("Время Москвы") },
            text = { TimeInput(state) },
            confirmButton = {
                TextButton(onClick = {
                    field("time", String.format(Locale.ROOT, "%02d:%02d", state.hour, state.minute))
                    timeOpen = false
                }) { Text("Выбрать") }
            },
            dismissButton = { TextButton(onClick = { timeOpen = false }) { Text("Отмена") } }
        )
    }
}
