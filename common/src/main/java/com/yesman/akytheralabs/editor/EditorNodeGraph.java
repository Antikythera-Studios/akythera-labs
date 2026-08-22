package com.yesman.akytheralabs.editor;

import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Companion of [EditorEditable] for [ScriptGraphData]-typed holder fields: declares the shape of the field's
/// node-graph canvas so the Details panel's "Edit Node Graph" button can open a correctly seeded tab. Mirrors
/// the Anim Graph convention (raw `ScriptGraphData` field on the holder, root node seeded by the screen,
/// compile in the holder's `makeObject`).
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EditorNodeGraph {
    /// Names of the context locals the runtime consumer binds into the script `ExecutionContext`
    /// (e.g. `equipper`) — they become the canvas' entry node outputs. Parallel to
    /// [#contextParamTypes] (annotations cannot hold record lists).
    String[] contextParams() default {};

    /// Pin types of [#contextParams], so pin drags filter pure-function suggestions.
    Class<?>[] contextParamTypes() default {};

    /// The type the canvas' result root takes, which is the type the field's compiled tree yields.
    Class<?> resultType() default boolean.class;
}
