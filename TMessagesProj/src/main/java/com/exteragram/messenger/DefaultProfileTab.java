package com.exteragram.messenger;

public enum DefaultProfileTab {
    DEFAULT(0),
    MEDIA(1),
    COMMON_GROUPS(2);

    public final int prefId;

    DefaultProfileTab(int prefId) {
        this.prefId = prefId;
    }
}
