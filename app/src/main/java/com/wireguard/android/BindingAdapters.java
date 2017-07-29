package com.wireguard.android;

import android.databinding.BindingAdapter;
import android.databinding.ObservableList;
import android.widget.ListView;

/**
 * Static methods for use by generated code in the Android data binding library.
 */

public final class BindingAdapters {
    @BindingAdapter({"items", "layout"})
    public static <T> void listBinding(ListView view, ObservableList<T> oldList, int oldLayoutId,
                                       ObservableList<T> newList, int newLayoutId) {
        // Remove any existing binding when there is no new list.
        if (newList == null) {
            view.setAdapter(null);
            return;
        }
        // The ListAdapter interface is not generic, so this cannot be checked.
        @SuppressWarnings("unchecked")
        ObservableListAdapter<T> adapter = (ObservableListAdapter<T>) view.getAdapter();
        // If the layout changes, any existing adapter must be replaced.
        if (newLayoutId != oldLayoutId)
            adapter = null;
        // Add a new binding if there was none, or if it must be replaced due to a layout change.
        if (adapter == null) {
            adapter = new ObservableListAdapter<>(view.getContext(), newLayoutId, newList);
            view.setAdapter(adapter);
        } else if (newList != oldList) {
            // Changing the list only requires modifying the existing adapter.
            adapter.setList(newList);
        }
    }

    private BindingAdapters() {
    }
}
