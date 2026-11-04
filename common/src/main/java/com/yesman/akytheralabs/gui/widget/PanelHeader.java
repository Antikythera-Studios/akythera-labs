package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/// The title bar of a panel. Made foldable by [AdjustablePanelManager.PanelSection.SetupProxy#foldable], a click on it
/// folds and unfolds the panel, shown by a chevron before the title.
public final class PanelHeader extends AbstractWidget implements AnchoredWidget {
    public static final int DEFAULT_HEADER_HEIGHT = 22;

    /// Room the chevron takes before the title, in pixels.
    private static final int CHEVRON_W = 10;

    private final Font font;

    /// Told the new fold state on each click, null for a header that does not fold.
    private @Nullable Consumer<Boolean> onFoldToggled;

    private boolean folded;

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

        int textY = getY() + (getHeight() - font.lineHeight) / 2;
        int textX = getX() + 6;

        if (onFoldToggled != null) {
            graphics.text(font, folded ? "▶" : "▼", textX, textY, isHovered() ? 0xFFDDDDDD : 0xFF999999, false);
            textX += CHEVRON_W;
        }

        graphics.text(font, getMessage(), textX, textY, 0xFF999999, false);
    }

    /// Lets a click fold and unfold the panel, telling `onFoldToggled` each new state.
    public void setFoldable(final Consumer<Boolean> onFoldToggled) {
        this.onFoldToggled = onFoldToggled;
    }

    public boolean isFolded() {
        return folded;
    }

    /// Shows the panel folded or not, without telling anyone.
    public void setFolded(final boolean folded) {
        this.folded = folded;
    }

    @Override
    public void onClick(final @NonNull MouseButtonEvent event, final boolean doubleClick) {
        if (onFoldToggled == null) return;

        folded = !folded;
        onFoldToggled.accept(folded);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }

    /// Takes clicks only while foldable.
    @Override
    public boolean isActive() {
        return onFoldToggled != null && visible;
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
