package com.yesman.akytheralabs;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.node.BlueprintFieldGetterNode;
import com.yesman.akythera.api.data.modularize.node.BlueprintFieldSetterNode;
import com.yesman.akythera.api.data.modularize.node.BranchNode;
import com.yesman.akythera.api.data.modularize.node.CastNode;
import com.yesman.akythera.api.data.modularize.node.ContextVariableAccessNode;
import com.yesman.akythera.api.data.modularize.node.FlowFunctionNode;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.data.modularize.node.FunctionCallNode;
import com.yesman.akythera.api.data.modularize.node.InvertBooleanNode;
import com.yesman.akythera.api.data.modularize.node.LiteralNode;
import com.yesman.akythera.api.data.modularize.node.LogicalOperationNode;
import com.yesman.akythera.api.data.modularize.node.MeshAccessorNode;
import com.yesman.akythera.api.data.modularize.node.NumericOperatorNode;
import com.yesman.akythera.api.data.modularize.node.ParentCallNode;
import com.yesman.akythera.api.data.modularize.node.PureFunctionNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.RegistryEntryNode;
import com.yesman.akythera.api.data.modularize.node.RegistryKeyNode;
import com.yesman.akythera.api.data.modularize.node.ReturnNode;
import com.yesman.akythera.api.data.modularize.node.ScriptVariableGetterNode;
import com.yesman.akythera.api.data.modularize.node.ScriptVariableSetterNode;
import com.yesman.akythera.api.data.modularize.node.SelfNode;
import com.yesman.akythera.api.data.modularize.node.SequenceNode;
import com.yesman.akythera.api.data.modularize.node.SupplierAdapterNode;
import com.yesman.akythera.api.data.modularize.node.TypeConvertNode;
import com.yesman.akythera.api.data.modularize.node.animgraph.AnimationStateMachineNode;
import com.yesman.akythera.api.data.modularize.node.animgraph.CachedPoseReferenceNode;
import com.yesman.akythera.client.api.mesh.MeshManager;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.core.animation.FSMAnimationState;
import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import com.yesman.akythera.client.api.particle.module.ParticleSystemParameterNode;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/// Renders a runtime script-node tree as **Java constructor expressions** — the data-literal counterpart of
/// [ScriptNodeSourceCompiler], which lowers the same tree into executable statements. The blueprint provider
/// export (`entity_patch_blueprint_template.ftl`) uses this to reproduce an [EntityPatchBlueprint]'s flows
/// and variables in generated datagen source. Covers the full editor-producible node vocabulary — animgraph
/// nodes included — so it renders animator-script trees too, not only entity patch flows.
///
/// Leaves that name engine objects lower to the same registry-lookup idiom the component fragments
/// generate: [AkytheraRegistries.Frozen#SCRIPT_FUNCTION]/[AkytheraRegistries.Frozen#SCRIPT_TYPE]
/// `getValue(Identifier.parse(...))`. An unregistered (dangling) function/type emits a `/* TODO */`
/// marker instead of failing the export.
///
/// Layout contract (shared with the templates' splice points): the returned expression's **first
/// line carries no indent** — the template's own padding places it — while continuation lines bake
/// absolute 4-space indents from `indent + 1` down.
@NullMarked
public final class NodeTreeSourceRenderer {
    private static final String INDENT = "    ";

    private NodeTreeSourceRenderer() {}

    // ***************************************************************
    // Flow nodes
    // ***************************************************************

    /// `node` as a `FlowNode` constructor expression; `null` (an absent
    /// [FlowFunctionNode#nextNode]) renders as the `null` literal.
    public static String flow(@Nullable FlowNode node, int indent) {
        return switch (node) {
            case null -> "null";
            case SequenceNode seq -> ctor("SequenceNode", indent,
                flowList(seq.executionBlocks(), indent + 1));
            case BranchNode branch -> ctor("BranchNode", indent,
                pure(branch.boolFunction(), indent + 1),
                flowList(branch.whenTrue(), indent + 1),
                flowList(branch.whenFalse(), indent + 1));
            case CastNode cast -> ctor("CastNode", indent,
                scriptType(cast.type()),
                pure(cast.object(), indent + 1),
                quote(cast.castName()),
                flowList(cast.whenSucceeded(), indent + 1),
                flowList(cast.whenFailed(), indent + 1));
            case FlowFunctionNode fn -> ctor("FlowFunctionNode", indent,
                scriptFunction(fn.function()),
                pureList(fn.params(), indent + 1),
                flow(fn.nextNode(), indent + 1));
            case ScriptVariableSetterNode setter -> ctor("ScriptVariableSetterNode", indent,
                quote(setter.variableName()),
                pure(setter.valueProvider(), indent + 1));
            case ReturnNode ret -> "new ReturnNode(" + ret.value()
                .map(value -> "Optional.of(" + pure(value, indent) + ")")
                .orElse("Optional.empty()") + ")";
            case ParentCallNode _ -> "new ParentCallNode(ScriptNode.NO_NODE_ID)";
            case FunctionCallNode call -> functionCall(call, indent);
            case BlueprintFieldSetterNode setter -> ctor("BlueprintFieldSetterNode", indent,
                quote(setter.field()),
                pure(setter.value(), indent + 1),
                "ScriptNode.NO_NODE_ID");
            default -> "null /* TODO: unsupported flow node " + node.getClass().getSimpleName() + " */";
        };
    }

    // ***************************************************************
    // Pure nodes
    // ***************************************************************

    /// `node` as a `PureNode` constructor expression. The `default` marker only fires for node
    /// types added to the engine after this renderer.
    public static String pure(PureNode node, int indent) {
        return switch (node) {
            case SelfNode _ -> "SelfNode.INSTANCE";
            case LiteralNode<?> literal -> "new LiteralNode<>(" + scriptType(literal.type())
                + ", " + ScriptNodeSourceCompiler.valueLiteral(literal.literal()) + ")";
            case ScriptVariableGetterNode getter ->
                "new ScriptVariableGetterNode(" + quote(getter.variableName()) + ")";
            case ContextVariableAccessNode access ->
                "new ContextVariableAccessNode(" + quote(access.localVariableName()) + ")";
            case PureFunctionNode fn -> ctor("PureFunctionNode", indent,
                scriptFunction(fn.function()),
                pureList(fn.params(), indent + 1));
            case NumericOperatorNode op -> ctor("NumericOperatorNode", indent,
                "NumericOperatorNode.Operator." + op.operator().name(),
                pure(op.a(), indent + 1),
                pure(op.b(), indent + 1));
            case LogicalOperationNode op -> ctor("LogicalOperationNode", indent,
                pure(op.bool1(), indent + 1),
                pure(op.bool2(), indent + 1),
                "LogicalOperationNode.Operator." + op.operator().name());
            case InvertBooleanNode invert -> "new InvertBooleanNode(" + pure(invert.child(), indent) + ")";
            case SupplierAdapterNode supplier -> "new SupplierAdapterNode(" + pure(supplier.child(), indent) + ")";
            case TypeConvertNode convert -> ctor("TypeConvertNode", indent,
                scriptType(convert.from()),
                scriptType(convert.to()),
                pure(convert.input(), indent + 1));
            case MeshAccessorNode mesh -> "new MeshAccessorNode(" + meshAccessor(mesh.mesh()) + ")";
            case RegistryEntryNode entry -> "new RegistryEntryNode(" + scriptType(entry.type())
                + ", Identifier.parse(" + quote(entry.id().toString()) + "))";
            case RegistryKeyNode key -> "new RegistryKeyNode(" + scriptType(key.type())
                + ", Identifier.parse(" + quote(key.id().toString()) + "))";
            case CachedPoseReferenceNode pose ->
                "new CachedPoseReferenceNode(" + quote(pose.poseCacheName())
                    + ", " + ScriptNodeSourceCompiler.valueLiteral(pose.controlReferenceDriver()) + ")";
            case AnimationStateMachineNode machine -> ctor("AnimationStateMachineNode", indent,
                transitionList(machine.entryTransitions(), indent + 1),
                stateList(machine.states(), indent + 1));
            case ParentCallNode _ -> "new ParentCallNode(ScriptNode.NO_NODE_ID)";
            case FunctionCallNode call -> functionCall(call, indent);
            case BlueprintFieldGetterNode getter ->
                "new BlueprintFieldGetterNode(" + quote(getter.field()) + ", ScriptNode.NO_NODE_ID)";
            case ParticleSystemParameterNode parameter ->
                "new ParticleSystemParameterNode(" + quote(parameter.parameter()) + ", " + scriptType(parameter.type()) + ")";
            default -> "null /* TODO: unsupported pure node " + node.getClass().getSimpleName() + " */";
        };
    }

    /// A [FunctionCallNode], which is a statement and an expression alike.
    private static String functionCall(FunctionCallNode call, int indent) {
        return ctor("FunctionCallNode", indent,
            quote(call.function()),
            pureList(call.arguments(), indent + 1),
            "ScriptNode.NO_NODE_ID");
    }

    // ***************************************************************
    // Blueprint body
    // ***************************************************************

    /// `body` as its `BlueprintBody` constructor: the method graphs, the declared functions, the property
    /// values re-read from their JSON text, and the variables.
    public static String body(BlueprintBody body, int indent) {
        return ctor("BlueprintBody", indent,
            mapOf(body.methods().entrySet().stream()
                .map(method -> entry(method.getKey(), flow(method.getValue(), indent + 3)))
                .toList(), indent + 1),
            mapOf(body.functions().entrySet().stream()
                .map(function -> entry(function.getKey(), ctor("FunctionGraph", indent + 3,
                    listOf(function.getValue().parameters().stream()
                        .map(parameter -> "new FunctionGraph.Parameter(" + quote(parameter.name()) + ", "
                            + scriptType(parameter.type()) + ")")
                        .toList(), indent + 4),
                    function.getValue().returnType()
                        .map(type -> "Optional.of(" + scriptType(type) + ")")
                        .orElse("Optional.empty()"),
                    flow(function.getValue().body(), indent + 4))))
                .toList(), indent + 1),
            mapOf(body.properties().entrySet().stream()
                .map(property -> entry(
                    property.getKey(), "JsonParser.parseString(" + quote(property.getValue().toString()) + ")"))
                .toList(), indent + 1),
            listOf(body.variables().stream().map(variable -> variableInfo(variable, indent + 2)).toList(), indent + 1));
    }

    /// One `Map.entry(key, value)` of a map rendered by [#mapOf].
    private static String entry(String key, String value) {
        return "Map.entry(" + quote(key) + ", " + value + ")";
    }

    /// `Map.ofEntries(...)` with one entry per line; an empty map stays inline.
    private static String mapOf(List<String> entries, int indent) {
        if (entries.isEmpty()) return "Map.of()";

        String pad = INDENT.repeat(indent + 1);
        return "Map.ofEntries(\n" + pad + String.join(",\n" + pad, entries)
            + "\n" + INDENT.repeat(indent) + ")";
    }

    // ***************************************************************
    // Variables
    // ***************************************************************

    /// A member variable row as its `ScriptVariables.VariableInfo` constructor; the default value
    /// renders through [ScriptNodeSourceCompiler#defaultValueLiteral] (`null` when the type carries none).
    public static String variableInfo(ScriptVariables.VariableInfo variable, int indent) {
        return ctor("ScriptVariables.VariableInfo", indent,
            quote(variable.name()),
            scriptType(variable.type()),
            ParseUtils.nvl(ScriptNodeSourceCompiler.defaultValueLiteral(variable), "null"));
    }

    // ***************************************************************
    // Anim-graph nodes (animator-script trees; never in blueprint flows)
    // ***************************************************************

    private static String transitionEntry(AnimationStateMachineNode.TransitionEntry transition, int indent) {
        return ctor("AnimationStateMachineNode.TransitionEntry", indent,
            fsmState(transition.to()),
            pure(transition.condition(), indent + 1),
            SourceTemplateEngine.floatLiteral(transition.blendTime()),
            ScriptNodeSourceCompiler.valueLiteral(transition.transferElapsedTime()),
            ScriptNodeSourceCompiler.valueLiteral(transition.transitionPriority()));
    }

    private static String stateEntry(AnimationStateMachineNode.StateEntry state, int indent) {
        return ctor("AnimationStateMachineNode.StateEntry", indent,
            fsmState(state.state()),
            pure(state.poseDriver(), indent + 1),
            transitionList(state.transitions(), indent + 1));
    }

    private static String transitionList(List<AnimationStateMachineNode.TransitionEntry> transitions, int indent) {
        return listOf(transitions.stream().map(t -> transitionEntry(t, indent + 1)).toList(), indent);
    }

    private static String stateList(List<AnimationStateMachineNode.StateEntry> states, int indent) {
        return listOf(states.stream().map(s -> stateEntry(s, indent + 1)).toList(), indent);
    }

    private static String fsmState(FSMAnimationState state) {
        return ScriptNodeSourceCompiler.dataBlockLookup("FSMAnimationState", state);
    }

    // ***************************************************************
    // Leaves & helpers
    // ***************************************************************

    /// A mesh accessor as the [MeshManager] call that reissues it, from the asset's trimmed id.
    public static String meshAccessor(AssetAccessor<? extends Mesh<?>> mesh) {
        Identifier id = mesh.identifier();
        Identifier trimmed = Identifier.fromNamespaceAndPath(
            id.getNamespace(), id.getPath().replace(MeshManager.DIRECTORY, ""));

        return mesh.checkType(AssetAccessor.Type.STATIC_MESH)
            ? "MeshManager.staticMesh(" + quote(trimmed.getNamespace()) + ", " + quote(trimmed.getPath())
                + ", StaticMesh::new)"
            : "MeshManager.skeletalMesh(" + quote(trimmed.getNamespace()) + ", " + quote(trimmed.getPath())
                + ", SkeletalMesh::new)";
    }

    /// A [ScriptFunction] as its frozen-registry lookup — the form a blueprint field holding one
    /// (an entity patch's parent constructor) takes in exported source.
    public static String scriptFunction(ScriptFunction function) {
        Identifier id = AkytheraRegistries.Frozen.SCRIPT_FUNCTION.getKey(function);
        return id == null
            ? "null /* TODO: unregistered script function */"
            : "AkytheraRegistries.Frozen.SCRIPT_FUNCTION.getValue(Identifier.parse(" + quote(id.toString()) + "))";
    }

    /// A [ScriptType] as its frozen-registry lookup.
    public static String scriptType(ScriptType type) {
        return switch (type.container()) {
            case LIST -> "ScriptType.listOf(" + scriptType(type.base()) + ")";
            case SET -> "ScriptType.setOf(" + scriptType(type.base()) + ")";
            case MAP -> "ScriptType.mapOf(" + scriptType(type.base()) + ", " + scriptType(Objects.requireNonNull(type.element())) + ")";
            case SINGLE -> {
                Identifier id = AkytheraRegistries.Frozen.SCRIPT_TYPE.getKey(type);

                yield id == null
                    ? "null /* TODO: unregistered script type */"
                    : "AkytheraRegistries.Frozen.SCRIPT_TYPE.getValue(Identifier.parse(" + quote(id.toString()) + "))";
            }
        };
    }

    private static String flowList(List<FlowNode> nodes, int indent) {
        return listOf(nodes.stream().map(n -> flow(n, indent + 1)).toList(), indent);
    }

    private static String pureList(List<PureNode> nodes, int indent) {
        return listOf(nodes.stream().map(n -> pure(n, indent + 1)).toList(), indent);
    }

    /// `List.of(...)` with one element per line; empty lists stay inline.
    private static String listOf(List<String> elements, int indent) {
        if (elements.isEmpty()) return "List.of()";

        String pad = INDENT.repeat(indent + 1);
        return "List.of(\n" + pad + String.join(",\n" + pad, elements)
            + "\n" + INDENT.repeat(indent) + ")";
    }

    /// `new <className>(...)` with one argument per line at `indent + 1`, closing paren at `indent`.
    private static String ctor(String className, int indent, String... args) {
        String pad = INDENT.repeat(indent + 1);
        return "new " + className + "(\n" + pad + String.join(",\n" + pad, args)
            + "\n" + INDENT.repeat(indent) + ")";
    }

    private static String quote(String raw) {
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
