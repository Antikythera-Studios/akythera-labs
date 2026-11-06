package com.yesman.akytheralabs.gui.widget.curve;

import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.ContextMenuContainer;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.gui.viewer.EntityModelComponent;
import com.yesman.akythera.core.animation.keyframe.FloatCurve;
import com.yesman.akythera.core.util.helper.MathUtils;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
import com.yesman.akytheralabs.editor.historytrack.DraggingDataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.PendingDataChange;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.EditorEditBox;
import com.yesman.akytheralabs.gui.widget.animation.AnimationTrackEditorPanel;
import com.yesman.akytheralabs.gui.widget.animation.CurrentFrameIndicator;
import com.yesman.akytheralabs.gui.widget.animation.FrameScrollBar;
import com.yesman.akytheralabs.gui.widget.animation.PlayheadMove;
import com.yesman.akytheralabs.gui.widget.animation.TrackTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// Edits a [FloatCurve] over a clip's time, the value running from 0 at the bottom to 1 at the top. Each key is a child
/// widget the mouse drags between its neighbors. A click on the plot moves the playhead, a double click adds a key, the
/// right button opens Add Key / Remove Key / Toggle Smooth, and Delete removes the selected key. Every edit is one
/// undoable step.
///
/// The time axis is a [TrackTimeline] in frames: a ruler with the playhead's handle, the clip's outside masked, and a
/// footer with play, pause, the current frame and the zoom bar. The wheel zooms around the cursor.
public final class FloatCurveEditorWidget extends AbstractContainerWidget implements AnchoredWidget, TrackTimeline {
    private static final int PADDING = 16;    // left and right of the plot, where the value labels sit
    private static final int PLOT_MARGIN = 6; // above and below the plot
    private static final int HEADER_H = AnimationTrackEditorPanel.HEADER_H;
    private static final int FOOTER_H = AnimationTrackEditorPanel.FOOTER_H;
    private static final int FOOTER_INSET = 2;
    private static final int FOOTER_BUTTON = FOOTER_H - FOOTER_INSET * 2;
    private static final int FRAME_BOX_W = 40;
    private static final int KEY_RADIUS = 4;

    /// Closest two keys may come in time, in seconds.
    private static final float MIN_KEY_GAP = 1.0E-3F;

    private static final int BACKGROUND_COLOR = 0xFF1A1A1A;
    private static final int HEADER_COLOR = 0xFF252525;
    private static final int DIVIDER_COLOR = 0xFF404040;
    private static final int GRID_COLOR = 0xFF2A2A2A;
    private static final int AXIS_COLOR = 0xFF3A3A3A;
    private static final int MASK_COLOR = 0x88000000; // outside the clip
    private static final int CURVE_COLOR = 0xFF44AAFF;
    private static final int KEY_COLOR = 0xFF44AAFF;
    private static final int SMOOTH_KEY_COLOR = 0xFF66DD88;
    private static final int SELECTED_KEY_COLOR = 0xFFFFCC00;
    private static final int RULER_LABEL_COLOR = 0xFF999999;
    private static final int LABEL_COLOR = 0xFF666666;

    private final Font font;

    /// The edited curve, null while it has no key.
    private final Supplier<@Nullable FloatCurve> curve;

    /// Writes the edited curve, null to remove it.
    private final Consumer<@Nullable FloatCurve> curveSetter;

    /// Whether a curve is picked to edit.
    private final BooleanSupplier picked;

    /// The length of the clip the curve runs over, in seconds.
    private final Supplier<Float> duration;

    /// The preview's animator, which holds the playhead. Null while the preview plays nothing.
    private final Supplier<EntityModelComponent.@Nullable EditorScrubAnimator> animator;

    /// Shown in place of the plot while no curve is picked.
    private final Component unpickedMessage;

    private final ContextMenuContainer contextMenu;
    private final List<KeyWidget> keyWidgets = new ArrayList<>();
    private @Nullable KeyWidget selectedKey;

    private final CurrentFrameIndicator frameIndicator;
    private final AkytheraButton playButton;
    private final AkytheraButton pauseButton;
    private final EditorEditBox<String> frameBox;
    private final FrameScrollBar scrollBar;

    /// The playhead, the footer and the zoom bar, which take a press before the keys and the plot.
    private final List<AbstractWidget> controls;

    /// Whether the preview advances by itself.
    private boolean playing = true;

    /// The visible window of the time axis, in frames.
    private float visualFrameStart;
    private float visualFrameEnd;

    /// Set once the visible window is fitted to a clip with length.
    private boolean windowFitted;

    /// Whether a press on the plot is moving the playhead.
    private boolean scrubbing;

    /// The frame the playhead was at when the scrubbing press landed.
    private float pressFrame;

    /// Where the context menu opened, in seconds and in curve value.
    private float contextTime;
    private float contextValue;

    public FloatCurveEditorWidget(
        final Minecraft minecraft,
        final Supplier<@Nullable FloatCurve> curve,
        final Consumer<@Nullable FloatCurve> curveSetter,
        final BooleanSupplier picked,
        final Supplier<Float> duration,
        final Supplier<EntityModelComponent.@Nullable EditorScrubAnimator> animator,
        final Component unpickedMessage,
        final Unit x1, final Unit x2, final Unit y1, final Unit y2,
        final AnchoredWidget.HorizontalAnchorType horizontalAnchorType,
        final AnchoredWidget.VerticalAnchorType verticalAnchorType
    ) {
        super(0, 0, 0, 0, Component.empty(), AbstractScrollArea.defaultSettings(4));

        this.font = minecraft.font;
        this.curve = curve;
        this.curveSetter = curveSetter;
        this.picked = picked;
        this.duration = duration;
        this.animator = animator;
        this.unpickedMessage = unpickedMessage;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchorType;
        this.verticalAnchorType = verticalAnchorType;

        this.contextMenu = ContextMenuContainer.builder(minecraft).pageWidth(110)
            .entry()
                .message(Component.translatable(LangKeys.GUI_ANIMATION_ASSET_SCREEN_MESSAGE_ADD_KEY))
                .onPress(_ -> addKey(contextTime, contextValue))
                .done()
            .entry()
                .predicate(() -> selectedKey != null)
                .message(Component.translatable(LangKeys.GUI_ANIMATION_ASSET_SCREEN_MESSAGE_REMOVE_KEY))
                .onPress(_ -> {
                    if (selectedKey != null) removeKey(selectedKey.index);
                })
                .done()
            .entry()
                .predicate(() -> selectedKey != null)
                .message(Component.translatable(LangKeys.GUI_ANIMATION_ASSET_SCREEN_MESSAGE_TOGGLE_SMOOTH))
                .onPress(_ -> {
                    FloatCurve before = curve.get();

                    if (selectedKey == null || before == null || selectedKey.index >= before.keys().size()) return;

                    List<FloatCurve.Key> keys = new ArrayList<>(before.keys());
                    FloatCurve.Key key = keys.get(selectedKey.index);
                    keys.set(selectedKey.index, new FloatCurve.Key(key.time(), key.value(), !key.smooth()));
                    commit(before, new FloatCurve(keys));
                })
                .done()
            .build();

        this.frameIndicator = new CurrentFrameIndicator(this);

        this.playButton = AkytheraButton.buttonBuilder(font, LocalizedMessage.EMPTY, () -> playing = true)
            .xUnits(AnchoredWidget.pixel(FOOTER_INSET), AnchoredWidget.pixel(FOOTER_BUTTON))
            .yUnits(AnchoredWidget.pixel(FOOTER_INSET), AnchoredWidget.pixel(FOOTER_BUTTON))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT)
            .theme(AkytheraLabsButtonTheme.ANIM_TRACK_RESUME_BUTTON)
            .build();

        this.pauseButton = AkytheraButton.buttonBuilder(font, LocalizedMessage.EMPTY, () -> playing = false)
            .xUnits(AnchoredWidget.pixel(FOOTER_INSET * 2 + FOOTER_BUTTON), AnchoredWidget.pixel(FOOTER_BUTTON))
            .yUnits(AnchoredWidget.pixel(FOOTER_INSET), AnchoredWidget.pixel(FOOTER_BUTTON))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT)
            .theme(AkytheraLabsButtonTheme.ANIM_TRACK_PAUSE_BUTTON)
            .build();

        this.frameBox = EditorEditBox.filteredStringBox(
            font,
            s -> s.matches("\\d*"),
            Component.empty(),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT
        );
        frameBox.setX1(AnchoredWidget.pixel(FOOTER_INSET * 3 + FOOTER_BUTTON * 2));
        frameBox.setX2(AnchoredWidget.pixel(FRAME_BOX_W));
        frameBox.setY1(AnchoredWidget.pixel(FOOTER_INSET + 1));
        frameBox.setY2(AnchoredWidget.pixel(FOOTER_BUTTON - 2));
        frameBox.setOnCommitted((_, _) -> {
            String text = frameBox.getText();

            if (!text.isEmpty()) scrubTo(Integer.parseInt(text));
        });

        this.scrollBar = new FrameScrollBar(this);
        scrollBar.setX1(AnchoredWidget.pixel(FOOTER_INSET * 4 + FOOTER_BUTTON * 2 + FRAME_BOX_W));

        this.controls = List.of(playButton, pauseButton, frameBox, scrollBar, frameIndicator);
    }

    // ***************************************************************
    // Edits
    // ***************************************************************

    private void addKey(final float time, final float value) {
        FloatCurve before = curve.get();
        List<FloatCurve.Key> keys = new ArrayList<>(before == null ? List.of() : before.keys());
        keys.add(new FloatCurve.Key(Math.clamp(time, 0.0F, duration.get()), Math.clamp(value, 0.0F, 1.0F), false));
        commit(before, new FloatCurve(keys));
    }

    private void removeKey(final int index) {
        FloatCurve before = curve.get();

        if (before == null || index >= before.keys().size()) return;

        List<FloatCurve.Key> keys = new ArrayList<>(before.keys());
        keys.remove(index);
        selectedKey = null;
        commit(before, keys.isEmpty() ? null : new FloatCurve(keys));
    }

    /// Writes `after` and records the change from `before` as one undoable step.
    private void commit(final @Nullable FloatCurve before, final @Nullable FloatCurve after) {
        curveSetter.accept(after);
        HistoryManager.getInstance().pushHistory(new DataChange<>(before, after, curveSetter));
        syncKeyWidgets();
    }

    /// One [KeyWidget] per key of the curve, made anew when the key count changes.
    private void syncKeyWidgets() {
        FloatCurve shown = picked.getAsBoolean() ? curve.get() : null;
        int keyCount = shown == null ? 0 : shown.keys().size();

        if (keyWidgets.size() == keyCount) return;

        keyWidgets.clear();
        selectedKey = null;

        for (int i = 0; i < keyCount; i++) keyWidgets.add(new KeyWidget(i));
    }

    // ***************************************************************
    // Input
    // ***************************************************************

    @Override
    public boolean mouseClicked(final @NonNull MouseButtonEvent event, final boolean doubleClick) {
        if (!isMouseOver(event.x(), event.y())) return false;

        for (AbstractWidget control : controls) {
            if (control.mouseClicked(event, doubleClick)) {
                setFocused(control);
                setDragging(event.button() == 0);
                return true;
            }
        }

        setFocused(null);

        if (!picked.getAsBoolean()) return false;

        syncKeyWidgets();

        for (KeyWidget key : keyWidgets) {
            if (key.isMouseOver(event.x(), event.y()) && key.mouseClicked(event, doubleClick)) {
                setFocused(key);
                setDragging(event.button() == 0);
                return true;
            }
        }

        boolean inPlot = trackArea().containsPoint((int)event.x(), (int)event.y());

        if (event.button() == 1 && inPlot) {
            selectedKey = keyWidgets.stream().filter(key -> key.isMouseOver(event.x(), event.y())).findFirst().orElse(null);
            contextTime = toTime(event.x());
            contextValue = toValue(event.y());
            ModalManager.getInstance().openModalWidget(contextMenu, (int)event.x(), (int)event.y());
            return true;
        }

        if (event.button() == 0 && inPlot) {
            selectedKey = null;

            if (doubleClick) {
                addKey(toTime(event.x()), toValue(event.y()));
            } else {
                scrubbing = true;
                pressFrame = currentFrame();
                setCurrentFrame(xToFrame(event.x()));
                setDragging(true);
            }

            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(final @NonNull MouseButtonEvent event, final double dx, final double dy) {
        if (scrubbing) {
            setCurrentFrame(xToFrame(event.x()));
            return true;
        }

        GuiEventListener focused = getFocused();

        return focused != null && isDragging() && event.button() == 0 && focused.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(final @NonNull MouseButtonEvent event) {
        if (scrubbing) {
            scrubbing = false;
            setDragging(false);

            if (currentFrame() != pressFrame) HistoryManager.getInstance().pushHistory(new PlayheadMove(this, pressFrame, currentFrame()));

            return true;
        }

        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        ScreenRectangle track = trackArea();

        if (mouseX < track.left() || mouseX >= track.right() || mouseY < getY() || mouseY >= track.bottom()) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        float xFraction = (float)(mouseX - track.left()) / track.width();
        float zoom = (visualFrameEnd - visualFrameStart) * 0.1F;
        int sign = Mth.sign(scrollY);

        visualFrameStart += xFraction * zoom * sign;
        visualFrameEnd -= MathUtils.oneMinus(xFraction) * zoom * sign;

        return true;
    }

    @Override
    public boolean keyPressed(final @NonNull KeyEvent event) {
        if (frameBox.isFocused()) return super.keyPressed(event);

        if (EditorKeyMappings.DELETE.matches(event) && selectedKey != null) {
            removeKey(selectedKey.index);
            return true;
        }

        return super.keyPressed(event);
    }

    // ***************************************************************
    // Rendering
    // ***************************************************************

    @Override
    protected void extractWidgetRenderState(final @NonNull GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
        EntityModelComponent.EditorScrubAnimator previewed = animator.get();
        float length = duration.get();

        // A preview model swap brings an animator that plays by default
        if (previewed != null) previewed.setUpdatePose(playing);

        if (!windowFitted && length > 0.0F) {
            visualFrameStart = -length * 0.1F * 60.0F;
            visualFrameEnd = length * 1.1F * 60.0F;
            windowFitted = true;
        }

        playButton.active = !playing;
        pauseButton.active = playing;

        if (!frameBox.isFocused()) frameBox.setValueByString(String.valueOf((int)currentFrame()));

        ScreenRectangle track = trackArea();
        int headerBottom = getY() + HEADER_H;

        graphics.fill(getX(), getY(), getRight(), getBottom(), BACKGROUND_COLOR);
        graphics.fill(getX(), getY(), getRight(), headerBottom, HEADER_COLOR);
        graphics.fill(getX(), headerBottom - 1, getRight(), headerBottom, DIVIDER_COLOR);
        graphics.fill(getX(), track.bottom(), getRight(), getBottom(), HEADER_COLOR);
        graphics.fill(getX(), track.bottom(), getRight(), track.bottom() + 1, DIVIDER_COLOR);

        graphics.enableScissor(track.left(), getY(), track.right(), track.bottom());

        int stride = TrackTimeline.rulerStride(visualFrameEnd - visualFrameStart);

        for (int frame = Mth.ceil(visualFrameStart / stride) * stride; frame < visualFrameEnd; frame += stride) {
            int x = frameToX(frame);
            graphics.centeredText(font, String.valueOf(frame), x, headerBottom - (font.lineHeight + 2), RULER_LABEL_COLOR);
            graphics.fill(x, headerBottom, x + 1, track.bottom(), GRID_COLOR);
        }

        int zeroX = frameToX(0.0F);
        int endX = frameToX(length * 60.0F);

        if (track.left() < zeroX) graphics.fill(track.left(), headerBottom, zeroX, track.bottom(), MASK_COLOR);
        if (endX < track.right()) graphics.fill(endX, headerBottom, track.right(), track.bottom(), MASK_COLOR);

        boolean editing = picked.getAsBoolean();

        if (editing) {
            syncKeyWidgets();

            for (int i = 0; i <= 4; i++) {
                int y = toScreenY(i / 4.0F);
                graphics.fill(track.left(), y, track.right(), y + 1, i == 0 || i == 4 ? AXIS_COLOR : GRID_COLOR);
            }

            FloatCurve shown = curve.get();

            if (shown != null) {
                int from = Math.max(track.left(), zeroX);
                int to = Math.min(track.right(), endX);
                int previousY = toScreenY(shown.valueAt(toTime(from)));

                for (int x = from; x < to; x++) {
                    int y = toScreenY(shown.valueAt(toTime(x)));
                    graphics.fill(x, Math.min(previousY, y), x + 1, Math.max(previousY, y) + 1, CURVE_COLOR);
                    previousY = y;
                }
            }

            for (KeyWidget key : keyWidgets) key.extractRenderState(graphics, mouseX, mouseY, a);
        }

        graphics.disableScissor();

        if (editing) {
            graphics.text(font, "1", getX() + 4, toScreenY(1.0F) - font.lineHeight / 2, LABEL_COLOR, false);
            graphics.text(font, "0", getX() + 4, toScreenY(0.0F) - font.lineHeight / 2, LABEL_COLOR, false);
        } else {
            graphics.text(
                font, unpickedMessage, track.left() + (track.width() - font.width(unpickedMessage)) / 2, track.top() + (track.height() - font.lineHeight) / 2,
                LABEL_COLOR, false
            );
        }

        for (AbstractWidget control : controls) control.extractRenderState(graphics, mouseX, mouseY, a);
    }

    /// The time axis's column below the ruler and above the footer, between the side paddings.
    private ScreenRectangle trackArea() {
        return new ScreenRectangle(
            getX() + PADDING, getY() + HEADER_H, Math.max(1, getWidth() - PADDING * 2), Math.max(1, getHeight() - HEADER_H - FOOTER_H)
        );
    }

    private int toScreenX(final float time) {
        return frameToX(time * 60.0F);
    }

    private int toScreenY(final float value) {
        ScreenRectangle track = trackArea();
        int plotHeight = Math.max(1, track.height() - PLOT_MARGIN * 2);

        return track.bottom() - PLOT_MARGIN - Math.round(value * plotHeight);
    }

    private float toTime(final double screenX) {
        return Math.clamp(xToFrame(screenX) / 60.0F, 0.0F, duration.get());
    }

    private float toValue(final double screenY) {
        ScreenRectangle track = trackArea();
        int plotHeight = Math.max(1, track.height() - PLOT_MARGIN * 2);

        return Math.clamp((float)(track.bottom() - PLOT_MARGIN - screenY) / plotHeight, 0.0F, 1.0F);
    }

    /// Lays the footer and the zoom bar out by their anchors, and the playhead over everything above the footer.
    @Override
    public void relocateChildren() {
        AnchoredWidget.super.relocateChildren();
        frameIndicator.setBounds(getX(), getY(), getWidth(), getHeight() - FOOTER_H);
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    protected void updateWidgetNarration(final @NonNull NarrationElementOutput narrationElementOutput) {
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        List<GuiEventListener> children = new ArrayList<>(controls);
        children.addAll(keyWidgets);

        return children;
    }

    // ***************************************************************
    // [TrackTimeline]
    // ***************************************************************

    @Override
    public float currentFrame() {
        EntityModelComponent.EditorScrubAnimator previewed = animator.get();

        return previewed == null ? 0.0F : previewed.getElapsedTime() * 60.0F;
    }

    @Override
    public void setCurrentFrame(final float frame) {
        EntityModelComponent.EditorScrubAnimator previewed = animator.get();

        if (previewed != null) previewed.setElapsedTime(frame / 60.0F);
    }

    @Override
    public String playheadLabel() {
        return String.format("%.1f", currentFrame());
    }

    /// Unclamped, so a key outside the visible window lands outside the plot.
    @Override
    public int frameToX(final float frame) {
        ScreenRectangle track = trackArea();

        if (visualFrameEnd - visualFrameStart <= 0.0F) return track.left();

        return track.left() + Math.round((frame - visualFrameStart) / (visualFrameEnd - visualFrameStart) * track.width());
    }

    @Override
    public float xToFrame(final double x) {
        ScreenRectangle track = trackArea();

        return (float)Mth.clampedLerp((x - track.left()) / track.width(), visualFrameStart, visualFrameEnd);
    }

    @Override public float visualFrameStart() { return visualFrameStart; }
    @Override public float visualFrameEnd() { return visualFrameEnd; }
    @Override public void setVisualFrameStart(final float frame) { visualFrameStart = frame; }
    @Override public void setVisualFrameEnd(final float frame) { visualFrameEnd = frame; }

    @Override
    public float timelineFrames() {
        return duration.get() * 60.0F;
    }

    /// One key of the curve, by its index. A drag keeps it between its neighbors, so the keys never reorder under it.
    private final class KeyWidget extends AbstractWidget {
        private final int index;

        private KeyWidget(final int index) {
            super(0, 0, KEY_RADIUS * 2, KEY_RADIUS * 2, Component.empty());
            this.index = index;
        }

        private FloatCurve.@Nullable Key key() {
            FloatCurve shown = curve.get();

            return shown == null || index >= shown.keys().size() ? null : shown.keys().get(index);
        }

        @Override
        public void onClick(final @NonNull MouseButtonEvent event, final boolean doubleClick) {
            selectedKey = this;
            HistoryManager.getInstance().pushHistory(new PendingDataChange<>(FloatCurveEditorWidget.this, curve.get(), curveSetter));
        }

        @Override
        protected void onDrag(final @NonNull MouseButtonEvent event, final double dx, final double dy) {
            FloatCurve shown = curve.get();
            FloatCurve.Key key = key();

            if (shown == null || key == null) return;

            List<FloatCurve.Key> keys = new ArrayList<>(shown.keys());
            float earliest = index == 0 ? 0.0F : keys.get(index - 1).time() + MIN_KEY_GAP;
            float latest = index == keys.size() - 1 ? duration.get() : keys.get(index + 1).time() - MIN_KEY_GAP;
            float time = Math.clamp(toTime(event.x()), Math.min(earliest, latest), Math.max(earliest, latest));

            keys.set(index, new FloatCurve.Key(time, toValue(event.y()), key.smooth()));
            curveSetter.accept(new FloatCurve(keys));
        }

        @Override
        public boolean mouseReleased(final @NonNull MouseButtonEvent event) {
            if (event.button() == 0) {
                HistoryManager.getInstance().pushHistory(new DraggingDataChange<>(FloatCurveEditorWidget.this, curve.get(), curveSetter));
            }

            return super.mouseReleased(event);
        }

        @Override
        public int getX() {
            FloatCurve.Key key = key();

            return key == null ? 0 : toScreenX(key.time());
        }

        @Override
        public int getY() {
            FloatCurve.Key key = key();

            return key == null ? 0 : toScreenY(key.value());
        }

        @Override
        public boolean isMouseOver(final double mouseX, final double mouseY) {
            return isActive() && trackArea().containsPoint(getX(), getY())
                && Math.abs(mouseX - getX()) <= KEY_RADIUS && Math.abs(mouseY - getY()) <= KEY_RADIUS;
        }

        @Override
        protected void extractWidgetRenderState(final @NonNull GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
            FloatCurve.Key key = key();

            if (key == null) return;

            int color = selectedKey == this ? SELECTED_KEY_COLOR : key.smooth() ? SMOOTH_KEY_COLOR : KEY_COLOR;
            int x = getX();
            int y = getY();

            for (int dy = -KEY_RADIUS; dy <= KEY_RADIUS; dy++) {
                int halfWidth = KEY_RADIUS - Math.abs(dy);
                graphics.fill(x - halfWidth, y + dy, x + halfWidth + 1, y + dy + 1, color);
            }
        }

        @Override
        protected void updateWidgetNarration(final @NonNull NarrationElementOutput narrationElementOutput) {
        }
    }

    // ***************************************************************
    // [AnchoredWidget] implementations
    // ***************************************************************
    private Unit x1;
    private Unit x2;
    private Unit y1;
    private Unit y2;
    private AnchoredWidget.HorizontalAnchorType horizontalAnchorType;
    private AnchoredWidget.VerticalAnchorType verticalAnchorType;

    @Override public Unit getX1() { return x1; }
    @Override public Unit getX2() { return x2; }
    @Override public Unit getY1() { return y1; }
    @Override public Unit getY2() { return y2; }
    @Override public void setX1(Unit unit) { x1 = unit; }
    @Override public void setX2(Unit unit) { x2 = unit; }
    @Override public void setY1(Unit unit) { y1 = unit; }
    @Override public void setY2(Unit unit) { y2 = unit; }
    @Override public HorizontalAnchorType getHorizontalAnchorType() { return horizontalAnchorType; }
    @Override public VerticalAnchorType getVerticalAnchorType() { return verticalAnchorType; }
    @Override public void setHorizontalAnchorType(HorizontalAnchorType horizontalAnchorType) { this.horizontalAnchorType = horizontalAnchorType; }
    @Override public void setVerticalAnchorType(VerticalAnchorType verticalAnchorType) { this.verticalAnchorType = verticalAnchorType; }
}
