package com.yesman.akytheralabs.akythera.physics.ik;

import com.yesman.akythera.core.animation.ik.AkytheraIKSolverType;
import com.yesman.akythera.core.animation.ik.FabrikIKSolver;
import com.yesman.akythera.core.animation.ik.IKSolver;
import com.yesman.akythera.core.animation.ik.IKSolverType;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;

/// Editable twin of [FabrikIKSolver].
public final class FabrikSolverHolder extends IKSolverSettingsHolder {
    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_ITERATIONS, order = 0, min = 1.0F, max = 100.0F)
    private int iterations = FabrikIKSolver.DEFAULT_ITERATIONS;

    @Override
    public IKSolverType type() {
        return AkytheraIKSolverType.FABRIK;
    }

    @Override
    public void readFrom(final IKSolver solver) {
        iterations = ((FabrikIKSolver) solver).iterations();
    }

    @Override
    public FabrikIKSolver makeObject() {
        return new FabrikIKSolver(iterations);
    }
}
