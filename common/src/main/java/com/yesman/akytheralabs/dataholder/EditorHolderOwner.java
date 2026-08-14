package com.yesman.akytheralabs.dataholder;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;

/// The owner [Holder.Reference] takes in its constructor.
///
/// The editor's holders answer `canSerializeIn` themselves and read their value off the project
/// registry rather than the owner, so this carries nothing — it only fills the parameter.
final class EditorHolderOwner<T> implements HolderOwner<T> {
    private static final EditorHolderOwner<?> INSTANCE = new EditorHolderOwner<>();

    @SuppressWarnings("unchecked")
    static <T> HolderOwner<T> get() {
        return (HolderOwner<T>) INSTANCE;
    }

    private EditorHolderOwner() {
    }

    @Override
    public String toString() {
        return "akythera-labs editor";
    }
}
