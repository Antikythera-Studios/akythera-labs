package com.yesman.akytheralabs.dataholder;

import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;

/// Placeholder for a patch component whose `type` id is unknown / unregistered (a removed compat
/// plugin, a renamed type). Carries only the failure + retained raw JSON (via
/// [AbstractDataHolder]), so the rest of the blueprint stays editable and a save re-emits the
/// component untouched. Blueprint holders must skip it when lowering (`makeObject`) and exporting
/// (`getDTO`) — calling [#makeObject] is a bug. (A known type with a malformed body never lands
/// here — [AbstractDataHolder#guardedComponent] gives it a defaults fallback instead.)
///
/// Not a [com.yesman.akytheralabs.akythera.entitypatch.component.ComponentAttachableHolder]: the
/// raw JSON it re-emits already carries whatever was attached under it, and the editor cannot know
/// what the missing type would accept, so the whole component stays an opaque leaf in the tree.
/// It names no source template either, so the source export writes a TODO marker where this
/// component's constructor expression would go.
public final class FailedComponentHolder extends ComponentDataHolder<ComponentBlueprint<?, ?>> {
    @Override
    public void readFrom(ComponentBlueprint<?, ?> object) {}

    @Override
    public ComponentBlueprint<?, ?> makeObject() {
        throw new IllegalStateException("A failed component has no runtime object", getDeserializationFailureException());
    }

    /// The missing type is not registered in this build, so the component it would have built is
    /// unknown — [PatchComponent] itself is as precise as this gets.
    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return PatchComponent.class;
    }
}
