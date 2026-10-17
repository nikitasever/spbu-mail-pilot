package ru.local.spbumailpilot
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun NativeReaderActions(model: NativeInboxModel,mail: NativeMail,text: String,extraLinks: org.json.JSONArray?=model.state.body?.message?.optJSONArray("links")) {
    val owner=model.state.owner;val context=LocalContext.current;val clipboard=LocalClipboardManager.current
    val targets=remember(text,extraLinks?.toString()){NativeReaderTargets.from(text,extraLinks)}
    var expanded by remember(owner,mail.ref){mutableStateOf(false)}
    var status by remember(owner,mail.ref){mutableStateOf<String?>(null)}
    fun perform(success: String,action: ()->Unit){
        status=runCatching {if(model.readerAction(owner,mail.ref,action))success else "Письмо недоступно. Откройте его ещё раз."}
            .getOrElse {"Не удалось открыть приложение. Можно выделить и скопировать текст."}
    }
    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick={
            val shared=runCatching {NativeReaderTargets.share(mail.subject,mail.sender,text)}
            if(shared.isFailure)status=shared.exceptionOrNull()?.message
            else perform("Открыт выбор приложения."){ShareActions.text(context,shared.getOrThrow())}
        },enabled=!model.state.busy,modifier=Modifier.heightIn(min=44.dp)){Text("Поделиться текстом")}
        if(targets.links.isNotEmpty()||targets.phones.isNotEmpty())TextButton(onClick={expanded=!expanded},modifier=Modifier.heightIn(min=44.dp)) {
            Text(if(expanded)"Скрыть ссылки и номера" else "Ссылки и номера · ${targets.links.size+targets.phones.size}")
        }
        if(expanded) {
            targets.links.forEach {url->
                SelectionContainer {Text(url,fontSize=13.sp)}
                Row {
                    TextButton(onClick={perform("Открыта ссылка."){context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}}){Text("Открыть ссылку")}
                    TextButton(onClick={perform("Ссылка скопирована."){clipboard.setText(AnnotatedString(url))}}){Text("Копировать")}
                }
            }
            targets.phones.forEach {number->
                Text(number,fontSize=13.sp)
                Row {
                    TextButton(onClick={perform("Открыт набор номера."){context.startActivity(Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",PhoneNumbers.normalize(number),null)))}}){Text("Набрать номер")}
                    TextButton(onClick={perform("Номер скопирован."){clipboard.setText(AnnotatedString(number))}}){Text("Копировать")}
                }
            }
        }
        if(targets.limited)Text("Поиск ссылок и номеров ограничен первыми 200 000 знаков и 20 результатами каждого вида. Остальной текст можно выделить.",fontSize=12.sp)
        status?.let {Text(it,fontSize=13.sp)}
    }
}
