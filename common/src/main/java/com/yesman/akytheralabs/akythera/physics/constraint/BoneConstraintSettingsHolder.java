package com.yesman.akytheralabs.akythera.physics.constraint;

import com.yesman.akythera.core.asset.physics.constraint.BoneConstraint;
import com.yesman.akythera.core.asset.physics.constraint.BoneConstraintType;
import com.yesman.akytheralabs.editor.EditorEditable;

/// Editor twin of one [BoneConstraintType]'s settings, as [EditorEditable] fields.
public abstract class BoneConstraintSettingsHolder {
    /// The type this holder authors, the one it is registered under in [BoneConstraintSettingsHolders].
    public abstract BoneConstraintType type();

    /// Overwrites the settings from `constraint`, which is of this holder's type.
    public abstract void readFrom(final BoneConstraint constraint);

    public abstract BoneConstraint makeObject();
}
