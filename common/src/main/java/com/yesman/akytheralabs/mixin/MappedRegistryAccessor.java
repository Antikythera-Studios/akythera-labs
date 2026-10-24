package com.yesman.akytheralabs.mixin;

import com.yesman.akytheralabs.dev.DataPackRegistryReloader;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/// Exposes [MappedRegistry]'s private lookup state, so a dev-time reload can refill a frozen datapack
/// registry in place — see [DataPackRegistryReloader].
@Mixin(MappedRegistry.class)
public interface MappedRegistryAccessor<T> {
    @Accessor("frozen")
    void akythera$setFrozen(boolean frozen);

    @Accessor("byId")
    ObjectList<Holder.Reference<T>> akythera$getById();

    @Accessor("toId")
    Reference2IntMap<T> akythera$getToId();

    @Accessor("byLocation")
    Map<Identifier, Holder.Reference<T>> akythera$getByLocation();

    @Accessor("byKey")
    Map<ResourceKey<T>, Holder.Reference<T>> akythera$getByKey();

    @Accessor("byValue")
    Map<T, Holder.Reference<T>> akythera$getByValue();

    @Accessor("registrationInfos")
    Map<ResourceKey<T>, RegistrationInfo> akythera$getRegistrationInfos();
}
