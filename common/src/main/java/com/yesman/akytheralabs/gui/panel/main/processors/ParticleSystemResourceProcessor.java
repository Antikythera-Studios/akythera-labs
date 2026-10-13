package com.yesman.akytheralabs.gui.panel.main.processors;

import com.yesman.akythera.core.blueprint.particle.ParticleSystemBlueprint;
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

/// Content browser behaviour for particle system entries.
public final class ParticleSystemResourceProcessor implements ResourceProcessor<ParticleSystemBlueprint> {
    public static final ParticleSystemResourceProcessor INSTANCE = new ParticleSystemResourceProcessor();

    private ParticleSystemResourceProcessor() {}

    @Override
    public boolean canCreateSubDirectories() {
        return true;
    }

    /// Green `+`: a new entry in the focused directory, opened straight away.
    @Override
    public void onPressAddButton(final ContentBrowserPanel browser, final ExplorerPanel.TreeNode selected) {
        ExplorerPanel.TreeNode regRoot = ResourceProcessor.regRootOf(selected);

        if (regRoot == null) return;

        String subPath = ResourceProcessor.buildSubPath(regRoot, selected);
        AkytheraProject project = AkytheraProject.getCurrentProject();
        String entryName = project.findNextFreeName(AkytheraRegistries.DataPack.PARTICLE_SYSTEM_KEY, "particle_system", subPath);
        String idPath = subPath.isEmpty() ? entryName : subPath + "/" + entryName;
        ResourceKey<ParticleSystemBlueprint> key = project.createEntry(AkytheraRegistries.DataPack.PARTICLE_SYSTEM_KEY, idPath, true);

        project.saveEntry(key);

        Path focusDir = project.getProjectRoot()
            .resolve("data")
            .resolve(AkytheraRegistries.DataPack.PARTICLE_SYSTEM_KEY.identifier().getPath())
            .resolve(subPath);

        AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainScreen -> {
            mainScreen.explorerPanel.rebuildTree();
            mainScreen.explorerPanel.focusNodeByPath(focusDir);
        });
    }

    /// The systems' data provider, with the class of every module node graph of the project.
    @Override
    public ExportModalGenerator.Compile categoryCompile() {
        return new ExportModalGenerator.Compile(
            key -> key.registryKey().equals(AkytheraRegistries.DataPack.PARTICLE_SYSTEM_KEY)
                || key.registryKey().equals(AkytheraRegistries.DataPack.PARTICLE_INITIALIZER_KEY)
                || key.registryKey().equals(AkytheraRegistries.DataPack.PARTICLE_UPDATER_KEY),
            (project, sourceRoot, packageName) -> {
                FreemarkerExporter.exportRegistryProvider(project, AkytheraRegistries.DataPack.PARTICLE_SYSTEM_KEY, sourceRoot, packageName);
                FreemarkerExporter.exportParticleModules(project, sourceRoot, packageName);
            }
        );
    }

    @Override
    public @Nullable ContentWidget<?> createContentWidget(final Font font, final Path path) {
        return ResourceProcessor.resolveDatapackWidget(font, path, AkytheraRegistries.DataPack.PARTICLE_SYSTEM_KEY, ContentWidget.ParticleSystemContent::new);
    }
}
