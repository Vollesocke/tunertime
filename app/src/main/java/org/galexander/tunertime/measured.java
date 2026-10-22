package org.galexander.tunertime;

import java.util.Collections;
import java.util.List;

/* A temperament entered by ear on the measure pages (measure): one note
 * per pitch class, its offset from equal temperament against the A set
 * on page 1, stored in the preset as C4..B4 like the built-in tables
 * (temperaments.make_notes()).  Plain Java, for the unit tests */
public class measured {
	private static final int U = preset.UNITS_PER_CENT, SEMI = 100*U;
	public static final int C4 = 3900*U;
	/* an offset stays within its semitone: +-49.9 c */
	public static final int MAX_OFF = 50*U - 1;

	/* the equal-tempered semitone nearest note v (tenths of a cent,
	 * 48000 = A4, as preset.table()), in tenths of a cent */
	private static int et(int v) {
		int t = v + SEMI/2;
		return ((t >= 0) ? t/SEMI : -((SEMI-1-t)/SEMI)) * SEMI;
	}
	/* the pitch class of v, 0 = C */
	public static int pitch_class(int v) {
		int k = et(v)/SEMI;		/* 48 = A4 */
		return ((k + 9) % 12 + 12) % 12;
	}
	/* v's offset from its equal-tempered semitone */
	public static int offset(int v) {
		return v - et(v);
	}

	/* the preset's offset for each pitch class (0 = C): of its lowest
	 * note there, 0 where it has none.  Page 2 starts from these */
	public static int[] offsets(List<Integer> notes) {
		int[] ret = new int[12];
		for (int v : notes) {
			int pc = pitch_class(v);
			if (v == lowest(notes, pc)) {
				ret[pc] = offset(v);
			}
		}
		return ret;
	}
	private static int lowest(List<Integer> notes, int pc) {
		int lo = Integer.MAX_VALUE;
		for (int v : notes) {
			if ((pitch_class(v) == pc) && (v < lo)) {
				lo = v;
			}
		}
		return lo;
	}

	/* stores off for pitch class pc as its note C4..B4, in place of
	 * any the preset had there, both Dis and Es: only the 12 semitones
	 * are entered (Onno, 22 Oct) */
	public static void store(List<Integer> notes, int pc, int off) {
		for (int i = notes.size() - 1; i >= 0; i--) {
			if (pitch_class(notes.get(i)) == pc) {
				notes.remove(i);
			}
		}
		off = Math.max(-MAX_OFF, Math.min(MAX_OFF, off));
		notes.add(C4 + pc*SEMI + off);
		Collections.sort(notes);
	}

	/* the finished table to A = 0 (Onno, 22 Oct): every note minus A's
	 * offset, each kept within its semitone.  Returns the shift */
	public static int a_to_zero(List<Integer> notes) {
		int a = offsets(notes)[9];
		if (a == 0) {
			return 0;
		}
		for (int i = 0; i < notes.size(); i++) {
			int v = notes.get(i);
			int o = Math.max(-MAX_OFF, Math.min(MAX_OFF,
							    offset(v) - a));
			notes.set(i, et(v) + o);
		}
		Collections.sort(notes);
		return a;
	}
}
