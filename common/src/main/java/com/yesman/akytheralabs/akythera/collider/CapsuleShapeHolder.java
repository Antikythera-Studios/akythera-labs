package com.yesman.akytheralabs.akythera.collider;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.TransformInheritPolicy;
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
    public static final MapCodec<CapsuleShapeHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            Codec.DOUBLE.fieldOf("radius").forGetter(CapsuleShapeHolder::getRadius),
            Codec.DOUBLE.fieldOf("height").forGetter(CapsuleShapeHolder::getHeight)
        ).apply(instance, CapsuleShapeHolder::new)
    );

    @EditorEditable(label = LangKeys.GUI_COLLIDER_RADIUS, order = 0, min = 0.0F)
    private double radius = 0.25D;

    @EditorEditable(label = LangKeys.GUI_COLLIDER_HEIGHT, order = 1, min = 0.0F)
    private double height = 1.0D;

    public CapsuleShapeHolder() {
    }

    public CapsuleShapeHolder(final double radius, final double height) {
        this.radius = radius;
        this.height = height;
    }

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
    public CapsuleCollider makeCollider(
        final Transform pivotTransform,
        final TransformInheritPolicy transformInheritPolicy,
        final boolean enabled
    ) {
        return new CapsuleCollider(pivotTransform, transformInheritPolicy, radius, height, enabled);
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
