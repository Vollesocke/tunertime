package org.galexander.tunertime;

public class note {
	public int note_idx;
	public String note_str;	/* pitch class in the note-names setting:
				 * "F#", or German "Fis" */
	public int cents;		/* tenths of a cent (preset.UNITS_PER_CENT) */
	public String cents_str;
	public int octave;
	public String name_oct;	/* with the octave: "F#4", German "fis1"
				 * (superscript 1) */
	public String full_str;	/* "F#4 +01.5c" */

	private static final String NOTE_NAMES[] = new String[] { "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B" };
		/* German: B is English Bb, H is English B */
	private static final String GERMAN_NAMES[] = new String[] { "C", "Cis", "D", "Dis", "E", "F", "Fis", "G", "Gis", "A", "B", "H" };

	private static final int U = preset.UNITS_PER_CENT;

		/* cents_ is in tenths of a cent, 48000 = A4 */
	public note(int cents_) {
		int adj_cents = cents_+900*U;	/* based on C instead of A */

		/* floored, so that a note below C0 has a name too: 18.7
		 * crashed on a negative index here, from a mixture-mode
		 * estimate divided by a high focused row's ratio */
		int semi = floor_div(adj_cents+50*U, 100*U);
		note_idx = semi - 12*floor_div(semi, 12);

		cents = adj_cents+50*U - semi*100*U - 50*U;
		cents_str = cents_str(cents);

		octave = floor_div(semi, 12);

		note_str = class_name(note_idx);
		name_oct = german() ? german(note_idx, octave)
				    : (note_str + octave);
		full_str = name_oct + " " + cents_str;
	}

		/* a/b rounded down, b > 0 (Math.floorDiv needs API 24) */
	private static int floor_div(int a, int b) {
		return (a >= 0) ? a/b : -((b-1-a)/b);
	}

		/* the "German note names" setting */
	public static boolean german() {
		return parms.german_names;
	}

		/* name of pitch class idx (0 = C), without octave */
	public static String class_name(int idx) {
		return (german() ? GERMAN_NAMES : NOTE_NAMES)[idx];
	}

		/* superscript and subscript digits 0-9 */
	private static final String SUPER = "⁰¹²³⁴" +
					    "⁵⁶⁷⁸⁹";
	private static final String SUB = "₀₁₂₃₄" +
					  "₅₆₇₈₉";

		/* German notation as organ builders write it: capitals up to
		 * the great octave, with a subscript digit counting the
		 * octaves below it; lower case from the small octave up, with
		 * a superscript digit counting the octaves above it.  (_n is
		 * subscript n, ^n superscript n here.)
		 *   C0 = C_2 (Subkontra-C)    C1 = C_1 (Kontra-C, 32')
		 *   C2 = C   (grosses C, 8')  C3 = c   (kleines c)
		 *   C4 = c^1 (middle C)       C5 = c^2, ... C8 = c^5
		 *   A4 = a^1, A#4 = b^1, B3 = h, F#2 = Fis, C#1 = Cis_1 */
	public static String german(int idx, int oct) {
		return german(GERMAN_NAMES[idx], oct);
	}
		/* the same for the pitch class name s, e.g. "Es" */
	private static String german(String s, int oct) {
		if (oct <= 2) {
			return (oct == 2) ? s : (s + digits(2-oct, SUB));
		}
		s = s.toLowerCase();
		return (oct == 3) ? s : (s + digits(oct-3, SUPER));
	}
	private static String digits(int n, String set) {
		String d = Integer.toString(n);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < d.length(); i++) {
			sb.append(set.charAt(d.charAt(i) - '0'));
		}
		return sb.toString();
	}

		/* label for octave oct (scientific numbering): "4", or with
		 * German names the C of that octave, c^1 */
	public static String octave_name(int oct) {
		return german() ? german(0, oct) : Integer.toString(oct);
	}

		/* the strobe's corner label: "A49" (piano key 49 = A4), or with
		 * German names just the note, a^1 */
	public static String piano_name(int cents_) {
		note n = new note(cents_);
		if (german()) {
			return n.name_oct;
		}
		int key = (cents_+50*U)/(100*U)+1;
		return n.note_str + (((key < 1) || (key > 88)) ? "__"
							: Integer.toString(key));
	}

	/* Organ builders commonly write sharp notes without the German "is". */
	public String organ_name() {
		if (!german()) return name_oct;
		String[] organ = { "C", "Cs", "D", "Ds", "E", "F",
			"Fs", "G", "Gs", "A", "B", "H" };
		return german(organ[note_idx], octave);
	}

		/* chain of fifths (temperaments.CHAIN_LOW..CHAIN_HIGH), A = 0 */
	private static final String CHAIN_GERMAN[] = new String[] {
		"Fes", "Ces", "Ges", "Des", "As", "Es", "B",
		"F", "C", "G", "D", "A", "E", "H",
		"Fis", "Cis", "Gis", "Dis", "Ais", "Eis", "His" };
	private static final String CHAIN_ENGLISH[] = new String[] {
		"Fb", "Cb", "Gb", "Db", "Ab", "Eb", "Bb",
		"F", "C", "G", "D", "A", "E", "B",
		"F#", "C#", "G#", "D#", "A#", "E#", "B#" };

		/* name of chain position n (A = 0, Es = -6, Dis = +6), without
		 * octave: German "Es", English "Eb" */
	public static String chain_name(int n) {
		return (german() ? CHAIN_GERMAN : CHAIN_ENGLISH)
					[n - temperaments.CHAIN_LOW];
	}
		/* -1 for the flats (Fes..B), 0 for the naturals, 1 for the
		 * sharps (Fis..His) of chain position n */
	/* chain position n in English with the octave of this note's
	 * letter, "Eb4", whatever the language: for file names */
	public String spelled_english(int n) {
		int letter = octave*12 + note_idx - chain_accidental(n);
		int oct = ((letter + 1200) / 12) - 100;	/* floor */
		return CHAIN_ENGLISH[n - temperaments.CHAIN_LOW].replace('#', 's')
			+ oct;
	}
	public static int chain_accidental(int n) {
		return ((n + 4 + 14) / 7) - 2;
	}

		/* this note spelled as chain position n of its pitch class,
		 * with the octave: German (as organ builders write it) "dis^1"
		 * or "es^1", English "Eb4".  for more than 12 notes, where the
		 * organ names "Ds" would not tell Dis from Es (Onno).  the
		 * octave goes with the letter: His3 sounds as C4, Ces4 as B3 */
	public String spelled(int n) {
		int letter = octave*12 + note_idx - chain_accidental(n);
		int oct = ((letter + 1200) / 12) - 100;	/* floor */
		return german() ? german(chain_name(n), oct)
				: (chain_name(n) + oct);
	}

		/* translates note names in English text (C D E F G A B with an
		 * optional # or b, not part of a longer word, e.g. "wolf G#-Eb")
		 * to the German names ("wolf Gis-Es") if those are selected */
	public static String names_in(String s) {
		if (!german()) {
			return s;
		}
		StringBuilder sb = new StringBuilder();
		int n = s.length();
		for (int i = 0; i < n; i++) {
			char c = s.charAt(i);
			int idx = "C D EF G A B".indexOf(c);
			int len = 1;
			char acc = (i+1 < n) ? s.charAt(i+1) : ' ';
			if ((acc == '#') || (acc == 'b')) {
				len = 2;
			}
			if ((c == ' ') || (idx < 0) ||
			    ((i > 0) && Character.isLetter(s.charAt(i-1))) ||
			    ((i+len < n) && Character.isLetter(s.charAt(i+len)))) {
				sb.append(c);	/* not a note name */
				continue;
			}
			if (acc == 'b') {
				/* Ces Des Es Fes Ges As B */
				sb.append((c == 'E') ? "Es" : (c == 'A') ? "As" :
					(c == 'B') ? "B" : (c + "es"));
			} else {
				sb.append(GERMAN_NAMES[(idx+len-1)%12]);
			}
			i += len-1;
		}
		return sb.toString();
	}

		/* tenths of a cent -> "+03.5c" */
	public static String cents_str(int c) {
		int a = Math.abs(c);
		return ((c < 0) ? "-" : "+") + ((a < 10*U) ? "0" : "") +
			(a/U) + "." + (a%U) + "c";
	}

		/* tenths of a cent -> "3.5" (for editing) */
	public static String cents_edit_str(int c) {
		int a = Math.abs(c);
		return ((c < 0) ? "-" : "") + (a/U) + "." + (a%U);
	}
}
