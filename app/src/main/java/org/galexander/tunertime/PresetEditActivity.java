package org.galexander.tunertime;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PresetEditActivity extends Activity {
	private EditText name_view = null;
	private ListView list = null;
	private static final int REQ_MEASURE = 1;
	private View match_button = null, measure_button = null;
	/* all 12 notes were just measured: matching is offered, its button
	 * selected (no popup) */
	private boolean offer_match = false;

	protected void attachBaseContext(android.content.Context base) {
		super.attachBaseContext(Lang.wrap(base));
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		parms.init(this);
		setTitle(R.string.title_preset_edit);
		setContentView(R.layout.preset_edit);

		name_view = (EditText)findViewById(R.id.preset_name);
		list = (ListView)findViewById(R.id.note_list);
		list.setEmptyView(findViewById(R.id.list_empty));
		match_button = findViewById(R.id.matching);
		measure_button = findViewById(R.id.measure_notes);
		if (savedInstanceState != null) {
			offer_match = savedInstanceState.getBoolean(
					"offer_match", false);
		}
		list.setOnItemClickListener(
			new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent,
						View v, int pos, long id) {
				preset p = get_preset();
				if ((pos < 0) || (pos >= p.notes.size())) {
					return;
				}
				parms.preset_note = pos;
				startActivity(new Intent(v.getContext(),
						PresetNoteActivity.class));
			} });
	}

	public void onResume() {
		parms.load();
		list.setAdapter(new SimpleAdapter(this, notes_list(),
			R.layout.list_entry, new String[] { "text" },
			new int[] { R.id.list_entry_text }));
		name_view.setText(get_preset().name);
		/* only a temperament, one note per pitch class, is matched */
		boolean t = (matching.table(get_preset().notes) != null);
		match_button.setEnabled(t);
		match_button.setAlpha(t ? 1.0f : 0.4f);
		match_button.setSelected(offer_match &&
					 match_button.isEnabled());
		/* Pythagorean and meantone are calculated, not measured,
		 * and need their Dis and Es apart (Onno, 23 Oct) */
		boolean m = (get_preset().wolf_template() < 1);
		measure_button.setEnabled(m);
		measure_button.setAlpha(m ? 1.0f : 0.4f);
		super.onResume();
	}

	protected void onSaveInstanceState(Bundle b) {
		super.onSaveInstanceState(b);
		b.putBoolean("offer_match", offer_match);
	}

	protected void onActivityResult(int req, int res, Intent data) {
		super.onActivityResult(req, res, data);
		if ((req == REQ_MEASURE) && (res == RESULT_OK) &&
		    (data != null) &&
		    data.getBooleanExtra(measure.EXTRA_DONE, false)) {
			offer_match = true;
		}
	}

	public void onPause() {
		if (parms.preset_id != -1) {
			get_preset().name = name_view.getText().toString();
		}
		parms.save();
		super.onPause();
	}

	private preset get_preset() {
		preset p = preset.find(parms.preset_id);
		if (p == null) {
			p = preset.make();
			parms.preset_id = p.id;
		}
		return p;
	}

	private List<HashMap<String, String> > notes_list() {
		List<HashMap<String, String> > ret =
			new ArrayList<HashMap<String, String> >();
		preset p = get_preset();
		p.sort_notes();
		for (Integer i : p.notes) {
			note n = new note(i);
			HashMap<String, String> t =
				new HashMap<String, String>();
			t.put("text", n.full_str);
			ret.add(t);
		}
		return ret;
	}

	public void do_new(View v) {
		List<Integer> notes = get_preset().notes;
		int n = notes.size();
		notes.add((n >= 1) ? notes.get(n-1)
					    : 4800*preset.UNITS_PER_CENT);
		parms.preset_note = n;
		startActivity(new Intent(v.getContext(),
				PresetNoteActivity.class));
	}

	/* the notes by ear on the strobe: the A, then the 12 notes
	 * (TunerTime's measure pages, on top of this one) */
	public void do_measure(View v) {
		preset p = get_preset();
		if (p.wolf_template() >= 1) {
			return;
		}
		EventLog.ui("preset edit: measure notes of " + p.name);
		startActivityForResult(new Intent(this, TunerTime.class)
			.putExtra(measure.EXTRA_PRESET, p.id), REQ_MEASURE);
	}

	/* the preset beside the nearest known temperaments */
	public void do_matching(View v) {
		preset p = get_preset();
		if (matching.table(p.notes) == null) {
			return;
		}
		EventLog.ui("preset edit: matching " + p.name);
		offer_match = false;
		startActivity(new Intent(this, MatchActivity.class));
	}

	public void do_delete(View v) {
		DialogInterface.OnClickListener listener =
			new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface d, int which) {
				if (which == DialogInterface.BUTTON_POSITIVE) {
					preset.presets.remove(get_preset());
					parms.preset_id = -1;
					finish();
				}
			} };
		new AlertDialog.Builder(this)
			.setTitle(R.string.confirm)
			.setMessage(getString(R.string.really_delete,
					get_preset().name))
			.setPositiveButton(R.string.delete, listener)
			.setNegativeButton(R.string.cancel, listener)
			.show();
	}
}
