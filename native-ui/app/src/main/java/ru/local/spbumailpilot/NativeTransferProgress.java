package ru.local.spbumailpilot;

/** A full byte counter is not a server acknowledgement. Unknown totals stay indeterminate. */
final class NativeTransferProgress {
    static float fraction(String stage,long bytes,long total){
        if(total<=0||bytes<0||bytes>total||!("downloading".equals(stage)||"uploading".equals(stage)||"copying".equals(stage)))return -1f;
        return Math.min(0.99f,(float)((double)bytes/total));
    }
    static String label(String stage){
        switch(stage){
            case "connecting":return "Подключение к серверу…";
            case "receiving":return "Получение вложения с сервера…";
            case "downloading":return "Скачивание вложения…";
            case "copying":return "Добавление файла в черновик…";
            case "preparing":return "Подготовка письма и вложений…";
            case "uploading":return "Отправка письма…";
            case "confirming":return "Ожидаем подтверждения сервера…";
            case "sent-copy":return "Письмо принято сервером. Сохраняем копию…";
            case "cancelling":return "Останавливаем операцию…";
            default:return "Подготовка передачи…";
        }
    }
}
