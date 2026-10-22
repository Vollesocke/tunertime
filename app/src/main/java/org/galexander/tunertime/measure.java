package org.galexander.tunertime;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

/* Entering a temperament by measuring it (Onno's spec of 21 Oct, "Töne
 * messen"): pages of TunerTime without the tab bar, opened from the
 * preset editor with EXTRA_PRESET, the id of the preset edited.
 *  1. "Set concert pitch" (Kammerton einstellen): the strobe rows 8' to
 *     1/4' on A, the A buttons and Reset; no detection and no Auto (Onno,
 *     22 Oct: the A is set by ear over the A pipes of all octaves).
 *     "Set" takes the A for the measurement.
 *
 * Hosted in TunerTime, as the strobe (Strobe, jni/) is bound to its
 * SurfaceView: a second instance on top of the preset editor, which
 * Back closes.  Its modes, rows, note, A and focus are temporary: set
 * over what parms.load() reads (apply()) and never saved, as
 * TunerTime.onPause() skips parms.save() for it.  So the preset editor's
 * parms.load(), and TunerTime's own when it is back on top, find the
 * user's settings as they were.  The pages' own state survives rotation
 * in the instance state (save()). */
public class measure {
	public static final String EXTRA_PRESET = "measure_preset";
	/* the rows, footages as parms.harm_mult in organ mode: 8' 4' 2' 1'
	 * 1/2' 1/4' */
	private static final int[] ROWS = { 4, 8, 16, 32, 64, 128 };
	private static final int U = preset.UNITS_PER_CENT;
	private static final int A2 = 2400*U;	/* the A of the 8' octave */

	private final TunerTime tt;
	final int preset_id;
	private int page = 1;
	private int a_freq = 0;		/* 0: the user's, at the first apply() */
	private double focus = 0.0d;	/* a row tapped on these pages */
	private boolean resumed = false;

	private final View a_page;	/* TunerTime's A buttons */
	private final Button action;

	measure(TunerTime tt_, View afreq_buttons, Bundle saved) {
		tt = tt_;
		preset_id = tt.getIntent().getIntExtra(EXTRA_PRESET, -1);
		a_page = afreq_buttons;
		/* the A is set by ear here (Onno, 22 Oct) */
		View auto = a_page.findViewById(R.id.auto_afreq);
		if (auto != null) {
			auto.setVisibility(View.GONE);
		}
		LayoutInflater fl8r = tt.getLayoutInflater();
		action = (Button)fl8r.inflate(R.layout.measure_action,
				(ViewGroup)tt.findViewById(R.id.mode_holder), false);
		action.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (page == 1) {
					set_a();
				}
			} });
		if (saved != null) {
			page = saved.getInt("measure_page", 1);
			a_freq = saved.getInt("measure_a", 0);
			focus = saved.getDouble("measure_focus", 0.0d);
		}
	}

	/* TunerTime.onSaveInstanceState() */
	void save(Bundle b) {
		sync();
		b.putInt("measure_page", page);
		b.putInt("measure_a", a_freq);
		b.putDouble("measure_focus", focus);
	}

	/* what the buttons changed in parms, while these pages show */
	private void sync() {
		if (resumed) {
			a_freq = parms.A_freq;
			focus = parms.focus_ratio;
		}
	}

	/* TunerTime.onResume(), right after parms.load(): the pages' settings
	 * over the user's, which are not saved from here */
	void apply() {
		if (a_freq == 0) {
			a_freq = parms.A_freq;	/* the user's to start from */
		}
		parms.organ_mode = true;
		parms.fft_mode = false;
		parms.length_mode = false;
		parms.mixture_mode = false;
		parms.preset_mode = false;
		parms.auto_note = false;	/* no detection */
		/* measured against equal temperament, not the preset */
		parms.preset_id = -1;
		parms.note_alt = 0;
		int[] m = new int[parms.MAX_STROBES];
		for (int i = 0; i < m.length; i++) {
			m[i] = (i < ROWS.length) ? ROWS[i] : 1;
		}
		parms.harm_mult = m;
		parms.num_strobes = ROWS.length;
		parms.A_freq = a_freq;
		parms.focus_ratio = focus;
		parms.note_focus = focus;
		parms.mixture_focus = focus;
		parms.note_cents = A2;
		resumed = true;
	}

	/* TunerTime.onPause(): instead of parms.save() */
	void pause() {
		sync();
		resumed = false;
	}

	/* TunerTime.onResume(), in place of set_mode(): the page's buttons */
	void show() {
		ViewGroup bh = (ViewGroup)tt.findViewById(R.id.buttons_holder);
		ViewGroup mh = (ViewGroup)tt.findViewById(R.id.mode_holder);
		bh.removeAllViews();
		bh.addView(a_page);
		mh.removeAllViews();
		mh.addView(action);
		action.setText(R.string.measure_set);
		tt.refresh("measure: page " + page);
	}

	/* the end of TunerTime.update_status(): title and status */
	void update() {
		tt.set_title(tt.getString(R.string.title_measure_a), null);
		((TextView)tt.findViewById(R.id.stretch_display)).setText("");
	}

	/* page 1, "set": the A for the measurement */
	private void set_a() {
		a_freq = parms.A_freq;
		tt.refresh("measure: A set");
	}

	/* Back: true if taken here */
	boolean back() {
		return false;
	}
}
