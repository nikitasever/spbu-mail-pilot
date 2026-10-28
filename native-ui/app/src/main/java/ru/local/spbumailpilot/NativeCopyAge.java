package ru.local.spbumailpilot;
/** Formatting only; the caller owns a foreground-only clock and never refreshes mail for this label. */
final class NativeCopyAge {
    static String label(long at,long now){
        if(at<=0)return "Время копии неизвестно";
        long minutes=now<=at?0:(now-at)/60000;
        if(minutes==0)return "Копия: только что";
        if(minutes<60)return "Копия: "+minutes+" мин. назад";
        if(minutes<1440)return "Копия: "+minutes/60+" ч. назад";
        return "Копия: "+minutes/1440+" дн. назад";
    }
}
