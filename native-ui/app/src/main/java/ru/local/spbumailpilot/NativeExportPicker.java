package ru.local.spbumailpilot;
/** Main-thread result ownership survives cancellation of the caller's plan until picker completion. */
final class NativeExportPicker {
    private NativeExportPlan launched;
    boolean pending(){return launched!=null;}
    boolean launch(NativeExportPlan plan){if(launched!=null||plan==null)return false;launched=plan;return true;}
    NativeExportPlan result(){NativeExportPlan value=launched;launched=null;return value;}
}
