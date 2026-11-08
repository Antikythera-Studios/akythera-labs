package com.yesman.akytheralabs.akythera.physics.ik;

import com.yesman.akythera.core.asset.physics.ArmaturePhysicsData;
import com.yesman.akytheralabs.akythera.physics.ArmaturePhysicsDataHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;

import java.util.ArrayList;
import java.util.List;

/// Editable twin of [ArmaturePhysicsData.BodyTurn].
public final class BodyTurnHolder {
    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_BODY_TURN_ENABLED, order = 0)
    private boolean enabled;

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_TURN_TIME, order = 1, min = 0.01F)
    private float turnTime;

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_MAX_TURN_SPEED, order = 2, min = 1.0F)
    private float maxTurnSpeed;

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_MAX_STEP_TURN_SPEED, order = 3, min = 1.0F)
    private float maxStepTurnSpeed;

    @EditorEditable(
        label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_STEADY_BONES, order = 4, candidates = "bones", searchable = true, defaultElement = "newBone"
    )
    private final List<String> steadyBones = new ArrayList<>();

    public BodyTurnHolder(final ArmaturePhysicsData.BodyTurn bodyTurn) {
        enabled = bodyTurn.enabled();
        turnTime = bodyTurn.turnTime();
        maxTurnSpeed = bodyTurn.maxTurnSpeed();
        maxStepTurnSpeed = bodyTurn.maxStepTurnSpeed();
        steadyBones.addAll(bodyTurn.steadyBones());
    }

    private static List<String> bones(final ArmaturePhysicsDataHolder holder) {
        return holder.boneNames();
    }

    private static String newBone() {
        return "";
    }

    /// The settings, without the steady bone rows left unpicked.
    public ArmaturePhysicsData.BodyTurn makeObject() {
        List<String> picked = steadyBones.stream().filter(bone -> !bone.isBlank()).toList();

        return new ArmaturePhysicsData.BodyTurn(enabled, turnTime, maxTurnSpeed, maxStepTurnSpeed, picked);
    }
}
