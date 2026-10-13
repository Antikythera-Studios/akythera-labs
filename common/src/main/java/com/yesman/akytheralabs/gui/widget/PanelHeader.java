package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

public final class PanelHeader extends AbstractWidget implements AnchoredWidget {
    public static final int DEFAULT_HEADER_HEIGHT = 22;

    private final Font font;

    public PanelHeader(Font font, Component title) {
        this(font, title, DEFAULT_HEADER_HEIGHT);
    }

    public PanelHeader(Font font, Component title, int height) {
        super(0, 0, 0, 0, title);

        this.font = font;
        this.x1 = AnchoredWidget.pixel(0);
        this.x2 = AnchoredWidget.pixel(0);
        this.y1 = AnchoredWidget.pixel(0);
        this.y2 = AnchoredWidget.pixel(height);
        this.horizontalAnchorType = HorizontalAnchorType.LEFT_RIGHT;
        this.verticalAnchorType = VerticalAnchorType.TOP_HEIGHT;
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(getX(), getY(), getRight(), getBottom(), 0xFF252525);
        graphics.fill(getX(), getBottom() - 1, getRight(), getBottom(), 0xFF404040);
        graphics.text(
            font,
            getMessage(),
            getX() + 6,
            getY() + (getHeight() - font.lineHeight) / 2,
            0xFF999999,
            false
        );
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }

    // Header always deactivated
    @Override
    public boolean isActive() {
        return false;
    }

    // ***************************************************************
    // [AnchoredWidget] implementations
    // ***************************************************************
    private Unit x1;
    private Unit x2;
    private Unit y1;
    private Unit y2;
    private AnchoredWidget.HorizontalAnchorType horizontalAnchorType;
    private AnchoredWidget.VerticalAnchorType verticalAnchorType;

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
    @Override public void setHorizontalAnchorType(HorizontalAnchorType horizontalAnchorType) { this.horizontalAnchorType = horizontalAnchorType; }
    @Override public void setVerticalAnchorType(VerticalAnchorType verticalAnchorType) { this.verticalAnchorType = verticalAnchorType; }
}
