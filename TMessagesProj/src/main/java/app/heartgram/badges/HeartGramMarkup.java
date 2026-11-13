package app.heartgram.badges;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.view.View;

import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Подпись значка: **жирный**, [текст](ссылка) и @username. Остальной текст остаётся как есть.
 */
public final class HeartGramMarkup {

    private static final Pattern TOKEN = Pattern.compile(
            "\\*\\*(.+?)\\*\\*|\\[([^\\]]+)\\]\\(([^)\\s]+)\\)|(?<![\\w@])@([A-Za-z][A-Za-z0-9_]{3,31})");

    private HeartGramMarkup() {
    }

    public static CharSequence render(final Context context, String raw) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        Matcher m = TOKEN.matcher(raw);
        int last = 0;
        while (m.find()) {
            out.append(raw, last, m.start());
            int from = out.length();
            if (m.group(1) != null) {
                out.append(m.group(1));
                out.setSpan(new StyleSpan(Typeface.BOLD), from, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (m.group(2) != null) {
                final String url = m.group(3);
                out.append(m.group(2));
                out.setSpan(new Link(() -> Browser.openUrl(context, url)), from, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                final String name = m.group(4);
                out.append('@').append(name);
                out.setSpan(new Link(() -> {
                    BaseFragment fragment = LaunchActivity.getSafeLastFragment();
                    if (fragment != null) {
                        fragment.getMessagesController().openByUserName(name, fragment, 1);
                    }
                }), from, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            last = m.end();
        }
        out.append(raw, last, raw.length());
        return out;
    }

    private static final class Link extends ClickableSpan {
        private final Runnable action;

        Link(Runnable action) {
            this.action = action;
        }

        @Override
        public void onClick(View widget) {
            action.run();
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            ds.setUnderlineText(false);
        }
    }
}
