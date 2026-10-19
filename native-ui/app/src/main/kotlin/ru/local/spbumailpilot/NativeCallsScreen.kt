package ru.local.spbumailpilot
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun NativeCallsScreen(model:NativeCallsModel){
    val s=model.state;val context=LocalContext.current
    BackHandler(onBack=model::back)
    Scaffold(topBar={TopAppBar(title={Text("Звонки и история")},navigationIcon={TextButton(onClick=model::back){Text("Назад")}})}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            item {Text("Открывает набор номера в приложении «Телефон». Сам звонок подтверждается там. Здесь хранятся последние 50 переданных номеров, а не системная история состоявшихся звонков.")}
            item {OutlinedTextField(s.number,model::number,label={Text("Номер телефона")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone),modifier=Modifier.fillMaxWidth())}
            item {Button(onClick={model.dial(context,s.number)},enabled=!s.busy&&s.number.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Набрать номер")}}
            if(s.busy)item {LinearProgressIndicator(Modifier.fillMaxWidth())}
            s.error?.let {item {Text(it,color=MaterialTheme.colorScheme.error)}}
            s.notice?.let {item {Text(it)}}
            item {Text("История приложения",style=MaterialTheme.typography.titleMedium)}
            if(s.rows.isEmpty()&&!s.busy)item {Text("Номера ещё не передавались из приложения.")}
            itemsIndexed(s.rows){_,row->
                ListItem(headlineContent={Text(row.getString("number"))},supportingContent={Text(mailTime(row.getString("at")))},
                    trailingContent={TextButton(onClick={model.dial(context,row.getString("number"))},enabled=!s.busy){Text("Набрать")}})
            }
            item {TextButton(onClick=model::load,enabled=!s.busy){Text("Обновить историю")}}
            item {Spacer(Modifier.height(24.dp))}
        }
    }
}
