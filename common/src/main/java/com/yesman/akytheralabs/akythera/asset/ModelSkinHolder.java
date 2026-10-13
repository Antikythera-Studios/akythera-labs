package com.yesman.akytheralabs.akythera.asset;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.armature.ArmatureManager;
import com.yesman.akythera.client.api.gui.viewer.Ray;
import com.yesman.akythera.client.core.asset.loader.MeshLoader;
import com.yesman.akythera.client.core.asset.loader.VertexBuilder;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.core.asset.mesh.VanillaModelBaker;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.InMemoryAssetAccessor;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.viewer.ClothMeshGeometry;
import com.yesman.akytheralabs.gui.widget.viewer.WeightPaintComponent;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// A vanilla model being skinned onto an armature in the Model Skinning screen: what its Model Information
/// panel picks, the mesh those picks make, and the bone weights painted on it.
///
/// Picking a model or an armature rebuilds the mesh. Saved nowhere; the screen exports [#toBuilder].
public final class ModelSkinHolder {
    private static final Identifier MESH_ID = AkytheraLabs.identifier("model_skinning/mesh");
    private static final Identifier BAKED_ARMATURE_ID = AkytheraLabs.identifier("model_skinning/baked_armature");

    /// Distance from a slice under which a vertex sits on it, in blocks.
    private static final float SLICE_TOLERANCE = 1.0E-5F;

    /// Share of an edge its longest component holds for the edge to follow that axis.
    private static final float AXIS_ALIGNED = 0.999F;

    /// The rows a slice makes of each point it cuts, in the order they are made.
    private static final int ROW_POSITIVE = 0; // joined to the triangles past the slice
    private static final int ROW_MIDDLE = 1;
    private static final int ROW_NEGATIVE = 2; // joined to the triangles before the slice

    /// A [ModelLayerLocation] of the entity model set, or a [VanillaModelBaker.Bake] the game keeps.
    @EditorEditable(
        label = LangKeys.GUI_MODEL_SKINNING_SCREEN_WIDGET_MODEL, order = 0,
        candidates = "models", candidateLabel = "modelLabel", candidateCategory = "modelCategory",
        searchable = true, rebuildOnWrite = true
    )
    private @Nullable Object model;

    @EditorEditable(label = LangKeys.GUI_MODEL_SKINNING_SCREEN_WIDGET_ARMATURE, order = 1, rebuildOnWrite = true)
    private AssetAccessor<? extends Armature> armature = ArmatureManager.EMPTY_ARMATURE_ACCESSOR;

    /// What [WeightPaintComponent.ViewMode#TEXTURED] draws the mesh with.
    @EditorEditable(label = LangKeys.GUI_MODEL_SKINNING_SCREEN_WIDGET_TEXTURE, order = 2)
    private ClientAsset.@Nullable Texture texture;

    /// The bone whose weights the brush paints and the view shows.
    private @Nullable Bone paintedBone;

    /// What the Details panel lists.
    private final PickedVertex pickedVertex = new PickedVertex();

    /// The model's vertices and triangles. Null while there is no mesh.
    private SkeletalMesh.@Nullable SkeletalMeshBuilder geometry;

    /// The bones deforming each mesh position, by position index.
    private final List<List<Influence>> weights = new ArrayList<>();

    /// [#geometry] as the viewer draws it. Carries the weights it was built with, not the painted ones.
    private @Nullable SkeletalMesh mesh;

    /// Reads [#mesh] as it stands.
    private final AssetAccessor<SkeletalMesh> meshAccessor = new AssetAccessor<>() {
        @Override
        public @Nullable SkeletalMesh get() {
            return mesh;
        }

        @Override
        public Type type() {
            return Type.SKELETAL_MESH;
        }

        @Override
        public Identifier identifier() {
            return MESH_ID;
        }

        @Override
        public boolean isResourcePresent() {
            return true;
        }
    };

    // ***************************************************************
    // Model Information rows
    // ***************************************************************

    /// Takes a model pick. A bake brings the armature it was baked on and the weights it was baked with.
    public void setModel(final @Nullable Object model) {
        this.model = model;

        if (model instanceof VanillaModelBaker.Bake bake) {
            armature = InMemoryAssetAccessor.armature(BAKED_ARMATURE_ID, bake.armature());
            paintedBone = null;
        }

        rebuild(null);
    }

    public AssetAccessor<? extends Armature> getArmature() {
        return armature;
    }

    public void setArmature(final AssetAccessor<? extends Armature> armature) {
        this.armature = armature;
        paintedBone = null;
        rebuild(null);
    }

    public void setTexture(final ClientAsset.@Nullable Texture texture) {
        this.texture = texture;

        if (mesh != null) remesh();
    }

    public @Nullable Bone getPaintedBone() {
        return paintedBone;
    }

    public void setPaintedBone(final @Nullable Bone paintedBone) {
        this.paintedBone = paintedBone;
    }

    /// Every model layer the entity model set holds by name, then every bake the game keeps.
    private List<Object> models() {
        List<Object> models = new ArrayList<>(
            Minecraft.getInstance().getModelManager().entityModels().get().roots.keySet().stream()
                .sorted(Comparator.comparing(ModelLayerLocation::toString))
                .toList()
        );

        models.addAll(VanillaModelBaker.bakes());

        return models;
    }

    private Component modelLabel(final Object model) {
        return model instanceof VanillaModelBaker.Bake bake
            ? Component.empty().append(bake.item().getDefaultInstance().getHoverName()).append(" (" + bake.slot().getName() + ")")
            : Component.literal(model.toString());
    }

    private Component modelCategory(final Object model) {
        return Component.translatable(
            model instanceof VanillaModelBaker.Bake
                ? LangKeys.GUI_MODEL_SKINNING_SCREEN_MODEL_CATEGORY_BAKED
                : LangKeys.GUI_MODEL_SKINNING_SCREEN_MODEL_CATEGORY_VANILLA
        );
    }

    /// The bones of [#armature] by id, empty while it resolves to none.
    public List<Bone> getBones() {
        Armature resolved = armature.getOrHandleException(AkytheraLabs.LOGGER::error);
        List<Bone> bones = new ArrayList<>();

        for (int id = 0; resolved != null && id < resolved.getBoneCount(); id++) {
            Bone bone = resolved.getBoneById(id);

            if (bone != null) bones.add(bone);
        }

        return bones;
    }

    // ***************************************************************
    // Mesh
    // ***************************************************************

    public AssetAccessor<SkeletalMesh> getMesh() {
        return meshAccessor;
    }

    public boolean hasMesh() {
        return mesh != null;
    }

    /// Rebuilds the mesh from [#model] on [#armature], weighted by `reference` the way [VanillaModelBaker]
    /// bakes a worn model over a body. Without a `reference` a bake keeps its own weights and a model layer
    /// has no vertex weighted. Leaves no mesh while either pick is missing.
    public void rebuild(final @Nullable SkeletalMesh reference) {
        Armature resolved = armature.getOrHandleException(AkytheraLabs.LOGGER::error);

        close();
        geometry = null;
        weights.clear();
        pickVertex(-1);

        if (model == null || resolved == null) return;

        try {
            if (model instanceof VanillaModelBaker.Bake bake) {
                bake.model().resetPose();
                geometry = reference == null ? bake.builder() : VanillaModelBaker.build(bake.model().root(), resolved, reference);
            } else {
                geometry = VanillaModelBaker.build(
                    Minecraft.getInstance().getModelManager().entityModels().get().bakeLayer((ModelLayerLocation) model),
                    resolved,
                    reference
                );
            }
        } catch (RuntimeException e) {
            AkytheraLabs.LOGGER.error("Failed to build the model {}", model, e);
        }

        if (geometry == null) return;

        for (int position = 0; position < geometry.positions.length / 3; position++) {
            List<Influence> influences = new ArrayList<>();
            int from = geometry.deformationFromToArray[position * 2];
            int to = geometry.deformationFromToArray[position * 2 + 1];

            for (int entry = from; entry < to; entry++) {
                Bone bone = resolved.getBoneById(geometry.affectingBoneIdArray[entry]);

                if (bone != null) influences.add(new Influence(bone, geometry.weights[geometry.affectingWeightIndexArray[entry]]));
            }

            weights.add(influences);
        }

        remesh();
    }

    /// Replaces [#mesh] with one built from [#toBuilder].
    private void remesh() {
        SkeletalMesh previous = mesh;

        mesh = new SkeletalMesh(toBuilder());

        if (previous != null) previous.close();
    }

    /// The mesh as it is painted, in the space and the layout a mesh loader hands over. Framed by its own
    /// bounds and textured with [#texture].
    public SkeletalMesh.SkeletalMeshBuilder toBuilder() {
        SkeletalMesh.SkeletalMeshBuilder builder = SkeletalMesh.builder();
        FloatArrayList weightValues = new FloatArrayList();
        IntArrayList fromTo = new IntArrayList();
        IntArrayList boneIds = new IntArrayList();
        IntArrayList weightIds = new IntArrayList();
        float width = 0.0F;
        float height = 0.0F;

        for (int i = 0; i + 2 < geometry.positions.length; i += 3) {
            width = Math.max(width, 2.0F * Math.max(Math.abs(geometry.positions[i]), Math.abs(geometry.positions[i + 2])));
            height = Math.max(height, geometry.positions[i + 1]);
        }

        for (List<Influence> influences : weights) {
            fromTo.add(boneIds.size());

            for (Influence influence : influences) {
                if (influence.bone == null || influence.weight <= 0.0F) continue;

                boneIds.add(influence.bone.getId());
                weightIds.add(weightValues.size());
                weightValues.add(influence.weight);
            }

            fromTo.add(boneIds.size());
        }

        builder.weights(weightValues.toFloatArray())
            .deformationFromToArray(fromTo.toIntArray())
            .affectingBoneIdArray(boneIds.toIntArray())
            .affectingWeightIndexArray(weightIds.toIntArray());
        builder.positions(geometry.positions)
            .normals(geometry.normals)
            .uvs(geometry.uvs)
            .metadata(new MeshLoader.Metadata(width, height, texture == null ? null : texture.texturePath()));
        builder.partBuilders.putAll(geometry.partBuilders);

        return builder;
    }

    /// The name an export of [#model] is written under.
    public String exportName() {
        String name = switch (model) {
            case ModelLayerLocation location -> location.model().getPath() + "_" + location.layer();
            case VanillaModelBaker.Bake bake -> BuiltInRegistries.ITEM.getKey(bake.item()).getPath();
            case null, default -> "";
        };

        return name.replaceAll("[^a-z0-9_.-]", "_");
    }

    public void close() {
        if (mesh != null) mesh.close();

        mesh = null;
    }

    // ***************************************************************
    // Weights
    // ***************************************************************

    /// What the Details panel shows: the vertex picked last.
    public PickedVertex getPickedVertex() {
        return pickedVertex;
    }

    /// Lists the weights of the mesh position `vertex` in the Details panel, nothing for an index the mesh lacks.
    public void pickVertex(final int vertex) {
        pickedVertex.influences = vertex >= 0 && vertex < weights.size() ? weights.get(vertex) : new ArrayList<>();
    }

    /// `bind` moved by `boneTransforms`, the skinning matrix of each bone by id, under the weights as they
    /// are painted. A position no bone weighs stays where it is.
    public Vector3f[] pose(final Vector3f[] bind, final Matrix4f[] boneTransforms) {
        Vector3f[] posed = new Vector3f[bind.length];
        Vector3f moved = new Vector3f();

        for (int position = 0; position < bind.length; position++) {
            Vector3f sum = new Vector3f();
            float total = 0.0F;

            for (Influence influence : position < weights.size() ? weights.get(position) : List.<Influence>of()) {
                if (influence.bone == null || influence.weight <= 0.0F || influence.bone.getId() >= boneTransforms.length) continue;

                sum.add(boneTransforms[influence.bone.getId()].transformPosition(bind[position], moved).mul(influence.weight));
                total += influence.weight;
            }

            posed[position] = total > 0.0F ? sum : bind[position];
        }

        return posed;
    }

    /// The weight [#paintedBone] holds on each mesh position, by position index.
    public float[] paintedWeights() {
        float[] painted = new float[weights.size()];

        for (int position = 0; paintedBone != null && position < painted.length; position++) {
            for (Influence influence : weights.get(position)) {
                if (paintedBone.equals(influence.bone)) painted[position] += influence.weight;
            }
        }

        return painted;
    }

    /// Gives [#paintedBone] the weight `painted` holds for each mesh position it differs from `before` on,
    /// and scales the position's other bones so its weights add up to 1.
    public void paint(final float[] before, final float[] painted) {
        if (paintedBone == null) return;

        for (int position = 0; position < weights.size() && position < painted.length; position++) {
            if (before[position] == painted[position]) continue;

            List<Influence> influences = weights.get(position);
            float weight = Math.clamp(painted[position], 0.0F, 1.0F);
            float others = 0.0F;

            influences.removeIf(influence -> paintedBone.equals(influence.bone));

            for (Influence influence : influences) {
                others += influence.weight;
            }

            for (Influence influence : influences) {
                if (others > 0.0F) influence.weight *= (1.0F - weight) / others;
            }

            if (weight > 0.0F) influences.add(new Influence(paintedBone, weight));
        }
    }

    /// A copy of every weight, for [#restoreWeights].
    public List<List<Influence>> copyWeights() {
        List<List<Influence>> copy = new ArrayList<>(weights.size());

        for (List<Influence> influences : weights) {
            List<Influence> copied = new ArrayList<>(influences.size());

            for (Influence influence : influences) {
                copied.add(new Influence(influence.bone, influence.weight));
            }

            copy.add(copied);
        }

        return copy;
    }

    /// Puts back the weights `copy` holds. Ignored once the mesh they were copied from is gone.
    public void restoreWeights(final List<List<Influence>> copy) {
        if (copy.size() != weights.size()) return;

        for (int position = 0; position < weights.size(); position++) {
            List<Influence> influences = weights.get(position);

            influences.clear();

            for (Influence influence : copy.get(position)) {
                influences.add(new Influence(influence.bone, influence.weight));
            }
        }
    }

    // ***************************************************************
    // Bending
    // ***************************************************************

    /// Where `ray` would slice the mesh: through the point it hits, across the edge of the hit triangle
    /// nearest that point, the way a loop cut runs across the edge under the cursor. Only an edge that
    /// follows an axis counts; a triangle with none is sliced across the longest axis of its part. Null for
    /// a miss.
    public @Nullable Slice sliceAt(final Ray ray) {
        if (geometry == null) return null;

        float nearest = Float.MAX_VALUE;
        String hitPart = null;
        Vector3f[] hitCorners = new Vector3f[3];

        for (Map.Entry<String, List<VertexBuilder>> part : geometry.partBuilders.entrySet()) {
            List<VertexBuilder> vertices = part.getValue();

            for (int t = 0; t + 2 < vertices.size(); t += 3) {
                Vector3f a = positionOf(vertices.get(t));
                Vector3f b = positionOf(vertices.get(t + 1));
                Vector3f c = positionOf(vertices.get(t + 2));
                float distance = ClothMeshGeometry.rayTriangle(ray, a, b, c);

                if (distance >= 0.0F && distance < nearest) {
                    nearest = distance;
                    hitPart = part.getKey();
                    hitCorners[0] = a;
                    hitCorners[1] = b;
                    hitCorners[2] = c;
                }
            }
        }

        if (hitPart == null) return null;

        Vector3f hit = ray.at(nearest);
        int edgeAxis = -1;
        float edgeDistance = Float.MAX_VALUE;

        for (int corner = 0; corner < 3; corner++) {
            Vector3f from = hitCorners[corner];
            Vector3f edge = hitCorners[(corner + 1) % 3].sub(from, new Vector3f());
            float length = edge.length();
            int along = edge.maxComponent();

            if (length < SLICE_TOLERANCE || Math.abs(edge.get(along)) < length * AXIS_ALIGNED) continue;

            float share = Math.clamp(hit.sub(from, new Vector3f()).dot(edge) / (length * length), 0.0F, 1.0F);
            float distance = from.fma(share, edge, new Vector3f()).distance(hit);

            if (distance < edgeDistance) {
                edgeDistance = distance;
                edgeAxis = along;
            }
        }

        Vector3f min = new Vector3f(Float.MAX_VALUE);
        Vector3f max = new Vector3f(-Float.MAX_VALUE);

        for (VertexBuilder vertex : geometry.partBuilders.get(hitPart)) {
            Vector3f position = positionOf(vertex);

            min.min(position);
            max.max(position);
        }

        int axis = edgeAxis < 0 ? max.sub(min, new Vector3f()).maxComponent() : edgeAxis;

        return new Slice(hitPart, axis, hit.get(axis), min, max);
    }

    /// The position, the normal and the UV `vertex` draws with: eight elements.
    private float[] dataOf(final VertexBuilder vertex) {
        return new float[] {
            geometry.positions[vertex.position() * 3],
            geometry.positions[vertex.position() * 3 + 1],
            geometry.positions[vertex.position() * 3 + 2],
            geometry.normals[vertex.normal() * 3],
            geometry.normals[vertex.normal() * 3 + 1],
            geometry.normals[vertex.normal() * 3 + 2],
            geometry.uvs[vertex.uv() * 2],
            geometry.uvs[vertex.uv() * 2 + 1]
        };
    }

    private Vector3f positionOf(final VertexBuilder vertex) {
        int at = vertex.position() * 3;

        return new Vector3f(geometry.positions[at], geometry.positions[at + 1], geometry.positions[at + 2]);
    }

    /// Cuts the part of `slice` along it into three rows of vertices sharing one place, joined by triangles
    /// with no area that open when the rows part, and weights those rows for a joint there. Every other
    /// vertex keeps its weights.
    ///
    /// The row on the side of the slice the root bone's head lies on follows the root bone, the row on the
    /// other side the bending bone. The middle row follows the inflation bone on its
    /// [BendingSettings#getDirection] half and the bending bone on the other. A bone left unpicked leaves
    /// its vertices unweighted.
    ///
    /// Front and behind are read along Z, or along Y for a part sliced across Z.
    public void addBending(final Slice slice, final BendingSettings settings) {
        if (geometry == null) return;

        int axis = slice.axis();
        FloatArrayList positions = new FloatArrayList(geometry.positions);
        FloatArrayList normals = new FloatArrayList(geometry.normals);
        FloatArrayList uvs = new FloatArrayList(geometry.uvs);
        List<VertexBuilder> source = geometry.partBuilders.get(slice.part());
        List<VertexBuilder> rebuilt = new ArrayList<>();
        // The three row vertices made of one cut point, by the point's position, normal and UV
        Map<String, int[]> rows = new HashMap<>();
        Int2IntOpenHashMap rowOf = new Int2IntOpenHashMap();
        Set<String> joined = new HashSet<>();

        rowOf.defaultReturnValue(-1);

        for (int t = 0; t + 2 < source.size(); t += 3) {
            // One face: a triangle, or the quad it makes with the next triangle. A quad is cut whole, so
            // each piece is a quad again and no diagonal is left crossing the slice.
            List<VertexBuilder> face = new ArrayList<>(source.subList(t, t + 3));

            if (t + 5 < source.size()) {
                int alone = -1;
                int opposite = -1;
                int shared = 0;

                for (int corner = 0; corner < 3; corner++) {
                    float[] own = dataOf(source.get(t + corner));
                    float[] next = dataOf(source.get(t + 3 + corner));
                    boolean ownShared = false;
                    boolean nextShared = false;

                    for (int other = 0; other < 3; other++) {
                        ownShared |= Arrays.equals(own, dataOf(source.get(t + 3 + other)));
                        nextShared |= Arrays.equals(next, dataOf(source.get(t + other)));
                    }

                    if (!ownShared) alone = corner;
                    if (!nextShared) opposite = corner;
                    if (ownShared) shared++;
                }

                if (shared == 2 && alone >= 0 && opposite >= 0) {
                    Vector3f a = positionOf(source.get(t + alone));
                    Vector3f b = positionOf(source.get(t + (alone + 1) % 3));
                    Vector3f c = positionOf(source.get(t + (alone + 2) % 3));
                    Vector3f d = positionOf(source.get(t + 3 + opposite));
                    Vector3f normal = b.sub(a, new Vector3f()).cross(c.sub(a, new Vector3f()));
                    float diagonal = b.distanceSquared(c);

                    // The two lie flat on one face and share their longest side: the diagonal of a quad
                    if (normal.lengthSquared() > 0.0F
                        && Math.abs(d.sub(a, new Vector3f()).dot(normal.normalize())) <= SLICE_TOLERANCE
                        && diagonal >= a.distanceSquared(b) && diagonal >= a.distanceSquared(c)
                        && diagonal >= d.distanceSquared(b) && diagonal >= d.distanceSquared(c)
                    ) {
                        face = List.of(
                            source.get(t + alone), source.get(t + (alone + 1) % 3),
                            source.get(t + 3 + opposite), source.get(t + (alone + 2) % 3)
                        );
                        t += 3;
                    }
                }
            }

            float[][] corners = new float[face.size()][];
            List<float[]> positive = new ArrayList<>();
            List<float[]> negative = new ArrayList<>();

            for (int corner = 0; corner < corners.length; corner++) {
                corners[corner] = dataOf(face.get(corner));
            }

            for (int corner = 0; corner < corners.length; corner++) {
                float[] from = corners[corner];
                float[] to = corners[(corner + 1) % corners.length];
                float fromOffset = from[axis] - slice.coordinate();
                float toOffset = to[axis] - slice.coordinate();

                if (fromOffset >= -SLICE_TOLERANCE) positive.add(from);
                if (fromOffset <= SLICE_TOLERANCE) negative.add(from);

                if (fromOffset > SLICE_TOLERANCE && toOffset < -SLICE_TOLERANCE
                    || fromOffset < -SLICE_TOLERANCE && toOffset > SLICE_TOLERANCE
                ) {
                    // Cut from the same end whichever triangle walks the edge, so both make one point
                    float[] first = Arrays.compare(from, to) < 0 ? from : to;
                    float[] second = first == from ? to : from;
                    float share = (slice.coordinate() - first[axis]) / (second[axis] - first[axis]);
                    float[] cut = new float[8];

                    for (int element = 0; element < 8; element++) {
                        cut[element] = first[element] + (second[element] - first[element]) * share;
                    }

                    cut[axis] = slice.coordinate();
                    positive.add(cut);
                    negative.add(cut);
                }
            }

            for (int row : new int[] {ROW_POSITIVE, ROW_NEGATIVE}) {
                List<VertexBuilder> polygon = new ArrayList<>();
                List<int[]> onSlice = new ArrayList<>();
                boolean offSlice = false;

                for (float[] point : row == ROW_POSITIVE ? positive : negative) {
                    if (Math.abs(point[axis] - slice.coordinate()) > SLICE_TOLERANCE) {
                        offSlice = true;

                        for (int corner = 0; corner < corners.length; corner++) {
                            if (corners[corner] == point) polygon.add(face.get(corner));
                        }

                        continue;
                    }

                    int[] made = rows.computeIfAbsent(Arrays.toString(point), _ -> {
                        int[] indices = new int[3];

                        for (int copy = ROW_POSITIVE; copy <= ROW_NEGATIVE; copy++) {
                            indices[copy] = positions.size() / 3;
                            rowOf.put(indices[copy], copy);
                            positions.addElements(positions.size(), point, 0, 3);
                            normals.addElements(normals.size(), point, 3, 3);
                            uvs.addElements(uvs.size(), point, 6, 2);
                            weights.add(new ArrayList<>());
                        }

                        return indices;
                    });

                    onSlice.add(made);
                    polygon.add(new VertexBuilder(made[row], made[row], made[row]));
                }

                if (!offSlice || polygon.size() < 3) continue;

                for (int fan = 1; fan + 1 < polygon.size(); fan++) {
                    rebuilt.add(polygon.get(0));
                    rebuilt.add(polygon.get(fan));
                    rebuilt.add(polygon.get(fan + 1));
                }

                if (onSlice.size() != 2) continue;

                int[] a = onSlice.get(0);
                int[] b = onSlice.get(1);

                // One strip of triangles with no area per cut segment, from the positive row down to the negative one
                if (!joined.add(Math.min(a[ROW_MIDDLE], b[ROW_MIDDLE]) + ":" + Math.max(a[ROW_MIDDLE], b[ROW_MIDDLE]))) continue;

                for (int upper = ROW_POSITIVE; upper < ROW_NEGATIVE; upper++) {
                    for (int index : new int[] {a[upper], b[upper], b[upper + 1], a[upper], b[upper + 1], a[upper + 1]}) {
                        rebuilt.add(new VertexBuilder(index, index, index));
                    }
                }
            }
        }

        SkeletalMesh.SkeletalMeshBuilder sliced = SkeletalMesh.builder();

        sliced.positions(positions.toFloatArray()).normals(normals.toFloatArray()).uvs(uvs.toFloatArray());
        sliced.partBuilders.putAll(geometry.partBuilders);
        sliced.partBuilders.put(slice.part(), rebuilt);
        geometry = sliced;

        Bone rootBone = settings.getRootBone();
        Bone bendingBone = settings.getBendingBone();
        Vector3f rootHead = new Vector3f();
        Vector3f bendingHead = new Vector3f();

        if (rootBone != null) rootBone.getInverseTransformToOrigin().invert(new Matrix4f()).getTranslation(rootHead);
        if (bendingBone != null) bendingBone.getInverseTransformToOrigin().invert(new Matrix4f()).getTranslation(bendingHead);

        boolean rootIsPositive = rootHead.get(axis) >= bendingHead.get(axis);
        int facingAxis = axis == 2 ? 1 : 2;
        float facingCenter = (slice.min().get(facingAxis) + slice.max().get(facingAxis)) * 0.5F;

        for (VertexBuilder vertex : rebuilt) {
            int position = vertex.position();
            int row = rowOf.get(position);

            if (row < 0) continue;

            boolean facing = settings.getDirection() == BendingSettings.Direction.FRONT
                ? geometry.positions[position * 3 + facingAxis] < facingCenter
                : geometry.positions[position * 3 + facingAxis] > facingCenter;
            Bone bone = row == ROW_MIDDLE
                ? facing ? settings.getInflationBone() : bendingBone
                : (row == ROW_POSITIVE) == rootIsPositive ? rootBone : bendingBone;

            if (bone == null) continue;

            weights.get(position).clear();
            weights.get(position).add(new Influence(bone, 1.0F));
        }

        pickVertex(-1);
        remesh();
    }

    /// A plane a part can be cut along.
    ///
    /// @param part       the name of the mesh part
    /// @param axis       the axis the plane stands across: 0 for X, 1 for Y, 2 for Z
    /// @param coordinate where the plane sits on that axis
    /// @param min        the least coordinate the part's vertices reach, per axis
    /// @param max        the greatest coordinate the part's vertices reach, per axis
    public record Slice(String part, int axis, float coordinate, Vector3f min, Vector3f max) {
    }

    /// The vertex the Details panel lists.
    public static final class PickedVertex {
        /// The [#weights] entry of the vertex; a list of its own while none is picked.
        @EditorEditable(
            label = LangKeys.GUI_MODEL_SKINNING_SCREEN_WIDGET_VERTEX_WEIGHTS, order = 0, defaultElement = "newInfluence"
        )
        private List<Influence> influences = new ArrayList<>();

        private static Influence newInfluence() {
            return new Influence(null, 0.0F);
        }
    }

    /// One bone deforming a vertex.
    public static final class Influence {
        @EditorEditable(
            label = LangKeys.GUI_MODEL_SKINNING_SCREEN_WIDGET_BONE, order = 0,
            candidates = "bones", candidateLabel = "boneLabel", searchable = true
        )
        private @Nullable Bone bone;

        @EditorEditable(label = LangKeys.GUI_MODEL_SKINNING_SCREEN_WIDGET_WEIGHT, order = 1, min = 0.0F, max = 1.0F)
        private float weight;

        private Influence(final @Nullable Bone bone, final float weight) {
            this.bone = bone;
            this.weight = weight;
        }

        private static List<Bone> bones(final ModelSkinHolder holder) {
            return holder.getBones();
        }

        private static Component boneLabel(final Bone bone) {
            return Component.literal(bone.getName());
        }
    }
}
