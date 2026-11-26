package com.exteragram.messenger.pillstack.core;

import android.content.Context;

import androidx.annotation.Keep;

import com.exteragram.messenger.pillstack.ui.pills.BasePill;

import org.telegram.ui.ActionBar.Theme;

public final class PillRegistry {
    private PillRegistry() {
    }

    public interface PillCreator {
        BasePill create(Context context, Theme.ResourcesProvider resourcesProvider);
    }

    public record PillInfo(int id, CharSequence name, int iconRes, int iconColorTop,
                           int iconColorBottom, PillCreator creator) {
    }

    @Keep
    public static void beginTransaction() {
        app.exteraless.pillstack.PillRegistry.beginTransaction();
    }

    @Keep
    public static void endTransaction() {
        app.exteraless.pillstack.PillRegistry.endTransaction();
    }

    public static boolean isRegistered(int id) {
        return app.exteraless.pillstack.PillRegistry.isRegistered(id);
    }

    public static void register(PillInfo info) {
        if (info == null || info.creator == null) {
            return;
        }
        app.exteraless.pillstack.PillRegistry.register(new app.exteraless.pillstack.PillRegistry.PillInfo(
                info.id, info.name, info.iconRes, info.iconColorTop, info.iconColorBottom,
                (ctx, res) -> info.creator.create(ctx, res)
        ));
    }

    @Keep
    public static void unregister(int id) {
        app.exteraless.pillstack.PillRegistry.unregister(id);
    }

    @Keep
    public static void activatePill(int id) {
        app.exteraless.pillstack.PillRegistry.activatePill(id);
    }

    public static PillInfo getPillInfo(int id) {
        app.exteraless.pillstack.PillRegistry.PillInfo info = app.exteraless.pillstack.PillRegistry.getPillInfo(id);
        if (info == null) {
            return null;
        }
        return new PillInfo(info.id, info.name, info.iconRes, info.iconColorTop, info.iconColorBottom,
                (ctx, res) -> {
                    app.exteraless.pillstack.pills.BasePill pill = info.creator.create(ctx, res);
                    if (pill instanceof BasePill) {
                        return (BasePill) pill;
                    }
                    return pill == null ? null : new WrappedPill(pill, res);
                });
    }

    private static final class WrappedPill extends BasePill {
        private final app.exteraless.pillstack.pills.BasePill delegate;

        private WrappedPill(app.exteraless.pillstack.pills.BasePill delegate,
                            Theme.ResourcesProvider resourcesProvider) {
            super(delegate.getContext(), resourcesProvider);
            this.delegate = delegate;
            addView(delegate);
        }

        @Override
        public int getPillId() { return delegate.getPillId(); }

        @Override
        public long getRefreshInterval() { return 0; }

        @Override
        public void onUpdateData(boolean force) { delegate.onUpdateData(force); }

        @Override
        public void onPillClicked() { delegate.onPillClicked(); }

        @Override
        public boolean onPillLongClicked() { return delegate.onPillLongClicked(); }

        @Override
        public void updateColors() { delegate.updateColors(); }

        @Override
        public void onPillSelected() { delegate.onPillSelected(); }

        @Override
        public void onPillUnselected() { delegate.onPillUnselected(); }

        @Override
        public void onStackVisibilityChanged(boolean visible) {
            super.onStackVisibilityChanged(visible);
            delegate.onStackVisibilityChanged(visible);
        }
    }
}
