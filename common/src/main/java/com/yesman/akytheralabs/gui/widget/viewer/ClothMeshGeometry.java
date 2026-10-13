package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Vector3f;

/// A skeletal mesh's bind pose as the cloth editor draws and picks it, in viewer world space.
///
/// @param positions bind-pose position per mesh position index
/// @param triangles position-index triples
/// @param edges     unique position-index pairs
public record ClothMeshGeometry(Vector3f[] positions, int[] triangles, int[] edges) {
    private static final float LINE_WIDTH = 1.5F;

    /// How far a line is pulled towards the camera, in viewer world units.
    private static final float SURFACE_LIFT = 0.002F;

    public static ClothMeshGeometry of(final SkeletalMesh mesh) {
        int positionCount = mesh.getPositionCount();
        Vector3f[] positions = new Vector3f[positionCount];
        IntArrayList triangles = new IntArrayList();
        LongOpenHashSet edgeKeys = new LongOpenHashSet();
        IntArrayList edges = new IntArrayList();

        for (int i = 0; i < positionCount; i++) {
            positions[i] = mesh.getPosition(i, new Vector3f());
        }

        for (SkeletalMesh.SkeletalMeshPart part : mesh.getParts().values()) {
            for (int v = 0; v + 2 < part.vertexCount(); v += 3) {
                int[] corners = { part.getPositionIndex(v), part.getPositionIndex(v + 1), part.getPositionIndex(v + 2) };

                if (corners[0] == corners[1] || corners[1] == corners[2] || corners[2] == corners[0]) continue;

                triangles.addElements(triangles.size(), corners);

                for (int c = 0; c < 3; c++) {
                    int from = Math.min(corners[c], corners[(c + 1) % 3]);
                    int to = Math.max(corners[c], corners[(c + 1) % 3]);

                    if (edgeKeys.add(((long) from << 32) | to)) {
                        edges.add(from);
                        edges.add(to);
                    }
                }
            }
        }

        return new ClothMeshGeometry(positions, triangles.toIntArray(), edges.toIntArray());
    }

    /// `point` pulled [#SURFACE_LIFT] towards `eye`, so a line lying on the surface draws over it.
    public static Vector3f lifted(final Vector3f point, final Vector3f eye) {
        Vector3f toEye = new Vector3f(eye).sub(point);
        float length = toEye.length();

        return length < 1.0e-6F ? new Vector3f(point) : toEye.mul(SURFACE_LIFT / length).add(point);
    }

    /// [RenderTypes#lines] expects the segment direction in the normal slot and a per-vertex width.
    public static void emitLine(
        final BufferBuilder builder, final PoseStack.Pose pose,
        final Vector3f from, final int fromColor, final Vector3f to, final int toColor
    ) {
        Vector3f direction = new Vector3f(to).sub(from);

        if (direction.lengthSquared() < 1.0e-10F) return;

        direction.normalize();

        builder.addVertex(pose, from.x, from.y, from.z)
            .setColor(fromColor)
            .setNormal(pose, direction.x, direction.y, direction.z)
            .setLineWidth(LINE_WIDTH);
        builder.addVertex(pose, to.x, to.y, to.z)
            .setColor(toColor)
            .setNormal(pose, direction.x, direction.y, direction.z)
            .setLineWidth(LINE_WIDTH);
    }
}
