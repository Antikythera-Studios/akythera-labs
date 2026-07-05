package com.yesman.akytheralabs.mixin;

import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Gui.class)
public abstract class MixinGui {
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
