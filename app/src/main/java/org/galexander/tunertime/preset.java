package org.galexander.tunertime;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class preset {
		/* notes are stored in tenths of a cent, 48000 = A4 at A_freq */
	public static final int UNITS_PER_CENT = 10;

	public int id;
	public String name;
	public List<Integer> notes;

	public static List<preset> presets = new ArrayList<preset>();
	private static int temporary_wolf_preset_id = -1;
	private static int temporary_wolf_root = -1;

	public static int temporary_wolf_root(int id) {
		return (id == temporary_wolf_preset_id) ? temporary_wolf_root : -1;
	}
	public static void set_temporary_wolf(int id, int root) {
		temporary_wolf_preset_id = id;
		temporary_wolf_root = root;
	}
	public static void clear_temporary_wolf() {
		temporary_wolf_preset_id = temporary_wolf_root = -1;
	}
	public static void clear_temporary_wolf_if_different(int id) {
		if ((temporary_wolf_preset_id != -1) &&
		    (temporary_wolf_preset_id != id)) {
			clear_temporary_wolf();
		}
	}

	/* Index in temperaments, or -1 if this preset is not the built-in
	 * Pythagorean or a meantone temperament.  Within a cent per note,
	 * so that presets saved in whole cents (before tenths) and their
	 * exports count as well */
	public int wolf_template() {
		for (int i = 1; i <= 3; i++) {
			List<Integer> t = temperaments.make_notes(i);
			if (notes.size() != t.size()) continue;
			boolean same = true;
			for (int j = 0; same && (j < t.size()); j++) {
				same = Math.abs(notes.get(j) - t.get(j)) <=
						UNITS_PER_CENT;
			}
			if (same) return i;
		}
		return -1;
	}

	public List<Integer> tuning_notes() {
		int root = temporary_wolf_root(id);
		int template = wolf_template();
		return ((root >= 0) && (template >= 1) && (template <= 3)) ?
			temperaments.with_wolf(template, root) : notes;
	}


	public void sort_notes() {
		Collections.sort(notes);
	}

	public static preset make() {
		preset ret = new preset();
		int highest_id = 0;
		for (preset p : presets) {
			if (p.id > highest_id) {
				highest_id = p.id;
			}
		}
		ret.id = highest_id+1;
		ret.name = "unnamed";
		ret.notes = new ArrayList<Integer>();
		presets.add(ret);
		return ret;
	}

	public static preset find(int id) {
		for (preset p : presets) {
			if (p.id == id) {
				return p;
			}
		}
		return null;
	}

		/* if this preset has exactly one note for each of the 12 pitch
		 * classes (a temperament), return the offset of each pitch class
		 * from equal temperament, in tenths of a cent, indexed with
		 * 0=A (like strobe_cents).  Otherwise null. */
	public int[] temper_offsets() {
		final int semi = 100*UNITS_PER_CENT;
		List<Integer> tuning = tuning_notes();
		if (tuning.size() != 12) {
			return null;
		}
		int[] ret = new int[12];
		boolean[] seen = new boolean[12];
		for (int v : tuning) {
			int t = v+semi/2;
			int et = ((t >= 0) ? t : (t-semi+1)) / semi;	/* floor */
			int pc = ((et%12)+12)%12;
			if (seen[pc]) {
				return null;
			}
			seen[pc] = true;
			ret[pc] = v - et*semi;	/* within -50c..+50c */
		}
		return ret;
	}

	public static void load(SharedPreferences p) {
		String ids_str = p.getString("presets", "");
		List<Integer> ids = csi_to_intarr(ids_str);
		presets.clear();
		for (int id : ids) {
			String name = p.getString("preset_"+id+"_name", null);
			String notes = p.getString("preset_"+id+"_notes10", null);
			int mult = 1;
			if (notes == null) {
				/* legacy: whole cents */
				notes = p.getString("preset_"+id+"_notes", null);
				mult = UNITS_PER_CENT;
			}
			if ((name != null) && (notes != null)) {
				preset n = new preset();
				n.id = id;
				n.name = name;
				n.notes = csi_to_intarr(notes);
				for (int i = 0; i < n.notes.size(); i++) {
					n.notes.set(i, n.notes.get(i)*mult);
				}
				presets.add(n);
			}
		}
	}

	public static void save(SharedPreferences.Editor e) {
		e.putString("presets", presetarr_to_csi(presets));
		for (preset p : presets) {
			e.putString("preset_"+p.id+"_name", p.name);
			e.putString("preset_"+p.id+"_notes10",
						intarr_to_csi(p.notes));
		}
	}

	private static List<Integer> csi_to_intarr(String s) {
		byte[] b = s.getBytes();
		int val = 0;
		boolean any_val = false;
		boolean neg = false;
		List<Integer> ret = new ArrayList<Integer>();
		for (int i = 0; i < b.length; i++) {
			byte c = b[i];
			if ((c == ',') && any_val) {
				ret.add(neg ? -val : val);
				val = 0;
				any_val = false;
				neg = false;
			} else if ((c == '-') && !any_val) {
				neg = true;
			} else if ((c >= '0') && (c <= '9')) {
				val = (val*10) + (c-'0');
				any_val = true;
			}
		}
		if (any_val) {
			ret.add(neg ? -val : val);
		}
		return ret;
	}
	private static String intarr_to_csi(List<Integer> arr) {
		String ret = "";
		String comma = "";
		for (int i : arr) {
			ret += comma + i;
			comma = ",";
		}
		return ret;
	}
	private static String presetarr_to_csi(List<preset> arr) {
		String ret = "";
		String comma = "";
		for (preset p : arr) {
			ret += comma + p.id;
			comma = ",";
		}
		return ret;
	}
}
