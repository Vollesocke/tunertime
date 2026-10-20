package org.galexander.tunertime;

import java.util.Locale;

/* EventLog - what the app did while a debug recording (DebugRecorder) was
 * being taken, written into its .txt as a timeline on the WAV's own time
 * base, so that it can be read side by side with a replay of the WAV
 * through the detector (tools/test_pitch -t).
 *
 * A ring of the last MAX events, each stamped with its position in the
 * microphone input (samples fed since the app started, jni/record.c):
 *  - est: every detector estimate (strobe thread), at the sample its
 *    analysed window ended on, with its confidence; Hz 0 = nothing clear,
 *  - drift: the focused row's strobe drift in each audio buffer that had
 *    usable signal (strobe thread), as cents against the row's frequency,
 *  - ui: what the UI did and why (UI thread, TunerTime.log_changes() and
 *    others): note changes with their cause (follow or which button),
 *    auto follow/lock/off and continuity, focus, tabs, mixture, length
 *    mode, A, stretch, presets, microphone switches.  These are stamped
 *    when the app handled them; the audio input latency is not taken off.
 * Fixed arrays: nothing is allocated per audio buffer, only the ui
 * events carry a String.  Kept only while debug recordings are on. */
public class EventLog {
	private static final int MAX = 8192;
	private static final byte EST = 1, DRIFT = 2, UI = 3, HINT = 4;
	private static final long[] pos = new long[MAX];
	private static final byte[] kind = new byte[MAX];
	private static final double[] va = new double[MAX];
	private static final double[] vb = new double[MAX];
	private static final double[] vc = new double[MAX];
	private static final String[] text = new String[MAX];
	private static int head = 0, count = 0;	/* head: next to write */
	/* the detector hints in effect before the oldest event kept: the
	 * native side logs a hint only when it changes, so the one in effect
	 * at a recording's start may be older than the log (or than 'on') */
	private static double base_focus = -1.0d, base_expect = 0.0d;

	/* DebugRecorder.enabled(), set by TunerTime.onResume() */
	public static volatile boolean on = false;
	/* the app's state as last logged (TunerTime.log_changes()) */
	public static volatile String state = "?";

	/* the position in the input now (jni/record.c), for events off the
	 * strobe thread */
	private static native long sample_pos();

	/* strobe thread */
	public static void estimate(long p, double hz, double conf) {
		if (on) {
			add(p, EST, hz, conf, 0.0d, null);
		}
	}

	/* strobe thread: the row of footage ratio r at freq gained cycles
	 * during secs of usable signal */
	public static void drift(long p, double r, double freq, double cycles,
				double secs) {
		if (on && (secs > 0.0d) && (freq > 0.0d)) {
			double c = 1200.0d * Math.log((freq + cycles/secs)/freq) /
					Math.log(2.0d);
			add(p, DRIFT, r, c, secs, null);
		}
	}

	/* strobe thread: the exact detector hints used for this audio buffer */
	public static synchronized void hint(long p, double focus,
						double expect) {
		if (on) {
			add(p, HINT, focus, expect, 0.0d, null);
		} else {
			/* nothing is recorded meanwhile: start the log afresh,
			 * from this hint */
			head = count = 0;
			base_focus = focus;
			base_expect = expect;
		}
	}

	/* any thread but the strobe thread */
	public static void ui(String s) {
		if (!on) {
			return;
		}
		long p;
		try {
			p = sample_pos();
		} catch (UnsatisfiedLinkError e) {
			return;
		}
		add(p, UI, 0.0d, 0.0d, 0.0d, s);
	}

	private static synchronized void add(long p, byte k, double a,
					double b, double c, String s) {
		if ((count == MAX) && (kind[head] == HINT)) {
			base_focus = va[head];	/* overwritten: still in effect */
			base_expect = vb[head];
		}
		pos[head] = p;
		kind[head] = k;
		va[head] = a;
		vb[head] = b;
		vc[head] = c;
		text[head] = s;
		head = (head + 1) % MAX;
		count = Math.min(count + 1, MAX);
	}

	public static final String HEADER =
		"# timeline: seconds from the start of the WAV (sample/rate), " +
			"the sample, what\n" +
		"#  est   detector estimate: Hz, nearest note (A4 = 440 Hz) " +
			"and cents, confidence\n" +
		"#        (as tools/test_pitch -t prints them)\n" +
		"#  drift the focused row's strobe drift in one audio buffer: " +
			"row, cents, seconds of usable signal\n" +
		"#  hint  detector hints for following samples: focus ratio, " +
			"expected note in Hz\n" +
		"#  ui    what the app did and why (stamped when it was " +
			"handled, input latency not taken off)\n";

	/* the events from sample start to end, one line each */
	public static synchronized String timeline(long start, long end,
							int rate) {
		StringBuilder sb = new StringBuilder();
		int first = (head - count + MAX) % MAX;
		/* the hints in effect at the start, as the first line, so
		 * that test_pitch --hints replays from the right ones */
		double f = base_focus, e = base_expect;
		for (int j = 0; j < count; j++) {
			int i = (first + j) % MAX;
			if ((kind[i] == HINT) && (pos[i] < start)) {
				f = va[i];
				e = vb[i];
			}
		}
		if (f >= 0.0d) {
			sb.append(String.format(Locale.US, "%9.5f %8d  " +
					"hint %.4f %.3f\n", 0.0d, 0L, f, e));
		}
		for (int j = 0; j < count; j++) {
			int i = (first + j) % MAX;
			long p = pos[i] - start;
			if ((pos[i] < start) || (pos[i] > end)) {
				continue;
			}
			sb.append(String.format(Locale.US, "%9.5f %8d  ",
					(double)p/rate, p));
			if (kind[i] == EST) {
				if (va[i] > 0.0d) {
					sb.append(String.format(Locale.US,
						"est   %8.2f  %s  %.2f\n",
						va[i], name(va[i]), vb[i]));
				} else {
					sb.append(String.format(Locale.US,
						"est          -  -              " +
						"%.2f\n", vb[i]));
				}
			} else if (kind[i] == DRIFT) {
				sb.append(String.format(Locale.US,
					"drift %s %+.2fc %.3fs\n",
					parms.footage_str(va[i]).trim(), vb[i],
					vc[i]));
			} else if (kind[i] == HINT) {
				sb.append(String.format(Locale.US,
					"hint %.4f %.3f\n", va[i], vb[i]));
			} else {
				sb.append("ui    ").append(text[i]).append('\n');
			}
		}
		return sb.toString();
	}

	/* the first estimate from sample start on, in seconds from start,
	 * or -1: for test_pitch -t, which lines its analyses up with it */
	public static synchronized double first_estimate(long start,
							long end, int rate) {
		int first = (head - count + MAX) % MAX;
		for (int j = 0; j < count; j++) {
			int i = (first + j) % MAX;
			if ((kind[i] == EST) && (pos[i] >= start) &&
			    (pos[i] <= end)) {
				return (double)(pos[i] - start)/rate;
			}
		}
		return -1.0d;
	}

	private static final String[] NAMES = { "A", "A#", "B", "C", "C#",
			"D", "D#", "E", "F", "F#", "G", "G#" };

	/* like tools/test_pitch.c: "C4   +0.4" against A4 = 440 Hz */
	private static String name(double hz) {
		double c = 1200.0d * Math.log(hz/440.0d) / Math.log(2.0d);
		int s = (int)Math.round(c/100.0d);
		int k = s + 48;		/* A0 = 0 */
		String n = NAMES[((k % 12) + 12) % 12] +
			(int)Math.floor((k + 9)/12.0d);
		return String.format(Locale.US, "%-5s %+6.1f", n, c - 100.0d*s);
	}

	static {
		System.loadLibrary("strobe-jni");
	}
}
