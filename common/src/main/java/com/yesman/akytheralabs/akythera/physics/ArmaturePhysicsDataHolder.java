package com.yesman.akytheralabs.akythera.physics;

import com.mojang.serialization.Codec;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.physics.ArmaturePhysicsData;
import com.yesman.akythera.core.asset.physics.constraint.BoneConstraint;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import com.yesman.akytheralabs.akythera.physics.constraint.BoneConstraintStackHolder;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/// Editable view of an armature physics asset. Lives on the asset's accessor, one per identifier —
/// see [AkytheraProject.EditorArmaturePhysicsAccessor].
///
/// The armature is the asset's own path, so it is read rather than edited: this holder's rows are the
/// bodies, the bone constraints and the simulation settings.
public final class ArmaturePhysicsDataHolder {
    /// The sidecar: the preview entity type alone. The bodies are the asset itself.
    private static final Codec<Optional<Identifier>> SIDECAR_CODEC =
        Identifier.CODEC.optionalFieldOf("preview_entity_type").codec();

    /// Bone name to body, including bodies left empty; [#makeObject] drops those.
    private final Map<String, BoneBodyHolder> bodies = new LinkedHashMap<>();

    /// Bone name to constraints, including empty stacks; [#makeObject] drops those.
    private final Map<String, BoneConstraintStackHolder> constraints = new LinkedHashMap<>();

    private final RagdollParametersHolder simulation;

    /// The entity the bodies are previewed on, or null for none.
    private @Nullable Identifier previewEntityType;

    /// The asset this holder edits. A rename of its armature mutates the id in place on this instance.
    private final AkytheraProject.EditorArmaturePhysicsAccessor accessor;

    private ArmaturePhysicsDataHolder(
        final AkytheraProject.EditorArmaturePhysicsAccessor accessor,
        final @Nullable ArmaturePhysicsData data,
        final @Nullable Identifier previewEntityType
    ) {
        this.accessor = accessor;
        this.previewEntityType = previewEntityType;
        this.simulation = new RagdollParametersHolder(data == null ? ArmaturePhysicsData.RagdollParameters.DEFAULT : data.simulation());

        if (data != null) {
            data.bodies().forEach((bone, body) -> bodies.put(bone, new BoneBodyHolder(body)));
            data.constraints().forEach((bone, stack) -> constraints.put(bone, new BoneConstraintStackHolder(stack)));
        }
    }

    /// The asset and its sidecar as they stand on disk, or empty ones when a file is blank or unreadable.
    public static ArmaturePhysicsDataHolder readFrom(final AkytheraProject.EditorArmaturePhysicsAccessor accessor) {
        return new ArmaturePhysicsDataHolder(
            accessor,
            FileSystemHelper.readJson(AkytheraProject.getJsonAssetFileOf(accessor.identifier()), ArmaturePhysicsData.CODEC, null),
            FileSystemHelper.readJson(AkytheraProject.getMetadataFileOf(accessor.identifier()), SIDECAR_CODEC, Optional.empty()).orElse(null)
        );
    }

    public ArmaturePhysicsData makeObject() {
        Map<String, ArmaturePhysicsData.BoneBody> madeBodies = new LinkedHashMap<>();
        Map<String, List<BoneConstraint>> madeConstraints = new LinkedHashMap<>();

        bodies.forEach((bone, body) -> {
            if (!body.getColliders().isEmpty()) {
                madeBodies.put(bone, body.makeObject());
            }
        });

        constraints.forEach((bone, stack) -> {
            if (!stack.getConstraints().isEmpty()) {
                madeConstraints.put(bone, stack.makeObject());
            }
        });

        return new ArmaturePhysicsData(Map.copyOf(madeBodies), Map.copyOf(madeConstraints), simulation.makeObject());
    }

    public boolean save() {
        if (!FileSystemHelper.writeJson(AkytheraProject.getJsonAssetFileOf(accessor.identifier()), ArmaturePhysicsData.CODEC, makeObject())) {
            AkytheraLabs.LOGGER.error("Failed to save armature physics '{}'", accessor.identifier());
            return false;
        }

        if (!FileSystemHelper.writeJson(AkytheraProject.getMetadataFileOf(accessor.identifier()), SIDECAR_CODEC, Optional.ofNullable(previewEntityType))) {
            AkytheraLabs.LOGGER.error("Failed to save armature physics metadata '{}'", accessor.identifier());
            return false;
        }

        return true;
    }

    /// The armature this asset belongs to, from its path.
    public AssetAccessor<? extends Armature> getArmature() {
        return AkytheraProject.getCurrentProject().getArmatureOfPhysics(accessor);
    }

    /// The armature's bone names, sorted. Empty while the armature cannot be read.
    public List<String> boneNames() {
        Armature armature = getArmature().getOrHandleException(AkytheraLabs.LOGGER::error);

        if (armature == null) return List.of();

        List<String> names = new ArrayList<>();
        armature.visitBones(bone -> names.add(bone.getName()));
        names.sort(Comparator.naturalOrder());

        return List.copyOf(names);
    }

    /// `bone`'s body, created empty on first access.
    public BoneBodyHolder bodyOf(final String bone) {
        return bodies.computeIfAbsent(bone, _ -> BoneBodyHolder.empty());
    }

    /// `bone`'s constraints, created empty on first access.
    public BoneConstraintStackHolder constraintsOf(final String bone) {
        return constraints.computeIfAbsent(bone, _ -> new BoneConstraintStackHolder(List.of()));
    }

    /// Every body touched so far, empty ones included.
    public Map<String, BoneBodyHolder> getBodies() {
        return bodies;
    }

    public RagdollParametersHolder getSimulation() {
        return simulation;
    }

    public AkytheraProject.EditorArmaturePhysicsAccessor getAccessor() {
        return accessor;
    }

    public @Nullable Identifier getPreviewEntityType() {
        return previewEntityType;
    }

    public void setPreviewEntityType(final @Nullable Identifier previewEntityType) {
        this.previewEntityType = previewEntityType;
    }
}
