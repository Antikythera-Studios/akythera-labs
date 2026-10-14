package com.yesman.akytheralabs.mixin;

import com.yesman.akytheralabs.mixinaccessor.GuiGraphicsExtractorAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GuiGraphicsExtractor.class)
public abstract class MixinGuiGraphicsExtractorAccessor implements GuiGraphicsExtractorAccessor {
    @Shadow @Final
    private GuiRenderState guiRenderState;

    /// Multiplied into the alpha of every fill, text and blit drawn.
    @Unique
    private float akytheralabs$alpha = 1.0F;

    @Override
    public GuiRenderState akytheralabs$guiRenderState() {
        return guiRenderState;
    }

    @Override
    public void akytheralabs$setAlpha(final float alpha) {
        this.akytheralabs$alpha = alpha;
    }

    @ModifyVariable(
        at = @At("HEAD"),
        method = "innerFill(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/gui/render/TextureSetup;IIIIILjava/lang/Integer;)V",
        argsOnly = true,
        ordinal = 4
    )
    private int fadeFill(int color) {
        return akytheralabs$alpha == 1.0F ? color : ARGB.multiplyAlpha(color, akytheralabs$alpha);
    }

    @ModifyVariable(
        at = @At("HEAD"),
        method = "innerFill(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/gui/render/TextureSetup;IIIIILjava/lang/Integer;)V",
        argsOnly = true
    )
    private @Nullable Integer fadeFillGradient(@Nullable Integer color) {
        return akytheralabs$alpha == 1.0F || color == null ? color : Integer.valueOf(ARGB.multiplyAlpha(color, akytheralabs$alpha));
    }

    @ModifyVariable(
        at = @At("HEAD"),
        method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
        argsOnly = true,
        ordinal = 2
    )
    private int fadeText(int color) {
        return akytheralabs$alpha == 1.0F ? color : ARGB.multiplyAlpha(color, akytheralabs$alpha);
    }

    @ModifyVariable(
        at = @At("HEAD"),
        method = "innerBlit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/textures/GpuTextureView;Lcom/mojang/blaze3d/textures/GpuSampler;IIIIFFFFI)V",
        argsOnly = true,
        ordinal = 4
    )
    private int fadeBlit(int color) {
        return akytheralabs$alpha == 1.0F ? color : ARGB.multiplyAlpha(color, akytheralabs$alpha);
    }

    @ModifyVariable(
        at = @At("HEAD"),
        method = "innerTiledBlit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/textures/GpuTextureView;Lcom/mojang/blaze3d/textures/GpuSampler;IIIIIIFFFFI)V",
        argsOnly = true,
        ordinal = 6
    )
    private int fadeTiledBlit(int color) {
        return akytheralabs$alpha == 1.0F ? color : ARGB.multiplyAlpha(color, akytheralabs$alpha);
    }
}
