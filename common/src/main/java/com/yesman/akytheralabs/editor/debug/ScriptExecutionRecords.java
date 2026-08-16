package com.yesman.akytheralabs.editor.debug;

import com.yesman.akythera.api.data.modularize.node.ExecutionRecord;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/// The last recorded run of each of an animator script's entry points.
///
/// Implemented onto `DataDrivenAnimatorScript` by
/// [com.yesman.akytheralabs.mixin.MixinDataDrivenAnimatorScript]; the script class is final, so reach
/// this through an `(Object)` cast.
public interface ScriptExecutionRecords {
    @Nullable ExecutionRecord akytheralabs$getConstructRecord();

    @Nullable ExecutionRecord akytheralabs$getUpdateRecord();

    @Nullable ExecutionRecord akytheralabs$getPoseRecord();

    /// The record the editor canvas `canvasKey` collects into right now, or null when this script's
    /// entity is not the possessed one. Created on first use, so a canvas nothing samples never
    /// allocates one.
    @Nullable ExecutionRecord akytheralabs$liveRecord(String canvasKey);

    /// What the sub-trees deferred past `construct` produced since the last
    /// [#akytheralabs$clearLiveRecords], keyed by the
    /// [com.yesman.akytheralabs.akythera.animation.ScriptCanvasKeys] key of the canvas they were
    /// compiled from. A canvas nothing sampled has no entry.
    Map<String, ExecutionRecord> akytheralabs$getLiveRecords();

    /// Drops every live record, so a canvas whose nodes stopped being sampled reads empty rather than
    /// holding what it last showed.
    void akytheralabs$clearLiveRecords();
}
