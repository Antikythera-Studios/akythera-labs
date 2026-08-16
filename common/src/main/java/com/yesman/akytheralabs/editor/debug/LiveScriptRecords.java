package com.yesman.akytheralabs.editor.debug;

import com.yesman.akythera.api.data.modularize.node.ExecutionRecord;
import com.yesman.akythera.api.data.modularize.node.ScriptNode;
import com.yesman.akythera.core.blueprint.script.DataDrivenAnimatorScript;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import org.jspecify.annotations.Nullable;

/// Where a sub-tree the Anim Graph defers past `construct` — a blendspace's coordinate inputs, a
/// transition's condition — writes its samples.
///
/// The node mixins ask here on every sample rather than holding a record, so a session that starts
/// long after the driver tree was built still catches it.
public final class LiveScriptRecords {
    private LiveScriptRecords() {}

    /// The record canvas `canvasKey` of `script` collects into right now, or null when the sub-tree
    /// carries no canvas, the script is not an animator script (or is headless), or its entity is not
    /// the possessed one.
    public static @Nullable ExecutionRecord of(final @Nullable DataDrivenScript<?, ?> script, final String canvasKey) {
        if (ScriptNode.NO_CANVAS.equals(canvasKey) || !(script instanceof DataDrivenAnimatorScript animatorScript)) {
            return null;
        }

        return ((ScriptExecutionRecords)(Object)animatorScript).akytheralabs$liveRecord(canvasKey);
    }
}
