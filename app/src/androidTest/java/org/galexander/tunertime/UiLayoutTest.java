package org.galexander.tunertime;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.DialogPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.PopupMenu;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/* Every screen, tab, menu and dialog in portrait and landscape, at each
 * font scale, through UiCheck's generic checks.  Each test goes through
 * all its states before failing, and fails only on problems that are not
 * in androidTest/assets/known_ui_issues.txt.
 *
 * Instrumentation arguments (-e name value, or with Gradle
 * -Pandroid.testInstrumentationRunnerArguments.name=value):
 *  device       name for the output folder (default: model, api, dpi)
 *  fontScales   default "1.0,1.3"
 *  orientations default "port,land"
 *  strict       "true" fails on known problems too
 *  screenshots  "false" skips them */
@RunWith(AndroidJUnit4.class)
public class UiLayoutTest {
	@Rule
	public GrantPermissionRule mic = GrantPermissionRule.grant(
				Manifest.permission.RECORD_AUDIO);

	private static UiCheck ui;
	private ActivityScenario<? extends Activity> scenario = null;

	@BeforeClass
	public static void setup_class() {
		/* WindowInspector, WindowInsets.Type: Android 11+ */
		Assume.assumeTrue(Build.VERSION.SDK_INT >= 30);
		ui = new UiCheck();
		ui.setup_device();
	}

	@AfterClass
	public static void teardown_class() {
		if (ui != null) {
			ui.restore_device();
		}
	}

	@Before
	public void setup() {
		ToneGenerator.stop_tone();
	}

	@After
	public void teardown() {
		ToneGenerator.stop_tone();
		if (scenario != null) {
			scenario.close();
			scenario = null;
		}
	}

	/* ---- state ---- */

	private static final int WERCK = 1, LONG = 2, EMPTY = 3;

	/* fresh settings, with a few presets to show */
	private void prefs(boolean organ) {
		SharedPreferences p = PreferenceManager
			.getDefaultSharedPreferences(ui.target);
		SharedPreferences.Editor e = p.edit();
		e.clear();
		e.putBoolean("fft_mode", false);	/* no legacy import */
		e.putBoolean("organ_mode", organ);
		e.putString("presets", WERCK + "," + LONG + "," + EMPTY);
		int t = java.util.Arrays.asList(temperaments.names)
				.indexOf("Werckmeister III");
		e.putString("preset_" + WERCK + "_name", temperaments.names[t]);
		StringBuilder sb = new StringBuilder();
		for (int n : temperaments.make_notes(t)) {
			sb.append((sb.length() > 0) ? "," : "").append(n);
		}
		e.putString("preset_" + WERCK + "_notes10", sb.toString());
		/* long name, as people name their organs' stops */
		e.putString("preset_" + LONG + "_name",
			"St. Marien Hauptwerk Mixtur IV-V 1 1/3' (Schnitger)");
		e.putString("preset_" + LONG + "_notes10",
				"36000,40000,43000,48000,52000,55000,60000");
		e.putString("preset_" + EMPTY + "_name", "empty");
		e.putString("preset_" + EMPTY + "_notes10", "");
		e.putInt("preset_id", WERCK);
		e.commit();
	}

	private void set_pref(String key, int v) {
		PreferenceManager.getDefaultSharedPreferences(ui.target).edit()
			.putInt(key, v).commit();
	}

	private void launch(Class<? extends Activity> c) {
		scenario = ActivityScenario.launch(c);
		ui.settle(800);
		/* rotating with no activity of ours on top doesn't always
		 * stick (Android 15) */
		ui.set_orientation(ui.orient);
	}

	/* runs per() in every language, font scale and orientation */
	private void matrix(Runnable per) {
		List<String> langs = UiCheck.arg_list("languages", "en,de");
		if (Build.VERSION.SDK_INT < 33) {
			langs = UiCheck.arg_list("", "system");
		}
		for (String l : langs) {
			ui.set_language(l);
			for (String fs : UiCheck.arg_list("fontScales",
							"1.0,1.3")) {
				ui.set_font_scale(fs);
				for (String o : UiCheck.arg_list("orientations",
							"port,land")) {
					ui.set_orientation(o);
					per.run();
				}
			}
		}
	}

	private void click(final Activity a, final int id) {
		ui.on_main(new Runnable() {
			public void run() {
				a.findViewById(id).performClick();
			} });
	}

	/* the button of the FFT/strobe-length mode area */
	private void click_mode(final Activity a) {
		ui.on_main(new Runnable() {
			public void run() {
				ViewGroup g = (ViewGroup)a.findViewById(
						R.id.mode_holder);
				View v = g.getChildAt(0).findViewById(
						R.id.mode_button);
				(v != null ? v : g.getChildAt(0)).performClick();
			} });
	}

	/* as if picked from the options menu (titles may be translated) */
	private void menu_action(final Activity a, final int menu,
			final int id) {
		ui.on_main(new Runnable() {
			public void run() {
				PopupMenu pm = new PopupMenu(a,
					a.getWindow().getDecorView());
				a.getMenuInflater().inflate(menu, pm.getMenu());
				MenuItem it = pm.getMenu().findItem(id);
				a.onOptionsItemSelected(it);
			} });
	}

	private void options_menu(final Activity a, String screen) {
		ui.on_main(new Runnable() {
			public void run() {
				a.openOptionsMenu();
			} });
		ui.settle(500);
		ui.check(screen);
		ui.back();
	}

	/* ---- main screen ---- */

	@Test
	public void main_normal() {
		prefs(false);
		launch(TunerTime.class);
		matrix(new Runnable() {
			public void run() {
				main_normal_states(ui.current_activity());
			} });
		ui.finish("main_normal");
	}

	private void main_normal_states(final Activity a) {
		click(a, R.id.note_button);
		ui.check("main.note");
		click(a, R.id.auto_button);		/* follow */
		ui.check("main.note.follow");
		click(a, R.id.auto_button);		/* locked */
		ui.check("main.note.locked");
		click(a, R.id.tone_button);
		ui.check("main.note.tone");
		click(a, R.id.tone_button);
		click(a, R.id.afreq_button);
		ui.check("main.afreq");
		click(a, R.id.stretch_button);
		ui.check("main.stretch");
		click(a, R.id.preset_button);
		ui.check("main.preset");
		click(a, R.id.note_button);
		click_mode(a);				/* FFT */
		ui.check("main.fft");
		click_mode(a);				/* back to strobe */
		options_menu(a, "main.menu");
		menu_action(a, R.menu.option, R.id.action_about);
		ui.check("main.about");
		ui.back();
	}

	@Test
	public void main_organ() {
		prefs(true);
		launch(TunerTime.class);
		matrix(new Runnable() {
			public void run() {
				main_organ_states(ui.current_activity());
			} });
		ui.finish("main_organ");
	}

	private void main_organ_states(Activity a) {
		click(a, R.id.note_button);
		ui.check("organ.note");
		click(a, R.id.afreq_button);
		ui.check("organ.afreq");
		click(a, R.id.stretch_button);		/* mixture */
		ui.check("organ.mixture");
		click(a, R.id.preset_button);
		ui.check("organ.preset");
		click(a, R.id.note_button);
		click_mode(a);				/* length mode */
		ui.check("organ.length");
		click(a, R.id.stretch_button);
		ui.check("organ.length.mixture");
		click(a, R.id.preset_button);
		ui.check("organ.length.preset");
		click(a, R.id.note_button);
		click_mode(a);				/* strobe again */
		options_menu(a, "organ.menu");
	}

	/* ---- settings ---- */

	@Test
	public void settings_normal() {
		settings(false);
	}

	@Test
	public void settings_organ() {
		settings(true);
	}

	private void settings(boolean organ) {
		prefs(organ);
		launch(Settings.class);
		final String s = organ ? "settings.organ" : "settings";
		matrix(new Runnable() {
			public void run() {
				settings_states(s, (PreferenceActivity)
						ui.current_activity());
			} });
		ui.finish(s);
	}

	private void settings_states(String s, PreferenceActivity a) {
		final ListView lv = a.getListView();
		ui.on_main(new Runnable() {
			public void run() {
				lv.setSelection(0);
			} });
		ui.check(s + ".p0");
		/* scroll a page at a time down to the end */
		for (int page = 1; (page < 10) && lv.canScrollVertically(1);
		     page++) {
			ui.on_main(new Runnable() {
				public void run() {
					lv.scrollListBy(lv.getHeight()*3/4);
				} });
			ui.check(s + ".p" + page);
		}
		/* every list dialog */
		final ListAdapter ad = lv.getAdapter();
		for (int i = 0; i < ad.getCount(); i++) {
			Object it = ad.getItem(i);
			if (!(it instanceof DialogPreference)) {
				continue;
			}
			final int pos = i;
			ui.on_main(new Runnable() {
				public void run() {
					lv.setSelection(pos);
				} });
			ui.on_main(new Runnable() {
				public void run() {
					View row = lv.getChildAt(pos -
						lv.getFirstVisiblePosition());
					lv.performItemClick(row, pos,
						ad.getItemId(pos));
				} });
			ui.check(s + ".dialog." + ((Preference)it).getKey());
			ui.back();
		}
	}

	/* ---- presets ---- */

	@Test
	public void preset_select() {
		prefs(false);
		launch(PresetSelectActivity.class);
		matrix(new Runnable() {
			public void run() {
				preset_select_states(ui.current_activity());
			} });
		ui.finish("preset_select");
	}

	private void preset_select_states(final Activity a) {
		ui.check("presets");
		options_menu(a, "presets.menu");
		menu_action(a, R.menu.preset_select,
				R.id.action_preset_temperament);
		ui.check("presets.temperament");
		ui.back();
		/* long press on the long-named preset */
		final ListView lv = (ListView)a.findViewById(R.id.preset_list);
		final View[] row = new View[1];
		ui.on_main(new Runnable() {
			public void run() {
				row[0] = lv.getChildAt(1 -
						lv.getFirstVisiblePosition());
			} });
		if (row[0] != null) {
			ui.long_press(row[0]);
			ui.check("presets.context");
			ui.back();
		}
		/* the rename and delete dialogs of the context menu */
		private_dialog(a, "do_rename", LONG, "presets.rename");
		private_dialog(a, "do_delete", LONG, "presets.delete");
	}

	private void private_dialog(final Activity a, final String method,
			final int id, String screen) {
		final boolean[] ok = new boolean[1];
		ui.on_main(new Runnable() {
			public void run() {
				try {
					Method m = PresetSelectActivity.class
						.getDeclaredMethod(method,
							preset.class);
					m.setAccessible(true);
					m.invoke(a, preset.find(id));
					ok[0] = true;
				} catch (Exception e) {
					android.util.Log.w(UiCheck.TAG,
						"can't call " + method, e);
				}
			} });
		if (ok[0]) {
			ui.check(screen);
			/* the keyboard may be up, then the dialog */
			ui.back();
			if (ui.top_root() != ui.current_activity().getWindow()
						.getDecorView()) {
				ui.back();
			}
		}
	}

	@Test
	public void preset_edit() {
		prefs(false);
		matrix(new Runnable() {
			public void run() {
				preset_edit_states();
			} });
		ui.finish("preset_edit");
	}

	private void preset_edit_states() {
		set_pref("preset_id", LONG);
		launch(PresetEditActivity.class);
		final Activity a = ui.current_activity();
		ui.check("preset_edit");
		ui.on_main(new Runnable() {
			public void run() {
				((PresetEditActivity)a).do_delete(null);
			} });
		ui.check("preset_edit.delete");
		ui.back();
		scenario.close();

		set_pref("preset_id", EMPTY);
		launch(PresetEditActivity.class);
		ui.check("preset_edit.empty");
		scenario.close();
		scenario = null;
	}

	/* the measure pages (TunerTime with measure.EXTRA_PRESET), as the
	 * preset editor's "measure notes" opens them */
	@Test
	public void measure() {
		prefs(true);
		matrix(new Runnable() {
			public void run() {
				measure_states();
			} });
		ui.finish("measure");
	}

	private void measure_states() {
		scenario = ActivityScenario.launch(new android.content.Intent(
				ui.target, TunerTime.class)
			.putExtra(measure.EXTRA_PRESET, WERCK));
		ui.settle(800);
		ui.set_orientation(ui.orient);
		ui.check("measure.a");
		click(ui.current_activity(), R.id.measure_action);	/* set */
		ui.check("measure.note");
		scenario.close();
		scenario = null;
	}

	/* the matching table of Werckmeister III: note and 3 columns at
	 * least, more in landscape, with marked cells */
	@Test
	public void matching() {
		prefs(true);
		matrix(new Runnable() {
			public void run() {
				set_pref("preset_id", WERCK);
				launch(MatchActivity.class);
				ui.check("matching");
				scenario.close();
				scenario = null;
			} });
		ui.finish("matching");
	}

	@Test
	public void preset_note() {
		prefs(false);
		matrix(new Runnable() {
			public void run() {
				preset_note_states();
			} });
		ui.finish("preset_note");
	}

	private void preset_note_states() {
		set_pref("preset_id", LONG);
		set_pref("preset_note", 2);
		launch(PresetNoteActivity.class);
		final Activity a = ui.current_activity();
		ui.check("preset_note");
		ui.on_main(new Runnable() {
			public void run() {
				((PresetNoteActivity)a).do_delete(null);
			} });
		ui.check("preset_note.delete");
		ui.back();
		scenario.close();
		scenario = null;
	}
}
