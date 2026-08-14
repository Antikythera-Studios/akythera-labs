package com.yesman.akytheralabs.gui.widget.script.node;

import com.yesman.akythera.api.data.modularize.node.FlowNode;

/// A node that is a **statement** in the execution flow: it occupies a slot in the flow skeleton and
/// lowers to an engine [FlowNode]. Value-only nodes do not implement this.
public interface FlowNodeData extends NodeData {
    /// Pass 1 — declare this statement's shape: the data inputs it reads, and the execution outputs
    /// whose targets become its children.
    void buildSkeleton(FlowSkeleton skeleton, FlowStatement self);

    /// Pass 2 — lower this statement; its children are already placed.
    FlowNode emit(FlowEmitter emitter, FlowStatement self);
}
