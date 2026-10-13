package com.yesman.akytheralabs.gui.panel.main.processors;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.physics.ArmaturePhysicsDataHolder;
import com.yesman.akytheralabs.gui.panel.main.ContentBrowserPanel;
import com.yesman.akytheralabs.gui.panel.main.ExplorerPanel;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import com.yesman.akytheralabs.gui.widget.main.AccessorSelectionModal;
import com.yesman.akytheralabs.gui.widget.main.ContentWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

/// The bone bodies of the project's armatures, one file per armature at the armature's own path.
public final class ArmaturePhysicsResourceProcessor implements ResourceProcessor<Identifier> {
    public static final ArmaturePhysicsResourceProcessor INSTANCE = new ArmaturePhysicsResourceProcessor();

    private ArmaturePhysicsResourceProcessor() {}

    /// The folders mirror the armature tree, so the tree takes none of its own.
    @Override
    public boolean canCreateSubDirectories() {
        return false;
    }

    /// Picks an armature and writes an empty body set for it.
    @Override
    public void onPressAddButton(ContentBrowserPanel browser, ExplorerPanel.TreeNode selected) {
        ModalManager.getInstance().openModalWidgetAtCenter(new AccessorSelectionModal(
            browser.getFont(),
            AssetAccessor.Type.ARMATURE,
            ArmaturePhysicsResourceProcessor::create
        ));
    }

    /// Writes an empty body set for `armature`, taking its preview model, unless it has one already;
    /// then focuses it.
    public static void create(AssetAccessor<?> armature) {
        Identifier physicsId = AkytheraProject.armaturePhysicsIdOf(armature.identifier());

        if (physicsId == null) return;

        Path file = AkytheraProject.getJsonAssetFileOf(physicsId);

        if (!Files.isRegularFile(file)) {
            ArmaturePhysicsDataHolder holder = AkytheraProject.getCurrentProject().getOrIssueEditorArmaturePhysicsAccessor(physicsId).metadata();

            if (armature instanceof AkytheraProject.EditorArmatureAccessor<?> editorArmature) {
                holder.setPreviewEntityType(editorArmature.metadata().getPreviewEntityType());
            }

            if (!holder.save()) return;
        }

        AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainScreen -> {
            mainScreen.explorerPanel.rebuildTree();
            mainScreen.explorerPanel.focusNodeByPath(file.getParent());
        });
    }

    @Override
    public @Nullable ContentWidget<?> createContentWidget(Font font, Path path) {
        if (!path.toString().endsWith(".json")) return null;

        return new ContentWidget.ArmaturePhysicsContent(font, path, ResourceProcessor.fileNameNoExt(path));
    }
}
