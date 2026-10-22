package org.galexander.tunertime;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/* The measure pages (measure, TunerTime with measure.EXTRA_PRESET) from
 * the preset editor: page 1 (the A) -> page 2 -> 12 notes stored -> back
 * at the editor with the table at A = 0, and the user's own settings
 * (footages, note, A, the focus of Ton and Mixtur) as they were.  Run
 * like UiLayoutTest:
 *   ./gradlew connectedUitestAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=org.galexander.tunertime.MeasureTest */
@RunWith(AndroidJUnit4.class)
public class MeasureTest {
	@Rule
	public GrantPermissionRule mic = GrantPermissionRule.grant(
				Manifest.permission.RECORD_AUDIO);

	private static final int U = preset.UNITS_PER_CENT;
	private static final int OTHER = 1, EDITED = 2;
	private static final int D4 = 4100*U;
	private Instrumentation inst;
	private SharedPreferences prefs;
	private ActivityScenario<PresetEditActivity> scenario = null;
	private List<Integer> werck;

	@Before
	public void setup() {
		inst = InstrumentationRegistry.getInstrumentation();
		prefs = PreferenceManager.getDefaultSharedPreferences(
				inst.getTargetContext());
		SharedPreferences.Editor e = prefs.edit();
		e.clear();
		e.putBoolean("fft_mode", false);	/* no legacy import */
		e.putBoolean("organ_mode", true);
		e.putBoolean("german_names", false);
		/* the user's: 4' and 2' rows, D4, A 441.5, Ton focused on
		 * the 4', Mixtur on the 2' */
		e.putStringSet("organ_footages",
			new HashSet<String>(Arrays.asList("8", "16")));
		e.putInt("note_cents10", D4);
		e.putInt("strobe_cents10", D4);
		e.putInt("A_freq", 4415);
		e.putFloat("focus_ratio", 2.0f);
		e.putFloat("note_focus", 2.0f);
		e.putFloat("mixture_focus", 4.0f);
		e.putBoolean("auto_note", true);
		int t = Arrays.asList(temperaments.names)
				.indexOf("Werckmeister III");
		werck = temperaments.make_notes(t);
		e.putString("presets", OTHER + "," + EDITED);
		e.putString("preset_" + OTHER + "_name", "other");
		e.putString("preset_" + OTHER + "_notes10", csv(werck));
		/* edited from Werckmeister III: page 2 starts from it */
		e.putString("preset_" + EDITED + "_name", "by ear");
		e.putString("preset_" + EDITED + "_notes10", csv(werck));
		e.putInt("preset_id", EDITED);
		e.commit();
		scenario = ActivityScenario.launch(PresetEditActivity.class);
		inst.waitForIdleSync();
	}

	@After
	public void teardown() {
		if (scenario != null) {
			scenario.close();
		}
	}

	private static String csv(List<Integer> l) {
		StringBuilder sb = new StringBuilder();
		for (int n : l) {
			sb.append((sb.length() > 0) ? "," : "").append(n);
		}
		return sb.toString();
	}

	private void main(Runnable r) {
		inst.runOnMainSync(r);
		inst.waitForIdleSync();
	}

	private Activity resumed() {
		final Activity[] a = new Activity[1];
		for (int i = 0; (i < 50) && (a[0] == null); i++) {
			inst.runOnMainSync(new Runnable() {
				public void run() {
					Collection<Activity> c =
						ActivityLifecycleMonitorRegistry
						.getInstance()
						.getActivitiesInStage(Stage.RESUMED);
					if (!c.isEmpty()) {
						a[0] = c.iterator().next();
					}
				} });
			if (a[0] == null) {
				SystemClock.sleep(100);
			}
		}
		return a[0];
	}

	/* the editor's "measure notes": page 1 */
	private TunerTime open() {
		final PresetEditActivity ed = (PresetEditActivity)resumed();
		main(new Runnable() {
			public void run() {
				ed.do_measure(null);
			} });
		SystemClock.sleep(500);
		Activity a = resumed();
		assertTrue("measure pages: " + a, a instanceof TunerTime);
		return (TunerTime)a;
	}

	private void click(final Activity a, final int id) {
		main(new Runnable() {
			public void run() {
				a.findViewById(id).performClick();
			} });
	}

	private void back(final Activity a) {
		main(new Runnable() {
			public void run() {
				a.onBackPressed();
			} });
		SystemClock.sleep(500);
	}

	private String title(final Activity a) {
		final String[] s = new String[1];
		main(new Runnable() {
			public void run() {
				s[0] = a.getTitle().toString();
			} });
		return s[0];
	}

	private static int offset(int pc) {
		return measured.offsets(preset.find(EDITED).notes)[pc];
	}

	/* everything of the user's as it was, saved and in parms */
	private void user_state_kept() {
		assertEquals(4415, prefs.getInt("A_freq", 0));
		assertEquals(D4, prefs.getInt("note_cents10", 0));
		assertEquals(new HashSet<String>(Arrays.asList("8", "16")),
			prefs.getStringSet("organ_footages", null));
		assertEquals(2.0f, prefs.getFloat("note_focus", 0), 0.0f);
		assertEquals(4.0f, prefs.getFloat("mixture_focus", 0), 0.0f);
		assertEquals(2.0f, prefs.getFloat("focus_ratio", 0), 0.0f);
		assertTrue(prefs.getBoolean("auto_note", false));
		assertEquals(EDITED, prefs.getInt("preset_id", -1));
		/* and read back by the editor's parms.load() */
		assertEquals(4415, parms.A_freq);
		assertEquals(D4, parms.note_cents);
		assertEquals(2, parms.num_strobes);
		assertEquals(8, parms.harm_mult[0]);
		assertEquals(16, parms.harm_mult[1]);
		assertEquals(EDITED, parms.preset_id);
		assertTrue(parms.auto_note);
	}

	@Test
	public void twelve_notes() {
		final TunerTime a = open();
		assertEquals(a.getString(R.string.title_measure_a), title(a));
		assertEquals(6, parms.num_strobes);
		int[] rows = { 4, 8, 16, 32, 64, 128 };
		for (int i = 0; i < rows.length; i++) {
			assertEquals(rows[i], parms.harm_mult[i]);
		}
		assertEquals(2400*U, parms.note_cents);		/* 8' A */
		assertEquals(2400*U, parms.strobe_cents);
		assertTrue(!parms.auto_note);
		assertEquals(4415, parms.A_freq);	/* the user's to start */
		main(new Runnable() {
			public void run() {
				a.A_incr1(null);
			} });
		assertEquals(4425, parms.A_freq);
		click(a, R.id.measure_action);			/* set */
		assertEquals(a.getString(R.string.title_measure_note, "by ear"),
			     title(a));
		/* C of the 8' octave with Werckmeister's C, no temperament
		 * on top */
		int[] w = measured.offsets(werck);
		assertEquals(1500*U + w[0], parms.note_cents);
		assertEquals(parms.note_cents, parms.strobe_cents);
		/* each note +0.3 c, the A +0.8 c: at the end every note
		 * 0.8 c lower, A at 0 */
		for (int n = 0; n < 12; n++) {
			int pc = n;
			int taps = (pc == 9) ? 8 : 3;
			for (int i = 0; i < taps; i++) {
				click(a, R.id.measure_cent_plus01);
			}
			assertEquals(1500*U + pc*100*U + w[pc] + taps,
				     parms.note_cents);
			assertEquals(4425, parms.A_freq);
			click(a, R.id.measure_action);		/* store */
		}
		SystemClock.sleep(500);
		Activity ed = resumed();
		assertTrue("back at the editor: " + ed,
			   ed instanceof PresetEditActivity);
		List<Integer> got = preset.find(EDITED).notes;
		assertEquals(12, got.size());
		assertEquals(4800*U, (int)got.get(9));
		for (int pc = 0; pc < 12; pc++) {
			assertEquals("pc " + pc,
				w[pc] + ((pc == 9) ? 0 : 3 - 8), offset(pc));
		}
		/* matching offered: its button selected */
		assertTrue(ed.findViewById(R.id.matching).isSelected());
		assertEquals(csv(werck), prefs.getString(
				"preset_" + OTHER + "_notes10", ""));
		user_state_kept();
	}

	/* Back: page 2 to page 1, then to the editor; what was stored stays,
	 * not shifted; the pages survive being recreated (rotation) */
	@Test
	public void back_keeps_what_was_stored() {
		final TunerTime a = open();
		click(a, R.id.measure_action);			/* set */
		click(a, R.id.measure_cent_plus1);		/* C +1 */
		click(a, R.id.measure_action);			/* store C */
		click(a, R.id.measure_semi_plus);		/* Cis -> D */
		click(a, R.id.measure_cent_minus1);
		click(a, R.id.measure_cent_minus1);		/* D -2 */
		main(new Runnable() {
			public void run() {
				a.recreate();
			} });
		SystemClock.sleep(1000);
		final TunerTime b = (TunerTime)resumed();
		int[] w = measured.offsets(werck);
		assertEquals(b.getString(R.string.title_measure_note, "by ear"),
			     title(b));
		assertEquals(1500*U + 200*U + w[2] - 20, parms.note_cents);
		assertEquals(6, parms.num_strobes);
		click(b, R.id.measure_action);			/* store D */
		back(b);					/* page 1 */
		assertEquals(b.getString(R.string.title_measure_a), title(b));
		assertEquals(2400*U, parms.note_cents);
		back(b);					/* editor */
		Activity ed = resumed();
		assertTrue("back at the editor: " + ed,
			   ed instanceof PresetEditActivity);
		assertEquals(w[0] + 10, offset(0));
		assertEquals(w[2] - 20, offset(2));
		assertEquals(w[4], offset(4));
		assertEquals(0, offset(9));
		assertTrue(!ed.findViewById(R.id.matching).isSelected());
		user_state_kept();
	}
}
