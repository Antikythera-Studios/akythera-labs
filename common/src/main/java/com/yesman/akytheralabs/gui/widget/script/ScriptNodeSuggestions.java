package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.script.VariableDraft;
import com.yesman.akytheralabs.gui.widget.NodeSuggestModal;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/// The node palette of a script canvas: flow nodes, value providers, pure functions and type
/// conversions for one script owner and its member variables. A host screen adds the nodes only its
/// own canvases have to what [#build] returns.
public final class ScriptNodeSuggestions {
    /// The literal types offered when a value drag carries no concrete expected type.
    private static final List<Class<?>> DEFAULT_LITERAL_TYPES =
        List.of(boolean.class, int.class, float.class, double.class, String.class);

    /// The category folder member-variable getters are filed under.
    private static final String MEMBER_VARIABLES_CATEGORY = "Member Variables";

    /// The category folder the "Cast To" nodes are filed under.
    private static final String CASTING_CATEGORY = "Casting";

    private final Supplier<Class<?>> ownerType;

    /// The member variables the canvases can read and write. Empty for a canvas whose graph runs
    /// without them.
    private final List<VariableDraft> variables;

    public ScriptNodeSuggestions(final Supplier<Class<?>> ownerType, final List<VariableDraft> variables) {
        this.ownerType = ownerType;
        this.variables = variables;
    }

    /// Routes a gesture to the suggestion sets its [NodeSuggestContext] asks for.
    public List<NodeSuggestModal.Suggestion<NodeWidgetFactory>> build(ScriptNodeGraphWidget graph, NodeSuggestContext context) {
        List<NodeSuggestModal.Suggestion<NodeWidgetFactory>> suggestions = new ArrayList<>();
        boolean scriptCanvas = context.usage().contains(ScriptFunction.Usage.SCRIPT);

        if (context.flow() && !context.dataFlowOnly()) {
            addFlowSuggestions(suggestions, context.providedType(), context.functionFilter(), context.usage());
        }

        if (context.valueProviders()) addValueProviderSuggestions(suggestions, context.expectedType(), scriptCanvas);

        if (context.pureFunctions() && scriptCanvas) {
            addPureFunctionSuggestions(suggestions, context.expectedType(), context.providedType(), context.functionFilter(), context.usage());
        }

        // The empty-canvas "All Actions" menu also offers the member getters
        if (scriptCanvas && context.flow() && context.pureFunctions() && context.providedType() == null) {
            addMemberVariableGetters(suggestions, null);
        }

        addTypeConvertSuggestions(suggestions, context);
        return suggestions;
    }

    /// Flow nodes: Sequence / Branch, a "Cast To" node per script type, a "Set" node per variable, and a
    /// flow-function node per **non-pure** listed [ScriptFunction] passing `filter`.
    private void addFlowSuggestions(
        List<NodeSuggestModal.Suggestion<NodeWidgetFactory>> suggestions,
        @Nullable Class<?> providedType,
        Predicate<ScriptFunction> filter,
        Set<ScriptFunction.Usage> usage
    ) {
        if (providedType == null) {
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SEQUENCE),
                (font, x, y) -> new SequenceNodeWidget(
                    font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SEQUENCE), x, y)));
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_BRANCH),
                (font, x, y) -> new BranchingNodeWidget(
                    font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_BRANCH), x, y)));
        }

        // A dragged value is offered the types narrower than its own
        for (ScriptType type : AkytheraRegistries.Frozen.SCRIPT_TYPE) {
            if (providedType != null && (providedType == type.type() || !providedType.isAssignableFrom(type.type()))) continue;
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_CAST, CastNodeWidget.typeName(type)),
                (font, x, y) -> new CastNodeWidget(font, type, x, y),
                CASTING_CATEGORY));
        }

        for (VariableDraft draft : variables) {
            String name = draft.getName();
            ScriptType type = draft.getType();
            if (!AbstractNodeWidget.valueAssignable(providedType, type.type())) continue;
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SET_VARIABLE, name),
                (font, x, y) -> new ScriptVariableSetterNodeWidget(
                    font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SET), name, type, x, y),
                MEMBER_VARIABLES_CATEGORY));
        }

        for (ScriptFunction function : AkytheraRegistries.Frozen.SCRIPT_FUNCTION) {
            if (function.isPure()) continue;
            if (!isListed(function, usage)) continue;
            if (!receiverAccepts(function, providedType)) continue;
            if (!filter.test(function)) continue;
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.literal(function.metadata().label()),
                (font, x, y) -> new FlowFunctionNodeWidget(font, function, x, y),
                function.metadata().category()));
        }
    }

    /// Value providers filling a **data input**: the Self node, an inline Literal, and on a SCRIPT
    /// canvas a getter per variable whose type can satisfy `expectedType`.
    private void addValueProviderSuggestions(
        List<NodeSuggestModal.Suggestion<NodeWidgetFactory>> suggestions,
        @Nullable Class<?> expectedType,
        boolean scriptCanvas
    ) {
        if (expectedType == null || expectedType.isAssignableFrom(ownerType.get())) {
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SELF),
                (font, x, y) -> new SelfNodeWidget(
                    font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SELF), x, y)));
        }

        if (LiteralValueSupport.isSupported(expectedType)) {
            suggestions.add(literalSuggestion(expectedType));
        } else if (expectedType == null) {
            for (Class<?> type : DEFAULT_LITERAL_TYPES) suggestions.add(literalSuggestion(type));
        }

        if (scriptCanvas) addMemberVariableGetters(suggestions, expectedType);
    }

    /// A getter node per variable whose type can satisfy `expectedType` (all when null).
    private void addMemberVariableGetters(
        List<NodeSuggestModal.Suggestion<NodeWidgetFactory>> suggestions,
        @Nullable Class<?> expectedType
    ) {
        for (VariableDraft draft : variables) {
            String name = draft.getName();
            ScriptType type = draft.getType();
            if (!AbstractNodeWidget.valueAssignable(type.type(), expectedType)) continue;
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_GET_VARIABLE, name),
                (font, x, y) -> new ScriptVariableGetterNodeWidget(
                    font, Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_GET_VARIABLE, name), name, type, x, y),
                MEMBER_VARIABLES_CATEGORY));
        }
    }

    /// A node per **pure** value-returning listed [ScriptFunction] passing `filter`, plus the Math /
    /// Logic nodes (comparators, arithmetic operators, NOT, binary logic).
    private void addPureFunctionSuggestions(
        List<NodeSuggestModal.Suggestion<NodeWidgetFactory>> suggestions,
        @Nullable Class<?> expectedType,
        @Nullable Class<?> providedType,
        Predicate<ScriptFunction> filter,
        Set<ScriptFunction.Usage> usage
    ) {
        for (ScriptFunction function : AkytheraRegistries.Frozen.SCRIPT_FUNCTION) {
            if (!function.isPure()) continue;
            if (!isListed(function, usage)) continue;
            // Comparison functions are offered as the comparator nodes below.
            if (function.functionSignature().parameters().stream().anyMatch(Comparator.class::isAssignableFrom)) continue;
            if (!receiverAccepts(function, providedType)) continue;
            if (!filter.test(function)) continue;
            Class<?> returnType = function.functionSignature().returnType();
            if (returnType == void.class || returnType == Void.class) continue;
            if (!AbstractNodeWidget.valueAssignable(returnType, expectedType)) continue;
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.literal(function.metadata().label()),
                (font, x, y) -> ScriptNodeGraphWidget.isSelect(function)
                    ? new SelectNodeWidget(font, function, x, y)
                    : new PureFunctionNodeWidget(font, function, x, y),
                function.metadata().category()));
        }

        if (providedType == null && AbstractNodeWidget.valueAssignable(AssetAccessor.class, expectedType)) {
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_NODE_MESH),
                (font, x, y) -> new MeshLiteralNodeWidget(
                    font, MeshLiteralNodeWidget.resolve(DataHolder.EMPTY_REFERENCE), x, y),
                "Asset"));
        }

        if (AbstractNodeWidget.valueAssignable(boolean.class, expectedType)) {
            for (ComparatorNodeWidget.Operator op : ComparatorNodeWidget.Operator.values()) {
                // Ordering runs on the numeric comparator; equality runs on any operand.
                if (op != ComparatorNodeWidget.Operator.EQUAL
                    && !AbstractNodeWidget.valueAssignable(providedType, double.class)) continue;

                suggestions.add(new NodeSuggestModal.Suggestion<>(
                    Component.literal(op.menuLabel()), (font, x, y) -> new ComparatorNodeWidget(font, op, x, y),
                    op == ComparatorNodeWidget.Operator.EQUAL ? "Logic" : "Math"));
            }
        }

        if (AbstractNodeWidget.valueAssignable(double.class, expectedType)
            && AbstractNodeWidget.valueAssignable(providedType, double.class)) {
            for (OperatorNodeWidget.Operator op : OperatorNodeWidget.Operator.values()) {
                suggestions.add(new NodeSuggestModal.Suggestion<>(
                    Component.literal(op.menuLabel()), (font, x, y) -> new OperatorNodeWidget(font, op, x, y), "Math"));
            }
        }

        if (AbstractNodeWidget.valueAssignable(boolean.class, expectedType)
            && AbstractNodeWidget.valueAssignable(providedType, boolean.class)) {
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.literal("NOT"), (font, x, y) -> new NotNodeWidget(font, x, y), "Logic"));
            for (LogicalOperationNodeWidget.Operator op : LogicalOperationNodeWidget.Operator.values()) {
                suggestions.add(new NodeSuggestModal.Suggestion<>(
                    Component.literal(op.menuLabel()), (font, x, y) -> new LogicalOperationNodeWidget(font, op, x, y), "Logic"));
            }
        }
    }

    /// Offers explicit numeric type-conversion entries for a pin dropped on empty canvas.
    private void addTypeConvertSuggestions(
        List<NodeSuggestModal.Suggestion<NodeWidgetFactory>> suggestions,
        NodeSuggestContext context
    ) {
        Class<?> provided = context.providedType();
        if (provided != null) {
            ScriptType from = ScriptType.of(provided);
            if (from == null) return;
            for (Class<?> target : from.implicitlyCastableTypes().keySet()) {
                suggestions.add(new NodeSuggestModal.Suggestion<>(
                    Component.literal("Convert to " + TypeConvertNodeWidget.typeName(target)),
                    (font, x, y) -> new TypeConvertNodeWidget(font, provided, target, x, y)));
            }
            return;
        }

        Class<?> expected = context.expectedType();
        if (expected == null) return;
        Class<?> expectedBoxed = ParseUtils.box(expected);
        for (ScriptType st : AkytheraRegistries.Frozen.SCRIPT_TYPE) {
            if (!st.implicitlyCastableTypes().containsKey(expectedBoxed)) continue;
            Class<?> source = st.type();
            suggestions.add(new NodeSuggestModal.Suggestion<>(
                Component.literal("Convert " + TypeConvertNodeWidget.typeName(source)
                    + " to " + TypeConvertNodeWidget.typeName(expected)),
                (font, x, y) -> new TypeConvertNodeWidget(font, source, expected, x, y)));
        }
    }

    /// Whether `function` is listed on a canvas accepting `usage`. [ScriptFunction.Usage#ANIM_GRAPH]
    /// functions are placed by their own pose nodes, never listed.
    private static boolean isListed(ScriptFunction function, Set<ScriptFunction.Usage> usage) {
        ScriptFunction.Usage functionUsage = function.metadata().usage();

        return functionUsage != ScriptFunction.Usage.ANIM_GRAPH && usage.contains(functionUsage);
    }

    /// Whether `function` has a receiver here: a member function's receiver slot (parameter 0) has to
    /// accept the owner type or the dragged `providedType`.
    private boolean receiverAccepts(ScriptFunction function, @Nullable Class<?> providedType) {
        if (!function.isMember()) return true;

        List<Class<?>> parameters = function.functionSignature().parameters();
        if (parameters.isEmpty()) return false;

        return ScriptType.castable(ownerType.get(), parameters.getFirst())
            || providedType != null && ScriptType.castable(providedType, parameters.getFirst());
    }

    private static NodeSuggestModal.Suggestion<NodeWidgetFactory> literalSuggestion(Class<?> type) {
        return new NodeSuggestModal.Suggestion<>(
            Component.literal("Literal: " + type.getSimpleName()),
            (font, x, y) -> new LiteralNodeWidget(font, type, x, y));
    }
}
