/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.activity

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.forEach
import androidx.databinding.DataBindingUtil
import androidx.databinding.Observable
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
import com.wireguard.android.util.UserKnobs
import com.wireguard.android.util.applicationScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class TvMainActivity : AppCompatActivity() {
    private val tunnelFileImportResultLauncher = registerForActivityResult(object : ActivityResultContracts.OpenDocument() {
        override fun createIntent(context: Context, input: Array<String>): Intent {
            val intent = super.createIntent(context, input)

            /* AndroidTV now comes with stubs that do nothing but display a Toast less helpful than
             * what we can do, so detect this and throw an exception that we can catch later. */
            val activitiesToResolveIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            if (activitiesToResolveIntent.all {
                    val name = it.activityInfo.packageName
                    name.startsWith("com.google.android.tv.frameworkpackagestubs") || name.startsWith("com.android.tv.frameworkpackagestubs")
                }) {
                throw ActivityNotFoundException()
            }
            return intent
        }
    }) { data ->
        if (data == null) return@registerForActivityResult
        lifecycleScope.launch {
            TunnelImporter.importTunnel(contentResolver, data) {
                Toast.makeText(this@TvMainActivity, it, Toast.LENGTH_LONG).show()
            }
        }
    }
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
        if (AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_YES) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                applicationScope.launch {
                    UserKnobs.setDarkTheme(true)
                }
            }
        }
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
        val gridManager = binding.tunnelList.layoutManager as GridLayoutManager
        gridManager.spanSizeLookup = SlatedSpanSizeLookup(gridManager)
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
                    if (item.file.isDirectory)
                        navigateTo(item.file)
                    else {
                        val uri = Uri.fromFile(item.file)
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
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                if (filesRoot.get()?.isEmpty() != false) {
                    navigateTo(File("/"))
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
            } else {
                try {
                    tunnelFileImportResultLauncher.launch(arrayOf("*/*"))
                } catch (_: Throwable) {
                    MaterialAlertDialogBuilder(binding.root.context).setMessage(R.string.tv_no_file_picker).setCancelable(false)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("https://play.google.com/store/apps/details?id=com.cxinventor.file.explorer")
                                    setPackage("com.android.vending")
                                })
                            } catch (_: Throwable) {
                            }
                        }.show()
                }
            }
        }

        binding.deleteButton.setOnClickListener {
            isDeleting.set(!isDeleting.get())
            runOnUiThread {
                binding.tunnelList.requestFocus()
            }
        }

        val backPressedCallback = onBackPressedDispatcher.addCallback(this) { handleBackPressed() }
        val updateBackPressedCallback = object : Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                backPressedCallback.isEnabled = isDeleting.get() || filesRoot.get()?.isNotEmpty() == true
            }
        }
        isDeleting.addOnPropertyChangedCallback(updateBackPressedCallback)
        filesRoot.addOnPropertyChangedCallback(updateBackPressedCallback)
        backPressedCallback.isEnabled = false

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

    private var cachedRoots: Collection<KeyedFile>? = null

    private suspend fun makeStorageRoots(): Collection<KeyedFile> = withContext(Dispatchers.IO) {
        cachedRoots?.let { return@withContext it }
        val list = HashSet<KeyedFile>()
        val storageManager: StorageManager = getSystemService() ?: return@withContext list
        list.addAll(storageManager.storageVolumes.mapNotNull { volume ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                volume.directory?.let { KeyedFile(it, volume.getDescription(this@TvMainActivity)) }
            } else {
                KeyedFile((StorageVolume::class.java.getMethod("getPathFile").invoke(volume) as File), volume.getDescription(this@TvMainActivity))
            }
        })
        cachedRoots = list
        list
    }

    private fun isBelowCachedRoots(maybeChild: File): Boolean {
        val cachedRoots = cachedRoots ?: return true
        for (root in cachedRoots) {
            if (maybeChild.canonicalPath.startsWith(root.file.canonicalPath))
                return false
        }
        return true
    }

    private fun navigateTo(directory: File) {
        require(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingNavigation = directory
            permissionRequestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            return
        }

        lifecycleScope.launch {
            if (isBelowCachedRoots(directory)) {
                val roots = makeStorageRoots()
                if (roots.count() == 1) {
                    navigateTo(roots.first().file)
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
                    directory.parentFile?.let {
                        newFiles.add(KeyedFile(it, "../"))
                    }
                    val listing = directory.listFiles() ?: return@withContext null
                    listing.forEach {
                        if (it.extension == "conf" || it.extension == "zip" || it.isDirectory)
                            newFiles.add(KeyedFile(it))
                    }
                    newFiles.sortWith { a, b ->
                        if (a.file.isDirectory && !b.file.isDirectory) -1
                        else if (!a.file.isDirectory && b.file.isDirectory) 1
                        else a.file.compareTo(b.file)
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

    private fun handleBackPressed() {
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

    class KeyedFile(val file: File, private val forcedKey: String? = null) : Keyed<String> {
        override val key: String
            get() = forcedKey ?: if (file.isDirectory) "${file.name}/" else file.name
    }

    private class SlatedSpanSizeLookup(private val gridManager: GridLayoutManager) : SpanSizeLookup() {
        private val originalHeight = gridManager.spanCount
        private var newWidth = 0
        private lateinit var sizeMap: Array<IntArray?>

        private fun emptyUnderIndex(index: Int, size: Int): Int {
            sizeMap[size - 1]?.let { return it[index] }
            val sizes = IntArray(size)
            val oh = originalHeight
            val nw = newWidth
            var empties = 0
            for (i in 0 until size) {
                val ox = (i + empties) / oh
                val oy = (i + empties) % oh
                var empty = 0
                for (j in oy + 1 until oh) {
                    val ni = nw * j + ox
                    if (ni < size)
                        break
                    empty++
                }
                empties += empty
                sizes[i] = empty
            }
            sizeMap[size - 1] = sizes
            return sizes[index]
        }

        override fun getSpanSize(position: Int): Int {
            if (newWidth == 0) {
                val child = gridManager.getChildAt(0) ?: return 1
                if (child.width == 0) return 1
                newWidth = gridManager.width / child.width
                sizeMap = Array(originalHeight * newWidth - 1) { null }
            }
            val total = gridManager.itemCount
            if (total >= originalHeight * newWidth || total == 0)
                return 1
            return emptyUnderIndex(position, total) + 1
        }
    }

    companion object {
        private const val TAG = "WireGuard/TvMainActivity"
    }
}
