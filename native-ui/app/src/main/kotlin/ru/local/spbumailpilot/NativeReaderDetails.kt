package ru.local.spbumailpilot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import javax.mail.internet.InternetAddress
import java.util.Locale

internal data class ReaderAddresses(val values: List<String>, val available: Boolean, val incomplete: Boolean)
internal data class ReaderDetails(
    val to: ReaderAddresses,
    val cc: ReaderAddresses,
    val reply: ReaderAddresses,
    val fallback: ReaderAddresses,
    val uid: String = "",
    val rawFrom: String = "",
    val references: String = "",
    val rawHeaders: String = "",
    val incomplete: Boolean = listOf(to, cc, reply, fallback).any { it.incomplete }
) {
    companion object {
        fun from(message: JSONObject, defaultUid: String = ""): ReaderDetails {
            val to=addresses(message.opt("toRecipients"))
            val cc=addresses(message.opt("ccRecipients"))
            var reply=addresses(message.opt("replyToRecipients"))
            if(!reply.available) reply=addresses((message.opt("replyTo") as? String)?.let {JSONArray().put(it)})
            // Old caches combine To/Cc/Bcc. Their roles cannot be reconstructed honestly.
            val fallback=if(!to.available&&!cc.available) addresses(message.opt("recipients"),true) else addresses(null)
            val uid=cleanHeader(message.optString("uid",defaultUid),128)
            val rawFrom=cleanHeader(message.optString("rawFrom"),2000)
            val references=cleanHeader(message.optString("references"),4000)
            val rawHeaders=cleanRawHeaders(message.optString("rawHeaders"))
            val incomplete=listOf(to,cc,reply,fallback).any {it.incomplete}
            return ReaderDetails(to,cc,reply,fallback,uid,rawFrom,references,rawHeaders,incomplete)
        }
        private fun cleanHeader(raw: String?, maxLen: Int): String {
            if(raw.isNullOrEmpty())return ""
            val sb=StringBuilder(minOf(raw.length,maxLen))
            for(c in raw){
                if(sb.length>=maxLen)break
                if(!Character.isISOControl(c)&&Character.getType(c)!=Character.FORMAT.toInt())sb.append(c)
                else if((c=='\r'||c=='\n'||c=='\t')&&(sb.isEmpty()||sb.last()!=' '))sb.append(' ')
            }
            return sb.toString().trim()
        }
        private fun cleanRawHeaders(raw: String?): String {
            if(raw.isNullOrEmpty())return ""
            val sb=StringBuilder(minOf(raw.length,16500))
            var i=0
            while(i<raw.length&&sb.length<16500){
                val c=raw[i]
                if(c=='\r'){
                    if(i+1<raw.length&&raw[i+1]=='\n')i++
                    sb.append('\n')
                }else if(c=='\n'||c=='\t'||(!Character.isISOControl(c)&&Character.getType(c)!=Character.FORMAT.toInt())){
                    sb.append(c)
                }
                i++
            }
            return sb.toString().trim()
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
internal typealias NativeReaderDetailsData = ReaderDetails

private fun readerAvatarLetters(sender: String): String {
    val words=sender.substringBefore('@').replace(Regex("[._-]+")," ").trim().split(Regex("\\s+")).filter {it.isNotEmpty()}
    return words.take(2).joinToString("") {String(Character.toChars(it.codePointAt(0)))}.uppercase(Locale.forLanguageTag("ru")).ifBlank {"?"}
}

@Composable internal fun NativeReaderHeader(owner: String,mail: NativeMail,message: JSONObject?) {
    var expanded by rememberSaveable(owner,mail.ref.folder,mail.ref.validity,mail.ref.uid){mutableStateOf(false)}
    var rawHeadersExpanded by rememberSaveable(owner,mail.ref.folder,mail.ref.validity,mail.ref.uid){mutableStateOf(false)}
    val details=remember(message,mail.ref.uid){message?.let {ReaderDetails.from(it,mail.ref.uid)}}
    val palette = NativeIcons.avatarPalette(mail.email.ifBlank { mail.sender })
    Row(
        Modifier.fillMaxWidth().padding(top=10.dp,bottom=8.dp),
        horizontalArrangement=Arrangement.spacedBy(12.dp),
        verticalAlignment=Alignment.CenterVertically
    ) {
        Surface(
            shape=CircleShape,
            color=palette.container,
            modifier=Modifier.size(42.dp).clearAndSetSemantics {}
        ) {
            Box(contentAlignment=Alignment.Center) {
                Text(
                    readerAvatarLetters(mail.sender),
                    fontSize=14.sp,
                    fontWeight=FontWeight.Bold,
                    color=palette.content
                )
            }
        }
        Box(Modifier.weight(1f)) {
            SelectionContainer {
                Column(Modifier.fillMaxWidth()) {
                    Text(mail.sender,fontWeight=FontWeight.SemiBold,fontSize=15.sp,color=MaterialTheme.colorScheme.onSurface)
                    val date=mailTime(mail.date).ifBlank {mailTime(message?.opt("date") as? String ?: "")}
                    val subline = buildString {
                        if(mail.email.isNotBlank()&&!mail.sender.trim().equals(mail.email.trim(),true)) append(mail.email)
                        if(date.isNotBlank()) {
                            if(isNotEmpty()) append(" · ")
                            append("$date МСК")
                        }
                    }
                    Text(subline.ifBlank { "Дата не указана" },Modifier.padding(top=2.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
                }
            }
        }
        Surface(
            onClick={if(message!=null)expanded=!expanded},
            shape=RoundedCornerShape(10.dp),
            color=MaterialTheme.colorScheme.surface,
            border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)
        ) {
            Text(
                if(expanded)"Скрыть ▴" else "Сведения ▾",
                Modifier.padding(horizontal=10.dp,vertical=6.dp),
                fontSize=12.sp,
                fontWeight=FontWeight.Medium,
                color=MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if(expanded&&details!=null) {
        Surface(
            shape=RoundedCornerShape(12.dp),
            color=MaterialTheme.colorScheme.surface,
            border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),
            modifier=Modifier.fillMaxWidth().padding(bottom=10.dp)
        ) {
            SelectionContainer {
                Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    @Composable fun addresses(label: String,value: ReaderAddresses){
                        if(value.available)Text("$label: ${value.values.joinToString(", ").ifBlank {"не указаны"}}",fontSize=13.sp)
                    }
                    val shownFrom=listOf(mail.sender.trim(),mail.email.trim()).filter {it.isNotEmpty()}.joinToString(" ")
                    if(details.rawFrom.isNotBlank()&&!details.rawFrom.equals(mail.email.trim(),true)&&!details.rawFrom.equals(mail.sender.trim(),true)&&!details.rawFrom.equals(shownFrom,true))
                        Text("От (исходный заголовок): ${details.rawFrom}",fontSize=12.sp)
                    addresses("Кому",details.to)
                    if(details.cc.values.isNotEmpty())addresses("Копия",details.cc)
                    if(details.reply.values.any {!it.equals(mail.email,true)})addresses("Ответить на",details.reply)
                    addresses("Получатели (без разделения)",details.fallback)
                    if(!details.to.available||!details.cc.available)
                        Text("В этой копии письма нет полного разделения адресатов на «Кому» и «Копия».",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(details.incomplete)
                        Text("Часть данных не показана: адреса не распознаны или список превышает 100 записей. Список адресов в заголовке неполный или содержит неразобранную запись.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    val uid=details.uid.ifBlank {mail.ref.uid}
                    if(uid.isNotBlank())Text("UID: $uid",fontSize=12.sp)
                    if(mail.messageId.isNotBlank())Text("Message-ID: ${mail.messageId}",fontSize=12.sp)
                    if(mail.inReplyTo.isNotBlank())Text("In-Reply-To: ${mail.inReplyTo}",fontSize=12.sp)
                    if(details.references.isNotBlank())Text("References: ${details.references}",fontSize=12.sp)
                    if(details.rawHeaders.isNotBlank()){
                        TextButton(onClick={rawHeadersExpanded=!rawHeadersExpanded},modifier=Modifier.heightIn(min=40.dp)) {
                            Text(if(rawHeadersExpanded)"Скрыть исходные заголовки письма" else "Исходные заголовки письма",fontSize=12.sp)
                        }
                        if(rawHeadersExpanded)Text(details.rawHeaders,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
