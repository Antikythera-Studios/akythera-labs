package com.yesman.akytheralabs.gui.widget.main;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.keyframe.BoneAnimationTrack;
import com.yesman.akythera.core.animation.keyframe.Keyframe;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.loader.ResourceLoadingFailureException;
import com.yesman.akythera.core.asset.loader.binary.BinaryAssetLoader;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.FileSystemHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.Deflater;

/// Owns the retargeting algorithm, template storage, and the live "preview" accessor
/// the modal renders on the model previewer. `Export All` calls [#remap] then optionally
/// runs [#generateRootBone] before encoding via the inline `AkAssetWriter`.
public final class AnimationRetargetManager {
    public static final AnimationRetargetManager INSTANCE = new AnimationRetargetManager();

    private AnimationRetargetManager() {}

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
    /// track, encode as `.efasset` and write into `destDir`. One-bad-file-doesn't-kill-
    /// the-batch: each per-entry failure becomes an [ExportResult.Failure]; other entries
    /// still export.
    public List<ExportResult> exportAll(
        List<RetargetEntry> entries,
        Armature sourceArmature,
        Armature destArmature,
        Path destDir,
        boolean generateRootBone
    ) {
        String sourceRootName = sourceArmature.getRootBone().getName();
        String destRootName = destArmature.getRootBone().getName();

        List<ExportResult> results = new ArrayList<>(entries.size());

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
                Path dest = destDir.resolve(FileSystemHelper.stem(e.source.getFileName().toString()) + ".efasset");
                Files.write(dest, writer.toBytes());

                results.add(new ExportResult.Success(e, dest));
            } catch (Exception ex) {
                AkytheraLabs.LOGGER.error("Failed to export {}", e.label(), ex);
                results.add(new ExportResult.Failure(e, ex.getMessage()));
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

    /// Per-entry export status. Success carries the written path; failure carries a
    /// localized reason so the modal can surface it without spitting a stack trace.
    public sealed interface ExportResult {
        RetargetEntry entry();

        record Success(RetargetEntry entry, Path written) implements ExportResult {}
        record Failure(RetargetEntry entry, String reason) implements ExportResult {}
    }

    /// Encodes an `.efasset` byte stream: `EFAS` magic + zlib(deflated bone-track blocks).
    /// Block header layout (16 bytes): depth, payloadStartOffset, payloadEndOffset, typeId.
    /// Class is named ahead of the upcoming `.akasset` rename — the wire-magic stays `EFAS`
    /// for now so the existing `BinaryAnimationLoader` keeps reading our output.
    private static final class AkAssetWriter {
        /// `BinaryAssetLoader.DataType.ANIM_BONE_TRACK.typeId` — mirrored as a constant
        /// because the enum's `typeId` field is package-private.
        private static final int TYPE_ID_ANIM_BONE_TRACK = 0x0200;

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

            appendBlock(0, TYPE_ID_ANIM_BONE_TRACK, body.toByteArray());
        }

        private void appendBlock(int depth, int typeId, byte[] body) {
            int absStart = payload.size() + BinaryAssetLoader.BLOCK_HEADER_SIZE;
            int absEnd = absStart + body.length;
            writeInt(payload, depth);
            writeInt(payload, absStart);
            writeInt(payload, absEnd);
            writeInt(payload, typeId);
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

    /// Wraps an in-memory `AnimationClip` as an `AssetAccessor` so
    /// `EntityModelComponent.setSimplePlayingAnimation` can consume it. ID is synthetic —
    /// useful in logs, never resolved through the resource manager.
    public record DirectAccessor(Identifier id, AnimationClip clip) implements AssetAccessor<AnimationClip> {
        @Override
        public AnimationClip get() throws ResourceLoadingFailureException {
            return clip;
        }

        @Override
        public Type type() {
            return Type.ANIMATION;
        }

        @Override
        public Identifier identifier() {
            return id;
        }

        @Override
        public boolean isResourcePresent() {
            return true;
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

        /// Step 5 uses this to mark incomplete entries red before export.
        public boolean isComplete(Set<String> clipBones) {
            for (String b : clipBones) {
                String t = sourceToTarget.get(b);
                if (t == null || t.isEmpty()) return false;
            }

            return true;
        }
    }
}
