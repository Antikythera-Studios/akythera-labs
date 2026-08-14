package com.yesman.akytheralabs.gui.widget.script.node;

import com.yesman.akythera.api.data.modularize.node.FlowNode;
import org.jspecify.annotations.Nullable;

/// Pass-2 services for [FlowNodeData#emit]: lower a child statement and test whether a slot is worth
/// emitting at all. Reading a statement's data inputs is the ordinary value lowering, inherited from
/// [PureCompileContext].
public interface FlowEmitter extends PureCompileContext {
    /// Lowers `statement`, applying any values hoisted at it.
    FlowNode emit(FlowStatement statement);

    /// Whether `statement` is absent, or a synthetic slot with neither children nor hoists.
    boolean isEmpty(@Nullable FlowStatement statement);
}
