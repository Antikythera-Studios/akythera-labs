package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.AkytheraScriptFunctions;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.world.ability.AbilityInstance;
import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.core.blueprint.AbilityCueBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.PlayAbilityCueNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.PlayAbilityCueScriptNodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;

import java.util.List;

/// Widget for a [PlayAbilityCueNodeData] or a [PlayAbilityCueScriptNodeData]: a call playing one data pack
/// cue or one Java cue, titled by its id.
public final class PlayAbilityCueNodeWidget extends AbstractNodeWidget {
    private static final int TITLE_COLOR = 0xFF1F3A5A;

    private final NodeData data;

    /// Plays the data pack cue `cue`, the target's shared live holder.
    public PlayAbilityCueNodeWidget(Font font, Holder<AbilityCueBlueprint> cue, int graphX, int graphY) {
        this(
            font,
            AkytheraScriptFunctions.PLAY_ABILITY_CUE.value().metadata().label() + ": " + EditorHolderCodecs.entryId(cue),
            new PlayAbilityCueNodeData(cue),
            graphX,
            graphY
        );
    }

    /// Plays the Java cue whose script class `script` registers.
    public PlayAbilityCueNodeWidget(Font font, ScriptType script, int graphX, int graphY) {
        this(
            font,
            AkytheraScriptFunctions.PLAY_ABILITY_CUE_SCRIPT.value().metadata().label() + ": "
                + AkytheraRegistries.Frozen.SCRIPT_TYPE.getKey(script),
            new PlayAbilityCueScriptNodeData(script),
            graphX,
            graphY
        );
    }

    private PlayAbilityCueNodeWidget(Font font, String title, NodeData data, int graphX, int graphY) {
        super(font, Component.literal(title), graphX, graphY);

        this.data = data;

        setTitleColor(TITLE_COLOR);

        addInput(new Pin("", NodeColors.EXEC, true));
        addInput(new Pin("Target", NodeColors.forType(AbilitySystemComponent.class), false, AbilitySystemComponent.class));
        addInput(new Pin("Source", NodeColors.forType(AbilityInstance.class), false, AbilityInstance.class));
        addInput(new Pin("With State", NodeColors.forType(Boolean.class), false, Boolean.class));
        addInput(new Pin("Ends With Ability", NodeColors.forType(Boolean.class), false, Boolean.class));
        addOutput(new Pin("", NodeColors.EXEC, true));

        relayout();
    }

    /// A node bound to a data pack cue that resolves nowhere is red on sight.
    @Override
    public List<Component> intrinsicErrors() {
        return data instanceof PlayAbilityCueNodeData(Holder<AbilityCueBlueprint> cue) && !cue.isBound()
            ? List.of(Component.translatable(
                LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VALIDATION_MISSING_ASSET, EditorHolderCodecs.entryId(cue).toString()))
            : List.of();
    }

    @Override
    public NodeData toData() {
        return data;
    }
}
