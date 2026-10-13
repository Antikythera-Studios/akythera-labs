package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.client.api.gui.viewer.Ray;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.Map;

/// A skeletal mesh's bind pose as the cloth editor draws and picks it, in viewer world space.
///
/// @param positions bind-pose position per mesh position index
/// @param triangles position-index triples
/// @param edges     position-index pairs, one per edge place, without the diagonals of quads
public record ClothMeshGeometry(Vector3f[] positions, int[] triangles, int[] edges) {
    private static final float LINE_WIDTH = 1.5F;

    /// Coordinates closer than one of this many parts of a block are one edge end.
    private static final float EDGE_GRID = 1.0E5F;

    /// Least dot product of two unit normals for their triangles to lie flat on one face.
    private static final float COPLANAR = 0.999F;

    /// How far a line is pulled towards the camera, in viewer world units.
    private static final float SURFACE_LIFT = 0.002F;

    private static final float RAY_EPSILON = 1.0E-6F;

    /// Reads `mesh`. Edges are told apart by where their ends sit, not by index, so the vertices a mesh
    /// stacks on one place make one edge. An edge that is the longest side of every triangle on it, all of
    /// them lying flat on one face, is the diagonal of a quad and is left out.
    public static ClothMeshGeometry of(final SkeletalMesh mesh) {
        int positionCount = mesh.getPositionCount();
        Vector3f[] positions = new Vector3f[positionCount];
        IntArrayList triangles = new IntArrayList();
        Map<String, MeshEdge> meshEdges = new LinkedHashMap<>();
        IntArrayList edges = new IntArrayList();

        for (int i = 0; i < positionCount; i++) {
            positions[i] = mesh.getPosition(i, new Vector3f());
        }

        for (SkeletalMesh.SkeletalMeshPart part : mesh.getParts().values()) {
            for (int v = 0; v + 2 < part.vertexCount(); v += 3) {
                int[] corners = { part.getPositionIndex(v), part.getPositionIndex(v + 1), part.getPositionIndex(v + 2) };

                if (corners[0] == corners[1] || corners[1] == corners[2] || corners[2] == corners[0]) continue;

                triangles.addElements(triangles.size(), corners);

                Vector3f normal = positions[corners[1]].sub(positions[corners[0]], new Vector3f())
                    .cross(positions[corners[2]].sub(positions[corners[0]], new Vector3f()));

                // A triangle with no area has no edge of its own to show
                if (normal.lengthSquared() < RAY_EPSILON * RAY_EPSILON) continue;

                normal.normalize();

                float longest = 0.0F;

                for (int c = 0; c < 3; c++) {
                    longest = Math.max(longest, positions[corners[c]].distanceSquared(positions[corners[(c + 1) % 3]]));
                }

                for (int c = 0; c < 3; c++) {
                    int from = corners[c];
                    int to = corners[(c + 1) % 3];
                    String fromKey = Math.round(positions[from].x * EDGE_GRID) + "," + Math.round(positions[from].y * EDGE_GRID) + "," + Math.round(positions[from].z * EDGE_GRID);
                    String toKey = Math.round(positions[to].x * EDGE_GRID) + "," + Math.round(positions[to].y * EDGE_GRID) + "," + Math.round(positions[to].z * EDGE_GRID);
                    boolean diagonal = positions[from].distanceSquared(positions[to]) >= longest;
                    String key = fromKey.compareTo(toKey) < 0 ? fromKey + ";" + toKey : toKey + ";" + fromKey;
                    MeshEdge seen = meshEdges.get(key);

                    meshEdges.put(
                        key,
                        seen == null
                            ? new MeshEdge(from, to, normal, false, diagonal)
                            : new MeshEdge(seen.from, seen.to, seen.normal, true, seen.diagonal && diagonal && Math.abs(seen.normal.dot(normal)) > COPLANAR)
                    );
                }
            }
        }

        for (MeshEdge edge : meshEdges.values()) {
            if (edge.shared && edge.diagonal) continue;

            edges.add(edge.from);
            edges.add(edge.to);
        }

        return new ClothMeshGeometry(positions, triangles.toIntArray(), edges.toIntArray());
    }

    /// One edge place while a mesh is read.
    ///
    /// @param normal   the unit normal of the first triangle on the edge
    /// @param shared   whether more than one triangle lies on the edge
    /// @param diagonal whether the edge is the longest side of every triangle on it, all of them lying flat with the first
    private record MeshEdge(int from, int to, Vector3f normal, boolean shared, boolean diagonal) {
    }

    /// Distance along `ray` to the triangle `a`, `b`, `c`, -1 for a miss. Möller–Trumbore, both faces.
    public static float rayTriangle(final Ray ray, final Vector3f a, final Vector3f b, final Vector3f c) {
        Vector3f edge1 = b.sub(a, new Vector3f());
        Vector3f edge2 = c.sub(a, new Vector3f());
        Vector3f p = ray.direction().cross(edge2, new Vector3f());
        float determinant = edge1.dot(p);

        if (Math.abs(determinant) < RAY_EPSILON) return -1.0F;

        float inverse = 1.0F / determinant;
        Vector3f s = ray.origin().sub(a, new Vector3f());
        float u = s.dot(p) * inverse;

        if (u < 0.0F || u > 1.0F) return -1.0F;

        Vector3f q = s.cross(edge1, new Vector3f());
        float v = ray.direction().dot(q) * inverse;

        if (v < 0.0F || u + v > 1.0F) return -1.0F;

        float distance = edge2.dot(q) * inverse;

        return distance > RAY_EPSILON ? distance : -1.0F;
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
