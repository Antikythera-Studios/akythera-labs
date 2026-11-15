package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.mojang.serialization.Codec;
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
/// Besides the switch every component carries, toggled from the Components tree, it holds how the body turns,
/// whether the entity can swim up, and whether a player may sprint in any direction, needs a new press for each jump and
/// floats at the water's surface.
public final class EntityMovementComponentHolder extends ComponentDataHolder<EntityMovementComponentBlueprint>
    implements RootAttachableComponent
{
    public static final MapCodec<EntityMovementComponentHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            EntityMovementComponent.BodyRotation.CODEC.optionalFieldOf("body_rotation", EntityMovementComponent.BodyRotation.VANILLA)
                .forGetter(h -> h.bodyRotation),
            Codec.BOOL.optionalFieldOf("can_float_in_water", true).forGetter(h -> h.canFloatInWater),
            Codec.BOOL.optionalFieldOf("omnidirectional_sprint", false).forGetter(h -> h.omnidirectionalSprint),
            Codec.BOOL.optionalFieldOf("jump_requires_press", false).forGetter(h -> h.jumpRequiresPress),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("surface_float", 0.0F).forGetter(h -> h.surfaceFloat)
        ).apply(instance, (bodyRotation, canFloatInWater, omnidirectionalSprint, jumpRequiresPress, surfaceFloat) -> {
            EntityMovementComponentHolder h = new EntityMovementComponentHolder();
            h.bodyRotation = bodyRotation;
            h.canFloatInWater = canFloatInWater;
            h.omnidirectionalSprint = omnidirectionalSprint;
            h.jumpRequiresPress = jumpRequiresPress;
            h.surfaceFloat = surfaceFloat;
            return h;
        })
    );

    /// How the body yaw of a living owner turns.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_BODY_ROTATION, order = 0)
    public EntityMovementComponent.BodyRotation bodyRotation = EntityMovementComponent.BodyRotation.VANILLA;

    /// Whether the entity can swim up in water at all.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_CAN_FLOAT_IN_WATER, order = 1)
    public boolean canFloatInWater = true;

    /// Whether a player owner may sprint in any direction it moves, not only forward.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_OMNIDIRECTIONAL_SPRINT, order = 2)
    public boolean omnidirectionalSprint;

    /// Whether each jump from the ground needs the jump key pressed again since the last jump.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_JUMP_REQUIRES_PRESS, order = 3)
    public boolean jumpRequiresPress;

    /// Part of its height a floating owner keeps under the water, 0 for vanilla swimming.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_SURFACE_FLOAT, order = 4, min = 0.0F, max = 1.0F)
    public float surfaceFloat;

    public static EntityMovementComponentHolder createDefault() {
        return new EntityMovementComponentHolder();
    }

    @Override
    public void readFrom(EntityMovementComponentBlueprint object) {
        bodyRotation = object.bodyRotation();
        canFloatInWater = object.canFloatInWater();
        omnidirectionalSprint = object.omnidirectionalSprint();
        jumpRequiresPress = object.jumpRequiresPress();
        surfaceFloat = object.surfaceFloat();
    }

    @Override
    public EntityMovementComponentBlueprint makeObject() {
        return new EntityMovementComponentBlueprint(bodyRotation, canFloatInWater, omnidirectionalSprint, jumpRequiresPress, surfaceFloat, isEnabled());
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
        return Map.of(
            "body_rotation", bodyRotation.name(), "can_float_in_water", canFloatInWater, "omnidirectional_sprint", omnidirectionalSprint,
            "jump_requires_press", jumpRequiresPress, "surface_float", surfaceFloat
        );
    }
}
