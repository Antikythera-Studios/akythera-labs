package com.yesman.akytheralabs.gui.widget.particle;

import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.akythera.particle.ParticleEmitterHolder;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.animation.AnimTrackEntry;
import com.yesman.akytheralabs.gui.widget.animation.AnimationTrackList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.List;

/// One emitter on the emitter track: its name in the name block, and its [EmitterSpanWidget] over the time axis. A
/// press selects the emitter, and off the span also moves the playhead there and starts a box selection
/// ([EmitterTrackPanel#pressEmptyTrack]); a right-press also opens the add / delete menu. A flagged emitter
/// ([EmitterTrackPanel#isFlagged]) shows a red `!` that explains itself on hover.
public final class EmitterRow extends AnimTrackEntry {
    private static final int SELECTED_BLOCK_COLOR = 0xFF385470;
    private static final int ERROR_COLOR = 0xFFFF5555;

    /// How far the error mark sits left of the name block's right edge, in pixels.
    private static final int ERROR_MARK_RIGHT = 14;

    private final EmitterTrackPanel parent;
    private final ParticleEmitterHolder emitter;
    private final EmitterSpanWidget span;

    public EmitterRow(final EmitterTrackPanel parent, final ParticleEmitterHolder emitter) {
        this.parent = parent;
        this.emitter = emitter;
        this.span = new EmitterSpanWidget(parent, emitter);
    }

    @Override
    protected Component rowLabel() {
        return Component.literal(emitter.getName());
    }

    @Override
    public void extractContent(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a) {
        super.extractContent(g, mouseX, mouseY, hovered, a);

        if (parent.getSelected() == emitter) g.fill(getContentX(), getContentY(), getContentX() + 2, getContentY() + getContentHeight(), SELECTED_BLOCK_COLOR);

        if (parent.isFlagged(emitter)) {
            Font font = Minecraft.getInstance().font;
            int markX = getContentX() + AnimationTrackList.NAME_BLOCK_W - ERROR_MARK_RIGHT;
            int markY = getContentY() + (getContentHeight() - font.lineHeight) / 2;

            g.text(font, "!", markX, markY, ERROR_COLOR, false);

            if (hovered && mouseX < getContentX() + AnimationTrackList.NAME_BLOCK_W) {
                g.setTooltipForNextFrame(font, font.split(Component.translatable(LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_ERROR_NO_PARTICLE), 220), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void extractTrackArea(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        span.updatePosition(getContentY(), getContentHeight());
        span.extractRenderState(g, mouseX, mouseY, a);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1) {
            parent.clearKeySelection();
            parent.setSelected(emitter);
            ModalManager.getInstance().openModalWidget(parent.emitterMenu(), (int)event.x(), (int)event.y());

            return true;
        }

        if (event.x() >= parent.getTrackStart() && span.isMouseOver(event.x(), event.y()) && span.mouseClicked(event, doubleClick)) {
            parent.clearKeySelection();
            setFocused(span);
            setDragging(true);

            return true;
        }

        if (event.button() != 0) return false;

        parent.pressEmptyTrack(event, emitter);

        return true;
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return List.of(span);
    }
}
