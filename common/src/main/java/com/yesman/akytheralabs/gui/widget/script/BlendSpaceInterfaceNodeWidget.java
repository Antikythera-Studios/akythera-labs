package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.animation.animator.AnimationInterfaceKey;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.BlendSpace2D;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import com.yesman.akytheralabs.gui.widget.script.node.BlendSpaceInterfaceNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/// The [com.yesman.akythera.api.animation.animator.BlendSpace2DInterface] node: a replaceable
/// 2D-blendspace slot. Two `float` inputs drive the blend coordinate and one [Pose] output yields
/// the blended pose; the Details-picked default animation is a [BlendSpace2D].
///
/// Unlike the fixed Blendspace Player — bound to one asset whose axis names it can show — the
/// interface's animation is replaceable at runtime, so the axis names are not inferrable and the
/// pins stay generically labeled "X axis input" / "Y axis input".
///
/// Compile rule: `play_blend_space_2d_interface(animator, interfaceKey, blendSpaceId, x, y)` with
/// the two inputs wrapped as live `Supplier` updaters.
public final class BlendSpaceInterfaceNodeWidget extends AnimInterfaceNodeWidget {
    public BlendSpaceInterfaceNodeWidget(
        Font font, Holder<AnimationInterfaceKey> interfaceKey,
        @Nullable Holder<BlendSpace2D> defaultAnimation, int graphX, int graphY
    ) {
        super(font, interfaceKey, defaultAnimation, graphX, graphY);

        addInput(new Pin("X axis input", NodeColors.forType(float.class), false, float.class));
        addInput(new Pin("Y axis input", NodeColors.forType(float.class), false, float.class));
        addOutput(new Pin("", NodeColors.forType(Pose.class), false, Pose.class));

        relayout();
    }

    @Override
    protected Holder<BlendSpace2D> resolveDefaultAnimation(Identifier id) {
        return EditorHolderCodecs.resolve(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY, id);
    }

    @Override
    @SuppressWarnings("unchecked")
    public NodeData toData() {
        return new BlendSpaceInterfaceNodeData(
            interfaceKey(), Optional.ofNullable((Holder<BlendSpace2D>) defaultAnimation()));
    }
}
