package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.node.SupplierAdapterNode;
import com.yesman.akythera.core.animation.driver.PoseBlendDriver;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.TwoPoseBlendNodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/// Interpolates between two poses (UE's "Blend"): `Alpha` 0 yields `A`, 1 yields `B`.
///
/// Compile rule: lowers to the `blend_two_poses` script function, whose [PoseBlendDriver] samples `Alpha`
/// live — the input is wrapped in a [SupplierAdapterNode] as a blendspace's axes are.
public final class TwoPoseBlendNodeWidget extends AbstractBlendNodeWidget {
    public TwoPoseBlendNodeWidget(Font font, int graphX, int graphY) {
        super(font, Component.literal("Blend"), "A", "B", graphX, graphY);

        addInput(alphaPin());

        relayout();
    }

    @Override
    public NodeData toData() {
        return new TwoPoseBlendNodeData();
    }
}
