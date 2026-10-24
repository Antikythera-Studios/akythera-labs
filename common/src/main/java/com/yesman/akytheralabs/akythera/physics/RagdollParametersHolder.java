package com.yesman.akytheralabs.akythera.physics;

import com.yesman.akythera.core.asset.physics.ArmaturePhysicsData;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;

/// Editable twin of [ArmaturePhysicsData.RagdollParameters].
public final class RagdollParametersHolder {
    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_SUBSTEPS, order = 0, min = 1.0F, max = 32.0F)
    private int substeps;

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_GRAVITY_SCALE, order = 1, min = -4.0F, max = 4.0F)
    private float gravityScale;

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_FRICTION, order = 2, min = 0.0F, max = 1.0F)
    private float friction;

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_RESTITUTION, order = 3, min = 0.0F, max = 1.0F)
    private float restitution;

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_SELF_COLLISION, order = 4)
    private boolean selfCollision;

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_SLEEP_SPEED, order = 5, min = 0.0F, max = 1.0F)
    private float sleepSpeed;

    public RagdollParametersHolder(final ArmaturePhysicsData.RagdollParameters parameters) {
        substeps = parameters.substeps();
        gravityScale = parameters.gravityScale();
        friction = parameters.friction();
        restitution = parameters.restitution();
        selfCollision = parameters.selfCollision();
        sleepSpeed = parameters.sleepSpeed();
    }

    public ArmaturePhysicsData.RagdollParameters makeObject() {
        return new ArmaturePhysicsData.RagdollParameters(substeps, gravityScale, friction, restitution, selfCollision, sleepSpeed);
    }
}
