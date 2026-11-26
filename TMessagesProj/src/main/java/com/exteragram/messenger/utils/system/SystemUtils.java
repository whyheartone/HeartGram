package com.exteragram.messenger.utils.system;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.BasePermissionsActivity;

import app.exteraless.utils.AppUtils;

public final class SystemUtils {

    private SystemUtils() {
    }

    public static boolean isPermissionGranted(String perm) {
        return ApplicationLoader.applicationContext.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestPermissions(Activity activity, int code, String... perms) {
        if (activity == null) return;
        activity.requestPermissions(perms, code);
    }

    public static boolean isImagesPermissionGranted() {
        if (Build.VERSION.SDK_INT >= 33)
            return isPermissionGranted(Manifest.permission.READ_MEDIA_IMAGES);
        else return isStoragePermissionGranted();
    }

    public static boolean isVideoPermissionGranted() {
        if (Build.VERSION.SDK_INT >= 33)
            return isPermissionGranted(Manifest.permission.READ_MEDIA_VIDEO);
        else return isStoragePermissionGranted();
    }

    public static boolean isAudioPermissionGranted() {
        if (Build.VERSION.SDK_INT >= 33)
            return isPermissionGranted(Manifest.permission.READ_MEDIA_AUDIO);
        else return isStoragePermissionGranted();
    }

    public static boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= 33)
            return isImagesPermissionGranted() && isVideoPermissionGranted() && isAudioPermissionGranted();
        else return isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    public static boolean isImagesAndVideoPermissionGranted() {
        if (Build.VERSION.SDK_INT >= 33)
            return isImagesPermissionGranted() && isVideoPermissionGranted();
        else return isStoragePermissionGranted();
    }

    public static void requestImagesAndVideoPermission(Activity activity) {
        requestImagesAndVideoPermission(activity, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE);
    }

    public static void requestImagesAndVideoPermission(Activity activity, int code) {
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(activity, code, Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO);
        else requestPermissions(activity, code, Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    public static int getRoundVideoResolution() {
        return AppUtils.getRoundVideoResolution(
                MessagesController.getInstance(UserConfig.selectedAccount).roundVideoSize);
    }

    public static int getRoundVideoBitrate() {
        return MessagesController.getInstance(UserConfig.selectedAccount).roundVideoBitrate;
    }

    public static int getRoundAudioBitrate() {
        return MessagesController.getInstance(UserConfig.selectedAccount).roundAudioBitrate;
    }

    public static long getRoundVideoMaxDurationMs() {
        return Utilities.clamp(83886080000L / ((Math.max(1, getRoundVideoBitrate())
                + Math.max(0, getRoundAudioBitrate())) * 1024L), 60000L, 10000L);
    }
}
