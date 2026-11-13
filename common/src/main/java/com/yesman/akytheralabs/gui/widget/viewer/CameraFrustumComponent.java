package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewerComponent;
import com.yesman.akythera.client.api.gui.viewer.Ray;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akytheralabs.akythera.entitypatch.component.CameraComponentHolder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/// A camera's view volume drawn as Blender draws one: a pyramid from the eye to the view's frame, with a triangle
/// over the frame's top edge marking up. Gray while its camera is not focused.
public final class CameraFrustumComponent implements Model3DViewerComponent {
    private static final int EDGE_COLOR = 0xFFFFAA33;
    private static final int FRAME_COLOR = 0xFFFFDD55;
    private static final int EDGE_COLOR_UNFOCUSED = 0xFF808080;
    private static final int FRAME_COLOR_UNFOCUSED = 0xFFA0A0A0;
    private static final float LINE_WIDTH = 2.0F;

    /// How far from a line a click still hits it, in widget pixels.
    private static final double PICK_TOLERANCE_PIXELS = 4.0D;

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
        Vector3f[] figure = figure();
        PoseStack.Pose pose = poseStack.last();
        int edgeColor = focused ? EDGE_COLOR : EDGE_COLOR_UNFOCUSED;
        int frameColor = focused ? FRAME_COLOR : FRAME_COLOR_UNFOCUSED;

        RenderType lines = RenderTypes.lines();
        BufferBuilder lineBuilder = new BufferBuilder(byteBuffer, lines.primitiveTopology(), lines.format());

        List<Vector3f[]> edges = edges(figure);

        for (int i = 0; i < edges.size(); i++) {
            addSegment(lineBuilder, pose, edges.get(i)[0], edges.get(i)[1], i < 4 ? edgeColor : frameColor);
        }

        MeshData lineMesh = lineBuilder.build();

        if (lineMesh != null) RenderUtils.drawImmediate(lines, lineMesh);

        // The up marker, a quad whose last two corners meet at the tip
        RenderType quads = RenderTypes.debugQuads();
        BufferBuilder quadBuilder = new BufferBuilder(byteBuffer, quads.primitiveTopology(), quads.format());

        for (Vector3f vertex : new Vector3f[] { figure[5], figure[6], figure[7], figure[7] }) {
            quadBuilder.addVertex(pose, vertex.x, vertex.y, vertex.z).setColor(edgeColor);
        }

        MeshData quadMesh = quadBuilder.build();

        if (quadMesh != null) RenderUtils.drawImmediate(quads, quadMesh);
    }

    /// Hits the frustum's lines and the up marker's edges.
    @Override
    public float rayTrace(final Model3DViewer owner, final Ray ray) {
        Vector3f[] figure = figure();
        List<Vector3f[]> segments = new ArrayList<>(edges(figure));

        segments.add(new Vector3f[] { figure[5], figure[6] });
        segments.add(new Vector3f[] { figure[6], figure[7] });
        segments.add(new Vector3f[] { figure[7], figure[5] });

        return pickSegments(owner, ray, segments);
    }

    /// The figure's points in world space: the apex, the frame's top left, top right, bottom right and bottom left
    /// corners, then the up marker's left, right and tip.
    private Vector3f[] figure() {
        Transform transform = eye.get();
        Quaternionf rotation = new Quaternionf(transform.rot());
        Vector3f apex = new Vector3f((float)transform.loc().x, (float)transform.loc().y, (float)transform.loc().z);
        float halfHeight = FRAME_DEPTH * (float)Math.tan(Math.toRadians(fov * 0.5F));
        float halfWidth = halfHeight * aspect;
        float base = halfHeight * 1.1F;
        float[][] local = {
            { -halfWidth, halfHeight }, { halfWidth, halfHeight }, { halfWidth, -halfHeight }, { -halfWidth, -halfHeight },
            { -halfWidth * 0.6F, base }, { halfWidth * 0.6F, base }, { 0.0F, base + halfWidth * 0.5F }
        };
        Vector3f[] points = new Vector3f[local.length + 1];

        points[0] = apex;

        for (int i = 0; i < local.length; i++) {
            points[i + 1] = new Vector3f(local[i][0], local[i][1], -FRAME_DEPTH).rotate(rotation).add(apex);
        }

        return points;
    }

    /// The pyramid's four edges from the apex, then the frame's four sides.
    private static List<Vector3f[]> edges(Vector3f[] figure) {
        return List.of(
            new Vector3f[] { figure[0], figure[1] }, new Vector3f[] { figure[0], figure[2] },
            new Vector3f[] { figure[0], figure[3] }, new Vector3f[] { figure[0], figure[4] },
            new Vector3f[] { figure[1], figure[2] }, new Vector3f[] { figure[2], figure[3] },
            new Vector3f[] { figure[3], figure[4] }, new Vector3f[] { figure[4], figure[1] }
        );
    }

    /// Distance along `ray` to the nearest of `segments` passing within [#PICK_TOLERANCE_PIXELS] of the cursor on
    /// screen, negative for a miss. Each segment is `{from, to}` in world space.
    static float pickSegments(final Model3DViewer viewer, final Ray ray, final List<Vector3f[]> segments) {
        Vector3f cursor = viewer.projectToWidget(ray.at(1.0F));

        if (cursor == null) return -1.0F;

        float nearest = -1.0F;

        for (Vector3f[] segment : segments) {
            Vector3f from = viewer.projectToWidget(segment[0]);
            Vector3f to = viewer.projectToWidget(segment[1]);

            if (from == null || to == null) continue;
            if (ArmatureViewComponent.distanceToSegment(cursor.x, cursor.y, from, to) > PICK_TOLERANCE_PIXELS) continue;

            float along = Math.max(0.0F, Math.min(
                new Vector3f(segment[0]).sub(ray.origin()).dot(ray.direction()),
                new Vector3f(segment[1]).sub(ray.origin()).dot(ray.direction())
            ));

            if (nearest < 0.0F || along < nearest) nearest = along;
        }

        return nearest;
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
