package ru.local.spbumailpilot;

/** Native surfaces receive unlock completion without a dependency on optional UI toolkits. */
public interface AppLockAware {
    void appLockChanged();
    default String getNativeAppearanceMode(){return "system";}
    default boolean secureSurface(){return true;}
}
