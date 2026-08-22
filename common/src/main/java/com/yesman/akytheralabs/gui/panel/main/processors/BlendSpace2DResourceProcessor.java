package com.yesman.akytheralabs.gui.panel.main.processors;

import com.yesman.akythera.core.animation.playable.BlendSpace2D;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FreemarkerExporter;
import com.yesman.akytheralabs.akythera.animation.BlendSpace2DHolder;
import com.yesman.akytheralabs.gui.panel.main.ContentBrowserPanel;
import com.yesman.akytheralabs.gui.panel.main.ExplorerPanel;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import com.yesman.akytheralabs.gui.widget.main.ContentWidget;
import com.yesman.akytheralabs.gui.widget.main.ExportModalGenerator;
import com.yesman.akytheralabs.gui.widget.main.ModelSelectModal;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

public final class BlendSpace2DResourceProcessor implements ResourceProcessor<BlendSpace2D> {
    public static final BlendSpace2DResourceProcessor INSTANCE = new BlendSpace2DResourceProcessor();

    private BlendSpace2DResourceProcessor() {}

    @Override
    public boolean canCreateSubDirectories() {
        return true;
    }

    @Override
    public void onPressAddButton(ContentBrowserPanel browser, ExplorerPanel.TreeNode selected) {
        ExplorerPanel.TreeNode regRoot = ResourceProcessor.regRootOf(selected);
        if (regRoot == null) return;

        // Pick a preview model
        ModelSelectModal modal = new ModelSelectModal(browser.getFont(), null, previewModel -> {
            String subPath = ResourceProcessor.buildSubPath(regRoot, selected);
            AkytheraProject project = AkytheraProject.getCurrentProject();

            String entryName = project.findNextFreeName(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY, "new_entry", subPath);
            String idPath = subPath.isEmpty() ? entryName : subPath + "/" + entryName;
            ResourceKey<@NonNull BlendSpace2D> key = project.createEntry(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY, idPath, true);

            BlendSpace2DHolder holder = project.getDataHolderOrThrow(key);
            holder.setPreviewModel(previewModel);
            project.saveEntry(key);

            int lastSlash = idPath.lastIndexOf('/');
            String parentSubdir = lastSlash >= 0 ? idPath.substring(0, lastSlash) : "";
            Path focusDir = project.getProjectRoot()
                .resolve("data")
                .resolve(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY.identifier().getPath())
                .resolve(parentSubdir);

            AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainScreen -> {
                mainScreen.explorerPanel.rebuildTree();
                mainScreen.explorerPanel.focusNodeByPath(focusDir);
            });

            AkytheraEditorLayoutManager.getInstance().closeModal();
        });

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(modal);
    }

    @Override
    public @Nullable ContentWidget<?> createContentWidget(Font font, Path path) {
        return ResourceProcessor.resolveDatapackWidget(
            font, path, AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY,
            ContentWidget.BlendSpaceContent::new
        );
    }

    @Override
    public ExportModalGenerator.Compile categoryCompile() {
        return new ExportModalGenerator.Compile(
            key -> key.registryKey().equals(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY),
            (project, sourceRoot, packageName) -> FreemarkerExporter.exportRegistryProvider(
                project, AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY, sourceRoot, packageName)
        );
    }
}
