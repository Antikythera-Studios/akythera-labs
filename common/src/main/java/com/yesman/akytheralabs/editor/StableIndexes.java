package com.yesman.akytheralabs.editor;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/// Indexes of a live list's elements that hold while the list changes: a removed element keeps its index, an added
/// one takes the next. [#rebuild] indexes the live list again from 0. Elements are told apart by identity.
public final class StableIndexes<T> {
    /// The list the indexes are given to, read on every call.
    private final Supplier<List<T>> live;

    /// Each element given an index so far, at its index.
    private final List<T> indexed = new ArrayList<>();

    public StableIndexes(final Supplier<List<T>> live) {
        this.live = live;
    }

    /// The index of `element`, or -1 when the live list never held it.
    public int indexOf(final T element) {
        sync();

        for (int i = 0; i < indexed.size(); i++) {
            if (indexed.get(i) == element) return i;
        }

        return -1;
    }

    /// The element at `index` while the live list still holds it, or null.
    public @Nullable T get(final int index) {
        sync();

        if (index < 0 || index >= indexed.size()) return null;

        T element = indexed.get(index);

        return live.get().stream().anyMatch(candidate -> candidate == element) ? element : null;
    }

    /// Indexes the live list again from 0, forgetting the removed elements.
    public void rebuild() {
        indexed.clear();
        indexed.addAll(live.get());
    }

    /// Gives the next indexes to the live elements that have none, in list order.
    private void sync() {
        for (T element : live.get()) {
            if (indexed.stream().noneMatch(candidate -> candidate == element)) indexed.add(element);
        }
    }
}
