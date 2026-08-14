package com.yesman.akytheralabs;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.impl.NumericComparators;
import com.yesman.akythera.api.data.modularize.node.BranchNode;
import com.yesman.akythera.api.data.modularize.node.ContextVariableAccessNode;
import com.yesman.akythera.api.data.modularize.node.FlowFunctionNode;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.data.modularize.node.InvertBooleanNode;
import com.yesman.akythera.api.data.modularize.node.LiteralNode;
import com.yesman.akythera.api.data.modularize.node.LogicalOperationNode;
import com.yesman.akythera.api.data.modularize.node.NumericOperatorNode;
import com.yesman.akythera.api.data.modularize.node.PureFunctionNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.ScriptVariableGetterNode;
import com.yesman.akythera.api.data.modularize.node.ScriptVariableSetterNode;
import com.yesman.akythera.api.data.modularize.node.SelfNode;
import com.yesman.akythera.api.data.modularize.node.SequenceNode;
import com.yesman.akythera.api.data.modularize.node.SupplierAdapterNode;
import com.yesman.akythera.api.data.modularize.node.TypeConvertNode;
import com.yesman.akythera.api.data.modularize.node.animgraph.AnimationStateMachineNode;
import com.yesman.akythera.api.data.modularize.node.animgraph.CachedPoseReferenceNode;
import com.yesman.akythera.api.animation.animator.AnimationInterfaceKey;
import com.yesman.akythera.core.animation.driver.AnimationLayerSignature;
import com.yesman.akythera.core.animation.driver.FSMAnimationState;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.akythera.animation.ScriptGraphCompiler;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateNotFoundException;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Lowers a runtime script-node tree (the same [FlowNode]/[PureNode] form the engine executes and
/// [ScriptGraphCompiler] produces) into **Java source**,
/// the codegen half of `File > Export > As Source Code` for node-graph scripts.
///
/// Shape rules:
/// - every [FlowNode] becomes one statement (terminated by `;`, or an `if` block for [BranchNode]);
/// - every [PureNode] is inlined as an expression inside its consuming statement;
/// - a [FlowFunctionNode] / [PureFunctionNode] renders through the per-function FreeMarker template
///   `source_templates/script_node/<namespace>/<path>.ftl`, addressed by the function's
///   [AkytheraRegistries.Frozen#SCRIPT_FUNCTION] id. The template receives `params` — the argument
///   expressions, index-aligned with the function's signature — and splices them into a Java
///   expression. A member function without a template falls back to `params[0].method(params[1..])`
///   (the method name recovered from the id path); a static/custom function without a template
///   renders a `/* TODO */` marker instead of failing the export.
///
/// **Local variables** (the [ContextVariableAccessNode] singularity): a context read has no
/// `flowNode(pureNode…)` shape — it must name a Java identifier that *exists* at that point. Two
/// producers create those identifiers: the flow's context parameters (`frameTime`, `animLayerMap`, …
/// — seeded per method by the caller and emitted as the generated method's parameter names), and
/// [ScriptVariableSetterNode]s whose name is **not** a member variable (the graph compiler's CSE
/// hoists, `cse_<id>_<pin>`). The first such assignment in a method declares the local
/// (`var name = <expr>;`); later assignments and every [ContextVariableAccessNode] simply reference
/// the name. Member-variable setters assign the generated `@ScriptField` field instead. Hoist
/// placement is inherited from the graph compiler (setters precede the LCA of all uses), so a
/// declared local always dominates its reads; block scoping holds because a `var` declared inside a
/// branch arm is only ever read inside that arm.
@NullMarked
public final class ScriptNodeSourceCompiler {
    private static final String INDENT = "    ";

    /// Member (script) variable names — assignments to these are field writes, not local declarations.
    private final Set<String> memberVariables;

    /// Java identifiers in scope: the method's context parameters plus every local declared so far.
    private final Set<String> scopedNames = new HashSet<>();

    /// The Java expression a [SelfNode] (an unwired owner/"Target" pin — see
    /// [ScriptGraphCompiler]) lowers to: the generated
    /// class's own handle on its script owner. An animator script's owner is `this.animator`; an
    /// entity-patch script's is `this.owner`. Threaded in so one compiler serves both script kinds.
    private final String selfExpr;

    private ScriptNodeSourceCompiler(Collection<String> memberVariables, Collection<String> contextParams, String selfExpr) {
        this.memberVariables = new HashSet<>(memberVariables);
        this.scopedNames.addAll(contextParams);
        this.selfExpr = selfExpr;
    }

    /// Compile `flow` into method-body statement lines (relative indent baked in, no base indent).
    /// `contextParams` are the generated method's parameter names — the identifiers
    /// [ContextVariableAccessNode]s may reference besides declared locals. `selfExpr` is the owner
    /// handle a [SelfNode] resolves to (e.g. `this.animator`, `this.owner`).
    public static List<String> compileFlow(FlowNode flow, Collection<String> memberVariables, Collection<String> contextParams, String selfExpr) {
        ScriptNodeSourceCompiler compiler = new ScriptNodeSourceCompiler(memberVariables, contextParams, selfExpr);
        List<String> out = new ArrayList<>();
        compiler.statements(flow, out, 0);
        return out;
    }

    /// As [#compileFlow], but the trailing [FlowFunctionNode]'s expression becomes the method's
    /// `return`; `fallbackReturn` is appended when the flow cannot end in a returnable expression.
    public static List<String> compileReturningFlow(
        FlowNode flow, Collection<String> memberVariables, Collection<String> contextParams, String selfExpr, String fallbackReturn
    ) {
        ScriptNodeSourceCompiler compiler = new ScriptNodeSourceCompiler(memberVariables, contextParams, selfExpr);
        List<String> out = new ArrayList<>();

        if (!compiler.returningStatements(flow, out, 0)) {
            out.add("return " + fallbackReturn + ";");
        }

        return out;
    }

    /// ***************************************************************
    /// Flow nodes — one statement per node
    /// ***************************************************************

    private void statements(FlowNode node, List<String> out, int indent) {
        switch (node) {
            case SequenceNode seq -> {
                for (FlowNode block : seq.executionBlocks()) statements(block, out, indent);
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
            case FlowFunctionNode fn -> {
                out.add(line(indent, functionExpr(fn.function(), argsOf(fn.params())) + ";"));
                if (fn.nextNode() != null) statements(fn.nextNode(), out, indent);
            }
            case ScriptVariableSetterNode setter -> out.add(line(indent, assignment(setter)));
            default -> out.add(line(indent, "// TODO: unsupported flow node " + node.getClass().getSimpleName()));
        }
    }

    /// Emits `node` with its last flow-function expression as `return <expr>;`. Returns whether a
    /// `return` was actually emitted (a trailing branch/setter can't be returned from).
    private boolean returningStatements(FlowNode node, List<String> out, int indent) {
        switch (node) {
            case SequenceNode seq -> {
                List<FlowNode> blocks = seq.executionBlocks();
                if (blocks.isEmpty()) return false;
                for (int i = 0; i < blocks.size() - 1; i++) statements(blocks.get(i), out, indent);
                return returningStatements(blocks.getLast(), out, indent);
            }
            case FlowFunctionNode fn -> {
                if (fn.nextNode() != null) {
                    statements(fn, out, indent);
                    return false;
                }
                out.add(line(indent, "return " + functionExpr(fn.function(), argsOf(fn.params())) + ";"));
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
    private String assignment(ScriptVariableSetterNode setter) {
        String name = setter.variableName();
        String value = expr(setter.valueProvider());

        if (memberVariables.contains(name)) return name + " = " + value + ";";
        return scopedNames.add(name) ? "var " + name + " = " + value + ";" : name + " = " + value + ";";
    }

    /// ***************************************************************
    /// Pure nodes — inline expressions
    /// ***************************************************************

    private String expr(PureNode node) {
        return switch (node) {
            case SelfNode _ -> selfExpr;
            case LiteralNode<?> literal -> literal(literal);
            case ContextVariableAccessNode access -> access.localVariableName();
            case ScriptVariableGetterNode getter -> getter.variableName();
            case PureFunctionNode fn -> functionExpr(fn.function(), argsOf(fn.params()));
            case NumericOperatorNode op -> "(" + expr(op.a()) + " " + operatorSign(op.operator()) + " " + expr(op.b()) + ")";
            case LogicalOperationNode op -> logicalExpr(op);
            case InvertBooleanNode invert -> "!(" + expr(invert.child()) + ")";
            case SupplierAdapterNode supplier -> "() -> " + expr(supplier.child());
            case TypeConvertNode convert -> "(" + primitiveName(convert.to().type()) + ") (" + expr(convert.input()) + ")";
            case CachedPoseReferenceNode pose -> "new CachedPoseReference(" + quote(pose.poseCacheName()) + ", " + selfExpr + ")";
            case AnimationStateMachineNode machine -> stateMachineExpr(machine);
            default -> "null /* TODO: unsupported pure node " + node.getClass().getSimpleName() + " */";
        };
    }

    private List<String> argsOf(List<PureNode> params) {
        List<String> args = new ArrayList<>(params.size());
        for (PureNode param : params) args.add(expr(param));
        return args;
    }

    /// ***************************************************************
    /// Function nodes — per-id templates
    /// ***************************************************************

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
    /// the template doesn't exist (the caller falls back). Other failures abort the export loudly —
    /// a broken template is an authoring error, not a graph error.
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

    /// Fallback for scanned **member** functions (no template): `receiver.method(rest…)`. The method
    /// name is recovered from the registry id — [com.yesman.akythera.core.reflection.ScriptModularizeHelper]
    /// registers instance methods as `<class_snake>_<method_snake>` with the receiver folded in as
    /// the first signature parameter, so stripping the receiver type's snake prefix leaves the
    /// method's snake name.
    private String memberCall(ScriptFunction function, Identifier id, List<String> args) {
        Class<?> receiverType = function.functionSignature().parameters().getFirst();
        String prefix = ParseUtils.camelToSnake(receiverType.getSimpleName()) + "_";
        String path = id.getPath();

        if (!path.startsWith(prefix)) {
            return "null /* TODO: missing template script_node/" + id.getNamespace() + "/" + path + ".ftl */";
        }

        String method = ParseUtils.snakeToCamel(path.substring(prefix.length()));
        return args.getFirst() + "." + method + "(" + String.join(", ", args.subList(1, args.size())) + ")";
    }

    /// ***************************************************************
    /// Anim-graph nodes
    /// ***************************************************************

    /// The state-machine builder chain, mirroring both the runtime
    /// [AnimationStateMachineNode#execute] and the handwritten `PlayerAnimatorScript#construct`:
    /// entry transitions first (the builder cursor starts on the entry state), then each state with
    /// its pose driver and outgoing transitions. States resolve through the
    /// [FSMAnimationState#ENUM_MANAGER] by identifier, so project-defined (data block) states work
    /// without a Java constant to reference.
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

    private void appendTransition(StringBuilder chain, AnimationStateMachineNode.TransitionEntry transition) {
        chain.append("\n").append(INDENT.repeat(2))
            .append(".transitTo(").append(fsmState(transition.to()))
            .append(", (_, _) -> ").append(expr(transition.condition())).append(")");
    }

    private static String fsmState(FSMAnimationState state) {
        Identifier id = FSMAnimationState.ENUM_MANAGER.derefIdentifier(state);
        return "FSMAnimationState.ENUM_MANAGER.getOrThrow(Identifier.parse(" + quote(id.toString()) + "))";
    }

    /// ***************************************************************
    /// Literals & leaves
    /// ***************************************************************

    /// A member variable's default value as a Java field-initializer literal, or null when the
    /// variable has no default (reference types) — the field is then left to Java's default.
    public static @Nullable String defaultValueLiteral(com.yesman.akythera.core.blueprint.script.ScriptVariables.VariableInfo variable) {
        return variable.defaultVal() == null ? null : valueLiteral(variable.defaultVal());
    }

    private static String literal(LiteralNode<?> node) {
        return valueLiteral(node.literal());
    }

    /// A value as a compilable Java literal — shared with [NodeTreeSourceRenderer] for
    /// [LiteralNode] literals and variable defaults.
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
            // The graph compiler's comparator constants (a comparator node's first param).
            case java.util.Comparator<?> comparator -> comparatorConstant(comparator);
            case AnimationLayerSignature signature ->
                "AnimationLayerSignature.ENUM_MANAGER.getOrThrow(Identifier.parse("
                    + quote(AnimationLayerSignature.ENUM_MANAGER.derefIdentifier(signature).toString()) + "))";
            case AnimationInterfaceKey interfaceKey ->
                "AnimationInterfaceKey.ENUM_MANAGER.getOrThrow(Identifier.parse("
                    + quote(AnimationInterfaceKey.ENUM_MANAGER.derefIdentifier(interfaceKey).toString()) + "))";
            default -> "null /* TODO: unsupported literal of " + value.getClass().getSimpleName() + " */";
        };
    }

    /// The [NumericComparators] constant expression for one of its singleton comparators.
    private static String comparatorConstant(java.util.Comparator<?> comparator) {
        if (comparator == NumericComparators.NUMBER_COMPARATOR)  return "NumericComparators.NUMBER_COMPARATOR";
        if (comparator == NumericComparators.BYTE_COMPARATOR)    return "NumericComparators.BYTE_COMPARATOR";
        if (comparator == NumericComparators.SHORT_COMPARATOR)   return "NumericComparators.SHORT_COMPARATOR";
        if (comparator == NumericComparators.INTEGER_COMPARATOR) return "NumericComparators.INTEGER_COMPARATOR";
        if (comparator == NumericComparators.LONG_COMPARATOR)    return "NumericComparators.LONG_COMPARATOR";
        if (comparator == NumericComparators.FLOAT_COMPARATOR)   return "NumericComparators.FLOAT_COMPARATOR";
        if (comparator == NumericComparators.DOUBLE_COMPARATOR)  return "NumericComparators.DOUBLE_COMPARATOR";
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

    /// The Java cast target for a [TypeConvertNode]: primitive name for primitives/wrappers
    /// (a `(float)` cast unboxes-and-narrows), the simple name otherwise.
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

    /// ***************************************************************
    /// Helpers
    /// ***************************************************************

    /// One output line at `indent` levels; multi-line expressions (the state-machine chain) have
    /// their continuation lines re-indented to stay aligned inside the statement.
    private static String line(int indent, String content) {
        String pad = INDENT.repeat(indent);
        return pad + content.replace("\n", "\n" + pad);
    }

    private static String quote(String raw) {
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
