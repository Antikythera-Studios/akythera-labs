package com.yesman.akytheralabs.mixin;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.editor.debug.EntityDebugSession;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @Final
    @Shadow
    public MouseHandler mouseHandler;

    @Final
    @Shadow
    private Window window;

    @Final
    @Shadow
    private DeltaTracker.Timer deltaTracker;

    /// The frame's one and only present. 26.2 replaced `RenderTarget#blitToScreen` with a blit off
    /// the window surface, and the surface rejects a second blit in the same frame — so the editor
    /// layout is drawn here and its target presented *instead of* the game's, not after it.
    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/GpuSurface;blitFromTexture(Lcom/mojang/blaze3d/systems/CommandEncoder;Lcom/mojang/blaze3d/textures/GpuTextureView;)V"
        ),
        method = "renderFrame(Z)V"
    )
    private void renderFrame(GpuSurface surface, CommandEncoder commandEncoder, GpuTextureView colorTexture) {
        if (AkytheraEditorLayoutManager.getInstance().isActive()) {
            int xMouse = (int)mouseHandler.getScaledXPos(window);
            int yMouse = (int)mouseHandler.getScaledYPos(window);

            AkytheraEditorLayoutManager.getInstance().renderEditorLayout(xMouse, yMouse, deltaTracker.getGameTimeDeltaTicks());

            surface.blitFromTexture(commandEncoder, AkytheraEditorLayoutManager.getInstance().getEditorRenderTarget().getColorTextureView());
        } else {
            surface.blitFromTexture(commandEncoder, colorTexture);
        }
    }

    @Inject(
        at = @At("TAIL"),
        method = "tick()V"
    )
    private void onTick(CallbackInfo callbackInfo) {
        EntityDebugSession.getInstance().clientTick();
    }
}
