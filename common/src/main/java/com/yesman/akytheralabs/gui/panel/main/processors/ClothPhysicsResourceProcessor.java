package com.yesman.akytheralabs.gui.panel.main.processors;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.core.asset.loader.binary.ClothPhysicsLoader;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.core.asset.loader.AssetLoader;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.physics.ClothPhysicsDataHolder;
import com.yesman.akytheralabs.gui.panel.main.ContentBrowserPanel;
import com.yesman.akytheralabs.gui.panel.main.ExplorerPanel;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import com.yesman.akytheralabs.gui.widget.main.AccessorSelectionModal;
import com.yesman.akytheralabs.gui.widget.main.ContentWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/// The cloths of the project's skeletal meshes, one file per mesh at the mesh's own path.
public final class ClothPhysicsResourceProcessor implements ResourceProcessor<Identifier> {
    public static final ClothPhysicsResourceProcessor INSTANCE = new ClothPhysicsResourceProcessor();

    private ClothPhysicsResourceProcessor() {}

    /// A cloth's folder mirrors its mesh's, so the tree takes no folders of its own.
    @Override
    public boolean canCreateSubDirectories() {
        return false;
    }

    /// Picks a skeletal mesh and writes an all-kinematic cloth for it.
    @SuppressWarnings("unchecked")
    @Override
    public void onPressAddButton(ContentBrowserPanel browser, ExplorerPanel.TreeNode selected) {
        ModalManager.getInstance().openModalWidgetAtCenter(new AccessorSelectionModal(
            browser.getFont(),
            AssetAccessor.Type.SKELETAL_MESH,
            accessor -> create((AssetAccessor<? extends SkeletalMesh>) accessor)
        ));
    }

    /// Writes a cloth for `mesh` — all-kinematic unless it has one — then focuses it.
    public static void create(AssetAccessor<? extends SkeletalMesh> mesh) {
        SkeletalMesh resolved = mesh.getOrHandleException(AkytheraLabs.LOGGER::error);
        Identifier clothId = ClothPhysicsLoader.clothPathOf(mesh.identifier());

        if (resolved == null || clothId == null) return;

        ClothPhysicsDataHolder holder = AkytheraProject.getCurrentProject().getClothPhysicsHolder(mesh);
        holder.fitWeights(resolved.getPositionCount());

        if (!holder.save()) return;

        Path folder = AkytheraProject.getBinaryAssetFileOf(clothId).getParent();

        AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainScreen -> {
            mainScreen.explorerPanel.rebuildTree();
            mainScreen.explorerPanel.focusNodeByPath(folder);
        });
    }

    /// The `.akasset` of a cloth; its `.akmeta` sidecar is not an entry.
    @Override
    public @Nullable ContentWidget<?> createContentWidget(Font font, Path path) {
        if (!path.toString().endsWith("." + AssetLoader.BINARY_EXTENSION)) return null;

        return new ContentWidget.ClothPhysicsContent(font, path, ResourceProcessor.fileNameNoExt(path));
    }
}
