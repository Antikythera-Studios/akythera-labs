package com.yesman.akytheralabs.gui.widget.particle;

import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.akythera.particle.ParticleEmitterHolder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/// One key of an emitter's channel on the emitter track: a diamond at its time. A press selects it (shift and control
/// as [EmitterTrackPanel#clickKey] reads them) and takes hold of the selected keys, which then follow the cursor along
/// the time axis until the button is released. A right-press opens the key menu.
public final class EmitterKeyWidget extends AbstractWidget {
    /// From the diamond's center to its tips, in pixels. Also its click radius.
    public static final int RADIUS = 4;

    private static final int PIVOT_COLOR = 0xFF33CCFF;
    private static final int TRANSFORM_COLOR = 0xFFFF9922;
    private static final int SELECTED_COLOR = 0xFFFFFFFF;

    private final EmitterTrackPanel parent;
    private final EmitterTrackPanel.KeyRef key;

    public EmitterKeyWidget(final EmitterTrackPanel parent, final EmitterTrackPanel.KeyRef key) {
        super(0, 0, RADIUS * 2 + 1, RADIUS * 2 + 1, Component.empty());

        this.parent = parent;
        this.key = key;
    }

    public EmitterTrackPanel.KeyRef key() {
        return key;
    }

    /// Recomputes the bounds from the key's time and the row's current top.
    public void updatePosition(final int rowTop, final int rowHeight) {
        setX(parent.frameToX((key.emitter().getStart() + key.time()) * 60.0F) - RADIUS);
        setY(rowTop + rowHeight / 2 - RADIUS);
    }

    /// The diamond's center, in screen coordinates.
    public int centerX() {
        return getX() + RADIUS;
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int centerY = getY() + RADIUS;
        int color = parent.isKeySelected(key)
            ? SELECTED_COLOR
            : key.channel() == ParticleEmitterHolder.KeyChannel.PIVOT ? PIVOT_COLOR : TRANSFORM_COLOR;

        for (int dy = -RADIUS; dy <= RADIUS; dy++) {
            int half = RADIUS - Math.abs(dy);

            g.fill(centerX() - half, centerY + dy, centerX() + half + 1, centerY + dy + 1, color);
        }
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1) {
            if (!parent.isKeySelected(key)) parent.clickKey(key, false, false);

            ModalManager.getInstance().openModalWidget(parent.keyMenu(), (int)event.x(), (int)event.y());

            return true;
        }

        if (event.button() != 0) return false;

        // A plain press on a selected key keeps the selection, so the drag moves all of it
        if (!parent.isKeySelected(key) || event.hasShiftDown() || event.hasControlDownWithQuirk()) {
            parent.clickKey(key, event.hasShiftDown(), event.hasControlDownWithQuirk());
        }

        if (parent.isKeySelected(key)) parent.beginKeyGrab(false, true, event.x());

        return true;
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput o) {
    }
}
