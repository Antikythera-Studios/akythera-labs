package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.blueprint.script.AnimatorScript;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/// An Anim Graph pose source bound to a project [com.yesman.akythera.core.animation.playable.AnimationSequence]
/// (UE's "Sequence Player"). No inputs — it plays a fixed sequence — and one [Pose] output. The
/// sequence is chosen by dragging it from the Asset Browser onto the canvas, which spawns this node
/// already bound to that asset's [Identifier].
///
/// Compile rule (see the graph compilers): emit the `ANIM_GRAPH`
/// [com.yesman.akythera.api.data.modularize.ScriptFunction] that wraps
/// [AnimatorScript#instantiateAnimSequence] for
/// `sequenceId`, returning the driver's [Pose].
public final class AnimSequencePlayerNodeWidget extends AbstractNodeWidget {
    /// Red/brown title, matching UE's Sequence Player tint.
    private static final int TITLE_COLOR = 0xFF5A2A2A;

    private final Identifier sequenceId;

    public AnimSequencePlayerNodeWidget(Font font, Identifier sequenceId, int graphX, int graphY) {
        super(font, Component.literal(sequenceId.getPath()), graphX, graphY);

        this.sequenceId = sequenceId;

        setTitleColor(TITLE_COLOR);
        addOutput(new Pin("", NodeColors.forType(Pose.class), false, Pose.class));

        relayout();
    }

    /// The bound sequence asset id, persisted so the node reloads bound to the same sequence.
    public Identifier sequenceId() {
        return sequenceId;
    }
}
