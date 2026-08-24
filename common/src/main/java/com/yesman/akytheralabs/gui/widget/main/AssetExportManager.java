package com.yesman.akytheralabs.gui.widget.main;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.client.core.asset.loader.MeshLoader;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.keyframe.BoneAnimationTrack;
import com.yesman.akythera.core.animation.keyframe.Keyframe;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.loader.ArmatureLoader;
import com.yesman.akythera.core.asset.loader.AssetLoader;
import com.yesman.akythera.core.asset.loader.binary.BinaryAssetLoader;
import com.yesman.akythera.core.asset.loader.binary.BinaryAssetLoader.DataType;
import com.yesman.akythera.core.asset.loader.json.JsonAssetLoader;
import com.yesman.akythera.core.asset.loader.json.JsonAssetLoader.TransformFormat;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.FileSystemHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.Deflater;

/// Writes `.akasset` files: retargeted animation clips for [AnimationRetargetModal], and the mesh /
/// armature halves of a legacy `.json` model for [ModelTransformerModal]. Also owns the retargeting
/// algorithm and its template storage.
public final class AssetExportManager {
    /// Type folders the two halves of a model land in, side by side under the chosen directory. The
    /// folder name is what tells a mesh from an armature — both files keep the source model's stem,
    /// which is the whole point of splitting a legacy `.json` across two of them.
    private static final String MESH_DIRECTORY = "meshes";
    private static final String ARMATURE_DIRECTORY = "armatures";

    public static final AssetExportManager INSTANCE = new AssetExportManager();

    private AssetExportManager() {}

    /// Relabel `source`'s bone tracks by `template`. Unmapped clip bones are dropped from
    /// the output (target armature bones missing a track fall back to `Pose.orElseIdentity`
    /// at render time). Tracks are reused as-is — `BoneAnimationTrack` is immutable.
    ///
    /// Premise: `JsonAnimationLoader` already unbound the *source* armature's bind
    /// transforms while baking, so keyframes are bind-pose-relative and reusable across rigs.
    public AnimationClip remap(AnimationClip source, MappingTemplate template) {
        AnimationClip.Builder builder = AnimationClip.builder();

        source.visitBoneTracks((clipBone, track) -> {
            String targetBone = template.getTarget(clipBone);
            if (targetBone == null || targetBone.isEmpty()) return;
            builder.addBoneAnimation(targetBone, track);
        });

        return builder.build();
    }

    /// Decode every `.json` file under `dir` as a `MappingTemplate`. Files that fail to
    /// parse are logged and skipped — a single bad template doesn't kill the listing.
    public List<TemplateEntry> loadTemplates(Path dir) {
        if (!Files.isDirectory(dir)) return List.of();

        List<TemplateEntry> out = new ArrayList<>();

        FileSystemHelper.forEachFile(dir, p -> p.toString().endsWith(".json"), file -> {
            try (BufferedReader reader = Files.newBufferedReader(file)) {
                JsonElement json = JsonParser.parseReader(reader);
                MappingTemplate t = MappingTemplate.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
                out.add(new TemplateEntry(file, t));
            } catch (Exception e) {
                AkytheraLabs.LOGGER.warn("Failed to decode retarget template {}", file, e);
            }
        });
        return out;
    }

    /// Encode `template` to JSON and write at `file`. Empty-value mappings are dropped so
    /// the saved file never carries `"BoneA": ""` rows — those are the runtime "unmapped"
    /// state and would just clutter the disk artifact.
    public boolean saveTemplate(Path file, MappingTemplate template) {
        Identifier armId = template.armatureId();

        if (armId == null) {
            AkytheraLabs.LOGGER.error("Refused to save template with null armatureId: {}", file);
            return false;
        }

        MappingTemplate cleaned = new MappingTemplate(armId);

        for (Map.Entry<String, String> e : template.mapping().entrySet()) {
            if (e.getValue() != null && !e.getValue().isEmpty()) {
                cleaned.putMapping(e.getKey(), e.getValue());
            }
        }

        return FileSystemHelper.writeJson(file, MappingTemplate.CODEC, cleaned);
    }

    /// Iterate `entries`, remap each via `template`, optionally synthesise a root-bone
    /// track, encode as `.akasset` and write into `destDir`. One-bad-file-doesn't-kill-
    /// the-batch: each per-entry failure becomes an [ExportResult.Failure]; other entries
    /// still export.
    public List<ExportResult<RetargetEntry>> exportAll(
        List<RetargetEntry> entries,
        Armature sourceArmature,
        Armature destArmature,
        Path destDir,
        boolean generateRootBone
    ) {
        String sourceRootName = sourceArmature.getRootBone().getName();
        String destRootName = destArmature.getRootBone().getName();

        List<ExportResult<RetargetEntry>> results = new ArrayList<>(entries.size());

        for (RetargetEntry e : entries) {
            try {
                AnimationClip remapped = remap(e.clip, e.template);

                if (generateRootBone) {
                    String topBone = e.template.getTarget(sourceRootName);
                    if (topBone != null && !topBone.isEmpty()) {
                        remapped = generateRootBone(remapped, topBone, destRootName);
                    }
                }

                AkAssetWriter writer = new AkAssetWriter();
                remapped.visitBoneTracks(writer::addAnimBoneTrack);

                Files.createDirectories(destDir);
                Path dest = destDir.resolve(FileSystemHelper.stem(e.source.getFileName().toString()) + "." + AssetLoader.BINARY_EXTENSION);
                Files.write(dest, writer.toBytes());

                results.add(new ExportResult.Success<>(e, List.of(dest)));
            } catch (Exception ex) {
                AkytheraLabs.LOGGER.error("Failed to export {}", e.label(), ex);
                results.add(new ExportResult.Failure<>(e, ex.getMessage()));
            }
        }

        return results;
    }

    /// Synthesise a new root-bone track from `topBone`'s keyframes: XZ verbatim,
    /// Y clamped at 0 (root never sinks). The top bone keeps a Y residue
    /// (`y - max(0, y)`) so the world-position invariant holds.
    public AnimationClip generateRootBone(AnimationClip remapped, String topBone, String rootBoneName) {
        BoneAnimationTrack top = remapped.getBoneTransform(topBone);
        if (top == null) return remapped;

        List<Keyframe> rootKfs = new ArrayList<>(top.length());
        List<Keyframe> residualKfs = new ArrayList<>(top.length());

        for (Keyframe kf : top.keyframes()) {
            Transform t = kf.transform();
            double rootY = Math.max(0.0, t.loc().y);

            Transform rootT = new Transform(
                new Vec3(t.loc().x, rootY, t.loc().z),
                new Quaterniond(),
                new Vec3(1.0, 1.0, 1.0)
            );
            Transform residualT = new Transform(
                new Vec3(0.0, t.loc().y - rootY, 0.0),
                t.rot(),
                t.sca()
            );

            rootKfs.add(new Keyframe(kf.timeStamp(), rootT));
            residualKfs.add(new Keyframe(kf.timeStamp(), residualT));
        }

        AnimationClip.Builder b = AnimationClip.builder();
        remapped.visitBoneTracks((bone, track) -> {
            if (bone.equals(topBone)) {
                b.addBoneAnimation(topBone, new BoneAnimationTrack(residualKfs));
            } else {
                b.addBoneAnimation(bone, track);
            }
        });
        b.addBoneAnimation(rootBoneName, new BoneAnimationTrack(rootKfs));

        return b.build();
    }

    // ***************************************************************
    // Legacy .json model -> mesh + armature .akasset
    // ***************************************************************

    /// One model queued for export: the legacy `.json` to read, and the bone renames to apply on the
    /// way out, keyed by the name that file carries.
    public record ModelExport(Path source, Map<String, String> boneRenames) {}

    /// Transcode each legacy `.json` model into a mesh and an armature `.akasset` under `destDir`, in
    /// [#MESH_DIRECTORY] and [#ARMATURE_DIRECTORY]. Both keep the source file's stem.
    /// Per-file failures become an [ExportResult.Failure]; the rest of the batch still exports.
    public List<ExportResult<Path>> exportAllModels(
        List<ModelExport> models,
        Path destDir,
        boolean generateRootInOrigin
    ) {
        Path meshDir = destDir.resolve(MESH_DIRECTORY);
        Path armatureDir = destDir.resolve(ARMATURE_DIRECTORY);

        List<ExportResult<Path>> results = new ArrayList<>(models.size());

        for (ModelExport model : models) {
            Path source = model.source();

            try {
                JsonObject root = readJsonObject(source);
                String stem = FileSystemHelper.stem(source.getFileName().toString());

                Files.createDirectories(meshDir);
                Files.createDirectories(armatureDir);

                Path meshFile = meshDir.resolve(stem + "." + AssetLoader.BINARY_EXTENSION);
                Path armatureFile = armatureDir.resolve(stem + "." + AssetLoader.BINARY_EXTENSION);

                Files.write(meshFile, encodeMesh(root));
                Files.write(armatureFile, encodeArmature(root, model.boneRenames(), generateRootInOrigin));

                results.add(new ExportResult.Success<>(source, List.of(meshFile, armatureFile)));
            } catch (Exception e) {
                AkytheraLabs.LOGGER.error("Failed to export model {}", source, e);
                results.add(new ExportResult.Failure<>(source, e.getMessage()));
            }
        }

        return results;
    }

    /// Encodes the `vertices` half of a legacy model as a skeletal-mesh `.akasset` stream.
    ///
    /// The arrays are copied verbatim: `BinaryMeshLoader` runs the same Z-up to Y-up conversion
    /// `JsonMeshLoader` does, so a converted export would land rotated.
    public byte[] encodeMesh(JsonObject root) {
        JsonObject vertices = GsonHelper.getAsJsonObject(root, "vertices");
        AkAssetWriter writer = new AkAssetWriter();

        writer.addFloatArray(DataType.MESH_POSITIONS, floatArrayOf(vertices, "positions"));
        writer.addFloatArray(DataType.MESH_UVS, floatArrayOf(vertices, "uvs"));
        writer.addFloatArray(DataType.MESH_NORMALS, floatArrayOf(vertices, "normals"));
        writer.addIntArray(DataType.MESH_BONE_COUNTS, intArrayOf(vertices, "vcounts"));
        writer.addIntArray(DataType.MESH_BONE_WEIGHT_INDICES, intArrayOf(vertices, "vindices"));
        writer.addFloatArray(DataType.MESH_WEIGHTS, floatArrayOf(vertices, "weights"));

        if (vertices.has("parts")) {
            for (Map.Entry<String, JsonElement> part : GsonHelper.getAsJsonObject(vertices, "parts").entrySet()) {
                JsonObject partObject = GsonHelper.convertToJsonObject(part.getValue(), "part");
                writer.addSubmesh(part.getKey(), ParseUtils.jsonArrayToPrimitiveInt(GsonHelper.getAsJsonArray(partObject, "array")));
            }
        }

        if (vertices.has("indices")) {
            writer.addSubmesh(MeshLoader.UNGROUPED_PART_NAME, intArrayOf(vertices, "indices"));
        }

        return writer.toBytes();
    }

    /// Encodes the `armature` half of a legacy model as an armature `.akasset` stream.
    ///
    /// Bone ids come from the `joints` order, matching how `JsonArmatureLoader` assigns them. The
    /// root's Blender to Minecraft correction is left off: `BinaryArmatureLoader` applies it itself.
    ///
    /// `boneRenames` relabels bones on the way out, keyed by the name the JSON carries. It reaches
    /// the armature alone — the mesh's weight blocks address bones by id, not by name.
    public byte[] encodeArmature(JsonObject root, Map<String, String> boneRenames, boolean generateRootInOrigin) {
        JsonObject armature = GsonHelper.getAsJsonObject(root, "armature");
        TransformFormat format = JsonAssetLoader.getAsTransformFormatOrDefault(root, "armature_format");
        JsonArray joints = GsonHelper.getAsJsonArray(armature, "joints");
        JsonArray hierarchy = GsonHelper.getAsJsonArray(armature, "hierarchy");

        List<String> boneNames = new ArrayList<>(joints.size() + 1);

        for (int i = 0; i < joints.size(); i++) {
            boneNames.add(renamed(GsonHelper.convertToString(joints.get(i), "joints"), boneRenames));
        }

        // A bone's id is its index here, and the mesh's weight blocks address bones by that id — so
        // the generated root goes on the end, never in front.
        String generatedRoot = generateRootInOrigin ? availableRootName(boneNames) : null;

        if (generatedRoot != null) boneNames.add(generatedRoot);

        checkNamesUnique(boneNames);

        AkAssetWriter writer = new AkAssetWriter();

        ByteArrayOutputStream names = new ByteArrayOutputStream();
        AkAssetWriter.writeInt(names, boneNames.size());

        for (String boneName : boneNames) {
            AkAssetWriter.writeString(names, boneName);
        }

        writer.addBlock(DataType.ARM_BONE_NAMES, names.toByteArray());

        ByteArrayOutputStream tree = new ByteArrayOutputStream();

        if (generatedRoot != null) {
            // One root holding what used to be the roots, so the coord-system correction the loader
            // applies at depth 0 lands on the generated bone instead of on them.
            AkAssetWriter.writeInt(tree, 1);
            writeGeneratedRoot(tree, generatedRoot, hierarchy.size());
        } else {
            AkAssetWriter.writeInt(tree, hierarchy.size());
        }

        for (JsonElement rootBone : hierarchy) {
            writeBone(tree, GsonHelper.convertToJsonObject(rootBone, "root_armature"), format, boneRenames);
        }

        writer.addBlock(DataType.ARM_HIERARCHY, tree.toByteArray());

        return writer.toBytes();
    }

    /// The name a bone is written under: its rename where one is set, its own otherwise.
    private static String renamed(String boneName, Map<String, String> boneRenames) {
        String rename = boneRenames.get(boneName);

        return rename == null || rename.isEmpty() ? boneName : rename;
    }

    /// [ArmatureLoader#ROOT_BONE], or `root_1`, `root_2`… when the armature already carries it.
    private static String availableRootName(List<String> taken) {
        if (!taken.contains(ArmatureLoader.ROOT_BONE)) return ArmatureLoader.ROOT_BONE;

        int suffix = 1;

        while (taken.contains(ArmatureLoader.ROOT_BONE + "_" + suffix)) suffix++;

        return ArmatureLoader.ROOT_BONE + "_" + suffix;
    }

    /// `BinaryArmatureLoader` keys its bones by name, so two bones sharing one would silently
    /// collapse into a single entry and leave the mesh skinned to the wrong id.
    private static void checkNamesUnique(List<String> boneNames) {
        Set<String> seen = new HashSet<>(boneNames.size());

        for (String boneName : boneNames) {
            if (!seen.add(boneName)) {
                throw new IllegalStateException("Two bones would be exported as \"" + boneName + "\"");
            }
        }
    }

    /// The synthesised root: identity transform at the origin, holding `childCount` former roots.
    private static void writeGeneratedRoot(ByteArrayOutputStream out, String boneName, int childCount) {
        AkAssetWriter.writeString(out, boneName);

        for (int i = 0; i < 3; i++) AkAssetWriter.writeFloat(out, 0.0F);    // loc

        AkAssetWriter.writeFloat(out, 1.0F);                                // rot, w first
        for (int i = 0; i < 3; i++) AkAssetWriter.writeFloat(out, 0.0F);

        for (int i = 0; i < 3; i++) AkAssetWriter.writeFloat(out, 1.0F);    // sca

        AkAssetWriter.writeInt(out, childCount);
    }

    /// Writes one bone record — name, loc / rot / sca, child count — then recurses into `children`.
    ///
    /// Mirrors `JsonArmatureLoader`'s workaround for a mis-declared `armature_format`: the element's
    /// own shape wins, and the corrected format carries down the subtree.
    private static void writeBone(
        ByteArrayOutputStream out, JsonObject bone,
        TransformFormat format, Map<String, String> boneRenames
    ) {
        String name = renamed(GsonHelper.getAsString(bone, "name"), boneRenames);
        JsonElement transformElement = GsonHelper.getNonNull(bone, "transform");
        TransformFormat resolved = format;

        if (resolved == TransformFormat.ATTRIBUTES && transformElement.isJsonArray()) {
            resolved = TransformFormat.MATRIX;
        } else if (resolved == TransformFormat.MATRIX && transformElement.isJsonObject()) {
            resolved = TransformFormat.ATTRIBUTES;
        }

        Transform transform = switch (resolved) {
            case MATRIX -> {
                float[] elements = ParseUtils.jsonArrayToPrimitiveFloat(GsonHelper.convertToJsonArray(transformElement, "transform"));
                yield Transform.fromMatrix(new Matrix4f().set(elements).transpose());
            }
            case ATTRIBUTES -> {
                JsonObject attributes = GsonHelper.convertToJsonObject(transformElement, "transform");
                JsonArray loc = GsonHelper.getAsJsonArray(attributes, "loc");
                JsonArray rot = GsonHelper.getAsJsonArray(attributes, "rot");
                JsonArray sca = GsonHelper.getAsJsonArray(attributes, "sca");

                yield new Transform(
                    new Vec3(
                        GsonHelper.convertToFloat(loc.get(0), "loc"),
                        GsonHelper.convertToFloat(loc.get(1), "loc"),
                        GsonHelper.convertToFloat(loc.get(2), "loc")
                    ),
                    new Quaterniond(
                        GsonHelper.convertToFloat(rot.get(1), "rot"),
                        GsonHelper.convertToFloat(rot.get(2), "rot"),
                        GsonHelper.convertToFloat(rot.get(3), "rot"),
                        GsonHelper.convertToFloat(rot.get(0), "rot")
                    ),
                    new Vec3(
                        GsonHelper.convertToFloat(sca.get(0), "sca"),
                        GsonHelper.convertToFloat(sca.get(1), "sca"),
                        GsonHelper.convertToFloat(sca.get(2), "sca")
                    )
                );
            }
        };

        AkAssetWriter.writeString(out, name);
        AkAssetWriter.writeFloat(out, (float) transform.loc().x);
        AkAssetWriter.writeFloat(out, (float) transform.loc().y);
        AkAssetWriter.writeFloat(out, (float) transform.loc().z);
        AkAssetWriter.writeFloat(out, (float) transform.rot().w());
        AkAssetWriter.writeFloat(out, (float) transform.rot().x());
        AkAssetWriter.writeFloat(out, (float) transform.rot().y());
        AkAssetWriter.writeFloat(out, (float) transform.rot().z());
        AkAssetWriter.writeFloat(out, (float) transform.sca().x);
        AkAssetWriter.writeFloat(out, (float) transform.sca().y);
        AkAssetWriter.writeFloat(out, (float) transform.sca().z);

        JsonArray children = bone.has("children") ? GsonHelper.getAsJsonArray(bone, "children") : new JsonArray();
        AkAssetWriter.writeInt(out, children.size());

        for (JsonElement child : children) {
            writeBone(out, GsonHelper.convertToJsonObject(child, "child_bone"), resolved, boneRenames);
        }
    }

    /// The `{ "array": [...] }` wrapper every numeric block of a legacy model sits in.
    private static float[] floatArrayOf(JsonObject owner, String property) {
        return ParseUtils.jsonArrayToPrimitiveFloat(GsonHelper.getAsJsonArray(GsonHelper.getAsJsonObject(owner, property), "array"));
    }

    private static int[] intArrayOf(JsonObject owner, String property) {
        return ParseUtils.jsonArrayToPrimitiveInt(GsonHelper.getAsJsonArray(GsonHelper.getAsJsonObject(owner, property), "array"));
    }

    private static JsonObject readJsonObject(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            return GsonHelper.convertToJsonObject(JsonParser.parseReader(reader), "root_object");
        }
    }

    /// Per-entry export status. Success carries what was written; failure carries a reason the
    /// caller can surface without spitting a stack trace.
    public sealed interface ExportResult<T> {
        T entry();

        record Success<T>(T entry, List<Path> written) implements ExportResult<T> {}
        record Failure<T>(T entry, String reason) implements ExportResult<T> {}
    }

    /// Encodes an `.akasset` byte stream: `EFAS` magic plus zlib-deflated blocks. A block header is
    /// 16 bytes — depth, payloadStartOffset, payloadEndOffset, typeId. Every block this writes is a
    /// top-level one, so depth is always 0.
    private static final class AkAssetWriter {
        private final ByteArrayOutputStream payload = new ByteArrayOutputStream();

        void addAnimBoneTrack(String boneName, BoneAnimationTrack track) {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            writeString(body, boneName);
            writeInt(body, track.length());

            for (Keyframe k : track.keyframes()) writeFloat(body, k.timeStamp());

            for (Keyframe k : track.keyframes()) {
                Transform t = k.transform();
                writeFloat(body, (float) t.loc().x);
                writeFloat(body, (float) t.loc().y);
                writeFloat(body, (float) t.loc().z);
                writeFloat(body, (float) t.rot().w());
                writeFloat(body, (float) t.rot().x());
                writeFloat(body, (float) t.rot().y());
                writeFloat(body, (float) t.rot().z());
                writeFloat(body, (float) t.sca().x);
                writeFloat(body, (float) t.sca().y);
                writeFloat(body, (float) t.sca().z);
            }

            addBlock(DataType.ANIM_BONE_TRACK, body.toByteArray());
        }

        void addFloatArray(DataType dataType, float[] values) {
            ByteArrayOutputStream body = new ByteArrayOutputStream(values.length * 4);
            for (float v : values) writeFloat(body, v);
            addBlock(dataType, body.toByteArray());
        }

        void addIntArray(DataType dataType, int[] values) {
            ByteArrayOutputStream body = new ByteArrayOutputStream(values.length * 4);
            for (int v : values) writeInt(body, v);
            addBlock(dataType, body.toByteArray());
        }

        void addSubmesh(String partName, int[] drawIndices) {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            writeString(body, partName);
            for (int i : drawIndices) writeInt(body, i);
            addBlock(DataType.MESH_SUBMESH, body.toByteArray());
        }

        void addBlock(DataType dataType, byte[] body) {
            int absStart = payload.size() + BinaryAssetLoader.BLOCK_HEADER_SIZE;
            int absEnd = absStart + body.length;
            writeInt(payload, 0);
            writeInt(payload, absStart);
            writeInt(payload, absEnd);
            writeInt(payload, dataType.typeId());
            try {
                payload.write(body);
            } catch (IOException impossible) {
                throw new AssertionError(impossible);
            }
        }

        byte[] toBytes() {
            Deflater d = new Deflater();
            d.setInput(payload.toByteArray());
            d.finish();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                out.write(BinaryAssetLoader.PADDING);
            } catch (IOException impossible) {
                throw new AssertionError(impossible);
            }

            byte[] buf = new byte[1024];
            while (!d.finished()) {
                int n = d.deflate(buf);
                out.write(buf, 0, n);
            }
            d.end();

            return out.toByteArray();
        }

        private static void writeInt(ByteArrayOutputStream s, int v) {
            s.write(v & 0xFF);
            s.write((v >>> 8) & 0xFF);
            s.write((v >>> 16) & 0xFF);
            s.write((v >>> 24) & 0xFF);
        }

        private static void writeFloat(ByteArrayOutputStream s, float v) {
            writeInt(s, Float.floatToIntBits(v));
        }

        private static void writeString(ByteArrayOutputStream s, String v) {
            byte[] bytes = v.getBytes(StandardCharsets.US_ASCII);
            writeInt(s, bytes.length);
            try {
                s.write(bytes);
            } catch (IOException impossible) {
                throw new AssertionError(impossible);
            }
        }
    }

    /// Copy a template file into `destDir`, suffixing `_N` if a same-named file already
    /// exists. Returns the destination path. Armature-compat is the caller's responsibility
    /// — incompatible templates copy fine, they just won't appear in the modal's combo.
    public Path importTemplate(Path src, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        String stem = FileSystemHelper.stem(src.getFileName().toString());
        String available = FileSystemHelper.findNextAvailableName(destDir, stem, ".json");
        Path dest = destDir.resolve(available + ".json");
        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        return dest;
    }

    /// Disk-backed handle on a template. `file` is the absolute path under the project's
    /// `retargeter_templates/` directory; `displayName` strips the `.json` extension for UI.
    public record TemplateEntry(Path file, MappingTemplate template) {
        public String displayName() {
            return FileSystemHelper.stem(file.getFileName().toString());
        }
    }

    /// Per-entry mapping: a clip's bone name → the armature's bone name.
    /// 1:1 with a `RetargetEntry`. Mutable in place — `setArmatureId` + `mapping().clear()`
    /// are used when the user swaps the target armature without dropping the source clips.
    public static final class MappingTemplate {
        /// On-disk shape locked to the user's existing `epicpack/retargeter_templates/*.json`:
        /// `{"armature": "<id>", "bone_mappings": {...}}`. A null `armatureId` is the
        /// in-construction state; encoding throws (the codec only takes the disk shape).
        public static final Codec<MappingTemplate> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("armature").forGetter(t -> {
                Identifier id = t.armatureId();
                if (id == null) throw new IllegalStateException("MappingTemplate.armatureId is null on encode");
                return id;
            }),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("bone_mappings").forGetter(MappingTemplate::mapping)
        ).apply(i, (armId, m) -> {
            MappingTemplate t = new MappingTemplate(armId);
            t.sourceToTarget.putAll(m);
            return t;
        }));

        @Nullable
        private Identifier armatureId;
        private final Map<String, String> sourceToTarget;

        public MappingTemplate(@Nullable Identifier armatureId) {
            this.armatureId = armatureId;
            this.sourceToTarget = new LinkedHashMap<>();
        }

        public @Nullable Identifier armatureId() {
            return armatureId;
        }

        public void setArmatureId(@Nullable Identifier id) {
            this.armatureId = id;
        }

        public Map<String, String> mapping() {
            return sourceToTarget;
        }

        public @Nullable String getTarget(String src) {
            return sourceToTarget.get(src);
        }

        public void putMapping(String src, String tgt) {
            sourceToTarget.put(src, tgt);
        }

        public void clearMapping(String src) {
            sourceToTarget.remove(src);
        }

        /// Whether every bone of `clipBones` has a non-empty target mapping.
        public boolean isComplete(Set<String> clipBones) {
            for (String b : clipBones) {
                String t = sourceToTarget.get(b);
                if (t == null || t.isEmpty()) return false;
            }

            return true;
        }
    }
}
