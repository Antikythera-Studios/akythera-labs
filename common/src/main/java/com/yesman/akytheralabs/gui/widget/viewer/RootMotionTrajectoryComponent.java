package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.client.gui.widget.viewer.GridFloorComponent;
import com.yesman.akythera.client.gui.widget.viewer.Model3DViewer;
import com.yesman.akythera.client.gui.widget.viewer.Model3DViewerComponent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Supplier;

/// Draws a root-motion trajectory: a connected path with a sampling dot every fixed step,
/// big dots on the start/end, and a gizmo marker on the current playback position.
///
/// Self-contained — it pulls positions from a [RootMotionSampler] (built by the editor screen)
/// so it knows nothing about holders. Flat quads on the XZ plane via [RenderTypes#debugQuads]
/// (translucent, no cull), same approach as [GridFloorComponent].
public final class RootMotionTrajectoryComponent implements Model3DViewerComponent {
    private static final int LINE_COLOR = 0xFFFFCC00; // path
    private static final int DOT_COLOR = 0xFFFFE08A; // per-point dots
    private static final int EDGE_COLOR = 0xFFFF4040; // big start/end dots
    private static final int GIZMO_COLOR = 0xFF44AAFF; // current playback marker
    private static final float LINE_HALF_W = 0.02F;
    private static final float DOT_HALF = 0.04F;
    private static final float EDGE_HALF = 0.09F;
    private static final float GIZMO_HALF = 0.11F;

    /// Ordered polyline points of the trajectory (preview space). Built by the editor screen —
    /// a sequence/blendspace samples its root over time; a montage runs a section-ordered playback
    /// simulation. Re-fetched each frame so edits show up live.
    private final Supplier<List<Vec3>> pathSupplier;

    /// Current playback marker position (where the model's root is right now).
    private final Supplier<Vec3> gizmoSupplier;

    public RootMotionTrajectoryComponent(Supplier<List<Vec3>> pathSupplier, Supplier<Vec3> gizmoSupplier) {
        this.pathSupplier = pathSupplier;
        this.gizmoSupplier = gizmoSupplier;
    }

    @Override
    public void extractComponent(GuiGraphicsExtractor graphics, Model3DViewer model3DViewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a) {
        List<Vec3> path = pathSupplier.get();
        if (path == null || path.isEmpty()) return;

        RenderType renderType = RenderTypes.debugQuads();
        BufferBuilder builder = new BufferBuilder(byteBuffer, renderType.primitiveTopology(), renderType.format());
        PoseStack.Pose pose = poseStack.last();

        int n = path.size();
        for (int i = 0; i < n; i++) {
            Vec3 p = path.get(i);
            if (i > 0) emitLine(builder, pose, path.get(i - 1), p, LINE_HALF_W, LINE_COLOR);

            boolean edge = (i == 0 || i == n - 1);
            emitDot(builder, pose, p, edge ? EDGE_HALF : DOT_HALF, edge ? EDGE_COLOR : DOT_COLOR);
        }

        Vec3 gizmo = gizmoSupplier.get();
        if (gizmo != null) emitDot(builder, pose, gizmo, GIZMO_HALF, GIZMO_COLOR);

        MeshData meshData = builder.build();

        if (meshData != null) {
            RenderUtils.drawImmediate(renderType, meshData);
        }
    }

    /// Flat horizontal ribbon from a→c, widened perpendicular in the XZ plane.
    private static void emitLine(BufferBuilder builder, PoseStack.Pose pose, Vec3 a, Vec3 c, float halfW, int color) {
        double dx = c.x - a.x, dz = c.z - a.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-5) return;

        float px = (float) (-dz / len * halfW), pz = (float) (dx / len * halfW);
        builder.addVertex(pose, (float) a.x + px, (float) a.y, (float) a.z + pz).setColor(color);
        builder.addVertex(pose, (float) a.x - px, (float) a.y, (float) a.z - pz).setColor(color);
        builder.addVertex(pose, (float) c.x - px, (float) c.y, (float) c.z - pz).setColor(color);
        builder.addVertex(pose, (float) c.x + px, (float) c.y, (float) c.z + pz).setColor(color);
    }

    /// Flat diamond on the XZ plane centered at p.
    private static void emitDot(BufferBuilder builder, PoseStack.Pose pose, Vec3 p, float half, int color) {
        float x = (float) p.x, y = (float) p.y, z = (float) p.z;
        builder.addVertex(pose, x - half, y, z).setColor(color);
        builder.addVertex(pose, x, y, z - half).setColor(color);
        builder.addVertex(pose, x + half, y, z).setColor(color);
        builder.addVertex(pose, x, y, z + half).setColor(color);
    }

    @Override
    public int drawOrder() {
        return 20; // after grid (1) and model (10)
    }
}
