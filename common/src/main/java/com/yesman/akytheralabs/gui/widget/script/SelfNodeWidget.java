package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.node.SelfNode;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.SelfNodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/// Widget for a [SelfNode]: a pure node that yields the script owner ([DataDrivenScript#getScriptOwner]). No
/// execution pins (pure) and no inputs — just one output, typed to the canvas' script owner. A canvas that
/// sets no owner type leaves it [Object], which any object slot accepts via
/// [AbstractNodeWidget#valueAssignable].
public final class SelfNodeWidget extends AbstractNodeWidget {
    public SelfNodeWidget(Font font, Component title, int graphX, int graphY) {
        super(font, title, graphX, graphY);

        setTitleColor(NodeColors.titleTint(Object.class));

        addOutput(new Pin("Self", NodeColors.forType(Object.class), false, Object.class));

        relayout();
    }

    @Override
    protected void setScriptOwnerType(@Nullable Class<?> ownerType) {
        Class<?> type = ownerType == null ? Object.class : ownerType;

        setTitleColor(NodeColors.titleTint(type));
        setOutputPin(0, new Pin("Self", NodeColors.forType(type), false, type));

        super.setScriptOwnerType(ownerType);
    }

    @Override
    public NodeData toData() {
        return new SelfNodeData();
    }
}
