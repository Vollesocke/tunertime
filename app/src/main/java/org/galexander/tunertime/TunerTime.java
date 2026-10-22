package org.galexander.tunertime;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import java.io.File;
import java.util.Arrays;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.widget.Button;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.os.PowerManager;
import android.os.SystemClock;
import android.app.Activity;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;
import java.text.DecimalFormat;
import java.util.Locale;


public class TunerTime extends Activity
		implements SurfaceHolder.Callback,
			   ActivityCompat.OnRequestPermissionsResultCallback {
	private SurfaceView w_strobe;
	private ViewGroup buttons_holder;
	private ViewGroup surface_frame;
	private ViewGroup controls_frame;
	private ViewGroup preset_nav_holder;
	private View preset_workbench;
	private TextView preset_workbench_static_name;
	private Button preset_workbench_wolf;
	private TextView preset_workbench_summary;
	private LinearLayout preset_workbench_notes;
	private ViewGroup mode_holder;
	private TextView center_display;
	private TextView stretch_display;
	private View note_buttons;
	private TextView note_semi_display;
	private TextView note_cent_display;
	private View afreq_buttons;
	private TextView afreq_center_display;
	private View stretch_buttons;
	private TextView stretch_stretch_display;
	private View mixture_buttons;
	private TextView mixture_display;
	private TextView mixture_status;
	private TextView mixture_pitch;
	private final mixture mix = new mixture();
	private View preset_buttons;	/* the temperament tab: none */
	private View fft_buttons;
	private TextView N_display;
	private Button grid_button;
	private View strobe_buttons;
	private Button note_button, afreq_button, stretch_button, preset_button;
	private View strobe_mode_buttons;
	private View fft_mode_buttons;
	private Button tone_button;
	private Button auto_button;
	private Button mode_button;
	private View length_panel;
	private pipelength length;

	private PowerManager.WakeLock wakelock = null;

		/* cents values are in tenths of a cent */
	private static final int U = preset.UNITS_PER_CENT;

	private Strobe strobe = null;
	private SurfaceHolder holder = null;
	private int width = 0;
	private int height = 0;

	/* the language this activity was created in (Lang) */
	private String lang = null;

	protected void attachBaseContext(Context base) {
		super.attachBaseContext(Lang.wrap(base));
	}

	protected void onRestart() {
		super.onRestart();
		/* the language was changed in Settings (before Android 13;
		 * later versions recreate the activity themselves) */
		if (!Lang.get(this).equals(lang)) {
			recreate();
		}
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		CrashLog.install(this);
		lang = Lang.get(this);

		parms.init(this);
		setContentView(R.layout.main);
		w_strobe = (SurfaceView)findViewById(R.id.strobe);
		w_strobe.getHolder().addCallback(this);
		w_strobe.setOnTouchListener(new strobe_listener());
		buttons_holder = (ViewGroup)findViewById(R.id.buttons_holder);
		surface_frame = (ViewGroup)findViewById(R.id.surface_frame);
		controls_frame = (ViewGroup)findViewById(R.id.controls_frame);
		preset_nav_holder = (ViewGroup)findViewById(R.id.preset_nav_holder);
		mode_holder = (ViewGroup)findViewById(R.id.mode_holder);
		center_display = (TextView)findViewById(R.id.center_display);
		stretch_display = (TextView)findViewById(R.id.stretch_display);

		LayoutInflater fl8r = (LayoutInflater)getSystemService(
				Context.LAYOUT_INFLATER_SERVICE);
		note_buttons = fl8r.inflate(
				R.layout.note_buttons, buttons_holder, false);
		note_semi_display = (TextView)note_buttons.findViewById(
				R.id.semi_display);
		note_cent_display = (TextView)note_buttons.findViewById(
				R.id.cent_display);
		((Button)note_buttons.findViewById(R.id.semi_plus))
			.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				incr_cents(700*U, "fifth+");
				return true;
			}
		});
		((Button)note_buttons.findViewById(R.id.semi_minus))
			.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				incr_cents(-700*U, "fifth-");
				return true;
			}
		});

		afreq_buttons = fl8r.inflate(
				R.layout.afreq_buttons, buttons_holder, false);
		afreq_center_display = (TextView)afreq_buttons.findViewById(
				R.id.center_display2);
		/* tapping the A opens a keypad: some organs stand near 480 Hz,
		 * many taps away (Onno) */
		afreq_center_display.setMinimumHeight((int)(48 *
			getResources().getDisplayMetrics().density));
		afreq_center_display.setGravity(android.view.Gravity.CENTER);
		afreq_center_display.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				afreq_dialog();
			} });

		stretch_buttons = fl8r.inflate(
				R.layout.stretch_buttons, buttons_holder,false);
		stretch_stretch_display = (TextView)stretch_buttons.
				findViewById(R.id.stretch_display2);

		mixture_buttons = fl8r.inflate(
				R.layout.mixture_buttons, buttons_holder,false);
		mixture_display = (TextView)mixture_buttons.
				findViewById(R.id.mixture_display);
		mixture_status = (TextView)mixture_buttons.
				findViewById(R.id.mixture_status);
		mixture_pitch = (TextView)mixture_buttons.
				findViewById(R.id.mixture_pitch);

		preset_buttons = fl8r.inflate(
				R.layout.preset_buttons, buttons_holder, false);
		preset_workbench = fl8r.inflate(
				R.layout.preset_workbench, surface_frame, false);
			preset_workbench_static_name = (TextView)preset_workbench.findViewById(
				R.id.preset_workbench_static_name);
			preset_workbench_wolf = (Button)preset_workbench.findViewById(
				R.id.preset_workbench_wolf);
		preset_workbench_summary = (TextView)preset_workbench.findViewById(
				R.id.preset_workbench_summary);
		preset_workbench_notes = (LinearLayout)preset_workbench.findViewById(
				R.id.preset_workbench_notes);

		fft_buttons = fl8r.inflate(
				R.layout.fft_buttons, buttons_holder, false);
		N_display = (TextView)fft_buttons.findViewById(
				R.id.N_display);
		grid_button = (Button)fft_buttons.findViewById(
				R.id.grid_button);

		strobe_buttons = fl8r.inflate(
				R.layout.strobe_buttons, surface_frame, false);
		note_button = (Button)strobe_buttons.findViewById(
				R.id.note_button);
		afreq_button = (Button)strobe_buttons.findViewById(
				R.id.afreq_button);
		stretch_button = (Button)strobe_buttons.findViewById(
				R.id.stretch_button);
		preset_button = (Button)strobe_buttons.findViewById(
				R.id.preset_button);
		/* long press: choose a preset (a temperament for note mode)
		 * without leaving the current mode, so following goes on at
		 * the note it was at */
		preset_button.setOnLongClickListener(
				new View.OnLongClickListener() {
			public boolean onLongClick(View v) {
				EventLog.ui("preset select (long press)");
				do_preset_select(v);
				return true;
			} });

		PowerManager pm = (PowerManager)getSystemService(
						Context.POWER_SERVICE);
		wakelock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
						"TunerTime");

		strobe_mode_buttons = fl8r.inflate(
				R.layout.strobe_mode, mode_holder, false);
		tone_button = (Button)strobe_mode_buttons.findViewById(
				R.id.tone_button);
		auto_button = (Button)strobe_mode_buttons.findViewById(
				R.id.auto_button);
		mode_button = (Button)strobe_mode_buttons.findViewById(
				R.id.mode_button);
		fft_mode_buttons = fl8r.inflate(
				R.layout.fft_mode, mode_holder, false);

		length_panel = fl8r.inflate(
				R.layout.length_panel, surface_frame, false);
		length = new pipelength(this, length_panel);
	}

	public void onStart() {
		super.onStart();
		request_permission();
	}
	public void onResume() {
		wakelock.acquire();
		stop_strobe();
		parms.load();
		EventLog.on = DebugRecorder.enabled(this);
		cause = "resume";
		if (parms.organ_mode) {
			parms.fft_mode = false;	/* length mode replaces it */
		} else {
			parms.length_mode = false;
		}
		organ_resume();
		if (parms.fft_mode) {
			set_mode(fft_buttons, true);
		} else if (parms.mixture_mode) {
			set_mode(mixture_buttons, false);
		} else {
			set_mode((parms.preset_mode ? preset_buttons
						    : note_buttons), false);
		}
		update_status();
		if (holder != null) {
			/* lingering surface must still be valid */
			start_strobe();
		}
		super.onResume();
	}

	public void onPause() {
		run_stop("app paused");
		if (wakelock.isHeld()) {
			wakelock.release();
		}
		stop_strobe();
		ToneGenerator.stop_tone();
		parms.save();
		super.onPause();
	}

	/* the tab of the current mode is shown selected (underlined) */
	private void darken_mode(Button b) {
		b.setSelected(false);
	}
	private void brighten_mode(Button b) {
		b.setSelected(true);
	}
	/* the preset or temperament goes in the action bar's subtitle, so
	 * that neither it nor the title is cut short.  only set when it
	 * changes, because update_status() calls this all the time */
	private String shown_title = null, shown_subtitle = null;
	private void set_title(String title, String subtitle) {
		if (!title.equals(shown_title)) {
			setTitle(title);
			shown_title = title;
		}
		if ((subtitle == null) ? (shown_subtitle != null)
				       : !subtitle.equals(shown_subtitle)) {
			if (getActionBar() != null) {
				getActionBar().setSubtitle(subtitle);
			}
			shown_subtitle = subtitle;
		}
	}

	private void set_mode(View buttons, boolean fft_mode_) {
		if (parms.run_active && (fft_mode_ || (buttons == preset_buttons))) {
			/* the detector stops in FFT mode, and the Preset tab
			 * hides the note stepped: not during a chromatic run */
			cause = null;
			Toast.makeText(this, R.string.run_tab_blocked,
					Toast.LENGTH_SHORT).show();
			return;
		}
		String title = null, subtitle = null;
		String why = cause;	/* entering mixture mode logs too */
		if (fft_mode_ == false) {
			darken_mode(note_button);
			darken_mode(afreq_button);
			darken_mode(stretch_button);
			darken_mode(preset_button);
			if (buttons == note_buttons) {
				brighten_mode(note_button);
			} else if (buttons == afreq_buttons) {
				brighten_mode(afreq_button);
			} else if ((buttons == stretch_buttons) ||
				   (buttons == mixture_buttons)) {
				brighten_mode(stretch_button);
			} else if (buttons == preset_buttons) {
				brighten_mode(preset_button);
			}
			/* the focused row stays with its mode: note mode and
			 * the A tab share one, mixture mode has its own (Onno,
			 * 22 Oct) */
			parms.keep_focus();
			boolean was_mix = parms.mixture_mode;
			boolean was_preset = parms.preset_mode;
			/* the temperament tab is a view only: it neither
			 * moves the note nor focuses a row, and leaving it the
			 * note is what it was (Onno, 22 Oct; it took note mode
			 * to its own note, C, before) */
			parms.preset_mode = (buttons == preset_buttons);
			mixture_set(buttons == mixture_buttons);
			if (!parms.mixture_mode) {
				parms.use_focus();
			}
			if (((was_mix != parms.mixture_mode) ||
			     (was_preset != parms.preset_mode)) &&
			    parms.length_mode && !parms.preset_mode) {
				length_focus();	/* as on entering length mode */
			}
			if (parms.mixture_mode) {
				/* the readout rounds to this temperament */
				title = getString(R.string.title_mixture);
				if (parms.temper_offsets() != null) {
					subtitle = preset.find(parms.preset_id).name;
				}
			} else if (parms.preset_mode) {
				preset p = preset.find(parms.preset_id);
				if (p == null) {
					parms.preset_note = 0;
					parms.preset_oct = 0;
					if (preset.presets.size() >= 1) {
						p = preset.presets.get(0);
						parms.preset_id = p.id;
					}
				}
				title = getString(R.string.title_preset_workbench);
			} else {
				title = getString(R.string.title_strobe);
			}
		} else {
			title = getString(R.string.title_fft);
			parms.fft_xoffset = Strobe.FFT_RANGE_MULT;
			mixture_set(false);
		}
		buttons_holder.removeAllViews();
		buttons_holder.addView(buttons);
		parms.fft_mode = fft_mode_;
		place_strobe_extras();
		mode_holder.removeAllViews();
		mode_holder.addView(parms.fft_mode ? fft_mode_buttons
						   : strobe_mode_buttons);
		parms.changed = true;
		set_title(title, subtitle);
		if (parms.fft_mode) ToneGenerator.stop_tone();

		cause = why;
		update_status();
	}


	/* below the strobes: the length readout (length mode), then the
	 * strobe-mode tab bar */
	private void place_strobe_extras() {
		surface_frame.removeView(length_panel);
		surface_frame.removeView(strobe_buttons);
		surface_frame.removeView(preset_workbench);
		if (strobe_buttons.getParent() != null) {
			((ViewGroup)strobe_buttons.getParent()).removeView(strobe_buttons);
		}
		if (parms.preset_mode) {
			w_strobe.setVisibility(View.GONE);
			controls_frame.setVisibility(View.GONE);
			preset_nav_holder.setVisibility(View.VISIBLE);
			preset_nav_holder.addView(strobe_buttons,
				new FrameLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT,
					(int)(56 * getResources().getDisplayMetrics()
						.density)));
			surface_frame.addView(preset_workbench,
				new ViewGroup.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.MATCH_PARENT));
		} else {
			w_strobe.setVisibility(View.VISIBLE);
			controls_frame.setVisibility(View.VISIBLE);
			preset_nav_holder.setVisibility(View.GONE);
			if (mode_holder.getParent() == preset_nav_holder) {
				ViewGroup old_parent = (ViewGroup)mode_holder.getParent();
				old_parent.removeView(mode_holder);
				mode_holder.setLayoutParams(new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
				((LinearLayout)findViewById(R.id.main_controls_side))
					.addView(mode_holder);
			}
		}
		if (!parms.fft_mode) {
			if (!parms.preset_mode && parms.length_mode) {
				surface_frame.addView(length_panel);
			}
			if (!parms.preset_mode) surface_frame.addView(strobe_buttons);
		}
		/* in organ mode a toggle, highlighted while the length
		 * panel is shown */
		mode_button.setText(parms.organ_mode ? R.string.mode_length
						     : R.string.mode_fft);
		mode_button.setSelected(parms.organ_mode && parms.length_mode);
	}

	public void do_mode(View v) {
		cause = parms.organ_mode ? "length button" : "fft button";
		if (parms.organ_mode) {
			/* organ mode toggles length mode instead of FFT */
			parms.length_mode = !parms.length_mode;
			if (parms.length_mode) {
				length_focus();
			}
			length.clear();
			place_strobe_extras();
			update_status();
			return;
		}
		if (parms.fft_mode) {
			set_mode(note_buttons, false);
		} else {
			set_mode(fft_buttons, true);
		}
	}

	/* entering length mode with no row focused focuses the 8' row (the
	 * pipe's own fundamental), if it is shown and not too high: the
	 * detector alone reads up to 1.5 c low below 8' C while showing
	 * stable (about 4.5 mm on a 32' stopped C), the row's strobe drift
	 * holds to a few hundredths of a cent.  shown by the focus frame;
	 * a row the user focused is left as it is, and tapping the row
	 * releases it as usual */
	private void length_focus() {
		if (parms.focus_row() >= 0) {
			return;
		}
		for (int i = 0; i < parms.num_strobes; i++) {
			if (parms.row_ratio(i) == 1.0d) {
				if (!Strobe.row_too_high(this, i)) {
					parms.focus_ratio = 1.0d;
				}
				return;
			}
		}
	}

	/* auto note detection: a tap toggles follow and locked (do_auto).
	 * the state is parms.auto_note, kept across rotation */
	private int det_semi = Integer.MIN_VALUE, det_count = 0;
	private int det_alt = 0;	/* with det_semi: parms.note_alt */
	/* estimates in a row.  4 rather than 3 (tools/sim_organ.c -a):
	 * loud speech near the phone moves the note 3 times in 40 s
	 * instead of 6, a Principal with speech around it never to a wrong
	 * note (3 before), for about 0.09 s more latency */
	private static final int DET_AGREE = 4;

	/* Corrections while following: continuity.  Decided with the organ
	 * tuner: a correction by hand applies to the current note only, and
	 * as a stop is tuned key by key, without big jumps, the notes heard
	 * after it are looked for near the current note.  So once the user
	 * moves the note by hand (semitone, octave or fifth buttons; in
	 * mixture mode the key and octave buttons, which before did not
	 * count as a correction: Onno, 21 Oct) while
	 * follow is on, continuity is on: each detection is taken in the
	 * octave that puts it nearest the note now shown, and only moves
	 * the note if that is within NEAR_SEMIS (a fourth, 500 cents)
	 * either way; a tritone off is ignored.  Corrected a 4' stop down
	 * an octave, the next keys follow in the 8' octave; a note that
	 * was slow to be detected and set by hand does not shift the
	 * following ones (v18.1 remembered the interval to the last
	 * detection and added it to all later ones).
	 * Not carried over: the detected note's partial.  A mutation (a
	 * Quinte 2 2/3' sounds a twelfth above the key) is folded into the
	 * octave around the note shown, not divided by 3: focus its row in
	 * organ mode, then the detector itself reports the key (pitch.c
	 * focus()).  Both together do not add up, as folding a note already
	 * near the current one changes nothing.
	 * Continuity ends, and detections are taken as heard again, when
	 *  - a chromatic run starts (run_lock),
	 *  - nothing clear was heard for NEAR_SILENCE ms (a new stop).
	 * Lock and unlock keep it.  tools/sim_organ.c -f new emulates it.
	 * The correction holds while the key sounds: the note the detector
	 * agreed on when the user overruled it is not followed again until
	 * it agrees on another one, or the input stays down at its noise
	 * floor for HOLD_QUIET ms (the key released: the next one may be the
	 * note it heard, a semitone up in a chromatic run; v18.5 took
	 * HOLD_QUIET ms of empty estimates, which a soft pipe gives now and
	 * then while it sounds in a noisy place).  Before, the next estimates
	 * undid it within half a second wherever the detector kept hearing
	 * the wrong note, and always a Dis/Es picked by hand (sim_organ -f
	 * new: 28 of 55 corrections undone with the key still held, none
	 * now; Onno's runs of 21 Oct, run_score.py: follow unchanged, 187
	 * of 208 keys, 185 with the hold kept over the release). */
	private boolean near = false;		/* continuity on */
	private int overruled = Integer.MIN_VALUE, overruled_alt = 0;
	private static final int NEAR_SEMIS = 5;
	/* Mixture mode following the reference rank (Settings,
	 * parms.mixture_focus_follow) keeps to the key: a mixture is tuned
	 * key by key, and the detector, looking for the focused rank, reads
	 * another rank sounding alone as a key an octave, a fourth or a
	 * third away (2 2/3', 1 3/5' with the 2' focused).  Octaves fold,
	 * and the key moves by at most a whole tone; a bigger jump is made
	 * by hand (key and octave buttons).  tools/sim_organ.c -x focus
	 * against the default, wrong keys shown: "Mixture III muted" 0
	 * instead of 4, "Mixture chromatic, ranks alone" 0 instead of 8 */
	private static final int MIX_SEMIS = 2;
	private int det_stable = Integer.MIN_VALUE;	/* last agreed note */
	private int det_stable_alt = 0;
	private double det_stable_hz = 0.0;
	private long det_stable_time = 0, last_heard = 0;
	private static final long CORR_RECENT = 4000;
	private static final long NEAR_SILENCE = 10000;
	private static final long HOLD_QUIET = 300;
	/* the floor (pitch.c pitch_floor) rises only after 0.7 s with no
	 * note heard (up to v18.8 under the notes too: in a long pass it
	 * crept up to soft keys held long, which were then taken for
	 * released).  Over that floor 1.6 rather than 1.4 (pitch.c QUIET)
	 * sees a release as soon as before.  tools/field (runs of 21 and
	 * 22 Oct, 239 keys): taken for released while the key sounds 16
	 * times -> 6, the release seen before the next key 14 times either
	 * way (median 0.68 s) */
	private static final double KEY_QUIET = 1.6;
	private static final double MIN_HZ = 15.0;	/* pitch.c MIN_F0 */
	private static final double KEY_MIN = 20.0;	/* pitch.c LOW_RMS:
							 * digital silence */
	private long last_loud = 0;

	/* the auto button: a tap toggles follow and locked (fixiert), no
	 * long press.  Locked leaves the note alone and keeps continuity,
	 * so following again goes on near the note shown.  Before v18.9
	 * there was a third state, off, which differed from locked only in
	 * clearing continuity (Onno, 21 Oct: "what is the difference?"):
	 * not worth a state, as a correction by hand sets continuity again
	 * and NEAR_SILENCE clears it */
	public void do_auto(View v) {
		if (!auto_applies()) {
			return;
		}
		cause = "auto tap";
		parms.auto_note = !parms.auto_note;
		if (parms.auto_note) {
			/* the silence that ends continuity counts from here */
			last_heard = SystemClock.uptimeMillis();
		}
		det_count = 0;
		update_status();
	}

	/* a chromatic run steps the note by hand: locked, continuity
	 * cleared; run_stop() restores the state from before */
	private boolean run_auto_before = false;
	private void run_lock() {
		run_auto_before = parms.auto_note;
		parms.auto_note = false;
		det_count = 0;
		near = false;
		det_stable = Integer.MIN_VALUE;
		overruled = Integer.MIN_VALUE;
	}

	/* Preset and FFT modes have their own navigation.  Tone and mixture
	 * modes both follow the detector. */
	private boolean auto_applies() {
		return !parms.preset_mode && !parms.fft_mode &&
			!parms.run_active;	/* the note shown is the truth */
	}

	/* from the strobe thread: the estimate, and the input level and its
	 * noise floor (pitch.h) */
	public void pitch_detected(final double hz, final double level,
				   final double floor) {
		runOnUiThread(new Runnable() {
			public void run() {
				on_pitch(hz, level, floor);
			} });
	}
	/* the same without levels (tests): a note heard is loud, nothing
	 * heard is quiet */
	public void pitch_detected(double hz) {
		pitch_detected(hz, (hz > 0.0) ? 1000.0 : 0.0, 1.0);
	}

	/* moves the target to the nearest note once DET_AGREE estimates in a
	 * row agree; silence holds the current note.  the cents offset set
	 * by hand carries over to the new note.  after a correction, only
	 * near the current note (continuity, above). */
	private void on_pitch(double hz, double level, double floor) {
		if (hz < MIN_HZ) {
			/* under the lowest organ C: only from a high focused
			 * row's partial divided down (pitch.c focus()) */
			hz = 0.0d;
		}
		a_heard(hz);
		if (parms.mixture_mode && (hz > 0.0d)) {
			/* Dis or Es by the nearer, where both were picked */
			int[] hn = parms.nearest_note(freq_to_cents(hz));
			int hc = parms.chain_at(hn[0]*100*U, hn[1]);
			note h = new note(hn[0]*100*U);
			mixture_pitch.setText((hc != parms.NO_CHAIN) ?
				h.spelled(hc) : h.organ_name());
			if (mix.heard(hz, cents_to_freq(parms.strobe_cents, 1),
				      parms.strobe_cents,
				      parms.mixture_focus_follow &&
				      (parms.focus_row() >= 0))) {
				update_mixture_status();
			}
		}
		if (parms.length_mode && !ToneGenerator.playing) {
			length.add(hz);
		}
		long now = SystemClock.uptimeMillis();
		if (hz > 0.0) {
			last_heard = now;
		} else if (near && (now - last_heard > NEAR_SILENCE)) {
			near = false;
			overruled = Integer.MIN_VALUE;
			cause = "10 s silence";
			log_changes();
		}
		if ((level >= KEY_QUIET*floor) && (level >= KEY_MIN)) {
			last_loud = now;
		} else if (now - last_loud > HOLD_QUIET) {
			/* the key released: down to the noise floor, not
			 * just the detector hearing nothing clear, which a
			 * soft pipe in a noisy place does now and then
			 * while it sounds (Onno: the hand always wins) */
			overruled = Integer.MIN_VALUE;
		}
		if (!parms.auto_note || !auto_applies() ||
		    ToneGenerator.playing || (hz <= 0.0)) {
			det_count = 0;
			return;
		}
		int semi = 100*U;
		int cur, off;
		if (parms.mixture_mode) {
			/* the mixture's key, and its measured offset from the
			 * tempered note: the organ's own pitch, which the keys
			 * heard next share */
			cur = nearest_step(parms.mixture_cents);
			off = parms.mixture_cents - step_cents(cur);
		} else {
			cur = (parms.note_cents + semi/2) / semi;
			off = parms.note_cents - cur*semi;
		}
		/* Dis or Es, where the pitch class has both */
		int cur_alt = Math.min(parms.note_alt,
				       parms.temper_alts(cur*semi) - 1);
		int heard_cents = freq_to_cents(hz);
		int k = (int)Math.round((heard_cents - off) /
							(double)semi);
		int alt = 0;
		if (!parms.mixture_mode && parms.temper_has_alts()) {
			/* more than 12 notes: the nearest of all, Dis or Es */
			int[] nn = parms.nearest_note(heard_cents - off);
			k = nn[0];
			alt = nn[1];
		}
		if (parms.mixture_mode) {
			/* Mixture ranks on the same key should stay within 20 cents
			 * of its tempered note.  Choose the nearest temperament step,
			 * and reject outliers before they can move the target.
			 * Both notes where the pitch class has two: Es heard
			 * on a mixture at Dis was rejected (41 cents in 1/4
			 * comma meantone), and once measured there its 41
			 * cents went to every key after it as the organ's
			 * pitch, so that all of them were rejected */
			int best = k;
			int best_error = Integer.MAX_VALUE;
			for (int candidate = Math.max(0, k-1);
			     candidate <= Math.min(96, k+1); candidate++) {
				for (int a = 0;
				     a < parms.temper_alts(candidate*semi); a++) {
					int error = Math.abs(heard_cents -
						(step_cents(candidate, a) + off));
					if (error < best_error) {
						best = candidate;
						alt = a;
						best_error = error;
					}
				}
			}
			if (best_error > 20*U) {
				det_count = 0;
				return;
			}
			k = best;
		}
		if ((k == det_semi) && (alt == det_alt)) {
			det_count++;
		} else {
			det_semi = k;
			det_alt = alt;
			det_count = 1;
		}
		if (det_count < DET_AGREE) {
			return;
		}
		/* remembered even out of range (a 32' pipe heard below A0),
		 * so that it can be corrected */
		det_stable = k;
		det_stable_alt = alt;
		det_stable_hz = hz;
		det_stable_time = now;
		if ((k == overruled) && (alt == overruled_alt)) {
			return;		/* still the note corrected by hand */
		}
		overruled = Integer.MIN_VALUE;
		int t = k;
		boolean keep_key = parms.mixture_mode &&
				   parms.mixture_focus_follow;
		if (near || keep_key) {
			/* the octave nearest the current note */
			t = k + 12*(int)Math.floor((cur - k)/12.0d + 0.5d);
			if (Math.abs(t - cur) >
			    (keep_key ? MIX_SEMIS : NEAR_SEMIS)) {
				return;
			}
		}
		boolean other_alt = (alt != cur_alt) &&
				    (parms.temper_alts(t*semi) > 1);
		if (((t != cur) || other_alt) &&
		    (t >= 0) && (t*semi + off <= 9600*U)) {
			if (parms.mixture_mode) {
				/* Keep the mixture's measured offset from its tempered
				 * note while moving to the newly heard key. */
				cause = String.format(Locale.US,
					"follow (heard %.2f Hz%s)", hz,
					near ? ", near" : "");
				mixture_goto(t, alt);
				mixture_measure(mixture.ONSET_SKIP);
			} else {
				parms.note_cents = t*semi + off;
				parms.note_alt = alt;
			}
			if (EventLog.on) {
				cause = String.format(Locale.US,
					"follow (heard %.2f Hz%s)", hz,
					near ? ", near" : "");
			}
			update_status();
		}
	}

	/* the user moved the note from semitone 'from' to 'to' by hand */
	private void user_moved_note(int from, int to) {
		if (!parms.auto_note || !auto_applies()) {
			return;
		}
		long now = SystemClock.uptimeMillis();
		boolean recent = (det_stable != Integer.MIN_VALUE) &&
				 (now - det_stable_time < CORR_RECENT);
		near = true;
		hold_correction();
		/* the silence that ends continuity counts from here */
		last_heard = now;
		DebugRecorder.intervention(this,
			recent ? det_stable*100*U : DebugRecorder.NONE,
			recent ? det_stable_hz : 0.0,
			from*100*U, to*100*U, near);
	}

	/* a correction by hand: the note the detector agreed on last stays
	 * overruled (on_pitch) */
	private void hold_correction() {
		boolean recent = (det_stable != Integer.MIN_VALUE) &&
			(SystemClock.uptimeMillis() - det_stable_time <
			 CORR_RECENT);
		overruled = recent ? det_stable : Integer.MIN_VALUE;
		overruled_alt = det_stable_alt;
		/* corrected while the key sounds: the release counts from
		 * here */
		last_loud = SystemClock.uptimeMillis();
	}

	/* inverse of cents_to_freq() for the fundamental */
	private int freq_to_cents(double hz) {
		double frac = ((double)parms.stretch())/12000.0d;
		double c = 1200.0d * Math.log(hz*10.0d/parms.A_freq) /
				Math.log(2.0d) / (1.0d+frac);
		return (int)Math.round(c*U) + 4800*U;
	}

	public void do_tone(View v) {
		cause = "tone button";
		if (ToneGenerator.playing) {
			ToneGenerator.stop_tone();
		} else {
			ToneGenerator.start_tone(
				cents_to_freq(parms.strobe_cents, 1));
		}
		update_status();
	}

	private void incr_cents(int delta, String what) {
		cause = what;
		int semi = 100*U;
		int before = (parms.note_cents + semi/2) / semi;
		parms.note_cents += delta;
		parms.note_cents = range_limit(parms.note_cents, 0, 9600*U);
		int after = (parms.note_cents + semi/2) / semi;
		if (after != before) {
			if ((after - before) % 12 != 0) {
				/* a pitch class with two notes (Dis, Es):
				 * from below the lower, from above the
				 * higher */
				parms.note_alt = (after > before) ? 0 :
					(parms.temper_alts(parms.note_cents) - 1);
			}
			user_moved_note(before, after);
		}
		update_status();
	}

	public void semi_incr(View v) { semi_step( 1, "semi+"); }
	public void semi_decr(View v) { semi_step(-1, "semi-"); }
	/* where the temperament has two notes on the pitch class (notes
	 * picked in the workbench), semi+ goes to the higher one before the
	 * next semitone: D Dis Es E in meantone */
	private void semi_step(int d, String what) {
		int n = parms.temper_alts(parms.note_cents);
		int a = Math.min(parms.note_alt, n-1) + d;
		if ((a >= 0) && (a < n)) {
			cause = what;
			parms.note_alt = a;
			int k = (parms.note_cents + 50*U) / (100*U);
			user_moved_note(k, k);
			update_status();
		} else {
			incr_cents(d*100*U, what);
		}
	}
	public void oct_incr(View v)  { incr_cents( 1200*U, "oct+"); }
	public void oct_decr(View v)  { incr_cents(-1200*U, "oct-"); }
	public void cent_incr(View v) { incr_cents(    1*U, "cent+"); }
	public void cent_decr(View v) { incr_cents(   -1*U, "cent-"); }

	public void A_incrp1(View v) {
		cause = "A +0.1";
		parms.A_freq++;
		update_status();
	}
	public void A_incr1(View v) {
		cause = "A +1";
		parms.A_freq+=10;
		update_status();
	}
	public void A_decrp1(View v) {
		cause = "A -0.1";
		parms.A_freq--;
		update_status();
	}
	public void A_decr1(View v) {
		cause = "A -1";
		parms.A_freq-=10;
		update_status();
	}
	private void afreq_dialog() {
		final EditText e = new EditText(this);
		e.setInputType(InputType.TYPE_CLASS_NUMBER |
				InputType.TYPE_NUMBER_FLAG_DECIMAL);
		e.setText(string_int10(parms.A_freq));
		e.setSelectAllOnFocus(true);
		int pad = (int)(16*getResources().getDisplayMetrics().density);
		FrameLayout f = new FrameLayout(this);
		f.setPadding(pad, pad/2, pad, 0);
		f.addView(e);
		final AlertDialog d = new AlertDialog.Builder(this)
			.setTitle(R.string.afreq_enter)
			.setView(f)
			.setPositiveButton(R.string.run_ok,
				new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface di, int w) {
					double hz;
					try {
						hz = Double.parseDouble(e.getText()
							.toString().replace(',', '.'));
					} catch (NumberFormatException x) {
						return;
					}
					if ((hz < 220.0) || (hz > 880.0)) {
						Toast.makeText(TunerTime.this,
							R.string.afreq_range,
							Toast.LENGTH_SHORT).show();
						return;
					}
					cause = "A keypad";
					parms.A_freq = (int)Math.round(hz*10.0);
					update_status();
				} })
			.setNegativeButton(R.string.cancel, null)
			.create();
		d.getWindow().setSoftInputMode(android.view.WindowManager
			.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		d.show();
		e.requestFocus();
	}

	public void reset_afreq(View v) {
		cause = "A reset";
		parms.A_freq = 4400;
		update_status();
	}

	/* A tab, "auto" (Onno, 22 Oct): the A that puts the note shown on
	 * what is heard.  The median of the detector's estimates of the
	 * last AUTO_A_MS that agree on the note (within half a semitone of
	 * its pitch in the temperament); the compromise of many pipes is
	 * made by hand after it, so 2 s are enough.  With fewer than
	 * AUTO_A_MIN of them, about 1 s (an estimate every 4096 samples,
	 * 85-93 ms), it does nothing.  The note's own cent offset set by
	 * hand is left out: the A is fitted to the note of the temperament
	 * (with stretch, if any), as Reset and the A buttons are */
	private static final long AUTO_A_MS = 2000;
	private static final int AUTO_A_MIN = 11;
	private static final int AUTO_A_RING = 64;
	private final long[] a_time = new long[AUTO_A_RING];
	private final double[] a_hz = new double[AUTO_A_RING];
	private int a_next = 0;

	/* every estimate (on_pitch), but not the tone generator's own */
	private void a_heard(double hz) {
		if ((hz <= 0.0d) || ToneGenerator.playing) {
			return;
		}
		a_time[a_next] = SystemClock.uptimeMillis();
		a_hz[a_next] = hz;
		a_next = (a_next + 1) % AUTO_A_RING;
	}

	public void auto_afreq(View v) {
		int k = (parms.note_cents + 50*U) / (100*U);
		int step = step_cents(k);
		long now = SystemClock.uptimeMillis();
		double[] h = new double[AUTO_A_RING];
		int n = 0;
		for (int i = 0; i < AUTO_A_RING; i++) {
			if ((a_hz[i] > 0.0d) && (now - a_time[i] <= AUTO_A_MS) &&
			    (Math.abs(freq_to_cents(a_hz[i]) - step) <= 50*U)) {
				h[n++] = a_hz[i];
			}
		}
		if (n < AUTO_A_MIN) {
			/* nothing heard long enough: nothing done, no popup */
			cause = "A auto: " + n + " estimates on " +
				new note(step).full_str + ", too few";
			log_changes();
			return;
		}
		Arrays.sort(h, 0, n);
		double med = ((n % 2) == 1) ? h[n/2] :
						(h[n/2 - 1] + h[n/2]) / 2.0d;
		/* the target is A times a factor of the note, temperament
		 * and stretch (cents_to_freq): scaled by heard / target */
		int a = (int)Math.round(parms.A_freq * med /
					cents_to_freq(step, 1));
		cause = String.format(Locale.US,
			"A auto (median %.2f Hz of %d estimates on %s)", med, n,
			new note(step).full_str);
		if ((a < 2200) || (a > 8800)) {
			cause += ", out of range";
			log_changes();
			return;
		}
		parms.A_freq = a;
		update_status();
	}

	public void stretch_incr(View v) {
		cause = "stretch+";
		parms.cents_per_octave++;
		update_status();
	}
	public void stretch_decr(View v) {
		cause = "stretch-";
		parms.cents_per_octave--;
		update_status();
	}
	public void reset_stretch(View v) {
		cause = "stretch reset";
		parms.cents_per_octave = 0;
		update_status();
	}

	public void do_preset_select(View v) {
		startActivity(new Intent(this, PresetSelectActivity.class));
	}
	public void do_choose_wolf(View v) {
		final preset p = preset.find(parms.preset_id);
		if ((p == null) || (p.wolf_template() < 1)) return;
		final int[] roots = temperaments.fifth_roots;
		/* one menu (Onno): the 12 places of the wolf, then the notes */
		final String[] items = new String[roots.length + 1];
		for (int i = 0; i < roots.length; i++) {
			items[i] = wolf_interval(roots[i]);
		}
		items[roots.length] = getString(R.string.pick_notes_item);
		new AlertDialog.Builder(this)
			.setTitle(R.string.wolf_select_title)
			.setItems(items,
				new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface d, int which) {
					d.dismiss();
					if (which >= roots.length) {
						pick_notes(p);
						return;
					}
					preset.set_temporary_wolf(p.id, roots[which]);
					cause = "temporary wolf selection";
					update_status();
				} })
			.setNegativeButton(R.string.cancel, null)
			.show();
	}

	/* the workbench: any notes of the chain of fifths Fes..His, each
	 * natural note with its flat and sharp, e.g. both Dis and Es for
	 * meantone with more than 12 notes.  until another temperament is
	 * selected, like the wolf.  each pitch class needs a note */
	private void pick_notes(final preset p) {
		final int template = p.wolf_template();
		/* C D E F G A H, each with its flat and sharp */
		final int[] naturals = { -3, -1, 1, -4, -2, 0, 2 };
		final int[] pos = new int[21];
		final String[] items = new String[21];
		final boolean[] checked = new boolean[21];
		int mask = preset.temporary_chain(p.id);
		if (mask == 0) {
			int root = preset.temporary_wolf_root(p.id);
			mask = temperaments.wolf_chain((root < 0) ? 8 : root);
		}
		for (int i = 0; i < 21; i++) {
			int n = naturals[i/3] + 7*((i%3) - 1);
			pos[i] = n;
			items[i] = note.chain_name(n) + "   " + note.cents_str(
					temperaments.chain_offset(template, n));
			checked[i] = (mask & (1 << (n - temperaments.CHAIN_LOW)))
					!= 0;
		}
		final AlertDialog dlg = new AlertDialog.Builder(this)
			.setTitle(R.string.pick_notes_title)
			.setMultiChoiceItems(items, checked,
				new DialogInterface.OnMultiChoiceClickListener() {
				public void onClick(DialogInterface d, int which,
						    boolean on) {
					checked[which] = on;
				} })
			.setPositiveButton(R.string.ok, null)
			.setNegativeButton(R.string.cancel, null)
			.create();
		dlg.setOnShowListener(new DialogInterface.OnShowListener() {
			public void onShow(DialogInterface d) {
				dlg.getButton(AlertDialog.BUTTON_POSITIVE)
				   .setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						int m = 0;
						boolean[] pc = new boolean[12];
						for (int i = 0; i < 21; i++) {
							if (!checked[i]) continue;
							m |= 1 << (pos[i] -
								temperaments.CHAIN_LOW);
							pc[temperaments.chain_class(
								pos[i])] = true;
						}
						String missing = "";
						for (int c = 0; c < 12; c++) {
							if (!pc[c]) {
								missing += (missing.isEmpty() ?
									"" : " ") +
									note.class_name(c);
							}
						}
						if (!missing.isEmpty()) {
							/* said in the dialog itself:
							 * no popups (Onno) */
							dlg.setTitle(getString(
								R.string.pick_notes_missing,
								missing));
							return;
						}
						preset.set_temporary_chain(p.id, m);
						cause = "temporary note selection";
						update_status();
						dlg.dismiss();
					} });
			} });
		dlg.show();
	}

	/* the workbench button with picked notes: how many, and those with
	 * an accidental, in pitch order: "Notes (14): Cis Dis Es Fis Gis
	 * As B" in meantone */
	private String notes_label(preset p) {
		java.util.List<Integer> chain = p.tuning_chain();
		String s = "";
		for (int n : chain) {
			if (note.chain_accidental(n) != 0) {
				s += (s.isEmpty() ? "" : " ") + note.chain_name(n);
			}
		}
		return getString(R.string.pick_notes_label, chain.size(), s);
	}
	private String wolf_interval(int root) {
		String[] english = { "C", "C#", "D", "D#", "E", "F",
			"F#", "G", "G#", "A", "A#", "B" };
		String[] german = { "C", "Cs", "D", "Ds", "E", "F",
			"Fs", "G", "Gs", "A", "B", "H" };
		String[] names = parms.german_names ? german : english;
		return getString(R.string.wolf_interval, names[root],
			names[(root + 7) % 12]);
	}
	public void do_preset_edit(View v) {
		startActivity(new Intent(this, PresetEditActivity.class));
	}

	public void N_incr(View v) {
		parms.fft_size *= 2;
		update_status();
	}
	public void N_decr(View v) {
		parms.fft_size /= 2;
		update_status();
	}
	public void grid_toggle(View v) {
		parms.fft_grid_freq = !parms.fft_grid_freq;
		update_status();
	}

	public void note_clicked(View v) {
		cause = "tab note";
		set_mode(note_buttons, false);
	}
	public void afreq_clicked(View v) {
		cause = "tab A";
		set_mode(afreq_buttons, false);
	}
	public void stretch_clicked(View v) {
		cause = !parms.organ_mode ? "tab stretch" : parms.mixture_mode ?
			"tab mixture again (measure)" : "tab mixture";
		if (!parms.organ_mode) {
			set_mode(stretch_buttons, false);
		} else if (parms.mixture_mode) {
			mixture_measure(v);	/* tapped again: measure again */
		} else {
			set_mode(mixture_buttons, false);
		}
	}

	/* organ mode: no stretch; the stretch tab becomes mixture mode, and
	 * a tap on a strobe row focuses its partial instead of pausing */
	private void organ_resume() {
		stretch_button.setText(parms.organ_mode ? R.string.tab_mixture
							: R.string.tab_stretch);
		if (!parms.organ_mode || (parms.focus_row() < 0)) {
			/* the focused footage is no longer shown */
			parms.focus_ratio = 0.0d;
		}
		if (!parms.organ_mode || (parms.row_of(parms.note_focus) < 0)) {
			parms.note_focus = 0.0d;
		}
		if (!parms.organ_mode ||
		    (parms.row_of(parms.mixture_focus) < 0)) {
			parms.mixture_focus = 0.0d;
		}
		if (!parms.organ_mode) {
			parms.mixture_mode = false;
		}
		mix.stop();
	}

	/* entering mixture mode starts from the current target and measures
	 * right away */
	private void mixture_set(boolean on) {
		if (on && !parms.mixture_mode) {
			parms.mixture_cents = parms.strobe_cents;
			parms.mixture_ref = 0.0d;
			parms.mixture_mode = true;
			parms.use_focus();	/* its own row (set_mode) */
			mixture_measure(null);
		} else if (!on) {
			parms.mixture_mode = false;
			mix.stop();
		}
	}

	public void mixture_measure(View v) {
		mixture_measure(0.0d);
	}

	private void mixture_measure(double settle) {
		if (cause == null) {
			cause = "measure";
		}
		int fr = parms.focus_row();
		if ((fr >= 0) && Strobe.row_too_high(this, fr)) {
			/* the target moved up since it was focused: not
			 * measured, but the row stays focused until the
			 * user taps another (Onno, 21 Oct) */
			if (settle == 0.0d) {
				too_high_toast();
			}
			mix.stop();
			update_status();
			return;
		}
		if (fr < 0) {
			/* nothing focused: the 8' row, else the lowest */
			int i;
			for (i = 0; i < parms.num_strobes; i++) {
				if (parms.row_ratio(i) == 1.0d) {
					break;
				}
			}
			parms.focus_ratio = parms.row_ratio(
					(i < parms.num_strobes) ? i : 0);
		}
		mix.start(settle);
		update_status();
	}

	/* the note-mode step nearest to c (tenths of a cent): the semitone
	 * k (k*100*U is its untempered pitch) whose pitch in note mode,
	 * k*100*U plus the temperament's offset if one is in effect, is
	 * closest to c.  the offsets are within half a semitone, so the
	 * nearest equal-tempered semitone or a neighbour */
	private static int nearest_step(int c) {
		int semi = 100*U;
		int k0 = (c + semi/2) / semi;
		int best = k0;
		for (int k = k0-1; k <= k0+1; k++) {
			if ((k >= 0) && (Math.abs(c - step_cents(k)) <
					 Math.abs(c - step_cents(best)))) {
				best = k;
			}
		}
		return best;
	}
	/* pitch of note-mode step k, with the temperament: of note alt
	 * where its pitch class has two (Dis, Es; note mode moves along
	 * with mixture mode, mixture_goto()) */
	private static int step_cents(int k) {
		return step_cents(k, parms.note_alt);
	}
	private static int step_cents(int k, int alt) {
		int c = k*100*U;
		return c + parms.temper_offset(c, alt);
	}

	/* the next key, without leaving mixture mode: the target moves by
	 * a step of note mode and keeps its offset from the step (the
	 * organ's pitch is much the same from key to key), until measure
	 * is tapped with the reference rank sounding.  note mode moves
	 * along, so that it is at the same key when going back to it.
	 * The focused row stays as it is until the user taps another (Onno,
	 * 21 Oct; v18.4 focused the row last measured on again) */
	public void mixture_semi_incr(View v) {
		cause = "mixture key+";
		int from = nearest_step(parms.mixture_cents);
		mixture_move(1);
		user_moved_note(from, nearest_step(parms.mixture_cents));
	}
	public void mixture_semi_decr(View v) {
		cause = "mixture key-";
		int from = nearest_step(parms.mixture_cents);
		mixture_move(-1);
		user_moved_note(from, nearest_step(parms.mixture_cents));
	}
	public void mixture_oct_incr(View v) { mixture_octave(1); }
	public void mixture_oct_decr(View v) { mixture_octave(-1); }
	private void mixture_octave(int d) {
		cause = (d > 0) ? "mixture octave+" : "mixture octave-";
		int from = nearest_step(parms.mixture_cents);
		parms.mixture_cents = range_limit(parms.mixture_cents + d*1200*U,
				0, 9600*U);
		parms.mixture_ref = 0.0d;
		parms.note_cents = range_limit(parms.note_cents + d*1200*U,
				0, 9600*U);
		update_status();
		user_moved_note(from, nearest_step(parms.mixture_cents));
	}
	/* by d steps: where the temperament has two notes on the pitch
	 * class, both, as semi_step() in note mode: D Dis Es E */
	private void mixture_move(int d) {
		int semi = 100*U;
		int k = nearest_step(parms.mixture_cents);
		int n = parms.temper_alts(k*semi);
		int a = Math.min(parms.note_alt, n-1) + d;
		if ((a >= 0) && (a < n)) {
			mixture_goto(k, a);
		} else {
			mixture_goto(k+d, (d > 0) ? 0 :
				     parms.temper_alts((k+d)*semi) - 1);
		}
	}
	/* to step k, its note alt */
	private void mixture_goto(int k, int alt) {
		int semi = 100*U;
		int cur = nearest_step(parms.mixture_cents);
		if ((k < 0) || (k*semi > 9600*U)) {
			return;
		}
		mix.stop();
		parms.mixture_cents += step_cents(k, alt) - step_cents(cur);
		parms.mixture_ref = 0.0d;
		int nk = (parms.note_cents + semi/2) / semi;
		parms.note_cents += (k-nk)*semi;
		parms.note_alt = alt;
		update_status();
	}

	/* back to the nearest note (of the temperament, if one is in
	 * effect), with no offset */
	public void mixture_reset(View v) {
		cause = "mixture reset";
		mix.stop();
		parms.mixture_cents = step_cents(nearest_step(
						parms.mixture_cents));
		parms.mixture_ref = 0.0d;
		update_status();
	}

	/* from the strobe thread, after each buffer while a row is focused */
	public void focus_drift(double freq, double cycles, double secs,
				double buf_secs, double amp) {
		if (parms.length_mode) {
			/* shown with the next pitch estimate (on_pitch) */
			length.add_drift(freq, cycles, secs);
		}
		if (!mix.measuring()) {
			return;
		}
		final double cents = mix.feed(freq, cycles, secs, buf_secs,
						amp);
		runOnUiThread(new Runnable() {
			public void run() {
				if (!parms.mixture_mode) {
					return;
				}
				if (Double.isNaN(cents) &&
				    mix.take_released()) {
					/* the key released too soon: no
					 * result, the status says so */
					cause = "mixture: key released, " +
						"measurement discarded";
					log_changes();
					update_mixture_status();
					return;
				}
				if (Double.isNaN(cents)) {
					/* just the progress: update_status()
					 * would set parms.changed, and the
					 * strobe recalculation that follows
					 * resets the drift being measured */
					update_mixture_status();
					return;
				}
				/* in tune where it was measured */
				cause = mix.cut() ? "mixture measured " +
					"(cut short by the key's release)" :
					"mixture measured";
				parms.mixture_cents += (int)Math.round(cents*U);
				parms.mixture_ref = parms.focus_ratio;
				update_status();
			} });
	}

	/* organ mode: the tapped strobe row's partial becomes the focused
	 * one, tapped again it is released */
	private void tap_row(int y, int h) {
		int n = parms.num_strobes;
		if ((n < 1) || (h <= 0)) {
			return;
		}
		int row = range_limit((y*n)/h, 0, n-1);
		double r = parms.row_ratio(row);
		if ((parms.focus_ratio != r) && Strobe.row_too_high(this, row)) {
			/* a blank row: no drift to show or measure */
			too_high_toast();
			return;
		}
		cause = "tap row " + parms.footage_str(r);
		parms.focus_ratio = (parms.focus_ratio == r) ? 0.0d : r;
		if (parms.focus_ratio == 0.0d) {
			mix.stop();
		} else if (mix.measuring()) {
			mix.start();		/* measure the new row */
		}
		update_status();
	}

	private void too_high_toast() {
		Toast.makeText(this, R.string.toast_row_too_high,
				Toast.LENGTH_SHORT).show();
	}

	/* the measured pitch, rounded to the nearest step of note mode
	 * (with its temperament), and the offset from that step */
	private String mixture_str() {
		int k = nearest_step(parms.mixture_cents);
		note n = new note(k*100*U);
		int chain = parms.chain_at(k*100*U, parms.note_alt);
		return ((chain != parms.NO_CHAIN) ? n.spelled(chain) :
							n.name_oct) + " " +
			note.cents_str(parms.mixture_cents - step_cents(k));
	}
	private String mixture_offset_str() {
		return note.cents_str(parms.mixture_cents -
				step_cents(nearest_step(parms.mixture_cents)));
	}

	private void update_mixture_status() {
		mixture_display.setText(mixture_str());
		String f = (parms.focus_row() >= 0) ?
				parms.footage_str(parms.focus_ratio) : null;
		if (mix.measuring()) {
			mixture_status.setText(getString(
				R.string.mixture_measuring, f,
				(int)Math.round(mix.progress()*100.0d)));
		} else if (parms.mixture_ref > 0.0d) {
			/* a key released before the measurement was done
			 * says nothing: the organ builder knows (Onno) */
			/* the rank the target was taken from, which need not
			 * be the row focused now (the rank being tuned) */
			String s = getString(R.string.mixture_measured,
					parms.footage_str(parms.mixture_ref));
			if (mix.beating()) {
				/* the result stands, but may be the louder
				 * of two pipes or between them */
				s += "\n" + getString(R.string.mixture_beating);
			}
			mixture_status.setText(s);
		} else if (f != null) {
			mixture_status.setText(R.string.mixture_tap_measure);
		} else {
			mixture_status.setText(R.string.mixture_tap_row);
		}
		if (mix.far(parms.strobe_cents)) {
			/* the drift wraps (mixture.FAR_CENTS) */
			mixture_status.append("\n" +
				getString(R.string.mixture_far));
		}
	}
	public void preset_clicked(View v) {
		cause = "tab preset";
		set_mode(preset_buttons, false);
	}

	/* the manual in the app (assets/help), offline: a church may have
	 * no network.  It links to Greg's site and the GitHub issues */
	private void do_help() {
		startActivity(new Intent(this, HelpActivity.class));
	}

	private String my_version() {
		try {
			return getPackageManager().
				getPackageInfo(getPackageName(), 0).versionName;
		} catch (Exception e) {
			return "UNKNOWN";
		}
	}

	private void do_about() {
		AlertDialog.Builder b = new AlertDialog.Builder(this);
		b.setCancelable(true);
		b.setPositiveButton(R.string.ok,
			new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface di, int which) { }
			});
		b.setIcon(android.R.drawable.ic_dialog_info);
		b.setTitle(R.string.about_title);
		b.setMessage(getString(R.string.about_message, my_version()));
		b.show();
	}

	private int range_limit(int val, int lo, int hi) {
		if (val < lo) {
			return lo;
		} else if (val > hi) {
			return hi;
		}
		return val;
	}

	private static String string_int10(int n) {
		return Integer.toString(n/10) + "." + Integer.toString(n%10);
	}

		/* note that ranges are checked in update_status() as well,
		 * because status display is really the only part that will go
		 * wonkers if the values go out-of-range anyways.  */
	private void update_status() {
		String temper_str = null;
		if (parms.mixture_mode) {
			/* wherever the focused partial was measured */
			parms.mixture_cents = range_limit(parms.mixture_cents,
							0, 9600*U);
			parms.strobe_cents = parms.mixture_cents;
		} else {
			/* note mode: apply the selected preset if it is a
			 * temperament (12 notes, one per pitch class).  Also
			 * the temperament tab, which keeps the note (a view
			 * only, Onno 22 Oct) */
			parms.note_cents = range_limit(parms.note_cents,
							0, 9600*U);
			int off = 0;
			if (parms.temper_offsets() != null) {
				off = parms.note_offset(parms.note_cents);
				temper_str = preset.find(parms.preset_id).name +
					" " + note.cents_str(off);
			}
			parms.strobe_cents = parms.note_cents + off;
		}
		parms.strobe_cents = range_limit(parms.strobe_cents, 0, 9600*U);
		parms.A_freq = range_limit(parms.A_freq, 2200, 8800);

		parms.fft_size = range_limit(parms.fft_size, 512, 32768);
		parms.fft_width = range_limit(parms.fft_width, 128, Strobe.FFT_RANGE_MULT);
		parms.fft_height = range_limit(parms.fft_height, 128, Strobe.FFT_RANGE_MULT);
		if ((strobe != null) && !strobe.paused) {
			parms.fft_xoffset = Strobe.FFT_RANGE_MULT;
		}
		parms.fft_xoffset = range_limit(parms.fft_xoffset, 0,
				Strobe.FFT_RANGE_MULT-parms.fft_width);
		parms.fft_yoffset = range_limit(parms.fft_yoffset, 0,
				Strobe.FFT_RANGE_MULT-parms.fft_height);

		parms.changed = true;
		ToneGenerator.set_freq(cents_to_freq(parms.strobe_cents, 1));

		/* note mode shows the position before the
		 * temperament, which is shown in the title */
		note nn = new note(parms.note_cents);
		int chain = parms.note_chain();
		note_semi_display.setText((chain != parms.NO_CHAIN) ?
			nn.spelled(chain) :
			parms.organ_mode ? nn.organ_name() : nn.name_oct);
		note_cent_display.setText(nn.cents_str);
		if (!parms.fft_mode && !parms.preset_mode &&
		    !parms.mixture_mode) {
			set_title(getString(R.string.title_strobe), temper_str);
		}

		update_preset_workbench();

		String center_str = string_int10(parms.A_freq);
		center_display.setText(getString(R.string.status_a, center_str));
		/* with the note set, as the A tab hides the note's buttons
		 * (Onno) */
		afreq_center_display.setText(getString(R.string.afreq_note,
			center_str, parms.mixture_mode ? mixture_str() :
			note_semi_display.getText() + " " + nn.cents_str));

		String stretch_str = ((parms.cents_per_octave >= 0) ? "+" : "-") +
				string_int10(Math.abs(parms.cents_per_octave));
		stretch_display.setText(stretch_str);
		stretch_stretch_display.setText(stretch_str);
		if (parms.organ_mode) {
			/* no stretch: the mixture offset, if any */
			stretch_display.setText(parms.mixture_mode ?
				getString(R.string.status_mix,
					  mixture_offset_str()) :
				getString(R.string.status_organ));
			update_mixture_status();
		}

		N_display.setText(Integer.toString(parms.fft_size));
		grid_button.setText(parms.fft_grid_freq ? R.string.fft_hz
							: R.string.fft_scale);

		/* highlighted while the tone plays */
		tone_button.setSelected(ToneGenerator.playing);
		tone_button.setTextColor(getResources().getColor(
			ToneGenerator.playing ? R.color.accent : R.color.text));

		auto_button.setEnabled(auto_applies());
		auto_button.setText(parms.auto_note ? R.string.auto_follow
						    : R.string.auto_locked);
		auto_button.setTextColor(getResources().getColor(
			!auto_applies() ? R.color.outline :
			parms.auto_note ? R.color.accent : R.color.warn));
		if (parms.length_mode) {
			length.update();
		}
		run_key();
		log_changes();
	}

	/* the name of n, preset p's i-th note (tuning_notes()): spelled
	 * from the chain of fifths where more than 12 notes were picked */
	private static String tuning_name(preset p, int i, note n) {
		java.util.List<Integer> chain = (p == null) ? null :
							p.tuning_chain();
		if ((chain != null) && p.spelled() && (i >= 0) &&
		    (i < chain.size())) {
			return n.spelled(chain.get(i));
		}
		return parms.organ_mode ? n.organ_name() : n.name_oct;
	}

	private void update_preset_workbench() {
		if (preset_workbench == null) return;
		preset p = preset.find(parms.preset_id);
		preset_workbench_notes.removeAllViews();
		if (p == null) {
			preset_workbench_static_name.setText(getString(R.string.no_presets));
			preset_workbench_static_name.setVisibility(View.VISIBLE);
			preset_workbench_wolf.setVisibility(View.GONE);
			preset_workbench_summary.setText("");
			return;
		}
		preset_workbench_static_name.setText(p.name);
		int template = p.wolf_template();
		preset_workbench_wolf.setVisibility((template >= 1) ?
			View.VISIBLE : View.GONE);
		preset_workbench_static_name.setVisibility((template >= 1) ?
			View.GONE : View.VISIBLE);
		java.util.List<Integer> tuning = p.tuning_notes();
		if (template >= 1) {
			int root = preset.temporary_wolf_root(p.id);
			if (root < 0) root = 8;
			preset_workbench_wolf.setText(p.name + "\n" +
				((p.tuning_chain() != null) ? notes_label(p) :
				getString(R.string.wolf_label, wolf_interval(root))));
		}
		int count = tuning.size();
		preset_workbench_summary.setText((count == 1 ?
			getString(R.string.preset_workbench_count_one, count) :
			getString(R.string.preset_workbench_count_many, count)));
		for (int i = 0; i < count; i++) {
			/* a display only, as in Onno's fork: no note is
			 * tapped there (Onno, 22 Oct) */
			final note n = new note(tuning.get(i));
			LinearLayout row = new LinearLayout(this);
			row.setOrientation(LinearLayout.HORIZONTAL);
			row.setGravity(android.view.Gravity.CENTER_VERTICAL);
			row.setMinimumHeight((int)(48 * getResources()
				.getDisplayMetrics().density));
			row.setPadding((int)(8 * getResources()
				.getDisplayMetrics().density), 0,
				(int)(8 * getResources().getDisplayMetrics().density), 0);
			TextView name = new TextView(this);
			name.setText(tuning_name(p, i, n));
			name.setTextSize(18);
			/* no note marked: the tab focuses nothing (Onno,
			 * 22 Oct; C was marked, as the note it played) */
			name.setTextColor(getResources().getColor(R.color.text));
			row.addView(name, new LinearLayout.LayoutParams(0,
				ViewGroup.LayoutParams.WRAP_CONTENT, 1));
			TextView deviation = new TextView(this);
			deviation.setText(n.cents_str);
			deviation.setTextSize(17);
			deviation.setGravity(android.view.Gravity.RIGHT);
			deviation.setTextColor(getResources().getColor(R.color.text));
			row.addView(deviation, new LinearLayout.LayoutParams(
				(int)(96 * getResources().getDisplayMetrics().density),
				ViewGroup.LayoutParams.WRAP_CONTENT));
			preset_workbench_notes.addView(row);
		}
	}

	/* Debug recordings (EventLog): what changed since the last call and
	 * why.  The handlers set cause before they change anything; called
	 * at the end of update_status(), and where continuity ends.  Only a
	 * few values are compared each time, strings are made only when
	 * something changed or a cause was given */
	private String cause = null;
	private boolean lg_first = true;
	private int lg_note, lg_target, lg_A, lg_stretch, lg_preset, lg_auto;
	private int lg_mix;
	private boolean lg_near, lg_length, lg_organ, lg_tone, lg_paused;
	private boolean lg_pmode, lg_mmode;
	private double lg_focus, lg_mixref;
	private View lg_tab;
	private String lg_src, lg_mic;

	private void log_changes() {
		String why = cause;
		cause = null;
		if (!EventLog.on) {
			lg_first = true;
			return;
		}
		int auto = parms.auto_note ? 1 : 2;
		View tab = (buttons_holder.getChildCount() > 0) ?
				buttons_holder.getChildAt(0) : null;
		boolean paused = (strobe != null) && strobe.paused;
		boolean ch = lg_first || (parms.note_cents != lg_note) ||
			(parms.strobe_cents != lg_target) ||
			(parms.A_freq != lg_A) ||
			(parms.cents_per_octave != lg_stretch) ||
			(parms.preset_id != lg_preset) || (auto != lg_auto) ||
			(near != lg_near) || (parms.length_mode != lg_length) ||
			(parms.organ_mode != lg_organ) ||
			(ToneGenerator.playing != lg_tone) ||
			(paused != lg_paused) || (tab != lg_tab) ||
			(parms.preset_mode != lg_pmode) ||
			(parms.mixture_mode != lg_mmode) ||
			(parms.focus_ratio != lg_focus) ||
			(parms.mixture_cents != lg_mix) ||
			(parms.mixture_ref != lg_mixref) ||
			!parms.audio_source.equals(lg_src) ||
			!parms.microphone.equals(lg_mic);
		if (!ch && (why == null)) {
			return;
		}
		StringBuilder sb = new StringBuilder((why != null) ? why : "?");
		if (ch) {
			String sep = ": ";
			if (lg_first) {
				sb.append(sep).append("state ").append(state_str());
			} else {
				if (parms.note_cents != lg_note) {
					sb.append(sep).append("note ").append(
						new note(lg_note).full_str).append(
						" -> ").append(new note(
						parms.note_cents).full_str);
					sep = ", ";
				}
				if (parms.strobe_cents != lg_target) {
					sb.append(sep).append("target ").append(
						new note(lg_target).full_str).append(
						" -> ").append(new note(
						parms.strobe_cents).full_str);
					sep = ", ";
				}
				if ((auto != lg_auto) || (near != lg_near)) {
					sb.append(sep).append("auto ").append(
						auto_str(auto, near));
					sep = ", ";
				}
				if (parms.focus_ratio != lg_focus) {
					sb.append(sep).append("focus ").append(
						focus_str());
					sep = ", ";
				}
				if ((tab != lg_tab) ||
				    (parms.preset_mode != lg_pmode) ||
				    (parms.mixture_mode != lg_mmode) ||
				    (parms.length_mode != lg_length) ||
				    (parms.organ_mode != lg_organ)) {
					sb.append(sep).append(mode_str());
					sep = ", ";
				}
				if ((parms.mixture_cents != lg_mix) ||
				    (parms.mixture_ref != lg_mixref)) {
					sb.append(sep).append(mixture_log_str());
					sep = ", ";
				}
				if ((parms.A_freq != lg_A) ||
				    (parms.cents_per_octave != lg_stretch) ||
				    (parms.preset_id != lg_preset)) {
					sb.append(sep).append(tuning_str());
					sep = ", ";
				}
				if ((ToneGenerator.playing != lg_tone) ||
				    (paused != lg_paused)) {
					sb.append(sep).append("tone ").append(
						ToneGenerator.playing ? "on" : "off")
						.append(paused ? ", paused" : "");
					sep = ", ";
				}
				if (!parms.audio_source.equals(lg_src) ||
				    !parms.microphone.equals(lg_mic)) {
					sb.append(sep).append(mic_str());
				}
			}
			EventLog.state = state_str();
		}
		EventLog.ui(sb.toString());
		lg_first = false;
		lg_note = parms.note_cents;
		lg_target = parms.strobe_cents;
		lg_A = parms.A_freq;
		lg_stretch = parms.cents_per_octave;
		lg_preset = parms.preset_id;
		lg_auto = auto;
		lg_near = near;
		lg_length = parms.length_mode;
		lg_organ = parms.organ_mode;
		lg_tone = ToneGenerator.playing;
		lg_paused = paused;
		lg_tab = tab;
		lg_pmode = parms.preset_mode;
		lg_mmode = parms.mixture_mode;
		lg_focus = parms.focus_ratio;
		lg_mix = parms.mixture_cents;
		lg_mixref = parms.mixture_ref;
		lg_src = parms.audio_source;
		lg_mic = parms.microphone;
	}

	/* 1 follow, 2 locked ("off" before v18.9; the run headers'
	 * auto_before) */
	private static String auto_str(int auto, boolean near) {
		return ((auto == 1) ? "follow" : "lock") +
			(near ? " near" : "");
	}
	private static String focus_str() {
		return (parms.focus_ratio > 0.0d) ?
			parms.footage_str(parms.focus_ratio) : "none";
	}
	private String mode_str() {
		View tab = (buttons_holder.getChildCount() > 0) ?
				buttons_holder.getChildAt(0) : null;
		String t = (tab == note_buttons) ? "note" :
			(tab == afreq_buttons) ? "A" :
			(tab == stretch_buttons) ? "stretch" :
			(tab == mixture_buttons) ? "mixture" :
			(tab == preset_buttons) ? "preset" :
			(tab == fft_buttons) ? "fft" : "?";
		return "tab " + t + (parms.organ_mode ? ", organ" : "") +
			(parms.length_mode ? ", length" : "");
	}
	private static String mixture_log_str() {
		return "mixture " + new note(parms.mixture_cents).full_str +
			((parms.mixture_ref > 0.0d) ? (" measured on " +
				parms.footage_str(parms.mixture_ref)) : "");
	}
	private static String tuning_str() {
		preset p = preset.find(parms.preset_id);
		return "A " + string_int10(parms.A_freq) + ", stretch " +
			parms.cents_per_octave/10.0d + ", preset " +
			((p != null) ? p.name : "none");
	}
	private static String mic_str() {
		return "mic " + parms.audio_source + "/" + parms.microphone;
	}
	private String state_str() {
		return "note " + new note(parms.note_cents).full_str +
			", target " + new note(parms.strobe_cents).full_str +
			", auto " + auto_str(parms.auto_note ? 1 : 2, near) +
			", focus " + focus_str() + ", " + mode_str() + ", " +
			mixture_log_str() + ", " + tuning_str() + ", " +
			mic_str();
	}

		/* cents in tenths of a cent (preset.UNITS_PER_CENT), 48000=A4.
		 * also called from the native side */
	public double cents_to_freq(int cents, int harm_mult) {
		double c = ((double)(cents-4800*U)) / (double)U;
		c += 1200.0d * Math.log((double)harm_mult) / Math.log(2.0d);
		double frac = ((double)parms.stretch())/12000.0d;
		double octpow = c*(1.0d+frac) / 1200.0d;
		double mult = Math.pow(2.0d, octpow);
		return ((double)parms.A_freq)*mult/10.0d;
	}

	private void start_strobe() {
		stop_strobe();
		if (!check_permission()) {
			return;
		}
		strobe = new Strobe(this);
		strobe.setSurface(holder, width, height);
		parms.changed = true;
		strobe.start();
	}

	private void stop_strobe() {
		if (strobe == null) {
			return;
		}
		strobe.interrupt();
		strobe = null;
	}

	public void onRequestPermissionsResult(int code, String[] perms,
			int[] results) {
		if ((results.length < 1) ||
		    (results[0] != PackageManager.PERMISSION_GRANTED)) {
			new AlertDialog.Builder(this)
			.setCancelable(false)
			.setPositiveButton(R.string.ok,
				new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface di,
						int which) { finish(); } })
			.setIcon(android.R.drawable.ic_dialog_alert)
			.setTitle(R.string.perm_title)
			.setMessage(R.string.perm_message)
			.show();
		} else {
			if (holder != null) {
				start_strobe();
			}
		}
	}
	private boolean check_permission() {
		return (ContextCompat.checkSelfPermission(this,
					Manifest.permission.RECORD_AUDIO) ==
				PackageManager.PERMISSION_GRANTED);
	}
	private void request_permission() {
		if (!check_permission()) {
			ActivityCompat.requestPermissions(this, new String[] {
					Manifest.permission.RECORD_AUDIO }, 0);
		}
	}

		/* interface SurfaceHolder.Callback */
	public void surfaceCreated(SurfaceHolder h) {
		/* ignore - no point starting the thread until we have
		 * dimensions */
		if (holder != null) {
			/* we've received a surfaceChanged() already, assume it
			 * is still valid */
			start_strobe();
		}
	}
	public void surfaceChanged(SurfaceHolder h, int format, int width_, int height_) {
		holder = h;
		width = width_;
		height = height_;
		if (strobe != null) {
			/* update existing thread */
			strobe.setSurface(h, width, height);
		} else {
			/* start new thread */
			start_strobe();
		}
	}
	public void surfaceDestroyed(SurfaceHolder h) {
		holder = null;
		if (strobe != null) {
			strobe.setSurface(null, 0, 0);
			stop_strobe();
		}
	}

	private class strobe_listener implements View.OnTouchListener {
		private int start_x = -1, start_y = -1;
		private int start_fft_xoffset = 0, start_fft_yoffset = 0;
		private int start_x_width = 0, start_y_height = 0;
		private int start_fft_width = 0, start_fft_height = 0;
		private boolean doing_x = false, doing_y = false;
		private boolean could_be_click = false;
		private long down_time = 0;
		/* the long press opens the partial settings as soon as it is
		 * recognised, not on release (Onno) */
		private boolean long_fired = false;
		private final Runnable long_press = new Runnable() {
			public void run() {
				if (!could_be_click || parms.fft_mode) {
					return;
				}
				long_fired = true;
				Intent settings_intent = new Intent(
					TunerTime.this, Settings.class);
				settings_intent.putExtra(
					Settings.EXTRA_OPEN_STROBE_ROWS, true);
				cause = "strobe long press: partial settings";
				startActivity(settings_intent);
			} };
		public boolean onTouch(View v, MotionEvent event) {
			int i;
			int x1 = -1, x2 = -1;
			int y1 = -1, y2 = -1;
			int width = v.getWidth();
			int height = v.getHeight();

			if ((width == 0) || (height == 0)) {
				/* unlikely, but don't divide by zero */
				return false;
			}

			if ((i = event.findPointerIndex(0)) != -1) {
				x1 = (int)event.getX(i);
				y1 = (int)event.getY(i);
			} else {
				start_x_width = 0;
				start_y_height = 0;
				return false;
			}
			if ((i = event.findPointerIndex(1)) != -1) {
				x2 = (int)event.getX(i);
				y2 = (int)event.getY(i);
				could_be_click = false;
			} else {
				start_x_width = 0;
				start_y_height = 0;
			}
			if ((Math.abs(x1 - start_x) > (width/20)) && !doing_y) {
				doing_x = true;
				could_be_click = false;
			}
			if ((Math.abs(y1 - start_y) > (height/20)) && !doing_x) {
				doing_y = true;
				could_be_click = false;
			}
			switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
			down_time = event.getEventTime();
				long_fired = false;
				v.removeCallbacks(long_press);
				if (!parms.fft_mode) {
					v.postDelayed(long_press,
						ViewConfiguration.getLongPressTimeout());
				}
				start_x = x1; start_x_width = 0;
				start_y = y1; start_y_height = 0;
				start_fft_xoffset = parms.fft_xoffset +
					((x1*parms.fft_width) / width);
				start_fft_yoffset = parms.fft_yoffset +
					((y1*parms.fft_height) / height);
				could_be_click = true;
				doing_x = false; doing_y = false;
				return true;
			case MotionEvent.ACTION_POINTER_2_DOWN:
				start_x_width = Math.abs(x1-x2);
				if (start_x_width < 10) start_x_width = 10;
				start_fft_width = parms.fft_width;
				start_y_height = Math.abs(y1-y2);
				if (start_y_height < 10) start_y_height = 10;
				start_fft_height = parms.fft_height;
				return true;
			case MotionEvent.ACTION_MOVE:
				if (!parms.fft_mode) {
					return true;
				}
				if (doing_x) {
					if (start_x_width != 0) {
						int new_width = Math.abs(x1-x2);
						if (new_width == 0) {
							new_width = 1;
						}
						parms.fft_width = (start_fft_width *
							start_x_width) / new_width;
					}
					parms.fft_xoffset = start_fft_xoffset -
						((x1*parms.fft_width) / width);
				}
				if (doing_y) {
					if (start_y_height != 0) {
						int new_height = Math.abs(y1-y2);
						if (new_height == 0) {
							new_height = 1;
						}
						parms.fft_height = (start_fft_height *
							start_y_height) / new_height;
					}
					parms.fft_yoffset = start_fft_yoffset -
						((y1*parms.fft_height) / height);
				}
				update_status();
				return true;
			case MotionEvent.ACTION_CANCEL:
				v.removeCallbacks(long_press);
				return true;
			case MotionEvent.ACTION_UP:
				v.removeCallbacks(long_press);
				if (long_fired) {
					return true;
				}
				if (could_be_click && parms.organ_mode &&
				    !parms.fft_mode) {
					tap_row(y1, height);
				} else if (could_be_click && (strobe != null)) {
					strobe.toggle_pause();
					cause = "tap strobe";
					update_status();
				}
				return true;
			}
			return false;
		}
	}

	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.option, menu);
		return super.onCreateOptionsMenu(menu);
	}

	public boolean onPrepareOptionsMenu(Menu menu) {
		boolean on = RunRecorder.active();
		menu.findItem(R.id.action_run).setVisible(!on &&
			!parms.fft_mode && !parms.preset_mode);
		menu.findItem(R.id.action_share).setVisible(!on);
		/* each would leave the screen, which ends the run */
		menu.findItem(R.id.action_settings).setVisible(!on);
		menu.findItem(R.id.action_help).setVisible(!on);
		menu.findItem(R.id.action_about).setVisible(!on);
		menu.findItem(R.id.action_run_mark).setVisible(on);
		MenuItem stop = menu.findItem(R.id.action_run_stop);
		stop.setVisible(on);
		if (on) {
			int t = RunRecorder.secs();
			stop.setTitle(getString(R.string.run_stop_status,
				t/60, t%60, RunRecorder.num_keys()));
		}
		return super.onPrepareOptionsMenu(menu);
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_run) {
			run_dialog();
			return true;
		} else if (id == R.id.action_share) {
			ShareRecordings.start(this);
			return true;
		} else if (id == R.id.action_run_stop) {
			run_stop("stop button");
			return true;
		} else if (id == R.id.action_run_mark) {
			run_mark_dialog();
			return true;
		} else if (id == R.id.action_settings) {
			startActivity(new Intent(this, Settings.class));
			return true;
		} else if (id == R.id.action_help) {
			do_help();
			return true;
		} else if (id == R.id.action_about) {
			do_about();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/* Chromatic run recording (RunRecorder): the menu starts it with a
	 * dialog for what is recorded; while it runs, the action bar shows
	 * the time and keys with a stop button, and a mark item.  The note
	 * shown is the key played, stepped by hand (semi+ / mixture key+) */
	private static final String[] RUN_KINDS = { "open", "stopped", "reed",
		"mixture", "mutation", "other" };
	private final Handler run_handler = new Handler();
	private final Runnable run_tick = new Runnable() {
		public void run() {
			if (!RunRecorder.active()) {
				return;
			}
			if (RunRecorder.limit()) {
				run_stop("limit");
				return;
			}
			if (RunRecorder.failed()) {
				run_stop("write error");
				Toast.makeText(TunerTime.this, R.string.run_failed,
						Toast.LENGTH_LONG).show();
				return;
			}
			invalidateOptionsMenu();
			run_handler.postDelayed(this, 1000);
		} };

	private void run_dialog() {
		final SharedPreferences prefs =
			PreferenceManager.getDefaultSharedPreferences(this);
		int pad = (int)(16*getResources().getDisplayMetrics().density);
		LinearLayout l = new LinearLayout(this);
		l.setOrientation(LinearLayout.VERTICAL);
		l.setPadding(pad, pad/2, pad, 0);
		TextView help = new TextView(this);
		help.setText(R.string.run_help);
		l.addView(help);
		final EditText organ = run_field(l, R.string.run_organ,
				prefs.getString("run_organ", ""));
		final EditText stop = run_field(l, R.string.run_stop_name,
				prefs.getString("run_stop", ""));
		final Spinner kind = new Spinner(this);
		ArrayAdapter<CharSequence> a = ArrayAdapter.createFromResource(
			this, R.array.run_kinds,
			android.R.layout.simple_spinner_item);
		a.setDropDownViewResource(
			android.R.layout.simple_spinner_dropdown_item);
		kind.setAdapter(a);
		kind.setSelection(Math.max(0, Arrays.asList(RUN_KINDS)
			.indexOf(prefs.getString("run_kind", "open"))));
		l.addView(kind);
		final EditText footage = run_field(l, R.string.run_footage,
			(parms.focus_ratio > 0.0d) ?
				parms.footage_str(parms.focus_ratio).trim() : "");
		final EditText notes = run_field(l, R.string.run_note, "");
		ScrollView sv = new ScrollView(this);
		sv.addView(l);
		new AlertDialog.Builder(this)
			.setTitle(R.string.run_title)
			.setView(sv)
			.setPositiveButton(R.string.run_start,
				new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface d, int w) {
					RunRecorder.Meta m = new RunRecorder.Meta();
					m.organ = organ.getText().toString();
					m.stop = stop.getText().toString();
					m.kind = RUN_KINDS[Math.max(0,
						kind.getSelectedItemPosition())];
					m.footage = footage.getText().toString();
					m.note = notes.getText().toString();
					prefs.edit().putString("run_organ", m.organ)
						.putString("run_stop", m.stop)
						.putString("run_kind", m.kind).apply();
					run_start(m);
				} })
			.setNegativeButton(R.string.cancel, null)
			.show();
	}

	private EditText run_field(LinearLayout l, int hint, String text) {
		boolean multi = (hint == R.string.run_note);
		EditText e = new EditText(this);
		e.setHint(hint);
		e.setText(text);
		e.setInputType(InputType.TYPE_CLASS_TEXT |
			InputType.TYPE_TEXT_FLAG_CAP_SENTENCES |
			(multi ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
		l.addView(e);
		return e;
	}

	private void run_start(RunRecorder.Meta m) {
		if ((strobe == null) || parms.fft_mode || parms.preset_mode) {
			/* the menu offers it only in strobe mode */
			Toast.makeText(this, R.string.run_need_strobe,
					Toast.LENGTH_LONG).show();
			return;
		}
		String before = auto_str(parms.auto_note ? 1 : 2, near);
		String version = "?";
		try {
			android.content.pm.PackageInfo pi = getPackageManager()
				.getPackageInfo(getPackageName(), 0);
			version = pi.versionName + " (" + pi.versionCode + ")";
		} catch (Exception e) {
		}
		StringBuilder offs = new StringBuilder();
		if (parms.temper_offsets() == null) {
			offs.append("none");
		} else {
			for (int k = 0; k < 12; k++) {
				/* C4 is semitone 39 */
				offs.append((k > 0) ? " " : "").append(
					String.format(Locale.US, "%.1f",
					parms.temper_offset((39+k)*100*U)/10.0d));
			}
		}
		StringBuilder rows = new StringBuilder();
		for (int i = 0; i < parms.num_strobes; i++) {
			rows.append((i > 0) ? ", " : "").append(
				parms.footage_str(parms.row_ratio(i)).trim());
		}
		preset p = preset.find(parms.preset_id);
		String h = "app " + version + "\n" +
			"audio " + parms.audio_source + " / " +
				parms.microphone + " (in use: " +
				MicSelect.active_name + ")\n" +
			"A_freq " + (parms.A_freq/10.0) + "\n" +
			"stretch " + (parms.stretch()/10.0) + " c/oct\n" +
			"temperament " + ((p != null) ? p.name : "none") + "\n" +
			"temper_offsets " + offs + "\n" +
			"organ_mode " + parms.organ_mode + "\n" +
			"mixture_mode " + parms.mixture_mode + "\n" +
			"focus_ratio " + parms.focus_ratio + "\n" +
			"mixture_focus_follow " + parms.mixture_focus_follow +
				"\n" +
			"rows " + rows + "\n" +
			"auto_before " + before + "\n";
		run_lock();	/* the note is stepped by hand */
		/* turning the phone would recreate the screen and end the run */
		setRequestedOrientation(
			android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED);
		if (!RunRecorder.start(this, m, h)) {
			parms.auto_note = run_auto_before;
			setRequestedOrientation(android.content.pm.ActivityInfo
				.SCREEN_ORIENTATION_UNSPECIFIED);
			Toast.makeText(this, R.string.run_failed,
					Toast.LENGTH_LONG).show();
			return;
		}
		cause = "run start";
		update_status();
		invalidateOptionsMenu();
		run_handler.postDelayed(run_tick, 1000);
		Toast.makeText(this, R.string.run_started,
				Toast.LENGTH_LONG).show();
	}

	/* the key shown, for the run's truth (update_status()) */
	private void run_key() {
		if (!RunRecorder.active()) {
			return;
		}
		int k;
		double hz;
		if (parms.mixture_mode) {
			k = nearest_step(parms.mixture_cents);
			hz = cents_to_freq(step_cents(k), 1);
		} else {
			k = (parms.note_cents + 50*U) / (100*U);
			hz = cents_to_freq(parms.strobe_cents, 1);
		}
		int chain = parms.chain_at(k*100*U, parms.note_alt);
		if ((chain != parms.NO_CHAIN) &&
		    (parms.temper_alts(k*100*U) > 1)) {
			/* Dis and Es: two keys on one semitone */
			RunRecorder.key(k, parms.note_alt, new note(
				k*100*U).spelled_english(chain), hz);
			return;
		}
		RunRecorder.key(k, 0, DebugRecorder.name(k*100*U), hz);
	}

	private void run_stop(String how) {
		boolean was = RunRecorder.active();
		File f = RunRecorder.stop(how);
		run_handler.removeCallbacks(run_tick);
		setRequestedOrientation(
			android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		invalidateOptionsMenu();
		if (was) {
			/* follow or locked, as before the run */
			parms.auto_note = run_auto_before;
			if (parms.auto_note) {
				last_heard = SystemClock.uptimeMillis();
			}
			cause = "run end";
			update_status();
		}
		if (f != null) {
			Toast.makeText(this, getString(R.string.run_saved,
				f.getName().replace(".txt", ""), f.getParent()),
				Toast.LENGTH_LONG).show();
		}
	}

	private void run_mark_dialog() {
		final String[] items = getResources().getStringArray(
						R.array.run_marks);
		final String[] what = { "noise", "pipe bad", "key wrong",
					"other" };
		/* the key the mark is for, as the moment is taken now */
		final int at = RunRecorder.num_keys();
		new AlertDialog.Builder(this)
			.setTitle(R.string.run_mark)
			.setItems(items, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface d, int w) {
					if (w < what.length - 1) {
						RunRecorder.mark("key " + at + ": " +
								what[w]);
						return;
					}
					final EditText e = new EditText(
							TunerTime.this);
					new AlertDialog.Builder(TunerTime.this)
						.setTitle(R.string.run_mark)
						.setView(e)
						.setPositiveButton(R.string.run_ok,
						    new DialogInterface.OnClickListener() {
						    public void onClick(
							DialogInterface d2, int w2) {
							RunRecorder.mark("key " + at +
							  ": " + e.getText());
						    } })
						.setNegativeButton(R.string.cancel,
							null)
						.show();
				} })
			.setNegativeButton(R.string.cancel, null)
			.show();
	}
}
