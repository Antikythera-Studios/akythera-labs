package com.yesman.akytheralabs.editor.historytrack;

import com.yesman.akythera.client.gui.widget.AnchoredButton;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/// The prompt shown before a history entry takes something off disk — undoing a create, or redoing
/// a delete.
final class ConfirmDeletePopup {
    private ConfirmDeletePopup() {}

    static void open(Runnable proceed) {
        Font font = Minecraft.getInstance().font;
        int popupW = 300;
        int btnW = WidgetContainerModal.evenButtonWidth(popupW, 2);

        WidgetContainerModal popup = WidgetContainerModal.builder(
            font,
            Component.translatable(LangKeys.GUI_HISTORY_POPUP_CONFIRM_DELETE_TITLE),
            popupW
            )
            .textLines(Component.translatable(LangKeys.GUI_HISTORY_POPUP_CONFIRM_DELETE_BODY))
            .footerButton(
                AnchoredButton.buttonBuilder(font, Component.translatable(LangKeys.GUI_CANCEL),
                        _ -> AkytheraEditorLayoutManager.getInstance().closeModal())
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .build(),
                btnW
            )
            .footerButton(
                AnchoredButton.buttonBuilder(font, Component.translatable(LangKeys.GUI_MESSAGE_BUTTON_REMOVE), _ -> {
                    AkytheraEditorLayoutManager.getInstance().closeModal();
                    proceed.run();
                })
                .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                .build(),
                btnW
            )
            .build();

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(popup);
    }
}