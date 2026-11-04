package com.yesman.akytheralabs.akythera.physics.ik;

import com.yesman.akythera.core.animation.ik.IKSolver;
import com.yesman.akythera.core.animation.ik.IKSolverType;
import com.yesman.akytheralabs.editor.EditorEditable;

/// Editor twin of one [IKSolverType]'s settings, as [EditorEditable] fields.
public abstract class IKSolverSettingsHolder {
    /// The type this holder authors, the one it is registered under in [IKSolverSettingsHolders].
    public abstract IKSolverType type();

    /// Overwrites the settings from `solver`, which is of this holder's type.
    public abstract void readFrom(final IKSolver solver);

    public abstract IKSolver makeObject();
}
