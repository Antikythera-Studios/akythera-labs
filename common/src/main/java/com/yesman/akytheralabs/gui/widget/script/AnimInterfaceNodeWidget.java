package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.animation.animator.AbstractAnimationInterface;
import com.yesman.akythera.api.animation.animator.AnimationInterfaceKey;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// An Anim Graph pose source backed by an engine [AbstractAnimationInterface] — an injection point
/// whose animation can be swapped at runtime (weapon-specific locomotion etc.) via
/// `BlendingAnimator#replaceAnimation` and rolled back with `resetAnimInterface`.
///
/// The node is keyed by an [AnimationInterfaceKey] picked from the node-suggestion menu — like an
/// Anim Layer's signature, a key is its own identity and registers **once per animator**
/// (`BlendingAnimator#addAnimInterface` throws on a duplicate), so the host screen only ever offers
/// keys not already used anywhere on this script, both when creating a node and when re-keying one
/// through the Details panel ([#setInterfaceKeyName]). The **default** animation the interface starts
/// on (and resets back to) is picked in the Details panel too and must match the subclass's resource
/// type; until one is chosen the node fails validation.
///
/// Both references are shared live holders, so a rename of the key or of the default animation
/// follows here without a rewrite.
///
/// Compile rule (see the graph compilers): emit the `play_*_interface` `ANIM_GRAPH` [ScriptFunction], passing
/// the construct-scope `animator` context variable, the key as an `anim_interface_key` literal, and the
/// default animation's id.
public abstract sealed class AnimInterfaceNodeWidget extends AbstractNodeWidget
    permits AnimSequenceInterfaceNodeWidget, BlendSpaceInterfaceNodeWidget {

    /// Bright purple title, setting the replaceable-interface sources apart from the fixed players.
    protected static final int TITLE_COLOR = 0xFF8A2BE2;

    private Holder<AnimationInterfaceKey> interfaceKey;

    /// The default animation, or null while none is picked yet (Details panel edits it).
    private @Nullable Holder<?> defaultAnimation;

    protected AnimInterfaceNodeWidget(
        Font font, Holder<AnimationInterfaceKey> interfaceKey, @Nullable Holder<?> defaultAnimation,
        int graphX, int graphY
    ) {
        super(font, title(EditorHolderCodecs.blockId(interfaceKey)), graphX, graphY);

        this.interfaceKey = interfaceKey;
        this.defaultAnimation = defaultAnimation;

        setTitleColor(TITLE_COLOR);
    }

    private static Component title(Identifier keyId) {
        return Component.literal("Anim Interface: " + keyId);
    }

    /// An [AnimationInterfaceKey] registers once per animator (`BlendingAnimator#addAnimInterface`
    /// throws on a duplicate) and is picked from the registry rather than minted, so a copy of this
    /// node has no key to land under.
    @Override
    public boolean isCopyable() {
        return false;
    }

    /// The bound key, for the row this widget describes.
    public Holder<AnimationInterfaceKey> interfaceKey() {
        return interfaceKey;
    }

    /// The [AnimationInterfaceKey] identifier (`identifier().toString()`) — the node's persistence
    /// key (stored in its `variable` field) and what the compiled interface registers under.
    public String interfaceKeyName() {
        return EditorHolderCodecs.blockId(interfaceKey).toString();
    }

    /// Re-keys this interface — the Details panel's picker. The key is its own identity, so the caller
    /// must only offer keys **not already used** by another interface node on the script (the engine's
    /// `BlendingAnimator#addAnimInterface` throws on a duplicate); the title follows the new key.
    public void setInterfaceKeyName(String interfaceKeyName) {
        Identifier id = Identifier.parse(interfaceKeyName);

        this.interfaceKey = AkytheraProject.getCurrentProject()
            .getDataBlockHolderOrDangling(AnimationInterfaceKey.ENUM_MANAGER, id);
        setMessage(title(id));
        relayout();
    }

    /// The default animation bound to this interface, or null while unset.
    public @Nullable Holder<?> defaultAnimation() {
        return defaultAnimation;
    }

    /// The default animation's current id, or null while unset, which fails validation.
    public @Nullable Identifier defaultAnimationId() {
        return defaultAnimation == null ? null : EditorHolderCodecs.entryId(defaultAnimation);
    }

    public void setDefaultAnimationId(@Nullable Identifier defaultAnimationId) {
        this.defaultAnimation = defaultAnimationId == null ? null : resolveDefaultAnimation(defaultAnimationId);
    }

    /// Binds `id` as a reference of this node's resource type (an anim sequence for a sequence
    /// interface, a blendspace for a Blend Space 2D one).
    protected abstract Holder<?> resolveDefaultAnimation(Identifier id);

    /// An interface with no default animation, or one whose pick resolves nowhere, is red on sight —
    /// no compile needed. Both conditions block the compile as well (see
    /// [ScriptNodeGraphWidget#validate]).
    @Override
    public List<Component> intrinsicErrors() {
        if (defaultAnimation == null) {
            return List.of(Component.translatable(
                LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VALIDATION_NO_DEFAULT_ANIMATION));
        }

        return !defaultAnimation.isBound()
            ? List.of(Component.translatable(
                LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VALIDATION_MISSING_ASSET, defaultAnimationId().toString()))
            : List.of();
    }
}
