package com.yesman.akytheralabs.akythera.physics.constraint;

import com.yesman.akythera.core.asset.physics.constraint.AkytheraBoneConstraintType;
import com.yesman.akythera.core.asset.physics.constraint.BoneConstraint;
import com.yesman.akythera.core.asset.physics.constraint.BoneConstraintType;
import com.yesman.akythera.core.asset.physics.constraint.LimitRotationConstraint;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;

/// Editable twin of [LimitRotationConstraint].
public final class LimitRotationHolder extends BoneConstraintSettingsHolder {
    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_LIMIT_X, order = 0)
    private final AngularLimitHolder x = new AngularLimitHolder(LimitRotationConstraint.AngularLimit.FREE);

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_LIMIT_Y, order = 1)
    private final AngularLimitHolder y = new AngularLimitHolder(LimitRotationConstraint.AngularLimit.FREE);

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_LIMIT_Z, order = 2)
    private final AngularLimitHolder z = new AngularLimitHolder(LimitRotationConstraint.AngularLimit.FREE);

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_SIMULATION_ONLY, order = 3)
    private boolean simulationOnly = LimitRotationConstraint.FREE.simulationOnly();

    @Override
    public BoneConstraintType type() {
        return AkytheraBoneConstraintType.LIMIT_ROTATION;
    }

    @Override
    public void readFrom(final BoneConstraint constraint) {
        LimitRotationConstraint limit = (LimitRotationConstraint) constraint;

        x.readFrom(limit.x());
        y.readFrom(limit.y());
        z.readFrom(limit.z());
        simulationOnly = limit.simulationOnly();
    }

    @Override
    public LimitRotationConstraint makeObject() {
        return new LimitRotationConstraint(x.makeObject(), y.makeObject(), z.makeObject(), simulationOnly);
    }

    /// Editable twin of [LimitRotationConstraint.AngularLimit]. A bound never passes the other.
    public static final class AngularLimitHolder {
        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_MIN, order = 0, min = -180.0F, max = 180.0F, rebuildOnWrite = true)
        private float min;

        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_MAX, order = 1, min = -180.0F, max = 180.0F, rebuildOnWrite = true)
        private float max;

        public AngularLimitHolder(final LimitRotationConstraint.AngularLimit limit) {
            readFrom(limit);
        }

        public void readFrom(final LimitRotationConstraint.AngularLimit limit) {
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

        public LimitRotationConstraint.AngularLimit makeObject() {
            return new LimitRotationConstraint.AngularLimit(min, max);
        }
    }
}
