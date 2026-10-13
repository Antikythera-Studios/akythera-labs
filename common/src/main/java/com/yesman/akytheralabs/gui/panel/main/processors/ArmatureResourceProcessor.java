package com.yesman.akytheralabs.gui.panel.main.processors;

import com.yesman.akythera.core.asset.loader.AssetLoader;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.gui.panel.main.ContentBrowserPanel;
import com.yesman.akytheralabs.gui.panel.main.ContentContextOption;
import com.yesman.akytheralabs.gui.panel.main.ExplorerPanel;
import com.yesman.akytheralabs.gui.widget.UniversalMenu;
import com.yesman.akytheralabs.gui.widget.main.ContentWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ArmatureResourceProcessor implements ResourceProcessor<Identifier> {
    public static final ArmatureResourceProcessor INSTANCE = new ArmatureResourceProcessor();

    private ArmatureResourceProcessor() {}

    @Override
    public boolean canCreateSubDirectories() {
        return true;
    }

    @Override
    public void onPressAddButton(ContentBrowserPanel browser, ExplorerPanel.TreeNode selected) {
        ExplorerPanel.TreeNode regRoot = ResourceProcessor.regRootOf(selected);
        Path targetDir = ResourceProcessor.resolveTargetDir(regRoot, selected);
        if (targetDir == null) return;

        CompletableFuture
            .supplyAsync(() -> TinyFileDialogs.tinyfd_openFileDialog(
                "Select Armature File", "", null, "Armature Files (*." + AssetLoader.BINARY_EXTENSION + " *.json)", false))
            .thenAcceptAsync(result -> {
                if (result == null) return;
                browser.importExternalAssets(List.of(Path.of(result)), targetDir, INSTANCE);
            }, Minecraft.getInstance());
    }

    @Override
    public boolean canImport(Path file, List<Path> batch) {
        return ResourceProcessor.isAssetFile(file);
    }

    @Override
    public AkytheraProject.AssetLoadResult<?> importFile(Path source, Path destination) {
        return AkytheraProject.getCurrentProject().loadArmatureToCurrentProject(source, destination);
    }

    @Override
    public ContentWidget<?> createContentWidget(Font font, Path path) {
        return new ContentWidget.ArmatureAssetContent(font, path, ResourceProcessor.fileNameNoExt(path));
    }

    @Override
    public UniversalMenu.Actions<ContentContextOption> contextActions(ContentBrowserPanel browser, ContentWidget<?> rightPressed) {
        return ResourceProcessor.entryActions(this, browser, rightPressed)
            .on(ContentContextOption.CREATE_ARMATURE_PHYSICS, () -> {
                for (ContentWidget<?> widget : browser.getSelectedWidgets()) {
                    if (widget.getReferencingObject() instanceof AkytheraProject.EditorArmatureAccessor<?> armature) {
                        ArmaturePhysicsResourceProcessor.create(armature);
                    }
                }
            });
    }
}