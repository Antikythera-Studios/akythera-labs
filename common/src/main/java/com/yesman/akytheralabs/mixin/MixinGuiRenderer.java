package com.yesman.akytheralabs.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiRenderer.class)
public abstract class MixinGuiRenderer {
    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;getMainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"
        ),
        method = "draw(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"
    )
    private RenderTarget draw(Minecraft minecraft) {
        // Return the AkytheraEditorLayoutManager main render target if it's activated.
        // This is to split frame buffer editor layout and in-game viewport layout
        if (AkytheraEditorLayoutManager.getInstance().shouldInterceptRenderTarget()) {
            return AkytheraEditorLayoutManager.getInstance().getEditorRenderTarget();
        }

        return minecraft.getMainRenderTarget();
    }
}
