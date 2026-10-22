package org.galexander.tunertime;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class Settings extends PreferenceActivity {
	public static final String EXTRA_OPEN_STROBE_ROWS =
			"org.galexander.tunertime.OPEN_STROBE_ROWS";
	private PreferenceCategory strobe_category;
	private Preference harmonics, organ_footages;

	protected void attachBaseContext(Context base) {
		super.attachBaseContext(Lang.wrap(base));
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		/* the manifest label is not translated before Android 13 */
		setTitle(R.string.title_settings);
		addPreferencesFromResource(R.xml.preferences);
		setup_language();
		setup_audio();
		/* edge-to-edge (Android 15+): keep the list clear of the
		 * action bar and navigation bar, like the other screens'
		 * layouts do with fitsSystemWindows */
		ViewGroup content = (ViewGroup)findViewById(android.R.id.content);
		if (content.getChildCount() > 0) {
			content.getChildAt(0).setFitsSystemWindows(true);
		}
		organ_rows_init();
		if (getIntent().getBooleanExtra(EXTRA_OPEN_STROBE_ROWS, false)) {
			final MultiSelectListPreference rows =
				(PreferenceManager.getDefaultSharedPreferences(this)
					.getBoolean("organ_mode", true)) ?
				(MultiSelectListPreference)organ_footages :
				(MultiSelectListPreference)harmonics;
			((DirectMultiSelectListPreference)rows).setOnDialogClosed(
				new Runnable() {
				public void run() { finish(); }
				});
			getListView().post(new Runnable() {
				public void run() {
					((DirectMultiSelectListPreference)rows).openDialog();
				}
			});
		}
	}

	/* "Language / Sprache", see Lang.java */
	private void setup_language() {
		ListPreference lp = (ListPreference)findPreference(Lang.KEY);
		if (lp == null) {
			return;
		}
		/* Android 13+: it may have been changed in the system
		 * settings */
		lp.setValue(Lang.get(this));
		lp.setOnPreferenceChangeListener(
				new Preference.OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference p, Object v) {
				Lang.set(Settings.this, (String)v);
				if (Build.VERSION.SDK_INT < 33) {
					/* after the value is stored */
					getListView().post(new Runnable() {
						public void run() {
							recreate();
						} });
				}
				return true;
			} });
	}

	/* organ mode offers organ footages in place of harmonic numbers */
	private void organ_rows_init() {
		strobe_category = (PreferenceCategory)findPreference(
						"strobe_category");
		harmonics = findPreference("harmonics");
		organ_footages = findPreference("organ_footages");
		Preference organ = findPreference("organ_mode");
		if ((strobe_category == null) || (harmonics == null) ||
		    (organ_footages == null) || (organ == null)) {
			return;
		}
		rows_summary(harmonics, null, R.string.harmonics_summary);
		rows_summary(organ_footages, null, R.string.footages_summary);
		organ_rows_show(PreferenceManager.getDefaultSharedPreferences(
				this).getBoolean("organ_mode", true));
		final Preference.OnPreferenceChangeListener prev =
				organ.getOnPreferenceChangeListener();
		organ.setOnPreferenceChangeListener(
				new Preference.OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference p, Object v) {
				if ((prev != null) &&
				    !prev.onPreferenceChange(p, v)) {
					return false;
				}
				organ_rows_show(((Boolean)v).booleanValue());
				return true;
			} });
		Preference.OnPreferenceChangeListener rows =
				new Preference.OnPreferenceChangeListener() {
			@SuppressWarnings("unchecked")
			public boolean onPreferenceChange(Preference p, Object v) {
				rows_summary(p, (Set<String>)v, (p == harmonics)
					? R.string.harmonics_summary
					: R.string.footages_summary);
				return true;
			} };
		harmonics.setOnPreferenceChangeListener(rows);
		organ_footages.setOnPreferenceChangeListener(rows);
	}

	private void organ_rows_show(boolean organ) {
		strobe_category.removePreference(harmonics);
		strobe_category.removePreference(organ_footages);
		strobe_category.addPreference(organ ? organ_footages
						    : harmonics);
	}

	/* the rows chosen, lowest first: "1, 2, 4, 6, 8: ..." or
	 * "16', 8', 4', 2 2/3', 2': ..." */
	private void rows_summary(Preference p, Set<String> vals, int fmt) {
		MultiSelectListPreference m = (MultiSelectListPreference)p;
		if (vals == null) {
			vals = m.getValues();
		}
		List<String> l = new ArrayList<String>(vals);
		Collections.sort(l, new Comparator<String>() {
			public int compare(String a, String b) {
				return Integer.compare(num(a), num(b));
			} });
		StringBuilder sb = new StringBuilder();
		for (String v : l) {
			int i = m.findIndexOfValue(v);
			if (i < 0) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append(m.getEntries()[i]);
		}
		p.setSummary(getString(fmt, sb.toString()));
	}
	private static int num(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/* the microphone list depends on what is plugged in, and "auto"
	 * says which source it amounts to on this phone */
	private void setup_audio() {
		MicSelect.init(this);
		ListPreference src = (ListPreference)findPreference("audio_source");
		if (src != null) {
			CharSequence[] e = src.getEntries().clone();
			/* entries 1 and 2: unprocessed, voice recognition */
			e[0] = getString(R.string.source_auto_is,
				MicSelect.unprocessed_supported() ? e[1] : e[2]);
			src.setEntries(e);
		}
		final ListPreference mic =
			(ListPreference)findPreference("microphone");
		if (mic != null) {
			String[][] c = MicSelect.mic_choices();
			mic.setEntries(c[0]);
			mic.setEntryValues(c[1]);
			if (mic.findIndexOfValue(mic.getValue()) < 0) {
				/* device no longer present: falls back to
				 * the system default when recording.  the
				 * summary goes back to the choice ("%s")
				 * once another one is made */
				String v = mic.getValue();	/* type:addr:name */
				mic.setSummary(getString(
					R.string.mic_not_connected,
					v.substring(v.lastIndexOf(':')+1))
						.replace("%", "%%"));
				mic.setOnPreferenceChangeListener(
					new Preference.OnPreferenceChangeListener() {
					public boolean onPreferenceChange(
							Preference p, Object v) {
						mic.setSummary("%s");
						return true;
					} });
			}
		}
	}
}
