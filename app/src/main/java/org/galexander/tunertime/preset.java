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

	/* The workbench's temporary tuning of a Pythagorean or meantone
	 * preset (wolf_template()), until another temperament is selected:
	 * either its wolf fifth placed elsewhere (root, pitch class 0 = C)
	 * or a set of notes on the chain of fifths (bit n -
	 * temperaments.CHAIN_LOW for position n), which can have more
	 * than 12 notes.  Kept in the preferences (load(), save()) */
	private static int temporary_wolf_preset_id = -1;
	private static int temporary_wolf_root = -1;
	private static int temporary_chain = 0;

	public static int temporary_wolf_root(int id) {
		return (id == temporary_wolf_preset_id) ? temporary_wolf_root : -1;
	}
	public static int temporary_chain(int id) {
		return (id == temporary_wolf_preset_id) ? temporary_chain : 0;
	}
	public static void set_temporary_wolf(int id, int root) {
		temporary_wolf_preset_id = id;
		temporary_wolf_root = root;
		temporary_chain = 0;
	}
	public static void set_temporary_chain(int id, int mask) {
		temporary_wolf_preset_id = id;
		temporary_wolf_root = -1;
		temporary_chain = mask;
	}
	public static void clear_temporary_wolf() {
		temporary_wolf_preset_id = temporary_wolf_root = -1;
		temporary_chain = 0;
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
		List<Integer> chain = tuning_chain();
		if (chain != null) {
			int template = wolf_template();
			List<Integer> ret = new ArrayList<Integer>();
			for (int n : chain) {
				ret.add(temperaments.chain_note(template, n));
			}
			return ret;
		}
		int root = temporary_wolf_root(id);
		int template = wolf_template();
		return ((root >= 0) && (template >= 1) && (template <= 3)) ?
			temperaments.with_wolf(template, root) : notes;
	}

	/* the chain positions of the notes picked in the workbench, in the
	 * order of tuning_notes() (by pitch: in meantone Dis below Es), or
	 * null if notes were not picked */
	public List<Integer> tuning_chain() {
		int mask = temporary_chain(id);
		int template = (mask != 0) ? wolf_template() : -1;
		if (template < 1) {
			return null;
		}
		List<Integer> ret = new ArrayList<Integer>();
		for (int n = temperaments.CHAIN_LOW;
		     n <= temperaments.CHAIN_HIGH; n++) {
			if ((mask & (1 << (n - temperaments.CHAIN_LOW))) == 0) {
				continue;
			}
			int v = temperaments.chain_note(template, n);
			int i = 0;
			while ((i < ret.size()) &&
			       (temperaments.chain_note(template, ret.get(i)) < v)) {
				i++;
			}
			ret.add(i, n);
		}
		return ret;
	}

	/* the names are spelled from the chain of fifths (German "Dis" and
	 * "Es", not "Ds") when more than 12 notes were picked (Onno) */
	public boolean spelled() {
		List<Integer> chain = tuning_chain();
		return (chain != null) && (chain.size() > 12);
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

		/* if this preset is a temperament, the offset of each pitch class
		 * from equal temperament, in tenths of a cent, indexed with
		 * 0=A (like strobe_cents): ret[pc] has one offset, or two
		 * (lower first, e.g. Dis and Es) where notes were picked in the
		 * workbench (tuning_chain()).  A temperament has exactly one
		 * note for each of the 12 pitch classes, or the picked notes
		 * cover each pitch class once or twice.  Otherwise null. */
	public int[][] temper_offsets() {
		return table(false);
	}
		/* like temper_offsets(), the chain positions of the notes if
		 * the names are spelled from them (spelled()), else null */
	public int[][] temper_chain() {
		return spelled() ? table(true) : null;
	}
	private int[][] table(boolean chain_positions) {
		final int semi = 100*UNITS_PER_CENT;
		List<Integer> tuning = tuning_notes();
		List<Integer> chain = tuning_chain();
		if ((chain == null) && (tuning.size() != 12)) {
			return null;
		}
		int[][] ret = new int[12][];
		for (int i = 0; i < tuning.size(); i++) {
			int v = tuning.get(i);
			int t = v+semi/2;
			int et = ((t >= 0) ? t : (t-semi+1)) / semi;	/* floor */
			int pc = ((et%12)+12)%12;
			int[] old = ret[pc];
			if ((old != null) && ((chain == null) || (old.length >= 2))) {
				return null;
			}
			int val = chain_positions ? chain.get(i)
						  : (v - et*semi);  /* -50c..+50c */
			/* in pitch order, as tuning_notes() */
			ret[pc] = (old == null) ? new int[] { val }
						: new int[] { old[0], val };
		}
		for (int pc = 0; pc < 12; pc++) {
			if (ret[pc] == null) {
				return null;
			}
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
		temporary_wolf_preset_id = p.getInt("temporary_preset", -1);
		temporary_wolf_root = p.getInt("temporary_wolf", -1);
		temporary_chain = p.getInt("temporary_chain", 0);
	}

	public static void save(SharedPreferences.Editor e) {
		e.putString("presets", presetarr_to_csi(presets));
		for (preset p : presets) {
			e.putString("preset_"+p.id+"_name", p.name);
			e.putString("preset_"+p.id+"_notes10",
						intarr_to_csi(p.notes));
		}
		if (find(temporary_wolf_preset_id) == null) {
			/* deleted: not for a new preset that gets its id */
			clear_temporary_wolf();
		}
		e.putInt("temporary_preset", temporary_wolf_preset_id);
		e.putInt("temporary_wolf", temporary_wolf_root);
		e.putInt("temporary_chain", temporary_chain);
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
