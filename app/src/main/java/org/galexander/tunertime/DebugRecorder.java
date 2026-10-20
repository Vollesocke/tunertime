package org.galexander.tunertime;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/* Debug recording (Settings): whenever the user corrects the note by hand
 * while follow is on, the microphone input from PRE_MS before to POST_MS
 * after is saved as a 16-bit mono WAV, with a .txt beside it giving the
 * detected and the corrected note, for replaying through the detector on
 * a computer (tools/test_pitch.c).  Several taps in a row (a fifth down is
 * seven) make one recording, with the note the user ended on.
 *
 * Files go to app-specific external storage, no permission needed:
 *   adb pull /sdcard/Android/data/org.galexander.tunertime.dev/files/recordings
 * named like 20261016-153012_det-C4_user-C3_44100Hz.wav (s = sharp).
 * Only the newest MAX_FILES are kept.
 *
 * The last seconds of input are kept natively (jni/record.c), fed from
 * the strobe thread's audio reads.  The .txt also has a timeline of what
 * the detector heard and the app did during the recording (EventLog). */
public class DebugRecorder {
	private static final int PRE_MS = 5000;
	private static final int POST_MS = 2000;
	private static final int MAX_FILES = 100;
	private static final int U = preset.UNITS_PER_CENT;
	public static final int NONE = Integer.MIN_VALUE;

	private static final Handler handler =
				new Handler(Looper.getMainLooper());
	private static boolean pending = false;
	private static Date when;
	private static int det_cents, from_cents, user_cents;
	private static boolean near;
	private static double det_hz;

	/* copies the newest samples, oldest first; returns how many.
	 * end[0]: the input position just after them (EventLog's base) */
	private static native int grab(short[] dst, long[] end);

	public static boolean enabled(Context ctx) {
		SharedPreferences prefs =
			PreferenceManager.getDefaultSharedPreferences(ctx);
		return prefs.getBoolean("debug_rec", ctx.getResources().
				getBoolean(R.bool.debug_rec_default));
	}

	/* det_cents NONE: nothing detected lately.  from/user: the note
	 * before and after the user's change.  all in tenths of a cent,
	 * 48000 = A4.  UI thread only */
	public static void intervention(Context ctx, int det_cents_,
			double det_hz_, int from_cents_, int user_cents_,
			boolean near_) {
		if (!enabled(ctx)) {
			return;
		}
		user_cents = user_cents_;	/* the latest change wins */
		near = near_;
		if (pending) {
			return;
		}
		pending = true;
		when = new Date();
		det_cents = det_cents_;
		det_hz = det_hz_;
		from_cents = from_cents_;
		final Context app = ctx.getApplicationContext();
		handler.postDelayed(new Runnable() {
			public void run() {
				save(app);
			} }, POST_MS);
	}

	private static void save(Context ctx) {
		pending = false;
		int rate = Listener.get_samplerate();
		short[] buf = new short[(int)((long)rate*(PRE_MS+POST_MS)/1000)];
		long[] end = new long[1];
		int n;
		try {
			n = grab(buf, end);
		} catch (UnsatisfiedLinkError e) {
			return;
		}
		long start = end[0] - n;
		double t_est = EventLog.first_estimate(start, end[0], rate);
		if (n < rate/2) {
			return;		/* the strobe was not running */
		}
		File dir = ctx.getExternalFilesDir("recordings");
		if (dir == null) {
			return;		/* no external storage */
		}
		String base = new SimpleDateFormat("yyyyMMdd-HHmmss",
				Locale.US).format(when) +
			"_det-" + ((det_cents == NONE) ? "none" : name(det_cents)) +
			"_user-" + name(user_cents) + "_" + rate + "Hz";
		final short[] samps = Arrays.copyOf(buf, n);
		final File wav = new File(dir, base + ".wav");
		final File txt = new File(dir, base + ".txt");
		final String timeline = EventLog.timeline(start, end[0], rate);
		/* the hints at the start also on the replay line, so that it
		 * replays the same without --hints (the app gives the
		 * detector the shown note's Hz with the focus, jni/interface.c) */
		final double[] h0 = EventLog.hint_at(start);
		final String info =
			"time " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
				Locale.US).format(when) + "\n" +
			"sample_rate " + rate + "\n" +
			"samples " + n + " (" + PRE_MS + " ms before the " +
				"change, " + POST_MS + " ms after)\n" +
			"detected " + ((det_cents == NONE) ? "none" :
				(name(det_cents) + " " +
				 String.format(Locale.US, "%.2f", det_hz) +
				 " Hz")) + "\n" +
			"note_before " + name(from_cents) + "\n" +
			"user_note " + name(user_cents) + "\n" +
			"follow_near " + near + " (continuity: detections " +
				"taken within a fourth of the note shown)\n" +
			"A_freq " + (parms.A_freq/10.0) + "\n" +
			"stretch " + (parms.stretch()/10.0) + " c/oct\n" +
			"organ_mode " + parms.organ_mode + "\n" +
			"focus_ratio " + parms.focus_ratio + "\n" +
			"state_now " + EventLog.state + "\n" +
			"replay: test_pitch " +
				((t_est >= 0.0) ? String.format(Locale.US,
					"-t %.5f ", t_est) : "") +
				base + ".wav" +
				((h0[0] > 0.0) ? String.format(Locale.US,
					" %.4f %.3f", h0[0], h0[1]) : "") +
				(timeline.contains(" hint ") ?
				 (" --hints " + base + ".txt") : "") + "\n" +
			"\n" + EventLog.HEADER + timeline;
		final File dir_ = dir;
		final int rate_ = rate;
		new Thread() {
			public void run() {
				write(wav, txt, samps, rate_, info);
				prune(dir_);
			}
		}.start();
	}

	private static void write(File wav, File txt, short[] s, int rate,
			String info) {
		ByteBuffer b = ByteBuffer.allocate(44 + 2*s.length);
		b.order(ByteOrder.LITTLE_ENDIAN);
		b.put("RIFF".getBytes()).putInt(36 + 2*s.length);
		b.put("WAVE".getBytes());
		b.put("fmt ".getBytes()).putInt(16);
		b.putShort((short)1).putShort((short)1);	/* PCM, mono */
		b.putInt(rate).putInt(rate*2);
		b.putShort((short)2).putShort((short)16);
		b.put("data".getBytes()).putInt(2*s.length);
		for (int i = 0; i < s.length; i++) {
			b.putShort(s[i]);
		}
		OutputStream os = null;
		try {
			os = new FileOutputStream(wav);
			os.write(b.array());
			os.close();
			os = null;
			PrintWriter pw = new PrintWriter(txt, "UTF-8");
			pw.print(info);
			pw.close();
		} catch (IOException e) {
			System.out.println("debug recording: " + e);
		} finally {
			if (os != null) {
				try { os.close(); } catch (IOException e) { }
			}
		}
	}

	/* keeps the newest MAX_FILES recordings; names sort by time */
	private static void prune(File dir) {
		String[] names = dir.list();
		if (names == null) {
			return;
		}
		Arrays.sort(names);
		int wavs = 0;
		for (String s : names) {
			if (s.endsWith(".wav")) wavs++;
		}
		for (String s : names) {
			if (wavs <= MAX_FILES) {
				break;
			}
			if (s.endsWith(".wav")) {
				new File(dir, s).delete();
				new File(dir, s.substring(0, s.length()-4) +
						".txt").delete();
				wavs--;
			}
		}
	}

	private static final String[] NAMES = { "C", "Cs", "D", "Ds", "E",
				"F", "Fs", "G", "Gs", "A", "As", "B" };

	/* tenths of a cent, 48000 = A4 -> "Cs4", like note.java but
	 * without '#' for file names */
	private static String name(int cents) {
		/* semitones from C-10, so that a 32' pipe below A0 still
		 * divides the right way */
		int semi = (cents + 950*U + 120*100*U) / (100*U);
		return NAMES[semi % 12] + (semi/12 - 10);
	}

	static {
		System.loadLibrary("strobe-jni");
	}
}
