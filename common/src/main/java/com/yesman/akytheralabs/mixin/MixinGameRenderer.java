package com.yesman.akytheralabs.mixin;

import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {
    @Shadow
    private Minecraft minecraft;

    @Inject(
        at = @At("TAIL"),
        method = "resize(II)V"
    )
    private void onResize(int width, int height, CallbackInfo ci) {
        if (AkytheraEditorLayoutManager.getInstance().isActive()) {
            int calculatedGuiScale = minecraft.getWindow().calculateScale(
                minecraft.options.guiScale().get(),
                minecraft.isEnforceUnicode()
            );

            AkytheraEditorLayoutManager.getInstance().onScreenResized(width, height, calculatedGuiScale);
        }
    }
}
