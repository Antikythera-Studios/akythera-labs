package com.yesman.akytheralabs.akythera.collider;

import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.AkytheraColliderType;
import com.yesman.akythera.core.math.physics.collider.CapsuleCollider;
import com.yesman.akythera.core.math.physics.collider.Collider;
import com.yesman.akythera.core.math.physics.collider.ColliderType;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;

/// Dimensions of a [CapsuleCollider]. `height` is the distance between the two hemisphere centres,
/// along the pivot's local +Y.
public final class CapsuleShapeHolder extends ColliderShapeHolder {
    @EditorEditable(label = LangKeys.GUI_COLLIDER_RADIUS, order = 0, min = 0.0F)
    private double radius = 0.25D;

    @EditorEditable(label = LangKeys.GUI_COLLIDER_HEIGHT, order = 1, min = 0.0F)
    private double height = 1.0D;

    public double getRadius() {
        return radius;
    }

    public double getHeight() {
        return height;
    }

    public void setRadius(final double radius) {
        this.radius = radius;
    }

    public void setHeight(final double height) {
        this.height = height;
    }

    @Override
    public ColliderType type() {
        return AkytheraColliderType.CAPSULE;
    }

    @Override
    public void readFrom(final Collider collider) {
        if (collider instanceof CapsuleCollider capsule) {
            radius = capsule.getRadius();
            height = capsule.getHeight();
        }
    }

    @Override
    public CapsuleCollider makeCollider(final Transform pivotTransform) {
        return new CapsuleCollider(pivotTransform, radius, height);
    }

    @Override
    public String sourceType() {
        return "CapsuleCollider";
    }

    @Override
    public String sourceArguments() {
        return radius + "D, " + height + "D";
    }
}
