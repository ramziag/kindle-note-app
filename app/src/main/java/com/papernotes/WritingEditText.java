package com.papernotes;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Layout;
import android.util.AttributeSet;
import android.widget.EditText;

/**
 * The note's text box. With line spacing, EditText's own cursor stretches over the
 * spacing below each line, so it is made transparent in the layout and this draws a
 * cursor just as tall as the letters, blinking at the usual rate.
 */
public class WritingEditText extends EditText {

    private static final int BLINK_MS = 500;

    private final Paint cursorPaint = new Paint();
    private final Paint.FontMetrics metrics = new Paint.FontMetrics();
    private final Runnable blink = new Runnable() {
        @Override
        public void run() {
            cursorOn = !cursorOn;
            invalidate();
            if (isFocused()) {
                postDelayed(this, BLINK_MS);
            }
        }
    };
    private final float cursorWidth;
    private boolean cursorOn = true;
    // False while EditText's constructor runs, before this class's fields are set up.
    private boolean ready;

    public WritingEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        cursorWidth = Math.max(2f, 1.5f * getResources().getDisplayMetrics().density);
        ready = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int offset = getSelectionStart();
        Layout layout = getLayout();
        if (!cursorOn || !isFocused() || layout == null || offset < 0 || offset != getSelectionEnd()) {
            return;
        }
        int line = layout.getLineForOffset(offset);
        float x = getCompoundPaddingLeft() + layout.getPrimaryHorizontal(offset);
        float baseline = getExtendedPaddingTop() + layout.getLineBaseline(line);
        getPaint().getFontMetrics(metrics);
        cursorPaint.setColor(getCurrentTextColor());
        canvas.drawRect(x - cursorWidth / 2, baseline + metrics.ascent, x + cursorWidth / 2,
                baseline + metrics.descent, cursorPaint);
    }

    /** Shows the cursor straight away after typing or moving it, then resumes blinking. */
    private void restartBlink() {
        if (!ready) {
            return;
        }
        cursorOn = true;
        removeCallbacks(blink);
        if (isFocused()) {
            postDelayed(blink, BLINK_MS);
        }
        invalidate();
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        restartBlink();
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        restartBlink();
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        restartBlink();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(blink);
        super.onDetachedFromWindow();
    }
}
