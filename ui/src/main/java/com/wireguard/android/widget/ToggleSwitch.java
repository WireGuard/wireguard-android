/*
 * Copyright © 2013 The Android Open Source Project
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.widget;

import android.content.Context;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.widget.Switch;

public class ToggleSwitch extends Switch {
    private boolean isRestoringState;
    @Nullable private OnBeforeCheckedChangeListener listener;

    public ToggleSwitch(final Context context) {
        this(context, null);
    }

    @SuppressWarnings({"SameParameterValue", "WeakerAccess"})
    public ToggleSwitch(final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onRestoreInstanceState(final Parcelable state) {
        isRestoringState = true;
        super.onRestoreInstanceState(state);
        isRestoringState = false;
    }

    @Override
    public void setChecked(final boolean checked) {
        if (checked == isChecked())
            return;
        if (isRestoringState || listener == null) {
            super.setChecked(checked);
            return;
        }
        setEnabled(false);
        listener.onBeforeCheckedChanged(this, checked);
    }

    public void setCheckedInternal(final boolean checked) {
        super.setChecked(checked);
        setEnabled(true);
    }

    public void setOnBeforeCheckedChangeListener(final OnBeforeCheckedChangeListener listener) {
        this.listener = listener;
    }

    public interface OnBeforeCheckedChangeListener {
        void onBeforeCheckedChanged(ToggleSwitch toggleSwitch, boolean checked);
    }
}
