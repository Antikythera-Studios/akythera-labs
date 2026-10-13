package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import com.yesman.akytheralabs.gui.widget.EditorEditBox;
import com.yesman.akytheralabs.gui.widget.ImageUploader;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

public final class ProjectSetupModalGenerator {
    private static final int POPUP_W = 360;
    private static final int LABEL_W = 110;

    @Nullable
    private static IconRow activeIconRow = null;

    public static void open(Font font) {
        AkytheraProject project = AkytheraProject.getCurrentProject();
        ProjectNameRow nameRow = new ProjectNameRow(font, project);
        VersionContainerRow versionRow = new VersionContainerRow(font, project);
        IconRow iconRow = new IconRow(font);

        Path iconFile = project.getProjectRoot().resolve(AkytheraProject.PROJECT_ICON_FILE);

        if (iconFile.toFile().isFile()) {
            iconRow.imageUploader.loadImageFromPath(iconFile);
        }

        activeIconRow = iconRow;

        // Holder so the confirm-button lambda can reference the popup created below.
        WidgetContainerModal[] popupRef = new WidgetContainerModal[1];

        AkytheraButton saveBtn = AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_SAVE), () -> {
            nameRow.errorMessage = null;
            versionRow.errorMessage = null;

            if (!nameRow.validate(popupRef[0])) {
                return;
            }

            if (!versionRow.validate(popupRef[0])) {
                return;
            }

            if (!applyChanges(nameRow, versionRow, iconRow, project)) {
                return;
            }

            activeIconRow = null;
            ModalManager.getInstance().closeModal();
        }).build();

        AkytheraButton cancelBtn = AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_CANCEL), () -> {
            activeIconRow = null;
            ModalManager.getInstance().closeModal();
        }).build();

        int btnW = WidgetContainerModal.evenButtonWidth(POPUP_W, 2);

        WidgetContainerModal popup = WidgetContainerModal.builder(font, Component.translatable(LangKeys.GUI_MAIN_SCREEN_WIDGET_PROJECT_SETTINGS, project.getProjectName()), POPUP_W)
            .saveHistory()
            .widgetContainerRow(ProjectNameRow.ROW_H, nameRow)
            .widgetContainerRow(VersionContainerRow.ROW_H, versionRow)
            .widgetContainerRow(IconRow.ROW_H, iconRow)
            .footerButton(cancelBtn, btnW)
            .footerButton(saveBtn, btnW)
            .build();

        popupRef[0] = popup;

        ModalManager.getInstance().openModalWidgetAtCenter(popup);
    }

    /// Routes file-drop events from [MainEditorScreen#onFilesDrop] to the active icon uploader.
    public static boolean onFilesDrop(List<Path> paths) {
        if (activeIconRow == null) return false;

        paths.stream()
            .filter(p -> p.toString().endsWith(".png"))
            .findFirst()
            .ifPresent(p -> activeIconRow.getImageUploader().loadImageFromPath(p));

        return true;
    }

    /// Returns false when the rename couldn't be applied (name collision /
    /// IO failure); the caller leaves the modal open and reads
    /// `nameRow.errorMessage` for display.
    private static boolean applyChanges(
        ProjectNameRow nameRow,
        VersionContainerRow versionRow,
        IconRow iconRow,
        AkytheraProject project
    ) {
        String newName = nameRow.nameBox.getText();

        if (!newName.equals(project.getProjectName())) {
            Path target = AkytheraProject.getProjectsRootDirectory().resolve(newName);

            if (Files.exists(target)) {
                nameRow.nameBox.mark();
                nameRow.errorMessage = Component.translatable(LangKeys.GUI_MAIN_SCREEN_WIDGET_PROJECT_SETTINGS_ERROR_NAME_TAKEN);
                return false;
            }

            if (!project.renameProject(newName)) {
                nameRow.nameBox.mark();
                nameRow.errorMessage = Component.translatable(LangKeys.GUI_MAIN_SCREEN_WIDGET_PROJECT_SETTINGS_ERROR_RENAME_FAILED);

                AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(
                    mainEditorScreen -> mainEditorScreen.topMenuBar.setMessage(Component.literal(newName))
                );

                return false;
            }
        }

        AkytheraProject.SemanticVersioning v = versionRow.buildVersion();

        if (v != null) {
            project.setVersion(v);
        }

        Path iconPath = iconRow.getImageUploader().getSelectedPath();

        if (iconPath != null) {
            FileSystemHelper.copyReplace(iconPath, project.getProjectRoot().resolve(AkytheraProject.PROJECT_ICON_FILE));
        }

        return true;
    }

    private static final class ProjectNameRow extends WidgetContainerModal.WidgetsContainerRow {
        private static final int ROW_H = 38;
        private static final int BOX_H = 20;

        private final Font font;
        private final EditorEditBox<String> nameBox;
        @Nullable
        private Component errorMessage = null;

        ProjectNameRow(Font font, AkytheraProject project) {
            this.font = font;
            this.nameBox = EditorEditBox.filteredStringBox(
                font,
                s -> s.matches("[a-z0-9_]*"),
                Component.literal("project_name"),
                AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
                AnchoredWidget.VerticalAnchorType.TOP_HEIGHT
            );
            this.nameBox.addCharReplacement(' ', '_');
            this.nameBox.setValueByString(project.getProjectName());

            // Span from x=LABEL_W (left of row) to right edge of row, top-pinned at BOX_H tall.
            nameBox.setX1(AnchoredWidget.pixel(LABEL_W));
            nameBox.setX2(AnchoredWidget.pixel(0));
            nameBox.setY1(AnchoredWidget.pixel(0));
            nameBox.setY2(AnchoredWidget.pixel(BOX_H));

            addWidget(nameBox);
        }

        public boolean validate(WidgetContainerModal parent) {
            if (nameBox.getText().isEmpty()) {
                nameBox.mark();
                errorMessage = Component.translatable(LangKeys.GUI_MAIN_SCREEN_WIDGET_PROJECT_SETTINGS_ERROR_EMPTY_NAME);
                parent.setFocused(this);
                setFocused(nameBox);
                return false;
            }

            return true;
        }

        @Override
        protected void extractRowContent(WidgetContainerModal popup, GuiGraphicsExtractor g, ScreenRectangle rowRectangle, int mx, int my, float a) {
            g.text(font, "Project Name", rowRectangle.left(), rowRectangle.top() + (nameBox.getHeight() - font.lineHeight) / 2, 0xFFAAAAAA, false);
            if (!nameBox.isWarned()) errorMessage = null;

            if (errorMessage != null) {
                int textW = font.width(errorMessage);

                g.text(
                    font, errorMessage,
                    rowRectangle.right() - textW,
                    nameBox.getBottom() + 2,
                    0xFFCC3333, false
                );
            }
        }
    }

    private static final class VersionContainerRow extends WidgetContainerModal.WidgetsContainerRow {
        private static final int BOX_W = 26;
        private static final int BOX_H = 20;
        private static final int PRE_W = 80;
        private static final int SEP_W = 8;
        private static final int QUESTION_W = 14;
        private static final int ROW_H = BOX_H + 18;

        @Nullable
        private Component errorMessage = null;

        private final Font font;
        private final EditorEditBox<String> majorBox;
        private final EditorEditBox<String> minorBox;
        private final EditorEditBox<String> patchBox;
        private final EditorEditBox<String> preBox;
        private final AkytheraButton questionButton;

        VersionContainerRow(Font font, AkytheraProject project) {
            this.font = font;

            Predicate<String> digitsOnly = s -> s.matches("\\d*");

            majorBox = EditorEditBox.filteredStringBox(font, digitsOnly, Component.empty());
            minorBox = EditorEditBox.filteredStringBox(font, digitsOnly, Component.empty());
            patchBox = EditorEditBox.filteredStringBox(font, digitsOnly, Component.empty());
            preBox   = EditorEditBox.filteredStringBox(font, s -> s.matches("[a-zA-Z0-9.]*"), Component.empty());

            AkytheraProject.SemanticVersioning v = project.getVersion();
            majorBox.setValueByString(String.valueOf(v.major()));
            minorBox.setValueByString(String.valueOf(v.minor()));
            patchBox.setValueByString(String.valueOf(v.patch()));
            preBox.setValueByString(v.preRelease());

            // All anchors below are RELATIVE TO THE ROW RECT, not the popup.
            // WidgetContainerItem#cascade(popupRect) computes the row rect from popupY1
            // (baked by Builder) + popup rect, then calls child.relocate(rowRect).
            int x = LABEL_W;
            pinAt(majorBox, x, BOX_W); x += BOX_W + SEP_W;
            pinAt(minorBox, x, BOX_W); x += BOX_W + SEP_W;
            pinAt(patchBox, x, BOX_W); x += BOX_W + SEP_W;
            pinAt(preBox,   x, PRE_W); x += PRE_W + 4;

            questionButton = AkytheraButton.buttonBuilder(font, LocalizedMessage.handwritten("?"), () -> {})
                .xUnits(AnchoredWidget.pixel(x), AnchoredWidget.pixel(QUESTION_W))
                .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(QUESTION_W))
                .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH)
                .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
                .build();

            addWidget(majorBox);
            addWidget(minorBox);
            addWidget(patchBox);
            addWidget(preBox);
            addWidget(questionButton);
        }

        private static void pinAt(EditorEditBox<?> box, int x1, int width) {
            box.setX1(AnchoredWidget.pixel(x1));
            box.setX2(AnchoredWidget.pixel(width));
            box.setY1(AnchoredWidget.pixel(0));
            box.setY2(AnchoredWidget.pixel(BOX_H));
        }

        public AkytheraProject.@Nullable SemanticVersioning buildVersion() {
            try {
                int major = Integer.parseInt(majorBox.getText());
                int minor = Integer.parseInt(minorBox.getText());
                int patch = Integer.parseInt(patchBox.getText());
                return new AkytheraProject.SemanticVersioning(major, minor, patch, preBox.getText());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        public boolean validate(WidgetContainerModal parent) {
            if (majorBox.getText().isEmpty()) {
                majorBox.mark();
                errorMessage = Component.translatable(LangKeys.GUI_MAIN_SCREEN_WIDGET_PROJECT_SETTINGS_ERROR_EMPTY_VERSION);
                parent.setFocused(majorBox);
                return false;
            }

            if (minorBox.getText().isEmpty()) {
                minorBox.mark();
                errorMessage = Component.translatable(LangKeys.GUI_MAIN_SCREEN_WIDGET_PROJECT_SETTINGS_ERROR_EMPTY_VERSION);
                parent.setFocused(minorBox);
                return false;
            }

            if (patchBox.getText().isEmpty()) {
                patchBox.mark();
                errorMessage = Component.translatable(LangKeys.GUI_MAIN_SCREEN_WIDGET_PROJECT_SETTINGS_ERROR_EMPTY_VERSION);
                parent.setFocused(patchBox);
                return false;
            }

            return true;
        }

        @Override
        protected void extractRowContent(WidgetContainerModal popup, GuiGraphicsExtractor g, ScreenRectangle rowRectangle, int mouseX, int mouseY, float a) {
            ScreenRectangle rowRect = getContentRectangle();
            int textY = rowRect.top() + (BOX_H - font.lineHeight) / 2;

            g.text(font, "Version", rowRect.left(), textY, 0xFFAAAAAA, false);

            // Separators between the boxes — read absolute positions from the children
            // (cascade(rect) has already resolved them by the time we render).
            g.text(font, ".", majorBox.getX() + BOX_W + 1, textY, 0xFFCCCCCC, false);
            g.text(font, ".", minorBox.getX() + BOX_W + 1, textY, 0xFFCCCCCC, false);
            g.text(font, "+", patchBox.getX() + BOX_W + 1, textY, 0xFFCCCCCC, false);

            if (questionButton.isMouseOver(mouseX, mouseY)) {
                g.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal("Semantic Versioning 2.0.0"),
                    Component.literal("MAJOR: breaking changes"),
                    Component.literal("MINOR: backwards-compatible features"),
                    Component.literal("PATCH: backwards-compatible bug fixes"),
                    Component.literal("Pre-release: e.g. alpha.1, beta, rc.2")
                ), mouseX, mouseY);
            }

            if (errorMessage != null) {
                int textW = font.width(errorMessage);
                g.text(
                    font, errorMessage,
                    rowRect.right() - textW,
                    patchBox.getBottom() + 2,
                    0xFFCC3333, false
                );
            }
        }
    }

    private static final class IconRow extends WidgetContainerModal.WidgetsContainerRow {
        static final int ICON_H = 72;
        static final int ROW_H = 82;

        private final Font font;
        private final ImageUploader imageUploader;

        IconRow(Font font) {
            this.font = font;
            this.imageUploader = new ImageUploader(
                AnchoredWidget.pixel(LABEL_W), AnchoredWidget.pixel(ICON_H),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(ICON_H),
                AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
                AnchoredWidget.VerticalAnchorType.CENTER_HEIGHT,
                _ -> {}
            );

            addWidget(imageUploader);
        }

        public ImageUploader getImageUploader() {
            return imageUploader;
        }

        @Override
        protected void extractRowContent(WidgetContainerModal popup, GuiGraphicsExtractor g, ScreenRectangle rowRectangle, int mx, int my, float a) {
            g.text(font, "Icon", rowRectangle.left(), rowRectangle.top() + (rowRectangle.height() - font.lineHeight) / 2, 0xFFAAAAAA, false);
        }
    }
}