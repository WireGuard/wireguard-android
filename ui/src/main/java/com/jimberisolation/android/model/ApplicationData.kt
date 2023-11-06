/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.jimberisolation.android.model

import android.graphics.drawable.Drawable
import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import com.jimberisolation.android.BR
import com.jimberisolation.android.databinding.Keyed

class ApplicationData(val icon: Drawable, val name: String, val packageName: String, isSelected: Boolean) : BaseObservable(), Keyed<String> {
    override val key = name

    @get:Bindable
    var isSelected = isSelected
        set(value) {
            field = value
            notifyPropertyChanged(BR.selected)
        }
}
