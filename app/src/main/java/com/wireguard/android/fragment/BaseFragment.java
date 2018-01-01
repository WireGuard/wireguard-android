package com.wireguard.android.fragment;

import android.app.Fragment;
import android.content.Context;

import com.wireguard.android.activity.BaseActivity;
import com.wireguard.android.activity.BaseActivity.OnSelectedTunnelChangedListener;
import com.wireguard.android.model.Tunnel;

/**
 * Base class for fragments that need to know the currently-selected tunnel. Only does anything when
 * attached to a {@code BaseActivity}.
 */

public abstract class BaseFragment extends Fragment implements OnSelectedTunnelChangedListener {
    private BaseActivity activity;

    protected Tunnel getSelectedTunnel() {
        return activity != null ? activity.getSelectedTunnel() : null;
    }

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        if (context instanceof BaseActivity) {
            activity = (BaseActivity) context;
            activity.addOnSelectedTunnelChangedListener(this);
        } else {
            activity = null;
        }
    }

    @Override
    public void onDetach() {
        if (activity != null)
            activity.removeOnSelectedTunnelChangedListener(this);
        activity = null;
        super.onDetach();
    }

    protected void setSelectedTunnel(final Tunnel tunnel) {
        if (activity != null)
            activity.setSelectedTunnel(tunnel);
    }
}
