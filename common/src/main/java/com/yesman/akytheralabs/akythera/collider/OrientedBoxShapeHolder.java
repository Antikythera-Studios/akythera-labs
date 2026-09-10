package com.yesman.akytheralabs.akythera.collider;

import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.AkytheraColliderType;
import com.yesman.akythera.core.math.physics.collider.Collider;
import com.yesman.akythera.core.math.physics.collider.ColliderType;
import com.yesman.akythera.core.math.physics.collider.OrientedBoxCollider;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.world.phys.Vec3;

/// Dimensions of an [OrientedBoxCollider].
public final class OrientedBoxShapeHolder extends ColliderShapeHolder {
    /// Full length along each of the box's own axes, before the pivot's scale.
    @EditorEditable(label = LangKeys.GUI_COLLIDER_LENGTH, order = 0)
    private Vec3 length = new Vec3(1.0D, 1.0D, 1.0D);

    public Vec3 getLength() {
        return length;
    }

    public void setLength(final Vec3 length) {
        this.length = length;
    }

    @Override
    public ColliderType type() {
        return AkytheraColliderType.ORIENTED_BOX;
    }

    @Override
    public void readFrom(final Collider collider) {
        if (collider instanceof OrientedBoxCollider box) length = box.getLength();
    }

    @Override
    public OrientedBoxCollider makeCollider(final Transform pivotTransform) {
        return new OrientedBoxCollider(pivotTransform, length);
    }

    @Override
    public String sourceType() {
        return "OrientedBoxCollider";
    }

    @Override
    public String sourceArguments() {
        return "new Vec3(" + length.x() + "D, " + length.y() + "D, " + length.z() + "D)";
    }
}
