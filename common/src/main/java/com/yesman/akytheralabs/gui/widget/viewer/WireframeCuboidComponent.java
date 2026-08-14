package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.client.core.asset.loader.MeshLoader;
import com.yesman.akythera.client.gui.widget.viewer.Model3DViewer;
import com.yesman.akythera.client.gui.widget.viewer.Model3DViewerComponent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Vector3f;

import java.util.function.Supplier;

/// The footprint a [MeshLoader.Metadata] describes, as a wireframe box: `width` × `height` ×
/// `width`, standing on `y = 0` and centered on the vertical axis — the same volume
/// [Model3DViewer#frameCamera] frames.
///
/// The metadata is read per frame, so the box follows the number boxes while they are being typed.
public final class WireframeCuboidComponent implements Model3DViewerComponent {
    private static final int COLOR = 0xFF37D5FF;
    private static final float LINE_WIDTH = 2.0F;

    /// The 12 edges of a unit box, as index pairs into [#CORNERS].
    private static final int[] EDGES = {
        0, 1,  1, 3,  3, 2,  2, 0, // bottom
        4, 5,  5, 7,  7, 6,  6, 4, // top
        0, 4,  1, 5,  2, 6,  3, 7  // uprights
    };

    /// Unit-box corners in (x, y, z) ∈ {-0.5, 0.5} × {0, 1} × {-0.5, 0.5}, scaled per frame.
    private static final float[] CORNERS = {
        -0.5F, 0.0F, -0.5F,
         0.5F, 0.0F, -0.5F,
        -0.5F, 0.0F,  0.5F,
         0.5F, 0.0F,  0.5F,
        -0.5F, 1.0F, -0.5F,
         0.5F, 1.0F, -0.5F,
        -0.5F, 1.0F,  0.5F,
         0.5F, 1.0F,  0.5F
    };

    private final Supplier<MeshLoader.Metadata> metadata;

    public WireframeCuboidComponent(Supplier<MeshLoader.Metadata> metadata) {
        this.metadata = metadata;
    }

    @Override
    public void extractComponent(GuiGraphicsExtractor graphics, Model3DViewer model3DViewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a) {
        MeshLoader.Metadata size = metadata.get();

        if (size.width() <= 0.0F || size.height() <= 0.0F) return;

        RenderType renderType = RenderTypes.lines();
        BufferBuilder builder = new BufferBuilder(byteBuffer, renderType.primitiveTopology(), renderType.format());
        PoseStack.Pose pose = poseStack.last();

        for (int i = 0; i < EDGES.length; i += 2) {
            Vector3f from = cornerAt(EDGES[i], size);
            Vector3f to = cornerAt(EDGES[i + 1], size);
            Vector3f direction = new Vector3f(to).sub(from).normalize();

            addVertex(builder, pose, from, direction);
            addVertex(builder, pose, to, direction);
        }

        MeshData meshData = builder.build();

        if (meshData != null) RenderUtils.drawImmediate(renderType, meshData);
    }

    private static Vector3f cornerAt(int index, MeshLoader.Metadata size) {
        return new Vector3f(
            CORNERS[index * 3] * size.width(),
            CORNERS[index * 3 + 1] * size.height(),
            CORNERS[index * 3 + 2] * size.width()
        );
    }

    /// [RenderTypes#lines] expects the segment direction in the normal slot and a per-vertex width.
    private static void addVertex(BufferBuilder builder, PoseStack.Pose pose, Vector3f position, Vector3f direction) {
        builder.addVertex(pose, position.x, position.y, position.z)
            .setColor(COLOR)
            .setNormal(pose, direction.x, direction.y, direction.z)
            .setLineWidth(LINE_WIDTH);
    }

    @Override
    public int drawOrder() {
        return 40; // after grid (1), model (10), root-motion trajectory (20) and bone sockets (30)
    }
}
