package org.galexander.tunertime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/* Matching (matching): the ranking by RMS at A = 0, the user's presets,
 * the marks.  ./gradlew testDevUnitTest --offline */
public class matchingTest {
	private static final int U = preset.UNITS_PER_CENT;

	private static int idx(String name) {
		return Arrays.asList(temperaments.names).indexOf(name);
	}

	private static preset p(int id, String name, List<Integer> notes) {
		preset r = new preset();
		r.id = id;
		r.name = name;
		r.notes = new ArrayList<Integer>(notes);
		return r;
	}

	/* Lambert measured by ear: each note up to 0.3 c off, the A pipes
	 * set 0.5 c high on page 2.  Stored as on the measure pages, then
	 * shifted to A = 0 */
	private static List<Integer> lambert_by_ear() {
		int[] lam = matching.table(temperaments.make_notes(
				idx("Lambert (1774)")));
		int[] err = { 3, -2, 1, -3, 2, 0, -1, 3, -2, 5, 1, -3 };
		List<Integer> n = new ArrayList<Integer>();
		for (int pc = 0; pc < 12; pc++) {
			measured.store(n, pc, lam[pc] + err[pc]);
		}
		measured.a_to_zero(n);
		return n;
	}

	@Test
	public void lambert_first() {
		List<Integer> by_ear = lambert_by_ear();
		int[] t = matching.table(by_ear);
		List<preset> mine = new ArrayList<preset>();
		mine.add(p(7, "by ear", by_ear));	/* the one matched */
		List<matching.cand> r = matching.rank(t, 7,
				temperaments.names, mine);
		for (int i = 0; i < 4; i++) {
			System.out.printf("%d. %s  RMS %.2f c%n", i + 1,
				r.get(i).name, r.get(i).rms);
		}
		assertEquals("Lambert (1774)", r.get(0).name);
		/* A's 0.5 c goes to the 11 others in the shift: 0.53 c,
		 * then Young II 1.05 c, Thomas Rothert 1.08 c */
		assertTrue(r.get(0).rms < 0.6);
		assertTrue(r.get(1).rms > r.get(0).rms);
		for (matching.cand c : r) {
			assertTrue("not itself", c.preset_id != 7);
		}
		assertEquals(temperaments.names.length, r.size());
	}

	/* A = 0, no free offset: a table shifted as a whole is not the same */
	@Test
	public void a_is_zero() {
		List<Integer> w = temperaments.make_notes(
				idx("Werckmeister III"));
		List<Integer> up = new ArrayList<Integer>();
		for (int v : w) {
			up.add(v + 30);		/* all 3 c high, A too */
		}
		/* table() takes A to 0: the same as Werckmeister III */
		int[] t = matching.table(up);
		assertEquals(0, t[9]);
		List<matching.cand> r = matching.rank(t, -1,
				temperaments.names, new ArrayList<preset>());
		assertEquals("Werckmeister III", r.get(0).name);
		assertEquals(0.0, r.get(0).rms, 1e-9);
		/* without A shifted: 3 c on 11 of 12 notes, RMS 2.87 c */
		int[] raw = new int[12];
		for (int pc = 0; pc < 12; pc++) {
			raw[pc] = t[pc] + ((pc == 9) ? 0 : 30);
		}
		assertEquals(3.0*Math.sqrt(11.0/12.0),
			matching.rms(raw, r.get(0).off), 1e-9);
	}

	/* the user's presets: temperaments only, not the one matched, not a
	 * copy of a built-in one; nearer ones first */
	@Test
	public void user_presets() {
		int k = idx("Kirnberger III");
		List<Integer> kirn = temperaments.make_notes(k);
		List<Integer> mine = new ArrayList<Integer>(kirn);
		mine.set(0, mine.get(0) + 5);		/* C 0.5 c higher */
		List<preset> ps = new ArrayList<preset>();
		ps.add(p(1, "Kirnberger III", kirn));		/* a copy */
		ps.add(p(2, "St. Marien", mine));
		ps.add(p(3, "five notes", kirn.subList(0, 5)));
		ps.add(p(4, "matched", mine));
		List<matching.cand> r = matching.rank(matching.table(mine), 4,
				temperaments.names, ps);
		assertEquals("St. Marien", r.get(0).name);
		assertEquals(0.0, r.get(0).rms, 1e-9);
		assertEquals(2, r.get(0).preset_id);
		assertEquals("Kirnberger III", r.get(1).name);
		assertEquals(k, r.get(1).builtin);
		assertEquals(temperaments.names.length + 1, r.size());
		assertNull(matching.table(kirn.subList(0, 11)));
	}

	@Test
	public void marks() {
		assertEquals(0, matching.level(0));
		assertEquals(0, matching.level(20));	/* 2.0 c: not over */
		assertEquals(1, matching.level(21));
		assertEquals(1, matching.level(-40));
		assertEquals(2, matching.level(41));
		assertEquals(2, matching.level(-41));
	}
}
