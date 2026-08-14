package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.SequenceNodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/// Widget for a [com.yesman.akythera.api.data.modularize.node.SequenceNode]: one
/// execution input that fans out to ordered execution outputs ("Then 0", "Then 1", …).
/// Since `SequenceNode` holds its next nodes as a list, the output count is dynamic —
/// the "Add pin" button appends a `Then N`, and the host graph's right-click "Remove Pin"
/// removes one. Outputs are relabeled to stay contiguous.
public final class SequenceNodeWidget extends AbstractNodeWidget {
    private static final int TITLE_COLOR = 0xFF3A3A3A;
    private static final int INITIAL_OUTPUTS = 2;

    public SequenceNodeWidget(Font font, Component title, int graphX, int graphY) {
        super(font, title, graphX, graphY);

        setTitleColor(TITLE_COLOR);

        addInput(new Pin("", NodeColors.EXEC, true));

        for (int i = 0; i < INITIAL_OUTPUTS; i++) {
            addOutput(new Pin(thenLabel(i), NodeColors.EXEC, true));
        }

        relayout();
    }

    @Override
    public boolean supportsDynamicOutputs() {
        return true;
    }

    /// Appends one more `Then N` execution output.
    public void addOutputPin() {
        addOutput(new Pin(thenLabel(outputPinCount()), NodeColors.EXEC, true));
        relayout();
    }

    @Override
    protected void onAddPin() {
        addOutputPin();
    }

    @Override
    public void removeOutputPin(int index) {
        super.removeOutputPin(index);
        relabelOutputs();
    }

    private void relabelOutputs() {
        for (int i = 0; i < outputPinCount(); i++) {
            setOutputLabel(i, thenLabel(i));
        }
    }

    private static String thenLabel(int index) {
        return "Then " + index;
    }

    @Override
    public NodeData toData() {
        return new SequenceNodeData(outputPinCount());
    }
}
