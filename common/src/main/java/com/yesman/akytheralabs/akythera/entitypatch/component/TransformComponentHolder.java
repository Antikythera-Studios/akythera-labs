package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.component.TransformComponent;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akytheralabs.dataholder.TransformHolder;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;

/// Editor mirror of [TransformComponent]: a component holder with a transform of its own, which the components
/// attached under it live in.
public interface TransformComponentHolder {
    /// This holder's transform inside its parent space. Details rows and gizmo drags write into it.
    TransformHolder getLocalTransform();

    /// The elements of the parent space this holder takes on.
    TransformInheritPolicy getTransformInheritPolicy();

    /// The spaces of one previewed patch's holders, resolved by [TransformComponent]'s rules: a socket on an armature
    /// rides its bone, a skeletal mesh and a worn equipment piece sit in their armature's space, anything else in its nearest
    /// [TransformComponentHolder] host's space, and the patch root gives [#rootTransform].
    ///
    /// @param hostOf        the holder a component holder is attached under
    /// @param boneTransform a bone's transform in the previewed armature's own space, by bone name
    /// @param rootTransform the space the patch root gives, in viewer space
    record Space(
        Function<ComponentDataHolder<?>, @Nullable ComponentAttachableHolder> hostOf,
        Function<String, Transform> boneTransform,
        Supplier<Transform> rootTransform
    ) {
        /// `holder`'s transform in viewer space.
        public Transform modelTransform(final TransformComponentHolder holder) {
            return holder.getTransformInheritPolicy().inherit(parentTransform(holder), holder.getLocalTransform().makeObject());
        }

        /// The space `holder`'s [#getLocalTransform] is in, in viewer space.
        public Transform parentTransform(final TransformComponentHolder holder) {
            ComponentAttachableHolder host = hostOf.apply((ComponentDataHolder<?>) holder);

            if (holder instanceof TransformSocketHolder socket && host instanceof ArmatureComponentHolder armature) {
                return modelTransform(armature).mul(boneTransform.apply(socket.getBoneName()));
            }

            if (holder instanceof MeshComponentHolder mesh && mesh.getMesh() != null && !mesh.getMesh().checkType(AssetAccessor.Type.STATIC_MESH)
                || holder instanceof SkinnedArmorRendererHolder
            ) {
                for (ComponentAttachableHolder walked = host; walked != null; walked = walked instanceof ComponentDataHolder<?> data ? hostOf.apply(data) : null) {
                    if (walked instanceof ArmatureComponentHolder armature) return modelTransform(armature);
                }
            }

            for (ComponentAttachableHolder walked = host; walked != null; walked = walked instanceof ComponentDataHolder<?> data ? hostOf.apply(data) : null) {
                if (walked instanceof TransformComponentHolder transform) return modelTransform(transform);
            }

            return rootTransform.get();
        }
    }
}
