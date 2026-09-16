package com.yesman.akytheralabs.akythera.ability;

import com.mojang.serialization.Codec;
import com.yesman.akythera.api.input.InputAction;
import com.yesman.akythera.api.input.TriggeringType;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.editor.EditorEditable;

import java.util.Optional;

/// Modifiable data holder for [InputAction]. The fields are the editor's properties panel; the
/// record is rebuilt from them on [#makeObject].
public final class InputActionHolder extends AbstractDataHolder<InputAction> {
    public static final Codec<InputActionHolder> EDITOR_CODEC =
        InputAction.CODEC.xmap(
            action -> {
                InputActionHolder holder = new InputActionHolder();
                holder.readFrom(action);

                return holder;
            },
            InputActionHolder::makeObject
        );

    /// The registered name of the key this action reads. A name no mod registered leaves the ability
    /// unbound at runtime.
    @EditorEditable
    private String keyMapping = "";

    @EditorEditable
    private TriggeringType triggeringType = TriggeringType.PRESS;

    /// Ticks a held release is judged against. Zero means the action names none.
    @EditorEditable
    private int threshold;

    /// Order among the actions bound to one key and triggering type. Higher runs first.
    @EditorEditable
    private int priority;

    @Override
    public void readFrom(final InputAction object) {
        this.keyMapping = object.keyMapping();
        this.triggeringType = object.triggeringType();
        this.threshold = object.thresholdTicks();
        this.priority = object.priority();
    }

    @Override
    public InputAction makeObject() {
        // A threshold only belongs on a held type, and the record refuses one anywhere else.
        return new InputAction(
            keyMapping,
            triggeringType,
            triggeringType.isHeld() && threshold > 0 ? Optional.of(threshold) : Optional.empty(),
            priority
        );
    }

    public String getKeyMapping() {
        return keyMapping;
    }

    public TriggeringType getTriggeringType() {
        return triggeringType;
    }

    public int getThreshold() {
        return threshold;
    }

    public int getPriority() {
        return priority;
    }
}
