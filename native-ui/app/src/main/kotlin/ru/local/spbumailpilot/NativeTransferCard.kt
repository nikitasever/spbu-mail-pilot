package ru.local.spbumailpilot

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Kept outside message/editor scroll, so a stalled connection always has a visible action. */
@Composable internal fun NativeTransferCard(name:String,stage:String,bytes:Long,total:Long=-1,
    cancel:(()->Unit)?=null,collapse:(()->Unit)?=null,expand:(()->Unit)?=null){
    val context=LocalContext.current
    Surface(tonalElevation=2.dp,modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
            if(name.isNotBlank())Text(name,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
            Text(NativeTransferProgress.label(stage),style=MaterialTheme.typography.bodySmall,
                modifier=Modifier.semantics {liveRegion=LiveRegionMode.Polite})
            val fraction=NativeTransferProgress.fraction(stage,bytes,total)
            if(fraction<0)LinearProgressIndicator(Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress={fraction},modifier=Modifier.fillMaxWidth())
            if(bytes>0||total>0){
                val done=android.text.format.Formatter.formatShortFileSize(context,bytes.coerceAtLeast(0))
                val size=if(total>0&&total>=bytes)" / "+android.text.format.Formatter.formatShortFileSize(context,total) else ""
                Text("Передано: $done$size",style=MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                cancel?.let {TextButton(onClick=it){Text("Отменить")}}
                collapse?.let {TextButton(onClick=it){Text("Свернуть")}}
                expand?.let {TextButton(onClick=it){Text("Открыть")}}
            }
        }
    }
}

@Composable internal fun NativeComposerTransfer(model:NativeComposerModel){
    val s=model.state
    when{
        s.importing -> NativeTransferCard(s.transferName,if(s.transferStopping)"cancelling" else "copying",s.transferBytes,s.transferTotal,
            cancel=if(s.transferStopping)null else model::cancelImport,collapse=if(s.minimized)null else model::minimize,expand=if(s.minimized)model::restoreTransfer else null)
        s.serverWorking -> NativeTransferCard("Серверный черновик",if(s.serverBytes>0)"uploading" else "connecting",s.serverBytes,s.serverTotal,
            cancel=model::cancelServerTransfer,collapse=if(s.minimized)null else model::minimize,expand=if(s.minimized)model::restoreTransfer else null)
        s.sending -> NativeTransferCard("Письмо и вложения",s.sendStage,s.sendBytes,s.sendTotal,
            collapse=if(s.minimized)null else model::minimize,expand=if(s.minimized)model::restoreTransfer else null)
        s.minimized -> Surface(tonalElevation=2.dp,modifier=Modifier.fillMaxWidth()){
            Column(Modifier.padding(12.dp)){
                Text(s.error ?: s.notice ?: if(s.busy)"Завершаем операцию с письмом…" else "Операция завершена. Откройте письмо для проверки.",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick=model::restoreTransfer){Text("Открыть письмо")}
            }
        }
    }
}
