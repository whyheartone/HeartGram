package app.exteraless.shortcuts;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import org.telegram.messenger.FileLog;
import org.telegram.ui.LaunchActivity;

/**
 * Прокси для старых закреплённых ярлыков лаунчера, передающий действие напрямую в LaunchActivity.
 */
public class QuickToggleActivity extends Activity {

    public static final String ACTION_GHOST_MODE = "app.exteraless.action.TOGGLE_GHOST_MODE";
    public static final String ACTION_SAFE_MODE = "app.exteraless.action.TOGGLE_SAFE_MODE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String action = getIntent() == null ? null : getIntent().getAction();
        try {
            final Intent launch = new Intent(this, LaunchActivity.class);
            if (action != null) {
                launch.setAction(action);
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(launch);
        } catch (Throwable e) {
            FileLog.e(e);
        }
        finish();
    }
}
