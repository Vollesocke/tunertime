package org.galexander.tunertime;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/* The measure pages' table (measured): notes stored one per pitch class,
 * the finished table shifted to A = 0.  ./gradlew testDevUnitTest
 * --offline */
public class measuredTest {
	private static final int U = preset.UNITS_PER_CENT, SEMI = 100*U;

	@Test
	public void pitch_class_and_offset() {
		assertEquals(9, measured.pitch_class(4800*U));		/* A4 */
		assertEquals(0, measured.pitch_class(3900*U - 20*U));	/* C4 -20c */
		assertEquals(11, measured.pitch_class(3900*U - 60*U));	/* B3 +40c */
		assertEquals(-20*U, measured.offset(3900*U - 20*U));
		assertEquals(40*U, measured.offset(3900*U - 60*U));
		assertEquals(0, measured.pitch_class(1500*U));		/* C2 */
		assertEquals(5, measured.offset(1500*U + 5));
	}

	/* a preset in any octave, with Dis and Es: the lowest of each */
	@Test
	public void offsets_from_preset() {
		List<Integer> n = new ArrayList<Integer>(Arrays.asList(
			5100*U + 12,		/* C5 +1.2 */
			4200*U - 137,		/* Eb4 -13.7 (Dis, lower) */
			4200*U + 103,		/* Eb4 +10.3 */
			4800*U));		/* A4 */
		int[] o = measured.offsets(n);
		assertEquals(12, o[0]);
		assertEquals(-137, o[3]);
		assertEquals(0, o[9]);
		assertEquals(0, o[1]);
	}

	/* storing replaces whatever the pitch class had, both Dis and Es */
	@Test
	public void store() {
		List<Integer> n = new ArrayList<Integer>(Arrays.asList(
			4200*U - 137, 4200*U + 103, 4800*U));
		measured.store(n, 3, -55);
		assertEquals(Arrays.asList(4200*U - 55, 4800*U), n);
		measured.store(n, 0, 60*U);	/* kept within its semitone */
		assertEquals(3900*U + measured.MAX_OFF, (int)n.get(0));
	}

	/* A stored +0.6 c: every note 0.6 c lower at the end */
	@Test
	public void a_to_zero() {
		List<Integer> n = new ArrayList<Integer>();
		int[] off = { 52, -64, -28, -25, -56, 14, -84, -14, -45, 6,
			      -6, -70 };
		for (int pc = 0; pc < 12; pc++) {
			measured.store(n, pc, off[pc]);
		}
		assertEquals(6, measured.a_to_zero(n));
		int[] got = measured.offsets(n);
		for (int pc = 0; pc < 12; pc++) {
			assertEquals("pc " + pc, off[pc] - 6, got[pc]);
		}
		assertEquals(4800*U, (int)n.get(9));
		assertEquals(0, measured.a_to_zero(n));	/* already */
	}
}
