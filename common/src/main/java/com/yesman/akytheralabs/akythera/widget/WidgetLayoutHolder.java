package com.yesman.akytheralabs.akythera.widget;

import com.mojang.serialization.Codec;
import com.yesman.akythera.core.blueprint.widget.WidgetLayout;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jspecify.annotations.Nullable;

/// Mutable editor mirror of [WidgetLayout]: the two anchor types and the four units, each a number of pixels or a
/// percentage of the parent's length.
///
/// [#CODEC] is [WidgetLayout#CODEC] mapped through, so the editor and the engine persist a layout in one shape.
/// Details edits it through [WidgetLayoutProducer].
public final class WidgetLayoutHolder extends AbstractDataHolder<WidgetLayout> {
    public static final Codec<WidgetLayoutHolder> CODEC = WidgetLayout.CODEC.xmap(WidgetLayoutHolder::of, WidgetLayoutHolder::makeObject);

    private WidgetLayout.Horizontal horizontalAnchor;

    private WidgetLayout.Vertical verticalAnchor;

    private int x1;

    private boolean x1Percent;

    private int x2;

    private boolean x2Percent;

    private int y1;

    private boolean y1Percent;

    private int y2;

    private boolean y2Percent;

    /// Where the canvas last laid the widget out. Editor-only and never saved; null until the canvas lays the
    /// widget out.
    private @Nullable Placement placement;

    private WidgetLayoutHolder(final WidgetLayout layout) {
        readFrom(layout);
    }

    public static WidgetLayoutHolder of(final WidgetLayout layout) {
        return new WidgetLayoutHolder(layout);
    }

    /// `width` x `height` pixels at the parent's top left, where a freshly added widget lands.
    public static WidgetLayoutHolder sized(final int width, final int height) {
        return of(new WidgetLayout(
            WidgetLayout.Unit.ZERO, new WidgetLayout.Unit(width, false), WidgetLayout.Unit.ZERO, new WidgetLayout.Unit(height, false),
            WidgetLayout.Horizontal.LEFT_WIDTH, WidgetLayout.Vertical.TOP_HEIGHT
        ));
    }

    public @Nullable Placement getPlacement() {
        return placement;
    }

    public void setPlacement(final @Nullable Placement placement) {
        this.placement = placement;
    }

    @Override
    public void readFrom(final WidgetLayout object) {
        horizontalAnchor = object.horizontalAnchor();
        verticalAnchor = object.verticalAnchor();
        x1 = object.x1().value();
        x1Percent = object.x1().fraction();
        x2 = object.x2().value();
        x2Percent = object.x2().fraction();
        y1 = object.y1().value();
        y1Percent = object.y1().fraction();
        y2 = object.y2().value();
        y2Percent = object.y2().fraction();
    }

    @Override
    public WidgetLayout makeObject() {
        return new WidgetLayout(
            new WidgetLayout.Unit(x1, x1Percent), new WidgetLayout.Unit(x2, x2Percent),
            new WidgetLayout.Unit(y1, y1Percent), new WidgetLayout.Unit(y2, y2Percent),
            horizontalAnchor, verticalAnchor
        );
    }

    /// A widget's rect and the rect of its parent it was anchored in, both in canvas pixels.
    public record Placement(ScreenRectangle area, ScreenRectangle rect) {
    }
}
