package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewerComponent;
import com.yesman.akythera.client.api.gui.viewer.Ray;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.akythera.physics.ParticleMappingSettings;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/// Pairs the vertices of a thick cloth's two layers 1:1 on a skeletal mesh's bind pose. The first click
/// picks a simulating vertex, the second its offset vertex; [EditorKeyMappings#DELETE] unpairs the
/// picked one. [#autoPair] pairs the rest along a direction.
///
/// Every vertex and edge is drawn, hidden ones included: simulating vertices orange, offset vertices
/// blue, and a line across each pair.
public final class ClothParticleMappingComponent implements Model3DViewerComponent {
    private static final int COLOR_EDGE = 0xFF5A5A5A;
    private static final int COLOR_VERTEX = 0xFF9A9A9A; // unpaired
    private static final int COLOR_SIMULATING = 0xFFFF8C1A;
    private static final int COLOR_OFFSET = 0xFF3A8DFF;
    private static final int COLOR_PICKED = 0xFFFFFF00;
    private static final int COLOR_HINT = 0xFFFFFFFF;
    private static final int COLOR_HINT_BACKGROUND = 0xAA000000;

    private static final Component HINT = Component.translatable(LangKeys.GUI_CLOTH_PHYSICS_SCREEN_MAPPING_HINT);

    /// Half-side of a vertex marker, in widget pixels.
    private static final int MARKER = 2;

    /// How close to a vertex a click must land, in widget pixels.
    private static final float PICK_PIXELS = 6.0F;

    /// Vertices this close to each other on screen count as one spot, where the one nearer the camera
    /// wins. In widget pixels.
    private static final float TIE_PIXELS = 1.0F;

    /// Candidates this close to the auto pair line count as on it, where the nearer one wins. In mesh
    /// units.
    private static final float TIE_DISTANCE = 1.0E-4F;

    private final AssetAccessor<? extends SkeletalMesh> mesh;
    private final Supplier<int[]> offsetPairs;

    /// Receives the pairs before and after an edit.
    private final BiConsumer<int[], int[]> onPairsCommitted;

    /// The mesh [#geometry] was read from.
    private @Nullable SkeletalMesh builtMesh;
    private @Nullable ClothMeshGeometry geometry;

    /// Mesh position index of the first click, -1 for none.
    private int picked = -1;

    public ClothParticleMappingComponent(
        final AssetAccessor<? extends SkeletalMesh> mesh,
        final Supplier<int[]> offsetPairs,
        final BiConsumer<int[], int[]> onPairsCommitted
    ) {
        this.mesh = mesh;
        this.offsetPairs = offsetPairs;
        this.onPairsCommitted = onPairsCommitted;
    }

    @Override
    public void extractComponent(
        GuiGraphicsExtractor graphics, Model3DViewer viewer, PoseStack poseStack,
        ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a
    ) {
        ClothMeshGeometry current = geometry();
        Ray probe = viewer.buildRay(viewer.getX(), viewer.getY());

        if (probe == null || current == null) return;

        Vector3f eye = probe.origin();
        PoseStack.Pose pose = poseStack.last();
        Vector3f[] positions = current.positions();
        int[] edges = current.edges();
        int[] pairs = offsetPairs.get();
        RenderType renderType = RenderTypes.lines();
        BufferBuilder builder = new BufferBuilder(byteBuffer, renderType.primitiveTopology(), renderType.format());

        for (int e = 0; e + 1 < edges.length; e += 2) {
            ClothMeshGeometry.emitLine(
                builder, pose, ClothMeshGeometry.lifted(positions[edges[e]], eye), COLOR_EDGE, ClothMeshGeometry.lifted(positions[edges[e + 1]], eye), COLOR_EDGE
            );
        }

        for (int k = 0; k + 1 < pairs.length; k += 2) {
            if (Math.max(pairs[k], pairs[k + 1]) >= positions.length) continue;

            ClothMeshGeometry.emitLine(
                builder, pose, ClothMeshGeometry.lifted(positions[pairs[k]], eye), COLOR_SIMULATING, ClothMeshGeometry.lifted(positions[pairs[k + 1]], eye), COLOR_OFFSET
            );
        }

        MeshData meshData = builder.build();

        if (meshData != null) RenderUtils.drawImmediate(renderType, meshData);
    }

    /// A marker on every vertex, bigger under the cursor and on the pick, and the hint in the top-right
    /// corner.
    @Override
    public void extractOverlay(final GuiGraphicsExtractor graphics, final Model3DViewer viewer, final int mouseX, final int mouseY, final float a) {
        if (geometry == null) return;

        Vector3f[] positions = geometry.positions();
        int[] pairs = offsetPairs.get();
        int[] colors = new int[positions.length];
        int hovered = pick(viewer, mouseX, mouseY);

        Arrays.fill(colors, COLOR_VERTEX);

        for (int k = 0; k + 1 < pairs.length; k += 2) {
            if (Math.max(pairs[k], pairs[k + 1]) >= positions.length) continue;

            colors[pairs[k]] = COLOR_SIMULATING;
            colors[pairs[k + 1]] = COLOR_OFFSET;
        }

        graphics.enableScissor(viewer.getX(), viewer.getY(), viewer.getRight(), viewer.getBottom());

        for (int i = 0; i < positions.length; i++) {
            Vector3f projected = viewer.projectToWidget(positions[i]);

            if (projected == null) continue;

            int x = Math.round(projected.x);
            int y = Math.round(projected.y);
            int size = i == picked || i == hovered ? MARKER + 1 : MARKER;

            graphics.fill(x - size, y - size, x + size + 1, y + size + 1, i == picked ? COLOR_PICKED : colors[i]);
        }

        Font font = Minecraft.getInstance().font;
        int hintX = viewer.getRight() - 6 - font.width(HINT);
        int hintY = viewer.getY() + 6;

        graphics.fill(hintX - 4, hintY - 3, viewer.getRight() - 2, hintY + font.lineHeight + 3, COLOR_HINT_BACKGROUND);
        graphics.text(font, HINT, hintX, hintY, COLOR_HINT, false);
        graphics.disableScissor();
    }

    @Override
    public int drawOrder() {
        return 20; // after grid (1) and the preview model (10)
    }

    @Override
    public boolean canBeFocused(final Model3DViewer owner) {
        return true;
    }

    /// Wins every pick while it is in the viewer.
    @Override
    public float rayTrace(final Model3DViewer owner, final Ray ray) {
        return 0.0F;
    }

    /// Takes a left press on a vertex. The first picks it, the second pairs the pick with it as its offset
    /// vertex, and a second press on the pick drops it. A press on no vertex goes to the camera.
    @Override
    public boolean onMouseDown(final Model3DViewer owner, final MouseButtonEvent event) {
        if (event.button() != 0) return false;

        int hit = pick(owner, event.x(), event.y());

        if (hit < 0) return false;

        if (picked < 0) {
            picked = hit;
            return true;
        }

        if (picked != hit) {
            int[] before = offsetPairs.get();
            int[] kept = withoutPairsOf(before, picked, hit);
            int[] after = Arrays.copyOf(kept, kept.length + 2);

            after[kept.length] = picked;
            after[kept.length + 1] = hit;
            onPairsCommitted.accept(before, after);
        }

        picked = -1;

        return true;
    }

    /// [EditorKeyMappings#DELETE] unpairs the picked vertex.
    @Override
    public boolean onKeyPressed(final Model3DViewer owner, final KeyEvent event) {
        if (picked < 0 || !EditorKeyMappings.DELETE.matches(event)) return false;

        int[] before = offsetPairs.get();
        int[] after = withoutPairsOf(before, picked, picked);

        if (after.length != before.length) onPairsCommitted.accept(before, after);

        picked = -1;

        return true;
    }

    @Override
    public void destroy() {
        picked = -1;
    }

    /// Removes every pair, as one edit.
    public void clearPairs() {
        int[] before = offsetPairs.get();

        picked = -1;

        if (before.length > 0) onPairsCommitted.accept(before, new int[0]);
    }

    /// Pairs every unpaired vertex with the unpaired vertex it sees along `settings`' direction, as one
    /// edit.
    ///
    /// A candidate must lie ahead of the root, within [ParticleMappingSettings#getMaxAngle] of the
    /// direction. The one nearest the line through the root wins, the nearer one along the line on a
    /// tie. When two roots want the same vertex, the root nearer its line keeps it.
    public void autoPair(final ParticleMappingSettings settings) {
        ClothMeshGeometry current = geometry();
        Vector3f direction = settings.getDirection().toVector3f();

        if (current == null || direction.lengthSquared() < 1.0E-12F) return;

        direction.normalize();

        Vector3f[] positions = current.positions();
        int[] before = offsetPairs.get();
        boolean[] used = new boolean[positions.length];
        float minCos = (float) Math.cos(Math.toRadians(settings.getMaxAngle()));
        Vector3f toCandidate = new Vector3f();
        List<Candidate> candidates = new ArrayList<>();

        for (int k = 0; k + 1 < before.length; k += 2) {
            if (before[k] < used.length) used[before[k]] = true;
            if (before[k + 1] < used.length) used[before[k + 1]] = true;
        }

        for (int root = 0; root < positions.length; root++) {
            if (used[root]) continue;

            Candidate best = null;

            for (int offset = 0; offset < positions.length; offset++) {
                if (offset == root || used[offset]) continue;

                positions[offset].sub(positions[root], toCandidate);

                float along = toCandidate.dot(direction);
                float distance = toCandidate.length();

                if (along <= 0.0F || along < distance * minCos) continue;

                float aside = (float) Math.sqrt(Math.max(0.0F, distance * distance - along * along));
                boolean closer = best == null || aside < best.aside() - TIE_DISTANCE;
                boolean tiedNearer = best != null && aside <= best.aside() + TIE_DISTANCE && along < best.along();

                if (closer || tiedNearer) best = new Candidate(root, offset, aside, along);
            }

            if (best != null) candidates.add(best);
        }

        candidates.sort(Comparator.comparingDouble(Candidate::aside).thenComparingDouble(Candidate::along));

        IntArrayList after = new IntArrayList(before);

        for (Candidate candidate : candidates) {
            if (used[candidate.root()] || used[candidate.offset()]) continue;

            used[candidate.root()] = true;
            used[candidate.offset()] = true;
            after.add(candidate.root());
            after.add(candidate.offset());
        }

        if (after.size() != before.length) onPairsCommitted.accept(before, after.toIntArray());
    }

    /// The bind pose, read again when the mesh was reloaded. Null while the mesh does not resolve.
    private @Nullable ClothMeshGeometry geometry() {
        SkeletalMesh skeletalMesh = mesh.getOrHandleException(AkytheraLabs.LOGGER::error);

        if (skeletalMesh == null) return null;

        if (skeletalMesh != builtMesh) {
            geometry = ClothMeshGeometry.of(skeletalMesh);
            builtMesh = skeletalMesh;
        }

        return geometry;
    }

    /// The vertex nearest (`x`, `y`) within [#PICK_PIXELS], -1 for none.
    private int pick(final Model3DViewer viewer, final double x, final double y) {
        if (geometry == null) return -1;

        Vector3f[] positions = geometry.positions();
        int nearest = -1;
        float nearestPixels = Float.MAX_VALUE;
        float nearestDepth = Float.MAX_VALUE;

        for (int i = 0; i < positions.length; i++) {
            Vector3f projected = viewer.projectToWidget(positions[i]);

            if (projected == null) continue;

            float pixels = (float) Math.hypot(projected.x - x, projected.y - y);

            if (pixels > PICK_PIXELS) continue;

            boolean closer = pixels < nearestPixels - TIE_PIXELS;
            boolean tiedInFront = pixels <= nearestPixels + TIE_PIXELS && projected.z < nearestDepth;

            if (!closer && !tiedInFront) continue;

            nearest = i;
            nearestPixels = pixels;
            nearestDepth = projected.z;
        }

        return nearest;
    }

    /// `pairs` without every pair touching `a` or `b`.
    private static int[] withoutPairsOf(final int[] pairs, final int a, final int b) {
        IntArrayList kept = new IntArrayList(pairs.length);

        for (int k = 0; k + 1 < pairs.length; k += 2) {
            if (pairs[k] == a || pairs[k] == b || pairs[k + 1] == a || pairs[k + 1] == b) continue;

            kept.add(pairs[k]);
            kept.add(pairs[k + 1]);
        }

        return kept.toIntArray();
    }

    /// The best offset vertex one root found: `aside` is its distance from the line through the root,
    /// `along` how far down that line it sits.
    private record Candidate(int root, int offset, float aside, float along) {
    }
}
