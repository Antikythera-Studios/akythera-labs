package com.yesman.akytheralabs.akythera.animation;

import com.mojang.serialization.Codec;
import com.yesman.akythera.api.animation.warping.MotionWarpTarget;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.blueprint.warping.MotionWarpTargetScriptBlueprint;
import com.yesman.akythera.core.reflection.BlueprintLayout;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintScriptData;
import com.yesman.akytheralabs.akythera.blueprint.ScriptBlueprintHolder;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.gui.panel.script.BlueprintEditorSession;
import com.yesman.akytheralabs.gui.widget.script.Compilation;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/// Modifiable data holder for [MotionWarpTargetScriptBlueprint]: one [BlueprintScriptData] laid out by
/// [MotionWarpTarget], with the entity patch as the graphs' Self.
public final class MotionWarpTargetScriptHolder extends AbstractDataHolder<MotionWarpTargetScriptBlueprint>
    implements ScriptBlueprintHolder<MotionWarpTargetScriptBlueprint>
{
    public static final Codec<MotionWarpTargetScriptHolder> EDITOR_CODEC = BlueprintScriptData.MAP_CODEC.codec().xmap(
        script -> {
            MotionWarpTargetScriptHolder holder = new MotionWarpTargetScriptHolder();

            holder.script = script;
            script.typeProperties(holder.layout());

            return holder;
        },
        MotionWarpTargetScriptHolder::getScript
    );

    private BlueprintScriptData script = new BlueprintScriptData();

    @Override
    public BlueprintLayout layout() {
        return ScriptModularizeHelper.scanBlueprintLayout(MotionWarpTarget.class);
    }

    @Override
    public Class<?> scriptOwner() {
        return EntityPatch.class;
    }

    @Override
    public BlueprintScriptData getScript() {
        return script;
    }

    /// Takes the body's values. A compiled body carries no canvas, so the graphs start empty.
    @Override
    public void readFrom(final MotionWarpTargetScriptBlueprint object) {
        BlueprintBody body = object.body();

        script = new BlueprintScriptData(
            ScriptGraphData.EMPTY, Map.of(), List.of(), body.properties(),
            body.variables().stream().map(BlueprintScriptData.Variable::of).toList()
        );
        script.typeProperties(layout());
    }

    @Override
    public MotionWarpTargetScriptBlueprint makeObject() {
        return new MotionWarpTargetScriptBlueprint(script.compile(layout(), EntityPatch.class));
    }

    @Override
    public void compile(final Compilation compilation) {
        BlueprintEditorSession.compileCanvases(compilation, script, layout(), EntityPatch.class, null);
        compilation.unresolved(script.failedNodes());
        compilation.build(this::makeObject);
    }

    @Override
    public Stream<EditorReference> referencedReferences() {
        return script.referencedReferences();
    }

    @Override
    public @Nullable Exception getNestedFailureException() {
        return script.firstFailure();
    }
}
