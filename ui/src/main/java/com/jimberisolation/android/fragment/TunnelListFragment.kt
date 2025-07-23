/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.jimberisolation.android.fragment

import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.lifecycle.lifecycleScope
import com.jimberisolation.android.Application
import com.jimberisolation.android.R
import com.jimberisolation.android.activity.SignInActivity
import com.jimberisolation.android.databinding.ObservableKeyedRecyclerViewAdapter.RowConfigurationHandler
import com.jimberisolation.android.databinding.TunnelListFragmentBinding
import com.jimberisolation.android.databinding.TunnelListItemBinding
import com.jimberisolation.android.model.ObservableTunnel
import com.jimberisolation.android.util.ErrorMessages
import com.jimberisolation.android.widget.MultiselectableRelativeLayout
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/**
 * Fragment containing a list of known WireGuard tunnels. It allows creating and deleting tunnels.
 */
class TunnelListFragment : BaseFragment() {
    private val actionModeListener = ActionModeListener()
    private var actionMode: ActionMode? = null
    private var backPressedCallback: OnBackPressedCallback? = null
    private var binding: TunnelListFragmentBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState != null) {
            val checkedItems = savedInstanceState.getIntegerArrayList(CHECKED_ITEMS)
            if (checkedItems != null) {
                for (i in checkedItems) actionModeListener.setItemChecked(i, true)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = TunnelListFragmentBinding.inflate(inflater, container, false)

        backPressedCallback = requireActivity().onBackPressedDispatcher.addCallback(this) { actionMode?.finish() }
        backPressedCallback?.isEnabled = false

        return binding?.root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntegerArrayList(CHECKED_ITEMS, actionModeListener.getCheckedItems())
    }

    override fun onSelectedTunnelChanged(oldTunnel: ObservableTunnel?, newTunnel: ObservableTunnel?) {
        binding ?: return
        lifecycleScope.launch {
            val tunnels = Application.getTunnelManager().getTunnelsOfUser()
            if (newTunnel != null) viewForTunnel(newTunnel, tunnels)?.setSingleSelected(true)
            if (oldTunnel != null) viewForTunnel(oldTunnel, tunnels)?.setSingleSelected(false)
        }
    }

    private fun onTunnelDeletionFinished(count: Int, throwable: Throwable?) {
        val message: String
        val ctx = activity ?: Application.get()
        if (throwable == null) {
            message = ctx.resources.getQuantityString(R.plurals.delete_success, count, count)
        } else {
            val error = ErrorMessages[throwable]
            message = ctx.resources.getQuantityString(R.plurals.delete_error, count, count, error)
            Log.e(TAG, message, throwable)
        }
        showSnackbar(message)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        binding ?: return
        binding!!.fragment = this
        lifecycleScope.launch { binding!!.tunnels = Application.getTunnelManager().getTunnelsOfUser() }
        binding!!.rowConfigurationHandler = object : RowConfigurationHandler<TunnelListItemBinding, ObservableTunnel> {
            override fun onConfigureRow(binding: TunnelListItemBinding, item: ObservableTunnel, position: Int) {
                binding.fragment = this@TunnelListFragment
                binding.root.setOnClickListener {
                    if (actionMode == null) {
                        selectedTunnel = item
                    } else {
                        actionModeListener.toggleItemChecked(position)
                    }
                }
                binding.root.setOnLongClickListener {
                    actionModeListener.toggleItemChecked(position)
                    true
                }
                if (actionMode != null)
                    (binding.root as MultiselectableRelativeLayout).setMultiSelected(actionModeListener.checkedItems.contains(position))
                else
                    (binding.root as MultiselectableRelativeLayout).setSingleSelected(selectedTunnel == item)
            }
        }
    }

    private fun showSnackbar(message: CharSequence) {
        Toast.makeText(activity ?: Application.get(), message, Toast.LENGTH_SHORT).show()
    }

    private fun viewForTunnel(tunnel: ObservableTunnel, tunnels: List<*>): MultiselectableRelativeLayout? {
        return binding?.tunnelList?.findViewHolderForAdapterPosition(tunnels.indexOf(tunnel))?.itemView as? MultiselectableRelativeLayout
    }

    private inner class ActionModeListener : ActionMode.Callback {
        val checkedItems: MutableCollection<Int> = HashSet()
        private var resources: Resources? = null

        fun getCheckedItems(): ArrayList<Int> {
            return ArrayList(checkedItems)
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.menu_action_delete -> {
                    val activity = activity ?: return true
                    val copyCheckedItems = HashSet(checkedItems)

                    activity.lifecycleScope.launch {
                        try {
                            val tunnels = Application.getTunnelManager().getTunnelsOfUser()
                            val tunnelsToDelete = ArrayList<ObservableTunnel>()
                            for (position in copyCheckedItems) tunnelsToDelete.add(tunnels[position])
                            val futures = tunnelsToDelete.map { async(SupervisorJob()) { it.deleteAsync() } }
                            onTunnelDeletionFinished(futures.awaitAll().size, null)

                            val intent = Intent(activity, SignInActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(intent)
                            activity.finish()
                        } catch (e: Throwable) {
                            onTunnelDeletionFinished(0, e)
                        }

                    }
                    checkedItems.clear()
                    mode.finish()
                    true
                }

                R.id.menu_action_select_all -> {
                    lifecycleScope.launch {
                        val tunnels = Application.getTunnelManager().getTunnels()
                        for (i in 0 until tunnels.size) {
                            setItemChecked(i, true)
                        }
                    }
                    true
                }

                else -> false
            }
        }

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            actionMode = mode
            backPressedCallback?.isEnabled = true
            if (activity != null) {
                resources = activity!!.resources
            }
            mode.menuInflater.inflate(R.menu.tunnel_list_action_mode, menu)
            binding?.tunnelList?.adapter?.notifyDataSetChanged()
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            actionMode = null
            backPressedCallback?.isEnabled = false
            resources = null
            checkedItems.clear()
            binding?.tunnelList?.adapter?.notifyDataSetChanged()
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            updateTitle(mode)
            return false
        }

        fun setItemChecked(position: Int, checked: Boolean) {
            if (checked) {
                checkedItems.add(position)
            } else {
                checkedItems.remove(position)
            }
            val adapter = if (binding == null) null else binding!!.tunnelList.adapter
            if (actionMode == null && !checkedItems.isEmpty() && activity != null) {
                (activity as AppCompatActivity).startSupportActionMode(this)
            } else if (actionMode != null && checkedItems.isEmpty()) {
                actionMode!!.finish()
            }
            adapter?.notifyItemChanged(position)
            updateTitle(actionMode)
        }

        fun toggleItemChecked(position: Int) {
            setItemChecked(position, !checkedItems.contains(position))
        }

        private fun updateTitle(mode: ActionMode?) {
            if (mode == null) {
                return
            }
            val count = checkedItems.size
            if (count == 0) {
                mode.title = ""
            } else {
                mode.title = resources!!.getQuantityString(R.plurals.delete_title, count, count)
            }
        }
    }

    companion object {
        private const val CHECKED_ITEMS = "CHECKED_ITEMS"
        private const val TAG = "WireGuard/TunnelListFragment"
    }
}
