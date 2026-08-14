package com.yesman.akytheralabs.mixin;

import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.data.modularize.node.ExecutionRecord;
import com.yesman.akythera.api.data.modularize.node.ScriptNode;
import com.yesman.akythera.core.blueprint.script.DataDrivenAnimatorScript;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akytheralabs.editor.debug.EntityDebugSession;
import com.yesman.akytheralabs.editor.debug.ScriptExecutionRecords;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/// Hands the script a recording [ScriptNode.ExecutionContext] while its entity is possessed, and keeps
/// the record of the most recent run of each entry point.
///
/// The record fills in place as `execute` runs, so redirecting the context's construction is enough —
/// nothing has to be collected afterwards.
@Mixin(DataDrivenAnimatorScript.class)
public abstract class MixinDataDrivenAnimatorScript implements ScriptExecutionRecords {
    @Unique
    private @Nullable ExecutionRecord akytheralabs$constructRecord;

    @Unique
    private @Nullable ExecutionRecord akytheralabs$updateRecord;

    @Unique
    private @Nullable ExecutionRecord akytheralabs$poseRecord;

    @Override
    public @Nullable ExecutionRecord akytheralabs$getConstructRecord() {
        return akytheralabs$constructRecord;
    }

    @Override
    public @Nullable ExecutionRecord akytheralabs$getUpdateRecord() {
        return akytheralabs$updateRecord;
    }

    @Override
    public @Nullable ExecutionRecord akytheralabs$getPoseRecord() {
        return akytheralabs$poseRecord;
    }

    @Redirect(
        method = "construct(Ljava/util/Map;Ljava/util/Map;Lcom/yesman/akythera/core/animation/animator/BlendingAnimator;)V",
        at = @At(
            value = "NEW",
            target = "(Lcom/yesman/akythera/core/blueprint/script/ScriptVariables;)Lcom/yesman/akythera/api/data/modularize/node/ScriptNode$ExecutionContext;"
        )
    )
    private ScriptNode.ExecutionContext akytheralabs$recordConstruct(ScriptVariables locals) {
        akytheralabs$constructRecord = akytheralabs$newRecord();

        return new ScriptNode.ExecutionContext(locals, akytheralabs$constructRecord);
    }

    @Redirect(
        method = "update(FF)V",
        at = @At(
            value = "NEW",
            target = "(Lcom/yesman/akythera/core/blueprint/script/ScriptVariables;)Lcom/yesman/akythera/api/data/modularize/node/ScriptNode$ExecutionContext;"
        )
    )
    private ScriptNode.ExecutionContext akytheralabs$recordUpdate(ScriptVariables locals) {
        akytheralabs$updateRecord = akytheralabs$newRecord();

        return new ScriptNode.ExecutionContext(locals, akytheralabs$updateRecord);
    }

    @Redirect(
        method = "getFinalPose(Ljava/util/Map;)Lcom/yesman/akythera/core/animation/keyframe/Pose;",
        at = @At(
            value = "NEW",
            target = "(Lcom/yesman/akythera/core/blueprint/script/ScriptVariables;)Lcom/yesman/akythera/api/data/modularize/node/ScriptNode$ExecutionContext;"
        )
    )
    private ScriptNode.ExecutionContext akytheralabs$recordPose(ScriptVariables locals) {
        akytheralabs$poseRecord = akytheralabs$newRecord();

        return new ScriptNode.ExecutionContext(locals, akytheralabs$poseRecord);
    }

    /// A fresh record while this script's entity is the possessed one, else null. An unrecorded
    /// context costs nothing.
    @Unique
    private @Nullable ExecutionRecord akytheralabs$newRecord() {
        DataDrivenAnimatorScript self = (DataDrivenAnimatorScript)(Object)this;
        Animator animator = self.getScriptOwner();

        return EntityDebugSession.getInstance().isPossessed(animator.getOwner().getOriginal())
            ? new ExecutionRecord()
            : null;
    }
}
