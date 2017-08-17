package com.wireguard.android;

import android.databinding.BindingAdapter;
import android.databinding.ObservableArrayMap;
import android.databinding.ObservableList;
import android.graphics.Typeface;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Static methods for use by generated code in the Android data binding library.
 */

@SuppressWarnings("unused")
public final class BindingAdapters {
    @BindingAdapter({"items", "layout"})
    public static <K, V> void arrayMapBinding(final ListView view,
                                              final ObservableArrayMap<K, V> oldMap,
                                              final int oldLayoutId,
                                              final ObservableArrayMap<K, V> newMap,
                                              final int newLayoutId) {
        // Remove any existing binding when there is no new map.
        if (newMap == null) {
            view.setAdapter(null);
            return;
        }
        // The ListAdapter interface is not generic, so this cannot be checked.
        @SuppressWarnings("unchecked")
        ObservableArrayMapAdapter<K, V> adapter =
                (ObservableArrayMapAdapter<K, V>) view.getAdapter();
        // If the layout changes, any existing adapter must be replaced.
        if (newLayoutId != oldLayoutId)
            adapter = null;
        // Add a new binding if there was none, or if it must be replaced due to a layout change.
        if (adapter == null) {
            adapter = new ObservableArrayMapAdapter<>(view.getContext(), newLayoutId, newMap);
            view.setAdapter(adapter);
        } else if (newMap != oldMap) {
            // Changing the list only requires modifying the existing adapter.
            adapter.setMap(newMap);
        }
    }

    @BindingAdapter({"items", "layout"})
    public static <T> void listBinding(final ListView view,
                                       final ObservableList<T> oldList, final int oldLayoutId,
                                       final ObservableList<T> newList, final int newLayoutId) {
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

    @BindingAdapter({"items", "layout"})
    public static <K extends Comparable<K>, V> void sortedMapBinding(final ListView view,
                                         final ObservableSortedMap<K, V> oldMap,
                                         final int oldLayoutId,
                                         final ObservableSortedMap<K, V> newMap,
                                         final int newLayoutId) {
        // Remove any existing binding when there is no new map.
        if (newMap == null) {
            view.setAdapter(null);
            return;
        }
        // The ListAdapter interface is not generic, so this cannot be checked.
        @SuppressWarnings("unchecked")
        ObservableMapAdapter<K, V> adapter = (ObservableMapAdapter<K, V>) view.getAdapter();
        // If the layout changes, any existing adapter must be replaced.
        if (newLayoutId != oldLayoutId)
            adapter = null;
        // Add a new binding if there was none, or if it must be replaced due to a layout change.
        if (adapter == null) {
            adapter = new ObservableMapAdapter<>(view.getContext(), newLayoutId, newMap);
            view.setAdapter(adapter);
        } else if (newMap != oldMap) {
            // Changing the list only requires modifying the existing adapter.
            adapter.setMap(newMap);
        }
    }

    @BindingAdapter({"android:textStyle"})
    public static void textStyleBinding(final TextView view, final Typeface typeface) {
        view.setTypeface(typeface);
    }

    private BindingAdapters() {
        // Prevent instantiation.
    }
}
