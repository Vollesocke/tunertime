package org.galexander.tunertime;

import android.app.Activity;
import android.content.Intent;
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
 *     "Set" takes the A for the measurement and opens page 2.
 *  2. "Enter note: <preset>" (Ton eingeben): the same rows on a note of
 *     the 8' octave, C to H; note+ and note- step through it, wrapping;
 *     cent +-1 and +-0.1 set the note's offset from equal temperament
 *     (against the A of page 1).  "Store note" writes it into the preset
 *     (measured.store(): only the 12 semitones, Onno 22 Oct) and steps
 *     to the next note not stored yet.  It starts from the preset's own
 *     notes, if any.  After the 12th the table is shifted to A = 0
 *     (measured.a_to_zero(), Onno 22 Oct) and the preset editor is back,
 *     told so by EXTRA_DONE.  Back goes to page 1, and from there to the
 *     preset editor; what was stored stays.
 *
 * Hosted in TunerTime, as the strobe (Strobe, jni/) is bound to its
 * SurfaceView: a second instance on top of the preset editor, which
 * Back closes.  Its modes, rows, note, A and focus are temporary: set
 * over what parms.load() reads (apply()) and never saved, as
 * TunerTime.onPause() skips parms.save() for it.  So the preset editor's
 * parms.load(), and TunerTime's own when it is back on top, find the
 * user's settings as they were; only the preset is written, note by
 * note (parms.save_presets()).  The pages' own state survives rotation
 * in the instance state (save()). */
public class measure {
	public static final String EXTRA_PRESET = "measure_preset";
	/* in the result: all 12 notes were stored */
	public static final String EXTRA_DONE = "measure_done";
	/* the rows, footages as parms.harm_mult in organ mode: 8' 4' 2' 1'
	 * 1/2' 1/4' */
	private static final int[] ROWS = { 4, 8, 16, 32, 64, 128 };
	private static final int U = preset.UNITS_PER_CENT, SEMI = 100*U;
	private static final int A2 = 2400*U;	/* the A of the 8' octave */
	private static final int C2 = 1500*U;	/* its C */

	private final TunerTime tt;
	final int preset_id;
	private int page = 1;
	private int a_freq = 0;		/* 0: the user's, at the first apply() */
	private double focus = 0.0d;	/* a row tapped on these pages */
	private boolean resumed = false;
	/* page 2: the note (pitch class, 0 = C), each one's offset in tenths
	 * of a cent (null until read from the preset), and those stored */
	private int pc = 0;
	private int[] off = null;
	private boolean[] stored = new boolean[12];

	private final View a_page;	/* TunerTime's A buttons */
	private final View note_page;
	private final TextView note_name, note_cents;
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
		note_page = fl8r.inflate(R.layout.measure_note,
			(ViewGroup)tt.findViewById(R.id.buttons_holder), false);
		note_name = (TextView)note_page.findViewById(
				R.id.measure_note_name);
		note_cents = (TextView)note_page.findViewById(
				R.id.measure_note_cents);
		button(R.id.measure_semi_plus, 1, 0);
		button(R.id.measure_semi_minus, -1, 0);
		button(R.id.measure_cent_plus1, 0, U);
		button(R.id.measure_cent_minus1, 0, -U);
		button(R.id.measure_cent_plus01, 0, 1);
		button(R.id.measure_cent_minus01, 0, -1);
		action = (Button)fl8r.inflate(R.layout.measure_action,
				(ViewGroup)tt.findViewById(R.id.mode_holder), false);
		action.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (page == 1) {
					set_a();
				} else {
					store();
				}
			} });
		if (saved != null) {
			page = saved.getInt("measure_page", 1);
			a_freq = saved.getInt("measure_a", 0);
			focus = saved.getDouble("measure_focus", 0.0d);
			pc = saved.getInt("measure_pc", 0);
			off = saved.getIntArray("measure_off");
			boolean[] s = saved.getBooleanArray("measure_stored");
			if (s != null) {
				stored = s;
			}
		}
	}

	/* page 2's buttons: semi steps the note, cents its offset */
	private void button(int id, final int semi, final int cents) {
		note_page.findViewById(id).setOnClickListener(
				new View.OnClickListener() {
			public void onClick(View v) {
				if (semi != 0) {
					pc = (pc + semi + 12) % 12;
				} else {
					off[pc] = Math.max(-measured.MAX_OFF,
						Math.min(measured.MAX_OFF,
							 off[pc] + cents));
				}
				parms.note_cents = note_cents();
				tt.refresh("measure: " + ((semi > 0) ? "semi+" :
					(semi < 0) ? "semi-" : ((cents > 0) ?
					"+" : "") + cents/(double)U + " c"));
			} });
	}

	/* TunerTime.onSaveInstanceState() */
	void save(Bundle b) {
		sync();
		b.putInt("measure_page", page);
		b.putInt("measure_a", a_freq);
		b.putDouble("measure_focus", focus);
		b.putInt("measure_pc", pc);
		b.putIntArray("measure_off", off);
		b.putBooleanArray("measure_stored", stored);
	}

	/* what the buttons changed in parms, while these pages show */
	private void sync() {
		if (resumed) {
			a_freq = parms.A_freq;
			focus = parms.focus_ratio;
		}
	}

	/* the note shown: page 1 the A, page 2 its note with its offset */
	private int note_cents() {
		return (page == 1) ? A2 : (C2 + pc*SEMI + off[pc]);
	}

	/* TunerTime.onResume(), right after parms.load(): the pages' settings
	 * over the user's, which are not saved from here */
	void apply() {
		if (a_freq == 0) {
			a_freq = parms.A_freq;	/* the user's to start from */
		}
		if (off == null) {
			/* a preset with notes: edited from them */
			preset p = preset.find(preset_id);
			off = (p != null) ? measured.offsets(p.notes)
					  : new int[12];
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
		parms.note_cents = note_cents();
		resumed = true;
	}

	/* TunerTime.onPause(): instead of parms.save() */
	void pause() {
		sync();
		resumed = false;
	}

	/* TunerTime.onResume(), in place of set_mode(), and on a change of
	 * page: the page's buttons */
	void show() {
		ViewGroup bh = (ViewGroup)tt.findViewById(R.id.buttons_holder);
		ViewGroup mh = (ViewGroup)tt.findViewById(R.id.mode_holder);
		bh.removeAllViews();
		bh.addView((page == 1) ? a_page : note_page);
		mh.removeAllViews();
		mh.addView(action);
		action.setText((page == 1) ? R.string.measure_set
					   : R.string.measure_store);
		parms.note_cents = note_cents();
		tt.refresh("measure: page " + page);
	}

	/* the end of TunerTime.update_status(): title and readouts */
	void update() {
		TextView status = (TextView)tt.findViewById(R.id.stretch_display);
		if (page == 1) {
			tt.set_title(tt.getString(R.string.title_measure_a), null);
			status.setText("");
			return;
		}
		preset p = preset.find(preset_id);
		tt.set_title(tt.getString(R.string.title_measure_note,
				(p != null) ? p.name : ""), null);
		int n = 0;
		for (boolean s : stored) {
			n += s ? 1 : 0;
		}
		status.setText(tt.getString(R.string.measure_progress, n));
		note_name.setText(new note(C2 + pc*SEMI).organ_name());
		/* a note stored on these pages in the accent colour */
		note_name.setTextColor(tt.getResources().getColor(stored[pc] ?
			R.color.accent : R.color.text));
		note_cents.setText(note.cents_str(off[pc]));
	}

	/* page 1, "set": the A for the measurement, on to the notes */
	private void set_a() {
		a_freq = parms.A_freq;
		page = 2;
		EventLog.ui("measure: A set to " + (a_freq/10.0d) + " Hz");
		show();
	}

	/* page 2, "store note": into the preset, then the next one not
	 * stored yet; after the 12th the table to A = 0 and back */
	private void store() {
		preset p = preset.find(preset_id);
		if (p == null) {
			tt.finish();		/* deleted meanwhile */
			return;
		}
		measured.store(p.notes, pc, off[pc]);
		stored[pc] = true;
		EventLog.ui("measure: stored " + note.class_name(pc) + " " +
			    note.cents_str(off[pc]) + " in " + p.name);
		int next = -1;
		for (int i = 1; (i <= 12) && (next < 0); i++) {
			if (!stored[(pc + i) % 12]) {
				next = (pc + i) % 12;
			}
		}
		if (next < 0) {
			int a = measured.a_to_zero(p.notes);
			parms.save_presets();
			EventLog.ui("measure: 12 notes stored in " + p.name +
				((a != 0) ? ", shifted by " + note.cents_str(-a) +
				 " to A = 0" : ""));
			tt.setResult(Activity.RESULT_OK,
				new Intent().putExtra(EXTRA_DONE, true));
			tt.finish();
			return;
		}
		parms.save_presets();
		pc = next;
		parms.note_cents = note_cents();
		tt.refresh("measure: next note");
	}

	/* Back: true if taken here (page 2 to page 1) */
	boolean back() {
		if (page != 2) {
			return false;
		}
		page = 1;
		EventLog.ui("measure: back to the A");
		show();
		return true;
	}
}
