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
		 * this much of the onset (tools/sim_organ.c MSTART) */
	public static final double ONSET_SKIP = 0.5d;
		/* the swing (hi-lo)/(hi+lo) of the drift's amplitude over a
		 * measurement (jni/strobe.c strobe_drift_amp) above which the
		 * partial is taken to beat: a second pipe sounds on it
		 * (celeste, a rank not muted), and the result is the louder
		 * one's pitch or somewhere between.  tools/sim_organ.c: two
		 * pipes of equal level flagged 5 of 5, single pipes about 1
		 * false flag in 210 (the reverberant treble) */
	public static final double BEAT_SWING = 0.7d;

		/* a measurement has begun to collect.  a change of
		 * microphone jumps the phase, so MicSelect holds its switches
		 * meanwhile.  not before: while nothing is heard yet a switch
		 * costs nothing, and the mic in use may be the one that
		 * hears nothing */
	public static volatile boolean busy = false;

	private boolean active = false;
	private double freq = 0.0d;		/* row frequency measured on */
	private double cycles = 0.0d, secs = 0.0d;
	private int gen = 0;			/* Listener.buffer_gen() */
	private double skip = 0.0d;
	private double need = MEASURE_SECONDS;	/* for the focused row */
	private double amp_lo = 0.0d, amp_hi = 0.0d;	/* over the measurement */
	private boolean beating = false;	/* the last result's swing */

		/* the usable seconds a measurement on the row of focus
		 * ratio r takes */
	public static double measure_seconds(double r) {
		return ((r > 0.0d) && (r < 1.0d - 1e-6)) ? MEASURE_SECONDS_LOW
							 : MEASURE_SECONDS;
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
		cycles = 0.0d;
		secs = 0.0d;
		gen = Listener.buffer_gen();
		skip = settle;
		need = measure_seconds(parms.focus_ratio);
		amp_lo = amp_hi = 0.0d;
		beating = false;
	}

	public synchronized void stop() {
		active = false;
		busy = false;
	}

	public synchronized boolean measuring() {
		return active;
	}

		/* 0..1 */
	public synchronized double progress() {
		return Math.min(secs / need, 1.0d);
	}

		/* the last measurement's partial beat (BEAT_SWING); its
		 * result stands, this only flags it.  false while measuring */
	public synchronized boolean beating() {
		return beating;
	}

		/* one buffer's drift of the focused row (Strobe.focus_drift),
		 * and the mean amplitude of its usable blocks (0 if none).
		 * returns the partial's offset in cents once measure_seconds()
		 * are in, which ends the measurement; NaN until then */
	public synchronized double feed(double freq_, double cycles_,
					double secs_, double amp) {
		if (!active || (freq_ <= 0.0d)) {
			return Double.NaN;
		}
		int g = Listener.buffer_gen();
		if (g != gen) {
			/* the microphone changed anyway (it can't always
			 * wait): start over, past the jump */
			gen = g;
			freq = freq_;
			cycles = 0.0d;
			secs = 0.0d;
			amp_lo = amp_hi = 0.0d;
			skip = SWITCH_SKIP;
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
			cycles = 0.0d;
			secs = 0.0d;
			need = measure_seconds(parms.focus_ratio);
			amp_lo = amp_hi = 0.0d;
		}
		cycles += cycles_;
		secs += secs_;
		if (amp > 0.0d) {
			if ((amp_lo == 0.0d) || (amp < amp_lo)) {
				amp_lo = amp;
			}
			if (amp > amp_hi) {
				amp_hi = amp;
			}
		}
		busy = (secs > 0.0d);
		if (secs < need) {
			return Double.NaN;
		}
		active = false;
		busy = false;
		beating = (amp_hi > 0.0d) &&
			((amp_hi - amp_lo)/(amp_hi + amp_lo) > BEAT_SWING);
		return 1200.0d * Math.log((freq + cycles/secs)/freq) /
				Math.log(2.0d);
	}
}
