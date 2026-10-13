package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.EditorEditBox;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Predicate;

/// A modal that asks for the name of something new: an id path of lowercase letters, digits and underscores.
public final class NameInputModalGenerator {
    private static final int POPUP_W = 260;
    private static final int CONTENT_PADDING = 8;
    private static final int BOX_ROW_H = 28;
    private static final int BOX_H = 20;

    private NameInputModalGenerator() {}

    /// @param accepted  whether a typed name may be used. A refused name marks the box and keeps the modal open
    /// @param onConfirm told the accepted name, after the modal closed
    public static void open(final Font font, final Component title, final Predicate<String> accepted, final Consumer<String> onConfirm) {
        EditorEditBox<String> nameBox = EditorEditBox.filteredStringBox(
            font,
            text -> text.matches("[a-z0-9_]*"),
            Component.empty(),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT
        );

        nameBox.setX1(AnchoredWidget.pixel(CONTENT_PADDING));
        nameBox.setX2(AnchoredWidget.pixel(CONTENT_PADDING));
        nameBox.setY1(AnchoredWidget.pixel((BOX_ROW_H - BOX_H) / 2));
        nameBox.setY2(AnchoredWidget.pixel(BOX_H));

        int buttonWidth = WidgetContainerModal.evenButtonWidth(POPUP_W, 2);

        AkytheraButton cancelButton = AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_CANCEL), () -> ModalManager.getInstance().closeModal())
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();

        AkytheraButton okButton = AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_OK), () -> {
                String name = nameBox.getText();

                if (name.isEmpty() || !accepted.test(name)) {
                    nameBox.mark();
                    return;
                }

                ModalManager.getInstance().closeModal();
                onConfirm.accept(name);
            })
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();

        ModalManager.getInstance().openModalWidgetAtCenter(
            WidgetContainerModal.builder(font, title, POPUP_W)
                .singleWidgetRow(BOX_ROW_H, nameBox)
                .space(8)
                .footerButton(cancelButton, buttonWidth)
                .footerButton(okButton, buttonWidth)
                .build()
        );
    }
}
