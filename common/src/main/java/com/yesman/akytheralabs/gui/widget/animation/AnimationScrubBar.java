package com.yesman.akytheralabs.gui.widget.animation;

import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/// A single-handle track over an animation's `[0, total]` seconds. Pressing or dragging anywhere on
/// it reports the scrubbed time; it holds no time of its own, so an animator that advances by
/// itself keeps the playhead moving under the cursor.
public final class AnimationScrubBar extends AbstractWidget implements AnchoredWidget {
    private static final int TRACK_COLOR = 0xFF1A1A1A;
    private static final int ELAPSED_COLOR = 0xFF2E5A70;
    private static final int PLAYHEAD_COLOR = 0xFF37D5FF;
    private static final int PLAYHEAD_W = 3;

    private final DoubleSupplier totalSeconds;
    private final DoubleSupplier currentSeconds;
    private final DoubleConsumer onScrubbed;

    public AnimationScrubBar(
        Unit x1, Unit x2, Unit y1, Unit y2,
        HorizontalAnchorType horizontalAnchor, VerticalAnchorType verticalAnchor,
        DoubleSupplier totalSeconds,
        DoubleSupplier currentSeconds,
        DoubleConsumer onScrubbed
    ) {
        super(0, 0, 0, 0, Component.empty());

        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchor;
        this.verticalAnchorType = verticalAnchor;
        this.totalSeconds = totalSeconds;
        this.currentSeconds = currentSeconds;
        this.onScrubbed = onScrubbed;
    }

    private double timeAt(double mouseX) {
        double total = totalSeconds.getAsDouble();

        if (total <= 0.0D || getWidth() <= 0) return 0.0D;

        return Math.clamp((mouseX - getX()) / getWidth(), 0.0D, 1.0D) * total;
    }

    private int playheadX() {
        double total = totalSeconds.getAsDouble();

        if (total <= 0.0D) return getX();

        return getX() + (int) (Math.clamp(currentSeconds.getAsDouble() / total, 0.0D, 1.0D) * getWidth());
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int playhead = playheadX();

        graphics.fill(getX(), getY(), getRight(), getBottom(), TRACK_COLOR);
        graphics.fill(getX(), getY(), playhead, getBottom(), ELAPSED_COLOR);
        graphics.fill(playhead - PLAYHEAD_W / 2, getY(), playhead + PLAYHEAD_W - PLAYHEAD_W / 2, getBottom(), PLAYHEAD_COLOR);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0 || !isMouseOver(event.x(), event.y())) return false;

        onScrubbed.accept(timeAt(event.x()));

        return true;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (event.button() != 0) return false;

        onScrubbed.accept(timeAt(event.x()));

        return true;
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
    private final HorizontalAnchorType horizontalAnchorType;
    private final VerticalAnchorType verticalAnchorType;

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
