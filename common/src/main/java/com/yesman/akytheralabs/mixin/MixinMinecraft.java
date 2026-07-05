package com.yesman.akytheralabs.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @Final
    @Shadow
    public GameRenderer gameRenderer;

    @Final
    @Shadow
    public MouseHandler mouseHandler;

    @Final
    @Shadow
    private Window window;

    @Final
    @Shadow
    private DeltaTracker.Timer deltaTracker;

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen()V"
        ),
        method = "renderFrame(Z)V"
    )
    private void renderFrame(RenderTarget renderTarget, boolean advanceGameTime) {


        if (AkytheraEditorLayoutManager.getInstance().isActive()) {
            int xMouse = (int)mouseHandler.getScaledXPos(window);
            int yMouse = (int)mouseHandler.getScaledYPos(window);

            gameRenderer.getGameRenderState().guiRenderState.reset();

            GuiGraphicsExtractor graphics = new GuiGraphicsExtractor((Minecraft)(Object)this, gameRenderer.getGameRenderState().guiRenderState, xMouse, yMouse);
            AkytheraEditorLayoutManager.getInstance().renderEditorLayout(graphics, xMouse, yMouse, deltaTracker.getGameTimeDeltaTicks());
        } else {
            renderTarget.blitToScreen();
        }
    }
}
