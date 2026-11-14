package com.yesman.akytheralabs.gui.widget.cinema;

import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.akythera.cinema.CinemaActorHolder;
import com.yesman.akytheralabs.akythera.cinema.CinemaMontagePreview;
import com.yesman.akytheralabs.gui.panel.main.processors.ResourceProcessor;
import com.yesman.akytheralabs.gui.widget.animation.TimelineTrackEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.List;

/// One actor on the cinema track: its role in the name block, and over the time axis a bar from the scene's start
/// to its montage's last frame, the fade-in darker, with the montage's id on it. A press selects the actor, and off the
/// name block also moves the playhead there ([CinemaTrackPanel#pressEmptyTrack]); a right-press also opens the actor
/// menu.
public final class CinemaActorRow extends TimelineTrackEntry {
    private static final int BAR_H = 14;
    private static final int BAR_COLOR = 0xFF4A86C8;
    private static final int FADE_IN_COLOR = 0xFF2F5A8A;
    private static final int SELECTED_COLOR = 0xFFFFEE55;
    private static final int SELECTED_BLOCK_COLOR = 0xFF385470;

    private final CinemaTrackPanel parent;
    private final CinemaActorHolder actor;

    public CinemaActorRow(final CinemaTrackPanel parent, final CinemaActorHolder actor) {
        this.parent = parent;
        this.actor = actor;
    }

    @Override
    protected Component rowLabel() {
        return Component.literal(actor.getRole());
    }

    @Override
    public void extractContent(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a) {
        super.extractContent(g, mouseX, mouseY, hovered, a);

        if (parent.getSelected() == actor) g.fill(getContentX(), getContentY(), getContentX() + 2, getContentY() + getContentHeight(), SELECTED_BLOCK_COLOR);
    }

    @Override
    protected void extractTrackArea(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        CinemaMontagePreview montage = CinemaMontagePreview.of(actor.getMontage());

        if (montage == null) return;

        Font font = Minecraft.getInstance().font;
        int x1 = parent.frameToX(0.0F);
        int fadeEnd = parent.frameToX(montage.fadeInTime() * 60.0F);
        int x2 = Math.max(x1 + 2, parent.frameToX(montage.length() * 60.0F));
        int y = getContentY() + (getContentHeight() - BAR_H) / 2;

        g.fill(x1, y, x2, y + BAR_H, BAR_COLOR);
        g.fill(x1, y, Math.min(fadeEnd, x2), y + BAR_H, FADE_IN_COLOR);

        if (parent.getSelected() == actor) {
            g.fill(x1 - 1, y - 1, x2 + 1, y, SELECTED_COLOR);
            g.fill(x1 - 1, y + BAR_H, x2 + 1, y + BAR_H + 1, SELECTED_COLOR);
            g.fill(x1 - 1, y, x1, y + BAR_H, SELECTED_COLOR);
            g.fill(x2, y, x2 + 1, y + BAR_H, SELECTED_COLOR);
        }

        g.enableScissor(x1, y, x2, y + BAR_H);
        g.text(font, ResourceProcessor.displayId(actor.getMontage().unwrapKey().orElseThrow()), x1 + 4, y + (BAR_H - font.lineHeight) / 2 + 1, 0xFFFFFFFF, false);
        g.disableScissor();
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1) {
            parent.clearKeySelection();
            parent.setSelected(actor);
            ModalManager.getInstance().openModalWidget(parent.actorMenu(), (int)event.x(), (int)event.y());

            return true;
        }

        if (event.button() != 0) return false;

        parent.pressEmptyTrack(event, actor);

        return true;
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return List.of();
    }
}
