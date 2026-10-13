package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewerComponent;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.client.core.asset.mesh.StaticMesh;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akytheralabs.AkytheraLabs;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/// A [StaticMesh] drawn at [#placement] — what a mesh component naming one stands in as, since a static
/// mesh reads no bone and rides whatever places it.
public final class StaticMeshPreviewComponent implements Model3DViewerComponent {
    private final AssetAccessor<? extends StaticMesh> mesh;

    /// Where the mesh sits in viewer world space, read per frame: a socket marker's transform follows
    /// the animation the body is playing.
    private final Supplier<Transform> placement;

    private Supplier<@Nullable Identifier> texture = () -> null;

    public StaticMeshPreviewComponent(final AssetAccessor<? extends StaticMesh> mesh, final Supplier<Transform> placement) {
        this.mesh = mesh;
        this.placement = placement;
    }

    /// Null from the supplier falls back to the missing texture.
    public void setTexture(final Supplier<@Nullable Identifier> texture) {
        this.texture = texture;
    }

    @Override
    public void extractComponent(
        GuiGraphicsExtractor graphics, Model3DViewer model3DViewer, PoseStack poseStack,
        ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a
    ) {
        StaticMesh resolved = mesh.getOrHandleException(AkytheraLabs.LOGGER::error);

        if (resolved == null) return;

        Identifier supplied = texture.get();
        Identifier drawTexture = supplied != null ? supplied : MissingTextureAtlasSprite.getLocation();

        poseStack.pushPose();
        poseStack.mulPose(placement.get().toMatrix());

        BufferBuilder builder = new BufferBuilder(byteBuffer, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.ENTITY);

        resolved.draw(
            poseStack.last(), builder,
            Mesh.DrawingFunction.NEW_ENTITY,
            Model3DViewer.FULL_BRIGHT,
            1.0F, 1.0F, 1.0F, 1.0F,
            OverlayTexture.NO_OVERLAY
        );

        MeshData meshData = builder.build();

        if (meshData != null) {
            RenderUtils.drawImmediate(RenderTypes.entityTranslucent(drawTexture), meshData);
        }

        poseStack.popPose();
    }

    @Override
    public int drawOrder() {
        return 10;
    }
}
