package com.yesman.akytheralabs.gui.widget.script.node;

/// A node that can root a flow. The flow compiler picks between several on one canvas by asking
/// whether the entry carries event parameters (the animator script's Update entry does, its
/// Constructor entry does not), or through a caller-supplied predicate for entries told apart by an
/// explicit marker.
public interface EntryNodeData extends NodeData {
    /// Whether this entry exposes event parameters as data outputs.
    boolean hasDataOutputs();
}
