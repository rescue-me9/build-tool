package com.vdl.kong520;

public final class ShortcutsModule {
    private ShortcutsModule() {}

    public static native void setShortcutEnabled(int featureId, boolean enabled);
    public static native boolean isShortcutEnabled(int featureId);
    public static native void setShortcutIntParam(int featureId, int index, int value);
    public static native void setShortcutFloatParam(int featureId, int index, float value);
    public static native void setShortcutStringParam(int featureId, String value);
    public static native int getShortcutIntParam(int featureId, int index);
    public static native float getShortcutFloatParam(int featureId, int index);
    public static native String getShortcutStringParam(int featureId);
    public static native String getShortcutPlayerInfo();
    public static native boolean setShortcutHome();
    public static native boolean setShortcutSafeZone();
    public static native String getShortcutStatus(int featureId);
}
