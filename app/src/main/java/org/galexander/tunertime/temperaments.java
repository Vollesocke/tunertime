package org.galexander.tunertime;

import java.util.ArrayList;
import java.util.List;

/* Well-known historical temperaments for keyboard instruments.  Each table
 * is the textbook deviation from equal temperament in cents for
 * C C# D Eb E F F# G G# A Bb B, with C = 0.  make_notes() shifts them so
 * A = 0, so the A-freq setting keeps meaning the A you hear. */
public class temperaments {
	/* roots of fifths in circle-of-fifths order: C-G, G-D, ... */
	public static final int[] fifth_roots = {
		0, 7, 2, 9, 4, 11, 6, 1, 8, 3, 10, 5
	};
	public static final String[] names = {
		"Equal temperament",
		"Pythagorean",
		"Meantone 1/4 comma",
		"Meantone 1/6 comma",
		"Werckmeister III",
		"Kirnberger III",
		"Vallotti (1754)",
		"Young II",
		"Kellner (Bach)",
		"Young-Sorge",
		"Near equal temperament",
		"Graef-Sorge",
		"Modified Vallotti",
		"Wegscheider / Reinfeld",
		"Johannes Lang (Freiburg)",
		"Thomas Rothert (Bayreuth)",
		"Frischknecht II",
		"Modified Neidhardt",
		"Neidhardt for a village (1732)",
		"Neidhardt for a small town (1732)",
		"Neidhardt for a large town (1724)",
		"Modified Werckmeister 1691 (Niederrodenbach)",
		"Werckmeister IV (Rohlf)",
		"Werckmeister V (Rohlf)",
		"Werckmeister III (Rohlf)",
	};

	public static final String[] about = {
		"all semitones equal; to switch back from a historical one",
		"pure fifths, wolf G#-Eb",
		"pure major thirds, wolf G#-Eb; Renaissance/early Baroque",
		"Baroque organs, mild wolf G#-Eb",
		"1691, well-tempered, all keys usable; Rohlf: Werckmeister 1691",
		"1779, pure C-E third",
		"1754; six fifths tempered by 1/6 Pythagorean comma",
		"1800, like Vallotti centred on C",
		"1977 reconstruction of Bach's tuning",
		"temperament by Young and Sorge",
		"near-equal historical temperament",
		"temperament by Graef and Sorge",
		"modified Vallotti temperament",
		"Wegscheider temperament for the Reinfeld organ",
		"Johannes Lang, Freiburg",
		"Thomas Rothert, Bayreuth",
		"Frischknecht II",
		"modified Neidhardt temperament",
		"Neidhardt for a village (1732)",
		"Neidhardt for a small town (1732)",
		"Neidhardt for a large town (1724)",
		"Werckmeister 1691 with A 1.9 c higher; Rohlf's Niederrodenbach chart",
		"Rohlf's numbering; usually called Werckmeister V",
		"Rohlf's numbering",
		"Rohlf's numbering; 1/3 comma, usually called Werckmeister IV",
	};

	private static final double[][] cents = {
		{ 0,   0,     0,     0,      0,     0,      0,     0,      0,      0,     0,      0    },
		{ 0,  13.69,  3.91, -9.78,   7.82, -1.96,  11.73,  1.96,  15.64,   5.87, -3.91,   9.78 },
		{ 0, -23.95, -6.84, 10.26, -13.69,  3.42, -20.53, -3.42, -27.37, -10.26,  6.84, -17.11 },
		{ 0, -11.40, -3.26,  4.89,  -6.52,  1.63,  -9.78, -1.63, -13.03,  -4.89,  3.26,  -8.15 },
		{ 0,  -9.78, -7.82, -5.87,  -9.78, -1.96, -11.73, -3.91,  -7.82, -11.73, -3.91,  -7.82 },
		{ 0,  -9.78, -6.84, -5.87, -13.69, -1.96,  -9.78, -3.42,  -7.82, -10.26, -3.91, -11.73 },
		{ 0,  -5.87, -3.91, -1.96,  -7.82,  1.96,  -7.82, -1.96,  -3.91,  -5.87,  0.00,  -9.78 },
		{ 0,  -9.78, -3.91, -5.87,  -7.82, -1.96, -11.73, -1.96,  -7.82,  -5.87, -3.91,  -9.78 },
		{ 0,  -9.78, -5.47, -5.87, -10.95, -1.96, -11.73, -2.74,  -7.82,  -8.21, -3.91,  -8.99 },
		/* Additional charts from Orgelbau Rohlf. Values below are
		 * deviations from equal temperament, converted from the page's
		 * chromatic absolute-cent tables. */
		{ 0, -9.8, -3.9, -5.9, -3.9, -2.0, -7.8, -2.0, -7.8, -5.9, -3.9, -5.9 },
		{ 0, -3.9, -1.0, -2.0, -2.0, -1.0, -2.9, -1.0, -2.9, -2.0, -1.0, -2.9 },
		{ 0, -5.9, -3.9, -5.9, -3.9, -2.0, -3.9, -2.0, -7.8, -5.9, -3.9, -2.0 },
		{ 0, -5.9, -3.9, -2.0, -3.9,  2.0, -3.9, -2.0, -3.9, -2.0,  0.0, -5.9 },
		{ 0, -5.9, -3.9, -2.0, -3.9,  2.0, -7.8, -2.0, -3.9, -2.0,  0.0, -5.9 },
		{ 0, -9.8, -3.9, -2.0, -3.9, -2.0, -7.8, -2.0, -5.9, -5.9,  0.0, -5.9 },
		{ 0, -6.8, -2.0, -2.9, -3.9,  1.0, -5.9, -1.0, -4.9, -2.9, -1.0, -4.9 },
		{ 0, -3.9, -2.0, -2.9, -3.9,  1.0, -5.9, -1.0, -4.9, -2.9, -1.0, -4.9 },
		{ 0, -2.0, -3.9, -2.0, -3.9,  2.0, -3.9, -2.0, -2.0, -3.9,  0.0, -3.9 },
		{ 0, -5.9, -2.0, -3.9, -9.8, -2.0, -7.8,  0.0, -5.9, -5.9, -2.0, -7.8 },
		{ 0, -5.9, -3.9, -3.9, -7.8, -2.0, -7.8, -2.0, -3.9, -5.9, -4.0, -7.8 },
		{ 0, -3.9, -3.9, -2.0, -5.9, -2.0, -3.9, -2.0, -3.9, -5.9, -2.0, -3.9 },
		{ 0, -9.8, -7.8, -5.9, -9.8, -2.0, -11.7, -3.9, -7.8, -9.8, -3.9, -7.8 },
		{ 0, -3.9,  3.9,  0.0, -3.9,  3.9,  0.0,  2.0, -7.8,  0.0,  2.0, -2.0 },
		{ 0, -9.8, -12.8, -2.5, -5.6, -2.0, -5.0, -1.4, -7.8, -7.5, -0.6, -3.6 },
		{ 0, -17.6, -3.9, -5.9, -7.8, -2.0, -11.7, -5.9, -15.6, -9.8, 3.9, -13.7 },
	};

	/* the 12 notes C4..B4 in preset units (tenths of a cent, 48000 = A4),
	 * normalised so A has no offset */
	public static List<Integer> make_notes(int idx) {
		double[] c = cents[idx];
		List<Integer> ret = new ArrayList<Integer>();
		int c4 = 3900*preset.UNITS_PER_CENT;
		for (int i = 0; i < 12; i++) {
			double off = c[i] - c[9];
			ret.add(c4 + 100*preset.UNITS_PER_CENT*i +
				(int)Math.round(off*preset.UNITS_PER_CENT));
		}
		return ret;
	}

	/* Re-voice a Pythagorean or meantone table so its wolf fifth is
	 * rooted at the requested pitch class.  This rotates the circle of
	 * fifths and keeps A at its original reference pitch. */
	public static List<Integer> with_wolf(int idx, int root) {
		List<Integer> base = make_notes(idx);
		int c4 = 3900*preset.UNITS_PER_CENT, semi = 100*preset.UNITS_PER_CENT;
		int[] offsets = new int[12];
		for (int i = 0; i < 12; i++) {
			offsets[i] = base.get(i) - (c4 + semi*i);
		}
		int shift = (8 - root + 12) % 12; /* templates put wolf at G# */
		int anchor = (9 + shift) % 12;
		List<Integer> ret = new ArrayList<Integer>();
		for (int i = 0; i < 12; i++) {
			int off = offsets[(i + shift) % 12] - offsets[anchor];
			ret.add(c4 + semi*i + off);
		}
		return ret;
	}
}
