package com.yesman.akytheralabs.akythera.physics.ik;

import com.yesman.akythera.core.asset.physics.ArmaturePhysicsData;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;

/// Editable twin of [ArmaturePhysicsData.RootFitting].
public final class RootFittingHolder {
    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_ROOT_FITTING_ENABLED, order = 0)
    private boolean enabled;

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_MAX_DROP, order = 1, min = 0.0F)
    private float maxDrop;

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_MAX_PITCH, order = 2, min = 0.0F, max = 90.0F)
    private float maxPitch;

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_MAX_ROLL, order = 3, min = 0.0F, max = 90.0F)
    private float maxRoll;

    public RootFittingHolder(final ArmaturePhysicsData.RootFitting fitting) {
        enabled = fitting.enabled();
        maxDrop = fitting.maxDrop();
        maxPitch = fitting.maxPitch();
        maxRoll = fitting.maxRoll();
    }

    public ArmaturePhysicsData.RootFitting makeObject() {
        return new ArmaturePhysicsData.RootFitting(enabled, maxDrop, maxPitch, maxRoll);
    }
}
