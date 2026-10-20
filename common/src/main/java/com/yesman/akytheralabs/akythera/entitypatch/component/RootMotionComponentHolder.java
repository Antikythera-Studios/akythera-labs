package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.impl.RootMotionComponent;
import com.yesman.akythera.core.blueprint.RootMotionComponentBlueprint;

/// Editor holder for an `akythera:root_motion` ([RootMotionComponentBlueprint]) — moves the entity by the root
/// motion of the montages its armature plays.
///
/// Its only authored state is the switch every component carries, toggled from the Components tree, so
/// this holder shows no Details rows. Not a [RootAttachableComponent] — it follows the armature it is attached under.
public final class RootMotionComponentHolder extends ComponentDataHolder<RootMotionComponentBlueprint> {
    public static final MapCodec<RootMotionComponentHolder> EDITOR_CODEC =
        MapCodec.unit(RootMotionComponentHolder::new);

    public static RootMotionComponentHolder createDefault() {
        return new RootMotionComponentHolder();
    }

    @Override
    public void readFrom(RootMotionComponentBlueprint object) {
    }

    @Override
    public RootMotionComponentBlueprint makeObject() {
        return new RootMotionComponentBlueprint(isEnabled());
    }

    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return RootMotionComponent.class;
    }

    // ***************************************************************
    // Source export (File > Export > As Source Code)
    // ***************************************************************

    @Override
    public String sourceTemplate() {
        return "components/root_motion.ftl";
    }
}
