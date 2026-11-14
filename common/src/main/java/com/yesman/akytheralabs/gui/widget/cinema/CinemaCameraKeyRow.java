package com.yesman.akytheralabs.gui.widget.cinema;

import com.yesman.akythera.core.blueprint.particle.KeyframeTrack;
import com.yesman.akytheralabs.akythera.cinema.CinemaActorHolder;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.animation.TimelineList;
import com.yesman.akytheralabs.gui.widget.animation.TimelineTrackEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.List;

/// The camera keys of an actor on the cinema track, under the actor's row: one [CinemaCameraKeyWidget] per key over
/// the time axis. A press off the keys selects the actor and moves the playhead there
/// ([CinemaTrackPanel#pressEmptyTrack]).
public final class CinemaCameraKeyRow extends TimelineTrackEntry {
    private static final int LABEL_INDENT = 20;
    private static final int BLOCK_COLOR = 0xFF2E2E2E;

    private final CinemaTrackPanel parent;
    private final CinemaActorHolder actor;

    /// One widget per camera key, in the keys' order.
    private List<CinemaCameraKeyWidget> keys = List.of();

    public CinemaCameraKeyRow(final CinemaTrackPanel parent, final CinemaActorHolder actor) {
        this.parent = parent;
        this.actor = actor;
    }

    @Override
    protected Component rowLabel() {
        return Component.translatable(LangKeys.GUI_CINEMA_SCENE_SCREEN_WIDGET_CAMERA);
    }

    @Override
    public void extractContent(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a) {
        Font font = Minecraft.getInstance().font;
        int rowLeft = getContentX();
        int rowTop = getContentY();
        int rowH = getContentHeight();

        g.fill(rowLeft, rowTop, rowLeft + TimelineList.NAME_BLOCK_W - 4, rowTop + rowH, BLOCK_COLOR);
        g.text(font, rowLabel(), rowLeft + LABEL_INDENT, rowTop + (rowH - font.lineHeight) / 2, 0xFF999999, false);
    }

    @Override
    protected void extractTrackArea(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        List<Float> times = actor.getCameraKeys().keys().stream().map(KeyframeTrack.Key::time).toList();

        if (!times.equals(keys.stream().map(key -> key.key().time()).toList())) {
            keys = times.stream().map(time -> new CinemaCameraKeyWidget(parent, new CinemaTrackPanel.KeyRef(actor, time))).toList();
        }

        for (CinemaCameraKeyWidget key : keys) {
            key.updatePosition(getContentY(), getContentHeight());
            key.extractRenderState(g, mouseX, mouseY, a);
        }
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.x() >= parent.getTrackStart() && super.mouseClicked(event, doubleClick)) return true;

        if (event.button() == 0) {
            parent.pressEmptyTrack(event, actor);
        } else if (event.button() == 1) {
            parent.clearKeySelection();
            parent.setSelected(actor);
        }

        return event.button() == 0 || event.button() == 1;
    }

    /// The keys, the one drawn last first.
    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return keys.reversed();
    }
}
