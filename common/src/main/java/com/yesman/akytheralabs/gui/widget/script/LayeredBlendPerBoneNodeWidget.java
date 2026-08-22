package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.core.animation.driver.LayeredBlendPerBoneDriver;
import com.yesman.akytheralabs.gui.widget.script.node.LayeredBlendPerBoneNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/// Hands one branch of the skeleton over to a second pose (UE's "Layered blend per bone"): the bones
/// from `Bone` down ramp toward `Layered` over `Depth` bones, the rest of the skeleton stays on `Base`.
///
/// Compile rule: lowers to the `layered_blend_per_bone` script function, whose [LayeredBlendPerBoneDriver]
/// reads the armature to walk from the animator. `Bone` and `Depth` are read once, when the driver tree is
/// constructed.
public final class LayeredBlendPerBoneNodeWidget extends AbstractBlendNodeWidget {
    public LayeredBlendPerBoneNodeWidget(Font font, int graphX, int graphY) {
        super(font, Component.literal("Layered Blend per Bone"), "Base", "Layered", graphX, graphY);

        addInput(new Pin("Bone", NodeColors.forType(String.class), false, String.class));
        addInput(new Pin("Depth", NodeColors.forType(int.class), false, int.class));

        relayout();
    }

    @Override
    public NodeData toData() {
        return new LayeredBlendPerBoneNodeData();
    }
}
