package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.impl.RootMotionComponent;
import com.yesman.akythera.api.world.patch.entity.component.EntityMovementComponent;
import com.yesman.akythera.core.blueprint.EntityMovementComponentBlueprint;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;

import java.util.Map;

/// Editor holder for an `akythera:entity_movement` ([EntityMovementComponentBlueprint]) — moves and turns
/// the entity, which is what a [RootMotionComponent] moves it through.
///
/// Besides the switch every component carries, toggled from the Components tree, it holds how the body turns.
public final class EntityMovementComponentHolder extends ComponentDataHolder<EntityMovementComponentBlueprint>
    implements RootAttachableComponent
{
    public static final MapCodec<EntityMovementComponentHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            EntityMovementComponent.BodyRotation.CODEC.optionalFieldOf("body_rotation", EntityMovementComponent.BodyRotation.VANILLA)
                .forGetter(h -> h.bodyRotation)
        ).apply(instance, bodyRotation -> {
            EntityMovementComponentHolder h = new EntityMovementComponentHolder();
            h.bodyRotation = bodyRotation;
            return h;
        })
    );

    /// How the body yaw of a living owner turns.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_BODY_ROTATION, order = 0)
    public EntityMovementComponent.BodyRotation bodyRotation = EntityMovementComponent.BodyRotation.VANILLA;

    public static EntityMovementComponentHolder createDefault() {
        return new EntityMovementComponentHolder();
    }

    @Override
    public void readFrom(EntityMovementComponentBlueprint object) {
        bodyRotation = object.bodyRotation();
    }

    @Override
    public EntityMovementComponentBlueprint makeObject() {
        return new EntityMovementComponentBlueprint(bodyRotation, isEnabled());
    }

    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return EntityMovementComponent.class;
    }

    // ***************************************************************
    // Source export (File > Export > As Source Code)
    // ***************************************************************

    @Override
    public String sourceTemplate() {
        return "components/entity_movement.ftl";
    }

    @Override
    public Map<String, Object> getDTO() {
        return Map.of("body_rotation", bodyRotation.name());
    }
}
