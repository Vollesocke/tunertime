package org.galexander.tunertime;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.util.Arrays;

/* length mode (organ mode, in place of FFT): how much the sounding pipe is
 * too long or too short for the target note.
 *
 * A flue pipe sounds at f = c/(2*(L+e)) when open, c/(4*(L+e)) when
 * stopped, with L the speaking length and e the end correction, which
 * depends on the diameter but not on L.  So the length to cut or add is
 *	dL = c/(n*f_actual) - c/(n*f_target),   n = 2 open, 4 stopped
 * whatever e is; positive means too long (flat).  c follows the
 * temperature, c = 331.3*sqrt(1+T/273.15) m/s; a degree off moves dL by
 * less than 0.2%.  Only dL is shown, as the length to cut off or to add.
 * The temperature setting only scales c; it does not move the target.
 * The pipes' pitch changes with the air temperature (about 3 c, or
 * 0.7 Hz at a = 440, per degree), and the tuner allows for that by
 * hand through the A frequency, as decided with the organ tuner: no
 * automatic temperature compensation anywhere.
 * Stopped pipes get the same "too long: cut off / too short: lengthen"
 * wording as open ones (also decided with the tuner): length mode is for
 * setting up a pipe's range, not for fine tuning at the cap.
 *
 * With a strobe row focused, the estimate is that row's drift over the last
 * WINDOW ms that had sound (Strobe.focus_drift): it follows exactly the
 * focused partial, against exactly the target, and resolves far below a
 * cent.  The drift is unambiguous only up to about DRIFT_RANGE cents, so
 * when the detector clearly hears the pipe further out, the detector wins.
 *
 * Otherwise it is the median of the detector's pitches over the last
 * WINDOW ms that had sound; the spread (median absolute deviation) tells
 * whether it has settled.  A pipe is not an octave out, so the detector's
 * pitch is compared with the nearest octave of the target; a stopped pipe
 * has hardly any even partials, and the detector may take its twelfth or
 * seventeenth (3rd, 5th partial), so those are folded too.  Whatever is
 * still more than UNCLEAR_CENTS off is shown as unclear, not as a length.
 * Both are held after the pipe stops. */
public class pipelength {
	private static final long WINDOW = 1000;	/* ms */
	private static final int MAX_EST = 64;
	private static final double STABLE_CENTS = 1.0;
	private static final int MIN_EST = 5;
	private static final double UNCLEAR_CENTS = 150.0;
		/* the length is shown in 0.01 mm when a cent is less than
		 * MM_PER_CENT_FINE mm of the pipe (about C6 open, C5 stopped
		 * and up), in 1 mm when it is MM_PER_CENT_COARSE or more
		 * (about D2 open, D1 stopped and down), in 0.1 mm between
		 * (tools/measure/FINDINGS.md Q4) */
	private static final double MM_PER_CENT_FINE = 0.1;
	private static final double MM_PER_CENT_COARSE = 1.0;
		/* "in tune" within this, whatever the length rounds to */
	private static final double IN_TUNE_CENTS = 0.2;
		/* drift: the strobe pattern steps by up to half a stripe per
		 * block of 8 periods (jni/strobe.c DRIFT_MAX_PERIODS), i.e.
		 * up to freq/16 Hz or 105 cents; trusted a bit inside that */
	private static final double DRIFT_RANGE = 90.0;
	private static final double DRIFT_MIN_SECS = 0.3;	/* usable */
	private static final int MAX_DRIFT = 256;	/* buffers */
		/* partials the detector may take for the pipe's pitch */
	private static final int[] OPEN_PARTIALS = { 1 };
	private static final int[] STOPPED_PARTIALS = { 1, 3, 5 };

	private final TunerTime ui;
	private final TextView main_display;
	private final Button kind_button;

	private final double[] hz = new double[MAX_EST];
	private final long[] when = new long[MAX_EST];
	private int n_est = 0;
	private int last_target = -1;
	private double last_focus = 0.0d;

	pipelength(TunerTime ui_, View panel) {
		ui = ui_;
		main_display = (TextView)panel.findViewById(R.id.length_display);
		kind_button = (Button)panel.findViewById(R.id.pipe_kind_button);
		kind_button.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				parms.pipe_stopped = !parms.pipe_stopped;
				EventLog.ui("length: " + (parms.pipe_stopped ?
						"stopped" : "open") + " pipe");
				update();
			} });
	}

	public void clear() {
		n_est = 0;
		clear_drift();
	}

	/* the focused row's drift, one entry per buffer with usable
	 * signal; fed from the strobe thread, read on the UI thread */
	private final double[] d_cycles = new double[MAX_DRIFT];
	private final double[] d_secs = new double[MAX_DRIFT];
	private final long[] d_when = new long[MAX_DRIFT];
	private int d_n = 0, d_head = 0;	/* d_head: next to write */
	private double d_freq = 0.0d;

	private synchronized void clear_drift() {
		d_n = 0;
	}

	/* Strobe.focus_drift: the focused partial gained cycles on the row
	 * frequency freq during secs of usable signal */
	public synchronized void add_drift(double freq, double cycles,
						double secs) {
		if (freq != d_freq) {
			d_freq = freq;
			d_n = 0;
		}
		if (secs <= 0.0d) {
			return;		/* silence: the reading is held */
		}
		d_cycles[d_head] = cycles;
		d_secs[d_head] = secs;
		d_when[d_head] = System.currentTimeMillis();
		d_head = (d_head+1) % MAX_DRIFT;
		d_n = Math.min(d_n+1, MAX_DRIFT);
	}

	/* the drift over the WINDOW up to the latest usable buffer, if it is
	 * for target frequency f: { cents, spread, time of the latest }, or
	 * null.  the spread is half the difference of the window's halves */
	private synchronized double[] drift_reading(double f) {
		if ((d_n == 0) || (Math.abs(d_freq - f) > 1e-6*f)) {
			return null;
		}
		int last = (d_head - 1 + MAX_DRIFT) % MAX_DRIFT;
		long latest = d_when[last];
		double c0 = 0.0d, s0 = 0.0d, c1 = 0.0d, s1 = 0.0d;
		for (int k = 0; k < d_n; k++) {
			int i = (last - k + MAX_DRIFT) % MAX_DRIFT;
			long age = latest - d_when[i];
			if (age > WINDOW) {
				break;
			}
			if (age > WINDOW/2) {
				c0 += d_cycles[i];
				s0 += d_secs[i];
			} else {
				c1 += d_cycles[i];
				s1 += d_secs[i];
			}
		}
		if (s0 + s1 < DRIFT_MIN_SECS) {
			return null;
		}
		double spread = ((s0 > 0.0d) && (s1 > 0.0d)) ?
			0.5d*Math.abs(drift_cents(f, c0, s0) -
				      drift_cents(f, c1, s1)) :
			Double.POSITIVE_INFINITY;
		return new double[] { drift_cents(f, c0+c1, s0+s1), spread,
					(double)latest };
	}

	private static double drift_cents(double f, double cycles, double secs) {
		return 1200.0d * Math.log((f + cycles/secs)/f) / Math.log(2.0d);
	}

	/* one estimate from the detector; hz <= 0 is silence */
	public void add(double f) {
		if (f > 0.0) {
			if (n_est == MAX_EST) {
				System.arraycopy(hz, 1, hz, 0, MAX_EST-1);
				System.arraycopy(when, 1, when, 0, MAX_EST-1);
				n_est--;
			}
			hz[n_est] = f;
			when[n_est] = System.currentTimeMillis();
			n_est++;
		}
		update();
	}

	public static double speed_of_sound(double celsius) {
		return 331.3d * Math.sqrt(1.0d + celsius/273.15d);
	}

	private static double median(double[] v, int n) {
		Arrays.sort(v, 0, n);
		return ((n%2) == 1) ? v[n/2] : 0.5d*(v[n/2-1]+v[n/2]);
	}

	private static String fmt(String f, double v) {
		return String.format(java.util.Locale.US, f, v);
	}

	public void update() {
		kind_button.setText(parms.pipe_stopped ? R.string.pipe_stopped
						       : R.string.pipe_open);

		if ((parms.strobe_cents != last_target) ||
		    (parms.focus_ratio != last_focus)) {
			/* a new note or rank: earlier pitches belong to
			 * another pipe */
			last_target = parms.strobe_cents;
			last_focus = parms.focus_ratio;
			n_est = 0;
			clear_drift();
		}
		/* the note's pipe; a focused partial (4', 2 2/3', ...)
		 * stands for its own rank */
		double f_note = ui.cents_to_freq(parms.strobe_cents, 1);
		double f_base = f_note;
		if (parms.focus_ratio > 0.0d) {
			f_base *= parms.focus_ratio;
		}

		/* pitches in the WINDOW up to the latest, as cents off f_base.
		 * with a row focused the detector gives the focused
		 * partial divided by its ratio (jni/pitch.h), i.e. the note
		 * it stands for, so that is compared with the note: hz/f_note
		 * is the focused pipe's pitch over f_base.  (Compared with
		 * f_base itself, a 4' rank came out as the 8' pipe an octave
		 * down, twice the length, and a quint rank as "unclear".) */
		double[] c = new double[MAX_EST];
		int n = 0;
		for (int i = n_est-1; i >= 0; i--) {
			if (when[n_est-1] - when[i] > WINDOW) {
				break;
			}
			c[n++] = 1200.0d * Math.log(hz[i]/f_note) / Math.log(2.0d);
		}
		double det = Double.NaN, det_spread = 0.0d;
		int det_oct = 0, det_part = 1;
		if (n > 0) {
			double med = median(c, n);
			double[] dev = new double[n];
			for (int i = 0; i < n; i++) {
				dev[i] = Math.abs(c[i]-med);
			}
			det_spread = median(dev, n);
			/* the nearest octave of the target, or of its
			 * twelfth or seventeenth for a stopped pipe */
			int[] parts = parms.pipe_stopped ? STOPPED_PARTIALS
							 : OPEN_PARTIALS;
			for (int p : parts) {
				double m = med - 1200.0d*Math.log(p)/Math.log(2.0d);
				int o = (int)Math.round(m/1200.0d);
				double r = m - 1200.0d*o;
				if (Double.isNaN(det) ||
				    (Math.abs(r) < Math.abs(det))) {
					det = r;
					det_oct = o;
					det_part = p;
				}
			}
		}
		/* the focused row's drift is measured against f_base itself */
		double[] dr = (parms.focus_row() >= 0) ? drift_reading(f_base)
						       : null;
		boolean use_drift = (dr != null) &&
			(Math.abs(dr[0]) < DRIFT_RANGE) &&
			(Double.isNaN(det) || (Math.abs(det) < DRIFT_RANGE) ||
			 (Math.abs(det - dr[0]) < DRIFT_RANGE/2.0d));

		double cents, spread;
		long t;
		int oct = 0, part = 1;
		boolean stable, unclear;
		if (use_drift) {
			cents = dr[0];
			spread = dr[1];
			t = (long)dr[2];
			stable = (spread < STABLE_CENTS);
			unclear = false;
		} else if (!Double.isNaN(det)) {
			cents = det;
			spread = det_spread;
			t = when[n_est-1];
			oct = det_oct;
			part = det_part;
			stable = (n >= MIN_EST) && (spread < STABLE_CENTS);
			unclear = (Math.abs(cents) > UNCLEAR_CENTS);
		} else if (dr != null) {
			/* only a drift beyond its range: could be aliased */
			cents = dr[0];
			spread = dr[1];
			t = (long)dr[2];
			stable = false;
			unclear = true;
		} else {
			main_display.setText(R.string.length_listening);
			main_display.setTextColor(color(R.color.text_muted));
			return;
		}
		boolean recent = (System.currentTimeMillis() - t < WINDOW);

		if (unclear) {
			/* the detector heard something that is no partial
			 * of the target: no length for that */
			main_display.setText(recent
				? ui.getString(R.string.length_unclear)
				: ui.getString(R.string.length_held,
					ui.getString(R.string.length_unclear)));
			main_display.setTextColor(color(!recent ?
					R.color.text_muted : R.color.warn));
			return;
		}

		/* the pipe: the partial heard divided out, compared with the
		 * nearest octave of the target */
		double f_ref = f_base * Math.pow(2.0d, oct);
		double f_act = f_ref * Math.pow(2.0d, cents/1200.0d);

		double div = parms.pipe_stopped ? 4.0d : 2.0d;
		double cs = speed_of_sound(18.0d);
		double len_ref = 1000.0d * cs/(div*f_ref);	/* mm */
		double mm = 1000.0d * cs/(div*f_act) - len_ref;

		/* the step shown follows the pipe: a cent is
		 * len_ref*ln2/1200 mm (6 mm on a 32' C, 0.01 mm at the top),
		 * and "in tune" is a pitch, not a rounded length */
		double mm_per_cent = len_ref * Math.log(2.0d)/1200.0d;
		int dec = (mm_per_cent < MM_PER_CENT_FINE) ? 2 :
			  (mm_per_cent < MM_PER_CENT_COARSE) ? 1 : 0;
		boolean in_tune = (Math.abs(cents) < IN_TUNE_CENTS);
		String amm = fmt("%." + dec + "f", in_tune ? 0.0d : Math.abs(mm));
		while (!in_tune && (dec < 3) && (Double.parseDouble(amm) == 0.0d)) {
			/* out of tune but below the step: one more digit */
			dec++;
			amm = fmt("%." + dec + "f", Math.abs(mm));
		}
		String s;
		String sign = ui.getString(in_tune ? R.string.length_sign_in_tune
				: (mm > 0.0d) ? R.string.length_sign_long
					      : R.string.length_sign_short);
		s = ui.getString(R.string.length_mm_compact, sign, amm,
				ui.getString(parms.pipe_stopped
					? R.string.length_ref_stopped
					: R.string.length_ref_open));
		if (!recent) {
			s = ui.getString(R.string.length_held, s);
		}
		/* only the length to cut off or to add (percentage, cents,
		 * spread and acoustic length are left out on purpose); the
		 * colour says whether it has settled */
		main_display.setText(s);
		main_display.setTextColor(color(!recent ? R.color.text_muted :
				stable ? R.color.accent : R.color.warn));
	}

	private int color(int id) {
		return ui.getResources().getColor(id);
	}
}
