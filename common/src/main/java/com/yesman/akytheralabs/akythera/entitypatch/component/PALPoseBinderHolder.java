package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.client.api.component.impl.PALPoseBinder;

/// Editor holder for an `akythera:pal_pose_binder` ([PALPoseBinder]) — binds the
/// entity's own vanilla model to its armature's pose through Player Animation Library.
///
/// The binder's only authored state is the switch every component carries, toggled from the
/// Components tree, so this holder shows no Details rows. Not a [RootAttachableComponent] — it names
/// the armature whose pose it binds.
public final class PALPoseBinderHolder extends ComponentDataHolder<PALPoseBinder> {
    public static final MapCodec<PALPoseBinderHolder> EDITOR_CODEC =
        MapCodec.unit(PALPoseBinderHolder::new);

    public static PALPoseBinderHolder createDefault() {
        return new PALPoseBinderHolder();
    }

    @Override
    public void readFrom(PALPoseBinder object) {
    }

    @Override
    public PALPoseBinder makeObject() {
        return new PALPoseBinder(isEnabled());
    }

    /// A [PALPoseBinder] is its own blueprint — it builds itself.
    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return PALPoseBinder.class;
    }

    // ***************************************************************
    // Source export (File > Export > As Source Code)
    // ***************************************************************

    @Override
    public String sourceTemplate() {
        return "components/pal_pose_binder.ftl";
    }
}
