package ru.local.spbumailpilot
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    if (targets.links.isEmpty() && targets.phones.isEmpty() && status == null) return
    Column(Modifier.fillMaxWidth()) {
        if(targets.links.isNotEmpty()||targets.phones.isNotEmpty()) {
            Surface(
                onClick={expanded=!expanded},
                shape=RoundedCornerShape(10.dp),
                color=Color(0xFF1C1F2A),
                modifier=Modifier.padding(top=6.dp)
            ) {
                Text(
                    if(expanded)"Скрыть ссылки и номера" else "Ссылки и номера · ${targets.links.size+targets.phones.size}",
                    Modifier.padding(horizontal=12.dp,vertical=6.dp),
                    fontSize=12.sp,
                    color=Color(0xFF8E929F)
                )
            }
        }
        if(expanded&&(targets.links.isNotEmpty()||targets.phones.isNotEmpty())) {
            Surface(
                shape=RoundedCornerShape(12.dp),
                color=Color(0xFF14161D),
                border=BorderStroke(1.dp,Color(0x14FFFFFF)),
                modifier=Modifier.fillMaxWidth().padding(top=8.dp)
            ) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    targets.links.forEach {url->
                        Column {
                            SelectionContainer {Text(url,fontSize=13.sp)}
                            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick={perform("Открыта ссылка."){context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}}){Text("Открыть ссылку",fontSize=12.sp)}
                                TextButton(onClick={perform("Ссылка скопирована."){clipboard.setText(AnnotatedString(url))}}){Text("Копировать",fontSize=12.sp)}
                            }
                        }
                    }
                    targets.phones.forEach {number->
                        Column {
                            Text(number,fontSize=13.sp)
                            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick={perform("Открыт набор номера."){context.startActivity(Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",PhoneNumbers.normalize(number),null)))}}){Text("Набрать номер",fontSize=12.sp)}
                                TextButton(onClick={perform("Номер скопирован."){clipboard.setText(AnnotatedString(number))}}){Text("Копировать",fontSize=12.sp)}
                            }
                        }
                    }
                }
            }
        }
        if(targets.limited)Text("Поиск ссылок и номеров ограничен первыми 200 000 знаков и 20 результатами каждого вида. Остальной текст можно выделить.",Modifier.padding(top=4.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        status?.let {Text(it,Modifier.padding(top=4.dp),fontSize=13.sp)}
    }
}
