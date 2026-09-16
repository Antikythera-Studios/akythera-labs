package com.yesman.akytheralabs.gui.panel.main.processors;

import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.gui.panel.main.ContentBrowserPanel;
import com.yesman.akytheralabs.gui.panel.main.ExplorerPanel;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import com.yesman.akytheralabs.gui.widget.main.ContentWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

import com.yesman.akythera.api.input.InputAction;

/// Content browser behaviour for an input action entries.
public final class InputActionResourceProcessor implements ResourceProcessor<InputAction> {
    public static final InputActionResourceProcessor INSTANCE = new InputActionResourceProcessor();

    private InputActionResourceProcessor() {}

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
        String entryName = project.findNextFreeName(AkytheraRegistries.DataPack.INPUT_ACTION_KEY, "input_action", subPath);
        String idPath = subPath.isEmpty() ? entryName : subPath + "/" + entryName;
        ResourceKey<InputAction> key = project.createEntry(AkytheraRegistries.DataPack.INPUT_ACTION_KEY, idPath, true);

        project.saveEntry(key);

        Path focusDir = project.getProjectRoot()
            .resolve("data")
            .resolve(AkytheraRegistries.DataPack.INPUT_ACTION_KEY.identifier().getPath())
            .resolve(subPath);

        AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainScreen -> {
            mainScreen.explorerPanel.rebuildTree();
            mainScreen.explorerPanel.focusNodeByPath(focusDir);
        });
    }

    @Override
    public @Nullable ContentWidget<?> createContentWidget(final Font font, final Path path) {
        return ResourceProcessor.resolveDatapackWidget(font, path, AkytheraRegistries.DataPack.INPUT_ACTION_KEY, ContentWidget.InputActionContent::new);
    }
}
