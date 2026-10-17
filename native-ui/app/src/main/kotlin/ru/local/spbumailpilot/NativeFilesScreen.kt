@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ru.local.spbumailpilot
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable internal fun NativeFilesScreen(model:NativeFilesModel,openMail:(String)->Unit,save:(String)->Unit,saveSelected:()->Unit = {}){
    val s=model.state;val context=LocalContext.current
    var input by remember(s.owner,s.folder,s.query){mutableStateOf(TextFieldValue(s.query))}
    BackHandler {if(s.foldersOpen)model.closeFolders() else if(s.selected.isNotEmpty())model.clearSelection() else model.back()}
    Scaffold(topBar={TopAppBar(title={Text("Файлы писем")},navigationIcon={TextButton(onClick=model::back){Text("Назад")}},actions={TextButton(onClick={model.load()},enabled=!s.busy){Text("Обновить")}})}){padding->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp)){
            TextButton(onClick=model::showFolders,enabled=!s.busy,modifier=Modifier.heightIn(min=44.dp)){
                Text("Папка файлов: ${NativeFolders.label(s.folder)} ⌄",style=MaterialTheme.typography.bodyMedium)
            }
            Text("Поиск по имени файла. Просматриваются страницы по 50 писем; содержимое файлов скачивается только по вашему действию.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(value=input,onValueChange={if(it.text.length<=200)input=it},label={Text("Имя файла")},singleLine=true,modifier=Modifier.fillMaxWidth(),
                trailingIcon={if(input.text.isNotEmpty())TextButton(onClick={input=TextFieldValue("");model.search("")}){Text("Очистить")}},
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={if(input.composition==null)model.search(input.text)}))
            Row(Modifier.fillMaxWidth()){
                TextButton(onClick={if(input.composition==null)model.search(input.text)},enabled=!s.busy){Text("Найти")}
                if(s.busy)TextButton(onClick=model::cancel){Text("Остановить")}
            }
            Text("Найдено файлов: ${s.rows.size} · Проверено писем: ${s.next} из ${s.total}",style=MaterialTheme.typography.bodySmall)
            if(s.query.isNotEmpty())Text("Поиск: ${s.query}",style=MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick=model::selectLoaded,enabled=!s.busy&&s.rows.isNotEmpty()){Text("Выбрать загруженные")}
                TextButton(onClick=model::clearSelection,enabled=s.selected.isNotEmpty()){Text("Снять выбор")}
            }
            if(s.selected.isNotEmpty())Button(onClick=saveSelected,enabled=!s.busy,modifier=Modifier.fillMaxWidth()){
                Text("Сохранить выбранные · ${s.selected.size}")
            }
            if(s.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(s.downloading)Text("Скачано: ${android.text.format.Formatter.formatShortFileSize(context,s.bytes)}",style=MaterialTheme.typography.bodySmall)
            s.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            if(s.expired&&!s.busy)Text("Для продолжения проверки папки обновите список.",style=MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){
                if(s.rows.isEmpty()&&!s.busy&&!s.expired&&s.error==null)item {Text(if(s.more)"На проверенных страницах файлов не найдено. Проверьте следующую страницу." else "Файлов с таким именем в проверенной папке нет.")}
                items(s.rows,key={NativeFilePage.key(it)}){entry->
                    val key=NativeFilePage.key(entry);val message=entry.getJSONObject("message")
                    Surface(tonalElevation=2.dp,shape=MaterialTheme.shapes.large,modifier=Modifier.fillMaxWidth()){
                        Column(Modifier.padding(12.dp)){
                            Row(Modifier.fillMaxWidth().heightIn(min=44.dp).toggleable(value=key in s.selected,
                                enabled=!s.busy,role=Role.Checkbox,onValueChange={model.toggleSelection(key)}),
                                verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                                Checkbox(checked=key in s.selected,onCheckedChange=null,enabled=!s.busy)
                                Text(entry.getString("name"),style=MaterialTheme.typography.titleSmall,modifier=Modifier.weight(1f))
                            }
                            Text(message.optString("subject").ifBlank {"Без темы"},style=MaterialTheme.typography.bodyMedium)
                            Text(message.optString("sender").ifBlank {message.optString("email")},style=MaterialTheme.typography.bodySmall)
                            val size=entry.getLong("size");if(size>=0)Text("Размер по данным сервера: ${android.text.format.Formatter.formatShortFileSize(context,size)}",style=MaterialTheme.typography.bodySmall)
                            Row {TextButton(onClick={model.download(key)},enabled=!s.busy){Text("Открыть")};TextButton(onClick={save(key)},enabled=!s.busy){Text("Сохранить")}}
                            Row {TextButton(onClick={model.download(key,true)},enabled=!s.busy){Text("Поделиться")};TextButton(onClick={openMail(key)},enabled=!s.busy){Text("К письму")}}
                        }
                    }
                }
                if(s.more)item {OutlinedButton(onClick={model.load(true)},enabled=!s.busy&&!s.expired,modifier=Modifier.fillMaxWidth()){Text("Проверить следующие 50 писем")}}
                item {Spacer(Modifier.height(24.dp))}
            }
        }
    }
    if(s.foldersOpen)ModalBottomSheet(onDismissRequest=model::closeFolders,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)){
        NativeFolderChoices("Папка файлов",s.folder,s.folders,s.foldersBusy,s.foldersOnline,s.foldersError,
            model::closeFolders,{model.loadFolders(true)},model::chooseFolder)
    }
}
