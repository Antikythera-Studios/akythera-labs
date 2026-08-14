package com.yesman.akytheralabs.mixin;

import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// The disconnect teardown moved off `Gui` and onto [Hud] as of 26.2.
@Mixin(value = Hud.class)
public abstract class MixinHud {
    @Inject(
        at = @At(
            value = "TAIL"
        ),
        method = "onDisconnected()V"
    )
    private void onDisconnected(CallbackInfo callbackInfo) {
        // Close the AkytheraEditorLayoutManager editor if the player quits a world
        if (AkytheraEditorLayoutManager.getInstance().isActive()) {
            AkytheraEditorLayoutManager.getInstance().closeEditorLayout();
        }
    }
}
