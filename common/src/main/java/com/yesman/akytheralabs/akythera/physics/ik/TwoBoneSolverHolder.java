package com.yesman.akytheralabs.akythera.physics.ik;

import com.yesman.akythera.core.animation.ik.AkytheraIKSolverType;
import com.yesman.akythera.core.animation.ik.IKSolver;
import com.yesman.akythera.core.animation.ik.IKSolverType;
import com.yesman.akythera.core.animation.ik.TwoBoneIKSolver;

/// Editable twin of [TwoBoneIKSolver], which has no settings.
public final class TwoBoneSolverHolder extends IKSolverSettingsHolder {
    @Override
    public IKSolverType type() {
        return AkytheraIKSolverType.TWO_BONE;
    }

    @Override
    public void readFrom(final IKSolver solver) {
    }

    @Override
    public TwoBoneIKSolver makeObject() {
        return TwoBoneIKSolver.INSTANCE;
    }
}
