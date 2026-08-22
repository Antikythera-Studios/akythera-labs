package com.yesman.akytheralabs.mixin;

import com.yesman.akytheralabs.editor.debug.EntityDebugSession;
import com.yesman.akytheralabs.gui.widget.main.EntityDebugModal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class MixinPlayer {
    /// Keeps sneak from dropping the possessed entity's camera.
    ///
    /// `ServerPlayer#tick` calls `setCamera(this)` whenever `wantsToStopRiding()` — which is the shift key —
    /// so the possessed entity would be released the moment the user sneaks with it. The editor releases
    /// through [EntityDebugModal]'s close button instead.
    @Inject(
        at = @At("HEAD"),
        method = "wantsToStopRiding()Z",
        cancellable = true
    )
    private void onWantsToStopRiding(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (EntityDebugSession.getInstance().isPossessor((Player)(Object)this)) {
            callbackInfo.setReturnValue(false);
        }
    }
}
