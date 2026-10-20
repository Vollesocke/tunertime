package org.galexander.tunertime;

import android.content.Context;
import android.preference.MultiSelectListPreference;
import android.util.AttributeSet;

/* The strobe long-press shortcut opens this existing preference's dialog. */
public class DirectMultiSelectListPreference
			extends MultiSelectListPreference {
	private Runnable on_dialog_closed;
	public DirectMultiSelectListPreference(Context context,
					       AttributeSet attrs) {
		super(context, attrs);
	}

	public void openDialog() {
		super.onClick();
	}

	public void setOnDialogClosed(Runnable callback) {
		on_dialog_closed = callback;
	}

	@Override
	protected void onDialogClosed(boolean positiveResult) {
		super.onDialogClosed(positiveResult);
		if (on_dialog_closed != null) on_dialog_closed.run();
	}
}
