package com.yesman.akytheralabs.akythera.blueprint;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.core.reflection.BlueprintLayout;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.gui.screen.ScriptBlueprintEditorScreen;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// A holder of a data pack entry that is one [BlueprintScriptData], edited in a [ScriptBlueprintEditorScreen].
public interface ScriptBlueprintHolder<T> extends DataHolder<T> {
    /// The members the editor is laid out with.
    BlueprintLayout layout();

    /// The class the graphs' Self is.
    Class<?> scriptOwner();

    BlueprintScriptData getScript();

    /// The parent constructors `File > Change Parent` offers, empty for a blueprint that takes no parent.
    default List<ScriptFunction> parentCandidates() {
        return List.of();
    }

    /// The parent constructor the blueprint is built with, null for a blueprint that takes no parent.
    default @Nullable ScriptFunction getParent() {
        return null;
    }

    /// Builds the blueprint with `parent`, one of [#parentCandidates], which [#layout] then follows.
    default void setParent(final ScriptFunction parent) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " takes no parent");
    }
}
