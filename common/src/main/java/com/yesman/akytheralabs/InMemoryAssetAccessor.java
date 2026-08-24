package com.yesman.akytheralabs;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.asset.armature.Armature;
import net.minecraft.resources.Identifier;

/// An asset the editor built itself — a dropped file baked in memory, a remapped clip — handed to
/// the APIs that only take an [AssetAccessor]. The identifier is synthetic: useful in logs, never
/// resolved through a resource manager.
public record InMemoryAssetAccessor<T>(Identifier identifier, AssetAccessor.Type type, T asset)
    implements AssetAccessor<T>
{
    public static InMemoryAssetAccessor<SkeletalMesh> mesh(final Identifier identifier, final SkeletalMesh mesh) {
        return new InMemoryAssetAccessor<>(identifier, Type.SKELETAL_MESH, mesh);
    }

    public static InMemoryAssetAccessor<Armature> armature(final Identifier identifier, final Armature armature) {
        return new InMemoryAssetAccessor<>(identifier, Type.ARMATURE, armature);
    }

    public static InMemoryAssetAccessor<AnimationClip> animation(final Identifier identifier, final AnimationClip clip) {
        return new InMemoryAssetAccessor<>(identifier, Type.ANIMATION, clip);
    }

    @Override
    public T get() {
        return asset;
    }

    @Override
    public boolean isResourcePresent() {
        return true;
    }
}
