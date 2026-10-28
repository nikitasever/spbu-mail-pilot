package ru.local.spbumailpilot;

/** Fixed user-facing failures; provider/server messages can contain credentials or private paths. */
final class NativeTransferFailure {
    static String describe(Throwable error){
        java.util.Set<Throwable> visited=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable,Boolean>());
        for(Throwable current=error;current!=null&&visited.size()<20&&visited.add(current);){
            if(current instanceof javax.mail.AuthenticationFailedException)return "Сервер отклонил вход. Проверьте аккаунт и сохранённый пароль, затем повторите загрузку.";
            if(current instanceof java.net.SocketTimeoutException)return "Сервер долго не отвечает. Проверьте подключение и повторите загрузку вложения.";
            if(current instanceof java.io.EOFException)return "Загрузка оборвалась. Неполный файл не открыт; повторите загрузку.";
            if(current instanceof SecurityException)return "Доступ к файлу не разрешён. Выберите его повторно в системном окне «Файлы».";
            String message=current.getMessage();
            if("Attachment too large".equals(message)||"Attachment transfer too large".equals(message))return "Вложение превышает допустимый размер: 100 МБ.";
            if("UID validity changed".equals(message)||"Message unavailable".equals(message))return "Исходное письмо изменилось или удалено. Обновите письмо и выберите вложение заново.";
            if(message!=null&&message.contains("No space left on device"))return "На телефоне недостаточно свободного места для вложения.";
            Throwable cause=current.getCause();
            if(cause==null&&current instanceof javax.mail.MessagingException)cause=((javax.mail.MessagingException)current).getNextException();
            current=cause;
        }
        return "Не удалось передать вложение. Проверьте сеть, наличие файла и свободное место; затем повторите.";
    }
}
