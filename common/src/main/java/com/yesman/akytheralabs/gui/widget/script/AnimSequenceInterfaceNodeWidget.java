package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.animation.animator.AnimationInterfaceKey;
import com.yesman.akythera.api.animation.animator.AnimationSequenceInterface;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import com.yesman.akytheralabs.gui.widget.script.node.AnimSequenceInterfaceNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/// The [AnimationSequenceInterface] node: a replaceable sequence slot. Like the fixed Sequence Player it has
/// no inputs and one [Pose] output; the Details-picked default animation is an [AnimationSequence].
///
/// Compile rule: `play_anim_sequence_interface(animator, interfaceKey, sequenceId)`.
public final class AnimSequenceInterfaceNodeWidget extends AnimInterfaceNodeWidget {
    public AnimSequenceInterfaceNodeWidget(
        Font font, Holder<AnimationInterfaceKey> interfaceKey,
        @Nullable Holder<AnimationSequence> defaultAnimation, int graphX, int graphY
    ) {
        super(font, interfaceKey, defaultAnimation, graphX, graphY);

        addOutput(new Pin("", NodeColors.forType(Pose.class), false, Pose.class));

        relayout();
    }

    @Override
    protected Holder<AnimationSequence> resolveDefaultAnimation(Identifier id) {
        return EditorHolderCodecs.resolve(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY, id);
    }

    @Override
    @SuppressWarnings("unchecked")
    public NodeData toData() {
        return new AnimSequenceInterfaceNodeData(
            interfaceKey(), Optional.ofNullable((Holder<AnimationSequence>) defaultAnimation()));
    }
}
