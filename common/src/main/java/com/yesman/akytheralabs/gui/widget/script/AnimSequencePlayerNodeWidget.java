package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.blueprint.script.AnimatorScript;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.node.AnimSequencePlayerNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.List;

/// An Anim Graph pose source bound to a project [AnimationSequence] (UE's "Sequence Player"). No inputs — it
/// plays a fixed sequence — and one [Pose] output. The sequence is chosen by dragging it from the Asset
/// Browser onto the canvas, which spawns this node already bound to that asset's [Identifier].
///
/// Compile rule (see the graph compilers): emit the `ANIM_GRAPH` [ScriptFunction] that wraps
/// [AnimatorScript#instantiateAnimSequence] for `sequenceId`, returning the driver's [Pose].
public final class AnimSequencePlayerNodeWidget extends AbstractNodeWidget {
    /// Red/brown title, matching UE's Sequence Player tint.
    private static final int TITLE_COLOR = 0xFF5A2A2A;

    /// The bound sequence, as the target's shared live holder — the binding follows its rename.
    private Holder<AnimationSequence> sequence;

    public AnimSequencePlayerNodeWidget(Font font, Holder<AnimationSequence> sequence, int graphX, int graphY) {
        super(font, Component.literal(EditorHolderCodecs.entryId(sequence).getPath()), graphX, graphY);

        this.sequence = sequence;

        setTitleColor(TITLE_COLOR);
        addOutput(new Pin("", NodeColors.forType(Pose.class), false, Pose.class));

        relayout();
    }

    /// The bound sequence, for the row this widget describes.
    public Holder<AnimationSequence> sequence() {
        return sequence;
    }

    /// The bound sequence's current id.
    public Identifier sequenceId() {
        return EditorHolderCodecs.entryId(sequence);
    }

    /// Rebinds this player to another sequence — the Details panel's picker (the drag from the Asset
    /// Browser only ever creates a node). The title follows the new asset, as it does on creation.
    public void setSequenceId(Identifier sequenceId) {
        this.sequence = EditorHolderCodecs.resolve(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY, sequenceId);
        setMessage(Component.literal(sequenceId.getPath()));
        relayout();
    }

    /// Whether the bound sequence no longer resolves in the project or the level
    public boolean isBoundAssetMissing() {
        return !sequence.isBound();
    }

    /// A player bound to a sequence that resolves nowhere is red on sight — no compile needed.
    @Override
    public List<Component> intrinsicErrors() {
        return isBoundAssetMissing()
            ? List.of(Component.translatable(
                LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VALIDATION_MISSING_ASSET, sequenceId().toString()))
            : List.of();
    }

    @Override
    public NodeData toData() {
        return new AnimSequencePlayerNodeData(sequence);
    }
}
