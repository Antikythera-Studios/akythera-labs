package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.client.api.gui.AkytheraEditBox;
import com.yesman.akythera.client.api.gui.ComboBox;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

/// [ComboBox] whose dropdown search box is an [EditorEditBox].
public final class EditorComboBox<T> extends ComboBox<T> {
    public EditorComboBox(
        Font font,
        Unit x1,
        Unit x2,
        Unit y1,
        Unit y2,
        HorizontalAnchorType horizontalAnchor,
        VerticalAnchorType verticalAnchor,
        Callable<T> dataProvider,
        Consumer<T> onWidgetChanged,
        int maxRows,
        Component title,
        Collection<T> items,
        Function<T, String> displayStringMapper
    ) {
        super(font, x1, x2, y1, y2, horizontalAnchor, verticalAnchor, dataProvider, onWidgetChanged, maxRows, title, items, displayStringMapper);
    }

    @Override
    protected AkytheraEditBox<String> createSearchBox(Consumer<String> onQueryChanged) {
        return EditorEditBox.stringBox(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0), AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            HorizontalAnchorType.LEFT_WIDTH,
            VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            onQueryChanged,
            Component.translatable(LangKeys.GUI_MESSAGE_EMPTY_SEARCHBAR)
        );
    }
}
