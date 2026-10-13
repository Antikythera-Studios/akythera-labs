package com.yesman.akytheralabs.akythera.physics;

import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.core.asset.physics.ArmaturePhysicsData;
import com.yesman.akythera.core.math.physics.collider.Collider;
import com.yesman.akytheralabs.akythera.collider.CapsuleShapeHolder;
import com.yesman.akytheralabs.akythera.collider.ColliderComponentHolder;
import com.yesman.akytheralabs.dataholder.TransformHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;

import java.util.ArrayList;
import java.util.List;

/// Editable twin of [ArmaturePhysicsData.BoneBody]. Every collider it holds is restricted to
/// [ArmaturePhysicsData#BODY_SHAPES], so the shape combo of a body row offers those alone.
public final class BoneBodyHolder {
    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_COLLIDERS, order = 0, defaultElement = "createCollider")
    private final List<ColliderComponentHolder> colliders = new ArrayList<>();

    public BoneBodyHolder(final ArmaturePhysicsData.BoneBody body) {
        for (Collider collider : body.colliders()) {
            ColliderComponentHolder holder = createCollider();
            holder.readFrom(collider);

            colliders.add(holder);
        }
    }

    /// A new body collider: a capsule on the bone, authorable as any shape a body may carry.
    public static ColliderComponentHolder createCollider() {
        return new ColliderComponentHolder(
            TransformHolder.identity(),
            TransformInheritPolicy.LOC_ROT_SCA,
            new CapsuleShapeHolder(),
            ArmaturePhysicsData.BODY_SHAPES
        );
    }

    public List<ColliderComponentHolder> getColliders() {
        return colliders;
    }

    public ArmaturePhysicsData.BoneBody makeObject() {
        return new ArmaturePhysicsData.BoneBody(colliders.stream().map(ColliderComponentHolder::makeObject).toList());
    }
}
