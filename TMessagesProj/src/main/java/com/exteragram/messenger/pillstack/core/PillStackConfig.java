package com.exteragram.messenger.pillstack.core;

import app.exteraless.pillstack.PillStackEvents;

import java.util.ArrayList;

public final class PillStackConfig {
    private PillStackConfig() {
    }

    public static ArrayList<Integer> getActivePills() {
        app.exteraless.pillstack.PillStackConfig.loadConfig(false);
        return app.exteraless.pillstack.PillStackConfig.getActivePills();
    }

    public static ArrayList<Integer> getHiddenPills() {
        app.exteraless.pillstack.PillStackConfig.loadConfig(false);
        return app.exteraless.pillstack.PillStackConfig.getHiddenPills();
    }

    public static int getLastActivePillId() {
        app.exteraless.pillstack.PillStackConfig.loadConfig(false);
        return app.exteraless.pillstack.PillStackConfig.lastActivePillId.Int();
    }

    public static boolean checkAndClearPendingUpdate(int pillId) {
        return PillStackEvents.checkAndClearPendingUpdate(pillId);
    }

    public static boolean shouldUpdatePill(Object[] args, int... pillIds) {
        if (args == null || args.length == 0 || pillIds.length == 0) {
            return true;
        }
        for (Object arg : args) {
            if (arg instanceof Integer) {
                int id = (Integer) arg;
                for (int pillId : pillIds) {
                    if (id == pillId) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static void notifySettingsChanged(int... pillIds) {
        PillStackEvents.notifySettingsChanged(pillIds);
    }
}
