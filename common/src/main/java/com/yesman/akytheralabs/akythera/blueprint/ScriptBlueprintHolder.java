package com.yesman.akytheralabs.akythera.blueprint;

import com.yesman.akythera.core.reflection.BlueprintLayout;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.gui.screen.ScriptBlueprintEditorScreen;

/// A holder of a data pack entry that is one [BlueprintScriptData], edited in a [ScriptBlueprintEditorScreen].
public interface ScriptBlueprintHolder<T> extends DataHolder<T> {
    /// The members the editor is laid out with.
    BlueprintLayout layout();

    /// The class the graphs' Self is.
    Class<?> scriptOwner();

    BlueprintScriptData getScript();
}
