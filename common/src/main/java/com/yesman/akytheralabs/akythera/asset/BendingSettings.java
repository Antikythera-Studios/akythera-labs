package com.yesman.akytheralabs.akythera.asset;

import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// How the three vertex rows of a bending added by [ModelSkinHolder#addBending] are weighted.
public final class BendingSettings {
    public enum Direction {
        /// The side of the middle row facing negative Z
        FRONT,
        /// The side of the middle row facing positive Z
        BEHIND
    }

    /// The bone the side of the slice nearer its own head follows.
    @EditorEditable(
        label = LangKeys.GUI_MODEL_SKINNING_SCREEN_BENDING_ROOT_BONE, order = 0,
        candidates = "rootBones", candidateLabel = "boneLabel", searchable = true, rebuildOnWrite = true
    )
    private @Nullable Bone rootBone;

    /// The bone the other side of the slice follows.
    @EditorEditable(
        label = LangKeys.GUI_MODEL_SKINNING_SCREEN_BENDING_BENDING_BONE, order = 1,
        candidates = "bendingBones", candidateLabel = "boneLabel", searchable = true, rebuildOnWrite = true
    )
    private @Nullable Bone bendingBone;

    /// The bone the [#direction] side of the middle row follows: an elbow or a knee.
    @EditorEditable(
        label = LangKeys.GUI_MODEL_SKINNING_SCREEN_BENDING_INFLATION_BONE, order = 2,
        candidates = "inflationBones", candidateLabel = "boneLabel", searchable = true
    )
    private @Nullable Bone inflationBone;

    @EditorEditable(label = LangKeys.GUI_MODEL_SKINNING_SCREEN_BENDING_DIRECTION, order = 3)
    private Direction direction = Direction.FRONT;

    public @Nullable Bone getRootBone() {
        return rootBone;
    }

    public @Nullable Bone getBendingBone() {
        return bendingBone;
    }

    public @Nullable Bone getInflationBone() {
        return inflationBone;
    }

    public Direction getDirection() {
        return direction;
    }

    /// Every bone of the skinned armature but [#bendingBone].
    private List<Bone> rootBones(final ModelSkinHolder holder) {
        return holder.getBones().stream().filter(bone -> !bone.equals(bendingBone)).toList();
    }

    /// Every bone of the skinned armature but [#rootBone].
    private List<Bone> bendingBones(final ModelSkinHolder holder) {
        return holder.getBones().stream().filter(bone -> !bone.equals(rootBone)).toList();
    }

    private List<Bone> inflationBones(final ModelSkinHolder holder) {
        return holder.getBones();
    }

    private Component boneLabel(final Bone bone) {
        return Component.literal(bone.getName());
    }
}
