package com.yesman.akytheralabs.akythera.animation;

import com.yesman.akythera.core.animation.FSMAnimationState;
import com.yesman.akytheralabs.gui.screen.AnimatorScriptEditorScreen;
import net.minecraft.core.Holder;

/// Names each editor canvas of one animator script.
///
/// A canvas' key travels into the pack on the sub-trees the Anim Graph defers past `construct` — a
/// blendspace's coordinate inputs, a transition's condition — so a value re-evaluated long after the driver
/// tree was built is recorded against the canvas that draws it. [AnimatorScriptEditorScreen] keys its open
/// sub-tabs by the same strings and reads each canvas' record back under them.
///
/// Keys are built here and nowhere else, so the compiling side and the reading side cannot drift.
public final class ScriptCanvasKeys {
    /// The Anim Graph pose canvas.
    public static final String ANIM_GRAPH = "anim";

    /// The `from` of a transition leaving a machine's Entry node, which has no state name of its own.
    public static final String ENTRY = "";

    private ScriptCanvasKeys() {}

    /// The `{state}` pose sub-canvas of a state machine.
    public static String statePose(final String machineName, final String stateName) {
        return machineName + "/state/" + stateName;
    }

    /// The `{from} to {to}` condition sub-canvas of a state machine.
    public static String transition(final String machineName, final String fromName, final String toName) {
        return machineName + "/trans/" + fromName + ">" + toName;
    }

    /// The `{group} to {to}` condition sub-canvas of a state machine — one canvas per group edge,
    /// carried unchanged onto every member the compile expands it onto, so the values a run records
    /// land on the single sub-tab that draws the condition. The prefix differs from [#transition]'s so
    /// a group named like a state signature cannot collide with it.
    public static String groupTransition(final String machineName, final String groupName, final String toName) {
        return machineName + "/gtrans/" + groupName + ">" + toName;
    }

    /// Display/identity name of a state holder — its key id, which exists even for a dangling
    /// (unresolved) holder; only a keyless engine direct holder reads the live value.
    public static String stateName(final Holder<FSMAnimationState> holder) {
        return holder.unwrapKey().map(k -> k.identifier().toString())
            .orElseGet(() -> holder.value().identifier().toString());
    }
}
