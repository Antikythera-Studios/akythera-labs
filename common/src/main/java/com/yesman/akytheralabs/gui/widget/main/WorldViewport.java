package com.yesman.akytheralabs.gui.widget.main;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.OptionalDouble;

/// A display viewport to render in-game frame buffer with scaled size in AkytheraEditorLayoutManager editor
public final class WorldViewport extends AbstractWidget implements AnchoredWidget {
    /// Main render buffer to render in a rescaled size fit to this widget
    private final RenderTarget mainRenderTarget;

    public WorldViewport(
        final Unit x1,
        final Unit x2,
        final Unit y1,
        final Unit y2,
        final AnchoredWidget.HorizontalAnchorType horizontalAnchorType,
        final AnchoredWidget.VerticalAnchorType verticalAnchorType,
        final Component message
    ) {
        super(0, 0, 0, 0, message);

        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchorType;
        this.verticalAnchorType = verticalAnchorType;
        mainRenderTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget();

        // Block inputs. In-game inputs are handled by AkytheraEditorLayoutManager#snatchInputs
        active = false;
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor guiGraphics, int x, int y, float delta) {
        GpuSampler sampler = RenderSystem.getDevice()
            .createSampler(
                AddressMode.CLAMP_TO_EDGE,
                AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR,
                FilterMode.LINEAR,
                1,
                OptionalDouble.empty()
            );

        guiGraphics.innerBlit(
            RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND,
            mainRenderTarget.getColorTextureView(),
            sampler,
            getX(),
            getY(),
            getX() + getWidth(),
            getY() + getHeight(),
            0.0F,
            1.0F,
            1.0F,
            0.0F,
            0xFFFFFFFF
        );
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }

    /// ***************************************************************
    /// [AnchoredWidget] implementations                              *
    /// ***************************************************************
    private Unit x1;
    private Unit x2;
    private Unit y1;
    private Unit y2;
    private final AnchoredWidget.HorizontalAnchorType horizontalAnchorType;
    private final AnchoredWidget.VerticalAnchorType verticalAnchorType;

    @Override public Unit getX1() { return x1; }
    @Override public Unit getX2() { return x2; }
    @Override public Unit getY1() { return y1; }
    @Override public Unit getY2() { return y2; }
    @Override public void setX1(Unit unit) { x1 = unit; }
    @Override public void setX2(Unit unit) { x2 = unit; }
    @Override public void setY1(Unit unit) { y1 = unit; }
    @Override public void setY2(Unit unit) { y2 = unit; }
    @Override public HorizontalAnchorType getHorizontalAnchorType() { return horizontalAnchorType; }
    @Override public VerticalAnchorType getVerticalAnchorType() { return verticalAnchorType; }
}
