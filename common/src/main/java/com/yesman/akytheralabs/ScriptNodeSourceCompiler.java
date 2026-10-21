package com.yesman.akytheralabs;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.client.api.particle.module.ParticleSystemParameterNode;
import com.yesman.akythera.api.animation.animator.AnimationInterfaceKey;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.impl.ScriptComparators;
import com.yesman.akythera.api.data.modularize.node.*;
import com.yesman.akythera.api.data.modularize.node.animgraph.AnimationStateMachineNode;
import com.yesman.akythera.api.data.modularize.node.animgraph.CachedPoseReferenceNode;
import com.yesman.akythera.api.data.modularize.node.animgraph.SourceDriverNode;
import com.yesman.akythera.api.world.ability.AbilitySlot;
import com.yesman.akythera.api.world.ability.AbilitySlotGroup;
import com.yesman.akythera.api.world.ability.AbilityTag;
import com.yesman.akythera.api.world.ability.AbilityTagSet;
import com.yesman.akythera.api.world.patch.item.WeaponStance;
import com.yesman.akythera.core.animation.AnimationLayerSignature;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.animation.FSMAnimationState;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.helper.ParseUtils;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateNotFoundException;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

/// Lowers a runtime [FlowNode]/[PureNode] tree into **Java source** for
/// `File > Export > As Source Code`.
///
/// Shape rules:
/// - every [FlowNode] becomes one statement (terminated by `;`, or an `if` block for [BranchNode] and
///   [CastNode], the latter binding its cast value by an `instanceof` pattern);
/// - every [PureNode] is inlined as an expression inside its consuming statement;
/// - a [FlowFunctionNode] / [PureFunctionNode] renders through the per-function FreeMarker template
///   `source_templates/script_node/<namespace>/<path>.ftl`, addressed by the function's
///   [AkytheraRegistries.Frozen#SCRIPT_FUNCTION] id and receiving `params` — the argument
///   expressions, index-aligned with the function's signature. Without a template, a member function
///   falls back to `params[0].method(params[1..])`, casting a `self` receiver narrower than the self
///   type, and anything else renders a `/* TODO */` marker;
/// - a [ContextVariableAccessNode] renders as a bare Java identifier, so it must name one in scope:
///   either a context parameter, or a local declared by the first [ScriptVariableSetterNode]
///   assigning a non-member name (`var name = <expr>;`). Member-variable setters assign the
///   generated `@ScriptField` field instead, on the member owner when the method has one;
/// - a [ParentCallNode] renders as the `super` call of the method being compiled, a [FunctionCallNode] as a
///   call of the generated method it names, and a blueprint field node as a read or write of the field.
@NullMarked
public final class ScriptNodeSourceCompiler {
    private static final String INDENT = "    ";

    /// Member (script) variable names — assignments to these are field writes, not local declarations.
    private final Set<String> memberVariables;

    /// Java identifiers in scope: the method's context parameters plus every local declared so far.
    private final Set<String> scopedNames = new HashSet<>();

    private final Map<String, Class<?>> localTypes = new HashMap<>();

    /// The Java expression a [SourceDriverNode] renders as while a transition lambda is being
    /// written; null elsewhere.
    private @Nullable String sourceDriverExpr;

    /// The Java expression a [SelfNode] lowers to: the generated class's own handle on its script
    /// owner, `this.animator` for an animator script and `this.owner` for an entity-patch one.
    private final String selfExpr;

    /// The declared type of [#selfExpr].
    private final Class<?> selfType;

    /// The `super` call a [ParentCallNode] renders as, or null in a method that overrides nothing.
    private final @Nullable String parentCall;

    /// The expression whose fields the member variables are, and which every [FunctionCallNode] passes
    /// first. Null when the member variables are fields of the generated class itself.
    private final @Nullable String memberOwner;

    private ScriptNodeSourceCompiler(
        Collection<String> memberVariables, Collection<String> contextParams, String selfExpr, Class<?> selfType,
        @Nullable String parentCall, @Nullable String memberOwner
    ) {
        this.memberVariables = new HashSet<>(memberVariables);
        this.scopedNames.addAll(contextParams);
        this.selfExpr = selfExpr;
        this.selfType = selfType;
        this.parentCall = parentCall;
        this.memberOwner = memberOwner;
    }

    /// Compiles `flow` into method-body statement lines (relative indent baked in, no base indent).
    /// `contextParams` are the generated method's parameter names, `selfExpr` the owner handle a
    /// [SelfNode] resolves to and `selfType` its declared type.
    public static List<String> compileFlow(
        FlowNode flow, Collection<String> memberVariables, Collection<String> contextParams, String selfExpr, Class<?> selfType
    ) {
        return compileMethod(flow, memberVariables, contextParams, selfExpr, selfType, null, null, null);
    }

    /// As [#compileFlow], for the body of a method that may override one and return a value.
    ///
    /// @param parentCall     the `super` call a [ParentCallNode] renders as, or null for a method that
    ///                       overrides nothing
    /// @param fallbackReturn appended as `return <fallbackReturn>;` when the body can end without
    ///                       returning, or null for a method that returns nothing
    /// @param memberOwner    the expression whose fields the member variables are, passed first to every
    ///                       declared function the body calls, or null when they are the generated class's
    public static List<String> compileMethod(
        FlowNode flow, Collection<String> memberVariables, Collection<String> contextParams, String selfExpr,
        Class<?> selfType, @Nullable String parentCall, @Nullable String fallbackReturn, @Nullable String memberOwner
    ) {
        ScriptNodeSourceCompiler compiler =
            new ScriptNodeSourceCompiler(memberVariables, contextParams, selfExpr, selfType, parentCall, memberOwner);
        List<String> out = new ArrayList<>();

        compiler.statements(flow, out, 0);

        if (fallbackReturn != null && !alwaysReturns(flow)) out.add("return " + fallbackReturn + ";");

        return out;
    }

    /// As [#compileFlow], but the trailing [FlowFunctionNode]'s expression becomes the method's
    /// `return`; `fallbackReturn` is appended when the flow cannot end in a returnable expression.
    public static List<String> compileReturningFlow(
        FlowNode flow, Collection<String> memberVariables, Collection<String> contextParams, String selfExpr, Class<?> selfType,
        String fallbackReturn
    ) {
        ScriptNodeSourceCompiler compiler =
            new ScriptNodeSourceCompiler(memberVariables, contextParams, selfExpr, selfType, null, null);
        List<String> out = new ArrayList<>();

        if (!compiler.returningStatements(flow, out, 0)) {
            out.add("return " + fallbackReturn + ";");
        }

        return out;
    }

    /// Compiles a headless `node` into a [PureNode#of] expression. The lambda reads the context local
    /// `ownerParam` into a local of `ownerType`, which also stands for `self`.
    ///
    /// ```java
    /// PureNode.of(context -> {
    ///     EntityPatch<?> owner = context.locals().getValue("owner");
    ///
    ///     return owner.isLogicalClient();
    /// })
    /// ```
    public static String compilePureLambda(PureNode node, String ownerParam, Class<?> ownerType) {
        ScriptNodeSourceCompiler compiler = new ScriptNodeSourceCompiler(List.of(), List.of(ownerParam), ownerParam, ownerType, null, null);

        return "PureNode.of(context -> {\n"
            + INDENT + typeName(ownerType) + " " + ownerParam + " = context.locals().getValue(" + quote(ownerParam) + ");\n"
            + "\n"
            + INDENT + "return " + compiler.expr(node) + ";\n"
            + "})";
    }

    // ***************************************************************
    // Flow nodes — one statement per node
    // ***************************************************************

    private void statements(FlowNode node, List<String> out, int indent) {
        switch (node) {
            case SequenceNode seq -> {
                for (FlowNode block : seq.executionBlocks()) {
                    statements(block, out, indent);

                    // Java refuses a statement no path reaches
                    if (alwaysReturns(block)) break;
                }
            }
            case BranchNode branch -> {
                out.add(line(indent, "if (" + expr(branch.boolFunction()) + ") {"));
                for (FlowNode arm : branch.whenTrue()) statements(arm, out, indent + 1);

                if (!branch.whenFalse().isEmpty()) {
                    out.add(line(indent, "} else {"));
                    for (FlowNode arm : branch.whenFalse()) statements(arm, out, indent + 1);
                }

                out.add(line(indent, "}"));
            }
            case CastNode cast -> {
                scopedNames.add(cast.castName());
                out.add(line(indent, "if (" + expr(cast.object()) + " instanceof " + typeName(cast.type().type()) + " " + cast.castName() + ") {"));
                for (FlowNode arm : cast.whenSucceeded()) statements(arm, out, indent + 1);

                if (!cast.whenFailed().isEmpty()) {
                    out.add(line(indent, "} else {"));
                    for (FlowNode arm : cast.whenFailed()) statements(arm, out, indent + 1);
                }

                out.add(line(indent, "}"));
            }
            case FlowFunctionNode fn -> {
                out.add(line(indent, functionExpr(fn.function(), argsOf(fn.function(), fn.params())) + ";"));
                if (fn.nextNode() != null) statements(fn.nextNode(), out, indent);
            }
            case ScriptVariableSetterNode setter -> out.add(line(indent, assignment(setter)));
            case ReturnNode ret -> out.add(line(indent, returnStatement(ret)));
            case ParentCallNode _ -> out.add(line(indent, parentCallExpr() + ";"));
            case FunctionCallNode call -> out.add(line(indent, functionCallExpr(call) + ";"));
            case BlueprintFieldSetterNode setter ->
                out.add(line(indent, "this." + setter.field() + " = " + expr(setter.value()) + ";"));
            default -> out.add(line(indent, "// TODO: unsupported flow node " + node.getClass().getSimpleName()));
        }
    }

    /// Whether every path through `node` ends in a [ReturnNode].
    private static boolean alwaysReturns(FlowNode node) {
        return switch (node) {
            case ReturnNode _ -> true;
            case SequenceNode seq -> seq.executionBlocks().stream().anyMatch(ScriptNodeSourceCompiler::alwaysReturns);
            case BranchNode branch -> branch.whenTrue().stream().anyMatch(ScriptNodeSourceCompiler::alwaysReturns)
                && branch.whenFalse().stream().anyMatch(ScriptNodeSourceCompiler::alwaysReturns);
            case CastNode cast -> cast.whenSucceeded().stream().anyMatch(ScriptNodeSourceCompiler::alwaysReturns)
                && cast.whenFailed().stream().anyMatch(ScriptNodeSourceCompiler::alwaysReturns);
            case FlowFunctionNode fn -> fn.nextNode() != null && alwaysReturns(fn.nextNode());
            default -> false;
        };
    }

    /// The `super` call a [ParentCallNode] renders as.
    private String parentCallExpr() {
        return parentCall != null ? parentCall : "null /* TODO: Parent call outside an overriding method */";
    }

    /// A [FunctionCallNode] as a call of the generated method it names.
    private String functionCallExpr(FunctionCallNode call) {
        List<String> args = new ArrayList<>();

        if (memberOwner != null) args.add(memberOwner);

        args.addAll(argsOf(call.arguments()));

        return "this." + call.function() + "(" + String.join(", ", args) + ")";
    }

    /// Emits `node` with its last flow-function expression as `return <expr>;`, returning whether a
    /// `return` was emitted.
    private boolean returningStatements(FlowNode node, List<String> out, int indent) {
        switch (node) {
            case SequenceNode seq -> {
                List<FlowNode> blocks = seq.executionBlocks();
                if (blocks.isEmpty()) return false;
                for (int i = 0; i < blocks.size() - 1; i++) statements(blocks.get(i), out, indent);
                return returningStatements(blocks.getLast(), out, indent);
            }
            case ReturnNode ret -> {
                out.add(line(indent, returnStatement(ret)));
                return true;
            }
            case FlowFunctionNode fn -> {
                if (fn.nextNode() != null) {
                    statements(fn, out, indent);
                    return false;
                }
                out.add(line(indent, "return " + functionExpr(fn.function(), argsOf(fn.function(), fn.params())) + ";"));
                return true;
            }
            default -> {
                statements(node, out, indent);
                return false;
            }
        }
    }

    /// A [ScriptVariableSetterNode] as one statement: member variables assign their generated field;
    /// anything else is a Java local — declared with `var` on its first assignment in this method.
    /// A [ReturnNode] as one statement. A node carrying no value returns nothing.
    private String returnStatement(ReturnNode node) {
        return node.value().map(value -> "return " + expr(value) + ";").orElse("return;");
    }

    /// A [CallbackAdapterNode] as a `Runnable` lambda holding its sub-flow.
    private String callbackExpr(CallbackAdapterNode node) {
        List<String> body = new ArrayList<>();

        statements(node.child(), body, 0);

        return body.isEmpty() ? "() -> {}" : "() -> { " + String.join(" ", body) + " }";
    }

    private String assignment(ScriptVariableSetterNode setter) {
        String name = setter.variableName();
        String value = expr(setter.valueProvider());

        if (memberVariables.contains(name)) return memberExpr(name) + " = " + value + ";";
        if (!scopedNames.add(name)) return name + " = " + value + ";";

        localTypes.put(name, staticType(setter.valueProvider()));

        return "var " + name + " = " + value + ";";
    }

    // ***************************************************************
    // Pure nodes — inline expressions
    // ***************************************************************

    private String expr(PureNode node) {
        return switch (node) {
            case SelfNode _ -> selfExpr;
            case LiteralNode<?> literal -> literal(literal);
            case RegistryEntryNode entry -> BlueprintSourceWriter.propertyLiteral(entry.type(), new JsonPrimitive(entry.id().toString()));
            case RegistryKeyNode key -> BlueprintSourceWriter.registryKey(Objects.requireNonNull(key.type().registry()), key.id().toString());
            case ContextVariableAccessNode access -> access.localVariableName();
            case ParticleSystemParameterNode parameter -> selfExpr + ".getSystem().getParameters().<"
                + (parameter.type().container() == ScriptType.Container.SINGLE
                    ? SourceNames.typeName(ParseUtils.box(parameter.type().type()))
                    : SourceNames.typeName(parameter.type()))
                + ">getValue(" + quote(parameter.parameter()) + ")";
            case ScriptVariableGetterNode getter -> memberVariables.contains(getter.variableName())
                ? memberExpr(getter.variableName())
                : getter.variableName();
            case SourceDriverNode _ -> sourceDriverExpr != null ? sourceDriverExpr
                : "null /* TODO: Source State Driver outside a transition */";
            case PureFunctionNode fn -> functionExpr(fn.function(), argsOf(fn.function(), fn.params()));
            case NumericOperatorNode op -> "(" + expr(op.a()) + " " + operatorSign(op.operator()) + " " + expr(op.b()) + ")";
            case LogicalOperationNode op -> logicalExpr(op);
            case InvertBooleanNode invert -> "!(" + expr(invert.child()) + ")";
            case SupplierAdapterNode supplier -> "() -> " + expr(supplier.child());
            case CallbackAdapterNode callback -> callbackExpr(callback);
            case TypeConvertNode convert -> "(" + primitiveName(convert.to().type()) + ") (" + expr(convert.input()) + ")";
            case CachedPoseReferenceNode pose -> "new CachedPoseReference(" + quote(pose.poseCacheName())
                + ", " + selfExpr + ", " + valueLiteral(pose.controlReferenceDriver()) + ")";
            case AnimationStateMachineNode machine -> stateMachineExpr(machine);
            case ParentCallNode _ -> parentCallExpr();
            case FunctionCallNode call -> functionCallExpr(call);
            case BlueprintFieldGetterNode getter -> "this." + getter.field();
            default -> "null /* TODO: unsupported pure node " + node.getClass().getSimpleName() + " */";
        };
    }

    /// The member variable `name` as read or written on [#memberOwner].
    private String memberExpr(String name) {
        return memberOwner == null ? name : memberOwner + "." + name;
    }

    private List<String> argsOf(List<PureNode> params) {
        List<String> args = new ArrayList<>(params.size());
        for (PureNode param : params) args.add(expr(param));
        return args;
    }

    private List<String> argsOf(ScriptFunction function, List<PureNode> params) {
        List<Class<?>> parameterTypes = function.functionSignature().parameters();
        List<String> args = new ArrayList<>(params.size());

        for (int i = 0; i < params.size(); i++) {
            PureNode param = params.get(i);
            Class<?> expected = i < parameterTypes.size() ? parameterTypes.get(i) : Object.class;
            String arg = expr(param);

            args.add(staticType(param) == Object.class && !expected.isPrimitive() && expected != Object.class
                ? "((" + typeName(expected) + ") " + arg + ")"
                : arg);
        }

        return args;
    }

    private @Nullable Class<?> staticType(PureNode node) {
        return switch (node) {
            case SelfNode _ -> selfType;
            case PureFunctionNode fn -> fn.function().functionSignature().returnType();
            case ContextVariableAccessNode access -> localTypes.get(access.localVariableName());
            case ScriptVariableGetterNode getter -> memberVariables.contains(getter.variableName()) ? null : localTypes.get(getter.variableName());
            default -> null;
        };
    }

    // ***************************************************************
    // Function nodes — per-id templates
    // ***************************************************************

    /// The Java expression invoking `function` with `args`: its `script_node/<ns>/<path>.ftl`
    /// template when one exists, else the member-call fallback, else a `/* TODO */` marker.
    private String functionExpr(ScriptFunction function, List<String> args) {
        Identifier id = AkytheraRegistries.Frozen.SCRIPT_FUNCTION.getKey(function);
        if (id == null) return "null /* TODO: unregistered script function */";

        String rendered = renderNodeTemplate(id, args);
        if (rendered != null) return rendered;

        if (function.isMember() && !args.isEmpty()) return memberCall(function, id, args);

        return "null /* TODO: missing template script_node/" + id.getNamespace() + "/" + id.getPath() + ".ftl */";
    }

    /// Renders `script_node/<ns>/<path>.ftl` with `params` = the argument expressions, or null when
    /// the template doesn't exist.
    private static @Nullable String renderNodeTemplate(Identifier id, List<String> args) {
        String templateName = "script_node/" + id.getNamespace() + "/" + id.getPath() + ".ftl";

        try (StringWriter writer = new StringWriter()) {
            Template template = SourceTemplateEngine.getTemplate(templateName);
            template.process(Map.of("params", args), writer);
            return writer.toString();
        } catch (TemplateNotFoundException e) {
            return null;
        } catch (IOException | TemplateException e) {
            throw new IllegalStateException("Failed to render script node template " + templateName, e);
        }
    }

    /// Fallback for scanned **member** functions with no template: `receiver.method(rest…)`.
    /// [ScriptModularizeHelper] registers instance methods as `<class_snake>_<method_snake>`, so
    /// stripping the receiver type's snake prefix off the id path leaves the method's snake name.
    private String memberCall(ScriptFunction function, Identifier id, List<String> args) {
        Class<?> receiverType = function.functionSignature().parameters().getFirst();
        String prefix = ParseUtils.camelToSnake(receiverType.getSimpleName()) + "_";
        String path = id.getPath();

        if (!path.startsWith(prefix)) {
            return "null /* TODO: missing template script_node/" + id.getNamespace() + "/" + path + ".ftl */";
        }

        String method = ParseUtils.snakeToCamel(path.substring(prefix.length()));
        String receiver = args.getFirst().equals(selfExpr) && !receiverType.isAssignableFrom(selfType)
            ? "((" + typeName(receiverType) + ") " + selfExpr + ")"
            : args.getFirst();

        return receiver + "." + method + "(" + String.join(", ", args.subList(1, args.size())) + ")";
    }

    // ***************************************************************
    // Anim-graph nodes
    // ***************************************************************

    /// The state-machine builder chain: entry transitions first (the builder cursor starts on the
    /// entry state), then each state with its pose driver and outgoing transitions.
    private String stateMachineExpr(AnimationStateMachineNode machine) {
        StringBuilder chain = new StringBuilder("AnimationStateMachine.stateModelBuilder()");

        for (AnimationStateMachineNode.TransitionEntry t : machine.entryTransitions()) {
            appendTransition(chain, t);
        }

        for (AnimationStateMachineNode.StateEntry s : machine.states()) {
            String state = fsmState(s.state());
            chain.append("\n").append(INDENT.repeat(2))
                .append(".addState(").append(state)
                .append(", AnimationStateMachine.stateInstanceBuilder(").append(state)
                .append(", ").append(expr(s.poseDriver())).append("))");

            for (AnimationStateMachineNode.TransitionEntry t : s.transitions()) {
                appendTransition(chain, t);
            }
        }

        return "new AnimationStateMachine<>(\n"
            + INDENT + selfExpr + ".getOwner(),\n"
            + INDENT + chain + "\n"
            + ")";
    }

    /// The transition lambda's first parameter — the state instance being left.
    private static final String TRANSITION_FROM = "from";

    private void appendTransition(StringBuilder chain, AnimationStateMachineNode.TransitionEntry transition) {
        String outer = sourceDriverExpr;
        sourceDriverExpr = "AnimationStateMachine.driverOf(" + TRANSITION_FROM + ")";

        try {
            chain.append("\n").append(INDENT.repeat(2))
                .append(".transitTo(new AnimationStateTransition<>(").append(fsmState(transition.to()))
                .append(", (").append(TRANSITION_FROM).append(", _) -> ")
                .append(expr(transition.condition()))
                .append(", null, 0, ").append(SourceTemplateEngine.floatLiteral(transition.blendTime()))
                .append(", ").append(valueLiteral(transition.transferElapsedTime()))
                .append(", ").append(valueLiteral(transition.transitionPriority()))
                .append("))");
        } finally {
            sourceDriverExpr = outer;
        }
    }

    private static String fsmState(FSMAnimationState state) {
        return dataBlockLookup("FSMAnimationState", state);
    }

    /// The expression naming a data block: the constant of the enum the project exports for its type
    /// ([SourceNames#dataBlockReference]), the engine `ENUM_MANAGER.getOrThrow(...)` lookup by id for
    /// a block the project does not own, or a `TODO` marker for a block that carries no id.
    ///
    /// `type` is the enum interface the block belongs to, e.g. `FSMAnimationState`.
    static String dataBlockLookup(String type, EnumerableDataBlock block) {
        String projectConstant = SourceNames.dataBlockReference(block);
        if (projectConstant != null) return projectConstant;

        Identifier id = block.identifier();

        return id == null
            ? "null /* TODO: unregistered " + type + " */"
            : type + ".ENUM_MANAGER.getOrThrow(Identifier.parse(" + quote(id.toString()) + "))";
    }

    // ***************************************************************
    // Literals & leaves
    // ***************************************************************

    /// A member variable's default value as a Java field-initializer literal, or null when the
    /// variable has no default. A list, set or map is built mutable, since a script may change it in place,
    /// and it and a registry entry are written from their codec's JSON, see [BlueprintSourceWriter#propertyLiteral].
    @SuppressWarnings("unchecked")
    public static @Nullable String defaultValueLiteral(ScriptVariables.VariableInfo variable) {
        Object value = variable.defaultVal();
        ScriptType type = variable.type();

        if (value == null) return null;
        if (type.container() == ScriptType.Container.SINGLE && type.registry() == null) return valueLiteral(value);

        JsonElement json = ((Codec<Object>) Objects.requireNonNull(type.codec()))
            .encodeStart(JsonOps.INSTANCE, value)
            .getOrThrow(IllegalStateException::new);
        String literal = BlueprintSourceWriter.propertyLiteral(type, json);

        return switch (type.container()) {
            case SINGLE -> literal;
            case LIST -> "new ArrayList<>(" + literal + ")";
            case SET -> "new LinkedHashSet<>(" + literal + ")";
            case MAP -> "new LinkedHashMap<>(" + literal + ")";
        };
    }

    private static String literal(LiteralNode<?> node) {
        return valueLiteral(node.literal());
    }

    /// A value as a compilable Java literal.
    public static String valueLiteral(@Nullable Object value) {
        return switch (value) {
            case null -> "null";
            case String s -> quote(s);
            case Boolean b -> b.toString();
            case Float f -> SourceTemplateEngine.floatLiteral(f);
            case Double d -> d + "D";
            case Long l -> l + "L";
            case Integer _, Short _, Byte _ -> value.toString();
            case Character c -> "'" + (c == '\'' ? "\\'" : c == '\\' ? "\\\\" : c) + "'";
            case Vec3 vec3 -> "new Vec3(" + vec3.x() + ", " + vec3.y() + ", " + vec3.z() + ")";
            case RGBA color -> "new RGBA(" + SourceTemplateEngine.floatLiteral(color.r()) + ", " + SourceTemplateEngine.floatLiteral(color.g()) + ", "
                + SourceTemplateEngine.floatLiteral(color.b()) + ", " + SourceTemplateEngine.floatLiteral(color.a()) + ")";
            // A comparator node's first param.
            case java.util.Comparator<?> comparator -> comparatorConstant(comparator);
            case AnimationLayerSignature signature -> dataBlockLookup("AnimationLayerSignature", signature);
            case AnimationInterfaceKey interfaceKey -> dataBlockLookup("AnimationInterfaceKey", interfaceKey);
            case WeaponStance stance -> dataBlockLookup("WeaponStance", stance);
            case AbilityTag tag -> dataBlockLookup("AbilityTag", tag);
            case AbilityTagSet set -> "AbilityTagSet.of(List.of("
                + set.tags().stream().map(tag -> dataBlockLookup("AbilityTag", tag)).collect(Collectors.joining(", ")) + "))";
            case AbilitySlot slot -> dataBlockLookup("AbilitySlot", slot);
            case AbilitySlotGroup group -> dataBlockLookup("AbilitySlotGroup", group);
            case Enum<?> constant -> SourceNames.typeName(constant.getDeclaringClass()) + "." + constant.name();
            default -> "null /* TODO: unsupported literal of " + value.getClass().getSimpleName() + " */";
        };
    }

    /// The [ScriptComparators] constant expression for one of its singleton comparators.
    private static String comparatorConstant(java.util.Comparator<?> comparator) {
        if (comparator == ScriptComparators.EQUALITY_COMPARATOR) return "ScriptComparators.EQUALITY_COMPARATOR";
        if (comparator == ScriptComparators.NUMBER_COMPARATOR)  return "ScriptComparators.NUMBER_COMPARATOR";
        if (comparator == ScriptComparators.BYTE_COMPARATOR)    return "ScriptComparators.BYTE_COMPARATOR";
        if (comparator == ScriptComparators.SHORT_COMPARATOR)   return "ScriptComparators.SHORT_COMPARATOR";
        if (comparator == ScriptComparators.INTEGER_COMPARATOR) return "ScriptComparators.INTEGER_COMPARATOR";
        if (comparator == ScriptComparators.LONG_COMPARATOR)    return "ScriptComparators.LONG_COMPARATOR";
        if (comparator == ScriptComparators.FLOAT_COMPARATOR)   return "ScriptComparators.FLOAT_COMPARATOR";
        if (comparator == ScriptComparators.DOUBLE_COMPARATOR)  return "ScriptComparators.DOUBLE_COMPARATOR";
        return "null /* TODO: unsupported comparator literal */";
    }

    private static String operatorSign(NumericOperatorNode.Operator operator) {
        return switch (operator) {
            case ADD -> "+";
            case SUBTRACT -> "-";
            case MULTIPLY -> "*";
            case DIVIDE -> "/";
        };
    }

    private String logicalExpr(LogicalOperationNode op) {
        String a = expr(op.bool1());
        String b = expr(op.bool2());

        return switch (op.operator()) {
            case AND -> "(" + a + " && " + b + ")";
            case OR -> "(" + a + " || " + b + ")";
            case NAND -> "!(" + a + " && " + b + ")";
            case NOR -> "!(" + a + " || " + b + ")";
            case XOR -> "(" + a + " ^ " + b + ")";
        };
    }

    /// The Java cast target for a [TypeConvertNode]: the primitive name for primitives and
    /// wrappers, the simple name otherwise.
    private static String primitiveName(Class<?> type) {
        if (type.isPrimitive()) return type.getName();
        if (type == Boolean.class) return "boolean";
        if (type == Byte.class) return "byte";
        if (type == Short.class) return "short";
        if (type == Character.class) return "char";
        if (type == Integer.class) return "int";
        if (type == Long.class) return "long";
        if (type == Float.class) return "float";
        if (type == Double.class) return "double";
        return type.getSimpleName();
    }

    // ***************************************************************
    // Helpers
    // ***************************************************************

    /// One output line at `indent` levels, with the continuation lines of a multi-line expression
    /// re-indented to match.
    private static String line(int indent, String content) {
        String pad = INDENT.repeat(indent);
        return pad + content.replace("\n", "\n" + pad);
    }

    private static String quote(String raw) {
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /// The simple name of `type`, with a `?` for each of its type parameters.
    private static String typeName(Class<?> type) {
        int arity = type.getTypeParameters().length;

        return arity == 0 ? type.getSimpleName() : type.getSimpleName() + "<" + String.join(", ", Collections.nCopies(arity, "?")) + ">";
    }
}
