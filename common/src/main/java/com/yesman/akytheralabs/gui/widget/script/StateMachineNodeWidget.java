package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akytheralabs.akythera.animation.AnimatorScriptHolder;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.StateMachineNodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/// An Anim Graph pose source backed by an animation state machine (UE's "State Machine" node). It
/// has no inputs and a single [Pose] output — the pose of the machine's currently active state.
/// Double-clicking it opens a closeable `{name}` sub-tab hosting the
/// [StateMachineGraphWidget] where its states / transitions are authored.
///
/// The machine's name is unique within the script, and is the key its [StateMachineData] is stored
/// under — see [AnimatorScriptHolder#getStateMachines].
///
/// Compile rule: emit the pose node that builds and reads the engine `AnimationStateMachine`
/// assembled from this machine's [StateMachineData].
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

    /// Renames the machine in place, title and name both.
    public void setMachineName(String name) {
        this.machineName = name;
        setMessage(Component.literal(name));
        relayout();
    }

    @Override
    public boolean isActivatable() {
        return true;
    }

    @Override
    public NodeData toData() {
        return new StateMachineNodeData(machineName);
    }
}
