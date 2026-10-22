package org.galexander.tunertime;

import android.app.Activity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.Locale;

/* "Matching" from the preset editor (Onno's spec of 21 Oct): the preset
 * edited beside the nearest known temperaments (matching.rank()), one
 * row per note C..H, in cents from equal temperament with A = 0.  A
 * candidate's cell is yellow where it differs by more than 2 c, red by
 * more than 4 c; its header has the name and the RMS.  Note and three
 * columns fit 360 dp; a wider screen (landscape) shows more candidates.
 * The cells set their own text and background colours, readable in any
 * theme.  Opened for the preset parms.preset_id, only if it is a
 * temperament (PresetEditActivity enables the button) */
public class MatchActivity extends Activity {
	private static final int NOTE_DP = 44, COL_DP = 88, MAX_CANDS = 4;
	/* the marks: dark text on amber, light text on red */
	private static final int YELLOW_BG = 0xFFFFD54F, YELLOW_FG = 0xFF000000;
	private static final int RED_BG = 0xFFD32F2F, RED_FG = 0xFFFFFFFF;

	protected void attachBaseContext(android.content.Context base) {
		super.attachBaseContext(Lang.wrap(base));
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		parms.init(this);
		setTitle(R.string.title_match);
	}

	public void onResume() {
		parms.load();
		super.onResume();
		preset p = preset.find(parms.preset_id);
		int[] t = (p == null) ? null : matching.table(p.notes);
		if (t == null) {
			finish();	/* deleted, or no longer a temperament */
			return;
		}
		if (getActionBar() != null) {
			getActionBar().setSubtitle(p.name);
		}
		String[] names = getResources().getStringArray(
				R.array.temperament_names);
		if (names.length != temperaments.names.length) {
			names = temperaments.names;
		}
		List<matching.cand> r = matching.rank(t, p.id, names,
						      preset.presets);
		float dens = getResources().getDisplayMetrics().density;
		int w = (int)(getResources().getDisplayMetrics().widthPixels /
			      dens);
		int n = Math.max(2, Math.min(MAX_CANDS,
					     (w - NOTE_DP) / COL_DP - 1));
		n = Math.min(n, r.size());
		EventLog.ui("matching " + p.name + ": " + ((n > 0) ?
			String.format(Locale.US, "%s %.2f c", r.get(0).name,
				      r.get(0).rms) : "none"));
		setContentView(table(p.name, t, r.subList(0, n)));
	}

	private ScrollView table(String name, int[] t,
				 List<matching.cand> cands) {
		LinearLayout l = new LinearLayout(this);
		l.setOrientation(LinearLayout.VERTICAL);
		int pad = dp(8);
		l.setPadding(pad, pad, pad, pad);
		LinearLayout h = row();
		h.addView(cell("", false), note_lp());
		h.addView(header(name, null), col_lp());
		for (matching.cand c : cands) {
			h.addView(header(c.name, getString(R.string.match_rms,
				String.format(Locale.US, "%.1f",
					      c.rms))), col_lp());
		}
		l.addView(h);
		for (int pc = 0; pc < 12; pc++) {
			LinearLayout r = row();
			r.addView(cell(new note(3900*preset.UNITS_PER_CENT +
				pc*100*preset.UNITS_PER_CENT).note_str, true),
				note_lp());
			r.addView(cell(cents(t[pc]), true), col_lp());
			for (matching.cand c : cands) {
				TextView v = cell(cents(c.off[pc]), true);
				int lvl = matching.level(c.off[pc] - t[pc]);
				if (lvl > 0) {
					v.setBackgroundColor((lvl == 2) ?
						RED_BG : YELLOW_BG);
					v.setTextColor((lvl == 2) ?
						RED_FG : YELLOW_FG);
				}
				r.addView(v, col_lp());
			}
			l.addView(r);
		}
		ScrollView sv = new ScrollView(this);
		sv.setFitsSystemWindows(true);
		sv.addView(l);
		return sv;
	}

	/* "-13.7", with a point like the RMS and the rest of the app */
	private static String cents(int c) {
		int a = Math.abs(c);
		int u = preset.UNITS_PER_CENT;
		return ((c < 0) ? "-" : (c > 0) ? "+" : " ") + (a/u) + "." +
			(a%u);
	}

	private LinearLayout row() {
		LinearLayout r = new LinearLayout(this);
		r.setOrientation(LinearLayout.HORIZONTAL);
		return r;
	}
	private LinearLayout.LayoutParams note_lp() {
		return new LinearLayout.LayoutParams(dp(NOTE_DP),
			ViewGroup.LayoutParams.MATCH_PARENT);
	}
	private LinearLayout.LayoutParams col_lp() {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
			ViewGroup.LayoutParams.MATCH_PARENT, 1);
		lp.setMargins(dp(1), dp(1), dp(1), dp(1));
		return lp;
	}

	private TextView cell(String s, boolean mono) {
		TextView v = new TextView(this);
		v.setText(s);
		v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
		v.setTextColor(getResources().getColor(R.color.text));
		v.setGravity(Gravity.CENTER);
		v.setMinHeight(dp(36));
		if (mono) {
			v.setTypeface(android.graphics.Typeface.MONOSPACE);
		}
		return v;
	}

	/* a column's name, up to three lines, and its RMS */
	private TextView header(String name, String rms) {
		TextView v = new TextView(this);
		v.setText((rms == null) ? name : (name + "\n" + rms));
		v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		if (android.os.Build.VERSION.SDK_INT >= 23) {
			/* "Werck-meister" rather than "Werck meister" in a
			 * narrow column */
			v.setHyphenationFrequency(
				android.text.Layout.HYPHENATION_FREQUENCY_NORMAL);
		}
		v.setTextColor(getResources().getColor(R.color.text));
		v.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
		v.setPadding(dp(2), 0, dp(2), dp(4));
		return v;
	}

	private int dp(int d) {
		return (int)(d * getResources().getDisplayMetrics().density + 0.5f);
	}
}
