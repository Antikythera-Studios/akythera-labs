package com.yesman.akytheralabs.gui.panel.main.processors;

import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import com.yesman.akytheralabs.gui.panel.main.ContentBrowserPanel;
import com.yesman.akytheralabs.gui.panel.main.ExplorerPanel;
import com.yesman.akytheralabs.gui.widget.main.ContentWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import org.jspecify.annotations.Nullable;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/// The project's PNG textures under [AkytheraProject#TEXTURES_DIRECTORY]. A texture's `.mcmeta`
/// sidecar travels with it and never shows as a content of its own. A sidecar is imported only when
/// it is picked or dropped together with its texture.
public final class TextureResourceProcessor implements ResourceProcessor<Identifier> {
    public static final TextureResourceProcessor INSTANCE = new TextureResourceProcessor();

    private static final String TEXTURE_EXTENSION = ".png";
    private static final String SIDECAR_EXTENSION = TEXTURE_EXTENSION + PackResources.METADATA_EXTENSION;

    private TextureResourceProcessor() {}

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
                "Select Texture File", "", null, "PNG Images (*" + TEXTURE_EXTENSION + ")", false))
            .thenAcceptAsync(result -> {
                if (result == null) return;
                browser.importExternalAssets(List.of(Path.of(result)), targetDir, INSTANCE);
            }, Minecraft.getInstance());
    }

    @Override
    public boolean canImport(Path file, List<Path> batch) {
        String fileName = file.getFileName().toString();

        if (fileName.endsWith(TEXTURE_EXTENSION)) return true;

        return fileName.endsWith(SIDECAR_EXTENSION) && batch.contains(file.resolveSibling(FileSystemHelper.stem(fileName)));
    }

    @Override
    public AkytheraProject.AssetLoadResult<?> importFile(Path source, Path destination) {
        AkytheraProject project = AkytheraProject.getCurrentProject();

        return source.getFileName().toString().endsWith(SIDECAR_EXTENSION)
            ? project.loadTextureMetadataToCurrentProject(source, destination)
            : project.loadTextureToCurrentProject(source, destination);
    }

    @Override
    public @Nullable ContentWidget<?> createContentWidget(Font font, Path path) {
        if (!path.getFileName().toString().endsWith(TEXTURE_EXTENSION)) return null;

        return new ContentWidget.TextureAssetContent(font, path, ResourceProcessor.fileNameNoExt(path));
    }
}
