package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;

public class LauncherIconController {
    public static void tryFixLauncherIconIfNeeded() {
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (isEnabled(icon)) {
                return;
            }
        }

        setIcon(LauncherIcon.EXTERALESS);
    }

    public static boolean isEnabled(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        int i = ctx.getPackageManager().getComponentEnabledSetting(icon.getComponentName(ctx));
        // Пока пользователь ничего не выбирал, включённой считается наша иконка:
        // именно она стоит у <application> в манифесте, и переключатель должен
        // показывать выбранным то, что человек видит на рабочем столе.
        return i == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                || i == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == LauncherIcon.EXTERALESS;
    }

    public static void setIcon(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        PackageManager pm = ctx.getPackageManager();
        for (LauncherIcon i : LauncherIcon.values()) {
            pm.setComponentEnabledSetting(i.getComponentName(ctx), i == icon ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }

    public enum LauncherIcon {
        EXTERALESS("ExteralessIcon", R.drawable.exteraless_icon_art,
                R.drawable.exteraless_icon_foreground, R.string.AppIconExteraless),
        MONO("MonoIcon", R.drawable.mono_icon_background,
                R.drawable.app_icon_empty_foreground, R.string.AppIconMono),
        NOTHING("NothingIcon", R.drawable.nothing_icon_background,
                R.drawable.app_icon_empty_foreground, R.string.AppIconNothing),
        HG_MIDNIGHT("HgMidnightIcon", R.drawable.hg_icon_midnight_bg,
                R.drawable.hg_icon_midnight_fg, R.string.HGIconMidnight),
        HG_SNOW("HgSnowIcon", R.drawable.hg_icon_snow_bg,
                R.drawable.hg_icon_snow_fg, R.string.HGIconSnow),
        HG_LAVENDER("HgLavenderIcon", R.drawable.hg_icon_lavender_bg,
                R.drawable.hg_icon_lavender_fg, R.string.HGIconLavender),
        HG_OCEAN("HgOceanIcon", R.drawable.hg_icon_ocean_bg,
                R.drawable.hg_icon_ocean_fg, R.string.HGIconOcean),
        HG_MINT("HgMintIcon", R.drawable.hg_icon_mint_bg,
                R.drawable.hg_icon_mint_fg, R.string.HGIconMint),
        HG_SUNSET("HgSunsetIcon", R.drawable.hg_icon_sunset_bg,
                R.drawable.hg_icon_sunset_fg, R.string.HGIconSunset),
        HG_MONET("HgMonetIcon", R.drawable.hg_icon_monet_bg,
                R.drawable.hg_icon_monet_fg, R.string.HGIconMonet),
        TELEGRAM("TelegramIcon", R.drawable.icon_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconTelegramOriginal),
        VINTAGE("VintageIcon", R.drawable.icon_6_background_sa, R.mipmap.icon_6_foreground_sa, R.string.AppIconVintage),
        AQUA("AquaIcon", R.drawable.icon_4_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconAqua),
        PREMIUM("PremiumIcon", R.drawable.icon_3_background_sa, R.mipmap.icon_3_foreground_sa, R.string.AppIconPremium),
        TURBO("TurboIcon", R.drawable.icon_5_background_sa, R.mipmap.icon_5_foreground_sa, R.string.AppIconTurbo),
        NOX("NoxIcon", R.mipmap.icon_2_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconNox);

        public final String key;
        public final int background;
        public final int foreground;
        public final int title;
        public final boolean premium;

        private ComponentName componentName;

        public ComponentName getComponentName(Context ctx) {
            if (componentName == null) {
                componentName = new ComponentName(ctx.getPackageName(), "org.telegram.messenger." + key);
            }
            return componentName;
        }

        LauncherIcon(String key, int background, int foreground, int title) {
            this(key, background, foreground, title, false);
        }

        LauncherIcon(String key, int background, int foreground, int title, boolean premium) {
            this.key = key;
            this.background = background;
            this.foreground = foreground;
            this.title = title;
            this.premium = premium;
        }

    }
}
