package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.core.animation.driver.AbstractBlendDriver;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akytheralabs.akythera.animation.AnimGraphCompiler;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/// The shape shared by the Anim Graph's pose-mixing nodes: two [Pose] inputs — the base and what is
/// mixed onto it, in that pin order — and one [Pose] output. Subclasses append the inputs their own
/// mix takes and relayout.
///
/// A pose pin carries an [AnimationDriver] once compiled (see [AnimGraphCompiler]), so each of these nodes
/// lowers to the `ANIM_GRAPH` [ScriptFunction] that builds the matching [AbstractBlendDriver] over its two
/// child drivers.
public abstract class AbstractBlendNodeWidget extends AbstractNodeWidget {
    /// Steel blue title, marking the mixing nodes apart from the pose sources.
    private static final int TITLE_COLOR = 0xFF2A3A5A;

    protected AbstractBlendNodeWidget(
        Font font, Component title, String baseLabel, String blendLabel, int graphX, int graphY
    ) {
        super(font, title, graphX, graphY);

        setTitleColor(TITLE_COLOR);
        addInput(posePin(baseLabel));
        addInput(posePin(blendLabel));
        addOutput(new Pin("", NodeColors.forType(Pose.class), false, Pose.class));
    }

    protected static Pin posePin(String label) {
        return new Pin(label, NodeColors.forType(Pose.class), false, Pose.class);
    }

    /// The `Alpha` input the weighted mixes take, in [0, 1].
    protected static Pin alphaPin() {
        return new Pin("Alpha", NodeColors.forType(float.class), false, float.class);
    }
}
