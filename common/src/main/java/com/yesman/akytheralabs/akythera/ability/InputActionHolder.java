package com.yesman.akytheralabs.akythera.ability;

import com.mojang.serialization.Codec;
import com.yesman.akythera.api.input.InputAction;
import com.yesman.akythera.api.input.TriggeringType;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
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
    @EditorEditable(
        label = LangKeys.GUI_INPUT_ACTION_SCREEN_WIDGET_KEY_MAPPING,
        order = 0,
        candidates = "keyMappingCandidates",
        candidateLabel = "keyMappingLabel",
        candidateCategory = "keyMappingCategory",
        searchable = true
    )
    private String keyMapping = "";

    @EditorEditable(label = LangKeys.GUI_INPUT_ACTION_SCREEN_WIDGET_TRIGGERING_TYPE, order = 1, rebuildOnWrite = true)
    private TriggeringType triggeringType = TriggeringType.PRESS;

    /// Ticks a held release is judged against. Zero means the action names none.
    @EditorEditable(label = LangKeys.GUI_INPUT_ACTION_SCREEN_WIDGET_THRESHOLD, order = 2, min = 0, visibleWhen = "readsThreshold")
    private int threshold;

    /// Order among the actions bound to one key and triggering type. Higher runs first.
    @EditorEditable(label = LangKeys.GUI_INPUT_ACTION_SCREEN_WIDGET_PRIORITY, order = 3)
    private int priority;

    /// Every key mapping the client registered, by name and grouped by category, see
    /// [EditorEditable#candidates].
    static List<String> keyMappingCandidates() {
        return Arrays.stream(Minecraft.getInstance().options.keyMappings)
            .sorted()
            .map(KeyMapping::getName)
            .toList();
    }

    /// The translated name of a key mapping, see [EditorEditable#candidateLabel].
    static Component keyMappingLabel(String name) {
        return Component.translatable(name);
    }

    /// The category a key mapping is listed under, see [EditorEditable#candidateCategory].
    static Component keyMappingCategory(String name) {
        return KeyMapping.get(name).getCategory().label();
    }

    /// Whether [#triggeringType] reads [#threshold], see [EditorEditable#visibleWhen].
    boolean readsThreshold() {
        return triggeringType.isHeld();
    }

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
