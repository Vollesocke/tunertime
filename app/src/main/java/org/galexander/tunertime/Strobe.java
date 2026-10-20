package org.galexander.tunertime;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.SurfaceHolder;
import java.util.concurrent.Semaphore;

public class Strobe extends Thread {
	public final static int FFT_RANGE_MULT = 65536;

	private static final Object lock = new Object();
	private static volatile Strobe curr_thread = null;
	private static final Semaphore sem = new Semaphore(1);

	private volatile int width = 0, height = 0;
	private SurfaceHolder holder = null;
	private volatile boolean surface_changed = false;

	public volatile boolean paused = false;

	private TunerTime ui = null;

	Strobe(TunerTime ui_) {
		super();
		synchronized(lock) {
			if (curr_thread != null) {
				curr_thread.interrupt();
			}
			curr_thread = this;
		}
		ui = ui_;
	}

	public void setSurface(SurfaceHolder holder_, int width_, int height_) {
		synchronized(lock) {
			holder = holder_;
			width = width_;
			height = height_;
			surface_changed = true;
		}
	}

	/* if the surface has changed, return [width, height].  else, null */
	public int[] check_surface() {
		int[] ret = null;
		synchronized(lock) {
			if (surface_changed) {
				ret = new int[2];
				ret[0] = width;
				ret[1] = height;
				surface_changed = false;
			}
		}
		return ret;
	}

	/* called from the native loop with each new pitch estimate, at
	 * input sample pos (jni/record.c) */
	public void pitch_detected(double hz, double conf, long pos) {
		EventLog.estimate(pos, hz, conf);
		ui.pitch_detected(hz);
	}

	/* organ mode: the focused row's partial gained cycles on the row's
	 * frequency freq during secs of usable signal, in the last buffer of
	 * buf_secs.  called from the native loop after every buffer while a
	 * row is focused.  smoothed here for the display in place of the
	 * piano key name, and passed on for mixture mode */
	private static final double FOCUS_TAU = 0.5;	/* display, seconds */
	private double focus_freq = 0.0d;
	private double focus_cycles = 0.0d, focus_secs = 0.0d;
	private volatile String focus_str = null;
	private volatile double focus_str_ratio = 0.0d;	/* focus_str is for */

	/* amp: the mean amplitude of the buffer's usable drift blocks, 0 if
	 * none (jni strobe_drift_amp), for mixture mode's beat warning.
	 * pos: the input sample at the end of the buffer (jni/record.c) */
	public void focus_drift(double freq, double cycles, double secs,
				double buf_secs, double amp, long pos) {
		EventLog.drift(pos, parms.focus_ratio, freq, cycles, secs);
		if (freq != focus_freq) {
			focus_freq = freq;
			focus_cycles = 0.0d;
			focus_secs = 0.0d;
		}
		double d = Math.exp(-buf_secs/FOCUS_TAU);
		focus_cycles = focus_cycles*d + cycles;
		focus_secs = focus_secs*d + secs;
		String s = parms.footage_str(parms.focus_ratio) + " ";
		if ((focus_secs > FOCUS_TAU/4.0d) && (freq > 0.0d)) {
			double c = 1200.0d * Math.log(
				(freq + focus_cycles/focus_secs)/freq) /
				Math.log(2.0d);
			s += note.cents_str((int)Math.round(
						c*preset.UNITS_PER_CENT));
		} else {
			s += " --.-c";	/* as wide as "+01.5c" */
		}
		focus_str = s;
		focus_str_ratio = parms.focus_ratio;
		ui.focus_drift(freq, cycles, secs, buf_secs, amp);
	}

	public void toggle_pause() {
		paused = !paused;
		if (!paused) {
			parms.fft_xoffset = FFT_RANGE_MULT;
		}
	}

	private native void runner();		/* efficient C loop */
		/* jni ORGAN_MAX_FREQ: organ mode leaves rows above this
		 * fraction of the sample rate blank */
	public static native double organ_max_freq();

	/* organ mode: strobe row i of the target in strobe_cents is above
	 * what the sample rate carries, so it is blank and never measures */
	public static boolean row_too_high(TunerTime ui, int i) {
		double f = ui.cents_to_freq(parms.strobe_cents, 1) *
				parms.row_ratio(i);
		return parms.organ_mode &&
			(f > organ_max_freq() * Listener.get_samplerate());
	}

	private Paint label_p = null;
	private Paint pause_p = null;
	private Paint pianoname_p = null;
	private String[] labels = null;
	private int label_h = 0;
	private String pianoname = null;

	private void draw_labels(Canvas c) {
		if ((labels == null) || (labels.length == 0) || parms.fft_mode) {
			return;
		}

		int strobe_height = height/labels.length;
		if (label_p == null) {
			label_p = new Paint();
			label_p.setARGB(0xff,0xe8,0xea,0xed);
			label_p.setTextAlign(Paint.Align.LEFT);
			label_p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
			label_p.setFakeBoldText(true);
			label_p.setAntiAlias(true);
			label_p.setSubpixelText(true);
		}
		if (label_h != strobe_height) {
			/* a fifth of a row, but at least 12sp so that five rows
			 * still read in landscape, and never over a third */
			float sp12 = TypedValue.applyDimension(
				TypedValue.COMPLEX_UNIT_SP, 12.0f,
				ui.getResources().getDisplayMetrics());
			float sz = Math.max(strobe_height/5.0f, sp12);
			label_p.setTextSize(Math.min(sz, strobe_height/3.0f));
			label_h = strobe_height;
		}
		int focus = parms.organ_mode ? parms.focus_row() : -1;
		int y = -(int)label_p.getFontMetrics().ascent;
		String[] shown = shown_labels();
		for (int i = 0; i < shown.length; i++) {
			if (i == focus) {
				draw_focus(c, i*strobe_height, strobe_height);
				c.drawText(shown[i], 0, y, focus_p);
			} else {
				c.drawText(shown[i], 0, y, label_p);
			}
			y += strobe_height;
		}
	}

	/* the native side writes the row labels in English: " 2nd  880.0",
	 * "  2 2/3' too high".  translated here when they change */
	private String[] labels_src = null, labels_shown = null;
	private String[] shown_labels() {
		String[] src = labels;
		if (src == labels_src) {
			return labels_shown;
		}
		String high = ui.getString(R.string.row_too_high);
		String suffix = ui.getString(R.string.ordinal_suffix);
		String[] out = new String[src.length];
		for (int i = 0; i < src.length; i++) {
			String s = src[i];
			if (s.endsWith(" too high")) {
				s = s.substring(0, s.length()-8) + high;
			}
			if (suffix.length() > 0) {
				/* " 2nd" -> " 2. ", keeping the columns */
				s = s.replaceFirst("^( *[0-9]+)(st|nd|rd|th) ",
						"$1" + suffix +
						("  ".substring(Math.min(2,
							suffix.length()))) + " ");
			}
			out[i] = s;
		}
		labels_shown = out;
		labels_src = src;
		return out;
	}

	/* organ mode: frames the focused row, labelled in the accent colour */
	private Paint focus_p = null;
	private Paint frame_p = null;
	private void draw_focus(Canvas c, int y, int h) {
		if (frame_p == null) {
			int accent = ui.getResources().getColor(R.color.accent);
			frame_p = new Paint();
			frame_p.setColor(accent);
			frame_p.setStyle(Paint.Style.STROKE);
			frame_p.setStrokeWidth(Math.max(3.0f, width/150.0f));
			focus_p = new Paint(label_p);
			focus_p.setColor(accent);
		}
		focus_p.setTextSize(label_p.getTextSize());
		float sw = frame_p.getStrokeWidth()/2.0f;
		c.drawRect(sw, y+sw, width-sw, y+h-sw, frame_p);
	}

	private String paused_str = null;
	private void draw_paused(Canvas c) {
		if (pause_p == null) {
			pause_p = new Paint();
			pause_p.setARGB(0xff,0x4c,0x8d,0xff);
			pause_p.setTextAlign(Paint.Align.LEFT);
			pause_p.setTextSize(height/10);
			pause_p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
			pause_p.setFakeBoldText(true);
			pause_p.setAntiAlias(true);
			pause_p.setSubpixelText(true);
		}
		if (paused_str == null) {
			paused_str = ui.getString(R.string.paused);
		}
		c.drawText(paused_str, 0, height/2, pause_p);
	}

	/* like "A49" for A-4.  the native side sets pianoname once the
	 * strobe is set up, but the text is formatted here so that it
	 * follows the note-names setting.  in organ mode, the focused
	 * partial's measured offset in place of the piano key, like
	 * "4' +01.5c"; with no row focused, the target's offset from equal
	 * temperament (note, temperament and cents set by hand), like
	 * "target -05.9c" for a Werckmeister note */
	private int pn_cents = -1;
	private boolean pn_german = false;
	private String pn_str = null;
	private int tgt_cents = Integer.MIN_VALUE;
	private String tgt_str = null;
	private String target_str() {
		int cents = parms.strobe_cents;
		if ((cents != tgt_cents) || (tgt_str == null)) {
			tgt_str = ui.getString(R.string.readout_target,
					note.cents_str(new note(cents).cents));
			tgt_cents = cents;
		}
		return tgt_str;
	}
	private void draw_pianoname(Canvas c) {
		String s = pianoname;
		if (parms.organ_mode) {
			if (parms.focus_ratio <= 0.0d) {
				/* The target pitch and temperament are already in the
				 * title in organ mode. */
				s = null;
			} else {
				s = (focus_str_ratio == parms.focus_ratio) ?
							focus_str : null;
			}
		}
		if (parms.fft_mode || (s == null)) {
			return;
		}
		if (!parms.organ_mode) {
			int cents = parms.strobe_cents;
			boolean german = parms.german_names;
			if ((cents != pn_cents) || (german != pn_german)) {
				pn_str = note.piano_name(cents);
				pn_cents = cents;
				pn_german = german;
			}
			s = pn_str;
		}
		if (pianoname_p == null) {
			pianoname_p = new Paint();
			pianoname_p.setARGB(0xff,0x9a,0xa0,0xa6);
			pianoname_p.setTextAlign(Paint.Align.RIGHT);
			pianoname_p.setTextSize(height/24);
			pianoname_p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
			pianoname_p.setFakeBoldText(true);
			pianoname_p.setAntiAlias(true);
			pianoname_p.setSubpixelText(true);
		}
		Paint p = parms.organ_mode ? focus_readout_paint(s) : pianoname_p;
		int y = -(int)p.getFontMetrics().ascent;
		if (label_p != null) {
			/* share the baseline of the first label */
			y = Math.max(y, -(int)label_p.getFontMetrics().ascent);
		}
		c.drawText(s, width - right_margin(), y, p);
	}

	/* keeps the right-aligned texts clear of the screen edge: the fake
	 * bold and the shadow reach past the advance width */
	private float right_margin_px = -1.0f;
	private float right_margin() {
		if (right_margin_px < 0.0f) {
			right_margin_px = TypedValue.applyDimension(
				TypedValue.COMPLEX_UNIT_DIP, 8.0f,
				ui.getResources().getDisplayMetrics());
		}
		return right_margin_px;
	}

	/* the focused partial's offset is what the organ tuner reads, from
	 * arm's length: in the accent colour, as big as fits beside the
	 * first row's label (up to a tenth of the height) */
	private Paint focus_read_p = null;
	private Paint focus_readout_paint(String s) {
		if (focus_read_p == null) {
			focus_read_p = new Paint(pianoname_p);
			focus_read_p.setColor(
				ui.getResources().getColor(R.color.accent));
			focus_read_p.setShadowLayer(4.0f, 0.0f, 0.0f, 0xff000000);
		}
		float hi = height/10.0f;
		float avail = width - right_margin();
		if ((label_p != null) && (labels != null) && (labels.length > 0)) {
			/* the label as drawn (translated), a gap of one
			 * character, and no taller than 3/5 of the row, so
			 * that it stays in the first row */
			String[] shown = shown_labels();
			avail -= label_p.measureText(shown[0]) +
					label_p.getTextSize();
			hi = Math.min(hi, 0.6f*height/labels.length);
		}
		/* fake bold and shadow widen it beyond measureText() */
		avail -= 0.06f*hi + 4.0f;
		focus_read_p.setTextSize(hi);
		float w = focus_read_p.measureText(s);
		float sz = ((w > avail) && (w > 0.0f) && (avail > 0.0f)) ?
						(hi*avail/w) : hi;
		float lo = Math.min(hi, (label_p != null) ?
				label_p.getTextSize() : height/24.0f);
		focus_read_p.setTextSize(Math.max(lo, sz));
		return focus_read_p;
	}

	private void draw_all(Canvas c, int[] bitmap, int w, int h) {
		if ((c.getWidth() == w) && (c.getHeight() == h)) {
			c.drawBitmap(bitmap, /*offset=*/0, /*stride=*/w,
				/*x=*/0, /*y=*/0, /*wid=*/w, /*hght=*/h,
				/*hasAlpha=*/true, /*paint=*/null);
			draw_labels(c);
			if (paused) {
				draw_paused(c);
			}
			draw_pianoname(c);
		}
	}

	/* Called from JNI to handle all of the Canvas interactions to draw the
	 * bitmap.  Returns true iff the thread is interrupted and should exit.
	 */
	private boolean do_draw(int[] bitmap, int w, int h) {
		if (time_to_die()) {
			return true;
		}
		synchronized(lock) {
			if (holder != null) {
				Canvas c = holder.lockCanvas();
				if (c != null) {
					try {
						draw_all(c, bitmap, w, h);
					} finally {
						holder.unlockCanvasAndPost(c);
					}
				}
			}
		}
		return false;
	}

	public void run() {
		try {
			sem.acquire();
		} catch (Exception e) {
			synchronized(lock) {
				if (curr_thread == this) {
					curr_thread = null;
				}
			}
			return;
		}
		try {
			Listener.start_recording();
			runner();
		} finally {
			Listener.stop_recording();
			synchronized(lock) {
				if (curr_thread == this) {
					curr_thread = null;
				}
			}
			sem.release();
		}
	}

	private boolean time_to_die() {
		if (isInterrupted()) {
			return true;
		}
		synchronized(lock) {
			if (curr_thread != this) {
				/* a new thread has been started, we'd better
				 * die toot sweet! */
				return true;
			}
		}
		return false;
	}

	static {
		System.loadLibrary("strobe-jni");
	}
}
