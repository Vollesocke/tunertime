package org.galexander.tunertime;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/* Meantone with more than 12 notes (HANDOFF "Since then"): notes picked
 * on the chain of fifths, pitch n fifths from A.  ./gradlew
 * testDevUnitTest --offline */
public class meantoneTest {
	private static final int U = preset.UNITS_PER_CENT;
	private static final int SEMI = 100*U;
	private preset p;

	/* bit mask of the chain positions from..to */
	private static int chain(int from, int to) {
		int m = 0;
		for (int n = from; n <= to; n++) {
			m |= 1 << (n - temperaments.CHAIN_LOW);
		}
		return m;
	}
	private static int bit(int n) {
		return 1 << (n - temperaments.CHAIN_LOW);
	}

	@Before
	public void setUp() {
		preset.presets.clear();
		preset.clear_temporary_wolf();
		p = preset.make();
		p.name = "Meantone 1/4 comma";
		p.notes.addAll(temperaments.make_notes(2));
		parms.preset_id = p.id;
		parms.note_alt = 0;
		parms.german_names = true;
	}
	@After
	public void tearDown() {
		preset.presets.clear();
		preset.clear_temporary_wolf();
		parms.preset_id = -1;
	}

	@Test
	public void fifths() {
		assertEquals(696.58, temperaments.fifth(2), 0.005);
		assertEquals(698.37, temperaments.fifth(3), 0.005);
		assertEquals(701.96, temperaments.fifth(1), 0.005);
	}

	/* the chain Es..Gis is the built-in table (wolf Gis-Es) */
	@Test
	public void default_wolf_is_the_template() {
		assertEquals(chain(-6, 5), temperaments.wolf_chain(8));
		int[][] t12 = p.temper_offsets();
		preset.set_temporary_chain(p.id, chain(-6, 5));
		int[][] tc = p.temper_offsets();
		for (int pc = 0; pc < 12; pc++) {
			assertEquals(1, tc[pc].length);
			assertEquals(t12[pc][0], tc[pc][0], 1);	/* rounding */
		}
		assertEquals(false, p.spelled());
		assertNull(p.temper_chain());
	}

	/* 1/4 comma, Es..Gis plus Dis and As: 14 notes */
	@Test
	public void dis_and_es() {
		preset.set_temporary_chain(p.id, chain(-7, 6));
		List<Integer> notes = p.tuning_notes();
		assertEquals(14, notes.size());
		int[][] t = p.temper_offsets();
		/* indexed 0 = A: Dis/Es is 6, Gis/As 11, C 3 */
		assertArrayEquals(new int[] { -205, 205 }, t[6]);   /* Dis below Es */
		assertArrayEquals(new int[] { -171, 240 }, t[11]);  /* Gis, As */
		assertArrayEquals(new int[] { 0 }, t[0]);           /* A */
		assertArrayEquals(new int[] { 103 }, t[3]);         /* C +10.3 */
		assertArrayEquals(new int[] { -34 }, t[7]);         /* E -3.4 */
		int[][] c = p.temper_chain();
		assertArrayEquals(new int[] { 6, -6 }, c[6]);
		assertArrayEquals(new int[] { 5, -7 }, c[11]);

		/* note mode: D# with note_alt 0 is Dis, 1 is Es */
		int ds4 = 4800*U - 6*SEMI;	/* D#4, untempered */
		assertEquals(2, parms.temper_alts(ds4));
		assertEquals(-205, parms.note_offset(ds4));
		assertEquals(-205, parms.temper_offset(ds4));	/* mixture: first */
		parms.note_alt = 1;
		assertEquals(205, parms.note_offset(ds4));
		assertEquals(-205, parms.temper_offset(ds4));

		/* follow: the nearest of all notes */
		assertArrayEquals(new int[] { 42, 1 },
				parms.nearest_note(ds4 + 180));
		assertArrayEquals(new int[] { 42, 0 },
				parms.nearest_note(ds4 - 150));
		assertArrayEquals(new int[] { 42, 1 },	/* Es, not E */
				parms.nearest_note(ds4 + 550));
		assertArrayEquals(new int[] { 43, 0 },
				parms.nearest_note(ds4 + 1000 - 34));

		/* names spelled from the chain */
		parms.note_cents = ds4;
		parms.note_alt = 0;
		assertEquals("dis\u00b9", new note(ds4).spelled(parms.note_chain()));
		parms.note_alt = 1;
		assertEquals("es\u00b9", new note(ds4).spelled(parms.note_chain()));
		parms.german_names = false;
		assertEquals("Eb4", new note(ds4).spelled(parms.note_chain()));
	}

	/* Pythagorean: Dis above Es */
	@Test
	public void pythagorean() {
		p.notes.clear();
		p.notes.addAll(temperaments.make_notes(1));
		preset.set_temporary_chain(p.id, chain(-6, 6));
		assertArrayEquals(new int[] { -117, 117 }, p.temper_offsets()[6]);
		assertArrayEquals(new int[] { -6, 6 }, p.temper_chain()[6]);
	}

	/* the octave goes with the letter */
	@Test
	public void his_and_ces() {
		preset.set_temporary_chain(p.id, chain(-10, 9) & ~bit(-3) &
					~bit(2));	/* His for C, Ces for H */
		parms.german_names = false;
		assertEquals("B#3", new note(3900*U).spelled(9));
		assertEquals("Cb4", new note(3800*U).spelled(-10));	/* B3 */
		parms.german_names = true;
		assertEquals("his", new note(3900*U).spelled(9));
	}

	/* a pitch class without a note: no temperament */
	@Test
	public void missing_pitch_class() {
		preset.set_temporary_chain(p.id, chain(-6, 4));	/* no Gis */
		assertNull(p.temper_offsets());
	}
}
