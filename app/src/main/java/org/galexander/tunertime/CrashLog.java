package org.galexander.tunertime;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/* CrashLog - a crash leaves a file in the app's "crashes" folder, which
 * "Recordings to GitHub" (ShareRecordings) sends along with the runs, as
 * nothing else gets a stack trace off a phone without a PC:
 *  - a Java exception on any thread: the stack, the app's state as last
 *    logged (EventLog.state) and, with debug recordings on, the last
 *    TIMELINE_SECS of the event log; then Android's own handler as before,
 *  - at the next start (Android 11 and later): what the system kept of
 *    the app's earlier ends, crashes in native code and ANRs too (those
 *    the handler above never sees), each once (LAST_EXIT).  Android 12
 *    adds the trace: an ANR's thread dump as text, a native crash's
 *    tombstone as protobuf (.trace, read with the NDK's tools). */
public class CrashLog {
	private static final String DIR = "crashes";
	private static final String LAST_EXIT = "crashlog_last_exit";
	private static final double TIMELINE_SECS = 20.0d;

	private static Context app = null;

	public static void install(Context ctx) {
		if (app != null) {
			return;
		}
		app = ctx.getApplicationContext();
		final Thread.UncaughtExceptionHandler prev =
			Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler(
				new Thread.UncaughtExceptionHandler() {
			public void uncaughtException(Thread t, Throwable e) {
				try {
					write_exception(t, e);
				} catch (Throwable ignored) {
				}
				if (prev != null) {
					prev.uncaughtException(t, e);
				}
			} });
		try {
			collect_exits();
		} catch (Throwable ignored) {
		}
	}

	private static String stamp(long ms) {
		return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
					.format(new Date(ms));
	}

	private static String header() {
		String version = "?";
		try {
			version = app.getPackageManager().getPackageInfo(
				app.getPackageName(), 0).versionName;
		} catch (Exception e) {
		}
		return "version " + version + ", Android " +
			Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT +
			"), " + Build.MANUFACTURER + " " + Build.MODEL + "\n";
	}

	private static void write_exception(Thread t, Throwable e)
							throws Exception {
		long now = System.currentTimeMillis();
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		pw.print("# crash " + stamp(now) + ", " + header());
		pw.println("# state: " + EventLog.state);
		pw.println("# thread: " + t.getName());
		e.printStackTrace(pw);
		if (EventLog.on) {
			int rate = Listener.get_samplerate();
			long end = EventLog.now_pos();
			long start = Math.max(0L,
				end - (long)(TIMELINE_SECS*rate));
			pw.println();
			pw.print(EventLog.HEADER);
			pw.print(EventLog.timeline(start, end, rate));
		}
		pw.flush();
		write("crash-" + stamp(now) + ".txt", sw.toString().getBytes(
							"UTF-8"), null);
	}

	/* the system's record of earlier ends not written yet */
	private static void collect_exits() throws Exception {
		if (Build.VERSION.SDK_INT < 30) {
			return;
		}
		ActivityManager am = (ActivityManager)
			app.getSystemService(Context.ACTIVITY_SERVICE);
		List<ApplicationExitInfo> exits =
			am.getHistoricalProcessExitReasons(null, 0, 0);
		SharedPreferences prefs =
			PreferenceManager.getDefaultSharedPreferences(app);
		long last = prefs.getLong(LAST_EXIT, 0L), newest = last;
		for (ApplicationExitInfo x : exits) {
			int r = x.getReason();
			if ((x.getTimestamp() <= last) ||
			    ((r != ApplicationExitInfo.REASON_CRASH) &&
			     (r != ApplicationExitInfo.REASON_CRASH_NATIVE) &&
			     (r != ApplicationExitInfo.REASON_ANR))) {
				continue;
			}
			newest = Math.max(newest, x.getTimestamp());
			String kind = (r == ApplicationExitInfo.REASON_CRASH) ?
				"crash" : (r == ApplicationExitInfo.REASON_ANR) ?
				"anr" : "native";
			String base = "exit-" + stamp(x.getTimestamp()) +
				"-" + kind;
			String txt = "# " + kind + " " +
				stamp(x.getTimestamp()) + " (kept by Android), " +
				"now " + header() + x.toString() + "\n";
			InputStream trace = null;
			if (Build.VERSION.SDK_INT >= 31) {
				try {
					trace = x.getTraceInputStream();
				} catch (Exception e) {
				}
			}
			write(base + ".txt", txt.getBytes("UTF-8"), null);
			if (trace != null) {
				write(base + ".trace", null, trace);
			}
		}
		if (newest != last) {
			prefs.edit().putLong(LAST_EXIT, newest).apply();
		}
	}

	private static void write(String name, byte[] b, InputStream in)
							throws Exception {
		File dir = app.getExternalFilesDir(DIR);
		if (dir == null) {
			return;
		}
		OutputStream out = new FileOutputStream(new File(dir, name));
		try {
			if (b != null) {
				out.write(b);
			}
			if (in != null) {
				byte[] buf = new byte[65536];
				int n;
				while ((n = in.read(buf)) > 0) {
					out.write(buf, 0, n);
				}
				in.close();
			}
		} finally {
			out.close();
		}
	}
}
