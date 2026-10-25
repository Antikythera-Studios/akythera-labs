package com.yesman.akytheralabs.akythera.physics.constraint;

import com.yesman.akythera.core.asset.physics.constraint.BoneConstraint;
import com.yesman.akythera.core.asset.physics.constraint.LimitRotationConstraint;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// The constraints of one bone, in the order they run.
public final class BoneConstraintStackHolder {
    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_CONSTRAINTS, order = 0, defaultElement = "createConstraint")
    private final List<BoneConstraintHolder> constraints = new ArrayList<>();

    public BoneConstraintStackHolder(final List<BoneConstraint> constraints) {
        constraints.forEach(constraint -> this.constraints.add(BoneConstraintHolder.of(constraint)));
    }

    /// A new constraint: a free Limit Rotation.
    private static BoneConstraintHolder createConstraint() {
        return new BoneConstraintHolder(new LimitRotationHolder());
    }

    public List<BoneConstraintHolder> getConstraints() {
        return constraints;
    }

    /// The first Limit Rotation of the stack, null for none.
    public @Nullable LimitRotationConstraint findLimitRotation() {
        for (BoneConstraintHolder constraint : constraints) {
            if (constraint.makeObject() instanceof LimitRotationConstraint limit) return limit;
        }

        return null;
    }

    public List<BoneConstraint> makeObject() {
        return constraints.stream().map(BoneConstraintHolder::makeObject).toList();
    }
}
