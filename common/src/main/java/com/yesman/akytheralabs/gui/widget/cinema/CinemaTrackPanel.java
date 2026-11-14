package com.yesman.akytheralabs.gui.widget.cinema;

import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.ContextMenuContainer;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.core.blueprint.particle.KeyframeTrack;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.helper.MathUtils;
import com.yesman.akytheralabs.akythera.cinema.CinemaActorHolder;
import com.yesman.akytheralabs.akythera.cinema.CinemaSceneHolder;
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
import com.yesman.akytheralabs.gui.widget.viewer.CinemaScenePreviewComponent;
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
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/// The track of a cinema scene: one [CinemaActorRow] per actor over a time axis in seconds, each followed by a
/// [CinemaCameraKeyRow] for its camera keys. `+` / `-` buttons and a right-press menu add and delete actors, and play /
/// pause / stop sit under it, beside the playback time over the preview's end.
///
/// [EditorKeyMappings#INSERT_KEYFRAME] keys the selected actor's camera at the playhead ([#insertCameraKey]). A press
/// on a key selects it, control adding to the selection, and a drag moves the selected keys along the time axis. The
/// Delete key and the key menu delete them. A press on empty track space moves the playhead there. Whenever the
/// playhead moves, each keyed camera takes its keyed transform there.
///
/// The red line is where the scene ends, see [CinemaSceneEndMarker].
public final class CinemaTrackPanel extends AdjustablePanelManager.PanelArea implements TrackTimeline {
    private static final float[] RULER_STEPS = { 0.05F, 0.1F, 0.25F, 0.5F, 1.0F, 2.0F, 5.0F, 10.0F, 30.0F, 60.0F };
    private static final int MAX_VISIBLE_RULER_STEPS = 12;

    private static final int HEADER_H = 38;
    private static final int FOOTER_H = 20;
    private static final int BUTTON = 16;
    private static final int TITLE_X = 6;
    private static final int TITLE_Y = 22;
    private static final int FOOTER_INSET = 2;
    private static final int DIVIDER_W = 2;

    /// The shortest timeline, in seconds, so a scene with no montage still has room.
    private static final float MIN_TIMELINE = 1.0F;

    /// The top of the red line's grip, below the playhead's row, in pixels from the panel's top.
    private static final int END_GRIP_TOP = 24;

    /// How far a key may sit from where a drag put it and still be that key, in seconds.
    private static final float KEY_MATCH = 1.0E-3F;

    private final CinemaSceneHolder holder;
    private final CinemaScenePreviewComponent preview;

    /// Told the selected actor, or null when none is, whenever an actor is selected.
    private final Consumer<@Nullable CinemaActorHolder> onSelectionChanged;

    /// Told the selected actor, or null when none is, whenever the track edits it or the scene, so the Details rows
    /// re-read them.
    private final Consumer<@Nullable CinemaActorHolder> onEdited;

    /// Told this panel whenever the rows are built again from the actors, after an actor is added or removed.
    private final Consumer<CinemaTrackPanel> onActorsChanged;

    private final TimelineList trackList = new TimelineList();
    private final CurrentFrameIndicator frameIndicator;
    private final FrameScrollBar scrollBar;
    private final CinemaSceneEndMarker endMarker;
    private final AkytheraButton addButton;
    private final AkytheraButton deleteButton;
    private final AkytheraButton playButton;
    private final AkytheraButton pauseButton;
    private final AkytheraButton stopButton;
    private final ContextMenuContainer actorMenu;
    private final ContextMenuContainer keyMenu;

    private final Set<KeyRef> selectedKeys = new HashSet<>();

    /// The playhead the keyed cameras were last written at, in seconds. NaN writes them again.
    private float syncedTime = Float.NaN;

    /// The keys being dragged along the time axis, or null.
    private @Nullable KeyGrab grab;

    private float visualFrameStart;
    private float visualFrameEnd;
    private @Nullable CinemaActorHolder selected;

    public CinemaTrackPanel(
        final CinemaSceneHolder holder,
        final CinemaScenePreviewComponent preview,
        final Consumer<@Nullable CinemaActorHolder> onSelectionChanged,
        final Consumer<@Nullable CinemaActorHolder> onEdited,
        final Consumer<CinemaTrackPanel> onActorsChanged
    ) {
        this.holder = holder;
        this.preview = preview;
        this.onSelectionChanged = onSelectionChanged;
        this.onEdited = onEdited;
        this.onActorsChanged = onActorsChanged;
        this.frameIndicator = new CurrentFrameIndicator(this);
        this.scrollBar = new FrameScrollBar(this);
        this.endMarker = new CinemaSceneEndMarker(this, holder);

        Font font = Minecraft.getInstance().font;
        int buttonTop = HEADER_H - BUTTON - 4;
        int footerButton = FOOTER_H - FOOTER_INSET * 2;

        this.addButton = headerButton(font, TimelineList.NAME_BLOCK_W - (BUTTON + 6) * 2, buttonTop, AkytheraLabsButtonTheme.MONTAGE_SEQ_ADD_BUTTON, this::addActor);
        this.deleteButton = headerButton(font, TimelineList.NAME_BLOCK_W - (BUTTON + 6), buttonTop, AkytheraLabsButtonTheme.MONTAGE_SEQ_REMOVE_BUTTON, () -> {
            if (selected != null) deleteActor(selected);
        });
        this.playButton = footerButton(font, FOOTER_INSET, footerButton, AkytheraLabsButtonTheme.ANIM_TRACK_RESUME_BUTTON, preview::play);
        this.pauseButton = footerButton(font, FOOTER_INSET, footerButton, AkytheraLabsButtonTheme.ANIM_TRACK_PAUSE_BUTTON, preview::pause);
        this.stopButton = footerButton(font, FOOTER_INSET * 2 + footerButton, footerButton, AkytheraLabsButtonTheme.SOUND_TRACK_STOP_BUTTON, preview::stop);
        this.actorMenu = ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(120)
            .entry()
                .message(Component.translatable(LangKeys.GUI_CINEMA_SCENE_SCREEN_MENU_ADD_ACTOR))
                .onPress(_ -> addActor())
                .done()
            .entry()
                .message(Component.translatable(LangKeys.GUI_CINEMA_SCENE_SCREEN_MENU_DELETE_ACTOR))
                .onPress(_ -> {
                    if (selected != null) deleteActor(selected);
                })
                .done()
            .entry()
                .message(Component.translatable(LangKeys.GUI_CINEMA_SCENE_SCREEN_MENU_INSERT_CAMERA_KEY))
                .onPress(_ -> insertCameraKey())
                .done()
            .build();
        this.keyMenu = ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(120)
            .entry()
                .message(Component.translatable(LangKeys.GUI_CINEMA_SCENE_SCREEN_MENU_DELETE_KEYFRAMES))
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

    public CinemaSceneHolder holder() {
        return holder;
    }

    public void rebuildEntries() {
        trackList.rebuild(holder.getActors().stream().<TimelineTrackEntry>flatMap(actor -> Stream.of(
            new CinemaActorRow(this, actor),
            new CinemaCameraKeyRow(this, actor)
        )).toList());
        onActorsChanged.accept(this);

        if (selected != null && !holder.getActors().contains(selected)) setSelected(null);
    }

    /// Appends a new actor, selects it, and pushes one undo step.
    public void addActor() {
        CinemaActorHolder added = holder.newActor();

        holder.getActors().add(added);
        HistoryManager.getInstance().pushHistory(new ListInsert<>(holder.getActors(), holder.getActors().size() - 1, added, this::rebuildEntries));
        rebuildEntries();
        setSelected(added);
    }

    /// Removes `actor` and pushes one undo step.
    public void deleteActor(final CinemaActorHolder actor) {
        int index = holder.getActors().indexOf(actor);

        if (index < 0) return;

        holder.getActors().remove(index);
        HistoryManager.getInstance().pushHistory(new ListRemove<>(holder.getActors(), index, actor, this::rebuildEntries));
        rebuildEntries();
    }

    /// Keys the selected actor's camera at the playhead, as one undo step.
    ///
    /// @return whether an actor was selected to key
    public boolean insertCameraKey() {
        if (selected == null) return false;

        Map<CinemaActorHolder, KeyframeTrack<Transform>> before = new IdentityHashMap<>();

        before.put(selected, selected.getCameraKeys());
        selected.insertCameraKey(preview.time());
        selectedKeys.clear();
        pushKeysChange(before);

        return true;
    }

    /// Deletes the selected keys, as one undo step.
    public void deleteSelectedKeys() {
        Map<CinemaActorHolder, KeyframeTrack<Transform>> before = new IdentityHashMap<>();

        for (KeyRef key : selectedKeys) {
            before.putIfAbsent(key.actor(), key.actor().getCameraKeys());
            key.actor().removeCameraKey(key.time());
        }

        selectedKeys.clear();
        pushKeysChange(before);
    }

    /// Pushes the change from `before` to each actor's camera keys now as one undo step.
    ///
    /// Opened for editing the track from outside the panel.
    public void pushKeysChange(final Map<CinemaActorHolder, KeyframeTrack<Transform>> before) {
        Map<CinemaActorHolder, KeyframeTrack<Transform>> after = new IdentityHashMap<>();

        before.keySet().forEach(actor -> after.put(actor, actor.getCameraKeys()));
        HistoryManager.getInstance().pushHistory(new WorkHistory() {
            @Override
            public void undo() {
                before.forEach(CinemaActorHolder::setCameraKeys);
                selectedKeys.clear();
                syncedTime = Float.NaN;
                onEdited.accept(selected);
            }

            @Override
            public void redo() {
                after.forEach(CinemaActorHolder::setCameraKeys);
                selectedKeys.clear();
                syncedTime = Float.NaN;
                onEdited.accept(selected);
            }
        });
        syncedTime = Float.NaN;
        onEdited.accept(selected);
    }

    public boolean isKeySelected(final KeyRef key) {
        return selectedKeys.contains(key);
    }

    /// Selects `key` and its actor. Control adds or removes `key`; without it `key` is selected alone.
    public void clickKey(final KeyRef key, final boolean control) {
        if (control) {
            if (!selectedKeys.remove(key)) selectedKeys.add(key);
        } else {
            selectedKeys.clear();
            selectedKeys.add(key);
        }

        if (selected != key.actor()) setSelected(key.actor());
    }

    public void clearKeySelection() {
        selectedKeys.clear();
    }

    /// A left press on track space with nothing under it: selects `actor` (none when null), and right of the name
    /// block moves the playhead there. Control keeps the selected keys.
    public void pressEmptyTrack(final MouseButtonEvent event, final @Nullable CinemaActorHolder actor) {
        if (!event.hasControlDownWithQuirk()) clearKeySelection();
        if (selected != actor) setSelected(actor);
        if (event.x() >= getTrackStart()) scrubTo(xToFrame(event.x()));
    }

    /// Takes hold of the selected keys at `mouseX`, to drag them along the time axis until the button is released.
    public void beginKeyGrab(final double mouseX) {
        Map<CinemaActorHolder, KeyframeTrack<Transform>> before = new IdentityHashMap<>();
        List<GrabbedKey> keys = new ArrayList<>();

        for (KeyRef key : selectedKeys) {
            for (KeyframeTrack.Key<Transform> keyframe : key.actor().getCameraKeys().keys()) {
                if (keyframe.time() != key.time()) continue;

                before.putIfAbsent(key.actor(), key.actor().getCameraKeys());
                keys.add(new GrabbedKey(key.actor(), keyframe.time(), keyframe.value()));
            }
        }

        if (!keys.isEmpty()) grab = new KeyGrab(before, keys, xToFrame(mouseX) / 60.0F);
    }

    /// Moves the grabbed keys as far as the cursor moved since the grab began, never before the scene's start.
    private void moveGrab(final KeyGrab grab, final double mouseX) {
        float delta = xToFrame(mouseX) / 60.0F - grab.grabTime();

        grab.before().forEach(CinemaActorHolder::setCameraKeys);

        for (GrabbedKey key : grab.keys()) {
            delta = Math.max(delta, -key.time());
        }

        grab.keys().forEach(key -> key.actor().removeCameraKey(key.time()));

        float moved = delta;

        selectedKeys.clear();

        for (GrabbedKey key : grab.keys()) {
            key.actor().insertCameraKey(key.time() + moved, key.value());
            key.actor().getCameraKeys().keys().stream()
                .filter(keyframe -> Math.abs(keyframe.time() - key.time() - moved) < KEY_MATCH)
                .forEach(keyframe -> selectedKeys.add(new KeyRef(key.actor(), keyframe.time())));
        }

        syncedTime = Float.NaN;
    }

    /// The right-press menu of a key.
    public ContextMenuContainer keyMenu() {
        return keyMenu;
    }

    /// The right-press menu of an actor row.
    public ContextMenuContainer actorMenu() {
        return actorMenu;
    }

    public @Nullable CinemaActorHolder getSelected() {
        return selected;
    }

    /// Selects `actor`. Selecting none also drops the key selection.
    public void setSelected(final @Nullable CinemaActorHolder actor) {
        selected = actor;

        if (actor == null) clearKeySelection();

        onSelectionChanged.accept(actor);
    }

    /// Tells the edit listener, so the Details rows re-read what a drag edits.
    public void refreshSelectionView() {
        onEdited.accept(selected);
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
        float playhead = preview.time();

        if (playhead != syncedTime) {
            syncedTime = playhead;
            holder.getActors().forEach(actor -> actor.moveToTime(playhead));

            // Details re-reads the selected actor's keyed camera, except while playing, which moves it every frame
            if (selected != null && !selected.getCameraKeys().isEmpty() && !preview.isPlaying()) onEdited.accept(selected);
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
        g.text(font, Component.translatable(LangKeys.GUI_CINEMA_SCENE_SCREEN_TRACK_TITLE), getX() + TITLE_X, getY() + TITLE_Y, 0xFFCCCCCC, false);
        g.fill(trackStart - DIVIDER_W, headerBottom, trackStart, trackBottom, 0xFF404040);
        g.fill(getX(), trackBottom, getRight(), getBottom(), 0xFF252525);
        g.fill(getX(), trackBottom - 1, getRight(), trackBottom, 0xFF404040);

        // The playhead over the preview's end, beside the footer buttons
        g.text(
            font, String.format("%.2f / %.2f s", preview.time(), preview.end()),
            getX() + FOOTER_INSET * 3 + (FOOTER_H - FOOTER_INSET * 2) * 2 + 4, trackBottom + (FOOTER_H - font.lineHeight) / 2 + 1, 0xFFAAAAAA, false
        );

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
        int endX = frameToX(holder.getDuration() * 60.0F);

        if (trackStart < zeroX) g.fill(trackStart, headerBottom, zeroX, trackBottom, 0x88000000);
        if (endX < getRight()) g.fill(endX, headerBottom, getRight(), trackBottom, 0x88000000);

        endMarker.updatePosition(getY() + END_GRIP_TOP, headerBottom, trackBottom);
        endMarker.extractRenderState(g, mouseX, mouseY, a);

        for (TimelineTrackEntry row : trackList.children()) {
            if (row instanceof CinemaActorRow actorRow) {
                actorRow.extractTrackArea(g, mouseX, mouseY, a);
            } else if (row instanceof CinemaCameraKeyRow keyRow) {
                keyRow.extractTrackArea(g, mouseX, mouseY, a);
            }
        }

        g.disableScissor();

        super.extractWidgetRenderState(g, mouseX, mouseY, a);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.x() >= getTrackStart() && endMarker.isMouseOver(event.x(), event.y()) && endMarker.mouseClicked(event, doubleClick)) {
            setDragging(true);

            return true;
        }

        // A press on the track list's empty space picks no actor. The list claims such a press too, so it is told
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

        if (endMarker.mouseDragged(event, dx, dy)) return true;
        if (frameIndicator.mouseDragged(event, dx, dy)) return true;
        if (scrollBar.mouseDragged(event, dx, dy)) return true;
        if (trackList.mouseDragged(event, dx, dy)) return true;

        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        boolean consumed = false;

        if (grab != null) {
            KeyGrab released = grab;

            grab = null;
            consumed = true;

            // A grab that moved nothing is a plain press
            if (released.before().entrySet().stream().anyMatch(entry -> !entry.getKey().getCameraKeys().equals(entry.getValue()))) {
                pushKeysChange(released.before());
            }
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
            deleteActor(selected);
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

    /// One camera key on the cinema track.
    ///
    /// @param time in seconds since the scene started
    public record KeyRef(CinemaActorHolder actor, float time) {
    }

    /// A key as it was when a grab took it.
    ///
    /// @param time in seconds since the scene started
    private record GrabbedKey(CinemaActorHolder actor, float time, Transform value) {
    }

    /// Keys being dragged along the time axis.
    ///
    /// @param before   the camera keys of the grabbed keys' actors when the grab began
    /// @param grabTime where the cursor took hold, in seconds
    private record KeyGrab(Map<CinemaActorHolder, KeyframeTrack<Transform>> before, List<GrabbedKey> keys, float grabTime) {
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

    /// Seeks the preview, pausing it.
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
        return Math.max(preview.end(), MIN_TIMELINE) * 60.0F;
    }
}
