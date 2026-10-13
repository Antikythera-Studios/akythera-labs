package com.yesman.akytheralabs.akythera.particle;

import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.client.api.particle.module.ParticleInitializerModule;
import com.yesman.akythera.client.api.particle.module.ParticleUpdateModule;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.reflection.BlueprintLayout;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintScriptData;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/// Modifiable data holder for a particle module written as a node graph: one [BlueprintScriptData] laid out by the
/// module's interface, [ParticleInitializerModule] or [ParticleUpdateModule], with the emitter as the script owner.
///
/// @param <T> the data pack entry
public abstract class ParticleModuleScriptHolder<T> extends AbstractDataHolder<T> {
    private final Class<?> moduleInterface;

    protected BlueprintScriptData script = new BlueprintScriptData();

    protected ParticleModuleScriptHolder(final Class<?> moduleInterface) {
        this.moduleInterface = moduleInterface;
    }

    /// The event of the module's interface the editor is laid out with.
    public BlueprintLayout layout() {
        return ScriptModularizeHelper.scanBlueprintLayout(moduleInterface);
    }

    public BlueprintScriptData getScript() {
        return script;
    }

    /// Takes `body`'s values. A compiled body carries no canvas, so the Event Graph starts empty.
    protected void readBody(final BlueprintBody body) {
        script = new BlueprintScriptData(
            ScriptGraphData.EMPTY, Map.of(), List.of(), body.properties(),
            body.variables().stream().map(BlueprintScriptData.Variable::of).toList()
        );
    }

    /// The node graph lowered to what the engine runs.
    public BlueprintBody compileBody() {
        return script.compile(layout(), AkytheraParticleEmitter.class);
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
