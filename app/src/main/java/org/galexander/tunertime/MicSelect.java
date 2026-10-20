package org.galexander.tunertime;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.MicrophoneInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Pair;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/* MicSelect - opens the AudioRecord for Listener: which audio source, which
 * microphone, and in "auto" mode keeps whichever microphone hears the
 * instrument best.  Listener still gets plain mono 16-bit samples.
 *
 * Phones have two to four microphones, and the default capture path may
 * pick one behind noise suppression, which smears a steady tone.
 *
 * Source (setting "audio_source"): auto takes UNPROCESSED where the device
 * says it supports it (API 24+), else VOICE_RECOGNITION, which is meant to
 * come without noise suppression and AGC.  MIC is the last resort when the
 * chosen source fails to open.
 *
 * Microphone (setting "microphone"): "default" leaves it to Android, a
 * device key (see key()) asks for that input with setPreferredDevice (API
 * 23+).  "auto" takes an external (USB, headset, line) input as it is.
 * Otherwise it asks for stereo from the built-in mics; on most phones the
 * two channels are two physical mics (bottom and top or back), and
 * listening to both all the time lets us change mic without a gap.  If the
 * "stereo" turns out to be one mic copied twice, it goes back to mono and,
 * where the built-in mics show up as separate devices (API 23+, e.g.
 * "bottom" and "back"), briefly tries the next one when the signal is
 * doubtful, keeping it only if it is clearly better.
 *
 * Quality, per channel, every WIN*DEC samples (~93ms): the periodicity is
 * the highest normalized autocorrelation of a 4:1 decimated window past its
 * first zero crossing (a clean steady tone ~1, noise ~0, lags down to
 * 21Hz); it is scaled down when the level is close to the noise floor
 * (a slowly rising minimum of the block RMS), and halved while clipping.
 * Scores are smoothed, and nothing is compared while no mic hears anything
 * above its floor.  The periodicity of the other channel is only worked out
 * while the one in use is doubtful (below), which halves the cost.
 *
 * A switch needs: the current mic doubtful (smoothed score below DOUBT,
 * clipping, or hearing nothing while the other does), the other better by
 * MARGIN for SWITCH_FRAMES frames in a row, and HOLD_MS since the last
 * switch.  While the current mic has a good signal nothing changes, so a
 * strobe that is being read stays put, and nothing changes either while a
 * mixture measurement is collecting (mixture.busy), whose phase would
 * jump.  The new mic is shown in a Toast.
 */
public class MicSelect {
	public static final int RATE = 44100;

	private static final int DEC = 4;	/* decimation for periodicity */
	private static final int WIN = 1024;	/* decimated window, 93ms */
	private static final int CORR = WIN/2;	/* correlation length, max lag */
	private static final int CLIP = 32000;
	private static final double MIN_RMS = 8.0;	/* 16-bit units */
	private static final double MIN_SNR = 10.0;	/* dB over the floor */
	private static final double FULL_SNR = 30.0;	/* no level penalty */
	private static final double FLOOR_RISE = 1.0005; /* per block, ~2%/s */
	private static final double EMA = 0.3;
	private static final double DOUBT = 0.6;
	private static final double MARGIN = 0.12;
	private static final int SWITCH_FRAMES = 8;	/* ~0.75s */
	private static final long HOLD_MS = 4000;
		/* mono, separate devices: */
	private static final int PROBE_AFTER = 16;	/* doubtful frames, 1.5s */
	private static final long PROBE_GAP_MS = 15000;
	private static final int PROBE_SETTLE = 3;	/* frames ignored */
	private static final int PROBE_FRAMES = 8;	/* frames compared */
		/* stereo that is really mono: */
	private static final int CHECK_BLOCKS = 40;

	private static Context ctx = null;
	private static Handler ui = null;

		/* name of the mic in use, for display */
	public static volatile String active_name = null;
		/* counts changes of mic or channel (Listener.buffer_gen) */
	public static volatile int switches = 0;

	public static void init(Context c) {
		ctx = c.getApplicationContext();
		ui = new Handler(Looper.getMainLooper());
	}

	private static AudioManager am() {
		if (ctx == null) {
			return null;
		}
		return (AudioManager)ctx.getSystemService(Context.AUDIO_SERVICE);
	}

	public static boolean unprocessed_supported() {
		if (Build.VERSION.SDK_INT < 24) {
			return false;
		}
		AudioManager m = am();
		return (m != null) && "true".equals(m.getProperty(
			AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED));
	}

	private static int source_for(String s) {
		if (s.equals("unprocessed") && (Build.VERSION.SDK_INT >= 24)) {
			return MediaRecorder.AudioSource.UNPROCESSED;
		} else if (s.equals("voice_recognition")) {
			return MediaRecorder.AudioSource.VOICE_RECOGNITION;
		} else if (s.equals("mic")) {
			return MediaRecorder.AudioSource.MIC;
		} else if (s.equals("camcorder")) {
			return MediaRecorder.AudioSource.CAMCORDER;
		} else if (unprocessed_supported()) {
			return MediaRecorder.AudioSource.UNPROCESSED;
		}
		return MediaRecorder.AudioSource.VOICE_RECOGNITION;
	}

		/* text in the app's language (Lang); ctx is the application
		 * context, which has no per-activity language override */
	private static String str(int id, Object... args) {
		if (ctx == null) {
			return "";
		}
		return Lang.res(ctx).getString(id, args);
	}

	/* only logged: the microphone is not shown to the user (Onno) */
	private static void toast(final String msg) {
		EventLog.ui("mic: " + msg);
		if (SHOW_MIC) {
			show(msg);
		}
	}
	private static final boolean SHOW_MIC = false;
	private static void show(final String msg) {
		if (ui == null) {
			return;
		}
		ui.post(new Runnable() {
			public void run() {
				Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
			}
		});
	}


	/* --- input devices (API 23+) --- */

	@TargetApi(23)
	private static boolean usable(AudioDeviceInfo d) {
		switch (d.getType()) {
		case AudioDeviceInfo.TYPE_BUILTIN_MIC:
		case AudioDeviceInfo.TYPE_WIRED_HEADSET:
		case AudioDeviceInfo.TYPE_USB_DEVICE:
		case AudioDeviceInfo.TYPE_USB_ACCESSORY:
		case AudioDeviceInfo.TYPE_USB_HEADSET:
		case AudioDeviceInfo.TYPE_LINE_ANALOG:
		case AudioDeviceInfo.TYPE_LINE_DIGITAL:
		case AudioDeviceInfo.TYPE_AUX_LINE:
			return true;
		}
		return false;
	}

	@TargetApi(23)
	private static boolean external(AudioDeviceInfo d) {
		return (d.getType() != AudioDeviceInfo.TYPE_BUILTIN_MIC);
	}

	@TargetApi(23)
	private static List<AudioDeviceInfo> inputs() {
		List<AudioDeviceInfo> l = new ArrayList<AudioDeviceInfo>();
		AudioManager m = am();
		if (m == null) {
			return l;
		}
		for (AudioDeviceInfo d :
				m.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
			if (usable(d)) {
				l.add(d);
			}
		}
		return l;
	}

	@TargetApi(23)
	private static String address(AudioDeviceInfo d) {
		if (Build.VERSION.SDK_INT < 28) {
			return "";
		}
		String a = d.getAddress();
		return (a == null) ? "" : a;
	}

		/* stable-ish name for the setting: device ids change when
		 * a USB mic is plugged in again, type/address/product don't */
	@TargetApi(23)
	private static String key(List<AudioDeviceInfo> l, int i) {
		String k = key1(l.get(i));
		int dup = 0;
		for (int j = 0; j < i; j++) {
			if (key1(l.get(j)).equals(k)) {
				dup++;
			}
		}
		return (dup == 0) ? k : (k + "#" + dup);
	}
	@TargetApi(23)
	private static String key1(AudioDeviceInfo d) {
		return d.getType() + ":" + address(d) + ":" + d.getProductName();
	}

	@TargetApi(23)
	private static String name(AudioDeviceInfo d) {
		switch (d.getType()) {
		case AudioDeviceInfo.TYPE_BUILTIN_MIC: {
			String place = place(d);
			return (place == null) ? str(R.string.mic_builtin)
				: str(R.string.mic_builtin_at, place);
		}
		case AudioDeviceInfo.TYPE_WIRED_HEADSET:
			return str(R.string.mic_headset);
		case AudioDeviceInfo.TYPE_USB_DEVICE:
		case AudioDeviceInfo.TYPE_USB_ACCESSORY:
		case AudioDeviceInfo.TYPE_USB_HEADSET:
			return str(R.string.mic_usb, "" + d.getProductName());
		case AudioDeviceInfo.TYPE_LINE_ANALOG:
		case AudioDeviceInfo.TYPE_LINE_DIGITAL:
		case AudioDeviceInfo.TYPE_AUX_LINE:
			return str(R.string.mic_line);
		}
		return "" + d.getProductName();
	}

	@TargetApi(23)
	private static String place(AudioDeviceInfo d) {
		String addr = address(d);
		if (Build.VERSION.SDK_INT >= 28) {
			try {
				List<MicrophoneInfo> all = am().getMicrophones();
				for (MicrophoneInfo m : all) {
					if ((m.getType() == d.getType()) &&
					    addr.equals(m.getAddress())) {
						return mic_place(m, all);
					}
				}
			} catch (Exception e) {
			}
		}
		return addr.equals("") ? null : addr;
	}

		/* where a built-in mic sits: "back" if it faces away from the
		 * user, else bottom/middle/top from its height among the
		 * built-in mics; falls back to the vendor's description */
	@TargetApi(28)
	private static String mic_place(MicrophoneInfo m,
					List<MicrophoneInfo> all) {
		MicrophoneInfo.Coordinate3F o = m.getOrientation();
		if (!MicrophoneInfo.ORIENTATION_UNKNOWN.equals(o) &&
		    (o.z < -0.5f)) {
			return str(R.string.mic_place_back);
		}
		MicrophoneInfo.Coordinate3F p = m.getPosition();
		if (!MicrophoneInfo.POSITION_UNKNOWN.equals(p)) {
			float lo = p.y, hi = p.y;
			for (MicrophoneInfo a : all) {
				MicrophoneInfo.Coordinate3F q = a.getPosition();
				if ((a.getType() != m.getType()) ||
				    MicrophoneInfo.POSITION_UNKNOWN.equals(q)) {
					continue;
				}
				lo = Math.min(lo, q.y);
				hi = Math.max(hi, q.y);
			}
			if (hi - lo > 0.02f) {
				if (p.y - lo < (hi-lo)/3.0f) {
					return str(R.string.mic_place_bottom);
				} else if (hi - p.y < (hi-lo)/3.0f) {
					return str(R.string.mic_place_top);
				}
				return str(R.string.mic_place_middle);
			}
		}
		String desc = m.getDescription();
		if ((desc != null) && !desc.equals("")) {
			return desc;
		}
		String addr = m.getAddress();
		if ((addr != null) && !addr.equals("")) {
			return addr;
		}
		return null;
	}

		/* entries and values for the "microphone" ListPreference */
	public static String[][] mic_choices() {
		List<String> names = new ArrayList<String>();
		List<String> keys = new ArrayList<String>();
		names.add(str(R.string.mic_auto));
		keys.add("auto");
		names.add(str(R.string.mic_default));
		keys.add("default");
		if (Build.VERSION.SDK_INT >= 23) {
			List<AudioDeviceInfo> l = inputs();
			for (int i = 0; i < l.size(); i++) {
				String n = name(l.get(i));
				String u = n;
				for (int dup = 2; names.contains(u); dup++) {
					u = n + " " + dup;
				}
				names.add(u);
				keys.add(key(l, i));
			}
		}
		return new String[][] {
			names.toArray(new String[0]),
			keys.toArray(new String[0])
		};
	}

	@TargetApi(23)
	private static AudioDeviceInfo find_device(String k) {
		List<AudioDeviceInfo> l = inputs();
		for (int i = 0; i < l.size(); i++) {
			if (key(l, i).equals(k)) {
				return l.get(i);
			}
		}
		return null;
	}


	/* --- signal quality of one channel --- */

	private static class Qual {
		final float[] d = new float[WIN];	/* decimated */
		int pos = 0, sub = 0;
		float acc = 0.0f;
		double sumsq = 0.0d;
		int nsamp = 0, nclip = 0;
		double floor = -1.0d;

			/* last frame */
		boolean sig = false, clip = false;
		double score = 0.0d;
		double ema = 0.0d;
			/* work out the periodicity (else score is not
			 * updated while there is a signal); and whether it
			 * was, in the last frame */
		boolean scoring = true, scored = false;

		void reset_frame() {
			pos = sub = 0;
			acc = 0.0f;
			sumsq = 0.0d;
			nsamp = nclip = 0;
		}

			/* returns true when a frame is complete */
		boolean feed(short[] b, int off, int stride, int n) {
			boolean ready = false;
			double blk = 0.0d;
			for (int i = 0; i < n; i++) {
				int s = b[off + i*stride];
				blk += (double)s*s;
				if ((s >= CLIP) || (s <= -CLIP)) {
					nclip++;
				}
				acc += s;
				if (++sub == DEC) {
					sub = 0;
					d[pos++] = acc / DEC;
					acc = 0.0f;
					if (pos == WIN) {
						pos = 0;
						ready = true;
					}
				}
			}
			sumsq += blk;
			nsamp += n;
			double rms = Math.max(Math.sqrt(blk/n), 1.0d);
			if ((floor < 0.0d) || (rms < floor)) {
				floor = rms;
			} else {
				floor *= FLOOR_RISE;
			}
			if (ready) {
				frame();
			}
			return ready;
		}

		private void frame() {
			double rms = Math.sqrt(sumsq/Math.max(nsamp, 1));
			double snr = 20.0d*Math.log10(Math.max(rms, 1.0d)/floor);
			clip = (nclip*500 > nsamp);
			sig = (rms > MIN_RMS) && (snr > MIN_SNR);
			scored = false;
			if (!sig) {
				score = 0.0d;
			} else if (scoring) {
				score = periodicity(d) *
					Math.min(1.0d, snr/FULL_SNR) *
					(clip ? 0.5d : 1.0d);
				scored = true;
			}
			sumsq = 0.0d;
			nsamp = nclip = 0;
		}

		void smooth() {
			ema += EMA*(score - ema);
		}
	}

	private static final double[] px = new double[WIN];

	private static double periodicity(float[] d) {
		double mean = 0.0d;
		for (int i = 0; i < WIN; i++) {
			mean += d[i];
		}
		mean /= WIN;
		double e0 = 0.0d;
		for (int i = 0; i < WIN; i++) {
			px[i] = d[i] - mean;
			if (i < CORR) {
				e0 += px[i]*px[i];
			}
		}
		if (e0 <= 0.0d) {
			return 0.0d;
		}
		double et = e0, best = 0.0d;
		boolean crossed = false;
		for (int lag = 1; lag <= WIN-CORR; lag++) {
			et += px[lag-1+CORR]*px[lag-1+CORR] - px[lag-1]*px[lag-1];
			double r = 0.0d;
			for (int i = 0; i < CORR; i++) {
				r += px[i]*px[i+lag];
			}
			r /= Math.sqrt(e0*Math.max(et, 1e-9));
			if (!crossed) {
				crossed = (r < 0.0d);
			} else if (r > best) {
				best = r;
			}
		}
		return best;
	}


	/* --- the recorder (used on the Listener thread only) --- */

	private static final int FIXED = 0;	/* no switching */
	private static final int STEREO = 1;	/* auto, pick a channel */
	private static final int PROBE = 2;	/* auto, try other devices */

	private AudioRecord rec = null;
	private int source;
	private int mode = FIXED;
	private int channels = 1;
	private short[] sbuf = null;
	private final Qual[] q = { new Qual(), new Qual() };
	private int ch = 0;			/* channel in use */
	private String[] ch_name = { str(R.string.mic_channel, 1),
				     str(R.string.mic_channel, 2) };
	private int streak = 0;
	private long last_switch = 0;

	private int check_blocks = 0;		/* stereo check */
	private long check_n = 0, check_same = 0;
	private double check_e0 = 0.0d, check_e1 = 0.0d;

	private Object[] devs = null;		/* PROBE: AudioDeviceInfo */
	private int dev_i = -1;
	private int prev_i = -1;
	private boolean probing = false;
	private int probe_frames = 0, probe_n = 0;
	private double probe_sum = 0.0d, probe_before = 0.0d;
	private long last_probe = 0;

	public int samplerate() {
		return RATE;
	}

	public boolean open(int bufsz) {
		sbuf = new short[2*bufsz];
		source = source_for(parms.audio_source);
		String mic = parms.microphone;
		Object dev = null;
		if (!mic.equals("auto") && !mic.equals("default") &&
		    (Build.VERSION.SDK_INT >= 23)) {
			dev = find_device(mic);
			if (dev == null) {
				toast(str(R.string.mic_not_found));
			}
		}
		if (mic.equals("auto")) {
			if (Build.VERSION.SDK_INT >= 23) {
				dev = auto_external();
			}
			if ((dev == null) && create(source, 2, null)) {
				mode = STEREO;
				name_channels();
				active_name = ch_name[ch];
				return true;
			}
			if (dev == null) {
				mode = PROBE;
			}
		}
		return open_mono(dev);
	}

	private boolean open_mono(Object dev) {
		channels = 1;
		if (!create(source, 1, dev) &&
		    !((source != MediaRecorder.AudioSource.MIC) &&
		      create(MediaRecorder.AudioSource.MIC, 1, dev))) {
			return false;
		}
		if ((mode == PROBE) && (Build.VERSION.SDK_INT >= 23)) {
			probe_setup();
		}
		active_name = routed_name();
		return true;
	}

	@TargetApi(23)
	private Object auto_external() {
		for (AudioDeviceInfo d : inputs()) {
			if (external(d)) {
				return d;
			}
		}
		return null;
	}

	@TargetApi(23)
	private void probe_setup() {
		if (devs == null) {
			List<AudioDeviceInfo> l = new ArrayList<AudioDeviceInfo>();
			for (AudioDeviceInfo d : inputs()) {
				if (!external(d)) {
					l.add(d);
				}
			}
			devs = l.toArray();
		}
		AudioDeviceInfo r = (Build.VERSION.SDK_INT >= 24) ?
					rec.getRoutedDevice() : null;
		dev_i = 0;
		for (int i = 0; (r != null) && (i < devs.length); i++) {
			if (((AudioDeviceInfo)devs[i]).getId() == r.getId()) {
				dev_i = i;
			}
		}
	}

	private String routed_name() {
		if (Build.VERSION.SDK_INT < 24) {
			return null;
		}
		AudioDeviceInfo r = rec.getRoutedDevice();
		return (r == null) ? null : name(r);
	}

		/* nchan 1 or 2; dev is an AudioDeviceInfo or null */
	private boolean create(int src, int nchan, Object dev) {
		int mask = (nchan == 2) ? AudioFormat.CHANNEL_IN_STEREO
					: AudioFormat.CHANNEL_IN_MONO;
		int ar_bufsz = AudioRecord.getMinBufferSize(RATE, mask,
				AudioFormat.ENCODING_PCM_16BIT);
		if (ar_bufsz <= 0) {
			return false;
		}
		ar_bufsz *= 2;		/* at least two hardware buffers */
				/* at least 250ms */
		ar_bufsz = Math.max(ar_bufsz, (RATE/4)*2*nchan);
		AudioRecord r;
		try {
			r = new AudioRecord(src, RATE, mask,
				AudioFormat.ENCODING_PCM_16BIT, ar_bufsz);
		} catch (IllegalArgumentException | SecurityException e) {
			System.out.println("AudioRecord: " + e);
			return false;
		}
		if (r.getState() != AudioRecord.STATE_INITIALIZED) {
			System.out.println("failed to initialize audio (source " +
					src + ", " + nchan + " channels)");
			r.release();
			return false;
		}
		if ((r.getAudioFormat() != AudioFormat.ENCODING_PCM_16BIT) ||
		    (r.getChannelCount() != nchan) ||
		    (r.getSampleRate() != RATE)) {
			System.out.println("got funny format/channels back from AudioRecord!");
			r.release();
			return false;
		}
		if ((dev != null) && (Build.VERSION.SDK_INT >= 23)) {
			r.setPreferredDevice((AudioDeviceInfo)dev);
		}
		try {
			r.startRecording();
		} catch (IllegalStateException e) {
			System.out.println("illegal state for startRecording()");
			r.release();
			return false;
		}
		if (r.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
			r.release();
			return false;
		}
		rec = r;
		channels = nchan;
		return true;
	}

	public void close() {
		if (rec == null) {
			return;
		}
		switches++;
		try {
			rec.stop();
		} catch (IllegalStateException e) { /* whatever */ }
		rec.release();
		rec = null;
	}

	private void name_channels() {
		if (Build.VERSION.SDK_INT < 28) {
			return;
		}
		try {
			List<MicrophoneInfo> act = rec.getActiveMicrophones();
			List<MicrophoneInfo> all = am().getMicrophones();
			String[] n = { null, null };
			for (MicrophoneInfo m : act) {
				for (Pair<Integer,Integer> p : m.getChannelMapping()) {
					int c = p.first.intValue();
					if ((c >= 0) && (c < 2) && (n[c] == null)) {
						n[c] = mic_place(m, all);
					}
				}
			}
			for (int c = 0; c < 2; c++) {
				if (n[c] != null) {
					ch_name[c] = str(R.string.mic_channel_at,
							 c+1, n[c]);
				}
			}
		} catch (Exception e) {
		}
	}

		/* fills dst[0..len) with mono samples; false on failure */
	public boolean read(short[] dst, int len) {
		if (rec == null) {
			return false;
		}
		if (channels == 1) {
			if (rec.read(dst, 0, len) != len) {
				return false;
			}
			if ((mode == PROBE) && (devs != null) &&
			    (devs.length >= 2) && q[0].feed(dst, 0, 1, len)) {
				probe_decide();
			}
			return true;
		}
		if (rec.read(sbuf, 0, 2*len) != 2*len) {
			return false;
		}
		for (int i = 0; i < len; i++) {
			dst[i] = sbuf[2*i+ch];
		}
		if (check_blocks < CHECK_BLOCKS) {
			if (!stereo_check(len)) {
				/* one mic copied twice: go mono */
				close();
				mode = PROBE;
				return open_mono(null);
			}
		}
		boolean r0 = q[0].feed(sbuf, 0, 2, len);
		boolean r1 = q[1].feed(sbuf, 1, 2, len);
		if (r0 && r1) {
			stereo_decide();
		}
		return true;
	}

		/* false once the channels have turned out to be the same */
	private boolean stereo_check(int len) {
		double e0 = 0.0d, e1 = 0.0d;
		int same = 0;
		for (int i = 0; i < len; i++) {
			short a = sbuf[2*i], b = sbuf[2*i+1];
			e0 += (double)a*a;
			e1 += (double)b*b;
			if (a == b) {
				same++;
			}
		}
		if ((e0 == 0.0d) && (e1 == 0.0d)) {
			return true;	/* digital silence while starting */
		}
		check_blocks++;
		check_n += len;
		check_same += same;
		check_e0 += e0;
		check_e1 += e1;
		if (check_blocks < CHECK_BLOCKS) {
			return true;
		}
		boolean dead = (check_e0 == 0.0d) || (check_e1 == 0.0d);
		boolean copy = (check_same*100 > check_n*99);
		if (dead || copy) {
			System.out.println("MicSelect: stereo is one mic, mono");
			return false;
		}
		return true;
	}

	private void stereo_decide() {
		Qual cur = q[ch], oth = q[1-ch];
		cur.scoring = true;
		if (!cur.sig && !oth.sig) {
			streak = 0;
			oth.scoring = true;	/* no signal: costs nothing */
			return;
		}
		cur.smooth();
		if (oth.scored || !oth.sig) {
			oth.smooth();
		}
		boolean doubtful = !cur.sig || cur.clip || (cur.ema < DOUBT);
		boolean better = oth.sig && oth.scored &&
				 (oth.ema > cur.ema + MARGIN);
		streak = (doubtful && better) ? (streak+1) : 0;
		/* the other channel is only of interest while this one is
		 * doubtful; from the next frame on */
		oth.scoring = doubtful;
		long now = SystemClock.elapsedRealtime();
		if ((streak >= SWITCH_FRAMES) && (now - last_switch >= HOLD_MS) &&
		    !mixture.busy) {
			ch = 1-ch;
			q[ch].scoring = true;
			switches++;
			streak = 0;
			last_switch = now;
			active_name = ch_name[ch];
			toast(str(R.string.mic_now, ch_name[ch]));
		}
	}

	private void probe_decide() {
		Qual c = q[0];
		long now = SystemClock.elapsedRealtime();
		if (probing) {
			if (++probe_frames <= PROBE_SETTLE) {
				return;
			}
			if (c.sig) {
				probe_sum += c.score;
				probe_n++;
			}
			if (probe_frames < PROBE_SETTLE+PROBE_FRAMES) {
				return;
			}
			double avg = (probe_n > 0) ? (probe_sum/probe_n) : 0.0d;
			probing = false;
			last_probe = now;
			streak = 0;
			if (avg > probe_before + MARGIN) {
				c.ema = avg;
				last_switch = now;
				toast(str(R.string.mic_now, active_name));
			} else {
				reopen(prev_i);
				c.ema = probe_before;
			}
			return;
		}
		if (!c.sig) {
			streak = 0;
			return;
		}
		c.smooth();
		boolean doubtful = c.clip || (c.ema < DOUBT);
		streak = doubtful ? (streak+1) : 0;
		if ((streak >= PROBE_AFTER) &&
		    (now - last_probe >= PROBE_GAP_MS) &&
		    (now - last_switch >= HOLD_MS) && !mixture.busy) {
			probing = true;
			probe_frames = probe_n = 0;
			probe_sum = 0.0d;
			probe_before = c.ema;
			prev_i = dev_i;
			if (!reopen((dev_i+1) % devs.length)) {
				probing = false;
				last_probe = now;
				if (mode == PROBE) {	/* else it gave up */
					reopen(prev_i);
				}
			}
		}
	}

		/* switches the mono recorder to devs[i]; the noise floor is
		 * carried over, assuming the mics are alike.  if that fails,
		 * back to the default route; if that fails as well, to plain
		 * MIC with no more probing, rather than ending the Listener
		 * (read() fails without a recorder) without a word */
	private boolean reopen(int i) {
		close();
		q[0].reset_frame();
		if (!create(source, 1, devs[i])) {
			if (!create(source, 1, null)) {
				mode = FIXED;
				if ((source != MediaRecorder.AudioSource.MIC) &&
				    create(MediaRecorder.AudioSource.MIC, 1,
					   null)) {
					source = MediaRecorder.AudioSource.MIC;
					toast(str(R.string.mic_reopen_default));
				} else {
					toast(str(R.string.mic_reopen_failed));
				}
			}
			active_name = (rec != null) ? routed_name() : null;
			return false;
		}
		dev_i = i;
		if (Build.VERSION.SDK_INT >= 23) {
			active_name = name((AudioDeviceInfo)devs[i]);
		}
		return true;
	}
}
