package com.yesman.akytheralabs.akythera.asset;

import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.loader.ArmatureLoader;
import com.yesman.akythera.core.asset.loader.AssetLoader;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// Editable view of an armature asset's sidecar [ArmatureLoader.Metadata]: the bones that stand
/// opposite each other across the mirror plane. Lives on the armature's accessor, one per
/// identifier — see [AkytheraProject.EditorArmatureAccessor].
public final class ArmatureMetadataHolder {
    /// One row of the pair table. A side is null while the row is still being filled in.
    public record PairEntry(@Nullable String first, @Nullable String second) {
        public boolean isComplete() {
            return first != null && second != null;
        }

        public boolean holds(final String bone) {
            return bone.equals(first) || bone.equals(second);
        }
    }

    /// The pair rows in the order they are shown, which is the order they are written in.
    private final List<PairEntry> entries = new ArrayList<>();

    /// The armature this sidecar describes. A rename mutates the id in place on this instance, so
    /// the holder keeps pointing at the file.
    private final AkytheraProject.EditorArmatureAccessor<?> accessor;

    private ArmatureMetadataHolder(
        final AkytheraProject.EditorArmatureAccessor<?> accessor,
        final ArmatureLoader.Metadata metadata
    ) {
        this.accessor = accessor;

        for (ArmatureLoader.MirrorBonePair pair : metadata.mirrorBonePairs()) {
            entries.add(new PairEntry(pair.first(), pair.second()));
        }
    }

    /// The sidecar of `accessor` as it stands on disk, or the defaults when the file is absent.
    public static ArmatureMetadataHolder readFrom(final AkytheraProject.EditorArmatureAccessor<?> accessor) {
        return new ArmatureMetadataHolder(accessor, AssetLoader.readMetadata(
            accessor.identifier(),
            AkytheraProject.getProjectResourceManager(),
            ArmatureLoader.Metadata.CODEC,
            ArmatureLoader.Metadata.DEFAULT
        ));
    }

    /// The rows as a sidecar. A row with an empty side is not a pair, so it is left out.
    public ArmatureLoader.Metadata makeMetadata() {
        List<ArmatureLoader.MirrorBonePair> pairs = new ArrayList<>(entries.size());

        for (PairEntry entry : entries) {
            if (entry.isComplete()) {
                pairs.add(new ArmatureLoader.MirrorBonePair(entry.first(), entry.second()));
            }
        }

        return new ArmatureLoader.Metadata(List.copyOf(pairs));
    }

    /// Writes the sidecar next to its asset, then drops the parsed armature: the metadata is read
    /// into [Armature#getMirrorBone] at load, so the next reference must re-read it.
    public boolean save() {
        Path metadataFile = AkytheraProject.getMetadataFileOf(accessor.identifier());

        if (!FileSystemHelper.writeJson(metadataFile, ArmatureLoader.Metadata.CODEC, makeMetadata())) {
            AkytheraLabs.LOGGER.error("Failed to save armature metadata '{}'", metadataFile);
            return false;
        }

        AkytheraProject.getCurrentProject().invalidateEditorArmature(accessor.identifier());

        return true;
    }

    public List<PairEntry> getEntries() {
        return List.copyOf(entries);
    }

    public void setEntries(final List<PairEntry> newEntries) {
        entries.clear();
        entries.addAll(newEntries);
    }

    /// This table with `first` and `second` paired: the rows either of them sat in are gone, and the
    /// new pair is appended. A bone belongs to one pair.
    public List<PairEntry> withPair(final String first, final String second) {
        List<PairEntry> next = new ArrayList<>(entries.size() + 1);

        for (PairEntry entry : entries) {
            if (!entry.holds(first) && !entry.holds(second)) {
                next.add(entry);
            }
        }

        next.add(new PairEntry(first, second));

        return next;
    }

    /// The bone `bone` is paired with, or null while it stands alone.
    public @Nullable String getPartner(final String bone) {
        for (PairEntry entry : entries) {
            if (bone.equals(entry.first())) return entry.second();
            if (bone.equals(entry.second())) return entry.first();
        }

        return null;
    }

    /// The bones no row has taken, plus `keep` so a side can re-offer its own current pick. Answers
    /// in `allBones` order.
    public List<String> unclaimedBones(final List<String> allBones, final @Nullable String keep) {
        Set<String> claimed = new HashSet<>();

        for (PairEntry entry : entries) {
            if (entry.first() != null) claimed.add(entry.first());
            if (entry.second() != null) claimed.add(entry.second());
        }

        claimed.remove(keep);

        List<String> candidates = new ArrayList<>();

        for (String bone : allBones) {
            if (!claimed.contains(bone)) candidates.add(bone);
        }

        return List.copyOf(candidates);
    }

    public AkytheraProject.EditorArmatureAccessor<?> getAccessor() {
        return accessor;
    }
}
