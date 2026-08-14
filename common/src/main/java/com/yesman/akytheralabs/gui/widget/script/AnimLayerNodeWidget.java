package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.driver.AnimationLayerSignature;
import com.yesman.akytheralabs.akythera.animation.AnimGraphCompiler;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import net.minecraft.core.Holder;
import com.yesman.akytheralabs.gui.widget.script.node.AnimLayerNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/// An Anim Graph node that registers an [com.yesman.akythera.api.animation.animator.AnimationLayer]
/// (UE's "Slot"): the [Pose] wired into `Source` becomes the layer's base
/// [com.yesman.akythera.core.animation.driver.AnimationDriver], and the `Pose` output yields the
/// layer's blended pose — the base [com.yesman.akythera.core.animation.driver.AnimationPlayer] blended
/// with the layer's [com.yesman.akythera.core.animation.driver.AnimationMontagePlayer] (see
/// [com.yesman.akythera.api.animation.animator.AnimationLayer#getPose]).
///
/// The layer is keyed by an [com.yesman.akythera.core.animation.driver.AnimationLayerSignature]; this
/// node's [#signatureName] is that signature's `identifier().toString()`, picked from the
/// node-suggestion menu and unique among the Anim Graph's layer nodes (the same no-duplicate rule the
/// State Machine applies to its [com.yesman.akythera.core.animation.driver.FSMAnimationState] states).
/// A signature is its own identity, so — like a state — it is never renamed in place.
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

    /// The layer's [com.yesman.akythera.core.animation.driver.AnimationLayerSignature] identifier
    /// (`identifier().toString()`) — its persistence key (stored in the node's `variable` field) and
    /// what the compiler stores / reads the layer under.
    public String signatureName() {
        return EditorHolderCodecs.blockId(signature).toString();
    }

    /// The bound signature, for the row this widget describes.
    public Holder<AnimationLayerSignature> signature() {
        return signature;
    }

    @Override
    public NodeData toData() {
        return new AnimLayerNodeData(signature);
    }
}