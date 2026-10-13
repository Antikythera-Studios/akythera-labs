package com.yesman.akytheralabs.gui.screen;

import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/// A screen with a guard that asking whether user to save the current editing object
public interface SafeCloseScreen {
    /// Expected to call [#openQuiteScreen], with unique parameters from each screen
    void handleQuitRequest();

    /// No runnable parameter for cancel button press. hardcoded to close modal.
    ///
    /// When `hasUnsavedChanges` is false the screen has nothing to save since its last save point, so
    /// the "Save ...?" prompt is skipped and `quit` runs straight away.
    static void openQuiteScreen(Font font, Component message, boolean hasUnsavedChanges, Runnable save, Runnable quit) {
        if (ModalManager.getInstance().getModal() != null) {
            ModalManager.getInstance().closeModal();
            return;
        }

        if (!hasUnsavedChanges) {
            quit.run();
            return;
        }

        int popupW = 200;
        int btnW = WidgetContainerModal.evenButtonWidth(popupW, 3);

        ModalManager.getInstance().openModalWidgetAtCenter(
            WidgetContainerModal
                .builder(font, Component.empty(), popupW)
                    .textLines(message)
                    .footerButton(
                        AkytheraButton
                            .buttonBuilder(
                                font,
                                LocalizedMessage.translatable(LangKeys.GUI_CANCEL),
                                () -> ModalManager.getInstance().closeModal()
                            )
                            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                            .build(),
                        btnW
                    )
                    .footerButton(
                        AkytheraButton
                            .buttonBuilder(
                                font,
                                LocalizedMessage.translatable(LangKeys.GUI_QUIT),
                                () -> quit.run()
                            )
                            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                            .build(),
                        btnW
                    )
                    .footerButton(
                        AkytheraButton
                            .buttonBuilder(
                                font,
                                LocalizedMessage.translatable(LangKeys.GUI_SAVE),
                                () -> save.run()
                            )
                            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                            .build(),
                        btnW
                    )
                    .build()
        );
    }
}
