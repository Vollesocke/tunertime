package org.galexander.tunertime;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.text.Collator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PresetSelectActivity extends Activity {
	private ListView list = null;

	private static final int REQ_EXPORT = 1;
	private static final int REQ_IMPORT = 2;
	private static final int MAX_IMPORT = 1024*1024;

	private static final int CM_EDIT = 1, CM_RENAME = 2, CM_DUP = 3,
		CM_UP = 4, CM_DOWN = 5, CM_EXPORT = 6, CM_DELETE = 7;

		/* id of the preset being exported, -1 for all of them.
		 * kept across the file picker in case we get recreated */
	private int export_id = -1;

	protected void attachBaseContext(android.content.Context base) {
		super.attachBaseContext(Lang.wrap(base));
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		parms.init(this);
		setTitle(R.string.title_preset_select);
		setContentView(R.layout.preset_select);
		if (savedInstanceState != null) {
			export_id = savedInstanceState.getInt("export_id", -1);
		}

		list = (ListView)findViewById(R.id.preset_list);
		list.setEmptyView(findViewById(R.id.list_empty));
		list.setOnItemClickListener(
			new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent,
						View v, int pos, long id) {
				if ((pos < 0) ||
				    (pos >= preset.presets.size())) {
					return;
				}
				int selected_id = preset.presets.get(pos).id;
				preset.clear_temporary_wolf_if_different(selected_id);
				parms.preset_id = selected_id;
				finish();
			} });
		registerForContextMenu(list);
	}

	protected void onSaveInstanceState(Bundle b) {
		super.onSaveInstanceState(b);
		b.putInt("export_id", export_id);
	}

	public void onResume() {
		parms.load();
		refresh();
		super.onResume();
	}

	public void onPause() {
		parms.save();
		super.onPause();
	}

	private void refresh() {
		list.setAdapter(new SimpleAdapter(this, presets_list(),
			R.layout.list_entry, new String[] { "text" },
			new int[] { R.id.list_entry_text }));
	}

		/* store a change right away, so that it survives the
		 * parms.load() in onResume() after a dialog or file picker */
	private void changed() {
		parms.save();
		refresh();
	}

	private List<HashMap<String, String> > presets_list() {
		List<HashMap<String, String> > ret =
			new ArrayList<HashMap<String, String> >();
		for (preset p : preset.presets) {
			HashMap<String, String> t =
				new HashMap<String, String>();
			t.put("text", p.name);
			ret.add(t);
		}
		return ret;
	}

	public void do_new(View v) {
		preset.clear_temporary_wolf();
		parms.preset_id = -1;
		startActivity(new Intent(this, PresetEditActivity.class));
	}

	/* long-tap on a preset */
	public void onCreateContextMenu(ContextMenu menu, View v,
				ContextMenu.ContextMenuInfo info) {
		int pos = ((AdapterView.AdapterContextMenuInfo)info).position;
		if ((pos < 0) || (pos >= preset.presets.size())) {
			return;
		}
		menu.setHeaderTitle(preset.presets.get(pos).name);
		menu.add(Menu.NONE, CM_EDIT, Menu.NONE, R.string.cm_edit);
		menu.add(Menu.NONE, CM_RENAME, Menu.NONE, R.string.cm_rename);
		menu.add(Menu.NONE, CM_DUP, Menu.NONE, R.string.cm_duplicate);
		menu.add(Menu.NONE, CM_UP, Menu.NONE, R.string.cm_move_up)
			.setEnabled(pos > 0);
		menu.add(Menu.NONE, CM_DOWN, Menu.NONE, R.string.cm_move_down)
			.setEnabled(pos < preset.presets.size()-1);
		menu.add(Menu.NONE, CM_EXPORT, Menu.NONE, R.string.cm_export);
		menu.add(Menu.NONE, CM_DELETE, Menu.NONE, R.string.cm_delete);
	}

	public boolean onContextItemSelected(MenuItem item) {
		int pos = ((AdapterView.AdapterContextMenuInfo)
				item.getMenuInfo()).position;
		List<preset> l = preset.presets;
		if ((pos < 0) || (pos >= l.size())) {
			return super.onContextItemSelected(item);
		}
		preset p = l.get(pos);
		switch (item.getItemId()) {
		case CM_EDIT:
			parms.preset_id = p.id;
			startActivity(new Intent(this,
					PresetEditActivity.class));
			return true;
		case CM_RENAME:
			do_rename(p);
			return true;
		case CM_DUP: {
			preset n = preset.make();	/* appended to the end */
			n.name = PresetIO.unique_name(p.name, names());
			n.notes.addAll(p.notes);
			l.remove(n);
			l.add(pos+1, n);
			changed();
			return true; }
		case CM_UP:
			if (pos > 0) {
				Collections.swap(l, pos, pos-1);
				changed();
			}
			return true;
		case CM_DOWN:
			if (pos < l.size()-1) {
				Collections.swap(l, pos, pos+1);
				changed();
			}
			return true;
		case CM_EXPORT:
			start_export(p);
			return true;
		case CM_DELETE:
			do_delete(p);
			return true;
		}
		return super.onContextItemSelected(item);
	}

	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.preset_select, menu);
		return super.onCreateOptionsMenu(menu);
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_preset_sort) {
			Collections.sort(preset.presets, new Comparator<preset>() {
				public int compare(preset a, preset b) {
					return String.CASE_INSENSITIVE_ORDER
						.compare(a.name, b.name);
				} });
			changed();
			return true;
		} else if (id == R.id.action_preset_import) {
			Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
			i.addCategory(Intent.CATEGORY_OPENABLE);
			i.setType("*/*");
			/* some providers say octet-stream for .txt */
			i.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
				"text/*", "application/octet-stream" });
			start_picker(i, REQ_IMPORT);
			return true;
		} else if (id == R.id.action_preset_export) {
			start_export(null);
			return true;
		} else if (id == R.id.action_preset_temperament) {
			pick_temperament();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/* add one of the built-in historical temperaments as a new preset
	 * and select it, so note mode is tempered by it right away */
	private void pick_temperament() {
		final String[] names = res_or(R.array.temperament_names,
						temperaments.names);
		String[] about = res_or(R.array.temperament_about,
					temperaments.about);
		String[] items = new String[names.length];
		final ArrayList<Integer> order = new ArrayList<Integer>();
		for (int i = 0; i < items.length; i++) order.add(i);
		final Collator collator = Collator.getInstance(getResources()
				.getConfiguration().locale);
		Collections.sort(order.subList(Math.min(4, order.size()), order.size()),
			new Comparator<Integer>() {
				public int compare(Integer a, Integer b) {
					return collator.compare(names[a], names[b]);
				}
			});
		for (int i = 0; i < items.length; i++) {
			int temperament = order.get(i);
			items[i] = names[temperament] + "\n    "
				+ note.names_in(about[temperament]);
		}
		new AlertDialog.Builder(this)
			.setTitle(R.string.add_temperament_title)
			.setItems(items, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface d, int which) {
					int temperament = order.get(which);
					preset.clear_temporary_wolf();
					preset n = preset.make();
					n.name = PresetIO.unique_name(
							names[temperament], names());
					n.notes.addAll(
							temperaments.make_notes(temperament));
					parms.preset_id = n.id;
					changed();
				} })
			.setNegativeButton(R.string.cancel, null)
			.show();
	}

	/* the translated list, unless it no longer matches
	 * temperaments.java */
	private String[] res_or(int id, String[] fallback) {
		String[] r = getResources().getStringArray(id);
		return (r.length == fallback.length) ? r : fallback;
	}

	private Set<String> names() {
		Set<String> ret = new HashSet<String>();
		for (preset p : preset.presets) {
			ret.add(p.name);
		}
		return ret;
	}

	private EditText edit_dialog_view(String text) {
		EditText e = new EditText(this);
		e.setSingleLine(true);
		e.setText(text);
		e.setSelectAllOnFocus(true);
		return e;
	}

	private View padded(View v) {
		FrameLayout f = new FrameLayout(this);
		int pad = (int)(20 * getResources().getDisplayMetrics().density);
		f.setPadding(pad, pad/2, pad, 0);
		f.addView(v, new FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT,
			ViewGroup.LayoutParams.WRAP_CONTENT));
		return f;
	}

	private void do_rename(final preset p) {
		final EditText e = edit_dialog_view(p.name);
		new AlertDialog.Builder(this)
			.setTitle(R.string.rename_title)
			.setView(padded(e))
			.setPositiveButton(R.string.rename,
				new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface d, int w) {
					p.name = PresetIO.clean_name(
						e.getText().toString());
					changed();
				} })
			.setNegativeButton(R.string.cancel, null)
			.show();
	}

	private void do_delete(final preset p) {
		new AlertDialog.Builder(this)
			.setTitle(R.string.confirm)
			.setMessage(getString(R.string.really_delete, p.name))
			.setPositiveButton(R.string.delete,
				new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface d, int w) {
					preset.presets.remove(p);
					if (parms.preset_id == p.id) {
						parms.preset_id = -1;
					}
					changed();
				} })
			.setNegativeButton(R.string.cancel, null)
			.show();
	}

	private void start_picker(Intent i, int req) {
		try {
			startActivityForResult(i, req);
		} catch (android.content.ActivityNotFoundException ex) {
			/* no file picker on this phone: nothing happens */
		}
	}

	/* p == null exports all presets */
	private void start_export(preset p) {
		String fn = getString(R.string.export_file_name);
		if (p != null) {
			fn = p.name.replaceAll("[\\\\/:*?\"<>|]", "_") + ".txt";
		}
		export_id = (p == null) ? -1 : p.id;
		Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
		i.addCategory(Intent.CATEGORY_OPENABLE);
		i.setType("text/plain");
		i.putExtra(Intent.EXTRA_TITLE, fn);
		start_picker(i, REQ_EXPORT);
	}

	protected void onActivityResult(int req, int res, Intent data) {
		super.onActivityResult(req, res, data);
		if ((res != RESULT_OK) || (data == null) ||
		    (data.getData() == null)) {
			return;
		}
		/* onResume() hasn't run yet, so reload what we saved in
		 * onPause() before working on it */
		parms.load();
		if (req == REQ_EXPORT) {
			do_export(data.getData());
		} else if (req == REQ_IMPORT) {
			do_import(data.getData());
		}
	}

	private static PresetIO.entry to_entry(preset p) {
		return new PresetIO.entry(p.name,
				new ArrayList<Integer>(p.notes));
	}

	private void do_export(Uri uri) {
		List<PresetIO.entry> l = new ArrayList<PresetIO.entry>();
		for (preset p : preset.presets) {
			if ((export_id == -1) || (p.id == export_id)) {
				l.add(to_entry(p));
			}
		}
		try {
			OutputStream os = getContentResolver()
						.openOutputStream(uri);
			if (os == null) {
				throw new java.io.IOException("can't open file");
			}
			try {
				os.write(PresetIO.format(l).getBytes("UTF-8"));
			} finally {
				os.close();
			}
		} catch (Exception ex) {
			error_dialog(getString(R.string.export_failed),
					ex.toString());
		}
	}

	private void do_import(Uri uri) {
		String text;
		try {
			InputStream is = getContentResolver()
						.openInputStream(uri);
			if (is == null) {
				throw new java.io.IOException("can't open file");
			}
			ByteArrayOutputStream bs = new ByteArrayOutputStream();
			try {
				byte buf[] = new byte[8192];
				int n;
				while ((n = is.read(buf)) > 0) {
					bs.write(buf, 0, n);
					if (bs.size() > MAX_IMPORT) {
						throw new java.io.IOException(
							"file is too big");
					}
				}
			} finally {
				is.close();
			}
			text = bs.toString("UTF-8");
		} catch (Exception ex) {
			error_dialog(getString(R.string.import_failed),
					ex.toString());
			return;
		}

		PresetIO.result r = PresetIO.parse(text);
		if (r.errors.size() > 0) {
			/* all or nothing, so a typo doesn't leave half a
			 * file imported */
			/* the messages themselves are about the file format,
			 * whose keywords are English (PresetIO) */
			StringBuilder sb = new StringBuilder(
				getString(R.string.nothing_imported) + "\n\n");
			int n = 0;
			for (String s : r.errors) {
				if (++n > 20) {
					sb.append(getString(R.string.and_more,
						r.errors.size()-20)).append("\n");
					break;
				}
				sb.append(s).append("\n");
			}
			error_dialog(getString(R.string.import_failed),
					sb.toString());
			return;
		}

		Set<String> taken = names();
		int renamed = 0;
		for (PresetIO.entry e : r.presets) {
			preset p = preset.make();
			p.name = PresetIO.unique_name(e.name, taken);
			if (!p.name.equals(e.name)) {
				renamed++;
			}
			taken.add(p.name);
			p.notes.addAll(e.notes);
		}
		changed();
	}

	private void error_dialog(String title, String msg) {
		new AlertDialog.Builder(this)
			.setTitle(title)
			.setMessage(msg)
			.setPositiveButton(R.string.ok, null)
			.show();
	}
}
