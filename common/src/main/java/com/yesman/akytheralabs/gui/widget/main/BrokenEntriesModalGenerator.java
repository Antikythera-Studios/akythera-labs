package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.client.gui.widget.AnchoredButton;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.main.WorldviewPanel;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;

import java.util.List;
import java.util.stream.Collectors;

/// The pre-export gate every "apply this project outside the editor" action shares: `File > Export` (both
/// flavors, see [ExportModalGenerator]) and the Worldview reload button ([WorldviewPanel]).
///
/// A broken entry ([DataHolder#getFailureException] — a file that failed to load, one carrying salvaged
/// nested content such as a dangling animation id in an Anim Graph, or one whose runtime object can't be
/// built) is **excluded** from the export rather than emitted as defaults. That is a silent omission, so it
/// is confirmed first: the modal names every affected file and only the OK button proceeds.
public final class BrokenEntriesModalGenerator {
    private static final int POPUP_W = 420;

    private BrokenEntriesModalGenerator() {
    }

    /// Runs `onProceed` straight away for a healthy project; otherwise opens the warning listing
    /// the broken entries' files ([AkytheraProject#findBrokenEntries]) and runs it only if the user
    /// confirms. Cancel just closes.
    public static void confirmProceed(Font font, AkytheraProject project, Runnable onProceed) {
        confirmProceed(font, project, project.findBrokenEntries(), onProceed);
    }

    /// [#confirmProceed(Font, AkytheraProject, Runnable)] over an already-narrowed entry set — a
    /// per-category or per-selection compile lists only the broken files it would have emitted.
    public static void confirmProceed(Font font, AkytheraProject project, List<ResourceKey<?>> broken, Runnable onProceed) {
        if (broken.isEmpty()) {
            onProceed.run();
            return;
        }

        // One text block of "- <path>" lines: the modal body scrolls when the list outgrows the
        // screen, so every broken file stays reachable however many there are.
        String files = broken.stream()
            .map(project::getRelativeEntryPath)
            .sorted()
            .map(path -> "- " + path)
            .collect(Collectors.joining("\n"));

        int btnW = WidgetContainerModal.evenButtonWidth(POPUP_W, 2);

        WidgetContainerModal popup = WidgetContainerModal.builder(
                font, Component.translatable(LangKeys.GUI_BROKEN_ENTRIES_MODAL_TITLE), POPUP_W)
            .textLines(Component.translatable(LangKeys.GUI_BROKEN_ENTRIES_MODAL_BODY))
            .textLines(Component.literal(files))
            .textLines(Component.translatable(LangKeys.GUI_BROKEN_ENTRIES_MODAL_PROCEED))
            // Footer buttons render right-to-left in insertion order, so add Cancel first
            // (rightmost) and OK last (leftmost) to read "OK ... Cancel".
            .footerButton(
                AnchoredButton.buttonBuilder(font, Component.translatable(LangKeys.GUI_CANCEL),
                        _ -> AkytheraEditorLayoutManager.getInstance().closeModal())
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .build(),
                btnW
            )
            .footerButton(
                AnchoredButton.buttonBuilder(font, Component.translatable(LangKeys.GUI_OK), _ -> {
                        AkytheraEditorLayoutManager.getInstance().closeModal();
                        onProceed.run();
                    })
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .build(),
                btnW
            )
            .build();

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(popup);
    }
}
