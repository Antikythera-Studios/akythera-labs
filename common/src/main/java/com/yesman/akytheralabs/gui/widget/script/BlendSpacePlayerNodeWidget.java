package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.BlendSpace2D;
import com.yesman.akythera.core.blueprint.script.AnimatorScript;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.animation.BlendSpace2DHolder;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.node.BlendSpacePlayerNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// An Anim Graph pose source bound to a project [com.yesman.akythera.core.animation.playable.BlendSpace2D]
/// (UE's "Blendspace Player"). Two `float` value inputs (`X` / `Y`) drive the blend coordinate, and a
/// single [Pose] output yields the blended pose. The blendspace is chosen by dragging it from the
/// Asset Browser onto the canvas, which spawns this node already bound to that asset's [Identifier].
///
/// Compile rule (see the graph compilers): emit the `ANIM_GRAPH`
/// [com.yesman.akythera.api.data.modularize.ScriptFunction] that wraps
/// [AnimatorScript#instantiateBlendSpace2D] for
/// `blendSpaceId`, supplying the `X` / `Y` inputs as the two `Supplier<Float>` updaters, and
/// returning the driver's [Pose].
public final class BlendSpacePlayerNodeWidget extends AbstractNodeWidget {
    /// Green title, matching UE's Blendspace Player tint.
    private static final int TITLE_COLOR = 0xFF2A4A2A;

    /// The bound blendspace, as the target's shared live holder — the binding follows its rename.
    private Holder<BlendSpace2D> blendSpace;

    public BlendSpacePlayerNodeWidget(Font font, Holder<BlendSpace2D> blendSpace, int graphX, int graphY) {
        super(font, Component.literal(EditorHolderCodecs.entryId(blendSpace).getPath()), graphX, graphY);

        this.blendSpace = blendSpace;
        setTitleColor(TITLE_COLOR);

        Identifier boundId = EditorHolderCodecs.entryId(blendSpace);
        addInput(axisPin(axisNameX(boundId)));
        addInput(axisPin(axisNameY(boundId)));
        addOutput(new Pin("", NodeColors.forType(Pose.class), false, Pose.class));

        relayout();
    }

    private static Pin axisPin(String axisName) {
        return new Pin(axisName, NodeColors.forType(float.class), false, float.class);
    }

    /// The bound blendspace's X axis name, or the generic `X` for one with no editor holder (a
    /// blendspace living only in an installed mod / the level, or an id that resolves nowhere).
    private static String axisNameX(Identifier blendSpaceId) {
        BlendSpace2DHolder holder = resolveHolder(blendSpaceId);
        return holder != null ? holder.getAxisNameX() : "X";
    }

    /// See [#axisNameX].
    private static String axisNameY(Identifier blendSpaceId) {
        BlendSpace2DHolder holder = resolveHolder(blendSpaceId);
        return holder != null ? holder.getAxisNameY() : "Y";
    }

    /// The editor holder for [blendSpaceId] in the current project, or `null` for a blendspace that
    /// lives only in an installed mod / the level (no editor-editable holder — the input pins then
    /// fall back to the default `X` / `Y` axis labels).
    private static @Nullable BlendSpace2DHolder resolveHolder(Identifier blendSpaceId) {
        ResourceKey<BlendSpace2D> key = ResourceKey.create(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY, blendSpaceId);
        return AkytheraProject.getCurrentProject().<BlendSpace2D, BlendSpace2DHolder>getDataHolder(key).orElse(null);
    }

    /// The bound blendspace, for the row this widget describes.
    public Holder<BlendSpace2D> blendSpace() {
        return blendSpace;
    }

    /// The bound blendspace's current id.
    public Identifier blendSpaceId() {
        return EditorHolderCodecs.entryId(blendSpace);
    }

    /// Rebinds this player to another blendspace — the Details panel's picker (the drag from the Asset
    /// Browser only ever creates a node). The title and the two input pins' axis labels follow the new
    /// asset, exactly as they are derived on creation; the pin count is unchanged, so wires survive.
    public void setBlendSpaceId(Identifier blendSpaceId) {
        this.blendSpace = EditorHolderCodecs.resolve(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY, blendSpaceId);
        setMessage(Component.literal(blendSpaceId.getPath()));
        setInputPin(0, axisPin(axisNameX(blendSpaceId)));
        setInputPin(1, axisPin(axisNameY(blendSpaceId)));
        relayout();
    }

    /// Whether the bound blendspace no longer resolves in the project or the level
    public boolean isBoundAssetMissing() {
        return !blendSpace.isBound();
    }

    /// A player bound to a blendspace that resolves nowhere is red on sight — no compile needed.
    @Override
    public List<Component> intrinsicErrors() {
        return isBoundAssetMissing()
            ? List.of(Component.translatable(
                LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VALIDATION_MISSING_ASSET, blendSpaceId().toString()))
            : List.of();
    }

    @Override
    public NodeData toData() {
        return new BlendSpacePlayerNodeData(blendSpace);
    }
}
