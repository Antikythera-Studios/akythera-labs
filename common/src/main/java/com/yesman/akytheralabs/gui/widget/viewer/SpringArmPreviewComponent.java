package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewerComponent;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.core.math.Transform;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Vector3f;

import java.util.function.Supplier;

/// A spring arm drawn as a line from its pivot to its end.
public final class SpringArmPreviewComponent implements Model3DViewerComponent {
    private static final int COLOR = 0xFFFF3333;

    /// The arm's pivot, in the viewer's world space.
    private final Supplier<Transform> pivot;

    /// The arm's end, in the viewer's world space.
    private final Supplier<Transform> end;

    public SpringArmPreviewComponent(Supplier<Transform> pivot, Supplier<Transform> end) {
        this.pivot = pivot;
        this.end = end;
    }

    @Override
    public void extractComponent(GuiGraphicsExtractor graphics, Model3DViewer model3DViewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a) {
        Transform from = pivot.get();
        Transform to = end.get();

        if (from.loc().distanceToSqr(to.loc()) < 1.0E-8D) return;

        RenderType lines = RenderTypes.lines();
        BufferBuilder builder = new BufferBuilder(byteBuffer, lines.primitiveTopology(), lines.format());

        CameraFrustumComponent.addSegment(
            builder, poseStack.last(),
            new Vector3f((float)from.loc().x, (float)from.loc().y, (float)from.loc().z),
            new Vector3f((float)to.loc().x, (float)to.loc().y, (float)to.loc().z),
            COLOR
        );

        MeshData mesh = builder.build();

        if (mesh != null) RenderUtils.drawImmediate(lines, mesh);
    }

    @Override
    public int drawOrder() {
        return 30; // with the focus markers, after grid (1), model (10) and root-motion trajectory (20)
    }
}
