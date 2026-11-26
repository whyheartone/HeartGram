package com.exteragram.messenger.utils.ui.material;

import androidx.core.graphics.ColorUtils;

public final class ColorTones {

    private static final float LIGHT_SURFACE_MIN_TONE = 50f;

    private static final double[] lab = new double[3];

    private ColorTones() {
    }

    public static boolean isLightSurface(int color) {
        return getTone(color) > LIGHT_SURFACE_MIN_TONE;
    }

    public static float getTone(int color) {
        ColorUtils.colorToLAB(color, lab);
        return (float) lab[0];
    }

    public static int withTone(int color, float tone, double maxChroma) {
        ColorUtils.colorToLAB(color, lab);
        final double chroma = Math.hypot(lab[1], lab[2]);
        if (chroma > maxChroma && chroma > 0) {
            final double scale = maxChroma / chroma;
            lab[1] *= scale;
            lab[2] *= scale;
        }
        return ColorUtils.LABToColor(tone, lab[1], lab[2]);
    }
}
