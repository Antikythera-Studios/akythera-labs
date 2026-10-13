package com.yesman.akytheralabs.akythera.particle;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.node.LiteralNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.module.ModuleParameter;
import com.yesman.akythera.client.api.particle.module.ParticleModuleContext;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.akythera.animation.ConditionGraphCompiler;
import com.yesman.akytheralabs.editor.ValueShape;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import com.yesman.akytheralabs.gui.widget.script.node.ParticleParameterGetterNodeData;
import net.minecraft.util.ExtraCodecs;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/// One parameter of a built-in particle module, as the editor holds it: a literal, and a node graph that answers the
/// value per particle from the particle and the system's parameters. A graph with a wired result wins over the
/// literal.
public final class ModuleParamHolder {
    private final String name;
    private final ModuleParameter declared;
    private final ScriptType type;
    private final ValueShape shape;

    /// The literal as the editor holds it, see [ValueShape]. Starts at zero, or white for a color.
    private Object literal;

    /// The canvas answering the value from the particle and the system's parameters.
    private ScriptGraphData graph = ScriptGraphData.EMPTY;

    /// What [#graph] compiled to, kept while [#compiledGraph] is still the graph so two compiles of one graph are equal.
    private @Nullable PureNode compiled;
    private @Nullable ScriptGraphData compiledGraph;

    /// @throws IllegalStateException when `declared` names a type that is no registered script type
    public ModuleParamHolder(final String name, final ModuleParameter declared) {
        ScriptType type = ScriptType.of(declared.value());

        if (type == null) throw new IllegalStateException("Module parameter " + name + " is of the unregistered type " + declared.value().getSimpleName());

        this.name = name;
        this.declared = declared;
        this.type = type;
        // One row per parameter: a record value is edited whole, by its type's own widget
        this.shape = ValueShape.of(type) instanceof ValueShape.RecordShape ? new ValueShape.Leaf(type.type(), type.codec()) : ValueShape.of(type);
        this.literal = declared.value() == RGBA.class ? new RGBA(1.0F, 1.0F, 1.0F) : Objects.requireNonNull(shape.blank());
    }

    /// The record component's name, which is also the parameter's json field in camel case.
    public String getName() {
        return name;
    }

    public String displayName() {
        return ParseUtils.camelToSpacedPascal(name);
    }

    public ModuleParameter declared() {
        return declared;
    }

    public ValueShape shape() {
        return shape;
    }

    public boolean hasGraph() {
        return !graph.isEmpty();
    }

    public ScriptGraphData getGraph() {
        return graph;
    }

    public void setGraph(final ScriptGraphData graph) {
        this.graph = graph;
    }

    public Object getLiteral() {
        return literal;
    }

    public void setLiteral(final Object literal) {
        this.literal = literal;
    }

    /// Whether the canvas has a node that reads the system's parameter `parameter`.
    public boolean readsParameter(final String parameter) {
        return graph.nodesOf(ParticleParameterGetterNodeData.class).anyMatch(node -> ((ParticleParameterGetterNodeData)node.data()).parameter().equals(parameter));
    }

    /// Takes `node` as read from a module: a literal as the literal. Anything else leaves the literal as it is, since
    /// a compiled tree does not turn back into a canvas.
    public void readFrom(final PureNode node) {
        graph = ScriptGraphData.EMPTY;

        if (node instanceof LiteralNode<?> literalNode) literal = Objects.requireNonNull(shape.toEditor(literalNode.literal()));
    }

    /// The node the module takes: the graph's result when one is wired, else the literal.
    ///
    /// @throws RuntimeException when the graph does not compile
    public PureNode compile() {
        if (compiledGraph != graph) {
            compiled = graph.isEmpty() ? null : ConditionGraphCompiler.compileOptional(graph, AkytheraParticle.class, ParticleModuleContext.PARTICLE_LOCAL);
            compiledGraph = graph;
        }

        return compiled == null ? new LiteralNode<>(type, shape.fromEditor(literal)) : compiled;
    }

    public Stored toStored() {
        return new Stored(Optional.of(shape.toJson(literal)), graph);
    }

    /// Takes `stored`. A literal that is missing or no longer decodes leaves the literal as it is.
    public void load(final Stored stored) {
        graph = stored.graph();

        try {
            stored.literal().map(shape::fromJson).ifPresent(decoded -> literal = decoded);
        } catch (RuntimeException e) {
            // The literal stays as it is
        }
    }

    /// A parameter in the editor's json: the literal in its type's own form, and the canvas.
    public record Stored(Optional<JsonElement> literal, ScriptGraphData graph) {
        public static final Codec<Stored> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    ExtraCodecs.JSON.optionalFieldOf("literal").forGetter(Stored::literal),
                    ScriptGraphData.CODEC.optionalFieldOf("graph", ScriptGraphData.EMPTY).forGetter(Stored::graph)
                ).apply(instance, Stored::new)
        );
    }
}
