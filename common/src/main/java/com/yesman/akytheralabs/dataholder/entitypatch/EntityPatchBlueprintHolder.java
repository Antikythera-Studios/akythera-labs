package com.yesman.akytheralabs.dataholder.entitypatch;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.dataholder.EntityPreviewModel;
import com.yesman.akytheralabs.dataholder.animation.ScriptGraphCompiler;
import com.yesman.akytheralabs.dataholder.entitypatch.component.EntityPatchComponentHolders;
import com.yesman.akytheralabs.gui.widget.script.EntityPatchEntryNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

/// Modifiable data holder for an [EntityPatchBlueprint]
/// (registry [com.yesman.akythera.core.registry.AkytheraRegistries.DataPack#ENTITY_PATCH_KEY]).
///
/// Like [com.yesman.akytheralabs.dataholder.animation.AnimatorScriptHolder], the editor keeps the
/// blueprint's flows as one **flat** [ScriptGraphData] canvas (the "Event Graph") that hosts all
/// three entry points — `onConstruct` / `onJoinWorld` / `update` — as distinct
/// [EntityPatchEntryNodeWidget] nodes. [#makeObject] is the "compile" step: it lowers the sub-flow
/// rooted at each entry back into a tree [FlowNode] via [ScriptGraphCompiler] and assembles the
/// runtime [EntityPatchBlueprint].
///
/// Components are editor holders (e.g. [com.yesman.akytheralabs.dataholder.entitypatch.component.ArmatureComponentHolder])
/// keyed by their [com.yesman.akythera.core.registry.AkytheraRegistries.Frozen#COMPONENT_BLUEPRINT_TYPES]
/// type id — a component is distinguished by its class, so one holder per type. Their properties are
/// edited in the Details panel; [#EDITOR_CODEC] persists them through
/// [EntityPatchComponentHolders#CODEC]'s type dispatch, and [#makeObject] lowers each holder into its
/// runtime [ComponentBlueprint].
public final class EntityPatchBlueprintHolder extends AbstractDataHolder<EntityPatchBlueprint> {
    /// The script owner type for an entity patch. Drives [ScriptGraphCompiler]'s `self` defaulting of
    /// unwired owner ("Target") pins, and matches the `@ScriptFunction` methods declared on [EntityPatch].
    private static final Class<?> OWNER_TYPE = EntityPatch.class;

    public static final Codec<EntityPatchBlueprintHolder> EDITOR_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            EntityPatchComponentHolders.CODEC.listOf().optionalFieldOf("components", List.of())
                .forGetter(h -> List.copyOf(h.components.values())),
            ScriptGraphData.CODEC.optionalFieldOf("event_graph", ScriptGraphData.EMPTY)
                .forGetter(h -> h.eventGraph),
            ScriptVariables.VariableInfo.CODEC.listOf().optionalFieldOf("variables", List.of())
                .forGetter(h -> h.variables),
            EntityPreviewModel.CODEC.optionalFieldOf("preview_model")
                .forGetter(ParseUtils.provideOptional(EntityPatchBlueprintHolder::getPreviewModel))
        ).apply(instance, (components, eventGraph, variables, pm) -> {
            EntityPatchBlueprintHolder h = new EntityPatchBlueprintHolder();
            components.forEach(c -> h.components.put(EntityPatchComponentHolders.typeIdOf(c), c));
            h.eventGraph = eventGraph;
            h.variables = new ArrayList<>(variables);
            pm.ifPresent(h::setPreviewModel);
            return h;
        })
    );

    /// The attached components' editor holders, keyed by component **type** id (a component is
    /// distinguished by its class, so one entry per type; insertion-ordered so the file is stable).
    private LinkedHashMap<Identifier, AbstractDataHolder<?>> components = new LinkedHashMap<>();

    /// The single Event Graph canvas, holding the three entry nodes and everything wired to them.
    private ScriptGraphData eventGraph = ScriptGraphData.EMPTY;

    private List<ScriptVariables.VariableInfo> variables = new ArrayList<>();

    @Nullable
    private EntityPreviewModel previewModel;

    public EntityPatchBlueprintHolder() {
    }

    @Override
    public void readFrom(EntityPatchBlueprint object) {
        // The runtime blueprint carries no on-canvas layout, so the flat Event Graph cannot be
        // reconstructed from it (decompiling the flow trees is future work). Only the variables —
        // which round-trip — are recovered; the editor itself always loads via EDITOR_CODEC.
        this.variables = new ArrayList<>(object.variables());
    }

    @Override
    public EntityPatchBlueprint makeObject() {
        FlowNode onConstruct = compileEntry(EntityPatchEntryNodeWidget.Kind.ON_CONSTRUCT);
        FlowNode onJoinWorld = compileEntry(EntityPatchEntryNodeWidget.Kind.ON_JOIN_WORLD);
        FlowNode update = compileEntry(EntityPatchEntryNodeWidget.Kind.UPDATE);

        // Each component holder lowers to its runtime blueprint. Freshly added components carry
        // dangling placeholder references until authored in the Details panel — the record still
        // builds; reference validation is a compile/export-time concern, not handled yet.
        List<ComponentBlueprint<?, ?>> componentBlueprints = components.values().stream()
            .<ComponentBlueprint<?, ?>>map(h -> (ComponentBlueprint<?, ?>) h.makeObject())
            .toList();

        return new EntityPatchBlueprint(componentBlueprints, onConstruct, onJoinWorld, update, List.copyOf(variables));
    }

    /// Lowers the sub-flow rooted at the `kind` entry node in the shared Event Graph canvas into a
    /// tree [FlowNode]. Matching is by the entry node's persisted kind marker (its `op` field), so
    /// the three param-less entries are distinguished without relying on output-label heuristics.
    private FlowNode compileEntry(EntityPatchEntryNodeWidget.Kind kind) {
        return ScriptGraphCompiler.compile(eventGraph, OWNER_TYPE, node ->
            node.kind() == ScriptGraphData.Kind.ENTITY_PATCH_ENTRY
                && node.op().map(kind.name()::equals).orElse(false));
    }

    /// Everything the attached components reference (armature accessors, animator script holders,
    /// ...), so renaming a referenced entry re-saves this patch's JSON. Flattens each component
    /// holder's own [DataHolder#referencedReferences].
    @Override
    public Stream<EditorReference> referencedReferences() {
        return components.values().stream().flatMap(DataHolder::referencedReferences);
    }

    /// ***************************************************************
    /// Components (editor holders keyed by component type id)
    /// ***************************************************************
    public LinkedHashMap<Identifier, AbstractDataHolder<?>> getComponents() {
        return components;
    }

    public void setComponents(LinkedHashMap<Identifier, AbstractDataHolder<?>> components) {
        this.components = new LinkedHashMap<>(components);
    }

    /// ***************************************************************
    /// Event Graph (flat editor form)
    /// ***************************************************************
    public ScriptGraphData getEventGraph() {
        return eventGraph;
    }

    public void setEventGraph(ScriptGraphData eventGraph) {
        this.eventGraph = eventGraph;
    }

    /// ***************************************************************
    /// Variables
    /// ***************************************************************
    public List<ScriptVariables.VariableInfo> getVariables() {
        return variables;
    }

    public void setVariables(List<ScriptVariables.VariableInfo> variables) {
        this.variables = new ArrayList<>(variables);
    }

    /// ***************************************************************
    /// Preview model (editor-only)
    /// ***************************************************************
    public @Nullable EntityPreviewModel getPreviewModel() {
        return previewModel;
    }

    public void setPreviewModel(@Nullable EntityPreviewModel previewModel) {
        this.previewModel = previewModel;
    }
}