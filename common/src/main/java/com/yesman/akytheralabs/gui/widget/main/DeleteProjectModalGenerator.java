package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.client.gui.widget.AnchoredButton;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.screen.LoadAkytheraProjectScreen;
import com.yesman.akytheralabs.gui.widget.AdvancedEditBox;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

/// "Delete Project" confirmation modal opened from the File menu. The user must
/// retype the exact project name before the project directory is wiped from disk
/// — a deliberately heavy gate since the action is irreversible.
public final class DeleteProjectModalGenerator {
    private static final int POPUP_W = 320;
    private static final int CONTENT_PADDING = 8;
    private static final int BOX_ROW_H = 28;
    private static final int BOX_H = 20;

    private DeleteProjectModalGenerator() {}

    public static void open(Font font) {
        AkytheraProject project = AkytheraProject.getCurrentProject();
        String projectName = project.getProjectName();

        AdvancedEditBox<String> nameBox = AdvancedEditBox.filteredStringBox(
            font,
            _ -> true,
            Component.literal(projectName),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT
        );

        // Span the row from CONTENT_PADDING on the left to CONTENT_PADDING from the right edge.
        nameBox.setX1(AnchoredWidget.pixel(CONTENT_PADDING));
        nameBox.setX2(AnchoredWidget.pixel(CONTENT_PADDING));
        nameBox.setY1(AnchoredWidget.pixel((BOX_ROW_H - BOX_H) / 2));
        nameBox.setY2(AnchoredWidget.pixel(BOX_H));

        int btnW = WidgetContainerModal.evenButtonWidth(POPUP_W, 2);

        AnchoredButton cancelBtn = AnchoredButton.buttonBuilder(font, Component.translatable(LangKeys.GUI_CANCEL),
                _ -> AkytheraEditorLayoutManager.getInstance().closeModal())
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();

        AnchoredButton okBtn = AnchoredButton.buttonBuilder(font, Component.translatable(LangKeys.GUI_OK), _ -> {
            // Only proceed when the typed name matches exactly; otherwise red-mark
            // the box and keep the modal open.
            if (!nameBox.getText().equals(projectName)) {
                nameBox.mark();
                return;
            }

            AkytheraEditorLayoutManager.getInstance().closeModal();
            deleteProject(project);
            Minecraft.getInstance().gui.setScreen(new LoadAkytheraProjectScreen());
        })
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();

        WidgetContainerModal popup = WidgetContainerModal.builder(
                font, Component.translatable(LangKeys.GUI_MAIN_SCREEN_DELETE_PROJECT_MODAL_TITLE), POPUP_W
            )
            .textLines(Component.translatable(LangKeys.GUI_MAIN_SCREEN_DELETE_PROJECT_MODAL_BODY))
            .singleWidgetRow(BOX_ROW_H, nameBox)
            .space(20)
            .footerButton(cancelBtn, btnW)
            .footerButton(okBtn, btnW)
            .build();

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(popup);
    }

    /// Closes the editor for this project, then erases its directory from disk.
    /// The root must be captured *before* [AkytheraProject#endProject] nulls out
    /// the current project.
    private static void deleteProject(AkytheraProject project) {
        Path projectRoot = project.getProjectRoot();
        AkytheraProject.endProject();
        FileSystemHelper.deleteRecursive(projectRoot);
    }
}