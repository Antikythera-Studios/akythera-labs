package com.yesman.akytheralabs.akythera.physics;

import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.core.asset.physics.ArmaturePhysicsData;
import com.yesman.akythera.core.math.physics.collider.Collider;
import com.yesman.akytheralabs.akythera.collider.CapsuleShapeHolder;
import com.yesman.akytheralabs.akythera.collider.ColliderComponentHolder;
import com.yesman.akytheralabs.dataholder.TransformHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/// Editable twin of [ArmaturePhysicsData.BoneBody]. Every collider it holds is restricted to
/// [ArmaturePhysicsData#BODY_SHAPES], so the shape combo of a body row offers those alone.
public final class BoneBodyHolder {
    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_MASS, order = 0, min = 0.001F)
    private float mass;

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_LINEAR_DAMPING, order = 1, min = 0.0F, max = 10.0F)
    private float linearDamping;

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_ANGULAR_DAMPING, order = 2, min = 0.0F, max = 10.0F)
    private float angularDamping;

    /// Where the center of mass sits from the colliders' own, in the bone's space, in blocks.
    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_CENTER_OF_MASS_OFFSET, order = 3)
    private Vec3 centerOfMassOffset;

    /// Multiplier on the fluid the colliders push aside.
    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_BUOYANCY_SCALE, order = 4, min = 0.0F, max = 10.0F)
    private float buoyancyScale;

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_COLLIDERS, order = 5, defaultElement = "createCollider")
    private final List<ColliderComponentHolder> colliders = new ArrayList<>();

    public BoneBodyHolder(final ArmaturePhysicsData.BoneBody body) {
        mass = body.mass();
        linearDamping = body.linearDamping();
        angularDamping = body.angularDamping();
        centerOfMassOffset = body.centerOfMassOffset();
        buoyancyScale = body.buoyancyScale();

        for (Collider collider : body.colliders()) {
            ColliderComponentHolder holder = createCollider();
            holder.readFrom(collider);

            colliders.add(holder);
        }
    }

    /// A body with no collider and the default mass and damping.
    public static BoneBodyHolder empty() {
        return new BoneBodyHolder(new ArmaturePhysicsData.BoneBody(
            List.of(),
            ArmaturePhysicsData.BoneBody.DEFAULT_MASS,
            ArmaturePhysicsData.BoneBody.DEFAULT_LINEAR_DAMPING,
            ArmaturePhysicsData.BoneBody.DEFAULT_ANGULAR_DAMPING,
            Vec3.ZERO,
            1.0F
        ));
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
        return new ArmaturePhysicsData.BoneBody(
            colliders.stream().map(ColliderComponentHolder::makeObject).toList(),
            mass,
            linearDamping,
            angularDamping,
            centerOfMassOffset,
            buoyancyScale
        );
    }
}
