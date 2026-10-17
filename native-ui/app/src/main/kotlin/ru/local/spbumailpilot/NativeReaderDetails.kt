package ru.local.spbumailpilot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import javax.mail.internet.InternetAddress
import java.util.Locale

internal data class ReaderAddresses(val values: List<String>, val available: Boolean, val incomplete: Boolean)
internal data class ReaderDetails(val to: ReaderAddresses, val cc: ReaderAddresses, val reply: ReaderAddresses, val fallback: ReaderAddresses) {
    companion object {
        fun from(message: JSONObject): ReaderDetails {
            val to=addresses(message.opt("toRecipients"))
            val cc=addresses(message.opt("ccRecipients"))
            var reply=addresses(message.opt("replyToRecipients"))
            if(!reply.available) reply=addresses((message.opt("replyTo") as? String)?.let {JSONArray().put(it)})
            // Old caches combine To/Cc/Bcc. Their roles cannot be reconstructed honestly.
            val fallback=if(!to.available&&!cc.available) addresses(message.opt("recipients"),true) else addresses(null)
            return ReaderDetails(to,cc,reply,fallback)
        }
        private fun addresses(raw: Any?, objects: Boolean=false): ReaderAddresses {
            if(raw !is JSONArray)return ReaderAddresses(emptyList(),false,raw!=null&&raw!==JSONObject.NULL)
            val found=linkedMapOf<String,String>();var incomplete=raw.length()>100
            for(i in 0 until minOf(raw.length(),100)) {
                val value=if(objects)(raw.opt(i) as? JSONObject)?.opt("email") else raw.opt(i)
                val email=(value as? String)?.trim()
                val valid=email!=null&&email.length in 1..998&&!email.any {Character.isISOControl(it)}&&runCatching {
                    val parsed=InternetAddress(email,true);parsed.validate();parsed.address==email
                }.getOrDefault(false)
                if(valid)found.putIfAbsent(email!!.lowercase(Locale.ROOT),email) else incomplete=true
            }
            return ReaderAddresses(found.values.toList(),true,incomplete)
        }
    }
}

@Composable internal fun NativeReaderHeader(owner: String,mail: NativeMail,message: JSONObject?) {
    var expanded by rememberSaveable(owner,mail.ref.folder,mail.ref.validity,mail.ref.uid){mutableStateOf(false)}
    val details=remember(message){message?.let(ReaderDetails::from)}
    SelectionContainer {
        Column(Modifier.fillMaxWidth()) {
            Text(mail.sender,Modifier.padding(top=12.dp),fontWeight=FontWeight.SemiBold)
            if(mail.email.isNotBlank()&&!mail.sender.trim().equals(mail.email.trim(),true))
                Text(mail.email,Modifier.padding(top=3.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            val date=mailTime(mail.date).ifBlank {mailTime(message?.opt("date") as? String ?: "")}
            Text(if(date.isBlank())"Дата не указана" else "$date · МСК",Modifier.padding(top=4.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    TextButton(onClick={expanded=!expanded},enabled=message!=null,modifier=Modifier.heightIn(min=44.dp)) {
        Text(if(expanded)"Скрыть сведения" else "Сведения о письме")
    }
    if(expanded&&details!=null)SelectionContainer {
        Column(Modifier.fillMaxWidth().padding(bottom=12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            @Composable fun addresses(label: String,value: ReaderAddresses){
                if(value.available)Text("$label: ${value.values.joinToString(", ").ifBlank {"не указаны"}}",fontSize=13.sp)
            }
            addresses("Кому",details.to)
            if(details.cc.values.isNotEmpty())addresses("Копия",details.cc)
            if(details.reply.values.any {!it.equals(mail.email,true)})addresses("Ответить на",details.reply)
            addresses("Получатели (без разделения)",details.fallback)
            if(!details.to.available||!details.cc.available)
                Text("В этой копии письма нет полного разделения адресатов на «Кому» и «Копия».",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(listOf(details.to,details.cc,details.reply,details.fallback).any {it.incomplete})
                Text("Часть данных не показана: адреса не распознаны или список превышает 100 записей.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(mail.messageId.isNotBlank())Text("Message-ID: ${mail.messageId}",fontSize=12.sp)
            if(mail.inReplyTo.isNotBlank())Text("In-Reply-To: ${mail.inReplyTo}",fontSize=12.sp)
        }
    }
}
