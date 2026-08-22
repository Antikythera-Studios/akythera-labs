package com.yesman.akytheralabs.mixin;

import com.mojang.serialization.Codec;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;
import com.yesman.akytheralabs.akythera.datablock.GuardedDataBlockCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// Hands out [GuardedDataBlockCodec] in place of the plain name codec, so a reference to a data
/// block the data packs have not been read for yet degrades to a stand-in instead of failing the
/// world load. See [GuardedDataBlockCodec] for why the two can be out of step.
///
/// Injected on the getter rather than the memoized supplier behind it: the engine captures the
/// result in static initializers (`AnimationStateMachineNode.StateEntry.CODEC` and friends), and
/// the wrapper is cached per manager so those captures all share one instance.
@Mixin(EnumerableDataBlockManager.class)
public abstract class MixinEnumerableDataBlockManager {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "getNameCodec", at = @At("RETURN"), cancellable = true)
    private void akytheralabs$guardNameCodec(CallbackInfoReturnable<Codec> callbackInfo) {
        EnumerableDataBlockManager manager = (EnumerableDataBlockManager)(Object)this;
        callbackInfo.setReturnValue(GuardedDataBlockCodec.guard(manager, callbackInfo.getReturnValue()));
    }
}
