package org.galexander.tunertime;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/* Help (menu): the user manual, bundled as HTML in assets/help, so that it
 * works in a church without network.  German or English by the app's
 * language (Lang): R.string.help_file names the page, values-de gives the
 * German one.  The pages are plain HTML and CSS, no scripts; their colours
 * follow prefers-color-scheme, which WebView takes from this screen's
 * theme (Tuner.Help: light, dark in values-night).  Links to the web
 * (Greg's site, the GitHub issues) open in the browser. */
public class HelpActivity extends Activity {
	private WebView web = null;

	protected void attachBaseContext(Context base) {
		super.attachBaseContext(Lang.wrap(base));
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		/* the manifest label is not translated before Android 13 */
		setTitle(R.string.menu_help);
		setContentView(R.layout.help);
		web = (WebView)findViewById(R.id.help_web);
		/* no flash of white in the dark theme while it loads */
		web.setBackgroundColor(Color.TRANSPARENT);
		WebSettings s = web.getSettings();
		s.setJavaScriptEnabled(false);
		s.setAllowContentAccess(false);
		s.setBuiltInZoomControls(true);	/* pinch zoom */
		s.setDisplayZoomControls(false);
		/* the font size set for the phone */
		s.setTextZoom(Math.round(100.0f *
			getResources().getConfiguration().fontScale));
		web.setWebViewClient(new WebViewClient() {
			public boolean shouldOverrideUrlLoading(WebView v,
								String url) {
				if (url.startsWith("file:///android_asset/")) {
					return false;	/* the manual itself */
				}
				try {
					startActivity(new Intent(Intent.ACTION_VIEW,
							Uri.parse(url)));
				} catch (ActivityNotFoundException e) {
					/* no browser: nothing happens */
				}
				return true;
			} });
		/* back where it was after the screen was recreated (dark
		 * theme turned on, say), unless the language changed */
		String file = getString(R.string.help_file);
		if ((savedInstanceState == null) ||
		    !file.equals(savedInstanceState.getString(FILE)) ||
		    (web.restoreState(savedInstanceState) == null)) {
			web.loadUrl("file:///android_asset/help/" + file);
		}
	}

	private static final String FILE = "help_file";

	protected void onSaveInstanceState(Bundle b) {
		super.onSaveInstanceState(b);
		if (web != null) {
			web.saveState(b);
			b.putString(FILE, getString(R.string.help_file));
		}
	}

	/* back goes back within the manual (a link followed), then out */
	public void onBackPressed() {
		if ((web != null) && web.canGoBack()) {
			web.goBack();
		} else {
			super.onBackPressed();
		}
	}
}
