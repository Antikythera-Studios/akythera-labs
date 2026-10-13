package com.yesman.akytheralabs.gui.widget.particle;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.Set;

/// The box selection dragged over the emitter track: a dashed outline from where the press landed to the cursor,
/// drawn once the cursor moved. Its bounds are the box, in screen coordinates.
public final class KeySelectionBox extends AbstractWidget {
    private static final int COLOR = 0xFFFFFFFF;

    /// The length of a dash of the outline, and of the gap after it, in pixels.
    private static final int DASH = 3;

    /// The corner where the press landed, in screen coordinates.
    private final int anchorX;
    private final int anchorY;

    /// The keys that stay selected whatever the box covers.
    private final Set<EmitterTrackPanel.KeyRef> kept;

    /// Whether the cursor moved since the press.
    private boolean dragged;

    public KeySelectionBox(final double x, final double y, final Set<EmitterTrackPanel.KeyRef> kept) {
        super((int)x, (int)y, 0, 0, Component.empty());

        this.anchorX = (int)x;
        this.anchorY = (int)y;
        this.kept = kept;
    }

    /// Moves the corner opposite the press to `x` / `y`.
    public void dragTo(final double x, final double y) {
        setX(Math.min(anchorX, (int)x));
        setY(Math.min(anchorY, (int)y));
        setWidth(Math.abs((int)x - anchorX));
        setHeight(Math.abs((int)y - anchorY));
        dragged = true;
    }

    public boolean wasDragged() {
        return dragged;
    }

    public Set<EmitterTrackPanel.KeyRef> kept() {
        return kept;
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        if (!dragged) return;

        for (int x = getX(); x < getRight(); x += DASH * 2) {
            g.fill(x, getY(), Math.min(x + DASH, getRight()), getY() + 1, COLOR);
            g.fill(x, getBottom(), Math.min(x + DASH, getRight()), getBottom() + 1, COLOR);
        }

        for (int y = getY(); y < getBottom(); y += DASH * 2) {
            g.fill(getX(), y, getX() + 1, Math.min(y + DASH, getBottom()), COLOR);
            g.fill(getRight(), y, getRight() + 1, Math.min(y + DASH, getBottom()), COLOR);
        }
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput o) {
    }
}
