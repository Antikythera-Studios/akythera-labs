package com.yesman.akytheralabs.akythera.collider;

import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.AkytheraColliderType;
import com.yesman.akythera.core.math.physics.collider.Collider;
import com.yesman.akythera.core.math.physics.collider.ColliderType;
import com.yesman.akythera.core.math.physics.collider.ConeCollider;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;

/// Dimensions of a [ConeCollider]. The base disc sits at `-height / 2` and the apex at `+height / 2`
/// along the pivot's local +Y.
public final class ConeShapeHolder extends ColliderShapeHolder {
    @EditorEditable(label = LangKeys.GUI_COLLIDER_RADIUS, order = 0, min = 0.0F)
    private double radius = 0.5D;

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
        return AkytheraColliderType.CONE;
    }

    @Override
    public void readFrom(final Collider collider) {
        if (collider instanceof ConeCollider cone) {
            radius = cone.getRadius();
            height = cone.getHeight();
        }
    }

    @Override
    public ConeCollider makeCollider(final Transform pivotTransform) {
        return new ConeCollider(pivotTransform, radius, height);
    }

    @Override
    public String sourceType() {
        return "ConeCollider";
    }

    @Override
    public String sourceArguments() {
        return radius + "D, " + height + "D";
    }
}
