package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.animation.animator.AnimationLayer;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akythera.core.animation.driver.AnimationMontagePlayer;
import com.yesman.akythera.core.animation.driver.AnimationPlayer;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.AnimationLayerSignature;
import com.yesman.akytheralabs.akythera.animation.AnimGraphCompiler;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import net.minecraft.core.Holder;
import com.yesman.akytheralabs.gui.widget.script.node.AnimLayerNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/// An Anim Graph node that registers an [AnimationLayer] (UE's "Slot"): the [Pose] wired into `Source`
/// becomes the layer's base [AnimationDriver], and the `Pose` output yields the layer's blended pose — the
/// base [AnimationPlayer] blended with the layer's [AnimationMontagePlayer] (see [AnimationLayer#getPose]).
///
/// The layer is keyed by an [AnimationLayerSignature],
/// picked from the node-suggestion menu. One signature registers one layer per animator, so a second
/// node carrying it fails the compile. A signature is its own identity, so it is never renamed in place.
///
/// Compile rule (in [AnimGraphCompiler], not a node
/// method): every layer node `map_put`s `animation_layer_create(source)` under its signature into the
/// `construct` event — so the layer exists for the whole lifecycle regardless of whether its output is
/// wired ("presence of any layer node puts a new layer in the constructor"). When this node's output
/// feeds the Output Pose, the graph's `poseGetter` reads the layer's pose back via
/// `AnimationLayer#getPose` over a `map_get` of the same signature.
public final class AnimLayerNodeWidget extends AbstractNodeWidget {
    /// Indigo title, distinguishing the layer node from the other pose sources.
    private static final int TITLE_COLOR = 0xFF3A2A4A;

    /// The layer's signature, as the shared live holder — the binding follows a rename of an
    /// editor-created signature.
    private final Holder<AnimationLayerSignature> signature;

    public AnimLayerNodeWidget(Font font, Holder<AnimationLayerSignature> signature, int graphX, int graphY) {
        super(font, Component.literal("Layer: " + EditorHolderCodecs.blockId(signature)), graphX, graphY);

        this.signature = signature;

        setTitleColor(TITLE_COLOR);
        addInput(new Pin("Source", NodeColors.forType(Pose.class), false, Pose.class));
        addOutput(new Pin("Pose", NodeColors.forType(Pose.class), false, Pose.class));

        relayout();
    }

    /// The bound signature, for the row this widget describes.
    public Holder<AnimationLayerSignature> signature() {
        return signature;
    }

    /// One [AnimationLayerSignature] registers one layer per
    /// Anim Graph, and a signature is picked from the registry rather than minted, so a copy of this
    /// node has no name to land under.
    @Override
    public boolean isCopyable() {
        return false;
    }

    @Override
    public NodeData toData() {
        return new AnimLayerNodeData(signature);
    }
}