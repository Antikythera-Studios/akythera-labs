package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.client.api.gui.AkytheraEditBox;
import com.yesman.akythera.client.api.gui.DragValueBox;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/// [DragValueBox] whose typed value goes through an [EditorEditBox].
public final class EditorDragValueBox<T extends Number> extends DragValueBox<T> {
    public EditorDragValueBox(
        final Font font,
        final Unit x1,
        final Unit x2,
        final Unit y1,
        final Unit y2,
        final HorizontalAnchorType horizontalAnchor,
        final VerticalAnchorType verticalAnchor,
        final Callable<T> dataProvider,
        final Consumer<T> onWidgetChanged,
        final T minValue,
        final T maxValue,
        final Function<String, T> parser,
        final Function<T, String> serializer,
        final DoubleFunction<T> fromDouble,
        final Component message
    ) {
        super(font, x1, x2, y1, y2, horizontalAnchor, verticalAnchor, dataProvider, onWidgetChanged, minValue, maxValue, parser, serializer, fromDouble, message);
    }

    @Override
    protected AkytheraEditBox<T> createEditBox(
        Callable<T> dataProvider,
        Predicate<String> filter,
        Function<String, T> parser,
        Function<T, String> serializer,
        Component message
    ) {
        return new EditorEditBox<>(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0), AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            HorizontalAnchorType.LEFT_WIDTH,
            VerticalAnchorType.TOP_HEIGHT,
            dataProvider,
            _ -> {},
            filter,
            parser,
            serializer,
            message
        );
    }
}
