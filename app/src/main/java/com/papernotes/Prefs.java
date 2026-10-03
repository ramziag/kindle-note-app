package com.papernotes;

import android.content.Context;
import android.content.SharedPreferences;

/** The writer's chosen typeface, text size, line spacing and page view, remembered between sessions. */
final class Prefs {

    static final float MIN_TEXT_SIZE = 12f;
    static final float MAX_TEXT_SIZE = 40f;
    /** Compact, normal, relaxed and wide line spacing, as multiples of the font's line height. */
    static final float[] LINE_SPACINGS = {1.15f, 1.4f, 1.7f, 2.0f};

    private static final String FILE = "settings";
    private static final String KEY_FONT = "font";
    private static final String KEY_TEXT_SIZE = "text_size_sp";
    private static final String KEY_LINE_SPACING = "line_spacing";
    private static final String KEY_PAGE_VIEW = "page_view";

    private Prefs() {}

    static String font(Context context) {
        return prefs(context).getString(KEY_FONT, Fonts.DEFAULT_KEY);
    }

    static void setFont(Context context, String key) {
        prefs(context).edit().putString(KEY_FONT, key).apply();
    }

    /** Body text size in sp; defaults to the size in dimens.xml for this screen. */
    static float textSize(Context context) {
        float defaultSp = context.getResources().getDimension(R.dimen.body_text)
                / context.getResources().getDisplayMetrics().scaledDensity;
        return prefs(context).getFloat(KEY_TEXT_SIZE, Math.round(defaultSp));
    }

    static void setTextSize(Context context, float sp) {
        float clamped = Math.max(MIN_TEXT_SIZE, Math.min(MAX_TEXT_SIZE, sp));
        prefs(context).edit().putFloat(KEY_TEXT_SIZE, clamped).apply();
    }

    static float lineSpacing(Context context) {
        return prefs(context).getFloat(KEY_LINE_SPACING, LINE_SPACINGS[1]);
    }

    static void setLineSpacing(Context context, float spacing) {
        prefs(context).edit().putFloat(KEY_LINE_SPACING, spacing).apply();
    }

    /** Whether notes are shown as separate pages (the default) or one continuous scroll. */
    static boolean pageView(Context context) {
        return prefs(context).getBoolean(KEY_PAGE_VIEW, true);
    }

    static void setPageView(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_PAGE_VIEW, on).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
