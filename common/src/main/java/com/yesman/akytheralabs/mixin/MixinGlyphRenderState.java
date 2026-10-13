package com.yesman.akytheralabs.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import org.joml.Matrix3x2fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GlyphRenderState.class)
public abstract class MixinGlyphRenderState {
    /// How far a scale may sit from a whole number and still count as one.
    @Unique
    private static final float WHOLE_SCALE_EPSILON = 0.001F;

    @Shadow @Final
    private Matrix3x2fc pose;

    @Shadow @Final
    private TextRenderable renderable;

    /// Samples a glyph linearly while the editor draws it at a scale that is not a whole number, such as a
    /// zoomed node graph.
    ///
    /// Vanilla samples glyphs with the nearest texel, which suits the whole-number scales it draws text at.
    /// At a scale like 1.1 each texel covers either one or two screen pixels, so the strokes of a glyph come
    /// out in uneven widths and the text looks broken. Linear sampling spreads every texel evenly instead.
    /// The glyph atlas keeps one empty texel between glyphs, so the blend never picks up a neighbor.
    @Inject(at = @At("RETURN"), method = "textureSetup()Lnet/minecraft/client/gui/render/TextureSetup;", cancellable = true)
    private void smoothScaledGlyphs(CallbackInfoReturnable<TextureSetup> callback) {
        if (!AkytheraEditorLayoutManager.getInstance().isActive()) return;

        boolean wholeScale = pose.m01() == 0.0F && pose.m10() == 0.0F
            && Math.abs(pose.m00() - Math.rint(pose.m00())) < WHOLE_SCALE_EPSILON
            && Math.abs(pose.m11() - Math.rint(pose.m11())) < WHOLE_SCALE_EPSILON;

        if (wholeScale) return;

        callback.setReturnValue(TextureSetup.singleTextureWithLightmap(
            renderable.textureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)));
    }
}
