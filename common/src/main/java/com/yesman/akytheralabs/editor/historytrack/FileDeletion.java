package com.yesman.akytheralabs.editor.historytrack;

/// File system change for deleting files/directories
/// and any registry bookkeeping the entry created from the files.
///
/// Same shape as [FileCreation]. Restoration state is captured in the
/// `create` lambda's closure — see [com.yesman.akytheralabs.AkytheraProject#buildRestoreFor].
public record FileDeletion(Runnable delete, Runnable create) implements WorkHistory {
    @Override
    public void undo() {
        create.run();
    }

    @Override
    public void redo() {
        delete.run();
    }

    /// Redoing a deletion wipes a file the user just restored — gated by
    /// [ConfirmDeletePopup] for the same reason [FileCreation]
    /// gates its undo.
    @Override
    public void requestRedoConfirmation(Runnable proceed) {
        ConfirmDeletePopup.open(proceed);
    }
}