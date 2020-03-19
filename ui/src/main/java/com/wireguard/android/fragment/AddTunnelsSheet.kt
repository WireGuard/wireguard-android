/*
 * Copyright © 2020 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.fragment

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.zxing.integration.android.IntentIntegrator
import com.wireguard.android.R
import com.wireguard.android.activity.TunnelCreatorActivity
import com.wireguard.android.util.resolveAttribute

class AddTunnelsSheet : BottomSheetDialogFragment() {

    private lateinit var behavior: BottomSheetBehavior<FrameLayout>
    private val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onSlide(bottomSheet: View, slideOffset: Float) {
        }

        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                dismiss()
            }
        }
    }

    override fun getTheme(): Int {
        return R.style.BottomSheetDialogTheme
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (savedInstanceState != null) dismiss()
        return inflater.inflate(R.layout.add_tunnels_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val dialog = dialog as BottomSheetDialog? ?: return
                behavior = dialog.behavior
                behavior.apply {
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
            setColor(requireContext().resolveAttribute(R.attr.colorBackground))
        }
        view.background = gradientDrawable
    }

    override fun dismiss() {
        super.dismiss()
        behavior.removeBottomSheetCallback(bottomSheetCallback)
    }

    private fun requireTargetFragment(): Fragment {
        return requireNotNull(targetFragment) { "A target fragment should always be set" }
    }

    private fun onRequestCreateConfig() {
        startActivity(Intent(activity, TunnelCreatorActivity::class.java))
    }

    private fun onRequestImportConfig() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        requireTargetFragment().startActivityForResult(intent, TunnelListFragment.REQUEST_IMPORT)
    }

    private fun onRequestScanQRCode() {
        val integrator = IntentIntegrator.forSupportFragment(requireTargetFragment()).apply {
            setOrientationLocked(false)
            setBeepEnabled(false)
            setPrompt(getString(R.string.qr_code_hint))
        }
        integrator.initiateScan(listOf(IntentIntegrator.QR_CODE))
    }
}
