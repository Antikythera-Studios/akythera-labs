package com.yesman.akytheralabs.akythera.blueprint;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.data.modularize.node.ParentCallNode;
import com.yesman.akythera.api.data.modularize.node.SequenceNode;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.blueprint.script.FunctionGraph;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.reflection.BlueprintLayout;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.animation.ScriptGraphCompiler;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.editor.StoredJson;
import com.yesman.akytheralabs.gui.panel.main.processors.ResourceProcessor;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import com.yesman.akytheralabs.gui.widget.script.node.GraphNode;
import com.yesman.akytheralabs.gui.widget.script.node.MethodEntryNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.ParentCallNodeData;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// The editor form of a [BlueprintBody]: one Event Graph holding an entry node per event, one canvas per
/// overridden function and per declared function, the property values and the member variables. [#compile]
/// lowers it for a parent's [BlueprintLayout].
///
/// Data laid over a parent script, a [#dataParent], a [#sourceParent] or a [#holderParent], holds only what it
/// changes: an event reaches the parent's through a Parent node, and a property, a variable default value or a
/// function it leaves out is the data parent's.
public final class BlueprintScriptData {
    /// Writes the five fields beside the fields of the holder that holds the data. Reads no data parent.
    public static final MapCodec<BlueprintScriptData> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ScriptGraphData.CODEC.optionalFieldOf("event_graph", ScriptGraphData.EMPTY)
                    .forGetter(BlueprintScriptData::getEventGraph),
                Codec.unboundedMap(Codec.STRING, ScriptGraphData.CODEC).optionalFieldOf("overrides", Map.of())
                    .forGetter(BlueprintScriptData::getOverrides),
                UserFunction.CODEC.listOf().optionalFieldOf("functions", List.of())
                    .forGetter(BlueprintScriptData::getFunctions),
                Codec.unboundedMap(Codec.STRING, ExtraCodecs.JSON).optionalFieldOf("properties", Map.of())
                    .forGetter(BlueprintScriptData::propertyJson),
                Variable.CODEC.listOf().optionalFieldOf("variables", List.of())
                    .forGetter(BlueprintScriptData::getVariables)
            ).apply(instance, BlueprintScriptData::new)
    );

    /// Graph-space distance between an entry and the Parent node [#seedParentCalls] wires behind it.
    private static final int PARENT_CALL_GAP = 240;

    private ScriptGraphData eventGraph;

    /// The canvas of each overridden function, by method name.
    private final Map<String, ScriptGraphData> overrides;

    private final List<UserFunction> functions;

    /// The property values in their type's codec form, by [BlueprintLayout.Property#key]. Plain until
    /// [#typeProperties] reads them as their types.
    private final Map<String, StoredJson> properties;

    private final List<Variable> variables;

    /// The entry of the holder's own registry this data is laid over, as the shared live holder. Null for none.
    private @Nullable Holder<?> dataParent;

    /// The source code script the graphs are laid over. Null for none. The holder persists it.
    private @Nullable ScriptType sourceParent;

    /// Whether the graphs are laid over a parent script the holder resolves itself, as an item patch's are over
    /// the script of its parent patch.
    private boolean holderParent;

    public BlueprintScriptData() {
        this(ScriptGraphData.EMPTY, Map.of(), List.of(), Map.of(), List.of());
    }

    public BlueprintScriptData(
        final ScriptGraphData eventGraph,
        final Map<String, ScriptGraphData> overrides,
        final List<UserFunction> functions,
        final Map<String, JsonElement> properties,
        final List<Variable> variables
    ) {
        this.eventGraph = eventGraph;
        this.overrides = new LinkedHashMap<>(overrides);
        this.functions = new ArrayList<>(functions);
        this.properties = new LinkedHashMap<>();
        this.variables = new ArrayList<>(variables);

        properties.forEach((key, json) -> this.properties.put(key, new StoredJson.Plain(json)));
    }

    /// [#MAP_CODEC] with a `data_parent` field beside it, naming an entry of `registry` by the id the engine
    /// resolves it under.
    @SuppressWarnings("unchecked")
    public static <T> MapCodec<BlueprintScriptData> mapCodec(final ResourceKey<? extends Registry<T>> registry) {
        return RecordCodecBuilder.mapCodec(
            instance ->
                instance.group(
                    Identifier.CODEC.<Holder<T>>xmap(
                            id -> dataParentOf(registry, id),
                            holder -> ResourceProcessor.exportedId(holder.unwrapKey().orElseThrow()))
                        .optionalFieldOf("data_parent")
                        .forGetter(data -> Optional.ofNullable((Holder<T>) data.dataParent)),
                    MAP_CODEC.forGetter(data -> data)
                ).apply(instance, (parent, data) -> {
                    data.dataParent = parent.orElse(null);

                    return data;
                })
        );
    }

    /// The data of the compiled `body` of an entry of `registry` laid over `dataParent`: its values and its data
    /// parent. A compiled body carries no canvas, so the graphs start empty.
    public static <T> BlueprintScriptData of(
        final ResourceKey<? extends Registry<T>> registry, final BlueprintBody body, final Optional<Identifier> dataParent
    ) {
        BlueprintScriptData data = new BlueprintScriptData(
            ScriptGraphData.EMPTY, Map.of(), List.of(), body.properties(), body.variables().stream().map(Variable::of).toList());

        data.dataParent = dataParent.map(parent -> dataParentOf(registry, parent)).orElse(null);

        return data;
    }

    /// The shared live holder of the entry of `registry` the engine resolves under `exportedId`.
    public static <T> Holder<T> dataParentOf(final ResourceKey<? extends Registry<T>> registry, final Identifier exportedId) {
        return EditorHolderCodecs.resolve(registry, ResourceProcessor.projectId(registry, exportedId));
    }

    /// Matches the entry node of the method or function `name`.
    public static Predicate<GraphNode> entryOf(final String name) {
        return row -> row.data() instanceof MethodEntryNodeData entry && entry.method().equals(name);
    }

    /// What the canvases of this data know of a parent laid out by `layout`: its members, the functions this data
    /// declares, and the ones it inherits. A declared function named like an inherited one overrides it, and may
    /// call it through a Parent node.
    public BlueprintMembers members(final BlueprintLayout layout) {
        List<UserFunction> inherited = inheritedFunctions();

        return BlueprintMembers.of(layout, hasParentScript(), name -> {
            BlueprintSignature overridden = inherited.stream()
                .filter(function -> function.name().equals(name))
                .map(UserFunction::signature)
                .findFirst()
                .orElse(null);

            for (UserFunction function : functions) {
                if (function.name().equals(name)) return function.signature().withParentCallable(overridden != null);
            }

            return overridden;
        });
    }

    // ***************************************************************
    // Parent script
    // ***************************************************************

    public @Nullable Holder<?> getDataParent() {
        return dataParent;
    }

    public void setDataParent(final @Nullable Holder<?> dataParent) {
        this.dataParent = dataParent;
    }

    public @Nullable ScriptType getSourceParent() {
        return sourceParent;
    }

    public void setSourceParent(final @Nullable ScriptType sourceParent) {
        this.sourceParent = sourceParent;
    }

    public void setHolderParent(final boolean holderParent) {
        this.holderParent = holderParent;
    }

    /// Whether the graphs are laid over a parent script, which a Parent node on the Event Graph calls.
    public boolean hasParentScript() {
        return dataParent != null || sourceParent != null || holderParent;
    }

    /// [#dataParent] as the id the engine resolves the entry under.
    public Optional<Identifier> exportedDataParent() {
        return Optional.ofNullable(dataParent).map(parent -> ResourceProcessor.exportedId(parent.unwrapKey().orElseThrow()));
    }

    /// Wires a Parent node behind every entry of the Event Graph that leads nowhere, so each such event runs the
    /// parent script's.
    public void seedParentCalls() {
        List<GraphNode> nodes = new ArrayList<>(eventGraph.nodes());
        List<ScriptGraphData.WireData> wires = new ArrayList<>(eventGraph.wires());
        int nextId = Stream.concat(
            eventGraph.nodes().stream().map(GraphNode::id), eventGraph.failedNodes().stream().map(ScriptGraphData.FailedNode::id)
        ).mapToInt(Integer::intValue).max().orElse(-1) + 1;

        for (GraphNode node : eventGraph.nodes()) {
            if (!(node.data() instanceof MethodEntryNodeData entry)) continue;
            if (eventGraph.wires().stream().anyMatch(wire -> wire.from() == node.id() && wire.fromPin() == 0)) continue;

            nodes.add(new GraphNode(nextId, node.x() + PARENT_CALL_GAP, node.y(), new ParentCallNodeData(Optional.of(entry.method()))));
            wires.add(new ScriptGraphData.WireData(node.id(), 0, nextId, 0));
            nextId++;
        }

        eventGraph = new ScriptGraphData(nodes, wires, eventGraph.failedNodes());
    }

    /// The data of each data parent, nearest first. Ends at a parent the project does not hold, and at one met
    /// twice.
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<BlueprintScriptData> ancestors() {
        List<BlueprintScriptData> ancestors = new ArrayList<>();
        Set<BlueprintScriptData> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Holder<?> parent = dataParent;

        visited.add(this);

        while (parent != null) {
            Optional<DataHolder<?>> holder = AkytheraProject.getCurrentProject().getDataHolder((ResourceKey) parent.unwrapKey().orElseThrow());
            BlueprintScriptData data = holder.map(DataHolder::getScript).orElse(null);

            if (data == null || !visited.add(data)) break;

            ancestors.add(data);
            parent = data.dataParent;
        }

        return ancestors;
    }

    /// The member variables of the data parents, the root's first. A parent declaring an ancestor's variable
    /// again takes its place.
    public List<Variable> inheritedVariables() {
        Map<String, Variable> inherited = new LinkedHashMap<>();

        for (BlueprintScriptData ancestor : ancestors().reversed()) {
            ancestor.variables.forEach(variable -> inherited.put(variable.name(), variable));
        }

        return List.copyOf(inherited.values());
    }

    /// The property values the data parents set, by [BlueprintLayout.Property#key], each the nearest parent's.
    public Map<String, JsonElement> inheritedProperties() {
        Map<String, JsonElement> inherited = new LinkedHashMap<>();

        for (BlueprintScriptData ancestor : ancestors().reversed()) {
            inherited.putAll(ancestor.propertyJson());
        }

        return inherited;
    }

    /// The functions the data parents declare and do not keep private, each the nearest parent's.
    public List<UserFunction> inheritedFunctions() {
        Map<String, UserFunction> inherited = new LinkedHashMap<>();

        for (BlueprintScriptData ancestor : ancestors().reversed()) {
            ancestor.functions.forEach(function -> inherited.put(function.name(), function));
        }

        return inherited.values().stream().filter(function -> !function.isPrivate()).toList();
    }

    /// The names of the functions the data parents keep private, which this data may neither call nor declare.
    public Set<String> privateInheritedFunctions() {
        return ancestors().stream()
            .flatMap(ancestor -> ancestor.functions.stream())
            .filter(UserFunction::isPrivate)
            .map(UserFunction::name)
            .collect(Collectors.toSet());
    }

    /// Lowers this data for a parent laid out by `layout`, whose script owner is `ownerType`. An event whose
    /// entry node leads nowhere, or only into a Parent call, is left out, and so falls back to the parent.
    ///
    /// @throws IllegalStateException when two declared functions share a name, one has the name of a function a
    ///                               data parent keeps private, a canvas carries an unresolved node, or a
    ///                               variable's default value does not decode
    public BlueprintBody compile(final BlueprintLayout layout, final Class<?> ownerType) {
        BlueprintMembers members = members(layout);
        Map<String, FlowNode> methods = new LinkedHashMap<>();

        for (BlueprintLayout.Signature event : layout.events()) {
            FlowNode flow = ScriptGraphCompiler.compile(eventGraph, ownerType, entryOf(event.name()), members, null, event.name());

            List<FlowNode> statements = flow instanceof SequenceNode sequence ? sequence.executionBlocks() : List.of(flow);

            if (!statements.isEmpty() && !(statements.size() == 1 && statements.getFirst() instanceof ParentCallNode)) {
                methods.put(event.key(), flow);
            }
        }

        for (BlueprintLayout.Signature function : layout.functions()) {
            ScriptGraphData graph = overrides.get(function.name());

            if (graph != null) {
                methods.put(function.key(), ScriptGraphCompiler.compile(
                    graph, ownerType, entryOf(function.name()), members, function.name(), function.name()));
            }
        }

        Map<String, FunctionGraph> declared = new LinkedHashMap<>();
        Set<String> hidden = privateInheritedFunctions();

        for (UserFunction function : functions) {
            if (declared.containsKey(function.name())) {
                throw new IllegalStateException("More than one function is named " + function.name());
            }

            if (hidden.contains(function.name())) {
                throw new IllegalStateException("A data parent keeps a function named " + function.name() + " private");
            }

            declared.put(function.name(), new FunctionGraph(
                function.parameters(),
                function.returnType(),
                ScriptGraphCompiler.compile(function.graph(), ownerType, entryOf(function.name()), members, function.name(), function.name())
            ));
        }

        return new BlueprintBody(methods, declared, propertyJson(), variables.stream().map(Variable::toInfo).toList());
    }

    /// Every canvas this data holds, the Event Graph first.
    public Stream<ScriptGraphData> canvases() {
        return Stream.of(
            Stream.of(eventGraph),
            overrides.values().stream(),
            functions.stream().map(UserFunction::graph)
        ).flatMap(canvas -> canvas);
    }

    /// Replaces every canvas this data holds by what `mapper` makes of it.
    public void mapCanvases(final UnaryOperator<ScriptGraphData> mapper) {
        eventGraph = mapper.apply(eventGraph);
        overrides.replaceAll((_, canvas) -> mapper.apply(canvas));
        functions.replaceAll(function -> function.withGraph(mapper.apply(function.graph())));
    }

    /// The shared rename-tracking instances every canvas binds, and every property value and variable default
    /// value names.
    public Stream<EditorReference> referencedReferences() {
        return Stream.of(
            dataParent == null ? Stream.<EditorReference>empty() : EditorHolderCodecs.referenceOf(dataParent),
            canvases().flatMap(ScriptGraphData::referencedReferences),
            properties.values().stream().flatMap(StoredJson::referencedReferences),
            variables.stream().flatMap(Variable::referencedReferences)
        ).flatMap(references -> references);
    }

    /// Reads the property values as the types `layout` gives them, so each holds the project entries and textures
    /// it names. A key `layout` does not know stays as it is.
    public void typeProperties(final BlueprintLayout layout) {
        for (BlueprintLayout.Property property : layout.properties()) {
            properties.computeIfPresent(property.key(), (_, value) -> StoredJson.of(property.type(), value.json()));
        }
    }

    /// The first unresolved node a canvas kept, or null when every canvas is clean.
    public @Nullable Exception firstFailure() {
        return canvases().map(ScriptGraphData::firstFailure).filter(Objects::nonNull).findFirst().orElse(null);
    }

    /// The unresolved nodes of every canvas.
    public List<ScriptGraphData.FailedNode> failedNodes() {
        return canvases().flatMap(canvas -> canvas.failedNodes().stream()).toList();
    }

    public ScriptGraphData getEventGraph() {
        return eventGraph;
    }

    public void setEventGraph(final ScriptGraphData eventGraph) {
        this.eventGraph = eventGraph;
    }

    /// The live override map, by method name.
    public Map<String, ScriptGraphData> getOverrides() {
        return overrides;
    }

    /// The live declared function list.
    public List<UserFunction> getFunctions() {
        return functions;
    }

    /// The live property map, by [BlueprintLayout.Property#key].
    public Map<String, StoredJson> getProperties() {
        return properties;
    }

    /// The property values as they are written now, by [BlueprintLayout.Property#key].
    public Map<String, JsonElement> propertyJson() {
        Map<String, JsonElement> json = new LinkedHashMap<>();
        properties.forEach((key, value) -> json.put(key, value.json()));

        return json;
    }

    public List<Variable> getVariables() {
        return variables;
    }

    public void setVariables(final List<Variable> variables) {
        this.variables.clear();
        this.variables.addAll(variables);
    }

    /// A member variable, its default value held in its type's codec form.
    ///
    /// @param storedDefault null for none
    /// @param sync          forced to [ScriptVariables.SyncDirection#NONE] when the type carries no stream codec
    public record Variable(
        String name, ScriptType type, @Nullable StoredJson storedDefault, ScriptVariables.SyncDirection sync
    ) {
        public static final Codec<Variable> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.STRING.fieldOf("name").forGetter(Variable::name),
                    ScriptType.CODEC.fieldOf("type").forGetter(Variable::type),
                    ExtraCodecs.JSON.optionalFieldOf("default_value").forGetter(Variable::defaultValue),
                    ScriptVariables.SyncDirection.CODEC.optionalFieldOf("sync", ScriptVariables.SyncDirection.NONE)
                        .forGetter(Variable::sync)
                ).apply(instance, Variable::new)
        );

        public Variable {
            if (!type.isSyncable()) sync = ScriptVariables.SyncDirection.NONE;
        }

        public Variable(
            final String name, final ScriptType type, final Optional<JsonElement> defaultValue, final ScriptVariables.SyncDirection sync
        ) {
            this(name, type, defaultValue.map(json -> StoredJson.of(type, json)).orElse(null), sync);
        }

        /// The default value as it is written now.
        public Optional<JsonElement> defaultValue() {
            return Optional.ofNullable(storedDefault).map(StoredJson::json);
        }

        public Stream<EditorReference> referencedReferences() {
            return storedDefault == null ? Stream.empty() : storedDefault.referencedReferences();
        }

        /// `info` with its default value encoded through the type's codec.
        ///
        /// @throws IllegalStateException when the default value does not encode
        @SuppressWarnings("unchecked")
        public static Variable of(final ScriptVariables.VariableInfo info) {
            Optional<JsonElement> json = info.defaultVal() == null || info.type().codec() == null
                ? Optional.empty()
                : Optional.of(((Codec<Object>) info.type().codec())
                    .encodeStart(JsonOps.INSTANCE, info.defaultVal())
                    .getOrThrow(IllegalStateException::new));

            return new Variable(info.name(), info.type(), json, info.sync());
        }

        /// The engine form, its default value decoded through the type's codec. A type with no codec starts
        /// unset.
        ///
        /// @throws IllegalStateException when the default value does not decode
        public ScriptVariables.VariableInfo toInfo() {
            Object value = type.codec() == null
                ? null
                : defaultValue().map(json -> AkytheraProject.withProjectLookups(() -> type.parse(json))).orElse(null);

            return new ScriptVariables.VariableInfo(name, type, value, sync);
        }
    }

    /// A function the blueprint declares.
    ///
    /// @param returnType empty for a function that returns nothing
    /// @param isPrivate  whether the data laid over this one neither sees nor overrides the function
    public record UserFunction(
        String name, List<FunctionGraph.Parameter> parameters, Optional<ScriptType> returnType, boolean isPrivate, ScriptGraphData graph
    ) {
        public static final Codec<UserFunction> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.STRING.fieldOf("name").forGetter(UserFunction::name),
                    FunctionGraph.Parameter.CODEC.listOf().optionalFieldOf("parameters", List.of())
                        .forGetter(UserFunction::parameters),
                    AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec().optionalFieldOf("return_type")
                        .forGetter(UserFunction::returnType),
                    Codec.BOOL.optionalFieldOf("private", false).forGetter(UserFunction::isPrivate),
                    ScriptGraphData.CODEC.optionalFieldOf("graph", ScriptGraphData.EMPTY).forGetter(UserFunction::graph)
                ).apply(instance, UserFunction::new)
        );

        public UserFunction {
            parameters = List.copyOf(parameters);
        }

        public BlueprintSignature signature() {
            return new BlueprintSignature(
                name,
                BlueprintSignature.Kind.FUNCTION,
                parameters.stream()
                    .map(parameter -> new BlueprintSignature.Parameter(parameter.name(), parameter.type().type()))
                    .toList(),
                returnType.<Class<?>>map(ScriptType::type).orElse(null)
            );
        }

        public UserFunction withGraph(final ScriptGraphData graph) {
            return new UserFunction(name, parameters, returnType, isPrivate, graph);
        }
    }
}
