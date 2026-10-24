package com.yesman.akytheralabs.mixin;

import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.dev.ProjectReloader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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

    /// Skips extracting the level and the HUD while [ProjectReloader] reloads the world.
    @ModifyVariable(
        at = @At("HEAD"),
        method = "extract(Lnet/minecraft/client/DeltaTracker;Z)V",
        argsOnly = true
    )
    private boolean extract(boolean advanceGameTime) {
        return advanceGameTime && !ProjectReloader.isReloadingWorld();
    }

    /// Skips rendering the level while [ProjectReloader] reloads the world.
    @ModifyVariable(
        at = @At("HEAD"),
        method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
        argsOnly = true
    )
    private boolean render(boolean advanceGameTime) {
        return advanceGameTime && !ProjectReloader.isReloadingWorld();
    }

    /// Draws the frame [ProjectReloader] froze at the start of a world reload, where the level would be drawn.
    @Inject(
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V"
        ),
        method = "render(Lnet/minecraft/client/DeltaTracker;Z)V"
    )
    private void drawFrozenFrame(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo callbackInfo) {
        ProjectReloader.drawFrozenFrame(((GameRenderer)(Object)this).mainRenderTarget());
    }
}
