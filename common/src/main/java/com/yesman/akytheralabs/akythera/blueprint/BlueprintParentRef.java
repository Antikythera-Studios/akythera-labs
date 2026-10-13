package com.yesman.akytheralabs.akythera.blueprint;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.core.blueprint.DataDrivenEntityPatchBlueprint;
import com.yesman.akythera.core.blueprint.SourceCodeEntityPatchBlueprint;
import com.yesman.akythera.core.blueprint.widget.DataDrivenWidgetBlueprint;
import com.yesman.akythera.core.blueprint.widget.SourceCodeWidgetBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;

/// The parent a blueprint holder is built from, the editor-side mirror of the engine's data-driven / source code
/// blueprint split ([DataDrivenEntityPatchBlueprint] / [SourceCodeEntityPatchBlueprint],
/// [DataDrivenWidgetBlueprint] / [SourceCodeWidgetBlueprint]).
public sealed interface BlueprintParentRef {
    /// The constructor function the blueprint's object is built with.
    ScriptFunction ctor();

    /// Simple name of the class the blueprint runs as, shown beside the editor's title.
    String displayLabel();

    /// A class whose components, flows and variables are edited on screen.
    record ParentClass(ScriptFunction ctor) implements BlueprintParentRef {
        @Override
        public String displayLabel() {
            return ctor.functionSignature().returnType().getSimpleName();
        }
    }

    /// A hand-written script class registered in [AkytheraRegistries.Frozen#SCRIPT_TYPE].
    record SourceCodeScript(ScriptFunction ctor, ScriptType script) implements BlueprintParentRef {
        @Override
        public String displayLabel() {
            return script.type().getSimpleName();
        }
    }
}
