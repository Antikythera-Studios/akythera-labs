package com.yesman.akytheralabs.gui.widget;

import com.yesman.akytheralabs.dataholder.texture.SpriteScalingHolder;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/// One border of a nine-slice sprite, drawn as a green line over a [TexturePreviewer] while the sprite
/// scales as a nine-slice. Dragging it sets that border, in the nine-slice's own units, as one undoable
/// change.
public final class NineSliceGuide extends AbstractWidget {
    private static final int COLOR = 0xFF3CDC50;
    private static final int COLOR_ACTIVE = 0xFFA8FFB4;

    /// How far the grab band reaches on each side of the line, in widget pixels.
    private static final int GRIP = 2;

    /// The border a guide stands for, with the image edge it is measured from.
    public enum Edge {
        LEFT(true, SpriteScalingHolder.NineSlice::getBorderLeft, SpriteScalingHolder.NineSlice::setBorderLeft),
        TOP(false, SpriteScalingHolder.NineSlice::getBorderTop, SpriteScalingHolder.NineSlice::setBorderTop),
        RIGHT(true, SpriteScalingHolder.NineSlice::getBorderRight, SpriteScalingHolder.NineSlice::setBorderRight),
        BOTTOM(false, SpriteScalingHolder.NineSlice::getBorderBottom, SpriteScalingHolder.NineSlice::setBorderBottom);

        /// Whether the line runs top to bottom, measuring a horizontal border.
        private final boolean vertical;
        private final ToIntFunction<SpriteScalingHolder.NineSlice> getter;
        private final ObjIntConsumer<SpriteScalingHolder.NineSlice> setter;

        Edge(
            final boolean vertical,
            final ToIntFunction<SpriteScalingHolder.NineSlice> getter,
            final ObjIntConsumer<SpriteScalingHolder.NineSlice> setter
        ) {
            this.vertical = vertical;
            this.getter = getter;
            this.setter = setter;
        }

        /// Whether the border is measured from the image's right or bottom edge.
        private boolean fromFarEdge() {
            return this == RIGHT || this == BOTTOM;
        }

        private Edge opposite() {
            return switch (this) {
                case LEFT -> RIGHT;
                case TOP -> BOTTOM;
                case RIGHT -> LEFT;
                case BOTTOM -> TOP;
            };
        }
    }

    private final TexturePreviewer previewer;

    /// The nine-slice being edited, or null while the sprite scales some other way.
    private final Supplier<SpriteScalingHolder.@Nullable NineSlice> nineSlice;

    private final Edge edge;

    /// Runs after the border changes, from a drag or its undo / redo.
    private final Runnable onChanged;

    /// The border as it was when the drag started. Null while nothing is dragged.
    private @Nullable Integer borderBeforeDrag;

    public NineSliceGuide(
        final TexturePreviewer previewer,
        final Supplier<SpriteScalingHolder.@Nullable NineSlice> nineSlice,
        final Edge edge,
        final Runnable onChanged
    ) {
        super(0, 0, 0, 0, Component.literal("nine_slice_guide"));

        this.previewer = previewer;
        this.nineSlice = nineSlice;
        this.edge = edge;
        this.onChanged = onChanged;
    }

    /// Lays the guide over its border and draws it. With no nine-slice or no image, it shrinks to
    /// nothing, so it takes no press either.
    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        SpriteScalingHolder.NineSlice slice = nineSlice.get();
        ScreenRectangle image = previewer.imageRectangle();

        int units = slice == null ? 0 : edge.vertical ? slice.getWidth() : slice.getHeight();

        if (slice == null || image == null || units <= 0) {
            setSize(0, 0);
            return;
        }

        int span = edge.vertical ? image.width() : image.height();
        int offset = Math.round((float)edge.getter.applyAsInt(slice) * span / units);

        // A far-edge line sits one pixel inside the image, so a zero border stays visible
        int at = switch (edge) {
            case LEFT -> image.left() + offset;
            case TOP -> image.top() + offset;
            case RIGHT -> image.right() - offset - 1;
            case BOTTOM -> image.bottom() - offset - 1;
        };

        int color = isHovered() || borderBeforeDrag != null ? COLOR_ACTIVE : COLOR;

        if (edge.vertical) {
            setRectangle(GRIP * 2 + 1, image.height(), at - GRIP, image.top());
            graphics.fill(at, image.top(), at + 1, image.bottom(), color);
        } else {
            setRectangle(image.width(), GRIP * 2 + 1, image.left(), at - GRIP);
            graphics.fill(image.left(), at, image.right(), at + 1, color);
        }
    }

    @Override
    public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {
        SpriteScalingHolder.NineSlice slice = nineSlice.get();
        if (slice != null) borderBeforeDrag = edge.getter.applyAsInt(slice);
    }

    /// Moves the border under the cursor, in whole nine-slice units. It stops one unit short of the
    /// opposite border, so the center slice never vanishes.
    @Override
    protected void onDrag(@NonNull MouseButtonEvent event, double dx, double dy) {
        SpriteScalingHolder.NineSlice slice = nineSlice.get();
        ScreenRectangle image = previewer.imageRectangle();

        if (slice == null || image == null || borderBeforeDrag == null) return;

        float fraction = edge.vertical
            ? (float)(event.x() - image.left()) / image.width()
            : (float)(event.y() - image.top()) / image.height();

        if (edge.fromFarEdge()) fraction = 1.0F - fraction;

        int units = edge.vertical ? slice.getWidth() : slice.getHeight();
        int oppositeBorder = edge.opposite().getter.applyAsInt(slice);
        int border = Mth.clamp(Math.round(fraction * units), 0, Math.max(0, units - oppositeBorder - 1));

        edge.setter.accept(slice, border);
    }

    @Override
    public void onRelease(@NonNull MouseButtonEvent event) {
        SpriteScalingHolder.NineSlice slice = nineSlice.get();
        Integer before = borderBeforeDrag;

        borderBeforeDrag = null;

        if (slice == null || before == null) return;

        int after = edge.getter.applyAsInt(slice);

        if (before == after) return;

        HistoryManager.getInstance().pushHistory(slice, new DataChange<>(before, after, border -> {
            edge.setter.accept(slice, border);
            onChanged.run();
        }));

        onChanged.run();
    }

    @Override
    public void playDownSound(@NonNull SoundManager soundManager) {
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }
}
