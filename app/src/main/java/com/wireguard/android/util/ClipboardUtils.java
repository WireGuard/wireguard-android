package com.wireguard.android.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.commonsware.cwac.crossport.design.widget.Snackbar;

/**
 * Standalone utilities for interacting with the system clipboard.
 */

public final class ClipboardUtils {
    private ClipboardUtils() {
    }

    public static void copyTextView(final View view) {
        if (!(view instanceof TextView))
            return;
        final CharSequence text = ((TextView) view).getText();
        if (text == null || text.length() == 0)
            return;
        final Object service = view.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (!(service instanceof ClipboardManager))
            return;
        final CharSequence description = view.getContentDescription();
        ((ClipboardManager) service).setPrimaryClip(ClipData.newPlainText(description, text));
        Snackbar.make(view, description + " copied to clipboard", Snackbar.LENGTH_LONG).show();
    }
}
