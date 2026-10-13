package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.client.api.gui.TextArea;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/// [TextArea] whose clipboard and select-all keys follow [EditorKeyMappings] alone.
public final class EditorTextArea extends TextArea {
    public EditorTextArea(
        Font font,
        Unit x1,
        Unit x2,
        Unit y1,
        Unit y2,
        HorizontalAnchorType horizontalAnchor,
        VerticalAnchorType verticalAnchor,
        Callable<String> dataProvider,
        Consumer<String> onValueChanged,
        Component placeholder
    ) {
        super(font, x1, x2, y1, y2, horizontalAnchor, verticalAnchor, dataProvider, onValueChanged, placeholder);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (active && isFocused()) {
            if (EditorKeyMappings.COPY.matches(event)) {
                copySelection();
                return true;
            }

            if (EditorKeyMappings.CUT.matches(event)) {
                cutSelection();
                return true;
            }

            if (EditorKeyMappings.PASTE.matches(event)) {
                paste();
                return true;
            }

            if (EditorKeyMappings.SELECT_ALL.matches(event)) {
                selectAll();
                return true;
            }

            // The vanilla bindings of the keys above do nothing here.
            if (event.isCopy() || event.isCut() || event.isPaste() || event.isSelectAll()) return true;
        }

        return super.keyPressed(event);
    }
}
