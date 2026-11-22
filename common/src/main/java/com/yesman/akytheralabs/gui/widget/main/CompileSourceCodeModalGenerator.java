package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.dev.hotreload.HotReloadResult;
import com.yesman.akytheralabs.dev.hotreload.HotReloader;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.AwaitIconModal;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/// `Tools > Compile Source Code`: closes the editor screens, then hot reloads the source code ([HotReloader]).
public final class CompileSourceCodeModalGenerator {
    private static final int POPUP_W = 420;

    /// Compile errors listed in the popup at most. The rest are counted.
    private static final int MAX_LISTED_ERRORS = 30;

    private CompileSourceCodeModalGenerator() {}

    /// Lists the missing setup, or asks each editor screen to save, then reloads behind an await modal and shows
    /// what changed.
    public static void open(final Font font) {
        List<String> missing = HotReloader.missingSetup();

        if (!missing.isEmpty()) {
            openMessage(font, Component.translatable(LangKeys.GUI_COMPILE_SOURCE_CODE_MISSING_SETUP), "- " + String.join("\n- ", missing));
            return;
        }

        AkytheraEditorLayoutManager.getInstance().closeEditorScreens(AkytheraEditorLayoutManager.UnsavedChanges.ASK).thenRun(() -> {
            AwaitIconModal.open(font, Component.translatable(LangKeys.GUI_COMPILE_SOURCE_CODE_COMPILING));

            HotReloader.reload().thenAcceptAsync(result -> {
                ModalManager.getInstance().closeAllModals();

                if (!result.errors().isEmpty()) {
                    List<String> listed = new ArrayList<>(result.errors().subList(0, Math.min(MAX_LISTED_ERRORS, result.errors().size())));
                    if (result.errors().size() > MAX_LISTED_ERRORS) listed.add("... " + (result.errors().size() - MAX_LISTED_ERRORS) + " more");

                    openMessage(font, Component.translatable(LangKeys.GUI_COMPILE_SOURCE_CODE_COMPILE_FAILED), String.join("\n", listed));
                } else if (result.failure() != null) {
                    openMessage(font, Component.translatable(LangKeys.GUI_COMPILE_SOURCE_CODE_SWAP_FAILED), result.failure());
                } else if (result.refused().isEmpty()) {
                    openMessage(font, reloaded(result), "");
                } else {
                    openMessage(font, reloaded(result).append("\n").append(Component.translatable(LangKeys.GUI_COMPILE_SOURCE_CODE_REFUSED)), "- " + String.join("\n- ", result.refused()));
                }
            }, Minecraft.getInstance());
        });
    }

    /// How many classes `result` redefined, added and removed.
    private static MutableComponent reloaded(final HotReloadResult result) {
        return Component.translatable(LangKeys.GUI_COMPILE_SOURCE_CODE_RELOADED, result.redefined().size(), result.added().size(), result.removed().size());
    }

    /// A popup with `message` over `details`, closed by OK.
    private static void openMessage(final Font font, final Component message, final String details) {
        WidgetContainerModal.Builder popup = WidgetContainerModal.builder(font, Component.translatable(LangKeys.GUI_COMPILE_SOURCE_CODE_TITLE), POPUP_W)
            .textLines(message);

        if (!details.isEmpty()) popup.textLines(Component.literal(details));

        ModalManager.getInstance().openModalWidgetAtCenter(popup
            .footerButton(
                AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_OK), () -> ModalManager.getInstance().closeModal())
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .build(),
                WidgetContainerModal.evenButtonWidth(POPUP_W, 3)
            )
            .build());
    }
}
