package com.yesman.akytheralabs.gui.widget.particle;

import com.yesman.akythera.core.blueprint.particle.KeyframeTrack;
import com.yesman.akytheralabs.akythera.particle.ParticleEmitterHolder;
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

/// One keyed channel of an emitter on the emitter track, under the emitter's row: one [EmitterKeyWidget] per key over
/// the time axis, on a faint band where the time span lets keys live. A press off the keys selects the emitter and
/// starts a box selection ([EmitterTrackPanel#pressEmptyTrack]).
public final class EmitterKeyRow extends TimelineTrackEntry {
    private static final int LABEL_INDENT = 20;
    private static final int BLOCK_COLOR = 0xFF2E2E2E;
    private static final int SPAN_BAND_COLOR = 0x22FFFFFF;

    private final EmitterTrackPanel parent;
    private final ParticleEmitterHolder emitter;
    private final ParticleEmitterHolder.KeyChannel channel;

    /// One widget per key of the channel, in the keys' order.
    private List<EmitterKeyWidget> keys = List.of();

    public EmitterKeyRow(final EmitterTrackPanel parent, final ParticleEmitterHolder emitter, final ParticleEmitterHolder.KeyChannel channel) {
        this.parent = parent;
        this.emitter = emitter;
        this.channel = channel;
    }

    @Override
    protected Component rowLabel() {
        return Component.translatable(channel == ParticleEmitterHolder.KeyChannel.PIVOT ? LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_WIDGET_PIVOT : LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_WIDGET_TRANSFORM);
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
        int centerY = getContentY() + getContentHeight() / 2;

        if (emitter.getSpawnType() == ParticleEmitterHolder.SpawnType.SPAWN_RATE) {
            int x1 = parent.frameToX(emitter.getStart() * 60.0F);
            int x2 = parent.frameToX((emitter.getStart() + emitter.getDuration()) * 60.0F);

            g.fill(x1, centerY - EmitterKeyWidget.RADIUS, x2, centerY + EmitterKeyWidget.RADIUS + 1, SPAN_BAND_COLOR);
        }

        List<Float> times = emitter.getKeys(channel).keys().stream().map(KeyframeTrack.Key::time).toList();

        if (!times.equals(keys.stream().map(key -> key.key().time()).toList())) {
            keys = times.stream().map(time -> new EmitterKeyWidget(parent, new EmitterTrackPanel.KeyRef(emitter, channel, time))).toList();
        }

        for (EmitterKeyWidget key : keys) {
            key.updatePosition(getContentY(), getContentHeight());
            key.extractRenderState(g, mouseX, mouseY, a);
        }
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.x() >= parent.getTrackStart() && super.mouseClicked(event, doubleClick)) return true;

        if (event.button() == 0) {
            parent.pressEmptyTrack(event, emitter);
        } else if (event.button() == 1) {
            parent.clearKeySelection();
            parent.setSelected(emitter);
        }

        return event.button() == 0 || event.button() == 1;
    }

    /// This row's keys inside the screen rectangle `x1` / `y1` to `x2` / `y2`.
    public List<EmitterTrackPanel.KeyRef> keysIn(final double x1, final double y1, final double x2, final double y2) {
        if (y2 < getContentY() || y1 > getContentY() + getContentHeight()) return List.of();

        return keys.stream().filter(key -> key.centerX() >= x1 && key.centerX() <= x2).map(EmitterKeyWidget::key).toList();
    }

    /// The keys, the one drawn last first.
    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return keys.reversed();
    }
}
