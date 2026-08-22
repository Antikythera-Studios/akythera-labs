package com.yesman.akytheralabs.mixin;

import com.yesman.akytheralabs.editor.debug.EntityDebugSession;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MixinMob {
    /// Holds the goals, navigation and controls of a possessed mob.
    ///
    /// `Mob#setNoAi` cannot be used for this: it flips [Mob#isEffectiveAi], and [LivingEntity#aiStep] gates
    /// `travel(input)` on that flag, so a no-AI mob is frozen and the editor's movement inputs do nothing.
    /// Cancelling `serverAiStep` stops the AI at its source and leaves `travel` running.
    @Inject(
        at = @At("HEAD"),
        method = "serverAiStep()V",
        cancellable = true
    )
    private void onServerAiStep(CallbackInfo callbackInfo) {
        if (EntityDebugSession.getInstance().isPossessed((Mob)(Object)this)) {
            callbackInfo.cancel();
        }
    }
}
