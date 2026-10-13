package com.yesman.akytheralabs.akythera.blueprint;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.data.modularize.node.SequenceNode;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.blueprint.script.FunctionGraph;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.reflection.BlueprintLayout;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.animation.ScriptGraphCompiler;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import com.yesman.akytheralabs.gui.widget.script.node.GraphNode;
import com.yesman.akytheralabs.gui.widget.script.node.MethodEntryNodeData;
import net.minecraft.util.ExtraCodecs;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/// The editor form of a [BlueprintBody]: one Event Graph holding an entry node per event, one canvas per
/// overridden function and per declared function, the property values and the member variables. [#compile]
/// lowers it for a parent's [BlueprintLayout].
public final class BlueprintScriptData {
    /// Writes the five fields beside the fields of the holder that holds the data.
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
                    .forGetter(BlueprintScriptData::getProperties),
                Variable.CODEC.listOf().optionalFieldOf("variables", List.of())
                    .forGetter(BlueprintScriptData::getVariables)
            ).apply(instance, BlueprintScriptData::new)
    );

    private ScriptGraphData eventGraph;

    /// The canvas of each overridden function, by method name.
    private final Map<String, ScriptGraphData> overrides;

    private final List<UserFunction> functions;

    /// The property values in their type's codec form, by [BlueprintLayout.Property#key].
    private final Map<String, JsonElement> properties;

    private final List<Variable> variables;

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
        this.properties = new LinkedHashMap<>(properties);
        this.variables = new ArrayList<>(variables);
    }

    /// Matches the entry node of the method or function `name`.
    public static Predicate<GraphNode> entryOf(final String name) {
        return row -> row.data() instanceof MethodEntryNodeData entry && entry.method().equals(name);
    }

    /// What the canvases of this data know of a parent laid out by `layout`.
    public BlueprintMembers members(final BlueprintLayout layout) {
        return BlueprintMembers.of(layout, name -> {
            for (UserFunction function : functions) {
                if (function.name().equals(name)) return function.signature();
            }

            return null;
        });
    }

    /// Lowers this data for a parent laid out by `layout`, whose script owner is `ownerType`. An event whose
    /// entry node leads nowhere is left out, and so falls back to the parent.
    ///
    /// @throws IllegalStateException when two declared functions share a name, a canvas carries an unresolved
    ///                               node, or a variable's default value does not decode
    public BlueprintBody compile(final BlueprintLayout layout, final Class<?> ownerType) {
        BlueprintMembers members = members(layout);
        Map<String, FlowNode> methods = new LinkedHashMap<>();

        for (BlueprintLayout.Signature event : layout.events()) {
            FlowNode flow = ScriptGraphCompiler.compile(eventGraph, ownerType, entryOf(event.name()), members, null);

            if (!(flow instanceof SequenceNode sequence && sequence.executionBlocks().isEmpty())) {
                methods.put(event.key(), flow);
            }
        }

        for (BlueprintLayout.Signature function : layout.functions()) {
            ScriptGraphData graph = overrides.get(function.name());

            if (graph != null) {
                methods.put(function.key(), ScriptGraphCompiler.compile(
                    graph, ownerType, entryOf(function.name()), members, function.name()));
            }
        }

        Map<String, FunctionGraph> declared = new LinkedHashMap<>();

        for (UserFunction function : functions) {
            if (declared.containsKey(function.name())) {
                throw new IllegalStateException("More than one function is named " + function.name());
            }

            declared.put(function.name(), new FunctionGraph(
                function.parameters(),
                function.returnType(),
                ScriptGraphCompiler.compile(function.graph(), ownerType, entryOf(function.name()), members, function.name())
            ));
        }

        return new BlueprintBody(methods, declared, properties, variables.stream().map(Variable::toInfo).toList());
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

    /// The shared rename-tracking instances every canvas binds.
    public Stream<EditorReference> referencedReferences() {
        return canvases().flatMap(ScriptGraphData::referencedReferences);
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
    public Map<String, JsonElement> getProperties() {
        return properties;
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
    /// @param sync forced to [ScriptVariables.SyncDirection#NONE] when the type carries no stream codec
    public record Variable(
        String name, ScriptType type, Optional<JsonElement> defaultValue, ScriptVariables.SyncDirection sync
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
                : defaultValue.map(json -> AkytheraProject.withProjectLookups(() -> type.parse(json))).orElse(null);

            return new ScriptVariables.VariableInfo(name, type, value, sync);
        }
    }

    /// A function the blueprint declares.
    ///
    /// @param returnType empty for a function that returns nothing
    public record UserFunction(
        String name, List<FunctionGraph.Parameter> parameters, Optional<ScriptType> returnType, ScriptGraphData graph
    ) {
        public static final Codec<UserFunction> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.STRING.fieldOf("name").forGetter(UserFunction::name),
                    FunctionGraph.Parameter.CODEC.listOf().optionalFieldOf("parameters", List.of())
                        .forGetter(UserFunction::parameters),
                    AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec().optionalFieldOf("return_type")
                        .forGetter(UserFunction::returnType),
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
            return new UserFunction(name, parameters, returnType, graph);
        }
    }
}
