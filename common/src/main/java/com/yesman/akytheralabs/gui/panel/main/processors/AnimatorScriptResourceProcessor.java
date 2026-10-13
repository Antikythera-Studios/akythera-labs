package com.yesman.akytheralabs.gui.panel.main.processors;

import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.core.blueprint.script.DataDrivenAnimatorScript;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FreemarkerExporter;
import com.yesman.akytheralabs.akythera.animation.AnimatorScriptHolder;
import com.yesman.akytheralabs.gui.panel.main.ContentBrowserPanel;
import com.yesman.akytheralabs.gui.panel.main.ExplorerPanel;
import com.yesman.akytheralabs.gui.screen.AnimatorScriptEditorScreen;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import com.yesman.akytheralabs.gui.widget.main.ContentWidget;
import com.yesman.akytheralabs.gui.widget.main.EntityTypeSelectModal;
import com.yesman.akytheralabs.gui.widget.main.ExportModalGenerator;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

public final class AnimatorScriptResourceProcessor implements ResourceProcessor<DataDrivenAnimatorScript.AnimatorScriptBlueprint> {
    public static final AnimatorScriptResourceProcessor INSTANCE = new AnimatorScriptResourceProcessor();

    private AnimatorScriptResourceProcessor() {}

    @Override
    public boolean canCreateSubDirectories() {
        return true;
    }

    /// Green `+`: pick the preview model first, then create. The script's id path is
    /// free-form — the entity patch selects which script an entity uses, so the data
    /// pack path is no longer keyed by entity type. The entry is created in the
    /// directory the user has focused, named after the chosen model's leaf name.
    @Override
    public void onPressAddButton(ContentBrowserPanel browser, ExplorerPanel.TreeNode selected) {
        ExplorerPanel.TreeNode regRoot = ResourceProcessor.regRootOf(selected);
        if (regRoot == null) return;

        EntityTypeSelectModal modal = new EntityTypeSelectModal(browser.getFont(), null, previewEntityType -> {
            String subPath = ResourceProcessor.buildSubPath(regRoot, selected);
            AkytheraProject project = AkytheraProject.getCurrentProject();

            // Only the leaf — user already chose the target dir via focus.
            String entityPath = previewEntityType.getPath();
            int entityLastSlash = entityPath.lastIndexOf('/');
            String baseName = entityLastSlash >= 0 ? entityPath.substring(entityLastSlash + 1) : entityPath;

            String entryName = project.findNextFreeName(AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY, baseName, subPath);
            String idPath = subPath.isEmpty() ? entryName : subPath + "/" + entryName;
            ResourceKey<DataDrivenAnimatorScript.AnimatorScriptBlueprint> key = project.createEntry(
                AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY, idPath, true
            );

            AnimatorScriptHolder holder = project.getDataHolderOrThrow(key);
            holder.setPreviewEntityType(previewEntityType);
            project.saveEntry(key);

            Path focusDir = project.getProjectRoot()
                .resolve("data")
                .resolve(AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY.identifier().getPath())
                .resolve(subPath);

            AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainScreen -> {
                mainScreen.explorerPanel.rebuildTree();
                mainScreen.explorerPanel.focusNodeByPath(focusDir);
            });

            AkytheraEditorLayoutManager.getInstance().openNewScreen(
                new AnimatorScriptEditorScreen(key, holder));
        });

        ModalManager.getInstance().openModalWidgetAtCenter(modal);
    }

    @Override
    public @Nullable ContentWidget<?> createContentWidget(Font font, Path path) {
        return ResourceProcessor.resolveDatapackWidget(
            font, path, AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY,
            ContentWidget.AnimatorScriptContent::new);
    }

    @Override
    public ExportModalGenerator.Compile entryCompile(List<ResourceKey<?>> entryKeys) {
        return new ExportModalGenerator.Compile(
            entryKeys::contains,
            (project, sourceRoot, packageName) -> {
                for (ResourceKey<?> entryKey : entryKeys) {
                    FreemarkerExporter.exportAnimatorScript(
                        project,
                        ResourceKey.create(AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY, entryKey.identifier()),
                        sourceRoot,
                        packageName
                    );
                }
            }
        );
    }
}
