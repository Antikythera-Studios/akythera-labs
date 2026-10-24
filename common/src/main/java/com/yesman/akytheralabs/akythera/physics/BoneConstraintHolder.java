package com.yesman.akytheralabs.akythera.physics;

import com.yesman.akythera.core.asset.physics.ArmaturePhysicsData;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// Editable twin of [ArmaturePhysicsData.BoneConstraint].
public final class BoneConstraintHolder {
    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_LIMIT_X, order = 0)
    private final AngularLimitHolder x;

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_LIMIT_Y, order = 1)
    private final AngularLimitHolder y;

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_LIMIT_Z, order = 2)
    private final AngularLimitHolder z;

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_HALF_BEND_BONE, order = 3, candidates = "bones", searchable = true)
    private @Nullable String halfBendBone;

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_MAX_STRETCH, order = 4, min = 1.0F)
    private float maxStretch;

    public BoneConstraintHolder(final ArmaturePhysicsData.BoneConstraint constraint) {
        x = new AngularLimitHolder(constraint.x());
        y = new AngularLimitHolder(constraint.y());
        z = new AngularLimitHolder(constraint.z());
        halfBendBone = constraint.halfBendBone();
        maxStretch = constraint.maxStretch();
    }

    private static List<String> bones(final ArmaturePhysicsDataHolder holder) {
        return holder.boneNames();
    }

    public AngularLimitHolder getX() {
        return x;
    }

    public AngularLimitHolder getY() {
        return y;
    }

    public AngularLimitHolder getZ() {
        return z;
    }

    public ArmaturePhysicsData.BoneConstraint makeObject() {
        String halfBend = halfBendBone == null || halfBendBone.isBlank() ? null : halfBendBone;

        return new ArmaturePhysicsData.BoneConstraint(x.makeObject(), y.makeObject(), z.makeObject(), halfBend, maxStretch);
    }

    /// Editable twin of [ArmaturePhysicsData.AngularLimit]. A bound never passes the other.
    public static final class AngularLimitHolder {
        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_MIN, order = 0, min = -180.0F, max = 180.0F, rebuildOnWrite = true)
        private float min;

        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_MAX, order = 1, min = -180.0F, max = 180.0F, rebuildOnWrite = true)
        private float max;

        public AngularLimitHolder(final ArmaturePhysicsData.AngularLimit limit) {
            min = limit.min();
            max = limit.max();
        }

        public float getMin() {
            return min;
        }

        public void setMin(final float min) {
            this.min = Math.clamp(min, -180.0F, max);
        }

        public float getMax() {
            return max;
        }

        public void setMax(final float max) {
            this.max = Math.clamp(max, min, 180.0F);
        }

        public ArmaturePhysicsData.AngularLimit makeObject() {
            return new ArmaturePhysicsData.AngularLimit(min, max);
        }
    }
}
