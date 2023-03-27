/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.fragment

import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.wireguard.android.R
import com.wireguard.android.util.resolveAttribute

class AddTunnelsSheet : BottomSheetDialogFragment() {

    private var behavior: BottomSheetBehavior<FrameLayout>? = null
    private val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onSlide(bottomSheet: View, slideOffset: Float) {
        }

        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                dismiss()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (savedInstanceState != null) dismiss()
        val view = inflater.inflate(R.layout.add_tunnels_bottom_sheet, container, false)
        if (activity?.packageManager?.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) != true) {
            val qrcode = view.findViewById<View>(R.id.create_from_qrcode)
            qrcode.isEnabled = false
            qrcode.visibility = View.GONE
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val dialog = dialog as BottomSheetDialog? ?: return
                behavior = dialog.behavior
                behavior?.apply {
                    state = BottomSheetBehavior.STATE_EXPANDED
                    peekHeight = 0
                    addBottomSheetCallback(bottomSheetCallback)
                }
                dialog.findViewById<View>(R.id.create_empty)?.setOnClickListener {
                    dismiss()
                    onRequestCreateConfig()
                }
                dialog.findViewById<View>(R.id.create_from_file)?.setOnClickListener {
                    dismiss()
                    onRequestImportConfig()
                }
                dialog.findViewById<View>(R.id.create_from_qrcode)?.setOnClickListener {
                    dismiss()
                    onRequestScanQRCode()
                }
            }
        })
        val gradientDrawable = GradientDrawable().apply {
            setColor(requireContext().resolveAttribute(com.google.android.material.R.attr.colorSurface))
        }
        view.background = gradientDrawable
    }

    override fun dismiss() {
        super.dismiss()
        behavior?.removeBottomSheetCallback(bottomSheetCallback)
    }

    private fun onRequestCreateConfig() {
        setFragmentResult(REQUEST_KEY_NEW_TUNNEL, bundleOf(REQUEST_METHOD to REQUEST_CREATE))
    }

    private fun onRequestImportConfig() {
        setFragmentResult(REQUEST_KEY_NEW_TUNNEL, bundleOf(REQUEST_METHOD to REQUEST_IMPORT))
    }

    private fun onRequestScanQRCode() {
        setFragmentResult(REQUEST_KEY_NEW_TUNNEL, bundleOf(REQUEST_METHOD to REQUEST_SCAN))
    }

    companion object {
        const val REQUEST_KEY_NEW_TUNNEL = "request_new_tunnel"
        const val REQUEST_METHOD = "request_method"
        const val REQUEST_CREATE = "request_create"
        const val REQUEST_IMPORT = "request_import"
        const val REQUEST_SCAN = "request_scan"
    }
}
