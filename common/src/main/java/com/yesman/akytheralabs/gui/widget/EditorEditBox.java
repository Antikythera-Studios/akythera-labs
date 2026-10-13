package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.client.api.gui.AkytheraEditBox;
import com.yesman.akythera.client.api.gui.ContextMenuContainer;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/// [AkytheraEditBox] in the editor style: the clipboard and select-all keys follow [EditorKeyMappings] alone,
/// and a right press opens a cut / copy / paste menu.
public final class EditorEditBox<T> extends AkytheraEditBox<T> {
    /// Convenience factory for a plain string edit box (T = String, no bound data, no conversion).
    public static EditorEditBox<String> stringBox(
        Font font,
        Unit x1, Unit x2, Unit y1, Unit y2,
        HorizontalAnchorType horizontalAnchor,
        VerticalAnchorType verticalAnchor,
        Callable<String> dataProvider,
        Consumer<String> onWidgetChanged,
        Component message
    ) {
        return new EditorEditBox<>(
            font,
            x1, x2, y1, y2, horizontalAnchor, verticalAnchor,
            dataProvider, onWidgetChanged,
            _ -> true, s -> s, s -> s,
            message
        );
    }

    /// Creates a plain string edit box not bound to the anchor layout system.
    /// Position and size must be set explicitly (e.g. by a parent widget's layout pass).
    public static EditorEditBox<String> filteredStringBox(
        Font font,
        Predicate<String> filter,
        Component placeholder
    ) {
        return filteredStringBox(font, filter, placeholder, HorizontalAnchorType.LEFT_WIDTH, VerticalAnchorType.TOP_HEIGHT);
    }

    /// Variant that lets the caller pick anchor types (e.g. `LEFT_RIGHT` for boxes that should
    /// span from a fixed left offset to the row's right edge). Anchor units default to `pixel(0)`
    /// — the caller sets them via [#setX1] / [#setX2] / [#setY1] / [#setY2] before the first relocate.
    public static EditorEditBox<String> filteredStringBox(
        Font font,
        Predicate<String> filter,
        Component placeholder,
        HorizontalAnchorType horizontalAnchor,
        VerticalAnchorType verticalAnchor
    ) {
        return new EditorEditBox<>(
            font,
            AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0),
            horizontalAnchor,
            verticalAnchor,
            () -> "",
            _ -> {},
            filter,
            s -> s,
            s -> s,
            placeholder
        );
    }

    public EditorEditBox(
        final Font font,
        final Unit x1,
        final Unit x2,
        final Unit y1,
        final Unit y2,
        final AnchoredWidget.HorizontalAnchorType horizontalAnchor,
        final AnchoredWidget.VerticalAnchorType verticalAnchor,
        final Callable<T> dataProvider,
        final Consumer<T> onWidgetChanged,
        final Predicate<String> filter,
        final Function<String, T> deserializer,
        final Function<T, String> serializer,
        final Component message
    ) {
        super(font, x1, x2, y1, y2, horizontalAnchor, verticalAnchor, dataProvider, onWidgetChanged, filter, deserializer, serializer, message);

        setTheme(AkytheraLabsEditBoxTheme.AKYTHERA_EDITOR_EDIT_BOX);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        // Right-press opens the clipboard menu with the caret and the highlight left alone, so the menu
        // acts on what is already selected. The menu is a modal, which needs the editor layout up.
        if (event.button() == 1
            && isActive()
            && isMouseOver(event.x(), event.y())
            && AkytheraEditorLayoutManager.getInstance().isActive()
        ) {
            openClipboardMenu((int) event.x(), (int) event.y());
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (isEditable() && isFocused()) {
            if (EditorKeyMappings.COPY.matches(event)) {
                copyRange(selectionStart(), selectionEnd());
                return true;
            }

            if (EditorKeyMappings.CUT.matches(event)) {
                cutRange(selectionStart(), selectionEnd());
                return true;
            }

            if (EditorKeyMappings.PASTE.matches(event)) {
                replaceRange(selectionStart(), selectionEnd(), clipboardInsertion());
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

    /// Opens the cut / copy / paste menu at the pointer.
    ///
    /// The highlighted range and the clipboard text are read here and captured by the entries, because
    /// [ContextMenuContainer.ContextMenu.Entry] closes the modal before it runs the action and the box can
    /// lose its focus — and with it its highlight — on the way out.
    private void openClipboardMenu(int x, int y) {
        int start = selectionStart();
        int end = selectionEnd();
        String insertion = clipboardInsertion();

        ContextMenuContainer menu = ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(90)
            .entry()
                .message(Component.translatable(LangKeys.GUI_EDIT_BOX_CUT))
                .predicate(() -> isEditable() && end > start)
                .onPress(_ -> cutRange(start, end))
                .done()
            .entry()
                .message(Component.translatable(LangKeys.GUI_EDIT_BOX_COPY))
                .predicate(() -> end > start)
                .onPress(_ -> copyRange(start, end))
                .done()
            .entry()
                .message(Component.translatable(LangKeys.GUI_EDIT_BOX_PASTE))
                .predicate(() -> isEditable() && !insertion.isEmpty())
                .onPress(_ -> replaceRange(start, end, insertion))
                .done()
            .build();

        ModalManager.getInstance().openModalWidget(menu, x, y);
    }
}
