package com.yesman.akytheralabs.gui.widget.script.node;

import com.yesman.akythera.api.animation.animator.AnimationInterfaceKey;
import com.yesman.akythera.api.data.modularize.node.ContextVariableAccessNode;
import com.yesman.akythera.api.data.modularize.node.PureFunctionNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import com.yesman.akytheralabs.dataholder.EditorReference;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/// A replaceable pose slot, keyed by an [AnimationInterfaceKey] and starting on a default animation.
/// Both references are shared live holders, so a rename of either follows without a rewrite.
///
/// The two implementations differ only in their resource type, their widget and the extra parameters
/// their `play_*_interface` function takes.
public interface AnimInterfaceNodeData extends NodeData {
    /// The key this interface registers under. Its identity, so it is never empty.
    Holder<AnimationInterfaceKey> interfaceKey();

    /// The animation the interface starts on (and resets back to), or empty until the Details panel
    /// picks one.
    Optional<? extends Holder<?>> defaultAnimation();

    /// The key's current id — what the compiled interface registers under and what the Details panel
    /// shows.
    default Identifier interfaceKeyId() {
        return EditorHolderCodecs.blockId(interfaceKey());
    }

    /// Lowers to `function(animator, key, defaultAnimation, extraParams…)`, tagged with the lowering
    /// row's `nodeId`. A node whose default animation is unpicked, or whose key resolves nowhere,
    /// degrades to an empty driver — the editor's validation rejects it before an intentional compile.
    default PureNode lower(int nodeId, String function, List<PureNode> extraParams) {
        Holder<?> animation = defaultAnimation().orElse(null);
        if (animation == null) return AnimGraphFunctions.emptyDriver();

        PureNode key = AnimGraphFunctions.interfaceKeyLiteral(interfaceKey());
        if (key == null) return AnimGraphFunctions.emptyDriver();

        List<PureNode> params = new ArrayList<>();
        params.add(new ContextVariableAccessNode("animator"));
        params.add(key);
        params.add(AnimGraphFunctions.stringLiteral(EditorHolderCodecs.entryId(animation).toString()));
        params.addAll(extraParams);

        return new PureFunctionNode(AnimGraphFunctions.func(function), params, nodeId);
    }

    @Override
    default Stream<EditorReference> referencedReferences() {
        return Stream.concat(
            EditorHolderCodecs.referenceOf(interfaceKey()),
            defaultAnimation().stream().flatMap(EditorHolderCodecs::referenceOf));
    }

    @Override
    default Stream<Identifier> missingReferences() {
        return Stream.concat(
            EditorHolderCodecs.missingOf(interfaceKey()),
            defaultAnimation().stream().flatMap(EditorHolderCodecs::missingOf));
    }
}
