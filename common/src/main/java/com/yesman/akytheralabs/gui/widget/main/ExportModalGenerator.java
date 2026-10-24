package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.CheckBox;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import com.yesman.akytheralabs.FreemarkerExporter;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.main.processors.ResourceProcessor;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.EditorEditBox;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

/// Builds the two `File > Export` modals — "Export as Pack" (writes the loadable resource/data packs
/// into a chosen folder) and "Export as Source Code" (runs [FreemarkerExporter] against a chosen
/// source root + package) — and the per-category / per-selection `Compile` modal ([#openCompile]).
/// Mirrors the layout style of [ProjectSetupModalGenerator] / [EditorPreferencesModalGenerator]: a
/// [WidgetContainerModal] of labelled rows plus Export / Cancel footer buttons.
public final class ExportModalGenerator {
    private static final int POPUP_W = 450;
    private static final int LABEL_W = 100;
    private static final int RIGHT_MARGIN = 6;

    /// `src/main/java/` marker used to derive the java package from a chosen export directory.
    /// Forward-slash form — paths are normalized before matching so Windows `\` paths also hit.
    private static final String SOURCE_ROOT_MARKER = "src/main/java/";

    private ExportModalGenerator() {
    }

    /// `Export > As Pack`: a Data Path row + a Resource Path row + footer buttons. The Data Path
    /// receives the re-encoded data pack via [AkytheraProject#exportDataPack] — with "Include assets
    /// in data pack" ticked it carries the project's assets under `data/<projectName>/assets/` and is
    /// named `<projectName>_datapack_with_assets.zip`;
    /// the Resource Path receives the assets as a zip resource pack (`<projectName>_resources.zip`) via
    /// [AkytheraProject#exportResourcePack]. Both destinations are remembered in
    /// `project_settings.akproject` ([AkytheraProject#saveExportPaths]) so the modal reopens on the same
    /// folders. Each field is optional: a blank field skips that half of the export.
    ///
    /// Gated behind [BrokenEntriesModalGenerator]: broken entries are silently left out of the
    /// export, so the user confirms that first.
    public static void openAsPack(Font font) {
        AkytheraProject project = AkytheraProject.getCurrentProject();
        BrokenEntriesModalGenerator.confirmProceed(font, project, () -> openAsPackModal(font, project));
    }

    private static void openAsPackModal(Font font, AkytheraProject project) {
        String fallback = project.getProjectRoot().toString();

        PathRow dataPathRow = new PathRow(
            font,
            Component.translatable(LangKeys.GUI_EXPORT_MODAL_DATA_PATH),
            project.getLastExportDataPath().isBlank() ? fallback : project.getLastExportDataPath(),
            null
        );
        OptionRow includeAssetsRow = new OptionRow(
            font,
            Component.translatable(LangKeys.GUI_EXPORT_MODAL_INCLUDE_ASSETS)
        );
        PathRow resourcePathRow = new PathRow(
            font,
            Component.translatable(LangKeys.GUI_EXPORT_MODAL_RESOURCE_PATH),
            project.getLastExportResourcePath().isBlank() ? fallback : project.getLastExportResourcePath(),
            null
        );

        AkytheraButton exportBtn = AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_EXPORT_MODAL_EXPORT), () -> {
            String dataDir = dataPathRow.getPath();
            String resourceDir = resourcePathRow.getPath();
            if (dataDir.isBlank() && resourceDir.isBlank()) return;

            project.saveExportPaths(dataDir, resourceDir);

            Path opened = null;

            if (!dataDir.isBlank()) {
                Path dataPath = Path.of(dataDir);
                boolean includeAssets = includeAssetsRow.isChecked();
                String dataPackName = project.getProjectName() + (includeAssets ? "_datapack_with_assets.zip" : "_data.zip");
                project.exportDataPack(dataPath.resolve(dataPackName), includeAssets);
                opened = dataPath;
            }

            if (!resourceDir.isBlank()) {
                Path resourcePath = Path.of(resourceDir);
                project.exportResourcePack(resourcePath.resolve(project.getProjectName() + "_resources.zip"));
                opened = resourcePath;
            }

            ModalManager.getInstance().closeModal();
            if (opened != null) Util.getPlatform().openFile(opened.toFile());
        }).build();

        AkytheraButton cancelBtn = AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_CANCEL), () ->
            ModalManager.getInstance().closeModal()
        ).build();

        int btnW = WidgetContainerModal.evenButtonWidth(POPUP_W, 2);

        WidgetContainerModal popup = WidgetContainerModal.builder(font, Component.translatable(LangKeys.GUI_EXPORT_MODAL_AS_PACK_TITLE), POPUP_W)
            .widgetContainerRow(PathRow.ROW_H, dataPathRow)
            .widgetContainerRow(OptionRow.ROW_H, includeAssetsRow)
            .widgetContainerRow(PathRow.ROW_H, resourcePathRow)
            .footerButton(cancelBtn, btnW)
            .footerButton(exportBtn, btnW)
            .build();

        ModalManager.getInstance().openModalWidgetAtCenter(popup);
    }

    /// `Export > As Source Code`: a Data Path row (generated `.java` output), a Resource Path row
    /// (loose assets), plus an editable package row. Picking a Data Path re-derives the package name
    /// from it (see [#derivePackageName]); the user can still override it. The Data Path feeds
    /// [FreemarkerExporter#exportProject]; the Resource Path feeds [AkytheraProject#exportAssetsToDirectory]
    /// (raw asset directory, NOT a zip — source-code exports keep resources editable on disk).
    /// Both destinations are remembered in `project_settings.akproject`
    /// ([AkytheraProject#saveSourceExportPaths]), independently of the `As Pack` paths. Each field is
    /// optional: a blank field skips that half of the export.
    ///
    /// Gated behind [BrokenEntriesModalGenerator], like [#openAsPack].
    public static void openAsSourceCode(Font font) {
        AkytheraProject project = AkytheraProject.getCurrentProject();
        BrokenEntriesModalGenerator.confirmProceed(font, project, () -> openAsSourceCodeModal(font, project));
    }

    private static void openAsSourceCodeModal(Font font, AkytheraProject project) {
        // Prefill the package from the remembered source path so a reopened modal shows the same
        // derived package the last export used, not the bare "com.example.mod" placeholder.
        PackageRow packageRow = new PackageRow(font, derivePackageName(project.getLastSourceDataPath()));

        PathRow dataPathRow = new PathRow(
            font,
            Component.translatable(LangKeys.GUI_EXPORT_MODAL_SOURCE_CODE_PATH),
            project.getLastSourceDataPath(),
            chosen -> packageRow.setPackageName(derivePackageName(chosen))
        );
        PathRow resourcePathRow = new PathRow(
            font,
            Component.translatable(LangKeys.GUI_EXPORT_MODAL_RESOURCE_PATH),
            project.getLastSourceResourcePath(),
            null
        );

        AkytheraButton exportBtn = AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_EXPORT_MODAL_EXPORT), () -> {
            String dataDir = dataPathRow.getPath();
            String resourceDir = resourcePathRow.getPath();
            if (dataDir.isBlank() && resourceDir.isBlank()) return;

            project.saveSourceExportPaths(dataDir, resourceDir);

            Path opened = null;

            if (!dataDir.isBlank()) {
                Path dataPath = Path.of(dataDir);
                FreemarkerExporter.exportProject(project, dataPath, packageRow.getPackageName());
                opened = dataPath;
            }

            if (!resourceDir.isBlank()) {
                Path resourcePath = Path.of(resourceDir);
                project.exportAssetsToDirectory(resourcePath);
                // Show data pack directory first.
                if (opened == null) opened = resourcePath;
            }

            ModalManager.getInstance().closeModal();
            if (opened != null) Util.getPlatform().openFile(opened.toFile());
        }).build();

        AkytheraButton cancelBtn = AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_CANCEL), () ->
            ModalManager.getInstance().closeModal()
        ).build();

        int btnW = WidgetContainerModal.evenButtonWidth(POPUP_W, 2);

        WidgetContainerModal popup = WidgetContainerModal.builder(font, Component.translatable(LangKeys.GUI_EXPORT_MODAL_AS_SOURCE_CODE_TITLE), POPUP_W)
            .widgetContainerRow(PathRow.ROW_H, dataPathRow)
            .widgetContainerRow(PathRow.ROW_H, resourcePathRow)
            .widgetContainerRow(PackageRow.ROW_H, packageRow)
            .footerButton(cancelBtn, btnW)
            .footerButton(exportBtn, btnW)
            .build();

        ModalManager.getInstance().openModalWidgetAtCenter(popup);
    }

    /// `Compile` on one data category, or on a content-browser selection: the `As Source Code` layout
    /// without its Resource Path row, since a compile only writes generated `.java`. The folder picked
    /// is the same export root the whole-project export uses, so each kind still lands under the
    /// sub-directory it owns (`<root>\scripts\animator\MyAnimatorScript.java`), and it is remembered in
    /// `project_settings.akproject` alongside the `As Source Code` path.
    ///
    /// Gated behind [BrokenEntriesModalGenerator] over `compile`'s own entries only.
    public static void openCompile(Font font, Component title, Compile compile) {
        AkytheraProject project = AkytheraProject.getCurrentProject();
        project.compileAll();
        BrokenEntriesModalGenerator.confirmProceed(
            font,
            project,
            project.findBrokenEntries(compile.scope()),
            () -> openCompileModal(font, project, title, compile)
        );
    }

    private static void openCompileModal(Font font, AkytheraProject project, Component title, Compile compile) {
        PackageRow packageRow = new PackageRow(font, derivePackageName(project.getLastSourceDataPath()));

        PathRow sourcePathRow = new PathRow(
            font,
            Component.translatable(LangKeys.GUI_EXPORT_MODAL_SOURCE_CODE_PATH),
            project.getLastSourceDataPath(),
            chosen -> packageRow.setPackageName(derivePackageName(chosen))
        );

        AkytheraButton compileBtn = AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_EXPORT_MODAL_COMPILE), () -> {
            String sourceDir = sourcePathRow.getPath();
            if (sourceDir.isBlank()) return;

            // The Resource Path this modal doesn't offer keeps whatever the last full source export left.
            project.saveSourceExportPaths(sourceDir, project.getLastSourceResourcePath());

            Path sourceRoot = Path.of(sourceDir);
            compile.work().run(project, sourceRoot, packageRow.getPackageName());

            ModalManager.getInstance().closeModal();
            Util.getPlatform().openFile(sourceRoot.toFile());
        }).build();

        AkytheraButton cancelBtn = AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_CANCEL), () ->
            ModalManager.getInstance().closeModal()
        ).build();

        int btnW = WidgetContainerModal.evenButtonWidth(POPUP_W, 2);

        WidgetContainerModal popup = WidgetContainerModal.builder(font, title, POPUP_W)
            .widgetContainerRow(PathRow.ROW_H, sourcePathRow)
            .widgetContainerRow(PackageRow.ROW_H, packageRow)
            .footerButton(cancelBtn, btnW)
            .footerButton(compileBtn, btnW)
            .build();

        ModalManager.getInstance().openModalWidgetAtCenter(popup);
    }

    /// One compile the editor can run: which project entries it covers — its [BrokenEntriesModalGenerator]
    /// gate — and the writer emitting their generated `.java` into the chosen source root. Produced by
    /// [ResourceProcessor#categoryCompile] and [ResourceProcessor#entryCompile].
    public record Compile(Predicate<ResourceKey<?>> scope, Work work) {
        @FunctionalInterface
        public interface Work {
            void run(AkytheraProject project, Path sourceRoot, String packageName);
        }
    }

    /// Mirrors java's package rule: take the path segment after `src/main/java/` and dot-join it.
    /// e.g. `common\src\main\java\com\yesman\epicfight\data\` -> `com.yesman.epicfight.data`.
    /// Returns `""` when the marker isn't present (caller leaves the package field blank).
    static String derivePackageName(String path) {
        String normalized = path.replace('\\', '/');
        int idx = normalized.indexOf(SOURCE_ROOT_MARKER);
        if (idx < 0) return "";

        String relative = normalized.substring(idx + SOURCE_ROOT_MARKER.length());
        // Trim leading/trailing slashes, then dot-join the remaining segments.
        relative = relative.replaceAll("^/+", "").replaceAll("/+$", "");

        return relative.isEmpty() ? "" : relative.replace('/', '.');
    }

    /// A read-only path display ([EditorEditBox] in non-editable mode) plus a folder-picker
    /// button. Picking a folder updates the box and notifies `onPathChosen` (used by the
    /// source-code modal to refresh the derived package name).
    private static final class PathRow extends WidgetContainerModal.WidgetsContainerRow {
        static final int ROW_H = 38;
        static final int BOX_H = 20;
        static final int BROWSE_W = 20;

        private final Font font;
        private final Component label;
        private final EditorEditBox<String> pathBox;

        PathRow(Font font, Component label, String initialPath, @Nullable Consumer<String> onPathChosen) {
            this.font = font;
            this.label = label;

            this.pathBox = EditorEditBox.filteredStringBox(
                font,
                _ -> true,
                Component.empty(),
                AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
                AnchoredWidget.VerticalAnchorType.TOP_HEIGHT
            );
            pathBox.setValueByString(initialPath);
            pathBox.setEditable(false);
            // Span from x=LABEL_W to the row's right edge, leaving room for the browse button.
            pathBox.setX1(AnchoredWidget.pixel(LABEL_W));
            pathBox.setX2(AnchoredWidget.pixel(BROWSE_W + 4));
            pathBox.setY1(AnchoredWidget.pixel(0));
            pathBox.setY2(AnchoredWidget.pixel(BOX_H));

            AkytheraButton browseBtn = AkytheraButton.buttonBuilder(font, LocalizedMessage.EMPTY, () -> onPressBrowse(onPathChosen))
                .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(BROWSE_W))
                .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(BOX_H))
                .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
                .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
                .theme(AkytheraLabsButtonTheme.EXPORT_PATH_BROWSE_BUTTON)
                .build();

            addWidget(pathBox);
            addWidget(browseBtn);
        }

        String getPath() {
            return pathBox.getText();
        }

        /// Native folder picker runs off-thread (it blocks); the result is applied back on the
        /// render thread — the same pattern the retarget modal's export-path browse button uses.
        private void onPressBrowse(@Nullable Consumer<String> onPathChosen) {
            String current = FileSystemHelper.toOsString(pathBox.getText());

            CompletableFuture
                .supplyAsync(() -> TinyFileDialogs.tinyfd_selectFolderDialog("Choose Export Folder", current))
                .thenAcceptAsync(result -> {
                    if (result == null) return;
                    pathBox.setValueByString(result);
                    if (onPathChosen != null) onPathChosen.accept(result);
                }, Minecraft.getInstance());
        }

        @Override
        protected void extractRowContent(WidgetContainerModal popup, GuiGraphicsExtractor g, ScreenRectangle rowRectangle, int mx, int my, float a) {
            g.text(font, label, rowRectangle.left(), rowRectangle.top() + (BOX_H - font.lineHeight) / 2, 0xFFAAAAAA, false);
        }
    }

    /// A labelled boolean option row. The [CheckBox]'s data provider / consumer pair targets
    /// [#checked], which the modal reads when the Export button fires.
    private static final class OptionRow extends WidgetContainerModal.WidgetsContainerRow {
        static final int ROW_H = 26;
        static final int BOX_H = 14;

        private final Font font;
        private final Component label;
        private boolean checked;

        OptionRow(Font font, Component label) {
            this.font = font;
            this.label = label;

            CheckBox checkBox = new CheckBox(
                font,
                AnchoredWidget.pixel(RIGHT_MARGIN),
                AnchoredWidget.pixel(BOX_H),
                AnchoredWidget.pixel(0),
                AnchoredWidget.pixel(BOX_H),
                AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH,
                AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
                () -> checked,
                value -> checked = value,
                Component.empty(),
                false
            );

            addWidget(checkBox);
        }

        boolean isChecked() {
            return checked;
        }

        @Override
        protected void extractRowContent(WidgetContainerModal popup, GuiGraphicsExtractor g, ScreenRectangle rowRectangle, int mx, int my, float a) {
            g.text(font, label, rowRectangle.left(), rowRectangle.top() + (BOX_H - font.lineHeight) / 2, 0xFFAAAAAA, false);
        }
    }

    /// Editable package-name field, prefilled from the chosen export path but free to override.
    private static final class PackageRow extends WidgetContainerModal.WidgetsContainerRow {
        static final int ROW_H = 38;
        static final int BOX_H = 20;

        private final Font font;
        private final EditorEditBox<String> packageBox;

        PackageRow(Font font, String initialPackage) {
            this.font = font;
            this.packageBox = EditorEditBox.filteredStringBox(
                font,
                s -> s.matches("[a-zA-Z0-9_.]*"),
                Component.literal("com.example.mod"),
                AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
                AnchoredWidget.VerticalAnchorType.TOP_HEIGHT
            );
            packageBox.setValueByString(initialPackage);
            packageBox.setX1(AnchoredWidget.pixel(LABEL_W));
            packageBox.setX2(AnchoredWidget.pixel(0));
            packageBox.setY1(AnchoredWidget.pixel(0));
            packageBox.setY2(AnchoredWidget.pixel(BOX_H));

            addWidget(packageBox);
        }

        void setPackageName(String packageName) {
            packageBox.setValueByString(packageName);
        }

        String getPackageName() {
            return packageBox.getText();
        }

        @Override
        protected void extractRowContent(WidgetContainerModal popup, GuiGraphicsExtractor g, ScreenRectangle rowRectangle, int mx, int my, float a) {
            g.text(font, Component.translatable(LangKeys.GUI_EXPORT_MODAL_PACKAGE_NAME), rowRectangle.left(), rowRectangle.top() + (BOX_H - font.lineHeight) / 2, 0xFFAAAAAA, false);
        }
    }
}
