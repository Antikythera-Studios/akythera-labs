package com.yesman.akytheralabs.mixin;

import com.yesman.akythera.api.data.modularize.node.ExecutionRecord;
import com.yesman.akythera.api.data.modularize.node.ScriptNode;
import com.yesman.akythera.api.data.modularize.node.animgraph.AnimationStateMachineNode;
import com.yesman.akythera.core.math.model.Transition;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akytheralabs.editor.debug.LiveScriptRecords;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// Records what a transition's condition sub-graph answers on each check, against the canvas it was
/// compiled from. The check installed here is the machine's own with a record attached.
@Mixin(AnimationStateMachineNode.class)
public abstract class MixinAnimationStateMachineNode {
    @Inject(method = "check", at = @At("HEAD"), cancellable = true)
    private static void akytheralabs$checkIntoCanvasRecord(
        DataDrivenScript<?, ?> script, AnimationStateMachineNode.TransitionEntry entry,
        CallbackInfoReturnable<Transition.TransitionCheck<?, ?>> cir
    ) {
        Transition.TransitionCheck<?, ?> recording = (_, _) -> {
            ExecutionRecord live = LiveScriptRecords.of(script, entry.canvas());
            Object value = entry.condition().execute(
                script, new ScriptNode.ExecutionContext(script.getScriptVariable(), live));

            return !(value instanceof Boolean bool) || bool;
        };

        cir.setReturnValue(recording);
    }
}
