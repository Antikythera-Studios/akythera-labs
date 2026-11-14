package com.yesman.akytheralabs.gui.widget.cinema;

import com.yesman.akythera.client.api.gui.modal.ModalManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/// One camera key of an actor on the cinema track: a diamond at its time. A press selects it (control as
/// [CinemaTrackPanel#clickKey] reads it) and takes hold of the selected keys, which then follow the cursor along the
/// time axis until the button is released. A right-press opens the key menu.
public final class CinemaCameraKeyWidget extends AbstractWidget {
    /// From the diamond's center to its tips, in pixels. Also its click radius.
    public static final int RADIUS = 4;

    private static final int COLOR = 0xFFFFAA33;
    private static final int SELECTED_COLOR = 0xFFFFFFFF;

    private final CinemaTrackPanel parent;
    private final CinemaTrackPanel.KeyRef key;

    public CinemaCameraKeyWidget(final CinemaTrackPanel parent, final CinemaTrackPanel.KeyRef key) {
        super(0, 0, RADIUS * 2 + 1, RADIUS * 2 + 1, Component.empty());

        this.parent = parent;
        this.key = key;
    }

    public CinemaTrackPanel.KeyRef key() {
        return key;
    }

    /// Recomputes the bounds from the key's time and the row's current top.
    public void updatePosition(final int rowTop, final int rowHeight) {
        setX(parent.frameToX(key.time() * 60.0F) - RADIUS);
        setY(rowTop + rowHeight / 2 - RADIUS);
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int centerX = getX() + RADIUS;
        int centerY = getY() + RADIUS;
        int color = parent.isKeySelected(key) ? SELECTED_COLOR : COLOR;

        for (int dy = -RADIUS; dy <= RADIUS; dy++) {
            int half = RADIUS - Math.abs(dy);

            g.fill(centerX - half, centerY + dy, centerX + half + 1, centerY + dy + 1, color);
        }
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1) {
            if (!parent.isKeySelected(key)) parent.clickKey(key, false);

            ModalManager.getInstance().openModalWidget(parent.keyMenu(), (int)event.x(), (int)event.y());

            return true;
        }

        if (event.button() != 0) return false;

        // A plain press on a selected key keeps the selection, so the drag moves all of it
        if (!parent.isKeySelected(key) || event.hasControlDownWithQuirk()) parent.clickKey(key, event.hasControlDownWithQuirk());
        if (parent.isKeySelected(key)) parent.beginKeyGrab(event.x());

        return true;
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput o) {
    }
}
