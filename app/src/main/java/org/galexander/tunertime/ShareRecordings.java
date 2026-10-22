package org.galexander.tunertime;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
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

/* ShareRecordings - menu "Recordings to GitHub": a dialog lists what there
 * is to send, one line each, newest first: a chromatic run (RunRecorder:
 * its .txt, .wav and .timeline), a day's debug recordings (DebugRecorder),
 * the crash reports (CrashLog).  The lines not packed before are ticked; a
 * line packed before says so, and can be ticked again.  (Before 18.8 all
 * files were packed when none was new: 202 MB in 7 zips, which never got
 * attached from the phone, Onno 22 Oct.)
 *
 * Each ticked line goes into zips of its own in Downloads/TunerTime, named
 * after it (run_20261022-083757_Plenum-mixtur.zip), each under PART_MAX as
 * GitHub takes attachments of up to 25 MB; the zips of the share before
 * are removed from there first, so that the folder holds just what is to
 * be attached.  A new issue on ISSUES then opens in the browser with the
 * title and a list of the zips, which are attached there by hand ("Paste,
 * drop, or click to add files"): an app cannot upload to GitHub without a
 * login of its own.
 *
 * A file bigger than what is left in a zip goes on in the next one, as
 * pieces name.001, name.002 ...: `cat name.0* > name` (or
 * tools/field/unpack.py) joins them.  Which files were packed is kept in
 * the preferences (SENT). */
public class ShareRecordings {
	public static final String ISSUES =
		"https://github.com/Vollesocke/tunertime/issues/new";
	private static final long PART_MAX = 23L*1024*1024;
	private static final long MARGIN = 64*1024; /* what the deflater
						     * may still hold */
	private static final String SENT = "shared_recordings";
	private static final String ZIP_DIR = "TunerTime";

	/* one line of the dialog, one set of zips */
	private static class Item {
		String stem;		/* the zips' name: "run_...", "debug_..." */
		String label;
		final ArrayList<File> files = new ArrayList<File>();
		long bytes = 0;
		File run_txt = null;	/* a run's .txt */
		int recs = 0;		/* debug recordings */
		int crashes = 0;	/* crash reports */
		void add(File f) {
			files.add(f);
			bytes += f.length();
		}
	}

	public static void start(final Activity a) {
		final ArrayList<Item> items = items(a);
		if (items.isEmpty()) {
			return;		/* nothing recorded */
		}
		Set<String> sent = PreferenceManager.getDefaultSharedPreferences(
				a).getStringSet(SENT, new HashSet<String>());
		String[] labels = new String[items.size()];
		final boolean[] ticked = new boolean[items.size()];
		for (int i = 0; i < labels.length; i++) {
			Item it = items.get(i);
			boolean old = true;
			for (File f : it.files) {
				old &= sent.contains(key(f));
			}
			ticked[i] = !old;
			labels[i] = it.label + String.format(Locale.US,
				", %.1f MB", it.bytes/1048576.0) +
				(old ? " " + a.getString(R.string.share_packed) :
				       "");
		}
		new AlertDialog.Builder(a)
			.setTitle(R.string.menu_share)
			.setMultiChoiceItems(labels, ticked,
				new DialogInterface.OnMultiChoiceClickListener() {
				public void onClick(DialogInterface d, int i,
						    boolean on) {
					ticked[i] = on;
				} })
			.setPositiveButton(R.string.share_pack,
				new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface d, int w) {
					ArrayList<Item> chosen =
						new ArrayList<Item>();
					for (int i = 0; i < ticked.length; i++) {
						if (ticked[i]) {
							chosen.add(items.get(i));
						}
					}
					if (!chosen.isEmpty()) {
						pack_all(a, chosen);
					}
				} })
			.setNegativeButton(R.string.cancel, null)
			.show();
	}

	private static void pack_all(final Activity a,
				     final ArrayList<Item> chosen) {
		final ProgressDialog pd = new ProgressDialog(a);
		pd.setMessage(a.getString(R.string.share_packing));
		pd.setCancelable(false);
		pd.show();
		new Thread() {
			public void run() {
				final ArrayList<String> parts = new ArrayList<String>();
				String err = null;
				try {
					clear_zips(a);
					for (Item it : chosen) {
						pack(a, it, parts);
					}
				} catch (IOException e) {
					err = e.toString();
				}
				final String err_ = err;
				a.runOnUiThread(new Runnable() {
					public void run() {
						pd.dismiss();
						done(a, chosen, parts, err_);
					} });
			}
		}.start();
	}

	private static File[] list(Context ctx, String d) {
		File dir = ctx.getExternalFilesDir(d);
		File[] fs = (dir == null) ? null : dir.listFiles();
		if (fs == null) {
			return new File[0];
		}
		Arrays.sort(fs);
		return fs;
	}

	/* what there is to send: the runs newest first, the crash reports,
	 * the debug recordings by day, newest first */
	private static ArrayList<Item> items(Context ctx) {
		ArrayList<Item> items = new ArrayList<Item>();
		Item cur = null;
		for (File f : list(ctx, "runs")) {
			String n = f.getName();
			int dot = n.lastIndexOf('.');
			if ((dot < 0) || !(n.endsWith(".wav") ||
			    n.endsWith(".txt") || n.endsWith(".timeline"))) {
				continue;
			}
			String base = n.substring(0, dot);
			if (RunRecorder.active() &&
			    base.startsWith(RunRecorder.base())) {
				continue;	/* still being written */
			}
			if ((cur == null) || !cur.stem.equals(base)) {
				cur = new Item();
				cur.stem = base;
				items.add(0, cur);
			}
			cur.add(f);
			if (n.endsWith(".txt")) {
				cur.run_txt = f;
			}
		}
		for (Item it : items) {
			it.label = (it.run_txt != null) ?
				run_line(it.run_txt, false) : it.stem;
		}

		Item crashes = new Item();
		crashes.stem = "crashes_" + new SimpleDateFormat(
			"yyyyMMdd-HHmmss", Locale.US).format(new Date());
		for (File f : list(ctx, "crashes")) {
			crashes.add(f);
			if (f.getName().endsWith(".txt")) {
				crashes.crashes++;
			}
		}
		if (!crashes.files.isEmpty()) {
			crashes.label = ctx.getString(R.string.share_item_crashes,
						      crashes.crashes);
			items.add(crashes);
		}

		ArrayList<Item> days = new ArrayList<Item>();
		cur = null;
		for (File f : list(ctx, "recordings")) {
			String n = f.getName();
			if ((n.length() < 8) ||
			    !(n.endsWith(".wav") || n.endsWith(".txt"))) {
				continue;
			}
			String day = n.substring(0, 8);	/* yyyyMMdd */
			if ((cur == null) || !cur.stem.equals("debug_" + day)) {
				cur = new Item();
				cur.stem = "debug_" + day;
				days.add(0, cur);
			}
			cur.add(f);
			if (n.endsWith(".wav")) {
				cur.recs++;
			}
		}
		for (Item it : days) {
			String d = it.stem.substring(6);
			it.label = ctx.getString(R.string.share_item_recs,
				d.substring(6, 8) + "." + d.substring(4, 6) + "." +
				d.substring(0, 4), it.recs);
		}
		items.addAll(days);
		return items;
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

	/* the item's files into zips of its own: stem.zip, or stem_1.zip,
	 * stem_2.zip ... when it does not fit into one */
	private static void pack(Context ctx, Item it, ArrayList<String> parts)
			throws IOException {
		boolean several = it.bytes > PART_MAX - 2*MARGIN;
		int zips = 0;
		byte[] buf = new byte[65536];
		Counter cnt = null;
		ZipOutputStream z = null;
		try {
			for (File f : it.files) {
				String name = f.getParentFile().getName() + "/" +
						f.getName();
				long left = f.length();
				/* in pieces if it does not fit into what is
				 * left of this zip (WAV hardly compresses) */
				long room = (z == null) ? PART_MAX - MARGIN :
						PART_MAX - MARGIN - cnt.n;
				boolean split = left > room;
				int piece = 0;
				InputStream in = new FileInputStream(f);
				try {
					do {
						if ((z == null) ||
						    (PART_MAX - MARGIN - cnt.n
								< MARGIN)) {
							if (z != null) {
								z.close();
							}
							zips++;
							cnt = new Counter(open(ctx,
								it.stem + (several ?
								"_" + zips : "") +
								".zip", parts));
							z = new ZipOutputStream(cnt);
							if (several && (zips == 1)) {
								readme(z);
							}
						}
						room = PART_MAX - MARGIN - cnt.n;
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

	/* the zips this app put into Downloads/TunerTime (Android 10 on),
	 * else into its own files ("share"), removed: those of the share
	 * before.  Android lets an app delete what it created itself */
	private static void clear_zips(Context ctx) {
		try {
			if (Build.VERSION.SDK_INT >= 29) {
				ContentResolver cr = ctx.getContentResolver();
				Uri dl = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
				Cursor c = cr.query(dl,
					new String[] { MediaStore.MediaColumns._ID },
					MediaStore.MediaColumns.RELATIVE_PATH + "=?",
					new String[] { Environment.DIRECTORY_DOWNLOADS +
						       "/" + ZIP_DIR + "/" }, null);
				if (c == null) {
					return;
				}
				try {
					while (c.moveToNext()) {
						cr.delete(Uri.withAppendedPath(dl,
							Long.toString(c.getLong(0))),
							null, null);
					}
				} finally {
					c.close();
				}
			} else {
				for (File f : list(ctx, "share")) {
					f.delete();
				}
			}
		} catch (Exception e) {
			/* left there: the new zips are named apart */
		}
	}

	/* a new zip of that name; the name goes into parts */
	private static OutputStream open(Context ctx, String name,
			ArrayList<String> parts) throws IOException {
		parts.add(name);
		if (Build.VERSION.SDK_INT >= 29) {
			ContentResolver cr = ctx.getContentResolver();
			ContentValues v = new ContentValues();
			v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
			v.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
			v.put(MediaStore.MediaColumns.RELATIVE_PATH,
				Environment.DIRECTORY_DOWNLOADS + "/" + ZIP_DIR);
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
		z.write(("TunerTime Organ recordings\n" +
			"runs/: chromatic runs (RunRecorder), recordings/: " +
			"debug recordings (DebugRecorder), crashes/: crash " +
			"reports (CrashLog).\n" +
			"Files too big for one zip are in pieces name.001, " +
			"name.002 ... over the zips:\n" +
			"  cat name.0* > name   or   python " +
			"tools/field/unpack.py DIR\n").getBytes("UTF-8"));
		z.closeEntry();
	}

	private static void done(Activity a, ArrayList<Item> chosen,
			ArrayList<String> parts, String err) {
		if (err != null) {
			return;
		}
		SharedPreferences prefs =
			PreferenceManager.getDefaultSharedPreferences(a);
		Set<String> sent = new HashSet<String>(
			prefs.getStringSet(SENT, new HashSet<String>()));
		StringBuilder runs = new StringBuilder();
		int recs = 0, crashes = 0, files = 0;
		long bytes = 0;
		for (Item it : chosen) {
			for (File f : it.files) {
				sent.add(key(f));
			}
			files += it.files.size();
			bytes += it.bytes;
			recs += it.recs;
			crashes += it.crashes;
			if (it.run_txt != null) {
				runs.append("- ").append(run_line(it.run_txt,
							true)).append('\n');
			}
		}
		prefs.edit().putStringSet(SENT, sent).apply();
		EventLog.ui("share: " + files + " files in " +
				parts.size() + " zips");

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
			"Downloads/" + ZIP_DIR :
			a.getExternalFilesDir("share").getPath();
		String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
				.format(new Date());
		String title = a.getString(R.string.share_issue_title, stamp);
		String body = a.getString(R.string.share_issue_body,
			parts.size(), where) + "\n\n" + zips +
			"\n" + a.getString(R.string.share_issue_runs) + "\n" +
			((runs.length() > 0) ? runs.toString() : "- -\n") +
			"\n" + a.getString(R.string.share_issue_recs, recs) +
			((crashes > 0) ? "\n" + a.getString(
				R.string.share_issue_crashes, crashes) : "") +
			"\n\n" + String.format(Locale.US, "%.1f MB, ",
				bytes/1048576.0) +
			"TunerTime Organ " + version + ", " + Build.MANUFACTURER +
			" " + Build.MODEL + " (Android " +
			Build.VERSION.RELEASE + ")\n";
		try {
			Uri u = Uri.parse(ISSUES + "?title=" +
				URLEncoder.encode(title, "UTF-8") + "&body=" +
				URLEncoder.encode(body, "UTF-8"));
			a.startActivity(new Intent(Intent.ACTION_VIEW, u));
		} catch (Exception e) {
			/* no browser: the zips are in Downloads/TunerTime */
		}
	}

	/* from a run's .txt: for the issue (full) with its name, "run_20261021-
	 * 120143_Rohrfloete-8: Rohrflöte 8' (open), Hmth 202, 56 keys,
	 * 215.3 s"; for the dialog with its time in place of the name,
	 * "21.10. 12:01 Rohrflöte 8' (open), Hmth 202, 56 keys, 215 s" */
	private static String run_line(File f, boolean full) {
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
		String n = f.getName().replace(".txt", "");
		if (!full && n.matches("run_\\d{8}-\\d{6}.*")) {
			n = n.substring(10, 12) + "." + n.substring(8, 10) + ". " +
				n.substring(13, 15) + ":" + n.substring(15, 17);
			int i = secs.indexOf('.');
			if (i >= 0) {
				secs = secs.substring(0, i) + " s";
			}
		} else {
			n += ":";
		}
		return n + " " +
			(stop.isEmpty() ? "?" : stop) +
			(kind.isEmpty() ? "" : " (" + kind + ")") +
			(organ.isEmpty() ? "" : ", " + organ) +
			", " + keys + " keys" + (secs.isEmpty() ? "" : ", " + secs);
	}
}
