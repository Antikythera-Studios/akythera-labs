package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.client.api.component.impl.OverlayRendererBlueprint;
import com.yesman.akythera.client.api.component.impl.OverlayRendererComponent;

/// Editor holder for an `akythera:overlay_renderer` ([OverlayRendererBlueprint]) — keeps the overlay particles drawn
/// over the entity's model.
///
/// Its only authored state is the switch every component carries, toggled from the Components tree, so this holder
/// shows no Details rows.
public final class OverlayRendererHolder extends ComponentDataHolder<OverlayRendererBlueprint> implements RootAttachableComponent {
    public static final MapCodec<OverlayRendererHolder> EDITOR_CODEC =
        MapCodec.unit(OverlayRendererHolder::new);

    public static OverlayRendererHolder createDefault() {
        return new OverlayRendererHolder();
    }

    @Override
    public void readFrom(OverlayRendererBlueprint object) {
    }

    @Override
    public OverlayRendererBlueprint makeObject() {
        return new OverlayRendererBlueprint(isEnabled());
    }

    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return OverlayRendererComponent.class;
    }

    // ***************************************************************
    // Source export (File > Export > As Source Code)
    // ***************************************************************

    @Override
    public String sourceTemplate() {
        return "components/overlay_renderer.ftl";
    }
}
