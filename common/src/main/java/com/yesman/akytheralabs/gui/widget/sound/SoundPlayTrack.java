package com.yesman.akytheralabs.gui.widget.sound;

import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.ContextMenuContainer;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.ProjectSounds;
import com.yesman.akytheralabs.dataholder.sound.SoundEventHolder;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.ListRemove;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.UniversalMenu;
import com.yesman.akytheralabs.gui.widget.animation.AnimationTrackEditorPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.client.sounds.Weighted;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/// Plays the sounds of a [SoundEventHolder] one row at a time, laid out after
/// [AnimationTrackEditorPanel]: a seconds ruler, one row per sound with a bar as long as its file, a
/// playhead, and play / stop buttons. The playhead returns to 0 when the sound ends or is stopped.
/// `Delete`, or a row's right-click menu, removes the selected row's sound from the event. Pressing the
/// empty track deselects every row, and ▶ then plays a weighted pick as the game does. The selected row,
/// or the row the pick landed on, is drawn brighter.
public final class SoundPlayTrack extends AdjustablePanelManager.PanelArea {
    private static final float[] RULER_STEPS = {0.1F, 0.25F, 0.5F, 1.0F, 2.0F, 5.0F, 10.0F, 30.0F, 60.0F};
    private static final int MAX_RULER_TICKS = 10;

    private static final int HEADER_H = 22;
    private static final int ROW_H = 18;
    private static final int FOOTER_H = 20;
    private static final int NAME_W = 140;
    private static final int BUTTON_INSET = 2;
    private static final int CONTEXT_MENU_W = 120;

    /// Shortest timeline shown, in seconds.
    private static final float MIN_TIMELINE = 1.0F;

    private static final int BAR_COLOR = 0xFF2E6A9E;
    private static final int BAR_HIGHLIGHT_COLOR = 0xFF4A90D0;
    private static final int NAME_COLOR = 0xFFCCCCCC;
    private static final int NAME_HIGHLIGHT_COLOR = 0xFFFFFFFF;
    private static final int EVENT_LABEL_COLOR = 0xFF888888;
    private static final int EVENT_LABEL_HIGHLIGHT_COLOR = 0xFFBBBBBB;
    private static final int PLAYHEAD_COLOR = 0xFFFF4444;

    private final Font font;
    private final SoundEventHolder holder;
    private final Identifier eventId;

    /// The origin of the editor's viewer in its camera's space, where the preview plays from.
    private final Supplier<Vec3> listenerSpaceOrigin;

    /// Refreshes the owner after the event's sound list changed here.
    private final Runnable onSoundsChanged;

    private final AkytheraButton playButton;
    private final AkytheraButton stopButton;
    private final ContextMenuContainer contextMenu;

    /// Length of every sound file a row has asked for, by its location.
    private final Map<Identifier, CompletableFuture<Float>> durations = new HashMap<>();

    private @Nullable ScreenRectangle rect;

    /// The selected row, or -1 for none.
    private int selectedIndex;

    private ProjectSounds.@Nullable PreviewSoundInstance playing;

    /// Length of [#playing]'s file, in seconds. Completes with 0 when it can't be read.
    private CompletableFuture<Float> playingDuration = CompletableFuture.completedFuture(0.0F);

    /// Playhead, in seconds of the playing file.
    private float elapsed;

    /// The row a weighted pick is playing, or -1 while none is.
    private int pickedIndex = -1;

    public SoundPlayTrack(Font font, SoundEventHolder holder, Identifier eventId, Supplier<Vec3> listenerSpaceOrigin, Runnable onSoundsChanged) {
        this.font = font;
        this.holder = holder;
        this.eventId = eventId;
        this.listenerSpaceOrigin = listenerSpaceOrigin;
        this.onSoundsChanged = onSoundsChanged;

        int buttonSize = FOOTER_H - BUTTON_INSET * 2;

        this.playButton = AkytheraButton.buttonBuilder(font, LocalizedMessage.EMPTY, () -> play())
            .xUnits(AnchoredWidget.pixel(BUTTON_INSET), AnchoredWidget.pixel(buttonSize))
            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(buttonSize))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.CENTER_HEIGHT)
            .theme(AkytheraLabsButtonTheme.ANIM_TRACK_RESUME_BUTTON)
            .build();

        this.stopButton = AkytheraButton.buttonBuilder(font, LocalizedMessage.EMPTY, () -> stop())
            .xUnits(AnchoredWidget.pixel(BUTTON_INSET * 2 + buttonSize), AnchoredWidget.pixel(buttonSize))
            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(buttonSize))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.CENTER_HEIGHT)
            .theme(AkytheraLabsButtonTheme.SOUND_TRACK_STOP_BUTTON)
            .build();

        this.contextMenu = UniversalMenu.buildPage(
            List.of(new UniversalMenu.OptionRow<>(SoundTrackContextOption.DELETE)),
            UniversalMenu.<SoundTrackContextOption>actions().on(SoundTrackContextOption.DELETE, this::deleteSelected),
            CONTEXT_MENU_W
        );

        bgColor = 0; // drawn by this panel, under its header

        rebuildRows();
    }

    /// The sound of the selected row, or null when no row is selected.
    public SoundEventHolder.@Nullable SoundHolder selectedSound() {
        List<SoundEventHolder.SoundHolder> sounds = holder.getSounds();

        return selectedIndex >= 0 && selectedIndex < sounds.size() ? sounds.get(selectedIndex) : null;
    }

    /// Re-reads which rows exist, after the holder's sound list changed.
    public void rebuildRows() {
        clearWidgets();
        addWidget(playButton);
        addWidget(stopButton);

        for (int i = 0; i < holder.getSounds().size(); i++) {
            addWidget(new SoundRow(i));
        }

        selectedIndex = Math.min(selectedIndex, holder.getSounds().size() - 1);

        if (rect != null) onPanelResized(rect);
    }

    /// Plays the selected row from the start, or a row picked by weight when none is selected.
    public void play() {
        stop();

        List<SoundEventHolder.SoundHolder> sounds = holder.getSounds();
        int index = selectedSound() != null ? selectedIndex : -1;

        // No selection: a row picked by weight, the way [WeighedSoundEvents#getSound] picks
        if (index < 0) {
            int[] weights = new int[sounds.size()];
            int total = 0;

            for (int i = 0; i < sounds.size(); i++) {
                Weighted<Sound> weighted = ProjectSounds.makeEntry(sounds.get(i));
                weights[i] = weighted == null ? 0 : weighted.getWeight();
                total += weights[i];
            }

            if (total <= 0) return;

            int roll = RandomSource.create().nextInt(total);

            for (int i = 0; i < weights.length && index < 0; i++) {
                roll -= weights[i];
                if (roll < 0) index = i;
            }
        }

        Weighted<Sound> entry = ProjectSounds.makeEntry(sounds.get(index));

        if (entry == null) return;

        ProjectSounds.PreviewSoundInstance instance = new ProjectSounds.PreviewSoundInstance(eventId, entry, listenerSpaceOrigin.get());
        Minecraft.getInstance().getSoundManager().play(instance);

        Sound picked = instance.getSound();
        if (picked == null || picked == SoundManager.EMPTY_SOUND) return;

        playing = instance;
        playingDuration = durationOf(picked.getPath());

        if (selectedSound() == null) pickedIndex = index;
    }

    /// Stops the preview and returns the playhead to 0.
    public void stop() {
        if (playing != null) Minecraft.getInstance().getSoundManager().stop(playing);

        playing = null;
        pickedIndex = -1;
        elapsed = 0.0F;
    }

    /// Removes the selected row's sound from the event.
    private void deleteSelected() {
        List<SoundEventHolder.SoundHolder> sounds = holder.getSounds();
        int index = selectedIndex;

        if (index < 0 || index >= sounds.size()) return;

        stop();

        SoundEventHolder.SoundHolder removed = sounds.remove(index);

        HistoryManager.getInstance().pushHistory(new ListRemove<>(sounds, index, removed, onSoundsChanged));
        onSoundsChanged.run();
    }

    private CompletableFuture<Float> durationOf(Identifier location) {
        return durations.computeIfAbsent(location, ProjectSounds::durationOf);
    }

    /// Length of the file `sound` names, in seconds, or null while unknown — always for an event row,
    /// whose file is picked as it plays.
    private @Nullable Float fileDuration(SoundEventHolder.SoundHolder sound) {
        if (sound.getType() != Sound.Type.FILE) return null;

        Sound made = sound.makeSound();

        return made == null ? null : durationOf(made.getPath()).getNow(null);
    }

    /// The timeline's length in seconds: the longest known file with a margin after it.
    private float timelineEnd() {
        float longest = playing != null ? playingDuration.getNow(0.0F) : 0.0F;

        for (SoundEventHolder.SoundHolder sound : holder.getSounds()) {
            Float duration = fileDuration(sound);
            if (duration != null) longest = Math.max(longest, duration);
        }

        return Math.max(MIN_TIMELINE, longest * 1.1F);
    }

    private int trackStart() {
        return getX() + NAME_W;
    }

    private int secondsToX(float seconds) {
        return trackStart() + (int) (seconds / timelineEnd() * (getRight() - trackStart()));
    }

    @Override
    protected void onPanelResized(ScreenRectangle rect) {
        this.rect = rect;

        ScreenRectangle footerRect = new ScreenRectangle(rect.left(), rect.bottom() - FOOTER_H, rect.width(), FOOTER_H);
        playButton.relocate(footerRect);
        stopButton.relocate(footerRect);

        int y = rect.top() + HEADER_H;

        for (GuiEventListener child : children()) {
            if (!(child instanceof SoundRow row)) continue;

            row.setRectangle(rect.width(), ROW_H, rect.left(), y);
            row.visible = y + ROW_H <= rect.bottom() - FOOTER_H;
            y += ROW_H;
        }
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (playing != null) {
            elapsed += a * 0.05F * Mth.clamp(playing.getPitch(), 0.5F, 2.0F); // the clamp SoundEngine plays it at
            ProjectSounds.moveSound(playing, listenerSpaceOrigin.get());

            Float duration = playingDuration.getNow(null);
            if (duration != null && elapsed >= duration) stop();
        }

        playButton.active = !holder.getSounds().isEmpty();

        graphics.fill(getX(), getY(), getRight(), getBottom(), 0xFF1E1E1E);

        // Header with the seconds ruler
        int headerBottom = getY() + HEADER_H;
        int footerTop = getBottom() - FOOTER_H;
        float end = timelineEnd();
        float step = RULER_STEPS[RULER_STEPS.length - 1];

        for (float candidate : RULER_STEPS) {
            if (end / candidate <= MAX_RULER_TICKS) {
                step = candidate;
                break;
            }
        }

        graphics.fill(getX(), getY(), getRight(), headerBottom, 0xFF252525);
        graphics.fill(getX(), headerBottom - 1, getRight(), headerBottom, 0xFF404040);
        graphics.text(font, Component.translatable(LangKeys.GUI_SOUND_ASSET_SCREEN_TRACK_TITLE), getX() + 6, getY() + (HEADER_H - font.lineHeight) / 2, 0xFFCCCCCC, false);

        for (int i = 0; i * step <= end; i++) {
            int x = secondsToX(i * step);

            graphics.centeredText(font, String.format("%.2f", i * step).replaceAll("\\.?0+$", ""), x, headerBottom - font.lineHeight - 2, 0xFF999999);
            graphics.fill(x, headerBottom, x + 1, footerTop, 0xFF2A2A2A);
        }

        graphics.fill(trackStart() - 1, headerBottom, trackStart(), footerTop, 0xFF404040);

        // Footer
        graphics.fill(getX(), footerTop, getRight(), getBottom(), 0xFF252525);
        graphics.fill(getX(), footerTop, getRight(), footerTop + 1, 0xFF404040);

        Float duration = playing != null ? playingDuration.getNow(null) : null;
        String time = String.format("%.2f / %s s", elapsed, duration == null ? "-" : String.format("%.2f", duration));
        graphics.text(font, time, stopButton.getRight() + 6, footerTop + (FOOTER_H - font.lineHeight) / 2, 0xFFAAAAAA, false);

        super.extractWidgetRenderState(graphics, mouseX, mouseY, a);

        // Playhead over the rows
        int playheadX = secondsToX(elapsed);
        graphics.fill(playheadX, headerBottom, playheadX + 1, footerTop, PLAYHEAD_COLOR);
    }

    /// A press missing every row and button deselects the rows.
    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (!isMouseOver(event.x(), event.y())) return false;

        selectedIndex = -1;

        return true;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (EditorKeyMappings.DELETE.matches(event) && selectedSound() != null) {
            deleteSelected();
            return true;
        }

        return super.keyPressed(event);
    }

    /// One sound of the event: its name, and a bar as long as its file.
    private final class SoundRow extends AbstractWidget {
        private final int index;

        SoundRow(int index) {
            super(0, 0, 0, ROW_H, Component.empty());

            this.index = index;
        }

        @Override
        protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            List<SoundEventHolder.SoundHolder> sounds = holder.getSounds();
            if (index >= sounds.size()) return;

            SoundEventHolder.SoundHolder sound = sounds.get(index);
            boolean selected = index == selectedIndex;
            boolean highlighted = selected || index == pickedIndex;

            graphics.fill(getX(), getY(), trackStart() - 1, getBottom(), selected ? 0xFF1E4D72 : isHovered() ? 0xFF2A2A2A : 0xFF202020);
            graphics.fill(getX(), getBottom() - 1, getRight(), getBottom(), 0xFF2A2A2A);

            String label = font.plainSubstrByWidth("#" + index + " " + sound.getName(), NAME_W - 8);
            graphics.text(font, label, getX() + 4, getY() + (ROW_H - font.lineHeight) / 2, highlighted ? NAME_HIGHLIGHT_COLOR : NAME_COLOR, false);

            Float duration = fileDuration(sound);

            if (duration != null && duration > 0.0F) {
                graphics.fill(trackStart(), getY() + 3, secondsToX(duration), getBottom() - 3, highlighted ? BAR_HIGHLIGHT_COLOR : BAR_COLOR);
            } else if (sound.getType() == Sound.Type.SOUND_EVENT) {
                graphics.text(
                    font,
                    Component.translatable(LangKeys.GUI_SOUND_ASSET_SCREEN_TRACK_EVENT),
                    trackStart() + 4, getY() + (ROW_H - font.lineHeight) / 2,
                    highlighted ? EVENT_LABEL_HIGHLIGHT_COLOR : EVENT_LABEL_COLOR,
                    false
                );
            }
        }

        /// Takes the right press too, which opens the row's menu.
        @Override
        protected boolean isValidClickButton(@NonNull MouseButtonInfo buttonInfo) {
            return buttonInfo.button() == 0 || buttonInfo.button() == 1;
        }

        @Override
        public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {
            selectedIndex = index;

            if (event.button() == 1) {
                ModalManager.getInstance().openModalWidget(contextMenu, (int) event.x(), (int) event.y());
            }
        }

        @Override
        public void playDownSound(@NonNull SoundManager soundManager) {
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
        }
    }
}
