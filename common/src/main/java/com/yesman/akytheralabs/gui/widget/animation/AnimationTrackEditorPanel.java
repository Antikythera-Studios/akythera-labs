package com.yesman.akytheralabs.gui.widget.animation;

import com.yesman.akythera.client.gui.widget.AnchoredButton;
import com.yesman.akythera.client.gui.widget.viewer.EntityModelComponent;
import com.yesman.akythera.client.gui.widget.viewer.Model3DViewer;
import com.yesman.akythera.client.gui.widget.viewer.EntityModelComponent.EditorScrubAnimator;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.core.util.helper.MathUtils;
import com.yesman.akytheralabs.akythera.animation.NotifyTrackHolder;
import com.yesman.akytheralabs.akythera.animation.PlayableAnimationHolder;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.ListInsert;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.widget.AdvancedEditBox;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/// Reusable animation-track editor as a [com.yesman.akytheralabs.gui.panel.AdjustablePanelManager.PanelArea]
/// so it can drop into any panel section without extra glue. It owns:
///   - the title strip with the "+ track" button and per-frame ruler
///   - the scrolling track list ([AnimationTrackList])
///   - the playhead overlay ([CurrentFrameIndicator])
///
/// The list is generic: the host screen supplies ALL rows via a row builder (see the
/// constructor), so the sequence editor builds notify rows and the montage editor builds
/// section + segment + notify rows. The panel keeps the notify holder + "+" add-track
/// since notify tracks are the common editable track type across both screens.
public final class AnimationTrackEditorPanel extends AdjustablePanelManager.PanelArea {
    private static final int[] RULER_SCALES = {5, 10, 25, 50, 100, /* Grows by x2 afterward */ };
    private static final int MAX_VISIBLE_RULER_SCALES = 20;

    public static final int EDGE_DRAG_THRESHOLD = 4;
    public static final int HEADER_H = 38;
    public static final int FOOTER_H = 20;

    private static final int ADD_BUTTON_W = 18;
    private static final int ADD_BUTTON_H = 18;
    private static final int TITLE_X = 6;
    private static final int TITLE_Y = 6;
    private static final int FOOTER_BUTTON_INSET = 2;
    private static final int FRAME_BOX_W = 40;

    // Exposed for `FrameScrollBar`
    static final int DIVIDER_W = 2;

    private final NotifyTrackHolder holder;
    private final PlayableAnimationHolder<?> playableAnimationHolder;
    private final Consumer<NotifyTrackHolder.@Nullable NotifyRef> onSelectionChanged;

    /// Builds ALL rows for the list, given this panel. Run on every rebuild so the rows
    /// re-read live holder state. The host screen decides what rows exist (notify only,
    /// or sections + segments + notify).
    private final Function<AnimationTrackEditorPanel, List<AnimTrackEntry>> rowBuilderFn;

    private final AnimationTrackList trackList;
    private final CurrentFrameIndicator frameIndicator;
    private final AnchoredButton addTrackButton;

    /// A visible frame scope from
    private float visualFrameStart;

    /// A visible frame scope to
    private float visualFrameEnd;

    /// Set once the visible window has been fitted to a non-empty clip. Montages open empty
    /// (no segments → total 0 → collapsed `[0, 0]` window), so the ctor's fit is a no-op; we
    /// re-fit on the first [#rebuildEntries] where the clip has length, then leave the window
    /// to the user's zoom/pan. See [#fitFrameWindowToClip].
    private boolean frameWindowInitialized = false;

    private float currentFrame = 0F;

    /// Whether animation being updated
    private boolean playing = false;

    /// See [#isDebugDriven].
    private BooleanSupplier debugDriven = () -> false;

    /// Currently opened model previewer
    private final Model3DViewer model3DViewer;
    private NotifyTrackHolder.@Nullable NotifyRef selected;

    /// Footer children.
    private final AnchoredButton playButton;
    private final AnchoredButton pauseButton;
    private final AdvancedEditBox<String> frameBox;
    private final FrameScrollBar scrollBar;

    public AnimationTrackEditorPanel(
        NotifyTrackHolder holder,
        Model3DViewer model3DViewer,
        PlayableAnimationHolder<?> playableAnimationHolder,
        Consumer<NotifyTrackHolder.@Nullable NotifyRef> onSelectionChanged,
        Function<AnimationTrackEditorPanel, List<AnimTrackEntry>> rowBuilderFn
    ) {
        this.holder = holder;
        this.model3DViewer = model3DViewer;
        this.playableAnimationHolder = playableAnimationHolder;
        this.onSelectionChanged = onSelectionChanged;
        this.rowBuilderFn = rowBuilderFn;

        fitFrameWindowToClip();

        this.addTrackButton = AnchoredButton.buttonBuilder(
                Minecraft.getInstance().font, Component.empty(), _ -> onAddTrackPressed()
            )
            .xUnits(AnchoredWidget.pixel(AnimationTrackList.NAME_BLOCK_W - ADD_BUTTON_W - 8), AnchoredWidget.pixel(ADD_BUTTON_W))
            .yUnits(AnchoredWidget.pixel(HEADER_H - ADD_BUTTON_H - 4), AnchoredWidget.pixel(ADD_BUTTON_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_CONTENT_ADD_BUTTON)
            .build();

        this.trackList = new AnimationTrackList();
        this.frameIndicator = new CurrentFrameIndicator(this);

        // Footer: play/pause buttons, frame number box, scroll/zoom bar. All four anchor to the
        // panel rect's bottom and scale with FOOTER_H so changing the constant rescales everything.
        int btnSize = FOOTER_H - FOOTER_BUTTON_INSET * 2;

        this.playButton = AnchoredButton.buttonBuilder(
                Minecraft.getInstance().font, Component.empty(), _ -> setPlaying(true)
            )
            .xUnits(AnchoredWidget.pixel(FOOTER_BUTTON_INSET * 3 + FRAME_BOX_W + btnSize), AnchoredWidget.pixel(btnSize))
            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(btnSize))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.CENTER_HEIGHT)
            .theme(AkytheraLabsButtonTheme.ANIM_TRACK_RESUME_BUTTON)
            .build();

        this.pauseButton = AnchoredButton.buttonBuilder(
                Minecraft.getInstance().font, Component.empty(), _ -> setPlaying(false)
            )
            .xUnits(AnchoredWidget.pixel(FOOTER_BUTTON_INSET * 2 + FRAME_BOX_W), AnchoredWidget.pixel(btnSize))
            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(btnSize))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.CENTER_HEIGHT)
            .theme(AkytheraLabsButtonTheme.ANIM_TRACK_PAUSE_BUTTON)
            .build();

        this.frameBox = AdvancedEditBox.filteredStringBox(
            Minecraft.getInstance().font,
            s -> s.matches("\\d*"),
            Component.empty(),
            AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH,
            AnchoredWidget.VerticalAnchorType.CENTER_HEIGHT
        );
        frameBox.setX1(AnchoredWidget.pixel(FOOTER_BUTTON_INSET));
        frameBox.setX2(AnchoredWidget.pixel(FRAME_BOX_W));
        frameBox.setY1(AnchoredWidget.pixel(0));
        frameBox.setY2(AnchoredWidget.pixel(btnSize - 2));
        frameBox.setOnCommitted((_, _) -> {
            String t = frameBox.getText();
            if (t.isEmpty()) return;
            try {
                setCurrentFrame(Integer.parseInt(t));
            } catch (NumberFormatException ignored) {
            }
        });

        this.scrollBar = new FrameScrollBar(this);

        addWidget(trackList);
        addWidget(addTrackButton);
        addWidget(frameIndicator);
        addWidget(playButton);
        addWidget(pauseButton);
        addWidget(frameBox);
        addWidget(scrollBar);

        bgColor = 0;

        rebuildEntries();
    }

    public NotifyTrackHolder holder() {
        return holder;
    }

    public float currentFrame() {
        return currentFrame;
    }

    public void setCurrentFrame(float frame) {
        if (isDebugDriven()) return;

        applyCurrentFrame(frame);
    }

    /// Moves the playhead past the [#isDebugDriven] gate.
    private void applyCurrentFrame(float frame) {
        EditorScrubAnimator headlessAnimator = getHeadlessAnimator();
        if (headlessAnimator == null || headlessAnimator.getPreviewAnimation() == null) return;

        float totalFrames = totalFrames() * 60F;
        currentFrame = Math.clamp(frame, 0.0F, totalFrames);

        // Drive the animator's playhead
        headlessAnimator.setElapsedTime(currentFrame / 60F);
    }

    public boolean isCurrentFrameInScope() {
        return currentFrame >= visualFrameStart && currentFrame <= visualFrameEnd;
    }

    public boolean isPlaying() {
        return playing;
    }

    public void setPlaying(boolean playing) {
        if (isDebugDriven()) return;

        applyPlaying(playing);
    }

    /// Writes the playback state past the [#isDebugDriven] gate.
    private void applyPlaying(boolean playing) {
        this.playing = playing;

        EditorScrubAnimator a = getHeadlessAnimator();
        if (a != null) a.setUpdatePose(playing);
    }

    /// Whether the playhead follows a possessed entity's animator instead of the editor's own
    /// playback. Installed by the owning screen; false for a screen that does not debug.
    public boolean isDebugDriven() {
        return debugDriven.getAsBoolean();
    }

    /// Installs the probe behind [#isDebugDriven].
    public void setDebugDrivenChecker(final BooleanSupplier debugDriven) {
        this.debugDriven = debugDriven;
    }

    /// Moves the playhead to a frame taken from the possessed entity, stopping the editor's own
    /// playback first so the two do not both advance the preview.
    public void setDebugCurrentFrame(final float frame) {
        if (playing) applyPlaying(false);

        applyCurrentFrame(frame);
    }

    // These fields are exposed for [FrameScrollBar] to read/write the visible-frame window.
    public float visualFrameStart() { return visualFrameStart; }
    public float visualFrameEnd() { return visualFrameEnd; }
    public void setVisualFrameStart(float v) { visualFrameStart = v; }
    public void setVisualFrameEnd(float v) { visualFrameEnd = v; }

    /// Exposed for [FrameScrollBar] to read the clip's total frames.
    public @Nullable EditorScrubAnimator headlessAnimator() {
        return getHeadlessAnimator();
    }

    private @Nullable EditorScrubAnimator getHeadlessAnimator() {
        EntityModelComponent c = model3DViewer.getComponent(EntityModelComponent.class);
        return (c != null && c.getAnimator() instanceof EditorScrubAnimator s) ? s : null;
    }

    public float totalFrames() {
        return playableAnimationHolder.getTotalFrameTime();
    }

    /// X coordinate (in screen space) for a given frame.
    public int frameToX(float frame) {
        int trackStart = getTrackStart();
        // Empty montage → collapsed [0, 0] window. Guard the /0 until the first clip fits it.
        if (visualFrameEnd - visualFrameStart <= 0.0F) return trackStart;
        return trackStart + (int)Mth.clampedLerp((frame - visualFrameStart) / (visualFrameEnd - visualFrameStart), 0.0F, getWidth() - trackStart - AdjustablePanelManager.DIVIDER_LENGTH);
    }

    /// Frame at a given screen-space X.
    public float xToFrame(double x) {
        return (float)Mth.clampedLerp((x - getTrackStart()) / (getWidth() - getTrackStart() - AdjustablePanelManager.DIVIDER_LENGTH), visualFrameStart, visualFrameEnd);
    }

    public int getTrackStart() {
        return getX() + AnimationTrackList.NAME_BLOCK_W + DIVIDER_W;
    }

    public void setSelected(NotifyTrackHolder.@Nullable NotifyRef ref) {
        this.selected = ref;
        onSelectionChanged.accept(ref);
    }

    /// Re-fire the selection callback with the current selection so the
    /// details pane re-reads its bound widgets without changing selection.
    /// Used by drag handlers to keep the details panel in sync with the
    /// mutating holder field during a drag tick.
    public void refreshSelectionView() {
        onSelectionChanged.accept(selected);
    }

    public NotifyTrackHolder.@Nullable NotifyRef getSelected() {
        return selected;
    }

    /// Row-level selection (which name block is highlighted), delegated to the list.
    public void setSelectedRow(@Nullable AnimTrackEntry row) {
        trackList.setSelected(row);
    }

    public @Nullable AnimTrackEntry selectedRow() {
        return trackList.getSelected();
    }

    /// Bootstrap with a default first track so the user has somewhere to drop
    /// notifies on first open.
    public void ensureAtLeastOneTrack() {
        if (holder.getTracks().isEmpty()) {
            holder.addTrack();
        }

        rebuildEntries();
    }

    private void onAddTrackPressed() {
        NotifyTrackHolder.Track added = holder.addTrack();
        rebuildEntries();

        HistoryManager.getInstance().pushHistory(new ListInsert<>(
            holder.getTracks(), holder.getTracks().size() - 1, added, this::rebuildEntries
        ));
    }

    public void rebuildEntries() {
        trackList.rebuild(rowBuilderFn.apply(this));
        fitFrameWindowToClip();
    }

    /// Call when the underlying clip's length changed (e.g. the sequence's animation was swapped):
    /// re-fits the visible frame window to the new length — overriding the user's zoom/pan, which
    /// belonged to the old clip — and re-clamps the playhead into `[0, total]`.
    public void onClipLengthChanged() {
        frameWindowInitialized = false;
        fitFrameWindowToClip();
        setCurrentFrame(currentFrame);
    }

    /// Fits the visible frame window to the clip's length, once. Montages open empty
    /// (total 0 → collapsed `[0, 0]`, where zoom scales by 0 and can't grow), so we defer
    /// until the first non-empty clip — e.g. after the first segment drop. Past that we
    /// leave the window alone so the user's manual zoom/pan sticks.
    private void fitFrameWindowToClip() {
        if (frameWindowInitialized) return;

        float total = playableAnimationHolder.getTotalFrameTime();
        if (total <= 0.0F) return;

        visualFrameStart = -total * 0.1F * 60.0F;
        visualFrameEnd = total * 1.1F * 60.0F;
        frameWindowInitialized = true;
    }

    /// The row currently showing an inline editor (rename box), or null. Used to route
    /// keyboard/char/click input to it generically — the panel doesn't care what row type.
    private @Nullable AnimTrackEntry editingRow() {
        for (AnimTrackEntry row : trackList.children()) {
            if (row.activeInlineEditor() != null) return row;
        }

        return null;
    }

    @Override
    protected void onPanelResized(ScreenRectangle rect) {
        ScreenRectangle headerRect = new ScreenRectangle(rect.left(), rect.top(), rect.width(), HEADER_H);
        addTrackButton.relocate(headerRect);

        int listTop = rect.top() + HEADER_H;
        int listH = Math.max(0, rect.height() - (HEADER_H + FOOTER_H));
        trackList.updateSizeAndPosition(rect.width(), listH, rect.left(), listTop);
        frameIndicator.setBounds(rect.left(), rect.top(), rect.width(), rect.height() - FOOTER_H);

        ScreenRectangle footerRect = new ScreenRectangle(rect.left(), rect.bottom() - FOOTER_H, AnimationTrackList.NAME_BLOCK_W, FOOTER_H);

        // Relocate Footer children
        playButton.relocate(footerRect);
        pauseButton.relocate(footerRect);
        frameBox.relocate(footerRect);

        scrollBar.relocate(rect);
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        // While playing, the animator owns the clock — pull elapsedTime back into currentFrame so
        // the playhead tracks the running pose. When paused, currentFrame is only moved by user input.
        EditorScrubAnimator playingAnimator = getHeadlessAnimator();

        if (playing && playingAnimator != null && playingAnimator.getPreviewAnimation() != null) {
            currentFrame = playingAnimator.getElapsedTime() * 60F;
        }

        // Transport enablement, derived here so it also follows a debug session starting or ending.
        // The enabled state additionally picks the textured sprite's enabled/disabled variant.
        boolean debugDriven = isDebugDriven();
        playButton.active = !debugDriven && !playing;
        pauseButton.active = !debugDriven && playing;
        frameBox.active = !debugDriven;

        // Mirror the playhead into the frame box unless the user is currently typing in it —
        // don't stomp their in-progress edit.
        if (!frameBox.isFocused()) {
            frameBox.setValueByString(String.valueOf((int) currentFrame));
        }

        // Background
        g.fill(getX(), getY(), getRight(), getBottom(), 0xFF1E1E1E);

        int headerBottom = getY() + HEADER_H;

        // Header
        g.fill(getX(), getY(), getRight(), headerBottom, 0xFF252525);
        g.fill(getX(), headerBottom - 1, getRight(), headerBottom, 0xFF404040);

        // Title text
        Font font = Minecraft.getInstance().font;
        g.text(font, Component.translatable(LangKeys.GUI_NOTIFY_TRACKS_TITLE), getX() + TITLE_X, getY() + TITLE_Y + 16, 0xFFCCCCCC, false);

        int frameGraphBottom = getBottom() - FOOTER_H;

        // Vertical divider
        g.fill(getTrackStart() - DIVIDER_W, headerBottom, getTrackStart(), frameGraphBottom, 0xFF404040);

        // Footer background & divider
        g.fill(getX(), getBottom() - FOOTER_H, getRight(), getBottom(), 0xFF252525);
        g.fill(getX(), getBottom() - (FOOTER_H), getRight(), getBottom() - (FOOTER_H + 1), 0xFF404040);

        // Setup variables to render track
        float frameScope = (visualFrameEnd - visualFrameStart);
        int trackStart = getTrackStart();
        int frameStride;
        int i = 0;

        // Find the best matching stride, loops until it meets a scale less than max visible scales.
        do {
            frameStride = i < RULER_SCALES.length ? RULER_SCALES[i] : (int)Math.pow(2, i - RULER_SCALES.length) * 100;
            i++;
        } while (frameScope / frameStride > MAX_VISIBLE_RULER_SCALES);

        g.enableScissor(trackStart, getY(), getRight(), frameGraphBottom);
        int frame = frameStride * ((int)(visualFrameStart / frameStride) + (visualFrameStart < 0 ? 0 : 1));

        // Draw until the frame exceeds last frame
        while (frame < visualFrameEnd) {
            int x = frameToX(frame);
            String label = String.valueOf(frame);
            g.centeredText(font, label, x, headerBottom - (font.lineHeight + 2), 0xFF999999);
            g.fill(x, headerBottom, x + 1, frameGraphBottom, 0xFF2A2A2A);
            frame += frameStride;
        }

        EntityModelComponent entityModelComponent = model3DViewer.getComponent(EntityModelComponent.class);

        // Draws out-of-frame areas
        if (
            entityModelComponent != null
            && entityModelComponent.getAnimator() instanceof EntityModelComponent.EditorScrubAnimator headlessAnimator
            && headlessAnimator.getPreviewAnimation() != null
        ) {
            int x = getX();
            int frameZeroX = frameToX(0.0F);

            if (x < frameZeroX) { // Visibility check for left side bound
                g.fill(x, headerBottom, frameZeroX, frameGraphBottom, 0x88000000);
            }

            // Get a frame time by multiplying seconds * base FPS (60)
            float totalFrames = totalFrames() * 60.0F;

            if (totalFrames < visualFrameEnd) { // Visibility check for right side bound
                g.fill(frameToX(totalFrames), headerBottom, getRight(), frameGraphBottom, 0x88000000);
            }
        }

        for (AnimTrackEntry row : trackList.children()) {
            // Draw each row's track-area widgets after the frame measures
            row.extractTrackArea(g, mouseX, mouseY, a);
        }

        g.disableScissor();

        super.extractWidgetRenderState(g, mouseX, mouseY, a);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        // While a row is renaming, route input to its inline editor first; clicks outside
        // it commit and dismiss before falling through to the normal hit-test.
        AnimTrackEntry editing = editingRow();
        if (editing != null) {
            AbstractWidget editor = editing.activeInlineEditor();
            if (editor != null && editor.isMouseOver(event.x(), event.y())) {
                editor.mouseClicked(event, doubleClick);
                setFocused(editor);
                return true;
            }

            editing.commitInlineEdit();
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (frameIndicator.mouseDragged(event, dx, dy)) return true;
        if (scrollBar.mouseDragged(event, dx, dy)) return true;
        if (trackList.mouseDragged(event, dx, dy)) return true;

        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        boolean consumed = false;

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
        AnimTrackEntry editing = editingRow();
        if (editing != null) {
            if (event.isEscape()) {
                editing.commitInlineEdit();
                return true;
            }

            AbstractWidget editor = editing.activeInlineEditor();
            if (editor != null) return editor.keyPressed(event);
        }

        // Not editing: RENAME (F2) starts the selected row's inline rename, if it has one.
        if (EditorKeyMappings.RENAME.matches(event)) {
            AnimTrackEntry selected = selectedRow();
            if (selected != null) {
                selected.beginInlineEdit();
                return true;
            }
        }

        // DELETE removes the selected row's selected item (e.g. a montage segment), if any.
        if (EditorKeyMappings.DELETE.matches(event)) {
            AnimTrackEntry selected = selectedRow();
            if (selected != null) {
                selected.deleteSelected();
                return true;
            }
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        AnimTrackEntry editing = editingRow();
        if (editing != null) {
            AbstractWidget editor = editing.activeInlineEditor();
            if (editor != null) return editor.charTyped(event);
        }

        return super.charTyped(event);
    }

    private boolean isMouseOverTrack(double x, double y) {
        return isActive() && x >= (double)getTrackStart() && y >= (double)getY() && x < (double)getRight() && y < (double)getBottom();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (isMouseOverTrack(mx, my)) {
            float xFraction = (float)(mx - (getX() + AnimationTrackList.NAME_BLOCK_W + DIVIDER_W)) / getWidth();
            float scope = visualFrameEnd - visualFrameStart;
            float zoom = scope * 0.1F;
            int sign = Mth.sign(scrollY);

            visualFrameStart += xFraction * zoom * sign;
            visualFrameEnd -= MathUtils.oneMinus(xFraction) * zoom * sign;

            return true;
        }

        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }
}