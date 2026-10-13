package com.yesman.akytheralabs.gui.widget.script.node;

import com.yesman.akytheralabs.akythera.blueprint.BlueprintMembers;

/// Pass-1 services for [FlowNodeData#buildSkeleton]: attach the statements wired to an execution
/// output, open a synthetic slot for a body / arm, and declare a data input as a value root.
public interface FlowSkeleton {
    /// Appends the statements wired to execution output `(fromId,fromPin)` as children of `parent`,
    /// splicing through reroute knots.
    void chainInto(int fromId, int fromPin, FlowStatement parent);

    /// A fresh synthetic sequence slot appended to `parent` — a branch arm or a `next` body.
    FlowStatement newSequenceChild(FlowStatement parent);

    /// Declares that `self` reads data input `pin`, so shared values feeding it are attributed here.
    void readsInput(FlowStatement self, int pin);

    /// The members of the blueprint the canvas belongs to, whose signatures shape the statements that
    /// call them.
    BlueprintMembers members();
}
