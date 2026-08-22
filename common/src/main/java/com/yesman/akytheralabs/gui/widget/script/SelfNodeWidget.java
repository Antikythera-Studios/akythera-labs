package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.node.SelfNode;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.SelfNodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/// Widget for a [SelfNode]: a pure node that yields the script owner ([DataDrivenScript#getScriptOwner]). No
/// execution pins (pure) and no inputs — just one object-typed output. The owner's concrete class is unknown
/// at edit time, so the output is typed [Object] (accepted by any object slot via
/// [AbstractNodeWidget#valueAssignable]).
public final class SelfNodeWidget extends AbstractNodeWidget {
    public SelfNodeWidget(Font font, Component title, int graphX, int graphY) {
        super(font, title, graphX, graphY);

        setTitleColor(NodeColors.titleTint(Object.class));

        addOutput(new Pin("Self", NodeColors.forType(Object.class), false, Object.class));

        relayout();
    }

    @Override
    public NodeData toData() {
        return new SelfNodeData();
    }
}
