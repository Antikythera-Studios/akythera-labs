package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.core.animation.driver.AdditiveBlendDriver;
import com.yesman.akythera.core.asset.loader.AnimationLoader;
import com.yesman.akytheralabs.gui.widget.script.node.AdditiveBlendNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/// Lays an additive pose over a base pose (UE's "Apply Additive"), scaled by `Alpha`. The animation feeding
/// `Additive` must be imported as [AnimationLoader.ImportType#ADDITIVE] — it carries deltas from a reference
/// pose, not a pose of its own.
///
/// Compile rule: lowers to the `apply_additive` script function, whose [AdditiveBlendDriver] samples `Alpha`
/// live.
public final class AdditiveBlendNodeWidget extends AbstractBlendNodeWidget {
    public AdditiveBlendNodeWidget(Font font, int graphX, int graphY) {
        super(font, Component.literal("Apply Additive"), "Base", "Additive", graphX, graphY);

        addInput(alphaPin());

        relayout();
    }

    @Override
    public NodeData toData() {
        return new AdditiveBlendNodeData();
    }
}
