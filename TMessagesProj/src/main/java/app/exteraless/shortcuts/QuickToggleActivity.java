package app.exteraless.shortcuts;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

import app.exteraless.plugins.PluginsController;
import tw.nekomimi.nekogram.NekoConfig;

public class QuickToggleActivity extends Activity {

    public static final String ACTION_GHOST_MODE = "app.exteraless.action.TOGGLE_GHOST_MODE";
    public static final String ACTION_SAFE_MODE = "app.exteraless.action.TOGGLE_SAFE_MODE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String action = getIntent() == null ? null : getIntent().getAction();
        CharSequence message = null;
        try {
            ApplicationLoader.postInitApplication();
            if (ACTION_GHOST_MODE.equals(action)) {
                message = toggleGhostMode();
            } else if (ACTION_SAFE_MODE.equals(action)) {
                message = toggleSafeMode();
            }
        } catch (Throwable e) {
            FileLog.e(e);
            // раньше ошибка глоталась молча, и ярлык казался «мёртвым»
            message = "Ошибка ярлыка: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " - " + e.getMessage());
        }
        if (message != null) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } else {
            FileLog.e("QuickToggleActivity: unknown action " + action);
        }
        // ярлык включает режим и сразу открывает приложение уже в нём
        try {
            android.content.Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (launch != null) {
                launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(launch);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        finish();
    }

    private CharSequence toggleGhostMode() {
        try {
            // «зайти с призраком»: включаем, а не переключаем, чтобы второй тап не выключал режим перед входом
            if (!NekoConfig.isGhostModeActive()) {
                NekoConfig.toggleGhostMode();
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return LocaleController.getString(NekoConfig.isGhostModeActive()
                ? R.string.GhostModeEnabled
                : R.string.GhostModeDisabled);
    }

    private CharSequence toggleSafeMode() {
        final PluginsController controller = PluginsController.getInstance();
        final boolean enabled = true;
        if (!controller.isSafeMode()) {
            controller.restart(true);
        }
        return LocaleController.getString(enabled
                ? R.string.PluginsSafeModeEnabledNow
                : R.string.PluginsSafeModeDisabledNow);
    }
}
