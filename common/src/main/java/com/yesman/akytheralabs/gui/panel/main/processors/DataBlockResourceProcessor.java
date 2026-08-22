package com.yesman.akytheralabs.gui.panel.main.processors;

import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FreemarkerExporter;
import com.yesman.akytheralabs.dataholder.DataBlockRegistries;
import com.yesman.akytheralabs.gui.panel.main.ContentBrowserPanel;
import com.yesman.akytheralabs.gui.panel.main.ExplorerPanel;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import com.yesman.akytheralabs.gui.widget.main.ContentWidget;
import com.yesman.akytheralabs.gui.widget.main.ExportModalGenerator;
import net.minecraft.client.gui.Font;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/// Content-browser / explorer behaviour for one editable [EnumerableDataBlock] type. One instance
/// per type, bound to its labs registry key (see
/// [DataBlockRegistries]).
///
/// Data blocks are property-less, so there is **no screen editor** (double-click is a no-op) — only
/// **create** (green `+`, then rename to choose the identifier) and **remove** (context menu) are
/// offered. Entries live in the project's `ProjectObjectRegistry<T, ...>`; on-disk they are empty
/// `{}` JSON under `data/<registryPath>/`, so they ride the existing datapack delete / restore /
/// move pipeline unchanged.
public final class DataBlockResourceProcessor<T extends EnumerableDataBlock> implements ResourceProcessor<T> {
    private final ResourceKey<Registry<T>> registryKey;
    @SuppressWarnings("unused") // bound for symmetry / future merged-listing helpers
    private final EnumerableDataBlockManager<T> manager;

    public DataBlockResourceProcessor(
        ResourceKey<Registry<T>> registryKey,
        EnumerableDataBlockManager<T> manager
    ) {
        this.registryKey = registryKey;
        this.manager = manager;
    }

    @Override
    public boolean canCreateSubDirectories() {
        return true;
    }

    /// Green `+`: create a property-less entry under the focused directory with the next free name,
    /// then focus & select it so the user can rename it (the name *is* the data block identifier).
    /// No modal — there is nothing to configure.
    @Override
    public void onPressAddButton(ContentBrowserPanel browser, ExplorerPanel.TreeNode selected) {
        ExplorerPanel.TreeNode regRoot = ResourceProcessor.regRootOf(selected);
        if (regRoot == null) return;

        String subPath = ResourceProcessor.buildSubPath(regRoot, selected);
        AkytheraProject project = AkytheraProject.getCurrentProject();

        String entryName = project.findNextFreeName(registryKey, "new_entry", subPath);
        String idPath = subPath.isEmpty() ? entryName : subPath + "/" + entryName;
        ResourceKey<T> key = project.createEntry(registryKey, idPath, true);
        Path filePath = project.getEntryFilePath(key);

        int lastSlash = idPath.lastIndexOf('/');
        String parentSubdir = lastSlash >= 0 ? idPath.substring(0, lastSlash) : "";
        Path focusDir = project.getProjectRoot()
            .resolve("data")
            .resolve(registryKey.identifier().getPath())
            .resolve(parentSubdir);

        AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainScreen -> {
            mainScreen.explorerPanel.rebuildTree();
            mainScreen.explorerPanel.focusNodeByPath(focusDir);
            mainScreen.contentBrowserPanel.pickSelections(true, filePath);
        });
    }

    @Override
    public @Nullable ContentWidget<?> createContentWidget(Font font, Path path) {
        return ResourceProcessor.resolveDatapackWidget(
            font, path, registryKey, ContentWidget.DataBlockContent::new);
    }

    @Override
    public ExportModalGenerator.Compile categoryCompile() {
        return new ExportModalGenerator.Compile(
            key -> key.registryKey().equals(registryKey),
            (project, sourceRoot, packageName) -> FreemarkerExporter.exportDataBlockEnum(
                project, registryKey, sourceRoot, packageName)
        );
    }
}
