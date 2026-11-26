package com.exteragram.messenger.utils.network;

public final class RemoteUtils {

    public static final String USE_ADBLOCK = "use_adblock";

    private RemoteUtils() {
    }

    public static boolean getBooleanConfigValue(String key, boolean defaultValue) {
        if (USE_ADBLOCK.equals(key)) {
            return true;
        }
        return defaultValue;
    }
}
