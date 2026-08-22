package com.yesman.akytheralabs.mixin;

import com.yesman.akythera.api.data.modularize.node.animgraph.AnimationStateMachineNode;
import com.yesman.akythera.core.animation.driver.AnimationStateMachine;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akytheralabs.editor.debug.LiveScriptRecords;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiFunction;

/// Records what a transition's condition sub-graph answers on each check, against the canvas it was
/// compiled from. The check installed here is the machine's own with a record attached.
@Mixin(AnimationStateMachineNode.class)
public abstract class MixinAnimationStateMachineNode {
    @SuppressWarnings("rawtypes")
    @Inject(method = "check", at = @At("HEAD"), cancellable = true)
    private static void akytheralabs$checkIntoCanvasRecord(
        DataDrivenScript<?, ?> script, AnimationStateMachineNode.TransitionEntry entry,
        CallbackInfoReturnable<BiFunction<AnimationStateMachine.AnimationStateInstance, AnimationStateMachine.AnimationStateInstance, Boolean>> cir
    ) {
        BiFunction<AnimationStateMachine.AnimationStateInstance, AnimationStateMachine.AnimationStateInstance, Boolean> recording =
            (from, _) -> AnimationStateMachineNode.evaluate(
                script, entry, from, LiveScriptRecords.of(script, entry.canvas()));

        cir.setReturnValue(recording);
    }
}
