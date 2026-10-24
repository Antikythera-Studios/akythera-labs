package com.yesman.akytheralabs.mixin;

import com.yesman.akytheralabs.dev.DataPackRegistryReloader;
import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/// Exposes [Holder.Reference]'s value binding, which only `MappedRegistry#freeze` reaches, to
/// [DataPackRegistryReloader].
@Mixin(Holder.Reference.class)
public interface HolderReferenceAccessor<T> {
    @Invoker("bindValue")
    void akythera$bindValue(T value);
}
