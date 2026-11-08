package com.yesman.akytheralabs.akythera.physics.ik;

import com.yesman.akythera.core.animation.ik.AkytheraIKSolverType;
import com.yesman.akythera.core.animation.ik.IKSolver;
import com.yesman.akythera.core.animation.ik.IKSolverType;
import com.yesman.akythera.core.animation.ik.TwoBoneIKSolver;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;

/// Editable twin of [TwoBoneIKSolver].
public final class TwoBoneSolverHolder extends IKSolverSettingsHolder {
    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_STRAIGHT_BEND, order = 0)
    private TwoBoneIKSolver.StraightBend straightBend = TwoBoneIKSolver.DEFAULT.straightBend();

    @Override
    public IKSolverType type() {
        return AkytheraIKSolverType.TWO_BONE;
    }

    @Override
    public void readFrom(final IKSolver solver) {
        straightBend = ((TwoBoneIKSolver) solver).straightBend();
    }

    @Override
    public TwoBoneIKSolver makeObject() {
        return new TwoBoneIKSolver(straightBend);
    }
}
