package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewerComponent;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akytheralabs.akythera.entitypatch.component.CameraComponentHolder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.Supplier;

/// A camera's view volume drawn as Blender draws one: a pyramid from the eye to the view's frame, with a triangle
/// over the frame's top edge marking up. Gray while its camera is not focused.
public final class CameraFrustumComponent implements Model3DViewerComponent {
    private static final int EDGE_COLOR = 0xFFFFAA33;
    private static final int FRAME_COLOR = 0xFFFFDD55;
    private static final int EDGE_COLOR_UNFOCUSED = 0xFF808080;
    private static final int FRAME_COLOR_UNFOCUSED = 0xFFA0A0A0;
    private static final float LINE_WIDTH = 2.0F;

    /// How far ahead of the eye the frame is drawn, in blocks.
    private static final float FRAME_DEPTH = 0.5F;

    private final CameraComponentHolder camera;

    /// Where the camera sits, in the viewer's world space, looking along its -Z.
    private final Supplier<Transform> eye;

    /// The vertical field of view, in degrees.
    private final float fov;

    /// The view's width over its height.
    private final float aspect;

    private boolean focused;

    public CameraFrustumComponent(CameraComponentHolder camera, Supplier<Transform> eye, float fov, float aspect) {
        this.camera = camera;
        this.eye = eye;
        this.fov = fov;
        this.aspect = aspect;
    }

    public CameraComponentHolder getCamera() {
        return camera;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    @Override
    public void extractComponent(GuiGraphicsExtractor graphics, Model3DViewer model3DViewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a) {
        Transform transform = eye.get();
        Quaternionf rotation = new Quaternionf(transform.rot());
        Vector3f apex = new Vector3f((float)transform.loc().x, (float)transform.loc().y, (float)transform.loc().z);
        float halfHeight = FRAME_DEPTH * (float)Math.tan(Math.toRadians(fov * 0.5F));
        float halfWidth = halfHeight * aspect;
        Vector3f topLeft = corner(apex, rotation, -halfWidth, halfHeight, -FRAME_DEPTH);
        Vector3f topRight = corner(apex, rotation, halfWidth, halfHeight, -FRAME_DEPTH);
        Vector3f bottomRight = corner(apex, rotation, halfWidth, -halfHeight, -FRAME_DEPTH);
        Vector3f bottomLeft = corner(apex, rotation, -halfWidth, -halfHeight, -FRAME_DEPTH);
        PoseStack.Pose pose = poseStack.last();
        int edgeColor = focused ? EDGE_COLOR : EDGE_COLOR_UNFOCUSED;
        int frameColor = focused ? FRAME_COLOR : FRAME_COLOR_UNFOCUSED;

        RenderType lines = RenderTypes.lines();
        BufferBuilder lineBuilder = new BufferBuilder(byteBuffer, lines.primitiveTopology(), lines.format());

        addSegment(lineBuilder, pose, apex, topLeft, edgeColor);
        addSegment(lineBuilder, pose, apex, topRight, edgeColor);
        addSegment(lineBuilder, pose, apex, bottomRight, edgeColor);
        addSegment(lineBuilder, pose, apex, bottomLeft, edgeColor);
        addSegment(lineBuilder, pose, topLeft, topRight, frameColor);
        addSegment(lineBuilder, pose, topRight, bottomRight, frameColor);
        addSegment(lineBuilder, pose, bottomRight, bottomLeft, frameColor);
        addSegment(lineBuilder, pose, bottomLeft, topLeft, frameColor);

        MeshData lineMesh = lineBuilder.build();

        if (lineMesh != null) RenderUtils.drawImmediate(lines, lineMesh);

        // The up marker, a quad whose last two corners meet at the tip
        RenderType quads = RenderTypes.debugQuads();
        BufferBuilder quadBuilder = new BufferBuilder(byteBuffer, quads.primitiveTopology(), quads.format());
        float base = halfHeight * 1.1F;
        Vector3f tip = corner(apex, rotation, 0.0F, base + halfWidth * 0.5F, -FRAME_DEPTH);

        for (Vector3f vertex : new Vector3f[] {
            corner(apex, rotation, -halfWidth * 0.6F, base, -FRAME_DEPTH), corner(apex, rotation, halfWidth * 0.6F, base, -FRAME_DEPTH), tip, tip
        }) {
            quadBuilder.addVertex(pose, vertex.x, vertex.y, vertex.z).setColor(edgeColor);
        }

        MeshData quadMesh = quadBuilder.build();

        if (quadMesh != null) RenderUtils.drawImmediate(quads, quadMesh);
    }

    /// The point `(x, y, z)` of the camera's own space in world space.
    private static Vector3f corner(Vector3f apex, Quaternionf rotation, float x, float y, float z) {
        return new Vector3f(x, y, z).rotate(rotation).add(apex);
    }

    /// [RenderTypes#lines] expects the segment direction in the normal slot and a per-vertex width.
    static void addSegment(BufferBuilder builder, PoseStack.Pose pose, Vector3f from, Vector3f to, int color) {
        Vector3f direction = new Vector3f(to).sub(from).normalize();

        builder.addVertex(pose, from.x, from.y, from.z)
            .setColor(color)
            .setNormal(pose, direction.x, direction.y, direction.z)
            .setLineWidth(LINE_WIDTH);
        builder.addVertex(pose, to.x, to.y, to.z)
            .setColor(color)
            .setNormal(pose, direction.x, direction.y, direction.z)
            .setLineWidth(LINE_WIDTH);
    }

    @Override
    public int drawOrder() {
        return 30; // with the focus markers, after grid (1), model (10) and root-motion trajectory (20)
    }
}
