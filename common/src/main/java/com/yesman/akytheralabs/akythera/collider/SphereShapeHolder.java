package com.yesman.akytheralabs.akythera.collider;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.AkytheraColliderType;
import com.yesman.akythera.core.math.physics.collider.Collider;
import com.yesman.akythera.core.math.physics.collider.ColliderType;
import com.yesman.akythera.core.math.physics.collider.SphereCollider;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;

/// Dimensions of a [SphereCollider].
public final class SphereShapeHolder extends ColliderShapeHolder {
    public static final MapCodec<SphereShapeHolder> EDITOR_CODEC =
        Codec.DOUBLE.fieldOf("radius").xmap(SphereShapeHolder::new, SphereShapeHolder::getRadius);

    @EditorEditable(label = LangKeys.GUI_COLLIDER_RADIUS, order = 0, min = 0.0F)
    private double radius = 0.5D;

    public SphereShapeHolder() {
    }

    public SphereShapeHolder(final double radius) {
        this.radius = radius;
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(final double radius) {
        this.radius = radius;
    }

    @Override
    public ColliderType type() {
        return AkytheraColliderType.SPHERE;
    }

    @Override
    public void readFrom(final Collider collider) {
        if (collider instanceof SphereCollider sphere) radius = sphere.getRadius();
    }

    @Override
    public SphereCollider makeCollider(
        final Transform pivotTransform,
        final TransformInheritPolicy transformInheritPolicy,
        final boolean enabled
    ) {
        return new SphereCollider(pivotTransform, transformInheritPolicy, radius, enabled);
    }

    @Override
    public String sourceType() {
        return "SphereCollider";
    }

    @Override
    public String sourceArguments() {
        return radius + "D";
    }
}
