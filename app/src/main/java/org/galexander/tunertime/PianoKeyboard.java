package org.galexander.tunertime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/* One octave of a piano keyboard, C to B (German H), to pick a pitch
 * class.  The chosen key is filled with the accent color; a dot marks
 * the pitch classes the preset already has a note on. */
public class PianoKeyboard extends View {
	public interface Listener {
		void onKey(int pc);
	}

	private static final int[] WHITE = { 0, 2, 4, 5, 7, 9, 11 };
	/* black key pc, and the white key (index) it sits right of */
	private static final int[] BLACK = { 1, 3, 6, 8, 10 };
	private static final int[] BLACK_AFTER = { 0, 1, 3, 4, 5 };

	private Listener listener;
	private int selected = -1;
	private boolean[] present = new boolean[12];
	private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF r = new RectF();
	private final float dp;

	public PianoKeyboard(Context c, AttributeSet a) {
		super(c, a);
		dp = c.getResources().getDisplayMetrics().density;
	}

	public void setListener(Listener l) {
		listener = l;
	}

	public void setSelectedKey(int pc) {
		selected = pc;
		invalidate();
	}

	public void setPresent(boolean[] p) {
		present = p;
		invalidate();
	}

	private float white_w() {
		return getWidth() / 7f;
	}

	private void black_rect(int i, RectF out) {
		float w = white_w();
		float bw = w * 0.62f;
		float x = (BLACK_AFTER[i] + 1) * w - bw / 2;
		out.set(x, 0, x + bw, getHeight() * 0.6f);
	}

	private int key_at(float x, float y) {
		for (int i = 0; i < BLACK.length; i++) {
			black_rect(i, r);
			if (r.contains(x, y)) {
				return BLACK[i];
			}
		}
		int w = (int)(x / white_w());
		return WHITE[Math.max(0, Math.min(6, w))];
	}

	protected void onMeasure(int wspec, int hspec) {
		setMeasuredDimension(resolveSize((int)(300 * dp), wspec),
				     resolveSize((int)(150 * dp), hspec));
	}

	private void label(Canvas c, String s, float cx, float y, float size,
			   int color) {
		paint.setStyle(Paint.Style.FILL);
		paint.setColor(color);
		paint.setTextSize(size);
		paint.setTextAlign(Paint.Align.CENTER);
		c.drawText(s, cx, y, paint);
	}

	private void dot(Canvas c, int pc, float cx, float cy, int color) {
		if (present[pc] && (pc != selected)) {
			paint.setStyle(Paint.Style.FILL);
			paint.setColor(color);
			c.drawCircle(cx, cy, 3 * dp, paint);
		}
	}

	protected void onDraw(Canvas c) {
		int accent = getResources().getColor(R.color.accent);
		int bg = getResources().getColor(R.color.bg);
		float w = white_w();
		float h = getHeight();
		for (int i = 0; i < 7; i++) {
			int pc = WHITE[i];
			r.set(i * w + 1, 0, (i + 1) * w - 1, h);
			paint.setStyle(Paint.Style.FILL);
			paint.setColor((pc == selected) ? accent : 0xFFE8EAED);
			c.drawRect(r, paint);
			label(c, note.class_name(pc), r.centerX(), h - 14 * dp,
			      18 * dp, (pc == selected) ? 0xFFFFFFFF : 0xFF121417);
			dot(c, pc, r.centerX(), h - 38 * dp, accent);
		}
		for (int i = 0; i < BLACK.length; i++) {
			int pc = BLACK[i];
			black_rect(i, r);
			paint.setStyle(Paint.Style.FILL);
			paint.setColor(bg);
			c.drawRect(r.left - 1, r.top, r.right + 1, r.bottom + 1,
				   paint);
			paint.setColor((pc == selected) ? accent : 0xFF2A2F37);
			c.drawRect(r, paint);
			label(c, note.class_name(pc), r.centerX(),
			      r.bottom - 12 * dp, 13 * dp, 0xFFE8EAED);
			dot(c, pc, r.centerX(), r.bottom - 32 * dp, 0xFFE8EAED);
		}
	}

	public boolean onTouchEvent(MotionEvent e) {
		if (e.getAction() == MotionEvent.ACTION_DOWN) {
			int pc = key_at(e.getX(), e.getY());
			if (listener != null) {
				listener.onKey(pc);
			}
			performClick();
		}
		return true;
	}

	public boolean performClick() {
		return super.performClick();
	}
}
