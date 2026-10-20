package org.galexander.tunertime;

/* mixture mode (organ mode): no target pitch is assumed.  the focused
 * partial's pitch is averaged over MEASURE_SECONDS (MEASURE_SECONDS_LOW
 * on a row below 8'), and the target moves there, so the strobes stand
 * still.
 *
 * the measurement is the drift of the focused strobe row (jni/strobe.c):
 * how many cycles the partial gains on the row frequency, counted only
 * while the row hears enough signal.  cycles over seconds is then the
 * average offset in Hz, exactly what makes the strobe pattern move.
 * fed from the strobe thread, read from the UI thread */
public class mixture {
		/* seconds of usable signal to average over */
	public static final double MEASURE_SECONDS = 2.0d;
		/* ... when the row measured on is below 8' (focus ratio < 1:
		 * 16', 10 2/3', 32'), whose partial makes few cycles in 2 s
		 * and whose drift scatters more.  only usable seconds count,
		 * so the settling after an onset (jni/strobe.c) just takes
		 * longer, there is no timeout */
	public static final double MEASURE_SECONDS_LOW = 3.0d;
		/* after a change of microphone, usable seconds ignored: the
		 * drift blocks (jni/strobe.c) that straddle the change */
	private static final double SWITCH_SKIP = 0.25d;
		/* a measurement that follow mode starts at a new key skips
		 * this much of the onset: tools/sim_organ.c -m trail, 408
		 * keys, 29 off by more than 0.5 c without, 9 with it, for
		 * about 0.2 s */
	public static final double ONSET_SKIP = 0.5d;
		/* the trailing average (Onno, 06f3337) ends when
		 * STABLE_BUFFERS results in a row agree within STABLE_CENTS,
		 * or after measure_seconds() at the latest.  sim_organ -m
		 * trail against the fixed 2 s / 3 s average: done 1.6 s
		 * after the key instead of 2.5 s (median), error median
		 * 0.05 c instead of 0.03, 9 keys off by > 0.5 c instead of 7 */
	private static final double STABLE_CENTS = 0.15d;
	private static final int STABLE_BUFFERS = 3;
		/* buffers in the window: 3 s of buffers of 128 samples at
		 * 48 kHz, with room to spare */
	private static final int MAX_WINDOW = 2048;
		/* the swing (hi-lo)/(hi+lo) of the drift's amplitude over a
		 * measurement (jni/strobe.c strobe_drift_amp) above which the
		 * partial is taken to beat: a second pipe sounds on it
		 * (celeste, a rank not muted), and the result is the louder
		 * one's pitch or somewhere between.  tools/sim_organ.c: two
		 * pipes of equal level flagged 5 of 5, single pipes about 1
		 * false flag in 210 (the reverberant treble) */
	public static final double BEAT_SWING = 0.7d;
		/* the key released before the measurement is done (a 3 s
		 * bass measurement, above all): the row then hears the
		 * release and the reverberation, which goes on with no
		 * pipe behind it.  taken as released when the drift's
		 * amplitude stays below RELEASE_DROP of its mean over the
		 * measurement for RELEASE_SECS, or no usable block comes
		 * for RELEASE_GAP (dry: the tail falls below jni/strobe.c's
		 * gate at once; longer than its settling after an onset,
		 * 0.8 s at 16' C).  what came before the drop is the
		 * result if it is RELEASE_MIN of the window at least, else
		 * the measurement is discarded.  while low, it does not end
		 * (that would be on the tail).  RELEASE_SECS is longer than
		 * the dips of a slow beat: 0.1 s discarded 3 of 6 keys of
		 * a celeste beating at 0.6 Hz.  tools/sim_organ.c -r off
		 * against this, keys held 3 s (-DPEDAL_HOLD=3 -DHOLD_32=3):
		 * the last key of each 16' run, released into silence, off
		 * by 1.3 c and flagged as beating, now by 0.25 c and not
		 * flagged; 32' keys measured at all; with the keys held as
		 * long as needed no change, but for 4 keys whose row heard
		 * only the reverberation of the key before, which now are
		 * discarded instead of never finishing */
	private static final double RELEASE_DROP = 0.3d;
	private static final double RELEASE_SECS = 0.5d;
	private static final double RELEASE_GAP = 1.0d;
	private static final double RELEASE_MIN = 0.5d;
		/* the drift is unambiguous up to half a cycle per block of at
		 * most 8 periods (jni/strobe.c DRIFT_MAX_PERIODS), i.e. about
		 * a semitone: a pipe further from its row than that wraps,
		 * and the measurement comes out a wrong amount.  warned of
		 * when FAR_AGREE detector estimates in a row are more than
		 * FAR_CENTS from every row (octaves folded), and cleared by
		 * as many within */
	public static final double FAR_CENTS = 100.0d;
	private static final int FAR_AGREE = 4;

		/* a measurement has begun to collect.  a change of
		 * microphone jumps the phase, so MicSelect holds its switches
		 * meanwhile.  not before: while nothing is heard yet a switch
		 * costs nothing, and the mic in use may be the one that
		 * hears nothing */
	public static volatile boolean busy = false;

	private boolean active = false;
	private double freq = 0.0d;		/* row frequency measured on */
	private double cycles = 0.0d, secs = 0.0d, elapsed = 0.0d;
	private final double[] cycle_buf = new double[MAX_WINDOW];
	private final double[] sec_buf = new double[MAX_WINDOW];
	private int head = 0, count = 0;
	private double window = MEASURE_SECONDS, previous_cents = Double.NaN;
	private int stable_count = 0;
	private int gen = 0;			/* Listener.buffer_gen() */
	private double skip = 0.0d;
	private double need = MEASURE_SECONDS;	/* for the focused row */
	private double amp_lo = 0.0d, amp_hi = 0.0d;	/* over the measurement */
	private boolean beating = false;	/* the last result's swing */
		/* release: the mean amplitude (sums over usable seconds),
		 * how long it has been low or without a usable block, and
		 * the window as it was at the last buffer before the drop */
	private double amp_sum = 0.0d, amp_secs = 0.0d;
	private double low_secs = 0.0d, gap_secs = 0.0d;
	private boolean low = false;
	private double keep_cycles = 0.0d, keep_secs = 0.0d;
	private double keep_lo = 0.0d, keep_hi = 0.0d;
	private boolean released = false;	/* the last one discarded */
	private boolean released_new = false;	/* ... not yet logged */
	private boolean cut = false;		/* the last result cut short */
	private boolean far = false;		/* FAR_CENTS warning */
	private int far_count = 0, far_target = -1;

		/* the usable seconds a measurement on the row of focus
		 * ratio r takes */
	public static double measure_seconds(double r) {
		return ((r > 0.0d) && (r < 1.0d - 1e-6)) ? MEASURE_SECONDS_LOW
							 : MEASURE_SECONDS;
	}

	/* A trailing average follows high pipes quickly, while the lower rows
	 * keep the longer window that makes their sparse drift reliable. */
	private static double window_seconds(double f) {
		return (f >= 500.0d) ? 0.65d : (f >= 250.0d) ? 1.0d :
			measure_seconds(parms.focus_ratio);
	}

	private void clear_window() {
		cycles = secs = elapsed = 0.0d;
		head = count = stable_count = 0;
		previous_cents = Double.NaN;
		amp_sum = amp_secs = low_secs = gap_secs = 0.0d;
		low = false;
		keep_cycles = keep_secs = keep_lo = keep_hi = 0.0d;
	}

	public synchronized void start() {
		start(0.0d);
	}

		/* ... with the first 'settle' usable seconds ignored: started
		 * by follow mode at a key's onset, whose speech and chiff
		 * would otherwise go into the average */
	public synchronized void start(double settle) {
		active = true;
		busy = false;
		freq = 0.0d;
		clear_window();
		gen = Listener.buffer_gen();
		skip = settle;
		need = measure_seconds(parms.focus_ratio);
		window = need;
		amp_lo = amp_hi = 0.0d;
		beating = false;
		released = released_new = cut = false;
	}

	public synchronized void stop() {
		active = false;
		busy = false;
		released = released_new = false;
	}

	public synchronized boolean measuring() {
		return active;
	}

		/* 0..1 */
	public synchronized double progress() {
		return Math.min(elapsed / need, 1.0d);	/* secs: the window */
	}

		/* the last measurement's partial beat (BEAT_SWING); its
		 * result stands, this only flags it.  false while measuring */
	public synchronized boolean beating() {
		return beating;
	}

		/* the last measurement was discarded: the key was released
		 * before RELEASE_MIN of it was in */
	public synchronized boolean released() {
		return released;
	}
		/* ... once for each, for the debug log */
	public synchronized boolean take_released() {
		boolean r = released_new;
		released_new = false;
		return r;
	}
		/* the last result was cut short by the key's release */
	public synchronized boolean cut() {
		return cut;
	}

		/* one buffer's drift of the focused row (Strobe.focus_drift),
		 * the buffer's length buf_secs, and the mean amplitude of its
		 * usable blocks (0 if none).  returns the partial's offset in
		 * cents once measure_seconds() are in, which ends the
		 * measurement; NaN until then, and when it ends discarded
		 * (released()) */
	public synchronized double feed(double freq_, double cycles_,
					double secs_, double buf_secs,
					double amp) {
		if (!active || (freq_ <= 0.0d)) {
			return Double.NaN;
		}
		int g = Listener.buffer_gen();
		if (g != gen) {
			/* the microphone changed anyway (it can't always
			 * wait): start over, past the jump */
			gen = g;
			freq = freq_;
			clear_window();
			amp_lo = amp_hi = 0.0d;
			skip = SWITCH_SKIP;
			window = window_seconds(freq_);
			need = measure_seconds(parms.focus_ratio);
			busy = false;
			return Double.NaN;
		}
		if (skip > 0.0d) {
			skip -= secs_;
			return Double.NaN;
		}
		if (freq_ != freq) {
			/* other row, or the target moved: start over */
			freq = freq_;
			clear_window();
			need = measure_seconds(parms.focus_ratio);
			window = window_seconds(freq_);
			amp_lo = amp_hi = 0.0d;
		}
		if ((count > 0) && release_heard(secs_, buf_secs, amp)) {
			return release();
		}
		if (secs_ <= 0.0d) return Double.NaN;
		elapsed += secs_;
		amp_sum += amp*secs_;
		amp_secs += secs_;
		if (count == MAX_WINDOW) {
			/* full: the oldest leaves the window, and the sums */
			cycles -= cycle_buf[head];
			secs -= sec_buf[head];
			count--;
		}
		cycle_buf[head] = cycles_;
		sec_buf[head] = secs_;
		head = (head + 1) % MAX_WINDOW;
		count++;
		cycles += cycles_;
		secs += secs_;
		while ((secs > window) && (count > 1)) {
			int oldest = (head - count + MAX_WINDOW) % MAX_WINDOW;
			double excess = secs - window;
			if (sec_buf[oldest] <= excess) {
				secs -= sec_buf[oldest];
				cycles -= cycle_buf[oldest];
				count--;
			} else {
				double keep = (sec_buf[oldest] - excess) / sec_buf[oldest];
				secs -= excess;
				cycles -= cycle_buf[oldest] * (1.0d - keep);
				sec_buf[oldest] *= keep;
				cycle_buf[oldest] *= keep;
				break;
			}
		}
		if (amp > 0.0d) {
			if ((amp_lo == 0.0d) || (amp < amp_lo)) {
				amp_lo = amp;
			}
			if (amp > amp_hi) {
				amp_hi = amp;
			}
		}
		if (!low) {
			/* what a release would leave */
			keep_cycles = cycles;
			keep_secs = secs;
			keep_lo = amp_lo;
			keep_hi = amp_hi;
		}
		busy = (secs > 0.0d);
		if (secs < window) return Double.NaN;
		double cents = 1200.0d * Math.log((freq + cycles/secs)/freq) /
				Math.log(2.0d);
		if (!Double.isNaN(previous_cents) &&
		    Math.abs(cents - previous_cents) < STABLE_CENTS) {
			stable_count++;
		} else {
			stable_count = 0;
		}
		previous_cents = cents;
		if (((stable_count < STABLE_BUFFERS) && (elapsed < need)) ||
		    low) {
			/* low: perhaps the tail (release_heard) */
			return Double.NaN;
		}
		active = false;
		busy = false;
		beating = (amp_hi > 0.0d) &&
			((amp_hi - amp_lo)/(amp_hi + amp_lo) > BEAT_SWING);
		return cents;
	}

		/* a pitch estimate hz (> 0) in mixture mode, against the
		 * target (parms.strobe_cents) of 8' frequency f8; hinted:
		 * the detector reports the focused rank's key (the 8').
		 * true when the warning changed */
	public synchronized boolean heard(double hz, double f8, int target,
					  boolean hinted) {
		boolean was = far;
		if (target != far_target) {
			/* another key: judged afresh */
			far_target = target;
			far = false;
			far_count = 0;
		}
		double d = Double.MAX_VALUE;
		for (int i = 0; i < (hinted ? 1 : parms.num_strobes); i++) {
			double r = hinted ? 1.0d : parms.row_ratio(i);
			double c = 1200.0d * Math.log(hz/(f8*r)) /
					Math.log(2.0d);
			c -= 1200.0d * Math.floor(c/1200.0d + 0.5d);
			d = Math.min(d, Math.abs(c));
		}
		if ((d > FAR_CENTS) != far) {
			if (++far_count >= FAR_AGREE) {
				far = !far;
				far_count = 0;
			}
		} else {
			far_count = 0;
		}
		return far != was;
	}
		/* the warning, for this target */
	public synchronized boolean far(int target) {
		return far && (target == far_target);
	}

		/* a buffer once something is collected: true when the key is
		 * taken to be released (RELEASE_DROP) */
	private boolean release_heard(double secs_, double buf_secs,
				      double amp) {
		if (secs_ <= 0.0d) {
			/* no usable block ended in it: the low state goes
			 * on (blocks are up to 0.1 s, buffers a few ms) */
			gap_secs += buf_secs;
		} else {
			gap_secs = 0.0d;
			low = (amp < RELEASE_DROP*amp_sum/amp_secs);
		}
		low_secs = low ? (low_secs + buf_secs) : 0.0d;
		return (low_secs >= RELEASE_SECS) || (gap_secs >= RELEASE_GAP);
	}

		/* the key was released: the result from what came before
		 * the drop, or none */
	private double release() {
		active = false;
		busy = false;
		if (keep_secs < RELEASE_MIN*window) {
			released = released_new = true;
			return Double.NaN;
		}
		cut = true;
		beating = (keep_hi > 0.0d) &&
			((keep_hi - keep_lo)/(keep_hi + keep_lo) > BEAT_SWING);
		return 1200.0d * Math.log((freq + keep_cycles/keep_secs)/freq) /
				Math.log(2.0d);
	}
}
