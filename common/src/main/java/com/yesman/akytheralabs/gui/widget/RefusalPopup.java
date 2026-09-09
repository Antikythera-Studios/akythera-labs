package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.client.gui.widget.AnchoredButton;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/// Shown when an editor action is refused, carrying the reason its refuser gave.
public final class RefusalPopup {
    private RefusalPopup() {}

    private static final int POPUP_WIDTH = 300;

    public static void open(Component title, Component reason) {
        Font font = Minecraft.getInstance().font;

        WidgetContainerModal popup = WidgetContainerModal.builder(font, title, POPUP_WIDTH)
            .textLines(reason)
            .footerButton(
                AnchoredButton.buttonBuilder(font, Component.translatable(LangKeys.GUI_OK),
                        _ -> AkytheraEditorLayoutManager.getInstance().closeModal())
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .build(),
                WidgetContainerModal.evenButtonWidth(POPUP_WIDTH, 1)
            )
            .build();

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(popup);
    }
}
