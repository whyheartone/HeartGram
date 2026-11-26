package com.exteragram.messenger.backup;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

import java.io.Serializable;

public final class PreferencesUtils {

    private PreferencesUtils() {
    }

    public static SharedPreferences getPreferences(String name) {
        return ApplicationLoader.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    public static class BackupItem implements Serializable {

        public Class<?> clazz;
        public String key;

        public BackupItem(String key, Class<?> clazz) {
            this.key = key;
            this.clazz = clazz;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            return key.equals(((BackupItem) other).key);
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }
}
