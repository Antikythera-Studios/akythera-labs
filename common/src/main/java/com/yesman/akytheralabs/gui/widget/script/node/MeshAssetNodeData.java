package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.data.modularize.node.MeshAccessorNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.MeshLiteralNodeWidget;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.stream.Stream;

/// A mesh asset as a pure value — the leaf a mesh getter picks its meshes from.
///
/// The mesh is the project's **shared editor accessor**, whose id
/// [com.yesman.akytheralabs.AkytheraProject#renameAsset] mutates in place, so a renamed mesh file is
/// reflected here with no rewrite.
public record MeshAssetNodeData(AssetAccessor<? extends Mesh<?>> mesh) implements NodeData {
    /// Trimmed id &harr; the shared accessor. `MeshManager.CODEC` cannot be used here: it resolves
    /// through `MeshManager#unresolved`, which [com.yesman.akytheralabs.mixin.MixinMeshManager] does
    /// **not** reroute to the project's editor accessors, so a mesh bound through it would not
    /// follow a rename.
    private static final Codec<AssetAccessor<? extends Mesh<?>>> ACCESSOR_CODEC = Identifier.CODEC.xmap(
        MeshLiteralNodeWidget::resolve, MeshLiteralNodeWidget::trimmedId);

    public static final MapCodec<MeshAssetNodeData> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ACCESSOR_CODEC.fieldOf("asset").forGetter(MeshAssetNodeData::mesh)
            ).apply(instance, MeshAssetNodeData::new)
    );

    /// The bound mesh's current trimmed id.
    public Identifier meshId() {
        return MeshLiteralNodeWidget.trimmedId(mesh);
    }

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new MeshLiteralNodeWidget(ctx.font(), mesh, ctx.x(), ctx.y());
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return new MeshAccessorNode(mesh, in.id());
    }

    @Override
    public Stream<EditorReference> referencedReferences() {
        return mesh instanceof EditorReference ref ? Stream.of(ref) : Stream.empty();
    }
}
