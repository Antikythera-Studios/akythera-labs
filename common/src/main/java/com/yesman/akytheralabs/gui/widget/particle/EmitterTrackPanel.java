package com.yesman.akytheralabs.gui.widget.particle;

import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.ContextMenuContainer;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.core.blueprint.particle.KeyframeTrack;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akytheralabs.akythera.particle.ParticleEmitterHolder;
import com.yesman.akytheralabs.akythera.particle.ParticleSystemHolder;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.ListInsert;
import com.yesman.akytheralabs.editor.historytrack.ListRemove;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.animation.CurrentFrameIndicator;
import com.yesman.akytheralabs.gui.widget.animation.FrameScrollBar;
import com.yesman.akytheralabs.gui.widget.animation.TimelineList;
import com.yesman.akytheralabs.gui.widget.animation.TimelineTrackEntry;
import com.yesman.akytheralabs.gui.widget.animation.TrackTimeline;
import com.yesman.akytheralabs.gui.widget.viewer.ParticleSystemPreviewComponent;
import com.yesman.akythera.core.util.helper.MathUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/// The emitter track of a particle system: one [EmitterRow] per emitter over a time axis in seconds, each followed by
/// an [EmitterKeyRow] for its pivot keys and one for its emitter transform keys. `+` / `-` buttons and a right-press
/// menu add and delete emitters, and play / pause / stop sit under it.
///
/// [EditorKeyMappings#INSERT_KEYFRAME] keys the selected emitter at the playhead ([#openInsertMenu]). Keys are
/// selected like rows of a list or by a [KeySelectionBox] dragged from empty track space, dragged along the time
/// axis, copied and pasted at the playhead, and deleted with the Delete key or the key menu. [EditorKeyMappings#MOVE_KEYFRAME] takes
/// hold of the selected keys and [EditorKeyMappings#DUPLICATE_KEYFRAME] of copies of them: they follow the cursor
/// anywhere on the time axis, growing time spans to them, until a left press drops them or any other press cancels
/// ([#cancelKeyGrab]). A press on empty track space released without a drag moves the playhead there. Whenever the
/// playhead moves, each keyed transform field takes its keyed value there.
///
/// Particles play only through the play button, in the [ParticleSystemPreviewComponent]. Dragging the playhead, or
/// pressing its row, freezes the particles and seeks. The red line is where the preview ends, see
/// [PreviewEndMarker]. A system with a looping emitter has none.
public final class EmitterTrackPanel extends AdjustablePanelManager.PanelArea implements TrackTimeline {
    private static final float[] RULER_STEPS = { 0.05F, 0.1F, 0.25F, 0.5F, 1.0F, 2.0F, 5.0F, 10.0F, 30.0F, 60.0F };
    private static final int MAX_VISIBLE_RULER_STEPS = 12;

    private static final int HEADER_H = 38;
    private static final int FOOTER_H = 20;
    private static final int BUTTON = 16;
    private static final int TITLE_X = 6;
    private static final int TITLE_Y = 22;
    private static final int FOOTER_INSET = 2;
    private static final int DIVIDER_W = 2;

    /// The shortest timeline, in seconds, so an empty or instant-only system still has room.
    private static final float MIN_TIMELINE = 1.0F;

    /// The top of the red line's grip, below the playhead's row, in pixels from the panel's top.
    private static final int END_GRIP_TOP = 24;

    /// How far a key may sit from where a paste or a grab put it and still be that key, in seconds.
    private static final float KEY_MATCH = 1.0E-3F;

    private final ParticleSystemHolder holder;
    private final ParticleSystemPreviewComponent preview;

    /// Told the selected emitter, or null when none is, whenever the selection changes or a drag edits it.
    private final Consumer<@Nullable ParticleEmitterHolder> onSelectionChanged;

    /// Told this panel whenever the rows are built again from the emitters, after an emitter is added or removed.
    private final Consumer<EmitterTrackPanel> onEmittersChanged;

    private final TimelineList trackList = new TimelineList();
    private final CurrentFrameIndicator frameIndicator;
    private final FrameScrollBar scrollBar;
    private final PreviewEndMarker endMarker;
    private final AkytheraButton addButton;
    private final AkytheraButton deleteButton;
    private final AkytheraButton playButton;
    private final AkytheraButton pauseButton;
    private final AkytheraButton stopButton;
    private final ContextMenuContainer emitterMenu;
    private final ContextMenuContainer insertMenu;
    private final ContextMenuContainer keyMenu;

    /// Emitters with no particle when the screen opened or last saved, marked as errors while they still have none.
    private final Set<ParticleEmitterHolder> flagged = Collections.newSetFromMap(new IdentityHashMap<>());

    private final Set<KeyRef> selectedKeys = new HashSet<>();

    /// The key a shift-press selects a range from.
    private @Nullable KeyRef keyAnchor;

    /// The playhead the keyed transforms were last written at, in seconds. NaN writes them again.
    private float syncedTime = Float.NaN;

    /// The keys last copied.
    private List<CopiedKey> clipboard = List.of();

    /// The keys being moved along the time axis, or null.
    private @Nullable KeyGrab grab;

    /// The box selection being dragged, or null.
    private @Nullable KeySelectionBox box;

    /// The cursor's x at the last frame.
    private int lastMouseX;

    private float visualFrameStart;
    private float visualFrameEnd;
    private @Nullable ParticleEmitterHolder selected;

    public EmitterTrackPanel(
        final ParticleSystemHolder holder,
        final ParticleSystemPreviewComponent preview,
        final Consumer<@Nullable ParticleEmitterHolder> onSelectionChanged,
        final Consumer<EmitterTrackPanel> onEmittersChanged
    ) {
        this.holder = holder;
        this.preview = preview;
        this.onSelectionChanged = onSelectionChanged;
        this.onEmittersChanged = onEmittersChanged;
        this.frameIndicator = new CurrentFrameIndicator(this);
        this.scrollBar = new FrameScrollBar(this);
        this.endMarker = new PreviewEndMarker(this, holder);

        Font font = Minecraft.getInstance().font;
        int buttonTop = HEADER_H - BUTTON - 4;
        int footerButton = FOOTER_H - FOOTER_INSET * 2;

        this.addButton = headerButton(font, TimelineList.NAME_BLOCK_W - (BUTTON + 6) * 2, buttonTop, AkytheraLabsButtonTheme.MONTAGE_SEQ_ADD_BUTTON, this::addEmitter);
        this.deleteButton = headerButton(font, TimelineList.NAME_BLOCK_W - (BUTTON + 6), buttonTop, AkytheraLabsButtonTheme.MONTAGE_SEQ_REMOVE_BUTTON, () -> {
            if (selected != null) deleteEmitter(selected);
        });
        this.playButton = footerButton(font, FOOTER_INSET, footerButton, AkytheraLabsButtonTheme.ANIM_TRACK_RESUME_BUTTON, preview::play);
        this.pauseButton = footerButton(font, FOOTER_INSET, footerButton, AkytheraLabsButtonTheme.ANIM_TRACK_PAUSE_BUTTON, preview::pause);
        this.stopButton = footerButton(font, FOOTER_INSET * 2 + footerButton, footerButton, AkytheraLabsButtonTheme.SOUND_TRACK_STOP_BUTTON, preview::stop);
        this.emitterMenu = ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(120)
            .entry()
                .message(Component.translatable(LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_MENU_ADD_EMITTER))
                .onPress(_ -> addEmitter())
                .done()
            .entry()
                .message(Component.translatable(LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_MENU_DELETE_EMITTER))
                .onPress(_ -> {
                    if (selected != null) deleteEmitter(selected);
                })
                .done()
            .build();
        this.insertMenu = ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(120)
            .entry()
                .message(Component.translatable(LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_MENU_INSERT_PIVOT_KEY))
                .onPress(_ -> insertKey(ParticleEmitterHolder.KeyChannel.PIVOT))
                .done()
            .entry()
                .message(Component.translatable(LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_MENU_INSERT_TRANSFORM_KEY))
                .onPress(_ -> insertKey(ParticleEmitterHolder.KeyChannel.TRANSFORM))
                .done()
            .build();
        this.keyMenu = ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(120)
            .entry()
                .message(Component.translatable(LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_MENU_DELETE_KEYFRAMES))
                .onPress(_ -> deleteSelectedKeys())
                .done()
            .build();

        float length = timelineFrames();
        visualFrameStart = -length * 0.1F;
        visualFrameEnd = length * 1.1F;

        addWidget(trackList);
        addWidget(frameIndicator);
        addWidget(addButton);
        addWidget(deleteButton);
        addWidget(playButton);
        addWidget(pauseButton);
        addWidget(stopButton);
        addWidget(scrollBar);

        bgColor = 0;

        rebuildEntries();
        flagEmittersWithoutParticle();
    }

    private static AkytheraButton headerButton(final Font font, final int x, final int y, final AkytheraLabsButtonTheme theme, final Runnable onPress) {
        return AkytheraButton.buttonBuilder(font, LocalizedMessage.EMPTY, onPress)
            .xUnits(AnchoredWidget.pixel(x), AnchoredWidget.pixel(BUTTON))
            .yUnits(AnchoredWidget.pixel(y), AnchoredWidget.pixel(BUTTON))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .theme(theme)
            .build();
    }

    private static AkytheraButton footerButton(final Font font, final int x, final int size, final AkytheraLabsButtonTheme theme, final Runnable onPress) {
        return AkytheraButton.buttonBuilder(font, LocalizedMessage.EMPTY, onPress)
            .xUnits(AnchoredWidget.pixel(x), AnchoredWidget.pixel(size))
            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(size))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.CENTER_HEIGHT)
            .theme(theme)
            .build();
    }

    public void rebuildEntries() {
        trackList.rebuild(holder.getEmitters().stream().<TimelineTrackEntry>flatMap(emitter -> Stream.of(
            new EmitterRow(this, emitter),
            new EmitterKeyRow(this, emitter, ParticleEmitterHolder.KeyChannel.PIVOT),
            new EmitterKeyRow(this, emitter, ParticleEmitterHolder.KeyChannel.TRANSFORM)
        )).toList());
        onEmittersChanged.accept(this);

        if (selected != null && !holder.getEmitters().contains(selected)) setSelected(null);
    }

    /// Appends a new emitter, selects it, and pushes one undo step.
    public void addEmitter() {
        ParticleEmitterHolder added = holder.newEmitter();

        holder.getEmitters().add(added);
        HistoryManager.getInstance().pushHistory(new ListInsert<>(holder.getEmitters(), holder.getEmitters().size() - 1, added, this::rebuildEntries));
        rebuildEntries();
        setSelected(added);
    }

    /// Removes `emitter` and pushes one undo step.
    public void deleteEmitter(final ParticleEmitterHolder emitter) {
        int index = holder.getEmitters().indexOf(emitter);

        if (index < 0) return;

        holder.getEmitters().remove(index);
        HistoryManager.getInstance().pushHistory(new ListRemove<>(holder.getEmitters(), index, emitter, this::rebuildEntries));
        rebuildEntries();
    }

    /// Marks the emitters that have no particle now as errors.
    public void flagEmittersWithoutParticle() {
        flagged.clear();
        holder.getEmitters().stream().filter(emitter -> !emitter.hasParticle()).forEach(flagged::add);
    }

    /// Whether `emitter` is marked as an error: it had no particle when flagged, and still has none.
    public boolean isFlagged(final ParticleEmitterHolder emitter) {
        return flagged.contains(emitter) && !emitter.hasParticle();
    }

    /// Opens the menu that keys the selected emitter's pivot or emitter transform at the playhead, at `x` / `y`. Only a
    /// [ParticleEmitterHolder.SpawnType#SPAWN_RATE] emitter takes keys.
    ///
    /// @return whether the menu opened
    public boolean openInsertMenu(final int x, final int y) {
        if (selected == null || selected.getSpawnType() != ParticleEmitterHolder.SpawnType.SPAWN_RATE) return false;

        ModalManager.getInstance().openModalWidget(insertMenu, x, y);

        return true;
    }

    /// Keys `channel` of the selected emitter at the playhead, as one undo step.
    private void insertKey(final ParticleEmitterHolder.KeyChannel channel) {
        if (selected == null) return;

        Map<ParticleEmitterHolder, ParticleEmitterHolder.Timeline> before = Map.of(selected, selected.getTimeline());

        selected.insertKey(channel, preview.time());

        // Growing the span left moves every key in it, so the selected times no longer name them
        selectedKeys.clear();
        keyAnchor = null;
        pushTimelineChange(before);
    }

    /// Deletes the selected keys, as one undo step.
    public void deleteSelectedKeys() {
        Map<ParticleEmitterHolder, ParticleEmitterHolder.Timeline> before = new IdentityHashMap<>();

        for (KeyRef key : selectedKeys) {
            before.putIfAbsent(key.emitter(), key.emitter().getTimeline());
            key.emitter().removeKey(key.channel(), key.time());
        }

        selectedKeys.clear();
        keyAnchor = null;
        pushTimelineChange(before);
    }

    /// Pushes the change from `before` to each emitter's timeline now as one undo step.
    private void pushTimelineChange(final Map<ParticleEmitterHolder, ParticleEmitterHolder.Timeline> before) {
        Map<ParticleEmitterHolder, ParticleEmitterHolder.Timeline> after = new IdentityHashMap<>();

        before.keySet().forEach(emitter -> after.put(emitter, emitter.getTimeline()));
        HistoryManager.getInstance().pushHistory(new WorkHistory() {
            @Override
            public void undo() {
                before.forEach(ParticleEmitterHolder::setTimeline);
                syncedTime = Float.NaN;
                refreshSelectionView();
            }

            @Override
            public void redo() {
                after.forEach(ParticleEmitterHolder::setTimeline);
                syncedTime = Float.NaN;
                refreshSelectionView();
            }
        });
        refreshSelectionView();
    }

    public boolean isKeySelected(final KeyRef key) {
        return selectedKeys.contains(key);
    }

    /// Selects `key` and its emitter. Shift selects the keys of the same channel between the last key pressed and
    /// `key`; control adds or removes `key`; neither selects `key` alone.
    public void clickKey(final KeyRef key, final boolean shift, final boolean control) {
        if (shift && keyAnchor != null && keyAnchor.emitter() == key.emitter() && keyAnchor.channel() == key.channel()) {
            float from = Math.min(keyAnchor.time(), key.time());
            float to = Math.max(keyAnchor.time(), key.time());

            selectedKeys.clear();
            key.emitter().getKeys(key.channel()).keys().stream()
                .filter(keyframe -> keyframe.time() >= from && keyframe.time() <= to)
                .forEach(keyframe -> selectedKeys.add(new KeyRef(key.emitter(), key.channel(), keyframe.time())));
        } else if (control) {
            if (!selectedKeys.remove(key)) selectedKeys.add(key);

            keyAnchor = key;
        } else {
            selectedKeys.clear();
            selectedKeys.add(key);
            keyAnchor = key;
        }

        if (selected != key.emitter()) setSelected(key.emitter());
    }

    public void clearKeySelection() {
        selectedKeys.clear();
        keyAnchor = null;
    }

    /// A left press on track space with nothing under it: selects `emitter` (none when null), and right of the name
    /// block starts a box selection. Released without a drag, the playhead moves there instead. Shift or control keeps
    /// the selected keys.
    public void pressEmptyTrack(final MouseButtonEvent event, final @Nullable ParticleEmitterHolder emitter) {
        if (!event.hasShiftDown() && !event.hasControlDownWithQuirk()) clearKeySelection();
        if (selected != emitter) setSelected(emitter);

        if (event.x() >= getTrackStart()) box = new KeySelectionBox(event.x(), event.y(), Set.copyOf(selectedKeys));
    }

    /// Takes hold of the selected keys at the cursor, to move them along the time axis.
    ///
    /// @param duplicate whether copies move while the selected keys stay
    /// @param held      whether the left button holds the grab, which then ends when it is released and keeps the keys
    ///                  inside their time spans. Otherwise the next press ends it, and time spans grow to the keys
    public void beginKeyGrab(final boolean duplicate, final boolean held, final double mouseX) {
        Map<ParticleEmitterHolder, ParticleEmitterHolder.Timeline> before = new IdentityHashMap<>();
        List<GrabbedKey> keys = new ArrayList<>();

        for (KeyRef key : selectedKeys) {
            for (KeyframeTrack.Key<Transform> keyframe : key.emitter().getKeys(key.channel()).keys()) {
                if (keyframe.time() != key.time()) continue;

                before.putIfAbsent(key.emitter(), key.emitter().getTimeline());
                keys.add(new GrabbedKey(key.emitter(), key.channel(), keyframe.time(), key.emitter().getStart() + keyframe.time(), keyframe.value()));
            }
        }

        if (!keys.isEmpty()) grab = new KeyGrab(before, keys, xToFrame(mouseX) / 60.0F, duplicate, held);
    }

    /// Moves the grabbed keys as far as the cursor moved since the grab began, never before the system's start. A held
    /// grab keeps them inside their time spans; any other grows the spans to them.
    private void moveGrab(final KeyGrab grab, final double mouseX) {
        float delta = xToFrame(mouseX) / 60.0F - grab.grabTime();

        grab.before().forEach(ParticleEmitterHolder::setTimeline);

        for (GrabbedKey key : grab.keys()) {
            float earliest = grab.held() ? key.emitter().getStart() : 0.0F;
            float latest = grab.held() ? key.emitter().getStart() + key.emitter().getDuration() : Float.MAX_VALUE;

            delta = Math.clamp(delta, earliest - key.seconds(), latest - key.seconds());
        }

        if (!grab.duplicate()) grab.keys().forEach(key -> key.emitter().removeKey(key.channel(), key.time()));

        for (GrabbedKey key : grab.keys()) {
            key.emitter().insertKey(key.channel(), key.seconds() + delta, key.value());
        }

        // The moved keys are selected, by the times they hold once every span has grown
        float moved = delta;

        selectedKeys.clear();

        for (GrabbedKey key : grab.keys()) {
            key.emitter().getKeys(key.channel()).keys().stream()
                .filter(keyframe -> Math.abs(key.emitter().getStart() + keyframe.time() - key.seconds() - moved) < KEY_MATCH)
                .forEach(keyframe -> selectedKeys.add(new KeyRef(key.emitter(), key.channel(), keyframe.time())));
        }

        syncedTime = Float.NaN;
    }

    /// Ends the grab: `commit` keeps the keys where they are as one undo step, otherwise they go back.
    private void endGrab(final KeyGrab grab, final boolean commit) {
        this.grab = null;

        if (commit) {
            // A grab that moved nothing is a plain press, which keeps the key a shift-press ranges from
            if (grab.before().entrySet().stream().anyMatch(entry -> !entry.getKey().getTimeline().equals(entry.getValue()))) {
                keyAnchor = null;
                pushTimelineChange(grab.before());
            }

            return;
        }

        keyAnchor = null;
        grab.before().forEach(ParticleEmitterHolder::setTimeline);
        selectedKeys.clear();
        grab.keys().forEach(key -> selectedKeys.add(new KeyRef(key.emitter(), key.channel(), key.time())));
        syncedTime = Float.NaN;
        refreshSelectionView();
    }

    /// Puts the keys of a grab in progress back.
    ///
    /// @return whether there was a grab
    public boolean cancelKeyGrab() {
        if (grab == null) return false;

        endGrab(grab, false);

        return true;
    }

    /// The right-press menu of a key.
    public ContextMenuContainer keyMenu() {
        return keyMenu;
    }

    /// The right-press menu of an emitter row.
    public ContextMenuContainer emitterMenu() {
        return emitterMenu;
    }

    public @Nullable ParticleEmitterHolder getSelected() {
        return selected;
    }

    /// Selects `emitter`. Selecting none also drops the key selection.
    public void setSelected(final @Nullable ParticleEmitterHolder emitter) {
        selected = emitter;

        if (emitter == null) clearKeySelection();

        onSelectionChanged.accept(emitter);
    }

    /// Tells the selection listener again, so the Details rows re-read an emitter a drag edits.
    public void refreshSelectionView() {
        onSelectionChanged.accept(selected);
    }

    public int getTrackStart() {
        return getX() + TimelineList.NAME_BLOCK_W + DIVIDER_W;
    }

    @Override
    protected void onPanelResized(final ScreenRectangle rect) {
        ScreenRectangle headerRect = new ScreenRectangle(rect.left(), rect.top(), rect.width(), HEADER_H);
        ScreenRectangle footerRect = new ScreenRectangle(rect.left(), rect.bottom() - FOOTER_H, TimelineList.NAME_BLOCK_W, FOOTER_H);

        addButton.relocate(headerRect);
        deleteButton.relocate(headerRect);
        trackList.updateSizeAndPosition(rect.width(), Math.max(0, rect.height() - HEADER_H - FOOTER_H), rect.left(), rect.top() + HEADER_H);
        frameIndicator.setBounds(rect.left(), rect.top(), rect.width(), rect.height() - FOOTER_H);
        playButton.relocate(footerRect);
        pauseButton.relocate(footerRect);
        stopButton.relocate(footerRect);
        scrollBar.relocate(rect);
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        lastMouseX = mouseX;

        if (grab != null && !grab.held()) moveGrab(grab, mouseX);

        float playhead = preview.time();

        if (playhead != syncedTime) {
            syncedTime = playhead;
            holder.getEmitters().forEach(emitter -> emitter.moveToTime(playhead));

            // Details re-reads a keyed transform of the selected emitter, except while playing, which moves it every frame
            boolean keyed = selected != null && Stream.of(ParticleEmitterHolder.KeyChannel.values()).anyMatch(channel -> !selected.getKeys(channel).isEmpty());

            if (keyed && !preview.isPlaying()) refreshSelectionView();
        }

        playButton.visible = !preview.isPlaying();
        pauseButton.visible = preview.isPlaying();
        deleteButton.active = selected != null;

        Font font = Minecraft.getInstance().font;
        int headerBottom = getY() + HEADER_H;
        int trackBottom = getBottom() - FOOTER_H;
        int trackStart = getTrackStart();

        g.fill(getX(), getY(), getRight(), getBottom(), 0xFF1E1E1E);
        g.fill(getX(), getY(), getRight(), headerBottom, 0xFF252525);
        g.fill(getX(), headerBottom - 1, getRight(), headerBottom, 0xFF404040);
        g.text(font, Component.translatable(LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_TRACK_TITLE), getX() + TITLE_X, getY() + TITLE_Y, 0xFFCCCCCC, false);
        g.fill(trackStart - DIVIDER_W, headerBottom, trackStart, trackBottom, 0xFF404040);
        g.fill(getX(), trackBottom, getRight(), getBottom(), 0xFF252525);
        g.fill(getX(), trackBottom - 1, getRight(), trackBottom, 0xFF404040);

        // The playhead over the preview's end, beside the footer buttons
        boolean endless = holder.isEndless();
        String length = endless ? "-" : String.format("%.2f", holder.getPreviewEnd());
        g.text(font, String.format("%.2f / %s s", preview.time(), length), getX() + FOOTER_INSET * 3 + (FOOTER_H - FOOTER_INSET * 2) * 2 + 4, trackBottom + (FOOTER_H - font.lineHeight) / 2 + 1, 0xFFAAAAAA, false);

        g.enableScissor(trackStart, getY(), getRight(), trackBottom);

        float seconds = (visualFrameEnd - visualFrameStart) / 60.0F;
        float step = RULER_STEPS[RULER_STEPS.length - 1];

        for (float candidate : RULER_STEPS) {
            if (seconds / candidate <= MAX_VISIBLE_RULER_STEPS) {
                step = candidate;
                break;
            }
        }

        for (int i = Mth.ceil(visualFrameStart / 60.0F / step); i * step * 60.0F < visualFrameEnd; i++) {
            float time = i * step;
            int x = frameToX(time * 60.0F);

            g.centeredText(font, String.format(step < 1.0F ? "%.2f" : "%.0f", time), x, headerBottom - (font.lineHeight + 2), 0xFF999999);
            g.fill(x, headerBottom, x + 1, trackBottom, 0xFF2A2A2A);
        }

        int zeroX = frameToX(0.0F);
        int endX = frameToX(holder.getPreviewEnd() * 60.0F);

        if (trackStart < zeroX) g.fill(trackStart, headerBottom, zeroX, trackBottom, 0x88000000);

        endMarker.visible = !endless;
        endMarker.updatePosition(getY() + END_GRIP_TOP, headerBottom, trackBottom);

        if (!endless) {
            if (endX < getRight()) g.fill(endX, headerBottom, getRight(), trackBottom, 0x88000000);

            endMarker.extractRenderState(g, mouseX, mouseY, a);
        }

        for (TimelineTrackEntry row : trackList.children()) {
            if (row instanceof EmitterRow emitterRow) {
                emitterRow.extractTrackArea(g, mouseX, mouseY, a);
            } else if (row instanceof EmitterKeyRow keyRow) {
                keyRow.extractTrackArea(g, mouseX, mouseY, a);
            }
        }

        if (box != null) box.extractRenderState(g, mouseX, mouseY, a);

        g.disableScissor();

        super.extractWidgetRenderState(g, mouseX, mouseY, a);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (grab != null) {
            endGrab(grab, event.button() == 0);

            return true;
        }

        if (event.x() >= getTrackStart() && endMarker.isMouseOver(event.x(), event.y()) && endMarker.mouseClicked(event, doubleClick)) {
            setDragging(true);

            return true;
        }

        // A press on the track list's empty space picks no emitter. The list claims such a press too, so it is told
        // apart before the list takes it
        boolean emptySpace = event.button() == 0
            && trackList.isMouseOver(event.x(), event.y())
            && !trackList.isOnScrollbar(event.x(), event.y())
            && trackList.children().stream().noneMatch(row -> row.isMouseOver(event.x(), event.y()));
        boolean consumed = super.mouseClicked(event, doubleClick);

        if (emptySpace) pressEmptyTrack(event, null);

        return consumed || emptySpace;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (grab != null) {
            moveGrab(grab, event.x());

            return true;
        }

        if (box != null) {
            box.dragTo(event.x(), event.y());
            selectedKeys.clear();
            selectedKeys.addAll(box.kept());

            for (TimelineTrackEntry row : trackList.children()) {
                if (row instanceof EmitterKeyRow keyRow) selectedKeys.addAll(keyRow.keysIn(box.getX(), box.getY(), box.getRight(), box.getBottom()));
            }

            return true;
        }

        if (endMarker.mouseDragged(event, dx, dy)) return true;
        if (frameIndicator.mouseDragged(event, dx, dy)) return true;
        if (scrollBar.mouseDragged(event, dx, dy)) return true;
        if (trackList.mouseDragged(event, dx, dy)) return true;

        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        boolean consumed = false;

        if (grab != null && grab.held()) {
            endGrab(grab, true);
            consumed = true;
        }

        if (box != null) {
            if (!box.wasDragged()) scrubTo(xToFrame(event.x()));

            box = null;
            consumed = true;
        }

        if (endMarker.mouseReleased(event)) {
            consumed = true;
            setDragging(false);
        }

        if (frameIndicator.mouseReleased(event)) {
            consumed = true;
            setDragging(false);
        }

        if (scrollBar.mouseReleased(event)) {
            consumed = true;
            setDragging(false);
        }

        if (trackList.mouseReleased(event)) consumed = true;

        return consumed || super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (EditorKeyMappings.DELETE.matches(event) && !selectedKeys.isEmpty()) {
            deleteSelectedKeys();
            return true;
        }

        if (EditorKeyMappings.DELETE.matches(event) && selected != null) {
            deleteEmitter(selected);
            return true;
        }

        if (EditorKeyMappings.COPY.matches(event) && !selectedKeys.isEmpty()) {
            clipboard = selectedKeys.stream()
                .flatMap(key -> key.emitter().getKeys(key.channel()).keys().stream()
                    .filter(keyframe -> keyframe.time() == key.time())
                    .map(keyframe -> new CopiedKey(key.emitter(), key.channel(), key.emitter().getStart() + keyframe.time(), keyframe.value())))
                .toList();

            return true;
        }

        if (EditorKeyMappings.PASTE.matches(event) && !clipboard.isEmpty()) {
            // The earliest copied key lands on the playhead. Keys copied from one emitter go to the selected emitter
            float offset = preview.time() - (float)clipboard.stream().mapToDouble(CopiedKey::seconds).min().orElseThrow();
            boolean oneSource = clipboard.stream().map(CopiedKey::emitter).distinct().count() == 1;
            Map<ParticleEmitterHolder, ParticleEmitterHolder.Timeline> before = new IdentityHashMap<>();
            List<CopiedKey> pasted = new ArrayList<>();

            for (CopiedKey key : clipboard) {
                ParticleEmitterHolder target = oneSource && selected != null ? selected : key.emitter();

                if (!holder.getEmitters().contains(target) || target.getSpawnType() != ParticleEmitterHolder.SpawnType.SPAWN_RATE) continue;

                before.putIfAbsent(target, target.getTimeline());
                target.insertKey(key.channel(), key.seconds() + offset, key.value());
                pasted.add(new CopiedKey(target, key.channel(), key.seconds() + offset, key.value()));
            }

            if (pasted.isEmpty()) return true;

            // The pasted keys are selected, by the times they hold once every paste has grown its span
            clearKeySelection();

            for (CopiedKey key : pasted) {
                key.emitter().getKeys(key.channel()).keys().stream()
                    .filter(keyframe -> Math.abs(key.emitter().getStart() + keyframe.time() - key.seconds()) < KEY_MATCH)
                    .forEach(keyframe -> selectedKeys.add(new KeyRef(key.emitter(), key.channel(), keyframe.time())));
            }

            pushTimelineChange(before);

            return true;
        }

        if (EditorKeyMappings.DUPLICATE_KEYFRAME.matches(event) && !selectedKeys.isEmpty()) {
            beginKeyGrab(true, false, lastMouseX);

            return true;
        }

        if (EditorKeyMappings.MOVE_KEYFRAME.matches(event) && !selectedKeys.isEmpty()) {
            beginKeyGrab(false, false, lastMouseX);

            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= getTrackStart() && mouseX < getRight() && mouseY >= getY() && mouseY < getBottom()) {
            float xFraction = (float)(mouseX - getTrackStart()) / getWidth();
            float zoom = (visualFrameEnd - visualFrameStart) * 0.1F;
            int sign = Mth.sign(scrollY);

            visualFrameStart += xFraction * zoom * sign;
            visualFrameEnd -= MathUtils.oneMinus(xFraction) * zoom * sign;

            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /// One key on the emitter track.
    ///
    /// @param time in seconds since the emitter's time span started
    public record KeyRef(ParticleEmitterHolder emitter, ParticleEmitterHolder.KeyChannel channel, float time) {
    }

    /// A key as it was when a grab took it.
    ///
    /// @param time    in seconds since the emitter's time span started
    /// @param seconds since the system started
    private record GrabbedKey(ParticleEmitterHolder emitter, ParticleEmitterHolder.KeyChannel channel, float time, float seconds, Transform value) {
    }

    /// Keys being moved along the time axis.
    ///
    /// @param before    the timelines of the keys' emitters when the grab began
    /// @param grabTime  where the cursor took hold, in seconds
    /// @param duplicate whether copies move while the grabbed keys stay
    /// @param held      whether the left button holds the grab
    private record KeyGrab(
        Map<ParticleEmitterHolder, ParticleEmitterHolder.Timeline> before, List<GrabbedKey> keys, float grabTime, boolean duplicate, boolean held
    ) {
    }

    /// A copied key.
    ///
    /// @param seconds since the system started
    private record CopiedKey(ParticleEmitterHolder emitter, ParticleEmitterHolder.KeyChannel channel, float seconds, Transform value) {
    }

    // ***************************************************************
    // TrackTimeline
    // ***************************************************************

    @Override
    public float currentFrame() {
        return preview.time() * 60.0F;
    }

    @Override
    public String playheadLabel() {
        return String.format("%.2f s", preview.time());
    }

    /// Seeks the preview, freezing its particles.
    @Override
    public void setCurrentFrame(final float frame) {
        preview.seek(frame / 60.0F);
    }

    @Override
    public int frameToX(final float frame) {
        int trackStart = getTrackStart();

        return trackStart + (int)Mth.clampedLerp((frame - visualFrameStart) / (visualFrameEnd - visualFrameStart), 0.0F, getWidth() - (trackStart - getX()) - AdjustablePanelManager.DIVIDER_LENGTH);
    }

    @Override
    public float xToFrame(final double x) {
        return (float)Mth.clampedLerp((x - getTrackStart()) / (getWidth() - (getTrackStart() - getX()) - AdjustablePanelManager.DIVIDER_LENGTH), visualFrameStart, visualFrameEnd);
    }

    @Override public float visualFrameStart() { return visualFrameStart; }
    @Override public float visualFrameEnd() { return visualFrameEnd; }
    @Override public void setVisualFrameStart(final float frame) { visualFrameStart = frame; }
    @Override public void setVisualFrameEnd(final float frame) { visualFrameEnd = frame; }

    @Override
    public float timelineFrames() {
        return Math.max(Math.max(holder.getPreviewEnd(), holder.getLength()), MIN_TIMELINE) * 60.0F;
    }
}
