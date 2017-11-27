package com.wireguard.android;

import android.app.Activity;
import android.databinding.DataBindingUtil;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;

import com.wireguard.android.databinding.NotSupportedActivityBinding;

public class NotSupportedActivity extends Activity {
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final NotSupportedActivityBinding binding =
                DataBindingUtil.setContentView(this, R.layout.not_supported_activity);
        final String messageHtml = getString(R.string.not_supported_message);
        final Spanned messageText;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            messageText = Html.fromHtml(messageHtml, Html.FROM_HTML_MODE_COMPACT);
        else
            messageText = Html.fromHtml(messageHtml);
        binding.notSupportedMessage.setText(messageText);
    }
}
