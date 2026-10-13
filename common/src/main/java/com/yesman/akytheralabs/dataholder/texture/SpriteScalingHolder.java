package com.yesman.akytheralabs.dataholder.texture;

import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;

import java.util.Map;
import java.util.function.Supplier;

/// Editor twin of one [GuiSpriteScaling] type's parameters, as [EditorEditable] fields the Details
/// panel binds rows to.
public abstract class SpriteScalingHolder {
    private static final Map<GuiSpriteScaling.Type, Supplier<SpriteScalingHolder>> FACTORIES = Map.of(
        GuiSpriteScaling.Type.STRETCH, Stretch::new,
        GuiSpriteScaling.Type.TILE, Tile::new,
        GuiSpriteScaling.Type.NINE_SLICE, NineSlice::new
    );

    /// A fresh holder of `type`, at that type's defaults.
    public static SpriteScalingHolder create(final GuiSpriteScaling.Type type) {
        return FACTORIES.get(type).get();
    }

    /// A holder filled from `scaling`.
    public static SpriteScalingHolder of(final GuiSpriteScaling scaling) {
        SpriteScalingHolder holder = create(scaling.type());
        holder.readFrom(scaling);

        return holder;
    }

    /// The scaling type this holder authors.
    public abstract GuiSpriteScaling.Type type();

    /// Overwrites the parameters from `scaling`, which is of this holder's type.
    protected abstract void readFrom(final GuiSpriteScaling scaling);

    public abstract GuiSpriteScaling makeScaling();

    /// Shown as the row's value by a type with no parameters.
    @Override
    public String toString() {
        return ParseUtils.toDisplayName(type().name());
    }

    /// Stretches the whole sprite over the drawn area. Takes no parameters.
    public static final class Stretch extends SpriteScalingHolder {
        @Override
        public GuiSpriteScaling.Type type() {
            return GuiSpriteScaling.Type.STRETCH;
        }

        @Override
        protected void readFrom(final GuiSpriteScaling scaling) {
        }

        @Override
        public GuiSpriteScaling makeScaling() {
            return new GuiSpriteScaling.Stretch();
        }
    }

    /// Repeats the sprite over the drawn area, as a `width` × `height` tile.
    public static final class Tile extends SpriteScalingHolder {
        @EditorEditable(label = LangKeys.GUI_TEXTURE_PREVIEW_SCREEN_WIDGET_WIDTH, order = 0, min = 1)
        private int width = 16;

        @EditorEditable(label = LangKeys.GUI_TEXTURE_PREVIEW_SCREEN_WIDGET_HEIGHT, order = 1, min = 1)
        private int height = 16;

        @Override
        public GuiSpriteScaling.Type type() {
            return GuiSpriteScaling.Type.TILE;
        }

        @Override
        protected void readFrom(final GuiSpriteScaling scaling) {
            if (!(scaling instanceof GuiSpriteScaling.Tile tile)) return;

            width = tile.width();
            height = tile.height();
        }

        @Override
        public GuiSpriteScaling makeScaling() {
            return new GuiSpriteScaling.Tile(width, height);
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(final int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(final int height) {
            this.height = height;
        }
    }

    /// Keeps the four borders of a `width` × `height` sprite at their size and stretches, or tiles,
    /// the center and the edges between them.
    public static final class NineSlice extends SpriteScalingHolder {
        @EditorEditable(label = LangKeys.GUI_TEXTURE_PREVIEW_SCREEN_WIDGET_WIDTH, order = 0, min = 1)
        private int width = 16;

        @EditorEditable(label = LangKeys.GUI_TEXTURE_PREVIEW_SCREEN_WIDGET_HEIGHT, order = 1, min = 1)
        private int height = 16;

        @EditorEditable(label = LangKeys.GUI_TEXTURE_PREVIEW_SCREEN_WIDGET_BORDER_LEFT, order = 2, min = 0)
        private int borderLeft = 4;

        @EditorEditable(label = LangKeys.GUI_TEXTURE_PREVIEW_SCREEN_WIDGET_BORDER_TOP, order = 3, min = 0)
        private int borderTop = 4;

        @EditorEditable(label = LangKeys.GUI_TEXTURE_PREVIEW_SCREEN_WIDGET_BORDER_RIGHT, order = 4, min = 0)
        private int borderRight = 4;

        @EditorEditable(label = LangKeys.GUI_TEXTURE_PREVIEW_SCREEN_WIDGET_BORDER_BOTTOM, order = 5, min = 0)
        private int borderBottom = 4;

        @EditorEditable(label = LangKeys.GUI_TEXTURE_PREVIEW_SCREEN_WIDGET_STRETCH_INNER, order = 6)
        private boolean stretchInner;

        @Override
        public GuiSpriteScaling.Type type() {
            return GuiSpriteScaling.Type.NINE_SLICE;
        }

        @Override
        protected void readFrom(final GuiSpriteScaling scaling) {
            if (!(scaling instanceof GuiSpriteScaling.NineSlice nineSlice)) return;

            width = nineSlice.width();
            height = nineSlice.height();
            borderLeft = nineSlice.border().left();
            borderTop = nineSlice.border().top();
            borderRight = nineSlice.border().right();
            borderBottom = nineSlice.border().bottom();
            stretchInner = nineSlice.stretchInner();
        }

        @Override
        public GuiSpriteScaling makeScaling() {
            return new GuiSpriteScaling.NineSlice(
                width,
                height,
                new GuiSpriteScaling.NineSlice.Border(borderLeft, borderTop, borderRight, borderBottom),
                stretchInner
            );
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(final int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(final int height) {
            this.height = height;
        }

        public int getBorderLeft() {
            return borderLeft;
        }

        public void setBorderLeft(final int borderLeft) {
            this.borderLeft = borderLeft;
        }

        public int getBorderTop() {
            return borderTop;
        }

        public void setBorderTop(final int borderTop) {
            this.borderTop = borderTop;
        }

        public int getBorderRight() {
            return borderRight;
        }

        public void setBorderRight(final int borderRight) {
            this.borderRight = borderRight;
        }

        public int getBorderBottom() {
            return borderBottom;
        }

        public void setBorderBottom(final int borderBottom) {
            this.borderBottom = borderBottom;
        }

        public boolean getStretchInner() {
            return stretchInner;
        }

        public void setStretchInner(final boolean stretchInner) {
            this.stretchInner = stretchInner;
        }
    }
}
