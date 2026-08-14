package com.yesman.akytheralabs.editor.debug;

import com.yesman.akythera.api.data.modularize.node.ExecutionRecord;
import org.jspecify.annotations.Nullable;

/// The last recorded run of each of an animator script's entry points.
///
/// Implemented onto `DataDrivenAnimatorScript` by
/// [com.yesman.akytheralabs.mixin.MixinDataDrivenAnimatorScript]; the script class is final, so reach
/// this through an `(Object)` cast.
public interface ScriptExecutionRecords {
    @Nullable ExecutionRecord akytheralabs$getConstructRecord();

    @Nullable ExecutionRecord akytheralabs$getUpdateRecord();

    @Nullable ExecutionRecord akytheralabs$getPoseRecord();
}
