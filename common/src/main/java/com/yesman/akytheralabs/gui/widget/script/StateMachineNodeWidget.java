package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.core.animation.keyframe.Pose;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/// An Anim Graph pose source backed by an animation state machine (UE's "State Machine" node). It
/// has no inputs and a single [Pose] output — the pose of the machine's currently active state.
/// Double-clicking it opens a closeable `{name}` sub-tab hosting the
/// [StateMachineGraphWidget] where its states / transitions are authored.
///
/// The machine's name is unique within the script and is the key under which its
/// [StateMachineData] is stored (see
/// [com.yesman.akytheralabs.dataholder.animation.AnimatorScriptHolder#getStateMachines]). It is
/// also the cached-pose name a `CachedPoseGetter` references (Stage 3b).
///
/// Compile rule (see the graph compilers): emit the pose node that builds / reads the engine
/// `AnimationStateMachine` assembled from this machine's [StateMachineData] (engine plan, Stage 3b).
public final class StateMachineNodeWidget extends AbstractNodeWidget {
    /// Neutral gray title, matching UE's State Machine node.
    private static final int TITLE_COLOR = 0xFF3A3A3A;

    private String machineName;

    public StateMachineNodeWidget(Font font, String machineName, int graphX, int graphY) {
        super(font, Component.literal(machineName), graphX, graphY);

        this.machineName = machineName;

        setTitleColor(TITLE_COLOR);
        addOutput(new Pin("Pose", NodeColors.forType(Pose.class), false, Pose.class));

        relayout();
    }

    /// The machine's unique name — its sub-tab title and the key its [StateMachineData] persists under.
    public String machineName() {
        return machineName;
    }

    /// Renames the machine in place (title + name), e.g. when its sub-tab is renamed (Stage 3b).
    public void setMachineName(String name) {
        this.machineName = name;
        setMessage(Component.literal(name));
        relayout();
    }

    @Override
    public boolean isActivatable() {
        return true;
    }
}
