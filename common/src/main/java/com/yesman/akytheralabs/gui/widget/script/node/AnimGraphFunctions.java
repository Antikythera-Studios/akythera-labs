package com.yesman.akytheralabs.gui.widget.script.node;

import com.yesman.akythera.api.animation.animator.AnimationInterfaceKey;
import com.yesman.akythera.api.data.modularize.AkytheraScriptTypes;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.node.LiteralNode;
import com.yesman.akythera.api.data.modularize.node.PureFunctionNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.core.animation.driver.AnimationLayerSignature;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import net.minecraft.core.Holder;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// The leaf builders the Anim Graph's node types lower through — the `ANIM_GRAPH` script functions
/// and the two data-block literals a pose node needs.
public final class AnimGraphFunctions {
    private AnimGraphFunctions() {}

    /// The registered [ScriptFunction] with the given registry path (e.g. `play_anim_sequence`).
    public static ScriptFunction func(String path) {
        for (var entry : AkytheraRegistries.Frozen.SCRIPT_FUNCTION.entrySet()) {
            if (entry.getKey().identifier().getPath().equals(path)) return entry.getValue();
        }
        throw new IllegalStateException("Missing anim-graph script function: " + path);
    }

    public static PureNode emptyDriver() {
        return new PureFunctionNode(func("empty_driver"), List.of());
    }

    public static PureNode stringLiteral(String value) {
        return new LiteralNode<>(AkytheraScriptTypes.STRING.value(), value);
    }

    public static PureNode literalTrue() {
        return new LiteralNode<>(AkytheraScriptTypes.BOOLEAN.value(), Boolean.TRUE);
    }

    /// A literal carrying the [AnimationInterfaceKey] the compiled interface registers under in
    /// `BlendingAnimator`. Null, with a logged warning, when the key resolves nowhere.
    public static @Nullable PureNode interfaceKeyLiteral(Holder<AnimationInterfaceKey> key) {
        if (!key.isBound()) {
            AkytheraLabs.LOGGER.warn("Unresolved anim interface key {}", EditorHolderCodecs.blockId(key));
            return null;
        }

        return new LiteralNode<>(AkytheraScriptTypes.ANIM_INTERFACE_KEY.value(), key.value());
    }

    /// A literal carrying the [AnimationLayerSignature] — the map key shared by an Anim Layer node's
    /// `map_put` (construct) and `map_get` (pose read). Null (logged) when it resolves nowhere, for
    /// the same reason as [#interfaceKeyLiteral].
    public static @Nullable PureNode signatureLiteral(Holder<AnimationLayerSignature> signature) {
        if (!signature.isBound()) {
            AkytheraLabs.LOGGER.warn("Unresolved anim layer signature {}", EditorHolderCodecs.blockId(signature));
            return null;
        }

        return new LiteralNode<>(AkytheraScriptTypes.ANIM_LAYER_SIGNATURE.value(), signature.value());
    }
}
