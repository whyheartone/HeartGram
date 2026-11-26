package com.exteragram.messenger.pillstack.ui.pills;

import android.content.Context;
import android.text.format.DateUtils;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;

public abstract class BasePill extends app.exteraless.pillstack.pills.BasePill {

    private static final SparseArray<Long> globalUpdatedAt = new SparseArray<>();

    public BasePill(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
    }

    @Override
    public void markDataUpdated() {
        super.markDataUpdated();
        globalUpdatedAt.put(getPillId(), System.currentTimeMillis());
    }

    protected long getLastUpdatedAt() {
        return globalUpdatedAt.get(getPillId(), 0L);
    }

    protected ItemOptions addUpdatedAt(ItemOptions options) {
        long time = getLastUpdatedAt();
        if (time <= 0) {
            return options;
        }
        String formatted = LocaleController.getInstance().getFormatterDay().format(time);
        TextView textView = new TextView(options.getContext());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        textView.setTypeface(AndroidUtilities.regular());
        textView.setTextColor(Theme.getColor(Theme.key_groupcreate_sectionText, resourcesProvider));
        textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        textView.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(9), AndroidUtilities.dp(18), AndroidUtilities.dp(9));
        textView.setTag(R.id.fit_width_tag, 1);
        if (DateUtils.isToday(time)) {
            textView.setText(LocaleController.formatString(R.string.CustomPillUpdatedAt, formatted));
        } else {
            String date = LocaleController.formatString(R.string.formatDateAtTime, LocaleController.getInstance().getFormatterDayMonth().format(time), formatted);
            textView.setText(LocaleController.formatString(R.string.CustomPillUpdatedDate, date));
        }
        options.addGap();
        options.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return options;
    }
}
