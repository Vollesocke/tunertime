package org.galexander.tunertime;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import java.util.List;

public class PresetNoteActivity extends Activity {
	private TextView pn_text;
	private EditText pn_cents;
	private View[] pn_notes = new View[7];
	private final static int[] pn_note_vals = new int[] { 0,2,4,5,7,9,11 };
	private View pn_sharp;
	private View[] pn_octs = new View[9];

	private List<Integer> notes;
	private int note_idx;

		/* notes are in tenths of a cent */
	private static final int U = preset.UNITS_PER_CENT;

	/*        C  C# D  D# E  F  F# G  G# A  A# B */
	private static final int[] is_sharp = new int[]
		{ 0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 1, 0 };
	private static final int[] can_sharpen = new int[]
		{ 1, 0, 1, 0, 0, 1, 0, 1, 0, 1, 0, 0 };
	private static final int[] sharp_swap = new int[]
		{ 1, 0, 3, 2, 4, 6, 5, 8, 7, 10,9, 11};
	private static final int[] no_sharps = new int[]
		{ 0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6 };

	protected void attachBaseContext(android.content.Context base) {
		super.attachBaseContext(Lang.wrap(base));
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		parms.init(this);
		setTitle(R.string.title_preset_note);
		setContentView(R.layout.preset_note);
		pn_text = (TextView)findViewById(R.id.pn_text);
		pn_cents = (EditText)findViewById(R.id.pn_cents);
		pn_cents.setOnEditorActionListener(new SubmitListener() {
			public void onSubmit(String s) {
				try {
					double d = Double.parseDouble(
						s.trim().replace(',', '.'));
					set_cents((int)Math.round(d*U));
				} catch (Exception x) { }
			} });
		pn_notes[0] = findViewById(R.id.pn_note_0);
		pn_notes[1] = findViewById(R.id.pn_note_1);
		pn_notes[2] = findViewById(R.id.pn_note_2);
		pn_notes[3] = findViewById(R.id.pn_note_3);
		pn_notes[4] = findViewById(R.id.pn_note_4);
		pn_notes[5] = findViewById(R.id.pn_note_5);
		pn_notes[6] = findViewById(R.id.pn_note_6);
		pn_sharp = findViewById(R.id.pn_sharp);
		pn_octs[0] = findViewById(R.id.pn_oct_0);
		pn_octs[1] = findViewById(R.id.pn_oct_1);
		pn_octs[2] = findViewById(R.id.pn_oct_2);
		pn_octs[3] = findViewById(R.id.pn_oct_3);
		pn_octs[4] = findViewById(R.id.pn_oct_4);
		pn_octs[5] = findViewById(R.id.pn_oct_5);
		pn_octs[6] = findViewById(R.id.pn_oct_6);
		pn_octs[7] = findViewById(R.id.pn_oct_7);
		pn_octs[8] = findViewById(R.id.pn_oct_8);
	}

	/* flip the sign of the cents field and apply it */
	public void do_negate(View v) {
		String s = pn_cents.getText().toString().trim();
		if (s.startsWith("-")) {
			s = s.substring(1);
		} else if (s.startsWith("+")) {
			s = "-" + s.substring(1);
		} else {
			s = "-" + s;
		}
		pn_cents.setText(s);
		pn_cents.setSelection(s.length());
		try {
			double d = Double.parseDouble(s.replace(',', '.'));
			set_cents((int)Math.round(d*U));
		} catch (Exception x) { }
	}

	public void onResume() {
		parms.load();
		super.onResume();
		preset p = preset.find(parms.preset_id);
		if (p == null) {
			finish();
			return;
		}
		notes = p.notes;
		note_idx = parms.preset_note;
		if ((note_idx < 0) || (note_idx >= notes.size())) {
			finish();
			return;
		}
		/* "B" or German "H"; with "#" the sharp of A is B or German B */
		((TextView)pn_notes[6]).setText(note.class_name(11));
		/* 0..8, or with German names C2 (subscript) .. c5 */
		for (int i = 0; i < pn_octs.length; i++) {
			((TextView)pn_octs[i]).setText(note.octave_name(i));
		}
		update();
	}

	public void onPause() {
		parms.save();
		super.onPause();
		notes = null;
	}

		/* selected button: accent outline (drawable/mode_button) */
	private void set_brightness(View v, boolean bright) {
		v.setSelected(bright);
	}

	private void update() {
		int cents = notes.get(note_idx);
		if (cents < 0) {
			cents = 0;
			notes.set(note_idx, cents);
		} else if (cents > 9600*U) {
			cents = 9600*U;
			notes.set(note_idx, cents);
		}

		note n = new note(cents);
		pn_text.setText(n.full_str);
		pn_cents.setText(note.cents_edit_str(n.cents));

		int t = no_sharps[n.note_idx];
		for (int i = 0; i < pn_notes.length; i++) {
			set_brightness(pn_notes[i], (t == i));
		}
		set_brightness(pn_sharp, (is_sharp[n.note_idx] != 0));
		for (int i = 0; i < pn_octs.length; i++) {
			set_brightness(pn_octs[i], (n.octave == i));
		}
	}

	public void do_note(View v) {
		if (notes == null) {
			return;		/* deleted, finishing */
		}
		int idx = 0;
		for (int i = 0; i < pn_notes.length; i++) {
			if (v == pn_notes[i]) {
				idx = pn_note_vals[i];
				break;
			}
		}
		note n = new note(notes.get(note_idx));
		notes.set(note_idx, (n.octave*1200*U) +
			((idx+can_sharpen[idx]*is_sharp[n.note_idx])*100*U) +
			n.cents - 900*U);
		update();
	}

	public void do_sharp(View v) {
		if (notes == null) {
			return;		/* deleted, finishing */
		}
		note n = new note(notes.get(note_idx));
		notes.set(note_idx, (n.octave*1200*U) +
			(sharp_swap[n.note_idx]*100*U) + n.cents - 900*U);
		update();
	}

	public void do_oct(View v) {
		if (notes == null) {
			return;		/* deleted, finishing */
		}
		int oct = 4;
		for (int i = 0; i < pn_octs.length; i++) {
			if (v == pn_octs[i]) {
				oct = i;
				break;
			}
		}
		note n = new note(notes.get(note_idx));
		notes.set(note_idx,
			(oct*1200*U) + (n.note_idx*100*U) + n.cents - 900*U);
		update();
	}

		/* c in tenths of a cent */
	private void set_cents(int c) {
		if (notes == null) {
			return;		/* deleted, finishing */
		}
		int prev = notes.get(note_idx);
		note n = new note(prev);
		int next = (n.octave*1200*U) + (n.note_idx*100*U) + c - 900*U;
		if (prev != next) {
			notes.set(note_idx, next);
			update();
		}
	}

		/* cent steps; crossing +-50c moves on to the next note */
	private void step_cents(int delta) {
		if (notes == null) {
			return;		/* deleted, finishing */
		}
		notes.set(note_idx, notes.get(note_idx) + delta);
		update();
	}
	public void do_cent_incr(View v)  { step_cents( 1*U); }
	public void do_cent_decr(View v)  { step_cents(-1*U); }
	public void do_tenth_incr(View v) { step_cents( 1); }
	public void do_tenth_decr(View v) { step_cents(-1); }

	public void do_save(View v) {
		finish();
	}

	public void do_delete(View v) {
		DialogInterface.OnClickListener listener =
			new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface d, int which) {
				if (which == DialogInterface.BUTTON_POSITIVE) {
					notes.remove(note_idx);
					notes = null;
					parms.preset_note = 0;
					finish();
				}
			} };
		new AlertDialog.Builder(this)
			.setTitle(R.string.confirm)
			.setMessage(getString(R.string.really_delete,
				new note(notes.get(note_idx)).full_str))
			.setPositiveButton(R.string.delete, listener)
			.setNegativeButton(R.string.cancel, listener)
			.show();
	}
}
