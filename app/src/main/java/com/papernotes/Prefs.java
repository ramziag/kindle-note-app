package com.papernotes;

import android.content.Context;
import android.content.SharedPreferences;

/** The reader's chosen typeface and text size, remembered between sessions. */
final class Prefs {

    static final float MIN_TEXT_SIZE = 12f;
    static final float MAX_TEXT_SIZE = 40f;

    private static final String FILE = "settings";
    private static final String KEY_FONT = "font";
    private static final String KEY_TEXT_SIZE = "text_size_sp";

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

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
