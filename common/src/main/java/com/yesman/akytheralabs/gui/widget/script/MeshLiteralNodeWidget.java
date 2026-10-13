package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.api.mesh.MeshManager;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akytheralabs.akythera.asset.EditorAssetAccessors;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.node.MeshAssetNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/// A pure value node bound to one [Mesh] asset, with no inputs and one accessor output — the leaf a
/// mesh getter picks its meshes from.
///
/// [#mesh] is an `@EditorEditable` field, so focusing the node fills the Details panel with the
/// accessor's search PopupBox and picking there rebinds it. The accessor is the project's shared
/// instance, so the binding follows a rename of the mesh file.
public final class MeshLiteralNodeWidget extends AbstractNodeWidget {
    /// Blue/grey title, matching the accessor pin's object color.
    private static final int TITLE_COLOR = 0xFF20405A;

    /// The mesh this node yields. [DataHolder#EMPTY_REFERENCE] stands in for nothing picked and
    /// displays as `<none>`; it is non-null, so the accessor PopupBox can read the value's runtime
    /// `type()` to route its pick modal.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_MESH, order = 0)
    private AssetAccessor<? extends Mesh<?>> mesh;

    public MeshLiteralNodeWidget(Font font, AssetAccessor<? extends Mesh<?>> mesh, int graphX, int graphY) {
        super(font, title(trimmedId(mesh)), graphX, graphY);

        this.mesh = mesh;

        setTitleColor(TITLE_COLOR);
        addOutput(new Pin("", NodeColors.forType(AssetAccessor.class), false, AssetAccessor.class));

        relayout();
    }

    public AssetAccessor<? extends Mesh<?>> getMesh() {
        return mesh;
    }

    /// Rebinds this node to another mesh — the Details panel's picker. The title follows the new
    /// asset, as it does on creation.
    public void setMesh(AssetAccessor<? extends Mesh<?>> mesh) {
        this.mesh = mesh;
        setMessage(title(meshId()));
        relayout();
    }

    /// The bound asset's trimmed id, which is what the graph persists.
    public Identifier meshId() {
        return trimmedId(mesh);
    }

    /// The accessor's id minus [MeshManager#DIRECTORY] — the form the graph persists. Inverse of
    /// [#resolve].
    public static Identifier trimmedId(AssetAccessor<? extends Mesh<?>> mesh) {
        Identifier id = mesh.identifier();
        return Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().replace(MeshManager.DIRECTORY, ""));
    }

    /// Trimmed mesh id → accessor, never null — see [EditorAssetAccessors#mesh].
    public static AssetAccessor<? extends Mesh<?>> resolve(Identifier trimmedId) {
        return EditorAssetAccessors.mesh(trimmedId);
    }

    private static Component title(Identifier meshId) {
        return Component.literal(meshId.equals(DataHolder.EMPTY_REFERENCE) ? "Mesh" : meshId.getPath());
    }

    /// A node with nothing picked yields no mesh, so it is red on sight — no compile needed.
    @Override
    public List<Component> intrinsicErrors() {
        return meshId().equals(DataHolder.EMPTY_REFERENCE)
            ? List.of(Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VALIDATION_MISSING_ASSET, "<none>"))
            : List.of();
    }

    @Override
    public NodeData toData() {
        return new MeshAssetNodeData(mesh);
    }
}
