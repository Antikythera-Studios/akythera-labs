package com.yesman.akytheralabs.gui.widget.curve;

import com.yesman.akythera.client.api.gui.ContextMenuContainer;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.core.animation.keyframe.FloatCurve;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
import com.yesman.akytheralabs.editor.historytrack.DraggingDataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.PendingDataChange;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// Edits a [FloatCurve] over a clip's time, the value running from 0 at the bottom to 1 at the top. Each key is a child
/// widget the mouse drags between its neighbors. A click on the plot moves the playhead, a double click adds a key, the
/// right button opens Add Key / Remove Key / Toggle Smooth, and Delete removes the selected key. Every edit is one
/// undoable step.
public final class FloatCurveEditorWidget extends AbstractContainerWidget implements AnchoredWidget {
    private static final int PADDING = 16;
    private static final int KEY_RADIUS = 4;

    /// Closest two keys may come in time, in seconds.
    private static final float MIN_KEY_GAP = 1.0E-3F;

    /// Fewest pixels between two time grid lines.
    private static final int MIN_GRID_PIXELS = 40;

    private static final float[] GRID_STEPS = { 0.05F, 0.1F, 0.25F, 0.5F, 1.0F, 2.0F, 5.0F, 10.0F };

    private static final int BACKGROUND_COLOR = 0xFF1A1A1A;
    private static final int GRID_COLOR = 0xFF2A2A2A;
    private static final int AXIS_COLOR = 0xFF3A3A3A;
    private static final int CURVE_COLOR = 0xFF44AAFF;
    private static final int KEY_COLOR = 0xFF44AAFF;
    private static final int SMOOTH_KEY_COLOR = 0xFF66DD88;
    private static final int SELECTED_KEY_COLOR = 0xFFFFCC00;
    private static final int PLAYHEAD_COLOR = 0xFFFF4444;
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

    /// Where the preview plays, in seconds.
    private final Supplier<Float> playhead;

    /// Moves the preview to a time, in seconds.
    private final Consumer<Float> scrub;

    /// Shown in place of the plot while no curve is picked.
    private final Component unpickedMessage;

    private final ContextMenuContainer contextMenu;
    private final List<KeyWidget> keyWidgets = new ArrayList<>();
    private @Nullable KeyWidget selectedKey;

    /// Whether a press on the plot is moving the playhead.
    private boolean scrubbing;

    /// Where the context menu opened, in seconds and in curve value.
    private float contextTime;
    private float contextValue;

    public FloatCurveEditorWidget(
        final Minecraft minecraft,
        final Supplier<@Nullable FloatCurve> curve,
        final Consumer<@Nullable FloatCurve> curveSetter,
        final BooleanSupplier picked,
        final Supplier<Float> duration,
        final Supplier<Float> playhead,
        final Consumer<Float> scrub,
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
        this.playhead = playhead;
        this.scrub = scrub;
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
        if (!picked.getAsBoolean() || !isMouseOver(event.x(), event.y())) return false;

        syncKeyWidgets();

        for (KeyWidget key : keyWidgets) {
            if (key.isMouseOver(event.x(), event.y()) && key.mouseClicked(event, doubleClick)) {
                setFocused(key);
                setDragging(event.button() == 0);
                return true;
            }
        }

        boolean inPlot = plotBoundary().containsPoint((int)event.x(), (int)event.y());

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
                scrub.accept(toTime(event.x()));
                setDragging(true);
            }

            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(final @NonNull MouseButtonEvent event, final double dx, final double dy) {
        if (scrubbing) {
            scrub.accept(toTime(event.x()));
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
            return true;
        }

        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(final @NonNull KeyEvent event) {
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
        graphics.fill(getX(), getY(), getRight(), getBottom(), BACKGROUND_COLOR);

        if (!picked.getAsBoolean()) {
            graphics.text(
                font, unpickedMessage, getX() + (getWidth() - font.width(unpickedMessage)) / 2, getY() + (getHeight() - font.lineHeight) / 2,
                LABEL_COLOR, false
            );
            return;
        }

        syncKeyWidgets();

        ScreenRectangle plot = plotBoundary();
        float length = Math.max(duration.get(), MIN_KEY_GAP);
        float step = GRID_STEPS[GRID_STEPS.length - 1];

        for (float candidate : GRID_STEPS) {
            if (candidate / length * plot.width() >= MIN_GRID_PIXELS) {
                step = candidate;
                break;
            }
        }

        for (float time = 0.0F; time <= length + MIN_KEY_GAP; time += step) {
            int x = toScreenX(time);
            graphics.fill(x, plot.top(), x + 1, plot.bottom(), GRID_COLOR);
            graphics.text(font, String.format("%.2f", time), x + 2, plot.bottom() + 2, LABEL_COLOR, false);
        }

        for (int i = 0; i <= 4; i++) {
            int y = toScreenY(i / 4.0F);
            graphics.fill(plot.left(), y, plot.right(), y + 1, i == 0 || i == 4 ? AXIS_COLOR : GRID_COLOR);
        }

        graphics.text(font, "1", getX() + 4, toScreenY(1.0F) - font.lineHeight / 2, LABEL_COLOR, false);
        graphics.text(font, "0", getX() + 4, toScreenY(0.0F) - font.lineHeight / 2, LABEL_COLOR, false);

        FloatCurve shown = curve.get();

        if (shown != null) {
            int previousY = toScreenY(shown.valueAt(toTime(plot.left())));

            for (int x = plot.left(); x < plot.right(); x++) {
                int y = toScreenY(shown.valueAt(toTime(x)));
                graphics.fill(x, Math.min(previousY, y), x + 1, Math.max(previousY, y) + 1, CURVE_COLOR);
                previousY = y;
            }
        }

        for (KeyWidget key : keyWidgets) key.extractRenderState(graphics, mouseX, mouseY, a);

        int playheadX = toScreenX(Math.clamp(playhead.get(), 0.0F, length));
        graphics.fill(playheadX, plot.top(), playheadX + 1, plot.bottom(), PLAYHEAD_COLOR);
    }

    /// The plotting rectangle: the widget less its padding.
    private ScreenRectangle plotBoundary() {
        return new ScreenRectangle(getX() + PADDING, getY() + PADDING, Math.max(1, getWidth() - PADDING * 2), Math.max(1, getHeight() - PADDING * 2));
    }

    private int toScreenX(final float time) {
        ScreenRectangle plot = plotBoundary();

        return plot.left() + Math.round(time / Math.max(duration.get(), MIN_KEY_GAP) * plot.width());
    }

    private int toScreenY(final float value) {
        ScreenRectangle plot = plotBoundary();

        return plot.bottom() - Math.round(value * plot.height());
    }

    private float toTime(final double screenX) {
        ScreenRectangle plot = plotBoundary();

        return Math.clamp((float)(screenX - plot.left()) / plot.width(), 0.0F, 1.0F) * duration.get();
    }

    private float toValue(final double screenY) {
        ScreenRectangle plot = plotBoundary();

        return Math.clamp((float)(plot.bottom() - screenY) / plot.height(), 0.0F, 1.0F);
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
        return Collections.unmodifiableList(keyWidgets);
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
            return isActive() && Math.abs(mouseX - getX()) <= KEY_RADIUS && Math.abs(mouseY - getY()) <= KEY_RADIUS;
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
