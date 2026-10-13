package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.impl.RootMotionComponent;
import com.yesman.akythera.api.world.patch.entity.component.EntityMovementComponent;
import com.yesman.akythera.core.blueprint.EntityMovementComponentBlueprint;

/// Editor holder for an `akythera:entity_movement` ([EntityMovementComponentBlueprint]) — moves and turns
/// the entity, which is what a [RootMotionComponent] moves it through.
///
/// Its only authored state is the switch every component carries, toggled from the Components tree, so
/// this holder shows no Details rows.
public final class EntityMovementComponentHolder extends ComponentDataHolder<EntityMovementComponentBlueprint>
    implements RootAttachableComponent
{
    public static final MapCodec<EntityMovementComponentHolder> EDITOR_CODEC =
        MapCodec.unit(EntityMovementComponentHolder::new);

    public static EntityMovementComponentHolder createDefault() {
        return new EntityMovementComponentHolder();
    }

    @Override
    public void readFrom(EntityMovementComponentBlueprint object) {
    }

    @Override
    public EntityMovementComponentBlueprint makeObject() {
        return new EntityMovementComponentBlueprint(isEnabled());
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
}
