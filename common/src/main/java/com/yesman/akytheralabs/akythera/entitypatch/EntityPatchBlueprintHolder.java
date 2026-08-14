package com.yesman.akytheralabs.akythera.entitypatch;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.AkytheraScriptFunctions;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.NodeTreeSourceRenderer;
import com.yesman.akytheralabs.akythera.animation.AnimatorScriptHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ArmatureComponentHolder;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.DTOProvider;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.dataholder.EntityPreviewModel;
import com.yesman.akytheralabs.dataholder.FailedComponentHolder;
import com.yesman.akytheralabs.akythera.animation.ScriptGraphCompiler;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentAttachableHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.EntityPatchComponentHolders;
import com.yesman.akytheralabs.akythera.entitypatch.component.RootAttachableComponent;
import com.yesman.akytheralabs.gui.widget.script.EntityPatchEntryNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import com.yesman.akytheralabs.gui.widget.script.node.EntityPatchEntryNodeData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.stream.Stream;

/// Modifiable data holder for an [EntityPatchBlueprint]
/// (registry [com.yesman.akythera.core.registry.AkytheraRegistries.DataPack#ENTITY_PATCH_KEY]).
///
/// As on [AnimatorScriptHolder], the editor keeps the blueprint's flows as one flat
/// [ScriptGraphData] canvas — the "Event Graph" — hosting all three entry points, `onConstruct` /
/// `onJoinWorld` / `update`, as distinct [EntityPatchEntryNodeWidget] nodes. [#makeObject] is the
/// compile step: it lowers the sub-flow rooted at each entry back into a tree [FlowNode] through
/// [ScriptGraphCompiler] and assembles the runtime [EntityPatchBlueprint].
///
/// Components are editor holders such as [ArmatureComponentHolder], keyed by the author-chosen name
/// the engine attaches them under. The patch itself is the root [ComponentAttachableHolder], as
/// [EntityPatch] is the root [com.yesman.akythera.api.component.ComponentAttachable]. Their
/// properties are edited in the Details panel; [#EDITOR_CODEC] persists the whole tree through
/// [ComponentAttachableHolder#componentsField], and [#makeObject] lowers each holder into its
/// runtime [ComponentBlueprint].
///
/// [#ctor] is the patch's parent — the [EntityPatch] subclass the engine instantiates — and its
/// return type is [#ownerType], the script owner every canvas on this blueprint is typed against.
public final class EntityPatchBlueprintHolder extends AbstractDataHolder<EntityPatchBlueprint>
    implements DTOProvider, ComponentAttachableHolder
{
    public static final Codec<EntityPatchBlueprintHolder> EDITOR_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            AkytheraRegistries.Frozen.SCRIPT_FUNCTION.byNameCodec().optionalFieldOf("parent")
                .forGetter(h -> Optional.of(h.ctor)),
            ComponentAttachableHolder.componentsField(() -> EntityPatchComponentHolders.REGISTRY)
                .forGetter(ComponentAttachableHolder::componentsOf),
            ScriptGraphData.CODEC.optionalFieldOf("event_graph", ScriptGraphData.EMPTY)
                .forGetter(h -> h.eventGraph),
            ScriptVariables.VariableInfo.CODEC.listOf().optionalFieldOf("variables", List.of())
                .forGetter(h -> h.variables),
            EntityPreviewModel.CODEC.optionalFieldOf("preview_model")
                .forGetter(ParseUtils.provideOptional(EntityPatchBlueprintHolder::getPreviewModel))
        ).apply(instance, (ctor, components, eventGraph, variables, pm) -> {
            EntityPatchBlueprintHolder h = new EntityPatchBlueprintHolder();
            ctor.ifPresent(h::setCtor);
            ComponentAttachableHolder.applyComponents(h, components);
            h.eventGraph = eventGraph;
            h.variables = new ArrayList<>(variables);
            pm.ifPresent(h::setPreviewModel);
            return h;
        })
    );

    /// The [ScriptFunction.Usage#ENTITY_PATCH_CTOR] function the engine builds this patch with. Its
    /// return type is the patch's class, and so the script owner type of every flow on it.
    private ScriptFunction ctor = defaultCtor();

    /// The attached components' editor holders, keyed by the name the engine attaches them under and
    /// insertion-ordered so the file is stable. Components that host children keep them on
    /// themselves, making this the root of the whole tree.
    private SequencedMap<String, ComponentDataHolder<?>> components = ComponentAttachableHolder.newComponentMap();

    /// The single Event Graph canvas, holding the three entry nodes and everything wired to them.
    private ScriptGraphData eventGraph = ScriptGraphData.EMPTY;

    private List<ScriptVariables.VariableInfo> variables = new ArrayList<>();

    @Nullable
    private EntityPreviewModel previewModel;

    public EntityPatchBlueprintHolder() {
    }

    /// ⚠ Requires the script-function registry to be filled (`Akythera.onModInitialized`).
    private static ScriptFunction defaultCtor() {
        return AkytheraScriptFunctions.ENTITY_PATCH_CONSTRUCTOR.value();
    }

    public ScriptFunction getCtor() {
        return ctor;
    }

    public void setCtor(ScriptFunction ctor) {
        this.ctor = ctor;
    }

    /// The patch's own class — what `self` is, and what a member [ScriptFunction]'s receiver slot has
    /// to accept for the function to be callable here.
    public Class<?> ownerType() {
        return ctor.functionSignature().returnType();
    }

    /// The patch is the root component host: anything marked [RootAttachableComponent] may be
    /// attached directly on it, and everything else only under a component that names it.
    @Override
    public List<Class<?>> attachableTypes() {
        return List.of(RootAttachableComponent.class);
    }

    @Override
    public void readFrom(EntityPatchBlueprint object) {
        // The runtime blueprint carries no on-canvas layout, so only the variables round-trip.
        this.ctor = object.ctor();
        this.variables = new ArrayList<>(object.variables());
    }

    @Override
    public EntityPatchBlueprint makeObject() {
        FlowNode onConstruct = compileEntry(EntityPatchEntryNodeWidget.Kind.ON_CONSTRUCT);
        FlowNode onJoinWorld = compileEntry(EntityPatchEntryNodeWidget.Kind.ON_JOIN_WORLD);
        FlowNode update = compileEntry(EntityPatchEntryNodeWidget.Kind.UPDATE);

        return new EntityPatchBlueprint(
            ComponentAttachableHolder.lowerComponents(components), ctor,
            onConstruct, onJoinWorld, update, List.copyOf(variables));
    }

    /// Lowers the sub-flow rooted at the `kind` entry node in the shared Event Graph canvas into a
    /// tree [FlowNode]. The entry is matched on its persisted kind marker, the `op` field.
    private FlowNode compileEntry(EntityPatchEntryNodeWidget.Kind kind) {
        return ScriptGraphCompiler.compile(eventGraph, ownerType(), row ->
            row.data() instanceof EntityPatchEntryNodeData entry && entry.entryKind() == kind);
    }

    /// Everything the attached components reference — armature accessors, animator script holders
    /// and so on — plus every asset the Event Graph's nodes bind. Flattens each component holder's
    /// own [DataHolder#referencedReferences], attached children included.
    @Override
    public Stream<EditorReference> referencedReferences() {
        return Stream.concat(
            ComponentAttachableHolder.flatten(this).flatMap(DataHolder::referencedReferences),
            eventGraph.referencedReferences()
        );
    }

    /// The first failure the decode guard salvaged: a defaults-fallback or unknown-type component
    /// placeholder, or an unresolved node kept on the Event Graph.
    @Override
    public @Nullable Exception getNestedFailureException() {
        Exception component = AbstractDataHolder.firstNestedFailure(components.values());
        if (component != null) return component;

        return eventGraph.firstFailure();
    }

    /// Tokens for `entity_patch_blueprint_template.ftl`: the parent constructor, the three event
    /// flows and the variable list as Java constructor expressions from [NodeTreeSourceRenderer],
    /// lowered through [#makeObject], plus the attached components' constructor expressions. The
    /// indent bases — 4 for flows, 5 for list elements — match the template's splice points.
    @Override
    public Map<String, Object> getDTO() {
        EntityPatchBlueprint blueprint = makeObject();

        Map<String, Object> dto = new HashMap<>();
        dto.put("components", ComponentDataHolder.sourcesOf(healthyComponents()));
        dto.put("parent", NodeTreeSourceRenderer.scriptFunction(blueprint.ctor()));
        dto.put("on_construct", NodeTreeSourceRenderer.flow(blueprint.onConstruct(), 4));
        dto.put("on_join_world", NodeTreeSourceRenderer.flow(blueprint.onJoinWorld(), 4));
        dto.put("update", NodeTreeSourceRenderer.flow(blueprint.update(), 4));
        dto.put("variables", blueprint.variables().stream()
            .map(v -> NodeTreeSourceRenderer.variableInfo(v, 5)).toList());

        return dto;
    }

    /// ***************************************************************
    /// Components (editor holders keyed by attachment name)
    /// ***************************************************************
    /// The component map minus unknown-type placeholders. A [FailedComponentHolder] has no source
    /// form and round-trips only through its raw JSON.
    private SequencedMap<String, ComponentDataHolder<?>> healthyComponents() {
        SequencedMap<String, ComponentDataHolder<?>> healthy = ComponentAttachableHolder.newComponentMap();
        components.forEach((name, c) -> {
            if (!(c instanceof FailedComponentHolder)) healthy.put(name, c);
        });
        return healthy;
    }

    @Override
    public SequencedMap<String, ComponentDataHolder<?>> getComponents() {
        return components;
    }

    public void setComponents(Map<String, ComponentDataHolder<?>> components) {
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