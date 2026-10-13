package com.yesman.akytheralabs.akythera.physics;

import com.yesman.akythera.client.core.asset.physics.ClothSimulationParameters;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;

/// Editable twin of [ClothSimulationParameters].
public final class ClothSimulationParametersHolder {
    @EditorEditable(label = LangKeys.GUI_CLOTH_PARAMETERS_SUBSTEPS, order = 0, min = 1.0F, max = 32.0F)
    private int substeps;

    @EditorEditable(label = LangKeys.GUI_CLOTH_PARAMETERS_GRAVITY_SCALE, order = 1, min = -4.0F, max = 4.0F)
    private float gravityScale;

    @EditorEditable(label = LangKeys.GUI_CLOTH_PARAMETERS_DAMPING, order = 2, min = 0.0F, max = 10.0F)
    private float damping;

    @EditorEditable(label = LangKeys.GUI_CLOTH_PARAMETERS_STRETCH_COMPLIANCE, order = 3, min = 0.0F, max = 0.01F)
    private float stretchCompliance;

    @EditorEditable(label = LangKeys.GUI_CLOTH_PARAMETERS_BEND_COMPLIANCE, order = 4, min = 0.0F, max = 0.01F)
    private float bendCompliance;

    @EditorEditable(label = LangKeys.GUI_CLOTH_PARAMETERS_TETHER_SCALE, order = 5, min = 1.0F, max = 4.0F)
    private float tetherScale;

    @EditorEditable(label = LangKeys.GUI_CLOTH_PARAMETERS_MAX_DISTANCE, order = 6, min = 0.0F, max = 4.0F)
    private float maxDistance;

    @EditorEditable(label = LangKeys.GUI_CLOTH_PARAMETERS_COLLISION_THICKNESS, order = 7, min = 0.0F, max = 0.25F)
    private float collisionThickness;

    @EditorEditable(label = LangKeys.GUI_CLOTH_PARAMETERS_FRICTION, order = 8, min = 0.0F, max = 1.0F)
    private float friction;

    @EditorEditable(label = LangKeys.GUI_CLOTH_PARAMETERS_SELF_COLLISION, order = 9)
    private boolean selfCollision;

    @EditorEditable(label = LangKeys.GUI_CLOTH_PARAMETERS_SELF_COLLISION_THICKNESS, order = 10, min = 0.0F, max = 0.25F)
    private float selfCollisionThickness;

    @EditorEditable(label = LangKeys.GUI_CLOTH_PARAMETERS_LINEAR_INERTIA_SCALE, order = 11, min = 0.0F, max = 1.0F)
    private float linearInertiaScale;

    @EditorEditable(label = LangKeys.GUI_CLOTH_PARAMETERS_ANGULAR_INERTIA_SCALE, order = 12, min = 0.0F, max = 1.0F)
    private float angularInertiaScale;

    public ClothSimulationParametersHolder(final ClothSimulationParameters parameters) {
        substeps = parameters.substeps();
        gravityScale = parameters.gravityScale();
        damping = parameters.damping();
        stretchCompliance = parameters.stretchCompliance();
        bendCompliance = parameters.bendCompliance();
        tetherScale = parameters.tetherScale();
        maxDistance = parameters.maxDistance();
        collisionThickness = parameters.collisionThickness();
        friction = parameters.friction();
        selfCollision = parameters.selfCollision();
        selfCollisionThickness = parameters.selfCollisionThickness();
        linearInertiaScale = parameters.linearInertiaScale();
        angularInertiaScale = parameters.angularInertiaScale();
    }

    public ClothSimulationParameters makeObject() {
        return new ClothSimulationParameters(
            substeps, gravityScale, damping, stretchCompliance, bendCompliance, tetherScale, maxDistance,
            collisionThickness, friction, selfCollision, selfCollisionThickness, linearInertiaScale, angularInertiaScale
        );
    }
}
