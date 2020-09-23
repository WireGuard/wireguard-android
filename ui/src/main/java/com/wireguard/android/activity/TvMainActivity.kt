/*
 * Copyright © 2020 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.activity

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.forEach
import androidx.databinding.DataBindingUtil
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import androidx.lifecycle.lifecycleScope
import com.wireguard.android.Application
import com.wireguard.android.R
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.databinding.Keyed
import com.wireguard.android.databinding.ObservableKeyedArrayList
import com.wireguard.android.databinding.ObservableKeyedRecyclerViewAdapter
import com.wireguard.android.databinding.TvActivityBinding
import com.wireguard.android.databinding.TvFileListItemBinding
import com.wireguard.android.databinding.TvTunnelListItemBinding
import com.wireguard.android.model.ObservableTunnel
import com.wireguard.android.util.ErrorMessages
import com.wireguard.android.util.QuantityFormatter
import com.wireguard.android.util.TunnelImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class TvMainActivity : AppCompatActivity() {
    private var pendingTunnel: ObservableTunnel? = null
    private val permissionActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val tunnel = pendingTunnel
        if (tunnel != null)
            setTunnelStateWithPermissionsResult(tunnel)
        pendingTunnel = null
    }

    private fun setTunnelStateWithPermissionsResult(tunnel: ObservableTunnel) {
        lifecycleScope.launch {
            try {
                tunnel.setStateAsync(Tunnel.State.TOGGLE)
            } catch (e: Throwable) {
                val error = ErrorMessages[e]
                val message = getString(R.string.error_up, error)
                Toast.makeText(this@TvMainActivity, message, Toast.LENGTH_LONG).show()
                Log.e(TAG, message, e)
            }
            updateStats()
        }
    }

    private lateinit var binding: TvActivityBinding
    private val isDeleting = ObservableBoolean()
    private val files = ObservableKeyedArrayList<String, KeyedFile>()
    private val filesRoot = ObservableField("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = TvActivityBinding.inflate(layoutInflater)
        lifecycleScope.launch {
            binding.tunnels = Application.getTunnelManager().getTunnels()
            if (binding.tunnels?.isEmpty() == true)
                binding.importButton.requestFocus()
            else
                binding.tunnelList.requestFocus()
        }
        binding.isDeleting = isDeleting
        binding.files = files
        binding.filesRoot = filesRoot
        binding.tunnelRowConfigurationHandler = object : ObservableKeyedRecyclerViewAdapter.RowConfigurationHandler<TvTunnelListItemBinding, ObservableTunnel> {
            override fun onConfigureRow(binding: TvTunnelListItemBinding, item: ObservableTunnel, position: Int) {
                binding.isDeleting = isDeleting
                binding.isFocused = ObservableBoolean()
                binding.root.setOnFocusChangeListener { _, focused ->
                    binding.isFocused?.set(focused)
                }
                binding.root.setOnClickListener {
                    lifecycleScope.launch {
                        if (isDeleting.get()) {
                            try {
                                item.deleteAsync()
                                if (this@TvMainActivity.binding.tunnels?.isEmpty() != false)
                                    isDeleting.set(false)
                            } catch (e: Throwable) {
                                val error = ErrorMessages[e]
                                val message = getString(R.string.config_delete_error, error)
                                Toast.makeText(this@TvMainActivity, message, Toast.LENGTH_LONG).show()
                                Log.e(TAG, message, e)
                            }
                        } else {
                            if (Application.getBackend() is GoBackend) {
                                val intent = GoBackend.VpnService.prepare(binding.root.context)
                                if (intent != null) {
                                    pendingTunnel = item
                                    permissionActivityResultLauncher.launch(intent)
                                    return@launch
                                }
                            }
                            setTunnelStateWithPermissionsResult(item)
                        }
                    }
                }
            }
        }

        binding.filesRowConfigurationHandler = object : ObservableKeyedRecyclerViewAdapter.RowConfigurationHandler<TvFileListItemBinding, KeyedFile> {
            override fun onConfigureRow(binding: TvFileListItemBinding, item: KeyedFile, position: Int) {
                binding.root.setOnClickListener {
                    if (item.isDirectory)
                        navigateTo(item)
                    else {
                        val uri = Uri.fromFile(item.canonicalFile)
                        files.clear()
                        filesRoot.set("")
                        lifecycleScope.launch {
                            TunnelImporter.importTunnel(contentResolver, uri) {
                                Toast.makeText(this@TvMainActivity, it, Toast.LENGTH_LONG).show()
                            }
                        }
                        runOnUiThread {
                            this@TvMainActivity.binding.tunnelList.requestFocus()
                        }
                    }
                }
            }
        }

        binding.importButton.setOnClickListener {
            if (filesRoot.get()?.isEmpty() != false) {
                navigateTo(myComputerFile)
                runOnUiThread {
                    binding.filesList.requestFocus()
                }
            } else {
                files.clear()
                filesRoot.set("")
                runOnUiThread {
                    binding.tunnelList.requestFocus()
                }
            }
        }

        binding.deleteButton.setOnClickListener {
            isDeleting.set(!isDeleting.get())
            runOnUiThread {
                binding.tunnelList.requestFocus()
            }
        }
        binding.executePendingBindings()
        setContentView(binding.root)

        lifecycleScope.launch {
            while (true) {
                updateStats()
                delay(1000)
            }
        }
    }

    private var pendingNavigation: File? = null
    private val permissionRequestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        val to = pendingNavigation
        if (it && to != null)
            navigateTo(to)
        pendingNavigation = null
    }

    private suspend fun makeStorageRoots(): Collection<KeyedFile> = withContext(Dispatchers.IO) {
        val list = HashSet<KeyedFile>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val storageManager: StorageManager = getSystemService() ?: return@withContext list
            list.addAll(storageManager.storageVolumes.mapNotNull { volume ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    volume.directory?.let { KeyedFile(it.canonicalPath) }
                } else {
                    KeyedFile((StorageVolume::class.java.getMethod("getPathFile").invoke(volume) as File).canonicalPath)
                }
            })
        } else {
            @Suppress("DEPRECATION")
            list.add(KeyedFile(Environment.getExternalStorageDirectory().canonicalPath))
            try {
                File("/storage").listFiles()?.forEach {
                    if (!it.isDirectory) return@forEach
                    try {
                        if (Environment.isExternalStorageRemovable(it)) {
                            list.add(KeyedFile(it.canonicalPath))
                        }
                    } catch (_: Throwable) {
                    }
                }
            } catch (_: Throwable) {
            }
        }
        list
    }

    private val myComputerFile = File("")

    private fun navigateTo(directory: File) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingNavigation = directory
            permissionRequestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            return
        }

        lifecycleScope.launch {
            if (directory == myComputerFile) {
                val roots = makeStorageRoots()
                if (roots.count() == 1) {
                    navigateTo(roots.first())
                    return@launch
                }
                files.clear()
                files.addAll(roots)
                filesRoot.set(getString(R.string.tv_select_a_storage_drive))
                return@launch
            }

            val newFiles = withContext(Dispatchers.IO) {
                val newFiles = ArrayList<KeyedFile>()
                try {
                    val parent = KeyedFile(directory.canonicalPath + "/..")
                    if (directory.canonicalPath != "/" && parent.list() != null)
                        newFiles.add(parent)
                    val listing = directory.listFiles() ?: return@withContext null
                    listing.forEach {
                        if (it.extension == "conf" || it.extension == "zip" || it.isDirectory)
                            newFiles.add(KeyedFile(it.canonicalPath))
                    }
                    newFiles.sortWith { a, b ->
                        if (a.isDirectory && !b.isDirectory) -1
                        else if (!a.isDirectory && b.isDirectory) 1
                        else a.compareTo(b)
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, Log.getStackTraceString(e))
                }
                newFiles
            }
            if (newFiles?.isEmpty() != false)
                return@launch
            files.clear()
            files.addAll(newFiles)
            filesRoot.set(directory.canonicalPath)
        }
    }

    override fun onBackPressed() {
        when {
            isDeleting.get() -> {
                isDeleting.set(false)
                runOnUiThread {
                    binding.tunnelList.requestFocus()
                }
            }
            filesRoot.get()?.isNotEmpty() == true -> {
                files.clear()
                filesRoot.set("")
                runOnUiThread {
                    binding.tunnelList.requestFocus()
                }
            }
            else -> super.onBackPressed()
        }
    }

    private suspend fun updateStats() {
        binding.tunnelList.forEach { viewItem ->
            val listItem = DataBindingUtil.findBinding<TvTunnelListItemBinding>(viewItem)
                    ?: return@forEach
            try {
                val tunnel = listItem.item!!
                if (tunnel.state != Tunnel.State.UP || isDeleting.get()) {
                    throw Exception()
                }
                val statistics = tunnel.getStatisticsAsync()
                val rx = statistics.totalRx()
                val tx = statistics.totalTx()
                listItem.tunnelTransfer.text = getString(R.string.transfer_rx_tx, QuantityFormatter.formatBytes(rx), QuantityFormatter.formatBytes(tx))
                listItem.tunnelTransfer.visibility = View.VISIBLE
            } catch (_: Throwable) {
                listItem.tunnelTransfer.visibility = View.GONE
                listItem.tunnelTransfer.text = ""
            }
        }
    }

    class KeyedFile(pathname: String) : File(pathname), Keyed<String> {
        override val key: String
            get() = if (isDirectory) "$name/" else name
    }

    companion object {
        private const val TAG = "WireGuard/TvMainActivity"
    }
}
