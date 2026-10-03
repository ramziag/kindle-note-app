package com.papernotes;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.AlignmentSpan;
import android.text.style.LineHeightSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.UpdateLayout;
import android.util.AttributeSet;
import android.widget.EditText;

/**
 * The writing surface. The note's first line is set as a large centred title and the
 * lines are spaced as chosen. In page view the text is laid out on separate,
 * paperback-proportioned pages with a running head and page number, and a line is
 * never split across a page break.
 */
public class PagedEditText extends EditText {

    static final float TITLE_SCALE = 1.5f;
    // Height to width of a page, close to a paperback's.
    private static final float PAGE_ASPECT = 1.45f;

    private final PageSpan pageSpan = new PageSpan();
    private final RelativeSizeSpan titleSize = new RelativeSizeSpan(TITLE_SCALE);
    private final AlignmentSpan titleAlign = new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER);
    private final TextPaint decorPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint();
    private final Runnable blink = new Runnable() {
        @Override
        public void run() {
            cursorOn = !cursorOn;
            invalidate();
            if (isFocused()) {
                postDelayed(this, 500);
            }
        }
    };

    // False while EditText's constructor runs, before this class's fields are set up.
    private boolean ready;
    private boolean settingText;
    private boolean paged;
    private boolean cursorOn = true;
    private int pageHeight;
    private final int plainTop;
    private final int plainBottom;
    private final int plainSide;
    private final int marginTop;
    private final int marginBottom;
    private final int marginSide;
    private final int pageGap;
    private final int shadow;
    private final int cursorWidth;
    private final int paperColor;
    private final int shadowColor;

    /**
     * Spaces lines and, in page view, pushes a line that would cross the foot of a page
     * down to the top of the next. It covers the whole text and, being a WrapTogetherSpan,
     * makes every reflow start from the first line, so v is always measured from the top.
     */
    private final class PageSpan implements LineHeightSpan, UpdateLayout {
        float spacing = 1.4f;
        int titleGap;
        int stride;      // page height plus the gap after it; 0 when not paged
        int textHeight;  // room for text on each page

        @Override
        public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int v,
                Paint.FontMetricsInt fm) {
            int natural = fm.descent - fm.ascent;
            int extra = Math.round(natural * (spacing - 1f));
            int titleEnd = text instanceof Spanned ? ((Spanned) text).getSpanEnd(titleSize) : -1;
            if (titleEnd > 0 && start < titleEnd && end >= titleEnd) {
                extra += titleGap;
            }
            fm.descent += extra;
            fm.bottom += extra;
            if (stride > 0) {
                int pageStart = v / stride * stride;
                if (v > pageStart && v + natural > pageStart + textHeight) {
                    int push = pageStart + stride - v;
                    fm.ascent -= push;
                    fm.top -= push;
                }
            }
        }
    }

    public PagedEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        plainTop = getPaddingTop();
        plainBottom = getPaddingBottom();
        plainSide = getPaddingLeft();
        marginTop = Math.round(52 * density);
        marginBottom = Math.round(60 * density);
        marginSide = getResources().getDimensionPixelSize(R.dimen.page_inner_margin);
        pageGap = Math.round(16 * density);
        shadow = Math.round(2 * density);
        cursorWidth = Math.max(2, Math.round(1.5f * density));
        paperColor = getResources().getColor(R.color.paper_card);
        shadowColor = getResources().getColor(R.color.page_shadow);
        pageSpan.titleGap = Math.round(14 * density);
        decorPaint.setTextAlign(Paint.Align.CENTER);
        decorPaint.setColor(getResources().getColor(R.color.ink_faint));
        decorPaint.setTextSize(13 * getResources().getDisplayMetrics().scaledDensity);
        decorPaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
        ready = true;
        attachSpans();
    }

    void setLineSpacingFactor(float spacing) {
        if (spacing != pageSpan.spacing) {
            pageSpan.spacing = spacing;
            relayout();
        }
    }

    void setPaged(boolean paged) {
        this.paged = paged;
        if (paged) {
            setPadding(marginSide, marginTop, marginSide, marginBottom);
        } else {
            setPadding(plainSide, plainTop, plainSide, plainBottom);
        }
        requestLayout();
    }

    boolean isPaged() {
        return paged && pageSpan.stride > 0;
    }

    int pageCount() {
        Layout layout = getLayout();
        if (!isPaged() || layout == null || layout.getHeight() <= 0) {
            return 1;
        }
        return (layout.getHeight() - 1) / pageSpan.stride + 1;
    }

    /** The page showing at y, in this view's coordinates, counting from 1. */
    int pageAt(int y) {
        if (!isPaged()) {
            return 1;
        }
        return Math.max(1, Math.min(pageCount(), y / pageSpan.stride + 1));
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        settingText = true;
        super.setText(text, type);
        settingText = false;
        attachSpans();
    }

    @Override
    public void setTypeface(Typeface typeface) {
        super.setTypeface(typeface);
        if (ready) {
            decorPaint.setTypeface(Typeface.create(typeface, Typeface.ITALIC));
        }
    }

    private void attachSpans() {
        if (!ready) {
            return;
        }
        Editable text = getText();
        text.setSpan(pageSpan, 0, text.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        updateTitle();
    }

    /** Keeps the title spans on exactly the first line. */
    private void updateTitle() {
        Editable text = getText();
        int end = TextUtils.indexOf(text, '\n');
        if (end < 0) {
            end = text.length();
        }
        if (end == 0) {
            text.removeSpan(titleSize);
            text.removeSpan(titleAlign);
        } else if (text.getSpanStart(titleSize) != 0 || text.getSpanEnd(titleSize) != end) {
            text.setSpan(titleAlign, 0, end, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            text.setSpan(titleSize, 0, end, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        }
    }

    /** Lays every line out again, e.g. after the spacing or page size changes. */
    private void relayout() {
        if (!ready) {
            return;
        }
        Editable text = getText();
        text.removeSpan(pageSpan);
        text.setSpan(pageSpan, 0, text.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        requestLayout();
        invalidate();
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        if (ready && !settingText) {
            updateTitle();
            restartBlink();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int stride = 0;
        int textHeight = 0;
        if (paged && width > 0) {
            pageHeight = Math.round(width * PAGE_ASPECT);
            stride = pageHeight + pageGap;
            textHeight = pageHeight - marginTop - marginBottom;
        }
        if (stride != pageSpan.stride || textHeight != pageSpan.textHeight) {
            pageSpan.stride = stride;
            pageSpan.textHeight = textHeight;
            relayout();
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (isPaged() && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
            // Always end on a whole page.
            int height = pageCount() * pageSpan.stride - pageGap;
            setMeasuredDimension(getMeasuredWidth(), Math.max(getMeasuredHeight(), height));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isPaged()) {
            drawPages(canvas);
        }
        super.onDraw(canvas);
        drawCursor(canvas);
    }

    private void drawPages(Canvas canvas) {
        Rect clip = canvas.getClipBounds();
        int stride = pageSpan.stride;
        int width = getWidth();
        int last = Math.min(pageCount() - 1, clip.bottom / stride);
        CharSequence head = runningHead(width - 2 * marginSide);
        float textOffset = decorPaint.getTextSize() / 3;
        for (int i = Math.max(0, clip.top / stride); i <= last; i++) {
            int top = i * stride;
            int bottom = top + pageHeight;
            fill.setColor(shadowColor);
            canvas.drawRect(shadow, top + shadow, width, bottom + shadow, fill);
            fill.setColor(paperColor);
            canvas.drawRect(0, top, width - shadow, bottom, fill);
            if (i > 0 && head.length() > 0) {
                canvas.drawText(head, 0, head.length(), width / 2f, top + marginTop / 2f + textOffset, decorPaint);
            }
            canvas.drawText(String.valueOf(i + 1), width / 2f, bottom - marginBottom / 2f + textOffset, decorPaint);
        }
    }

    /** The title, shortened to fit, for the top of every page after the first. */
    private CharSequence runningHead(int width) {
        Editable text = getText();
        int end = text.getSpanEnd(titleSize);
        if (end <= 0 || width <= 0) {
            return "";
        }
        String title = text.subSequence(0, end).toString().trim();
        return TextUtils.ellipsize(title, decorPaint, width, TextUtils.TruncateAt.END);
    }

    /**
     * EditText's own cursor (made transparent in the layout) runs the full height of a
     * line, which here includes the line spacing and any page break above it; this one
     * covers just the letters.
     */
    private void drawCursor(Canvas canvas) {
        int offset = getSelectionStart();
        Layout layout = getLayout();
        if (!cursorOn || !isFocused() || layout == null || offset < 0 || offset != getSelectionEnd()) {
            return;
        }
        int line = layout.getLineForOffset(offset);
        float x = getCompoundPaddingLeft() + layout.getPrimaryHorizontal(offset);
        float baseline = getExtendedPaddingTop() + layout.getLineBaseline(line);
        int titleEnd = getText().getSpanEnd(titleSize);
        float scale = titleEnd > 0 && offset <= titleEnd ? TITLE_SCALE : 1f;
        Paint.FontMetrics fm = getPaint().getFontMetrics();
        fill.setColor(getCurrentTextColor());
        canvas.drawRect(x - cursorWidth / 2f, baseline + fm.ascent * scale, x + cursorWidth / 2f,
                baseline + fm.descent * scale, fill);
    }

    private void restartBlink() {
        if (!ready) {
            return;
        }
        cursorOn = true;
        removeCallbacks(blink);
        if (isFocused()) {
            postDelayed(blink, 500);
        }
        invalidate();
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
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
