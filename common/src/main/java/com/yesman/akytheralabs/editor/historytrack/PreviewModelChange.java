package com.yesman.akytheralabs.editor.historytrack;

import com.yesman.akythera.client.gui.widget.ModelPreviewer;
import com.yesman.akytheralabs.dataholder.EntityPreviewModel;
import com.yesman.akytheralabs.dataholder.animation.BlendSpace2DHolder;
import org.jspecify.annotations.Nullable;

/// Single-shot composite change for a BlendSpace2D preview-model selection
/// (written together via the picker modal).
///
/// The [EntityPreviewModel] stays encapsulated here — callers hand in the old/new
/// values and the history class owns holder writes and previewer refresh.
public final class PreviewModelChange implements WorkHistory {
    private final BlendSpace2DHolder holder;
    private final ModelPreviewer previewer;
    private final @Nullable EntityPreviewModel oldModel;
    private final @Nullable EntityPreviewModel newModel;

    public PreviewModelChange(
        BlendSpace2DHolder holder,
        ModelPreviewer previewer,
        @Nullable EntityPreviewModel oldModel,
        @Nullable EntityPreviewModel newModel
    ) {
        this.holder = holder;
        this.previewer = previewer;
        this.oldModel = oldModel;
        this.newModel = newModel;
    }

    @Override
    public void undo() {
        apply(oldModel);
    }

    @Override
    public void redo() {
        apply(newModel);
    }

    private void apply(@Nullable EntityPreviewModel model) {
        holder.setPreviewModel(model);
        EntityPreviewModel.display(previewer, model, true, true);
    }
}
