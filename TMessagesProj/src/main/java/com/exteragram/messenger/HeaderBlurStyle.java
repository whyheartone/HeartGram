package com.exteragram.messenger;

public enum HeaderBlurStyle {
    DEFAULT(0),
    PROGRESSIVE(1),
    FADE(2);

    public final int prefId;

    HeaderBlurStyle(int prefId) {
        this.prefId = prefId;
    }
}
