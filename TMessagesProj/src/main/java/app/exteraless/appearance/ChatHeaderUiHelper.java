package app.exteraless.appearance;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ChatActivityTopPanelLayout;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.Components.chat.layouts.ChatActivityFadeView;

/**
 * Material 3 для шапки чата.
 * exteraGram: com/exteragram/messenger/utils/ui/ChatHeaderUiHelper.java (233 строки).
 *
 * Перенесены только те методы, у которых в нашем дереве есть на что опереться;
 * ProfileTransitionState опущен — см. notes.
 */
public final class ChatHeaderUiHelper {

    private ChatHeaderUiHelper() {
    }

    public static boolean isMaterial3ChatHeaderStyle() {
        return AppearanceConfig.newChatHeaderStyle();
    }

    /** Назад ? 4 : (m3 ? 57 : 52). */
    public static int getAvatarContainerLeftMargin(boolean withBackButton) {
        if (withBackButton) {
            return 4;
        }
        return isMaterial3ChatHeaderStyle() ? 57 : 52;
    }

    /** M3 ? 0 : 1 пиксель отступа вокруг аватарки. */
    public static int getAvatarInsetPx() {
        return isMaterial3ChatHeaderStyle() ? 0 : 1;
    }

    public static int getAvatarSizePx(int sizeDp) {
        return AndroidUtilities.dp(sizeDp) - getAvatarInsetPx() * 2;
    }

    /** M3 ? 46 : 42. */
    public static int getChatAvatarSizeDp() {
        return isMaterial3ChatHeaderStyle() ? 46 : 42;
    }

    /**
     * Радиус аватарки в шапке. У exteraGram это ExteraConfig.getAvatarCorners
     * (ChatHeaderUiHelper.java:159-165) из отдельного среза «углы аватарок»; у нас его нет,
     * поэтому радиусы пропорциональны размеру. При 42dp значения совпадают с прежними
     * (21 / 16 / 11 — ChatAvatarContainer.java:1510, 1618, 1626).
     */
    public static int getChatAvatarRadius(int sizeDp, boolean forum, boolean hasStories) {
        return AppearanceConfig.getAvatarCorners(AndroidUtilities.dp(sizeDp),
                forum ? AppearanceConfig.CORNER_TYPE_FORUM : AppearanceConfig.CORNER_TYPE_DEFAULT, hasStories);
    }

    /** Dp(m3 ? 78 : 48). */
    public static int getChatTopFadeHeight() {
        return AndroidUtilities.dp(isMaterial3ChatHeaderStyle() ? 78.0f : 48.0f);
    }

    /** M3 ? height + dp(42) : height. */
    public static int getChatTopFadeZone(int height) {
        return isMaterial3ChatHeaderStyle() ? height + AndroidUtilities.dp(42.0f) : height;
    }

    public static int getScaledChatTopFadeZone(ActionBar actionBar, int height) {
        final int measuredHeight = actionBar.getMeasuredHeight();
        final int zone = getChatTopFadeZone(measuredHeight);
        if (measuredHeight == 0 || zone >= height) {
            return height;
        }
        return zone + Math.round((height - zone) * 0.5f);
    }

    public static int getChatFadeColorKey() {
        return Theme.key_windowBackgroundGray;
    }

    public static void setupChatTopFade(ChatActivityFadeView fadeView, ActionBar actionBar, int fadeColor, int actionBarHeight) {
        fadeView.setFadeTopAlpha(actionBar.getVisibility() == ActionBar.VISIBLE ? 255 : 0);
        final int zone = getChatTopFadeZone(actionBarHeight);
        if (isMaterial3ChatHeaderStyle()) {
            fadeView.setFadeZoneTop(getScaledChatTopFadeZone(actionBar, zone));
            fadeView.setTopFadeColor(fadeColor);
        } else {
            fadeView.setFadeZoneTop(zone);
        }
        fadeView.setFadeHeightTop(getChatTopFadeHeight());
    }

    public static float getTopPanelActionBarGapOffset(ChatActivityTopPanelLayout topPanel) {
        if (!isMaterial3ChatHeaderStyle() || topPanel == null) {
            return 0.0f;
        }
        return AndroidUtilities.dp(4.0f) * topPanel.getMetadata().getTotalVisibility();
    }

    public static float getFinalTopPanelHeight(float height, ChatActivityTopPanelLayout topPanel) {
        return height + getTopPanelActionBarGapOffset(topPanel);
    }

    /** (a + dp(m3 ? -1 : -5)) - b * c. */
    public static float getTopPanelTranslationY(float base, float value, float factor) {
        return (base + AndroidUtilities.dp(isMaterial3ChatHeaderStyle() ? -1 : -5)) - value * factor;
    }

    public static boolean isLightChatStatusBar(ActionBar actionBar, int color) {
        if (!isMaterial3ChatHeaderStyle() || actionBar.isActionModeShowed()) {
            color = actionBar.getBackgroundColor();
        }
        return AndroidUtilities.computePerceivedBrightness(color) > 0.721f;
    }

    /** M3 убирает среднюю стеклянную плашку под заголовком и её тень. */
    public static void applyChatHeaderGlassStyle(ActionBar actionBar) {
        if (isMaterial3ChatHeaderStyle()) {
            actionBar.setDrawGlassMiddlePill(false);
            actionBar.setGlassShadowAlpha(0.0f);
        }
    }

    public static void setupGlassAvatarContainer(ChatAvatarContainer avatarContainer) {
        avatarContainer.setGlassMode();
        if (isMaterial3ChatHeaderStyle()) {
            avatarContainer.setAvatarSizeInDp(getChatAvatarSizeDp());
        }
    }

    /** Состояние перехода аватарки и заголовка между шапкой чата и профилем (вкл. MD3 46dp). */
    public static final class ProfileTransitionState {
        private float avatarTranslation;
        private float avatarStartY = Float.NaN;
        private float avatarSizeDp = 42f;
        private float nameTranslationY = Float.NaN;
        private float onlineTranslationY = Float.NaN;
        private int titleWidthPx;
        private int subtitleWidthPx;

        public void reset() {
            avatarTranslation = 0f;
            avatarStartY = Float.NaN;
            avatarSizeDp = getChatAvatarSizeDp();
            nameTranslationY = Float.NaN;
            onlineTranslationY = Float.NaN;
            titleWidthPx = 0;
            subtitleWidthPx = 0;
        }

        public void capture(ChatAvatarContainer sourceAvatarContainer, View sourceRoot, View profileNameView, View profileOnlineView) {
            if (sourceAvatarContainer == null || !(sourceRoot instanceof ViewGroup)) {
                return;
            }
            ViewGroup sourceParent = (ViewGroup) sourceRoot;
            BackupImageView sourceAvatarImage = sourceAvatarContainer.avatarImageView;
            if (sourceAvatarImage == null) {
                return;
            }
            avatarTranslation = ViewPositionWatcher.computeXCoordinateInParent(sourceAvatarImage, sourceParent);
            avatarStartY = ViewPositionWatcher.computeYCoordinateInParent(sourceAvatarImage, sourceParent);
            int sourceAvatarWidth = sourceAvatarImage.getMeasuredWidth() != 0 ? sourceAvatarImage.getMeasuredWidth() : sourceAvatarImage.getWidth();
            if (sourceAvatarWidth > 0) {
                avatarSizeDp = sourceAvatarWidth / AndroidUtilities.density;
            }
            View sourceTitle = sourceAvatarContainer.getTitleTextView();
            if (sourceTitle != null) {
                int w = sourceTitle.getMeasuredWidth() != 0 ? sourceTitle.getMeasuredWidth() : sourceTitle.getWidth();
                if (w > 0) {
                    titleWidthPx = w;
                }
                if (profileNameView != null) {
                    nameTranslationY = ViewPositionWatcher.computeYCoordinateInParent(sourceTitle, sourceParent) - getTransitionLayoutTop(profileNameView);
                }
            }
            View sourceSubtitle = sourceAvatarContainer.getSubtitleTextView();
            if (sourceSubtitle != null) {
                int w = sourceSubtitle.getMeasuredWidth() != 0 ? sourceSubtitle.getMeasuredWidth() : sourceSubtitle.getWidth();
                if (w > 0) {
                    subtitleWidthPx = w;
                }
                if (profileOnlineView != null) {
                    onlineTranslationY = ViewPositionWatcher.computeYCoordinateInParent(sourceSubtitle, sourceParent) - getTransitionLayoutTop(profileOnlineView) - profileOnlineView.getPaddingTop();
                }
            }
        }

        public float getAvatarTranslation() {
            return avatarTranslation;
        }

        public float getAvatarStartY(ActionBar actionBar) {
            if (!Float.isNaN(avatarStartY)) {
                return avatarStartY;
            }
            return (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0)
                    + ActionBar.getCurrentActionBarHeight() / 2f
                    - dp(avatarSizeDp / 2f)
                    + actionBar.getTranslationY();
        }

        public float getAvatarStartScale() {
            return avatarSizeDp / 100f;
        }

        public float getAvatarSizeDp() {
            return avatarSizeDp;
        }

        public int getTitleWidthPx() {
            return titleWidthPx;
        }

        public int getSubtitleWidthPx() {
            return subtitleWidthPx;
        }

        public float getTextTranslationXFallback() {
            float gapDp = isMaterial3ChatHeaderStyle() ? 10.66f : 6f;
            return avatarTranslation - dp(109) + dp(avatarSizeDp + gapDp);
        }

        public float getNameTranslationY(ActionBar actionBar) {
            if (!Float.isNaN(nameTranslationY)) {
                return nameTranslationY;
            }
            float fallbackTopDp = isMaterial3ChatHeaderStyle() ? 1.66f : 1.3f;
            return (float) Math.floor(getAvatarStartY(actionBar)) + dp(fallbackTopDp);
        }

        public float getOnlineTranslationY(ActionBar actionBar) {
            if (!Float.isNaN(onlineTranslationY)) {
                return onlineTranslationY;
            }
            float fallbackTopDp = isMaterial3ChatHeaderStyle() ? 26.66f : 24f;
            return (float) Math.floor(getAvatarStartY(actionBar)) + dp(fallbackTopDp);
        }

        public void updateBadgePositionsFromCollapsedAvatar(View avatarContainer, float avatarScale, ImageView timeItem, ImageView communityItem, ImageView starBgItem, ImageView starFgItem) {
            final float extra = dp(avatarSizeDp) * (avatarScale * 100f / avatarSizeDp) - dp(avatarSizeDp);
            updateBadgePositions(avatarContainer, extra, timeItem, communityItem, starBgItem, starFgItem);
        }

        public void updateBadgePositionsFromExpandedAvatar(View avatarContainer, float avatarScale, ImageView timeItem, ImageView communityItem, ImageView starBgItem, ImageView starFgItem) {
            final float extra = (avatarContainer.getMeasuredWidth() - dp(avatarSizeDp)) * (avatarScale * 100f / avatarSizeDp);
            updateBadgePositions(avatarContainer, extra, timeItem, communityItem, starBgItem, starFgItem);
        }

        private void updateBadgePositions(View avatarContainer, float extra, ImageView timeItem, ImageView communityItem, ImageView starBgItem, ImageView starFgItem) {
            if (timeItem != null) {
                timeItem.setTranslationX(avatarContainer.getX() + dp(avatarSizeDp - 26) + extra);
                timeItem.setTranslationY(avatarContainer.getY() + dp(-10) + extra);
            }
            if (communityItem != null) {
                communityItem.setTranslationX(avatarContainer.getX() + dp(avatarSizeDp - 14) + extra);
                communityItem.setTranslationY(avatarContainer.getY() + dp(avatarSizeDp - 15.5f) + extra);
            }
            if (starBgItem != null) {
                starBgItem.setTranslationX(avatarContainer.getX() + dp(avatarSizeDp - 14) + extra);
                starBgItem.setTranslationY(avatarContainer.getY() + dp(avatarSizeDp - 18) + extra);
            }
            if (starFgItem != null) {
                starFgItem.setTranslationX(avatarContainer.getX() + dp(avatarSizeDp - 14) + extra);
                starFgItem.setTranslationY(avatarContainer.getY() + dp(avatarSizeDp - 18) + extra);
            }
        }

        private static int getTransitionLayoutTop(View view) {
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
                return ((ViewGroup.MarginLayoutParams) layoutParams).topMargin;
            }
            return view.getTop();
        }
    }
}
