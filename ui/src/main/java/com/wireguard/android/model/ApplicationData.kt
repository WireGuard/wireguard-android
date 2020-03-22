/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.model

import android.graphics.drawable.Drawable
import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import com.wireguard.android.BR
import com.wireguard.util.Keyed

class ApplicationData(val icon: Drawable, val name: String, val packageName: String, isExcludedFromTunnel : Boolean) : BaseObservable(), Keyed<String> {
    override fun getKey(): String {
        return name
    }

    @get:Bindable
    var isExcludedFromTunnel = isExcludedFromTunnel
        set(value) {
            field = value
            notifyPropertyChanged(BR.excludedFromTunnel)
        }
}
