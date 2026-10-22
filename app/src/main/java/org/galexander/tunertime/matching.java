package org.galexander.tunertime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/* Matching a temperament against the known ones (Onno's spec of 21 Oct;
 * MatchActivity): all built-in tables (temperaments) and the user's
 * presets except the one matched, nearest first by the RMS of the 12
 * differences.  Both sides at A = 0 and no free common offset (Onno, 22
 * Oct: the A is set to 0 c by ear on the measure page, over the A pipes
 * of all octaves; a table whose A is not 0 is shifted first).  Plain
 * Java, for the unit tests */
public class matching {
	private static final int U = preset.UNITS_PER_CENT;
	/* a difference over these is marked: yellow over 2 c, red over 4 c
	 * (tenths of a cent) */
	public static final int YELLOW = 2*U, RED = 4*U;

	public static class cand {
		public final int builtin;	/* temperaments index, or -1 */
		public final int preset_id;	/* or -1 */
		public final String name;
		public final int[] off;		/* by pitch class, 0 = C */
		public double rms;		/* cents */
		cand(int builtin_, int preset_id_, String name_, int[] off_) {
			builtin = builtin_;
			preset_id = preset_id_;
			name = name_;
			off = off_;
		}
	}

	/* the offsets from equal temperament by pitch class (0 = C), in
	 * tenths of a cent, A = 0; null unless the notes are a temperament
	 * of exactly one note per pitch class */
	public static int[] table(List<Integer> notes) {
		if (notes.size() != 12) {
			return null;
		}
		int[] ret = new int[12];
		boolean[] seen = new boolean[12];
		for (int v : notes) {
			int pc = measured.pitch_class(v);
			if (seen[pc]) {
				return null;
			}
			seen[pc] = true;
			ret[pc] = measured.offset(v);
		}
		int a = ret[9];
		for (int pc = 0; pc < 12; pc++) {
			ret[pc] -= a;
		}
		return ret;
	}

	/* root mean square of the 12 differences, in cents */
	public static double rms(int[] a, int[] b) {
		double s = 0.0d;
		for (int pc = 0; pc < 12; pc++) {
			double d = (a[pc] - b[pc]) / (double)U;
			s += d*d;
		}
		return Math.sqrt(s / 12.0d);
	}

	/* 0 as it is, 1 yellow, 2 red, for a difference d (tenths of a
	 * cent) */
	public static int level(int d) {
		d = Math.abs(d);
		return (d > RED) ? 2 : (d > YELLOW) ? 1 : 0;
	}

	/* the candidates for target, nearest first: the built-in tables
	 * (named by names, the translated temperaments.names), then the
	 * presets that are temperaments, except skip_id and copies of a
	 * built-in one (within 0.1 c, as added from the temperament list) */
	public static List<cand> rank(int[] target, int skip_id,
				      String[] names, List<preset> presets) {
		List<cand> ret = new ArrayList<cand>();
		for (int i = 0; i < temperaments.names.length; i++) {
			ret.add(new cand(i, -1, names[i],
				table(temperaments.make_notes(i))));
		}
		int builtins = ret.size();
		for (preset p : presets) {
			int[] t = (p.id == skip_id) ? null : table(p.notes);
			if ((t == null) || copy(t, ret, builtins)) {
				continue;
			}
			ret.add(new cand(-1, p.id, p.name, t));
		}
		for (cand c : ret) {
			c.rms = rms(target, c.off);
		}
		/* stable: on a tie the built-in one first */
		Collections.sort(ret, new Comparator<cand>() {
			public int compare(cand x, cand y) {
				return Double.compare(x.rms, y.rms);
			} });
		return ret;
	}

	private static boolean copy(int[] t, List<cand> l, int n) {
		for (int i = 0; i < n; i++) {
			boolean same = true;
			for (int pc = 0; same && (pc < 12); pc++) {
				same = Math.abs(t[pc] - l.get(i).off[pc]) <= 1;
			}
			if (same) {
				return true;
			}
		}
		return false;
	}
}
