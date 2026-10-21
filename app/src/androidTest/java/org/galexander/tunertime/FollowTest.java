package org.galexander.tunertime;

import android.Manifest;
import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/* Follow mode and corrections by hand (TunerTime.on_pitch,
 * hold_correction, semi_step, mixture_move) in 1/4 comma meantone with
 * Dis and Es, Gis and As picked in the workbench.  The estimates are fed
 * in on the main thread, as the strobe thread hands them over
 * (pitch_detected), several in a row so that they agree (DET_AGREE).
 * Run like UiLayoutTest:
 *   ./gradlew connectedUitestAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=org.galexander.tunertime.FollowTest */
@RunWith(AndroidJUnit4.class)
public class FollowTest {
	@Rule
	public GrantPermissionRule mic = GrantPermissionRule.grant(
				Manifest.permission.RECORD_AUDIO);

	private static final int U = preset.UNITS_PER_CENT, MEAN = 1;
	/* semitones, 48 = A4 */
	private static final int D4 = 41, DS4 = 42, E4 = 43;
	private Instrumentation inst;
	private ActivityScenario<TunerTime> scenario = null;
	private TunerTime a;

	@Before
	public void setup() {
		inst = InstrumentationRegistry.getInstrumentation();
		SharedPreferences.Editor e = PreferenceManager
			.getDefaultSharedPreferences(inst.getTargetContext())
			.edit();
		e.clear();
		e.putBoolean("fft_mode", false);	/* no legacy import */
		e.putBoolean("organ_mode", true);
		e.putBoolean("german_names", false);
		e.putString("presets", "" + MEAN);
		int t = java.util.Arrays.asList(temperaments.names)
				.indexOf("Meantone 1/4 comma");
		e.putString("preset_" + MEAN + "_name", temperaments.names[t]);
		StringBuilder sb = new StringBuilder();
		for (int n : temperaments.make_notes(t)) {
			sb.append((sb.length() > 0) ? "," : "").append(n);
		}
		e.putString("preset_" + MEAN + "_notes10", sb.toString());
		e.putInt("preset_id", MEAN);
		/* As Es .. Gis Dis on the chain of fifths (A = 0): 14 notes */
		int mask = 0;
		for (int n = -7; n <= 6; n++) {
			mask |= 1 << (n - temperaments.CHAIN_LOW);
		}
		e.putInt("temporary_preset", MEAN);
		e.putInt("temporary_chain", mask);
		e.putInt("note_cents10", D4*100*U);
		e.putBoolean("auto_note", true);	/* follow */
		e.commit();
		scenario = ActivityScenario.launch(TunerTime.class);
		scenario.onActivity(new ActivityScenario.ActivityAction<TunerTime>() {
			public void perform(TunerTime t) {
				a = t;
			} });
		inst.waitForIdleSync();
		assertTrue("Dis and Es", parms.temper_alts(DS4*100*U) == 2);
	}

	@After
	public void teardown() {
		if (scenario != null) {
			scenario.close();
		}
	}

	private void main(Runnable r) {
		inst.runOnMainSync(r);
	}

	/* the Hz of semitone k, note alt, in the temperament */
	private static double hz(int k, int alt) {
		int c = k*100*U + parms.temper_offset(k*100*U, alt);
		return parms.A_freq/10.0 * Math.pow(2.0,
				(c - 4800*U)/(1200.0*U));
	}

	/* a pipe heard: estimates in a row, at once, so that no estimate of
	 * the emulator's microphone comes between them */
	private void hear(final double f) {
		main(new Runnable() {
			public void run() {
				for (int i = 0; i < 6; i++) {
					a.pitch_detected(f);
				}
			} });
	}

	private void tap(final String what) {
		main(new Runnable() {
			public void run() {
				if (what.equals("semi+")) a.semi_incr(null);
				else if (what.equals("semi-")) a.semi_decr(null);
				else if (what.equals("mix+")) a.mixture_semi_incr(null);
				else if (what.equals("mix-")) a.mixture_semi_decr(null);
				else if (what.equals("mixture"))
					a.stretch_clicked(null);
			} });
	}

	private static int note_key() {
		return (parms.note_cents + 50*U) / (100*U);
	}
	private static int mix_key() {
		return (parms.mixture_cents + 50*U) / (100*U);
	}
	private String mix_text() {
		final String[] s = new String[1];
		main(new Runnable() {
			public void run() {
				s[0] = ((TextView)a.findViewById(
					R.id.mixture_display)).getText().toString();
			} });
		return s[0];
	}

	/* Es picked by hand where the detector hears Dis holds while that
	 * key sounds (it went back to Dis within half a second); the next
	 * key is followed */
	@Test
	public void note_alt_by_hand_holds() {
		hear(hz(DS4, 0));
		assertEquals(DS4, note_key());
		assertEquals(0, parms.note_alt);
		tap("semi+");				/* Dis -> Es */
		assertEquals(DS4, note_key());
		assertEquals(1, parms.note_alt);
		hear(hz(DS4, 0));
		assertEquals("Es by hand holds", 1, parms.note_alt);
		hear(hz(E4, 0));
		assertEquals(E4, note_key());
	}

	/* a semitone corrected by hand holds too, while the detector still
	 * hears the wrong one */
	@Test
	public void semitone_by_hand_holds() {
		hear(hz(E4, 0));
		assertEquals(E4, note_key());
		tap("semi-");				/* E -> Es */
		assertEquals(DS4, note_key());
		hear(hz(E4, 0));
		assertEquals("correction holds", DS4, note_key());
		hear(hz(D4, 0));
		assertEquals(D4, note_key());
	}

	/* the hold ends with the key's release: the next key may be the
	 * note the detector heard (a chromatic run, the pipe of Es heard as
	 * E, then the key E) */
	@Test
	public void hold_ends_with_release() {
		hear(hz(E4, 0));
		tap("semi-");				/* E -> Es */
		assertEquals(DS4, note_key());
		try {
			Thread.sleep(500);		/* > HOLD_QUIET */
		} catch (InterruptedException x) {
		}
		main(new Runnable() {
			public void run() {
				a.pitch_detected(0.0);	/* released */
			} });
		hear(hz(E4, 0));
		assertEquals("next key followed", E4, note_key());
	}

	/* mixture mode: key+ and key- go through both notes, D Dis Es E,
	 * named apart */
	@Test
	public void mixture_keys_both_notes() {
		tap("mixture");
		assertTrue(parms.mixture_mode);
		assertEquals(D4, mix_key());
		int[][] steps = { { DS4, 0 }, { DS4, 1 }, { E4, 0 } };
		String[] names = { "D#4", "Eb4", "E4" };
		for (int i = 0; i < steps.length; i++) {
			tap("mix+");
			assertEquals(steps[i][0], mix_key());
			assertEquals(steps[i][1], parms.note_alt);
			assertTrue(mix_text(), mix_text().startsWith(names[i]));
			/* on the note itself: Es is not Dis plus an offset */
			int c = steps[i][0]*100*U;
			assertEquals(c + parms.temper_offset(c, steps[i][1]),
				     parms.mixture_cents);
		}
		tap("mix-");
		assertEquals(DS4, mix_key());
		assertEquals(1, parms.note_alt);
		tap("mix-");
		assertEquals(0, parms.note_alt);
		tap("mix-");
		assertEquals(D4, mix_key());
	}

	/* mixture mode: a key set by hand is a correction as in note mode
	 * (Onno: "kept as the right note, the next one looked for within
	 * 500 cents of it"): it holds, and an octave heard next is folded
	 * to the key nearest it */
	@Test
	public void mixture_correction() {
		tap("mixture");
		hear(hz(E4, 0));
		assertEquals(E4, mix_key());
		tap("mix-");				/* E -> Es */
		assertEquals(DS4, mix_key());
		hear(hz(E4, 0));
		assertEquals("correction holds", DS4, mix_key());
		hear(2.0*hz(E4 + 1, 0));		/* F5, as an F4 rank */
		assertEquals("continuity", E4 + 1, mix_key());
	}

	/* mixture mode follows to Es (rejected before: 41 cents from Dis)
	 * and on to the next key, which an offset of 41 cents carried
	 * over would reject */
	@Test
	public void mixture_follows_es() {
		tap("mixture");
		assertEquals(D4, mix_key());
		hear(hz(DS4, 1));
		assertEquals(DS4, mix_key());
		assertEquals(1, parms.note_alt);
		assertTrue(mix_text(), mix_text().startsWith("Eb4 "));
		final String[] heard = new String[1];
		main(new Runnable() {
			public void run() {
				heard[0] = ((TextView)a.findViewById(
					R.id.mixture_pitch)).getText().toString();
			} });
		assertEquals("heard", "Eb4", heard[0]);
		hear(hz(E4, 0));
		assertEquals(E4, mix_key());
		assertEquals(0, parms.note_alt);
	}
}
