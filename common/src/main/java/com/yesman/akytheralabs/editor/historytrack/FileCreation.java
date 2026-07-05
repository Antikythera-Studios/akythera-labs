package com.yesman.akytheralabs.editor.historytrack;

///
/// Undoing a create deletes the just-created entry — gated by
/// [ConfirmDeletePopup] since the deletion is destructive.
public record FileCreation(Runnable create, Runnable delete) implements WorkHistory {
    @Override
    public void undo() {
        delete.run();
    }

    @Override
    public void redo() {
        create.run();
    }

    @Override
    public void requestUndoConfirmation(Runnable proceed) {
        ConfirmDeletePopup.open(proceed);
    }
}