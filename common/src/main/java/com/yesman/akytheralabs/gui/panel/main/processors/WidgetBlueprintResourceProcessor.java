package com.yesman.akytheralabs.gui.panel.main.processors;

import com.yesman.akythera.core.blueprint.widget.WidgetBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FreemarkerExporter;
import com.yesman.akytheralabs.gui.panel.main.ContentBrowserPanel;
import com.yesman.akytheralabs.gui.panel.main.ExplorerPanel;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import com.yesman.akytheralabs.gui.widget.main.ContentWidget;
import com.yesman.akytheralabs.gui.widget.main.ExportModalGenerator;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/// Content browser behaviour for widget blueprint entries.
public final class WidgetBlueprintResourceProcessor implements ResourceProcessor<WidgetBlueprint> {
    public static final WidgetBlueprintResourceProcessor INSTANCE = new WidgetBlueprintResourceProcessor();

    private WidgetBlueprintResourceProcessor() {}

    @Override
    public boolean canCreateSubDirectories() {
        return true;
    }

    /// Green `+`: a new screen blueprint in the focused directory.
    @Override
    public void onPressAddButton(final ContentBrowserPanel browser, final ExplorerPanel.TreeNode selected) {
        ExplorerPanel.TreeNode regRoot = ResourceProcessor.regRootOf(selected);

        if (regRoot == null) return;

        String subPath = ResourceProcessor.buildSubPath(regRoot, selected);
        AkytheraProject project = AkytheraProject.getCurrentProject();
        String entryName = project.findNextFreeName(AkytheraRegistries.DataPack.WIDGET_KEY, "widget", subPath);
        String idPath = subPath.isEmpty() ? entryName : subPath + "/" + entryName;
        ResourceKey<WidgetBlueprint> key = project.createEntry(AkytheraRegistries.DataPack.WIDGET_KEY, idPath, true);

        project.saveEntry(key);

        Path focusDir = project.getProjectRoot()
            .resolve("data")
            .resolve(AkytheraRegistries.DataPack.WIDGET_KEY.identifier().getPath())
            .resolve(subPath);

        AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainScreen -> {
            mainScreen.explorerPanel.rebuildTree();
            mainScreen.explorerPanel.focusNodeByPath(focusDir);
        });
    }

    @Override
    public @Nullable ContentWidget<?> createContentWidget(final Font font, final Path path) {
        return ResourceProcessor.resolveDatapackWidget(font, path, AkytheraRegistries.DataPack.WIDGET_KEY, ContentWidget.WidgetBlueprintContent::new);
    }

    /// The per-entry `…ScreenScript.java` (or `WidgetScript`, `HUDScript`).
    @Override
    public ExportModalGenerator.Compile entryCompile(List<ResourceKey<?>> entryKeys) {
        return new ExportModalGenerator.Compile(
            entryKeys::contains,
            (project, sourceRoot, packageName) -> {
                for (ResourceKey<?> entryKey : entryKeys) {
                    FreemarkerExporter.exportWidgetScript(
                        project,
                        ResourceKey.create(AkytheraRegistries.DataPack.WIDGET_KEY, entryKey.identifier()),
                        sourceRoot,
                        packageName
                    );
                }
            }
        );
    }
}
