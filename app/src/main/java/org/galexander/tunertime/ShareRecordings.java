package org.galexander.tunertime;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/* ShareRecordings - menu "Recordings to GitHub": the chromatic runs
 * (RunRecorder) and debug recordings (DebugRecorder) not sent before are
 * packed into zips in Downloads/TunerTime, each under PART_MAX as GitHub
 * takes attachments of up to 25 MB, and a new issue on ISSUES opens in the
 * browser with the title and a list of what is in them.  The zips are then
 * attached there by hand ("Paste, drop, or click to add files"): an app
 * cannot upload to GitHub without a login of its own.
 *
 * A file bigger than what is left in a zip goes on in the next one, as
 * pieces name.001, name.002 ...: `cat name.0* > name` (or
 * tools/field/unpack.py) joins them.  Which files were packed is kept in
 * the preferences (SENT), so the next time takes only the new ones; with
 * nothing new, all are packed again. */
public class ShareRecordings {
	public static final String ISSUES =
		"https://github.com/Vollesocke/tunertime/issues/new";
	private static final long PART_MAX = 23L*1024*1024;
	private static final String SENT = "shared_recordings";

	public static void start(final Activity a) {
		final ArrayList<File> files = collect(a);
		if (files.isEmpty()) {
			Toast.makeText(a, R.string.share_none,
					Toast.LENGTH_LONG).show();
			return;
		}
		final ProgressDialog pd = new ProgressDialog(a);
		pd.setMessage(a.getString(R.string.share_packing));
		pd.setCancelable(false);
		pd.show();
		new Thread() {
			public void run() {
				final ArrayList<String> parts = new ArrayList<String>();
				String err = null;
				try {
					pack(a, files, parts);
				} catch (IOException e) {
					err = e.toString();
				}
				final String err_ = err;
				a.runOnUiThread(new Runnable() {
					public void run() {
						pd.dismiss();
						done(a, files, parts, err_);
					} });
			}
		}.start();
	}

	/* the files not sent before, oldest first; all of them if none
	 * is new */
	private static ArrayList<File> collect(Context ctx) {
		SharedPreferences prefs =
			PreferenceManager.getDefaultSharedPreferences(ctx);
		Set<String> sent = prefs.getStringSet(SENT, new HashSet<String>());
		ArrayList<File> all = new ArrayList<File>();
		for (String d : new String[] { "runs", "recordings" }) {
			File dir = ctx.getExternalFilesDir(d);
			File[] fs = (dir == null) ? null : dir.listFiles();
			if (fs == null) {
				continue;
			}
			Arrays.sort(fs);
			for (File f : fs) {
				String n = f.getName();
				if (n.endsWith(".wav") || n.endsWith(".txt") ||
				    n.endsWith(".timeline")) {
					all.add(f);
				}
			}
		}
		/* a run still being written is left out */
		if (RunRecorder.active()) {
			ArrayList<File> done = new ArrayList<File>();
			for (File f : all) {
				if (!f.getName().startsWith(RunRecorder.base())) {
					done.add(f);
				}
			}
			all = done;
		}
		ArrayList<File> fresh = new ArrayList<File>();
		for (File f : all) {
			if (!sent.contains(key(f))) {
				fresh.add(f);
			}
		}
		return fresh.isEmpty() ? all : fresh;
	}

	private static String key(File f) {
		return f.getParentFile().getName() + "/" + f.getName() + ":" +
			f.length();
	}

	/* counts what reaches the file, to keep a zip under PART_MAX */
	private static class Counter extends FilterOutputStream {
		long n = 0;
		Counter(OutputStream o) { super(o); }
		public void write(int b) throws IOException {
			out.write(b);
			n++;
		}
		public void write(byte[] b, int off, int len) throws IOException {
			out.write(b, off, len);
			n += len;
		}
	}

	private static String stamp;

	private static void pack(Context ctx, ArrayList<File> files,
			ArrayList<String> parts) throws IOException {
		stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
				.format(new Date());
		byte[] buf = new byte[65536];
		long margin = 64*1024;	/* what the deflater may still hold */
		Counter cnt = null;
		ZipOutputStream z = null;
		try {
			for (File f : files) {
				String name = f.getParentFile().getName() + "/" +
						f.getName();
				long left = f.length();
				/* in pieces if it does not fit into what is
				 * left of this zip (WAV hardly compresses) */
				long room = (z == null) ? PART_MAX - margin :
						PART_MAX - margin - cnt.n;
				boolean split = left > room;
				int piece = 0;
				InputStream in = new FileInputStream(f);
				try {
					do {
						if ((z == null) ||
						    (PART_MAX - margin - cnt.n
								< margin)) {
							if (z != null) {
								z.close();
							}
							cnt = new Counter(open(ctx,
								parts));
							z = new ZipOutputStream(cnt);
							if (parts.size() == 1) {
								readme(z);
							}
						}
						room = PART_MAX - margin - cnt.n;
						piece++;
						z.putNextEntry(new ZipEntry(split ?
							String.format(Locale.US,
							"%s.%03d", name, piece) :
							name));
						long w = 0;
						int n;
						while ((w < room) && ((n = in.read(buf,
							0, (int)Math.min(buf.length,
							room - w))) > 0)) {
							z.write(buf, 0, n);
							w += n;
						}
						z.closeEntry();
						left -= w;
						if (left > 0) {
							z.close();	/* full */
							z = null;
						}
					} while (left > 0);
				} finally {
					in.close();
				}
			}
		} finally {
			if (z != null) {
				z.close();
			}
		}
	}

	/* a new zip in Downloads/TunerTime (Android 10 on), else in the
	 * app's own files ("share"); its name goes into parts */
	private static OutputStream open(Context ctx, ArrayList<String> parts)
			throws IOException {
		String name = String.format(Locale.US,
			"tunertime_%s_%d.zip", stamp, parts.size() + 1);
		parts.add(name);
		if (Build.VERSION.SDK_INT >= 29) {
			ContentResolver cr = ctx.getContentResolver();
			ContentValues v = new ContentValues();
			v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
			v.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
			v.put(MediaStore.MediaColumns.RELATIVE_PATH,
				Environment.DIRECTORY_DOWNLOADS + "/TunerTime");
			Uri u = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
					v);
			OutputStream o = (u == null) ? null :
					cr.openOutputStream(u);
			if (o == null) {
				throw new IOException("cannot create " + name);
			}
			return o;
		}
		File dir = ctx.getExternalFilesDir("share");
		if (dir == null) {
			throw new IOException("no external storage");
		}
		return new FileOutputStream(new File(dir, name));
	}

	private static void readme(ZipOutputStream z) throws IOException {
		z.putNextEntry(new ZipEntry("README.txt"));
		z.write(("TunerTime Organ recordings, " + stamp + "\n" +
			"runs/: chromatic runs (RunRecorder), recordings/: " +
			"debug recordings (DebugRecorder).\n" +
			"Files too big for one zip are in pieces name.001, " +
			"name.002 ... over the zips:\n" +
			"  cat name.0* > name   or   python " +
			"tools/field/unpack.py DIR\n").getBytes("UTF-8"));
		z.closeEntry();
	}

	private static void done(Activity a, ArrayList<File> files,
			ArrayList<String> parts, String err) {
		if (err != null) {
			Toast.makeText(a, a.getString(R.string.share_failed, err),
					Toast.LENGTH_LONG).show();
			return;
		}
		SharedPreferences prefs =
			PreferenceManager.getDefaultSharedPreferences(a);
		Set<String> sent = new HashSet<String>(
			prefs.getStringSet(SENT, new HashSet<String>()));
		for (File f : files) {
			sent.add(key(f));
		}
		prefs.edit().putStringSet(SENT, sent).apply();
		EventLog.ui("share: " + files.size() + " files in " +
				parts.size() + " zips");

		StringBuilder runs = new StringBuilder();
		int recs = 0;
		long bytes = 0;
		for (File f : files) {
			bytes += f.length();
			if (f.getName().endsWith(".txt") &&
			    f.getParentFile().getName().equals("runs")) {
				runs.append("- ").append(run_line(f)).append('\n');
			} else if (f.getName().endsWith(".wav") &&
			    f.getParentFile().getName().equals("recordings")) {
				recs++;
			}
		}
		StringBuilder zips = new StringBuilder();
		for (String p : parts) {
			zips.append("- ").append(p).append('\n');
		}
		String version = "?";
		try {
			version = a.getPackageManager().getPackageInfo(
				a.getPackageName(), 0).versionName;
		} catch (Exception e) {
		}
		String where = (Build.VERSION.SDK_INT >= 29) ?
			"Downloads/TunerTime" :
			a.getExternalFilesDir("share").getPath();
		String title = a.getString(R.string.share_issue_title, stamp);
		String body = a.getString(R.string.share_issue_body,
			parts.size(), where) + "\n\n" + zips +
			"\n" + a.getString(R.string.share_issue_runs) + "\n" +
			((runs.length() > 0) ? runs.toString() : "- -\n") +
			"\n" + a.getString(R.string.share_issue_recs, recs) +
			"\n\n" + String.format(Locale.US, "%.1f MB, ",
				bytes/1048576.0) +
			"TunerTime Organ " + version + ", " + Build.MANUFACTURER +
			" " + Build.MODEL + " (Android " +
			Build.VERSION.RELEASE + ")\n";
		Toast.makeText(a, a.getString(R.string.share_ready,
				parts.size(), where), Toast.LENGTH_LONG).show();
		try {
			Uri u = Uri.parse(ISSUES + "?title=" +
				URLEncoder.encode(title, "UTF-8") + "&body=" +
				URLEncoder.encode(body, "UTF-8"));
			a.startActivity(new Intent(Intent.ACTION_VIEW, u));
		} catch (Exception e) {
			Toast.makeText(a, a.getString(R.string.share_failed,
				e.toString()), Toast.LENGTH_LONG).show();
		}
	}

	/* "Rohrflöte 8' (open), 61 keys, 4:12" from a run's .txt */
	private static String run_line(File f) {
		String stop = "", kind = "", organ = "", keys = "?", secs = "";
		try {
			BufferedReader r = new BufferedReader(new InputStreamReader(
				new FileInputStream(f), "UTF-8"));
			try {
				String l;
				while ((l = r.readLine()) != null) {
					if (l.isEmpty()) {
						break;	/* the timeline */
					}
					if (l.startsWith("stop ")) stop = l.substring(5);
					else if (l.startsWith("kind ")) kind = l.substring(5);
					else if (l.startsWith("organ ")) organ = l.substring(6);
					else if (l.startsWith("keys ")) keys = l.substring(5);
					else if (l.startsWith("samples ")) {
						int i = l.indexOf('(');
						if (i >= 0) secs = l.substring(i+1,
							l.length()-1);
					}
				}
			} finally {
				r.close();
			}
		} catch (IOException e) {
		}
		return f.getName().replace(".txt", "") + ": " +
			(stop.isEmpty() ? "?" : stop) +
			(kind.isEmpty() ? "" : " (" + kind + ")") +
			(organ.isEmpty() ? "" : ", " + organ) +
			", " + keys + " keys" + (secs.isEmpty() ? "" : ", " + secs);
	}
}
