package org.galexander.tunertime;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import java.util.List;

/* Edit one note of a preset: pick its pitch class on one octave of keys,
 * type the cents on the built-in number pad.  The octave a note is stored
 * in does not matter (the tuning uses the pitch class only), so every note
 * is stored in octave 4 as soon as it is touched here. */
public class PresetNoteActivity extends Activity {
	/* the note was just added (empty field, not yet typed into) */
	public static final String EXTRA_NEW = "new_note";

	private TextView pn_text;
	private EditText pn_cents;
	private PianoKeyboard pn_keys;

	private List<Integer> notes;
	private int note_idx;
	private int pc;		/* picked pitch class, 0 = C */
	private boolean created;	/* added by this screen (empty until typed) */
	private boolean silent;	/* setting the field from code */

		/* notes are in tenths of a cent */
	private static final int U = preset.UNITS_PER_CENT;
	private static final int MAX_CENTS = 50*U;
	private static final int OCTAVE_C = 3900*U;	/* C4; A4 = 4800 */

		/* pitch class pc (0 = C), c tenths of a cent off, in octave 4 */
	public static int stored(int pc, int c) {
		return OCTAVE_C + pc*100*U + c;
	}

	protected void attachBaseContext(android.content.Context base) {
		super.attachBaseContext(Lang.wrap(base));
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		parms.init(this);
		setTitle(R.string.title_preset_note);
		/* the built-in pad is the keyboard */
		getWindow().setSoftInputMode(
			WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		setContentView(R.layout.preset_note);
		created = (savedInstanceState != null) ?
			savedInstanceState.getBoolean("created", false) :
			getIntent().getBooleanExtra(EXTRA_NEW, false);
		pn_text = (TextView)findViewById(R.id.pn_text);
		pn_cents = (EditText)findViewById(R.id.pn_cents);
		pn_cents.setShowSoftInputOnFocus(false);
		pn_cents.addTextChangedListener(new TextWatcher() {
			public void beforeTextChanged(CharSequence s, int a,
						      int b, int c) { }
			public void onTextChanged(CharSequence s, int a,
						  int b, int c) { }
			public void afterTextChanged(Editable e) {
				if (!silent) {
					typed();
				}
			} });
		pn_keys = (PianoKeyboard)findViewById(R.id.pn_keys);
		pn_keys.setListener(new PianoKeyboard.Listener() {
			public void onKey(int k) {
				do_key(k);
			} });
	}

	protected void onSaveInstanceState(Bundle b) {
		super.onSaveInstanceState(b);
		b.putBoolean("created", created);
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
		load_note();
	}

	public void onPause() {
		parms.save();
		super.onPause();
		notes = null;
	}

		/* the field shows the cents of note_idx; the focus stays there */
	private void load_note() {
		note n = new note(notes.get(note_idx));
		pc = n.note_idx;
		set_field((created && (n.cents == 0)) ? "" : fmt(n.cents));
		pn_cents.requestFocus();
		update();
	}

		/* 30 -> "3", -35 -> "-3.5" (comma with German names) */
	private static String fmt(int c) {
		int a = Math.abs(c);
		return ((c < 0) ? "-" : "") + (a/U) +
			(((a%U) != 0) ? (sep() + Integer.toString(a%U)) : "");
	}

	private static char sep() {
		return note.german() ? ',' : '.';
	}

	private void set_field(String s) {
		silent = true;
		pn_cents.setText(s);
		pn_cents.setSelection(s.length());
		silent = false;
	}

		/* the field in tenths of a cent, empty is 0, at most +-50c: more
		 * would be another pitch class */
	private int field_cents() {
		String s = pn_cents.getText().toString().trim()
				.replace(',', '.');
		double d = 0;
		try {
			d = Double.parseDouble(s);
		} catch (Exception x) { }
		long c = Math.round(d*U);
		return (int)Math.max(-MAX_CENTS, Math.min(MAX_CENTS, c));
	}

		/* the field changed: store the note live */
	private void typed() {
		if (notes == null) {
			return;		/* deleted, finishing */
		}
		String s = pn_cents.getText().toString().trim()
				.replace(',', '.');
		int c = field_cents();
		try {
			if (Math.abs(Double.parseDouble(s)*U) > MAX_CENTS) {
				set_field(fmt(c));
			}
		} catch (Exception x) { }
		notes.set(note_idx, stored(pc, c));
		update();
	}

	private void update() {
		int c = field_cents();
		pn_text.setText(note.class_name(pc) + " " + note.cents_str(c));
		pn_keys.setSelectedKey(pc);
		boolean[] have = new boolean[12];
		for (int i = 0; i < notes.size(); i++) {
			if (i != note_idx) {
				have[new note(notes.get(i)).note_idx] = true;
			}
		}
		pn_keys.setPresent(have);
	}

		/* A key moves this note to that pitch class.  The cents stay as
		 * they are: an empty field of a new note stays empty, the value
		 * of an existing note is kept. */
	private void do_key(int k) {
		if (notes == null) {
			return;		/* deleted, finishing */
		}
		pc = k;
		notes.set(note_idx, stored(pc, field_cents()));
		update();
	}

		/* a key of the number pad; its tag is what it types */
	public void do_pad(View v) {
		if (notes == null) {
			return;
		}
		String t = (String)v.getTag();
		String s = pn_cents.getText().toString().trim();
		boolean neg = s.startsWith("-");
		String body = neg ? s.substring(1) : s;
		int dot = Math.max(body.indexOf('.'), body.indexOf(','));
		if (t.equals("<")) {
			if (body.length() == 0) {
				neg = false;
			} else {
				body = body.substring(0, body.length()-1);
			}
		} else if (t.equals("+-")) {
			neg = !neg;
		} else if (t.equals(",")) {
			if (dot < 0) {
				body = ((body.length() == 0) ? "0" : body) + sep();
			}
		} else if (dot >= 0) {
			if (body.length() - dot - 1 < 1) {	/* tenths only */
				body += t;
			}
		} else if (body.equals("0")) {
			body = t;
		} else if (body.length() < 2) {
			body += t;
		}
		set_field((neg ? "-" : "") + body);
		typed();
	}

	public void do_save(View v) {
		if (notes == null) {
			return;		/* deleted, finishing */
		}
		notes.set(note_idx, stored(pc, field_cents()));
		boolean[] have = new boolean[12];
		int count = 0;
		for (int i : notes) {
			int k = new note(i).note_idx;
			if (!have[k]) {
				have[k] = true;
				count++;
			}
		}
		/* the 12th pitch class completed, or past the H of a complete
		 * preset: back to the list */
		if ((count == 12) && (created || (pc == 11))) {
			finish();
			return;
		}
		/* on with the semitone above: edit that note, or add it */
		int next = (pc + 1) % 12;
		int idx = -1;
		for (int i = 0; (idx < 0) && (i < notes.size()); i++) {
			if (new note(notes.get(i)).note_idx == next) {
				idx = i;
			}
		}
		created = (idx < 0);
		if (created) {
			notes.add(stored(next, 0));
			idx = notes.size() - 1;
		}
		note_idx = idx;
		parms.preset_note = idx;
		load_note();
	}

	public void do_delete(View v) {
		if (notes == null) {
			return;
		}
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
				note.class_name(pc) + " " +
				note.cents_str(field_cents())))
			.setPositiveButton(R.string.delete, listener)
			.setNegativeButton(R.string.cancel, listener)
			.show();
	}
}
