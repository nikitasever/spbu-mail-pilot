package ru.local.spbumailpilot
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun NativeExportDialog(model:NativeExportModel){
    val state=model.state;if(!state.open)return
    AlertDialog(onDismissRequest=model::cancel,title={Text(if(state.original)"Сохранить оригинал .eml" else "Сохранить вложения · ${state.total}")},text={
        Column(Modifier.heightIn(max=380.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            if(state.phase=="confirm"){
                Text("Выберите папку. Существующие файлы не перезаписываются; одинаковые имена получат номер.")
                Text("При остановке готовые файлы останутся в папке. Временные файлы могут остаться, если провайдер запретит их удаление.",fontSize=12.sp)
            }
            if(state.phase=="transfer"){
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Обработано: ${state.done} из ${state.total}")
                if(state.name.isNotBlank())Text(state.name,fontSize=13.sp)
                if(state.size>0)Text("${state.bytes/1024} / ${state.size/1024} КиБ",fontSize=12.sp)
            }
            if(state.phase=="result")Text("Сохранено: ${state.saved} из ${state.total}. Ошибок: ${state.failures}.")
            if(state.notice.isNotBlank())Text(state.notice)
            state.failureNames.forEach {Text(it,fontSize=12.sp)}
        }
    },confirmButton={if(state.phase=="confirm")Button(onClick=model::confirm){Text("Выбрать папку")}
        else if(state.phase=="result")TextButton(onClick=model::cancel){Text("Закрыть")}},
        dismissButton={if(state.phase!="result")TextButton(onClick=model::cancel){Text("Отмена")}})
}
