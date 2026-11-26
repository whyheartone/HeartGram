package com.exteragram.messenger.utils.ui

import android.os.Build
import androidx.annotation.ColorInt
import org.telegram.messenger.ApplicationLoader

object MonetUtils {

    @JvmStatic
    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    @JvmStatic
    @ColorInt
    fun getSecondaryContainerColor(isDark: Boolean): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return ApplicationLoader.applicationContext.getColor(
                if (isDark) android.R.color.system_secondary_container_dark else android.R.color.system_secondary_container_light
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ApplicationLoader.applicationContext.getColor(
                if (isDark) android.R.color.system_accent2_700 else android.R.color.system_accent2_100
            )
        }
        return 0
    }

    @JvmStatic
    @ColorInt
    fun getOnSecondaryContainerColor(isDark: Boolean): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return ApplicationLoader.applicationContext.getColor(
                if (isDark) android.R.color.system_on_secondary_container_dark else android.R.color.system_on_secondary_container_light
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ApplicationLoader.applicationContext.getColor(
                if (isDark) android.R.color.system_accent2_100 else android.R.color.system_accent2_900
            )
        }
        return 0
    }
}
