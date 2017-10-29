package com.wireguard.android.bindings;

import android.databinding.BindingAdapter;
import android.databinding.ObservableList;
import android.databinding.adapters.ListenerUtil;
import android.graphics.Typeface;
import android.text.InputFilter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.wireguard.android.R;

/**
 * Static methods for use by generated code in the Android data binding library.
 */

@SuppressWarnings("unused")
public final class BindingAdapters {
    @BindingAdapter({"filter"})
    public static void setFilter(final TextView view, final InputFilter filter) {
        view.setFilters(new InputFilter[]{filter});
    }

    @BindingAdapter({"items", "layout"})
    public static <T> void setItems(final LinearLayout view,
                                    final ObservableList<T> oldList, final int oldLayoutId,
                                    final ObservableList<T> newList, final int newLayoutId) {
        if (oldList == newList && oldLayoutId == newLayoutId)
            return;
        ItemChangeListener<T> listener = ListenerUtil.getListener(view, R.id.item_change_listener);
        // If the layout changes, any existing listener must be replaced.
        if (listener != null && oldList != null && oldLayoutId != newLayoutId) {
            listener.setList(null);
            listener = null;
            // Stop tracking the old listener.
            ListenerUtil.trackListener(view, null, R.id.item_change_listener);
        }
        // Avoid adding a listener when there is no new list or layout.
        if (newList == null || newLayoutId == 0)
            return;
        if (listener == null) {
            listener = new ItemChangeListener<>(view, newLayoutId);
            ListenerUtil.trackListener(view, listener, R.id.item_change_listener);
        }
        // Either the list changed, or this is an entirely new listener because the layout changed.
        listener.setList(newList);
    }

    @BindingAdapter({"items", "layout"})
    public static <T> void setItems(final ListView view,
                                    final ObservableList<T> oldList, final int oldLayoutId,
                                    final ObservableList<T> newList, final int newLayoutId) {
        if (oldList == newList && oldLayoutId == newLayoutId)
            return;
        // The ListAdapter interface is not generic, so this cannot be checked.
        @SuppressWarnings("unchecked")
        ObservableListAdapter<T> adapter = (ObservableListAdapter<T>) view.getAdapter();
        // If the layout changes, any existing adapter must be replaced.
        if (adapter != null && oldList != null && oldLayoutId != newLayoutId) {
            adapter.setList(null);
            adapter = null;
        }
        // Avoid setting an adapter when there is no new list or layout.
        if (newList == null || newLayoutId == 0)
            return;
        if (adapter == null) {
            adapter = new ObservableListAdapter<>(view.getContext(), newLayoutId, newList);
            view.setAdapter(adapter);
        }
        // Either the list changed, or this is an entirely new listener because the layout changed.
        adapter.setList(newList);
    }

    @BindingAdapter({"items", "layout"})
    public static <K extends Comparable<K>, V> void setItems(final ListView view,
                                                             final ObservableSortedMap<K, V> oldMap,
                                                             final int oldLayoutId,
                                                             final ObservableSortedMap<K, V> newMap,
                                                             final int newLayoutId) {
        if (oldMap == newMap && oldLayoutId == newLayoutId)
            return;
        // The ListAdapter interface is not generic, so this cannot be checked.
        @SuppressWarnings("unchecked")
        ObservableMapAdapter<K, V> adapter = (ObservableMapAdapter<K, V>) view.getAdapter();
        // If the layout changes, any existing adapter must be replaced.
        if (adapter != null && oldMap != null && oldLayoutId != newLayoutId) {
            adapter.setMap(null);
            adapter = null;
        }
        // Avoid setting an adapter when there is no new list or layout.
        if (newMap == null || newLayoutId == 0)
            return;
        if (adapter == null) {
            adapter = new ObservableMapAdapter<>(view.getContext(), newLayoutId, newMap);
            view.setAdapter(adapter);
        }
        // Either the list changed, or this is an entirely new listener because the layout changed.
        adapter.setMap(newMap);
    }

    @BindingAdapter({"android:textStyle"})
    public static void setTextStyle(final TextView view, final Typeface typeface) {
        view.setTypeface(typeface);
    }

    private BindingAdapters() {
        // Prevent instantiation.
    }
}
