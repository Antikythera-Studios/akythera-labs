package com.yesman.akytheralabs.akythera.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akythera.api.asset.armature.ArmatureManager;
import com.yesman.akythera.client.api.mesh.MeshManager;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.keyframe.AnimationMirror;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.loader.AnimationLoader;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.DataHolder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/// Asset accessors as the editor reads them: an id in the open project's namespace resolves to that
/// project's editor accessor, which reads the project directory and follows a rename of the file;
/// every other id resolves through the engine's manager.
///
/// The codecs are the editor's counterparts to [AnimationManager#CODEC], [ArmatureManager#CODEC] and
/// [MeshManager#CODEC] — a `DataHolder`'s `EDITOR_CODEC` reads through these, world data through the
/// engine's. Ids are the trimmed form both sides persist, with no asset directory prefix.
public final class EditorAssetAccessors {
    private EditorAssetAccessors() {
    }

    public static final Codec<AssetAccessor<AnimationClip>> ANIMATION_CODEC =
        Identifier.CODEC.xmap(
            EditorAssetAccessors::animation,
            accessor -> trim(accessor.identifier(), AnimationManager.DIRECTORY)
        );

    public static final Codec<AssetAccessor<? extends Armature>> ARMATURE_CODEC =
        Identifier.CODEC.xmap(
            EditorAssetAccessors::armature,
            accessor -> trim(accessor.identifier(), ArmatureManager.DIRECTORY)
        );

    public static final Codec<AssetAccessor<? extends Mesh<?>>> MESH_CODEC =
        Identifier.CODEC.xmap(
            EditorAssetAccessors::mesh,
            accessor -> trim(accessor.identifier(), MeshManager.DIRECTORY)
        );

    /// [AnimationLoader.Metadata] with `ref_pose_provider` read through [#ANIMATION_CODEC]. The
    /// fields and their names are the engine's, so the sidecar file is the same either way.
    public static final MapCodec<AnimationLoader.Metadata> ANIMATION_METADATA_MAP_CODEC =
        RecordCodecBuilder.mapCodec(
            instance ->
                instance.group(
                    AnimationLoader.ImportType.CODEC.fieldOf("import_type")
                        .forGetter(AnimationLoader.Metadata::importType),
                    ANIMATION_CODEC.optionalFieldOf("ref_pose_provider")
                        .forGetter(ParseUtils.provideOptional(AnimationLoader.Metadata::refPoseProvider)),
                    Codec.FLOAT.fieldOf("ref_pose_timestamp")
                        .forGetter(AnimationLoader.Metadata::refPoseTimeStamp)
                ).apply(instance, AnimationLoader.Metadata::new)
        );

    public static final Codec<AnimationLoader.Metadata> ANIMATION_METADATA_CODEC =
        ANIMATION_METADATA_MAP_CODEC.codec();

    /// [AnimationMirror.Setup] with `vendor` read through [#ARMATURE_CODEC]. The fields and their
    /// names are the engine's, so the sidecar file is the same either way.
    public static final MapCodec<AnimationMirror.Setup> ANIMATION_MIRROR_MAP_CODEC =
        RecordCodecBuilder.mapCodec(
            instance ->
                instance.group(
                    ARMATURE_CODEC.optionalFieldOf("vendor")
                        .forGetter(ParseUtils.provideOptional(AnimationMirror.Setup::vendor)),
                    Codec.BOOL.optionalFieldOf("enable_mirroring", false)
                        .forGetter(AnimationMirror.Setup::enabled)
                ).apply(instance, AnimationMirror.Setup::new)
        );

    public static AssetAccessor<AnimationClip> animation(final Identifier trimmedId) {
        AkytheraProject project = projectOwning(trimmedId);

        return project == null
            ? AnimationManager.accessor(trimmedId)
            : project.getOrIssueEditorAnimationAccessor(prefix(trimmedId, AnimationManager.DIRECTORY));
    }

    public static AssetAccessor<? extends Armature> armature(final Identifier trimmedId) {
        AkytheraProject project = projectOwning(trimmedId);

        return project == null
            ? ArmatureManager.accessor(trimmedId.getNamespace(), trimmedId.getPath(), Armature::new)
            : project.getOrIssueEditorArmatureAccessor(prefix(trimmedId, ArmatureManager.DIRECTORY), Armature::new);
    }

    /// The project's shared accessor for the mesh `trimmedId`, or the engine's for a mod id. A
    /// project mesh nothing has issued an accessor for yet is taken as skeletal; one already issued
    /// keeps whatever type the file turned out to hold.
    public static AssetAccessor<? extends Mesh<?>> mesh(final Identifier trimmedId) {
        AkytheraProject project = projectOwning(trimmedId);

        return project == null
            ? MeshManager.skeletalMesh(trimmedId.getNamespace(), trimmedId.getPath(), SkeletalMesh::new)
            : project.getOrIssueEditorSkeletalMeshAccessor(prefix(trimmedId, MeshManager.DIRECTORY), SkeletalMesh::new);
    }

    /// The accessor of `type` a slot holds while nothing is picked: the engine's empty accessor where
    /// it has one, otherwise the one for [DataHolder#EMPTY_REFERENCE].
    public static AssetAccessor<?> empty(final AssetAccessor.Type type) {
        return switch (type) {
            case ANIMATION -> AnimationManager.EMPTY_ANIMATION_ACCESSOR;
            case ARMATURE -> ArmatureManager.EMPTY_ARMATURE_ACCESSOR;
            case STATIC_MESH, SKELETAL_MESH -> mesh(DataHolder.EMPTY_REFERENCE);
            case ARMATURE_PHYSICS ->
                AkytheraProject.getCurrentProject().getOrIssueEditorArmaturePhysicsAccessor(DataHolder.EMPTY_REFERENCE);
        };
    }

    /// The open project when `id` names one of its assets. Null when no project is open, or when the
    /// id belongs to a mod.
    private static @Nullable AkytheraProject projectOwning(final Identifier id) {
        AkytheraProject project = AkytheraProject.getCurrentProjectOrNull();

        return project != null && id.getNamespace().equals(project.getProjectName()) ? project : null;
    }

    /// Trimmed id → the full id the `issuedEditor*Accessors` maps are keyed by.
    private static Identifier prefix(final Identifier trimmedId, final String directory) {
        return Identifier.fromNamespaceAndPath(trimmedId.getNamespace(), directory + trimmedId.getPath());
    }

    private static Identifier trim(final Identifier id, final String directory) {
        return Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().replace(directory, ""));
    }
}
