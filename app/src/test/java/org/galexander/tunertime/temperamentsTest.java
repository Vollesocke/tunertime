package org.galexander.tunertime;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/* The built-in tables against what they are said to be.  ./gradlew
 * testDevUnitTest --offline */
public class temperamentsTest {
	private static final int U = preset.UNITS_PER_CENT;

	/* names, about and the resource arrays go together */
	@Test
	public void same_length() {
		assertEquals(temperaments.names.length, temperaments.about.length);
	}

	/* Lambert (1774): F-C-G-D-A-E-B-F# 1/7 Pythagorean comma narrow,
	 * F#-C#-G#-D# and Eb-Bb-F pure; A = 0 */
	@Test
	public void lambert() {
		int t = Arrays.asList(temperaments.names).indexOf("Lambert (1774)");
		List<Integer> n = temperaments.make_notes(t);
		assertEquals(4800*U, (int)n.get(9));		/* A4 */
		double pure = 1200.0*Math.log(1.5)/Math.log(2.0);
		double pc = 1200.0*Math.log(531441.0/524288.0)/Math.log(2.0);
		/* pitch classes, 0 = C: each fifth from its lower note */
		int[] narrow = { 5, 0, 7, 2, 9, 4, 11 };	/* F C G D A E B */
		int[] fine = { 6, 1, 8, 3, 10 };		/* F# C# G# Eb Bb */
		for (int c : narrow) {
			assertEquals("fifth on " + c, pure - pc/7.0, fifth(n, c),
				     0.15);
		}
		for (int c : fine) {
			assertEquals("fifth on " + c, pure, fifth(n, c), 0.15);
		}
	}

	/* the fifth on pitch class c in cents, octaves folded */
	private static double fifth(List<Integer> n, int c) {
		int up = (c + 7) % 12;
		int d = n.get(up) - n.get(c);
		if (up < c) {
			d += 1200*U;
		}
		return d / (double)U;
	}
}
