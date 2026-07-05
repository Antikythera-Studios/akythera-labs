package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.BlendSpace2D;
import com.yesman.akythera.core.blueprint.script.AnimatorScript;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.animation.BlendSpace2DHolder;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

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

    private final Identifier blendSpaceId;

    public BlendSpacePlayerNodeWidget(Font font, Identifier blendSpaceId, int graphX, int graphY) {
        super(font, Component.literal(blendSpaceId.getPath()), graphX, graphY);

        this.blendSpaceId = blendSpaceId;
        setTitleColor(TITLE_COLOR);

        BlendSpace2DHolder holder = resolveHolder(blendSpaceId);
        String axisNameX = holder != null ? holder.getAxisNameX() : "X";
        String axisNameY = holder != null ? holder.getAxisNameY() : "Y";

        addInput(new Pin(axisNameX, NodeColors.forType(float.class), false, float.class));
        addInput(new Pin(axisNameY, NodeColors.forType(float.class), false, float.class));
        addOutput(new Pin("", NodeColors.forType(Pose.class), false, Pose.class));

        relayout();
    }

    /// The editor holder for [blendSpaceId] in the current project, or `null` for a blendspace that
    /// lives only in an installed mod / the level (no editor-editable holder — the input pins then
    /// fall back to the default `X` / `Y` axis labels).
    private static @Nullable BlendSpace2DHolder resolveHolder(Identifier blendSpaceId) {
        ResourceKey<BlendSpace2D> key = ResourceKey.create(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY, blendSpaceId);
        return AkytheraProject.getCurrentProject().<BlendSpace2D, BlendSpace2DHolder>getDataHolder(key).orElse(null);
    }

    /// The bound blendspace asset id, persisted so the node reloads bound to the same blendspace.
    public Identifier blendSpaceId() {
        return blendSpaceId;
    }
}
