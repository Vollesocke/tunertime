package org.galexander.tunertime;

import android.content.Context;
import android.os.Build;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;

/* RunRecorder - records a whole chromatic run (menu "Record chromatic
 * run"), for improving the detector on real pipes.  The organ tuner plays
 * every key of a stop in turn, holds it some 3 s, and steps the note shown
 * by hand (semi+, in mixture mode key+), so that the truth is known for
 * every key: a key is the note shown from one step to the next.  Stepping
 * back (semi-) plays a key again; the later take is marked "redo".
 *
 * Auto is off during a run (TunerTime.auto_applies()), as follow would
 * move the note shown, but the detector keeps running (parms.run_active)
 * and its estimates, the focused row's strobe drift and the UI events go
 * into the timeline (EventLog, kept on during the run).
 *
 * The microphone input is streamed to a WAV as it comes (jni/record.c
 * keeps the last 8 s; the writer thread fetches every WRITE_MS), and the
 * timeline to a .timeline file beside it.  At the end the .txt is made:
 * the header (organ, stop, tuning, device), one line per key, then the
 * timeline, in the format tools/field/run_score.py reads.  The WAV header
 * is brought up to date every second, so a run cut off by a crash still
 * plays, and its .timeline is kept.
 *
 * Files in app-specific external storage, no permission needed:
 *   adb pull /sdcard/Android/data/<package>/files/runs
 * named like run_20261021-101500_Rohrfloete-8.wav.  At most MAX_SECS. */
public class RunRecorder {
	private static final int WRITE_MS = 250;
	public static final int MAX_SECS = 20*60;
	private static final int U = preset.UNITS_PER_CENT;

	public static class Meta {
		public String organ = "", stop = "", kind = "", footage = "",
			note = "";
	}

	private static class Key {
		double t0, t1;
		String name;
		int semi;
		double hz;
		boolean redo;
	}

	private static volatile boolean active = false;
	private static Thread writer, finisher;
	private static volatile boolean stopping;
	/* the writer stopped by itself: MAX_SECS reached, or it could not
	 * write; TunerTime ends the run then (UI thread) */
	private static volatile boolean limit, failed;
	private static File wav, txt, tl;
	private static String base;
	private static long start_pos = -1;	/* input position of sample 0 */
	private static int rate;
	private static Date when;
	private static String header;
	private static boolean saved_eventlog_on;
	/* UI thread */
	private static final ArrayList<Key> keys = new ArrayList<Key>();
	private static final ArrayList<String> marks = new ArrayList<String>();
	private static final HashSet<Integer> visited = new HashSet<Integer>();
	private static int cur_semi = Integer.MIN_VALUE, cur_alt = 0;
	private static double cur_hz = 0.0;
	/* the first estimate, seconds into the WAV, for test_pitch -t */
	private static volatile double t_est = -1.0;
	private static volatile long written = 0;	/* samples in the WAV */

	/* from = input position, or < 0 for now; pos[0], pos[1]: the
	 * samples copied, see jni/record.c */
	private static native int grab_from(long from, short[] dst, long[] pos);

	public static boolean active() {
		return active;
	}

	/* seconds recorded so far */
	public static int secs() {
		return (rate > 0) ? (int)(written/rate) : 0;
	}

	public static int num_keys() {
		return keys.size();
	}

	/* UI thread.  header: the tuning and device lines (TunerTime), at
	 * the start of the run */
	public static boolean start(Context ctx, Meta m, String header_) {
		if (active || ((writer != null) && writer.isAlive()) ||
		    ((finisher != null) && finisher.isAlive())) {
			/* the last run's files are still being finished */
			return active;
		}
		File dir = ctx.getExternalFilesDir("runs");
		if (dir == null) {
			return false;
		}
		rate = Listener.get_samplerate();
		when = new Date();
		base = "run_" + new SimpleDateFormat("yyyyMMdd-HHmmss",
				Locale.US).format(when) + slug(m.stop);
		wav = new File(dir, base + ".wav");
		txt = new File(dir, base + ".txt");
		tl = new File(dir, base + ".timeline");
		header = "# TunerTime Organ chromatic run\n" +
			"format 1\n" +
			"time " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
				Locale.US).format(when) + "\n" +
			header_ +
			"device " + Build.MANUFACTURER + " " + Build.MODEL +
				" (Android " + Build.VERSION.RELEASE + ")\n" +
			"sample_rate " + rate + "\n" +
			"organ " + line(m.organ) + "\n" +
			"stop " + line(m.stop) + "\n" +
			"kind " + line(m.kind) + "\n" +
			"footage " + line(m.footage) + "\n" +
			"note " + line(m.note) + "\n";
		keys.clear();
		marks.clear();
		visited.clear();
		cur_semi = Integer.MIN_VALUE;
		cur_hz = 0.0;
		t_est = -1.0;
		written = 0;
		limit = failed = false;
		stopping = false;
		long[] p = new long[2];
		try {
			grab_from(-1, new short[1], p);
		} catch (UnsatisfiedLinkError e) {
			return false;
		}
		start_pos = p[1];
		saved_eventlog_on = EventLog.on;
		EventLog.on = true;
		final long seq0 = EventLog.seq();
		parms.run_active = true;
		active = true;
		writer = new Thread() {
			public void run() {
				write_loop(seq0);
			} };
		writer.start();
		EventLog.ui("run start " + base);
		return true;
	}

	/* UI thread, whenever the status is updated: the key shown now.
	 * alt: which of two notes on the semitone (Dis or Es, parms.note_alt),
	 * 0 where there is one */
	public static void key(int semi, int alt, String name, double hz) {
		boolean same = (semi == cur_semi) && (alt == cur_alt);
		if (!active || (same && (Math.abs(hz - cur_hz) < 0.0005))) {
			return;
		}
		double t = now();
		int n = keys.size();
		if (n > 0) {
			keys.get(n-1).t1 = t;
		}
		Key k = new Key();
		k.t0 = t;
		k.t1 = -1.0;
		k.name = name;
		k.semi = semi;
		k.hz = hz;
		/* played before in this run */
		k.redo = !same && visited.contains(2*semi + alt);
		visited.add(2*semi + alt);
		keys.add(k);
		cur_semi = semi;
		cur_alt = alt;
		cur_hz = hz;
		EventLog.ui(String.format(Locale.US, "run key %d %s %.3f Hz%s",
			keys.size(), name, hz, k.redo ? " redo" : ""));
	}

	/* UI thread: something to note at this moment */
	public static void mark(String what) {
		if (!active) {
			return;
		}
		marks.add(String.format(Locale.US, "mark %.3f %s",
				now(), line(what)));
		EventLog.ui("run mark " + line(what));
	}

	/* UI thread; how: why it ended, for the .txt.  returns the .txt, or
	 * null if nothing was running.  the files are finished on the
	 * writer thread */
	public static File stop(String how) {
		if (!active) {
			return null;
		}
		EventLog.ui("run end (" + how + ")");
		double t_end = now();
		active = false;
		parms.run_active = false;
		final StringBuilder sb = new StringBuilder();
		int n = keys.size();
		if (n > 0) {
			keys.get(n-1).t1 = t_end;
		}
		sb.append("ended ").append(how).append('\n');
		sb.append("keys ").append(n).append('\n');
		sb.append("# key <i> <start s> <end s> <name> <semi> " +
			"<target Hz> [redo]; semi: 48 = A4, redo: the key " +
			"was played before in this run\n");
		for (int i = 0; i < n; i++) {
			Key k = keys.get(i);
			sb.append(String.format(Locale.US,
				"key %d %.3f %.3f %s %d %.3f%s\n", i+1, k.t0,
				(k.t1 >= 0.0) ? k.t1 : t_end, k.name, k.semi,
				k.hz, k.redo ? " redo" : ""));
		}
		for (String s : marks) {
			sb.append(s).append('\n');
		}
		final String keys_str = sb.toString();
		stopping = true;
		final Thread w = writer;
		finisher = new Thread() {
			public void run() {
				try {
					w.join();
				} catch (InterruptedException e) {
				}
				finish(keys_str);
				if (!active) {
					EventLog.on = saved_eventlog_on;
				}
			}
		};
		finisher.start();
		return txt;
	}

	private static void write_loop(long seq0) {
		RandomAccessFile out = null;
		Writer tlw = null;
		short[] buf = new short[rate*2];
		long[] p = new long[2];
		long[] seq = { seq0 };
		long next = start_pos;
		long last_patch = 0;
		try {
			out = new RandomAccessFile(wav, "rw");
			out.setLength(0);
			out.write(wav_header(0));
			tlw = new OutputStreamWriter(new FileOutputStream(tl),
						"UTF-8");
			/* the header also here, marked "#: ", for a run cut
			 * short by a crash, which leaves no .txt */
			for (String hl : header.split("\n")) {
				tlw.write("#: " + hl + "\n");
			}
			tlw.write(EventLog.HEADER);
			/* the hints in effect at the start, as in the
			 * debug recordings */
			double[] h = EventLog.hint_at(start_pos);
			if (h[0] >= 0.0d) {
				tlw.write(String.format(Locale.US, "%9.5f %8d  " +
					"hint %.4f %.3f\n", 0.0d, 0L, h[0], h[1]));
			}
			while (true) {
				boolean last = stopping;
				int n;
				while ((n = grab_from(next, buf, p)) > 0) {
					if (p[0] > next) {
						/* overwritten before fetched:
						 * silence keeps the time base */
						long gap = p[0] - next;
						tlw.write(String.format(Locale.US,
							"# gap: %d samples lost " +
							"at %.3f s\n", gap,
							(double)written/rate));
						byte[] z = new byte[(int)Math.min(
							2*gap, 1 << 24)];
						for (long g = 0; g < 2*gap;
						     g += z.length) {
							out.write(z, 0, (int)Math.min(
							    z.length, 2*gap-g));
						}
						written += gap;
					}
					out.write(pcm(buf, n));
					written += n;
					next = p[1];
					if (n < buf.length) {
						break;
					}
				}
				StringBuilder sb = new StringBuilder();
				long lost = EventLog.drain(seq, start_pos, rate,
							sb);
				if (lost > 0) {
					sb.append("# gap: ").append(lost).append(
						" events lost\n");
				}
				if (t_est < 0.0) {
					int e = sb.indexOf("  est ");
					if (e >= 0) {
						int b = sb.lastIndexOf("\n", e) + 1;
						t_est = Double.parseDouble(sb.substring(
							b, e).trim().split(" +")[0]);
					}
				}
				tlw.write(sb.toString());
				if (written - last_patch >= rate) {
					patch(out);
					tlw.flush();
					last_patch = written;
				}
				if (last) {
					break;
				}
				if (written >= (long)MAX_SECS*rate) {
					limit = true;
					break;
				}
				try {
					Thread.sleep(WRITE_MS);
				} catch (InterruptedException e) {
				}
			}
			patch(out);
		} catch (IOException e) {
			System.out.println("run recording: " + e);
			failed = true;
		} finally {
			try {
				if (out != null) out.close();
				if (tlw != null) tlw.close();
			} catch (IOException e) {
			}
		}
	}

	/* writer thread finished: the .txt from the header, the keys and
	 * the timeline file */
	private static void finish(String keys_str) {
		Writer w = null;
		BufferedReader r = null;
		try {
			w = new OutputStreamWriter(new FileOutputStream(txt),
						"UTF-8");
			w.write(header);
			w.write("samples " + written + " (" +
				String.format(Locale.US, "%.1f",
					(double)written/rate) + " s)\n");
			w.write(keys_str);
			w.write("replay: test_pitch " + ((t_est >= 0.0) ?
				String.format(Locale.US, "-t %.5f ", t_est) : "") +
				base + ".wav\n\n");
			r = new BufferedReader(new InputStreamReader(
				new FileInputStream(tl), "UTF-8"));
			String l;
			while ((l = r.readLine()) != null) {
				if (!l.startsWith("#: ")) {
					w.write(l);
					w.write('\n');
				}
			}
			r.close();
			r = null;
			w.close();
			w = null;
			tl.delete();
		} catch (IOException e) {
			System.out.println("run recording: " + e);
		} finally {
			try {
				if (w != null) w.close();
				if (r != null) r.close();
			} catch (IOException e) {
			}
		}
	}

	/* the writer has stopped by itself (TunerTime then calls stop()) */
	public static boolean limit() {
		return active && limit;
	}
	public static boolean failed() {
		return active && failed;
	}

	/* seconds from the run's sample 0 to now, on the input's time base
	 * (EventLog.sample_pos(): the latency is not taken off) */
	private static double now() {
		return (double)(EventLog.now_pos() - start_pos)/rate;
	}

	private static byte[] pcm(short[] s, int n) {
		ByteBuffer b = ByteBuffer.allocate(2*n);
		b.order(ByteOrder.LITTLE_ENDIAN);
		for (int i = 0; i < n; i++) {
			b.putShort(s[i]);
		}
		return b.array();
	}

	private static byte[] wav_header(long samples) {
		int len = (int)Math.min(2*samples, 0x7fffffffL - 36);
		ByteBuffer b = ByteBuffer.allocate(44);
		b.order(ByteOrder.LITTLE_ENDIAN);
		b.put("RIFF".getBytes()).putInt(36 + len);
		b.put("WAVE".getBytes());
		b.put("fmt ".getBytes()).putInt(16);
		b.putShort((short)1).putShort((short)1);	/* PCM, mono */
		b.putInt(rate).putInt(rate*2);
		b.putShort((short)2).putShort((short)16);
		b.put("data".getBytes()).putInt(len);
		return b.array();
	}

	/* the sizes in the WAV header for what is written so far */
	private static void patch(RandomAccessFile f) throws IOException {
		long end = f.getFilePointer();
		f.seek(0);
		f.write(wav_header(written));
		f.seek(end);
	}

	/* one line of free text */
	private static String line(String s) {
		return (s == null) ? "" : s.replace('\n', ' ').replace('\r', ' ')
			.trim();
	}

	/* "_Rohrfloete-8" from "Rohrflöte 8'", for the file name */
	private static String slug(String s) {
		String t = line(s).replace("ä", "ae").replace("ö", "oe")
			.replace("ü", "ue").replace("Ä", "Ae")
			.replace("Ö", "Oe").replace("Ü", "Ue")
			.replace("ß", "ss");
		StringBuilder sb = new StringBuilder();
		for (char c : t.toCharArray()) {
			if (((c >= 'a') && (c <= 'z')) || ((c >= 'A') && (c <= 'Z'))
			    || ((c >= '0') && (c <= '9'))) {
				sb.append(c);
			} else if ((sb.length() > 0) &&
				   (sb.charAt(sb.length()-1) != '-')) {
				sb.append('-');
			}
		}
		while ((sb.length() > 0) && (sb.charAt(sb.length()-1) == '-')) {
			sb.setLength(sb.length()-1);
		}
		if (sb.length() > 40) {
			sb.setLength(40);
		}
		return (sb.length() > 0) ? ("_" + sb) : "";
	}

	static {
		System.loadLibrary("strobe-jni");
	}
}
