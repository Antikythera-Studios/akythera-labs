package com.yesman.akytheralabs.gui.screen;

import com.yesman.akytheralabs.dataholder.EntityPreviewModelMapping;

/// An editor screen whose previewer shows an entity type as [EntityPreviewModelMapping] binds it.
public interface PreviewModelScreen {
    /// Shows the previewed entity type again, under its current binding.
    void refreshPreviewModel();

    /// Eases the previewer's camera to frame the previewed model.
    void framePreviewModel();
}
