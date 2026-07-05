package com.yesman.akytheralabs.dataholder;

import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/// Shared boilerplate for [DataHolder] implementations: the holder's own [ResourceKey], pushed in
/// by [ProjectObjectRegistry] at register / rekey time so bind callbacks can call back into the
/// project without an extra map lookup.
public abstract class AbstractDataHolder<T> implements DataHolder<T> {
    @Nullable
    private ResourceKey<T> ownResourceKey;

    public @Nullable ResourceKey<T> getOwnResourceKey() {
        return ownResourceKey;
    }

    public void setOwnResourceKey(ResourceKey<T> key) {
        boolean firstAssignment = (this.ownResourceKey == null);
        this.ownResourceKey = key;
        if (firstAssignment) onOwnResourceKeyAssigned();
    }

    /// Called once, immediately after this holder is first registered with a project registry and
    /// learns its own [ResourceKey]. Subclasses override to do one-time setup that needs the key.
    protected void onOwnResourceKeyAssigned() {}
}
