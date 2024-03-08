/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.viewmodel

import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import androidx.core.os.ParcelCompat
import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import androidx.databinding.ObservableArrayList
import androidx.databinding.ObservableList
import com.wireguard.android.BR
import com.wireguard.config.BadConfigException
import com.wireguard.config.Config
import com.wireguard.config.Peer

class ConfigProxy : BaseObservable, Parcelable {
    val `interface`: InterfaceProxy
    val peers: ObservableList<PeerProxy> = ObservableArrayList()

    @get:Bindable
    var enableAutoDisconnect = false
        set(value) {
            field = value
            notifyPropertyChanged(BR.enableAutoDisconnect)
        }

    @get:Bindable
    var autoDisconnectNetworks: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.autoDisconnectNetworks)
        }


    private constructor(parcel: Parcel) {
        `interface` = ParcelCompat.readParcelable(parcel, InterfaceProxy::class.java.classLoader, InterfaceProxy::class.java) ?: InterfaceProxy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ParcelCompat.readParcelableList(parcel, peers, PeerProxy::class.java.classLoader, PeerProxy::class.java)
        } else {
            parcel.readTypedList(peers, PeerProxy.CREATOR)
        }
        enableAutoDisconnect = ParcelCompat.readSerializable(parcel, Boolean::class.java.classLoader, Boolean::class.java) ?: false
        autoDisconnectNetworks = ParcelCompat.readSerializable(parcel, String::class.java.classLoader, String::class.java) ?: String()
        peers.forEach { it.bind(this) }
    }

    constructor(other: Config) {
        `interface` = InterfaceProxy(other.getInterface())
        other.peers.forEach {
            val proxy = PeerProxy(it)
            peers.add(proxy)
            proxy.bind(this)
        }
        enableAutoDisconnect = other.isAutoDisconnectEnabled
        autoDisconnectNetworks = other.autoDisconnectNetworks
    }

    constructor() {
        `interface` = InterfaceProxy()
    }

    fun addPeer(): PeerProxy {
        val proxy = PeerProxy()
        peers.add(proxy)
        proxy.bind(this)
        return proxy
    }

    override fun describeContents() = 0

    @Throws(BadConfigException::class)
    fun resolve(): Config {
        val resolvedPeers: MutableCollection<Peer> = ArrayList()
        peers.forEach { resolvedPeers.add(it.resolve()) }
        return Config.Builder()
            .setInterface(`interface`.resolve())
            .addPeers(resolvedPeers)
            .setAutoDisconnect(enableAutoDisconnect)
            .setAutoDisconnectNetworks(autoDisconnectNetworks)
            .build()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(`interface`, flags)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            dest.writeParcelableList(peers, flags)
        } else {
            dest.writeTypedList(peers)
        }
        dest.writeSerializable(enableAutoDisconnect)
        dest.writeSerializable(autoDisconnectNetworks)
    }

    private class ConfigProxyCreator : Parcelable.Creator<ConfigProxy> {
        override fun createFromParcel(parcel: Parcel): ConfigProxy {
            return ConfigProxy(parcel)
        }

        override fun newArray(size: Int): Array<ConfigProxy?> {
            return arrayOfNulls(size)
        }
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ConfigProxy> = ConfigProxyCreator()
    }
}
