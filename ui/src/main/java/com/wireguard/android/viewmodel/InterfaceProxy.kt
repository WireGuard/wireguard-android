/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.viewmodel

import android.os.Parcel
import android.os.Parcelable
import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import androidx.databinding.ObservableArrayList
import androidx.databinding.ObservableList
import com.wireguard.android.BR
import com.wireguard.config.Attribute
import com.wireguard.config.BadConfigException
import com.wireguard.config.Interface
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyFormatException
import com.wireguard.crypto.KeyPair

class InterfaceProxy : BaseObservable, Parcelable {
    @get:Bindable
    val excludedApplications: ObservableList<String> = ObservableArrayList()

    @get:Bindable
    val includedApplications: ObservableList<String> = ObservableArrayList()

    @get:Bindable
    var addresses: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.addresses)
        }

    @get:Bindable
    var dnsServers: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.dnsServers)
        }

    @get:Bindable
    var listenPort: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.listenPort)
        }

    @get:Bindable
    var mtu: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.mtu)
        }

    @get:Bindable
    var privateKey: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.privateKey)
            notifyPropertyChanged(BR.publicKey)
        }

    @get:Bindable
    val publicKey: String
        get() = try {
            KeyPair(Key.fromBase64(privateKey)).publicKey.toBase64()
        } catch (ignored: KeyFormatException) {
            ""
        }

    private constructor(parcel: Parcel) {
        addresses = parcel.readString() ?: ""
        dnsServers = parcel.readString() ?: ""
        parcel.readStringList(excludedApplications)
        parcel.readStringList(includedApplications)
        listenPort = parcel.readString() ?: ""
        mtu = parcel.readString() ?: ""
        privateKey = parcel.readString() ?: ""
    }

    constructor(other: Interface) {
        addresses = Attribute.join(other.addresses)
        val dnsServerStrings = other.dnsServers.map { it.hostAddress }.plus(other.dnsSearchDomains)
        dnsServers = Attribute.join(dnsServerStrings)
        excludedApplications.addAll(other.excludedApplications)
        includedApplications.addAll(other.includedApplications)
        listenPort = other.listenPort.map { it.toString() }.orElse("")
        mtu = other.mtu.map { it.toString() }.orElse("")
        val keyPair = other.keyPair
        privateKey = keyPair.privateKey.toBase64()
    }

    constructor()

    override fun describeContents() = 0

    fun generateKeyPair() {
        val keyPair = KeyPair()
        privateKey = keyPair.privateKey.toBase64()
        notifyPropertyChanged(BR.privateKey)
        notifyPropertyChanged(BR.publicKey)
    }

    @Throws(BadConfigException::class)
    fun resolve(): Interface {
        val builder = Interface.Builder()
        if (addresses.isNotEmpty()) builder.parseAddresses(addresses)
        if (dnsServers.isNotEmpty()) builder.parseDnsServers(dnsServers)
        if (excludedApplications.isNotEmpty()) builder.excludeApplications(excludedApplications)
        if (includedApplications.isNotEmpty()) builder.includeApplications(includedApplications)
        if (listenPort.isNotEmpty()) builder.parseListenPort(listenPort)
        if (mtu.isNotEmpty()) builder.parseMtu(mtu)
        if (privateKey.isNotEmpty()) builder.parsePrivateKey(privateKey)
        return builder.build()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(addresses)
        dest.writeString(dnsServers)
        dest.writeStringList(excludedApplications)
        dest.writeStringList(includedApplications)
        dest.writeString(listenPort)
        dest.writeString(mtu)
        dest.writeString(privateKey)
    }

    private class InterfaceProxyCreator : Parcelable.Creator<InterfaceProxy> {
        override fun createFromParcel(parcel: Parcel): InterfaceProxy {
            return InterfaceProxy(parcel)
        }

        override fun newArray(size: Int): Array<InterfaceProxy?> {
            return arrayOfNulls(size)
        }
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<InterfaceProxy> = InterfaceProxyCreator()
    }
}
