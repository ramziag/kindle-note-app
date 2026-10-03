package com.papernotes;

import android.graphics.Paint;
import android.text.Editable;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.AlignmentSpan;
import android.text.style.LineHeightSpan;
import android.text.style.RelativeSizeSpan;

/**
 * Sets a note's first line as its title: larger, centred and with a little space
 * beneath, like a chapter heading. The spans cover only that line, so editing the
 * rest of the note lays out no more text than it otherwise would.
 */
final class TitleStyle {

    private static final float SCALE = 1.4f;

    private final Object size = new RelativeSizeSpan(SCALE);
    private final Object centre = new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER);
    private final Object gap;

    TitleStyle(final int gapPx) {
        gap = new LineHeightSpan() {
            @Override
            public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int v,
                    Paint.FontMetricsInt fm) {
                // Only the title's last line gets the extra space.
                if (text instanceof Spanned && end >= ((Spanned) text).getSpanEnd(this)) {
                    fm.descent += gapPx;
                    fm.bottom += gapPx;
                }
            }
        };
    }

    /** Fits the spans to the first line; does nothing if they already fit. */
    void apply(Editable text) {
        int end = TextUtils.indexOf(text, '\n');
        if (end < 0) {
            end = text.length();
        }
        if (end == 0) {
            text.removeSpan(size);
            text.removeSpan(centre);
            text.removeSpan(gap);
        } else if (text.getSpanStart(size) != 0 || text.getSpanEnd(size) != end) {
            text.setSpan(centre, 0, end, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            text.setSpan(gap, 0, end, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            text.setSpan(size, 0, end, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        }
    }
}
