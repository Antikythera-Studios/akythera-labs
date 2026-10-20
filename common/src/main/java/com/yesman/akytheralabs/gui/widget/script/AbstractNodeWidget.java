package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.common.DataBoundWidget;
import com.yesman.akythera.client.api.gui.common.ThemeApplicableWidget;
import com.yesman.akythera.client.api.gui.common.WidgetTheme;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.core.reflection.FunctionSignature;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.editor.ValueShape;
import com.yesman.akytheralabs.editor.historytrack.HistoryScoped;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsCheckBoxTheme;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsComboBoxTheme;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsEditBoxTheme;
import com.yesman.akytheralabs.gui.widget.EditorComboBox;
import com.yesman.akytheralabs.gui.widget.EditorEditBox;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.PinLiteral;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/// A base node widget: a titled body with input pins on the left and output pins
/// on the right, each pin being either a data pin (colored rectangle + label) or an
/// execution pin (white triangle).
///
/// Positions are stored in **graph space** via the inherited `x` / `y` — the host
/// [ScriptNodeGraphWidget] renders nodes inside a translated + scaled pose, so a node
/// draws at its graph coordinates and the graph's pan/zoom maps that onto the screen.
/// The host also drives [#setFocused] and, while the cursor is over the node, passes
/// graph-space mouse coordinates so the node resolves its own hover state.
///
/// An unwired data input whose type takes a literal shows an editor after its label: the widgets
/// [EditorWidgetFactory] makes for the type, or its [EditorWidgetFactory#registryCombo] for a registry
/// entry, in the blueprint themes, each as wide as its text. What is
/// typed there is persisted as a [PinLiteral] and lowered in place of the wire. The editor widgets live in
/// graph space like the node, so the host dispatches input to them through
/// [ModalManager#inWidgetSpace].
public abstract class AbstractNodeWidget extends AbstractWidget implements AnchoredWidget, HistoryScoped {
    protected static final int TITLE_PAD = 5;
    protected static final int ROW_H = 19;
    protected static final int PIN = 7;
    protected static final int H_PAD = 8;
    protected static final int PIN_LABEL_GAP = 7;
    protected static final int MIN_WIDTH = 104;
    /// Minimum clear space between the input-side and output-side pin labels.
    protected static final int MIN_PIN_GAP = 20;
    /// Narrowest and widest a text-showing literal editor widget grows to, in graph pixels.
    private static final int LITERAL_MIN_W = 32;
    private static final int LITERAL_MAX_W = 160;
    /// Space between the widgets of one literal editor, e.g. the axes of a vector.
    private static final int LITERAL_GAP = 4;
    /// What an edit box adds around its text: the padding on both sides and the caret.
    private static final int EDIT_BOX_TEXT_CHROME = 10;
    /// What a combo box adds around its text: the padding on both sides and the dropdown arrow.
    private static final int COMBO_BOX_TEXT_CHROME = 19;

    /// The themes a literal editor wears: every themeable widget in it takes the first one it supports.
    private static final List<WidgetTheme> BLUEPRINT_THEMES = List.of(
        AkytheraLabsEditBoxTheme.BLUEPRINT_EDIT_BOX,
        AkytheraLabsComboBoxTheme.BLUEPRINT_COMBO_BOX,
        AkytheraLabsCheckBoxTheme.BLUEPRINT_CHECK_BOX
    );

    private static final int COLOR_BODY = 0xFF3A3A3A;
    private static final int COLOR_BODY_DIVIDER = 0xFF1A1A1A;
    private static final int COLOR_OUTLINE_IDLE = 0xFF101010;
    private static final int COLOR_OUTLINE_ACTIVE = 0xFFFFFFFF;
    private static final int COLOR_LABEL = 0xFFCCCCCC;
    private static final int COLOR_LABEL_HOVER = 0xFFFFFFFF;

    /// The error-marking palette, shared with the non-node editor surfaces that mark failures the
    /// same way.
    public static final int COLOR_ERROR_OUTLINE = 0xFFD83A3A;
    public static final int COLOR_ERROR_BANNER = 0xCCB01010;
    private static final String ERROR_LABEL = "ERROR!";

    /// How many causes of a runtime error the tooltip lists under it.
    private static final int RUNTIME_ERROR_CAUSES = 8;

    private static final String ADD_PIN_LABEL = "Add pin";

    /// A connection point. `exec` pins draw as a triangle (execution flow); data pins
    /// draw as a colored rectangle with `label`. `type` is the value class a data pin
    /// carries (null for execution pins and untyped data pins) — used for connection
    /// compatibility checks. `genericType` is `type` as declared, e.g. `ResourceKey<AnimationMontage>`,
    /// which decides the pin's literal editor.
    public record Pin(String label, int color, boolean exec, @Nullable Class<?> type, @Nullable Type genericType) {
        public Pin(String label, int color, boolean exec) {
            this(label, color, exec, null);
        }

        public Pin(String label, int color, boolean exec, @Nullable Class<?> type) {
            this(label, color, exec, type, type);
        }
    }

    private static final int COLOR_SELF_BADGE = 0xFF2A2A2A;
    private static final String SELF_LABEL = "self";

    /// Colour of a recorded value written after its pin's parameter name — warm, so it reads as debug
    /// output rather than as node content.
    private static final int COLOR_RECORDED_VALUE = 0xFFF0C878;

    protected final Font font;

    private final List<Pin> inputs = new ArrayList<>();
    private final List<Pin> outputs = new ArrayList<>();
    private int titleColor = 0xFF3A3A4A;

    /// Recorded values written after this node's input-pin names, keyed by input pin index. Pushed by
    /// the host each frame from the captured run; empty when this node records nothing.
    private Map<Integer, String> recordedInputs = Map.of();

    /// Replaces the recorded values and re-fits the node to them, so a pin row grows and shrinks with
    /// the text it shows. Unchanged values cost a map comparison and nothing else.
    public void setRecordedInputs(final Map<Integer, String> recordedInputs) {
        if (this.recordedInputs.equals(recordedInputs)) {
            return;
        }

        this.recordedInputs = Map.copyOf(recordedInputs);
        relayout();
    }

    /// Validation errors flagged on this node by [ScriptNodeGraphWidget#validate] (an unconnected
    /// required input, an incompatible wire). Non-empty drives the red outline + "ERROR!" banner
    /// (UE-style) and the host's hover tooltip — as does a non-empty [#intrinsicErrors]. Empty = the
    /// node passed validation.
    private List<Component> errors = List.of();

    /// What a live run of this node's script threw here, or null. Marked like a validation error, with
    /// the exception written on the banner in place of "ERROR!".
    private @Nullable Throwable runtimeError;

    /// The script owner type (`DataDrivenScript#getScriptOwner`'s class), injected by the
    /// host [ScriptNodeGraphWidget]. An unconnected data input whose type the owner satisfies
    /// is shown defaulting to `self` (UE's "Target [self]"). Null until the host sets it.
    @Nullable
    private Class<?> scriptOwnerType;

    /// Per-frame map of this node's connected **input** pins (index → the source output pin
    /// feeding it), pushed by the host each frame from the live connection set. Drives the
    /// `self` badge (a pin is "filled" once present here) and type-detecting nodes. Empty when
    /// nothing is wired in.
    private Map<Integer, Pin> connectedInputs = Map.of();

    /// Per-frame map of this node's connected **output** pins (index → the destination input
    /// pin it feeds), the counterpart to [#connectedInputs]. Lets a type-detecting node guess
    /// its type from a typed slot its result is plugged into. Empty when nothing is wired out.
    private Map<Integer, Pin> connectedOutputs = Map.of();

    /// The values typed into this node's data inputs, by input pin index. A pin keeps its value while a
    /// wire hides its editor.
    private final Map<Integer, PinLiteral> literals = new HashMap<>();

    /// The literal editor of every input pin looked at so far, by input pin index, see [#literalEditor].
    private final Map<Integer, LiteralEditor> literalEditors = new HashMap<>();

    /// The input pins whose literal editor the last [#relayout] made room for.
    private Set<Integer> shownLiterals = Set.of();

    /// Makes the literal editors' widgets. Created with the first editor.
    @Nullable
    private EditorWidgetFactory widgetFactory;

    /// Widgets on the body that take the clicks landing on them, next to the literal editors. The subclass
    /// that adds one places and draws it.
    protected final List<AbstractWidget> bodyWidgets = new ArrayList<>();

    /// The body widget or literal editor widget last clicked, while it takes text.
    @Nullable
    private AbstractWidget focusedWidget;

    /// The canvas this node was last drawn on, see [#historyScope].
    @Nullable
    private ScriptNodeGraphWidget canvas;

    /// The editor of one input pin's literal.
    ///
    /// @param pinType the declared pin type the editor was made for
    /// @param widgets laid left to right, empty when the pin takes no literal
    /// @param value   reads and writes the pin's literal, null when the pin takes no literal
    private record LiteralEditor(
        @Nullable Type pinType, List<AbstractWidget> widgets, EditorWidgetFactory.@Nullable PropertySlot value
    ) {}

    protected void setScriptOwnerType(@Nullable Class<?> ownerType) {
        this.scriptOwnerType = ownerType;
        relayout(); // reserve room for any `self` badges the new owner type enables
    }

    protected AbstractNodeWidget(Font font, Component title, int graphX, int graphY) {
        super(graphX, graphY, MIN_WIDTH, ROW_H * 3, title);

        this.font = font;
        this.x1 = AnchoredWidget.pixel(0);
        this.x2 = AnchoredWidget.pixel(0);
        this.y1 = AnchoredWidget.pixel(0);
        this.y2 = AnchoredWidget.pixel(0);
        this.horizontalAnchorType = HorizontalAnchorType.LEFT_WIDTH;
        this.verticalAnchorType = VerticalAnchorType.TOP_HEIGHT;
    }

    protected void addInput(Pin pin) { inputs.add(pin); }
    protected void addOutput(Pin pin) { outputs.add(pin); }
    protected void setTitleColor(int color) { this.titleColor = color; }

    /// This node's current state as its persisted payload. The inverse of [NodeData#createWidget];
    /// the canvas adds the id and position around it.
    public abstract NodeData toData();

    /// The registered [ScriptType] whose java type matches `type` (boxing-aware), or null when the
    /// type is null or not a serializable script type.
    public static @Nullable ScriptType scriptTypeOf(@Nullable Class<?> type) {
        if (type == null) return null;

        for (ScriptType st : AkytheraRegistries.Frozen.SCRIPT_TYPE) {
            if (ParseUtils.box(st.type()) == ParseUtils.box(type)) return st;
        }

        return null;
    }

    public int outputPinCount() { return outputs.size(); }
    public int inputPinCount() { return inputs.size(); }

    protected void setOutputLabel(int index, String label) {
        Pin p = outputs.get(index);
        outputs.set(index, new Pin(label, p.color(), p.exec(), p.type(), p.genericType()));
    }

    /// Replaces the input pin at `index` (used by type-detecting nodes that re-type their
    /// pins as connections decide their value type). Callers relayout afterwards if the
    /// change can alter the node's size.
    protected void setInputPin(int index, Pin pin) {
        inputs.set(index, pin);
    }

    /// Replaces the output pin at `index` — the counterpart to [#setInputPin] for nodes whose
    /// result type follows their detected input type (e.g. [OperatorNodeWidget]).
    protected void setOutputPin(int index, Pin pin) {
        outputs.set(index, pin);
    }

    /// Pushed by the host [ScriptNodeGraphWidget] each frame with the node's currently connected
    /// input pins (index → source output pin) and output pins (index → destination input pin).
    /// Stores both for the `self` badge, re-fits the node when a wire hides or reveals a literal
    /// editor, and notifies [#onConnectionsChanged] so type-detecting subclasses can re-type pins.
    public void updateConnections(Map<Integer, Pin> connectedInputs, Map<Integer, Pin> connectedOutputs) {
        this.connectedInputs = connectedInputs;
        this.connectedOutputs = connectedOutputs;

        Set<Integer> shown = new HashSet<>();

        for (int i = 0; i < inputs.size(); i++) {
            if (showsLiteral(i)) shown.add(i);
        }

        if (!shown.equals(shownLiterals)) {
            endInlineEdit();
            relayout();
        }

        onConnectionsChanged();
    }

    /// Called by the canvas drawing this node each frame. A node deleted from it keeps pointing at it.
    public void setCanvas(ScriptNodeGraphWidget canvas) {
        this.canvas = canvas;
    }

    /// The canvas this node sits on, so the edits of its literal editors and Details rows are dropped with
    /// the canvas' tab. The node itself before it is first drawn.
    @Override
    public Object historyScope() {
        return canvas != null ? canvas : this;
    }

    /// Hook for nodes that reconfigure themselves from their connections (e.g.
    /// [ComparatorNodeWidget] / [OperatorNodeWidget] deciding their value type). Default: nothing.
    protected void onConnectionsChanged() {}

    /// The source output pins currently feeding this node's inputs, in input-pin order.
    protected Iterable<Pin> connectedInputSources() {
        return connectedInputs.values();
    }

    /// The source output pin feeding input `index`, or null while that input is unwired — for the
    /// type-detecting nodes whose inputs do not all take part in the detection.
    protected @Nullable Pin connectedInputSource(int index) {
        return connectedInputs.get(index);
    }

    /// The destination input pins this node's outputs currently feed into, in output-pin order.
    protected Iterable<Pin> connectedOutputTargets() {
        return connectedOutputs.values();
    }

    protected boolean isInputConnected(int index) {
        return connectedInputs.containsKey(index);
    }

    /// Re-types the data pins of a function declaring type variables, e.g. `E` of `List<E>`, from the wires.
    /// A collection wired in binds the variables of its slot first, then a value wired into a variable slot,
    /// then the input the result is wired into. A variable nothing binds stays open and takes any value.
    ///
    /// @param firstInput   the input pin of the first parameter
    /// @param resultOutput the output pin of the result, or -1 when there is none
    protected void bindTypeVariables(FunctionSignature signature, int firstInput, int resultOutput) {
        List<Type> declared = signature.genericParameters();

        if (declared.stream().allMatch(Class.class::isInstance) && signature.genericReturnType() instanceof Class<?>) return;

        Map<TypeVariable<?>, Type> bindings = new HashMap<>();

        for (boolean collections : new boolean[] { true, false }) {
            for (int i = 0; i < declared.size(); i++) {
                if (declared.get(i) instanceof ParameterizedType != collections) continue;

                Pin source = connectedInputs.get(firstInput + i);
                if (source != null) bind(declared.get(i), source.genericType(), bindings);
            }
        }

        Pin target = resultOutput < 0 ? null : connectedOutputs.get(resultOutput);
        if (target != null) bind(signature.genericReturnType(), target.genericType(), bindings);

        boolean changed = false;

        for (int i = 0; i < declared.size(); i++) {
            Pin pin = inputs.get(firstInput + i);
            Pin retyped = retype(pin, declared.get(i), bindings);

            changed |= !retyped.equals(pin);
            inputs.set(firstInput + i, retyped);
        }

        if (resultOutput >= 0) {
            Pin pin = outputs.get(resultOutput);
            Pin retyped = retype(pin, signature.genericReturnType(), bindings);

            changed |= !retyped.equals(pin);
            outputs.set(resultOutput, retyped);
        }

        if (changed) relayout();
    }

    /// Binds the variables `declared` names to the matching parts of `actual`, keeping the ones already bound.
    /// An `Object`, a wildcard or a type variable binds nothing.
    private static void bind(Type declared, @Nullable Type actual, Map<TypeVariable<?>, Type> bindings) {
        switch (declared) {
            case TypeVariable<?> variable when actual instanceof Class<?> c && c != Object.class ->
                bindings.putIfAbsent(variable, ParseUtils.box(c));
            case TypeVariable<?> variable when actual instanceof ParameterizedType ->
                bindings.putIfAbsent(variable, actual);
            case ParameterizedType d when actual instanceof ParameterizedType a && d.getRawType() == a.getRawType() -> {
                Type[] declaredArguments = d.getActualTypeArguments();
                Type[] actualArguments = a.getActualTypeArguments();

                for (int i = 0; i < declaredArguments.length; i++) bind(declaredArguments[i], actualArguments[i], bindings);
            }
            default -> {}
        }
    }

    /// `declared` with its bound variables replaced.
    private static Type substitute(Type declared, Map<TypeVariable<?>, Type> bindings) {
        return switch (declared) {
            case TypeVariable<?> variable -> bindings.getOrDefault(variable, variable);
            case ParameterizedType p -> ParseUtils.parameterized(
                (Class<?>) p.getRawType(),
                Arrays.stream(p.getActualTypeArguments()).map(argument -> substitute(argument, bindings)).toArray(Type[]::new)
            );
            default -> declared;
        };
    }

    /// `pin` typed as `declared` under `bindings`.
    private static Pin retype(Pin pin, Type declared, Map<TypeVariable<?>, Type> bindings) {
        Type type = substitute(declared, bindings);
        Class<?> erased = ParseUtils.erase(type);

        return new Pin(pin.label(), NodeColors.forType(erased), false, erased, type);
    }

    // ***************************************************************
    // Validation
    // ***************************************************************

    /// Replaces this node's validation errors (UE-style red "ERROR!" banner + outline + hover
    /// tooltip). Passing an empty list clears the flag. Driven by [ScriptNodeGraphWidget#validate].
    public void setErrors(@Nullable List<Component> errors) {
        this.errors = (errors == null || errors.isEmpty()) ? List.of() : List.copyOf(errors);
    }

    /// The errors [#setErrors] and [#addError] flagged on this node.
    public List<Component> validationErrors() {
        return errors;
    }

    /// Appends one more validation error, keeping the ones already flagged — the hook for a problem
    /// the owning canvas cannot see on its own, such as a name duplicated across the script's canvases.
    /// The next [#setErrors] (every [ScriptNodeGraphWidget#validate]) clears it again.
    public void addError(Component error) {
        List<Component> all = new ArrayList<>(errors);
        all.add(error);
        this.errors = List.copyOf(all);
    }

    /// Replaces the runtime error drawn on this node. Null clears it. [#setErrors] leaves it alone.
    public void setRuntimeError(@Nullable Throwable runtimeError) {
        this.runtimeError = runtimeError;
    }

    public boolean hasErrors() {
        return !errors.isEmpty() || !intrinsicErrors().isEmpty() || runtimeError != null;
    }

    /// Errors the node determines about itself — an asset binding that resolves nowhere, a required
    /// pick left unmade — next to the wiring problems the graph-wide [ScriptNodeGraphWidget#validate]
    /// pass finds. [#hasErrors] / [#errorLines] read them fresh, so such a node is red on sight, and
    /// [ScriptNodeGraphWidget#validate] folds them into its issue list, so they fail a compile too.
    ///
    /// Runs per node, per frame: keep the check to cheap lookups. Default: none.
    public List<Component> intrinsicErrors() {
        return List.of();
    }

    /// Whether validation may flag this node. Cosmetic / transparent nodes (a reroute knot) opt
    /// out so a dangling pass-through is not reported as a "required input" error. Default: true.
    public boolean reportsValidationErrors() {
        return true;
    }

    /// The error messages flagged on this node, for the host to show as a hover tooltip — the
    /// validation pass' ([#setErrors]), the node's own ([#intrinsicErrors]), and the runtime error in
    /// full with its causes.
    public List<Component> errorLines() {
        List<Component> all = new ArrayList<>(errors);
        all.addAll(intrinsicErrors());

        Throwable cause = runtimeError;

        for (int depth = 0; cause != null && depth <= RUNTIME_ERROR_CAUSES; depth++) {
            all.add(Component.literal((depth == 0 ? "" : "Caused by: ") + cause));
            cause = cause.getCause();
        }

        return all;
    }

    /// Whether input pin `index` is a *required* data input left unsatisfied — not an execution
    /// pin, not wired, not defaulting to `self`, and not holding a literal. These are the pins
    /// validation flags as errors; a registry entry pin with nothing picked yet is one. Relies on the
    /// host having pushed the current connection state first (see [ScriptNodeGraphWidget#validate]).
    public boolean isRequiredInputUnconnected(int index) {
        Pin pin = inputs.get(index);

        return !pin.exec()
            && !isInputConnected(index)
            && !shouldShowSelf(index, pin)
            && !(showsLiteral(index) && literals.containsKey(index));
    }

    // ***************************************************************
    // Literal inputs
    // ***************************************************************

    /// The values typed into this node's data inputs, in pin order, for the canvas to persist. A value
    /// whose pin is gone or has changed type is left out.
    public List<PinLiteral> pinLiterals() {
        return literals.values().stream()
            .filter(literal -> literal.pin() < inputs.size()
                && literal.type().equals(literalType(inputs.get(literal.pin()))))
            .sorted(Comparator.comparingInt(PinLiteral::pin))
            .toList();
    }

    /// The type of the literal `pin` takes, read from its declared type. Null for an execution pin or an
    /// untyped or unregistered one.
    private static @Nullable ScriptType literalType(Pin pin) {
        return pin.exec() || pin.genericType() == null ? null : ScriptType.of(pin.genericType());
    }

    /// Replaces the values typed into this node's data inputs with the persisted `literals`, and rebuilds
    /// the editors showing them.
    public void setPinLiterals(List<PinLiteral> literals) {
        endInlineEdit();

        this.literals.clear();
        for (PinLiteral literal : literals) this.literals.put(literal.pin(), literal);

        literalEditors.clear();
        relayout();
    }

    /// Whether input pin `index` shows its literal editor: an unwired data pin that takes a literal and
    /// does not stand for `self`, on a node validation checks.
    private boolean showsLiteral(int index) {
        Pin pin = inputs.get(index);

        return reportsValidationErrors()
            && !pin.exec()
            && !isInputConnected(index)
            && !isSelfCapable(pin)
            && !literalEditor(index).widgets().isEmpty();
    }

    /// The literal editor of input pin `index`, made on the first look and again whenever the pin changes
    /// type. It holds no widgets when the pin takes no literal: an execution pin, a type without a codec
    /// or a widget producer, or a list or record, which spans more than one row.
    ///
    /// A new editor starts from the pin's stored [PinLiteral] when it is of the pin's type, else from the
    /// type's blank value, and stores what it starts from. A registry entry with nothing picked stores
    /// nothing. A registry entry is picked from a combo over every entry of its registry.
    @SuppressWarnings("unchecked")
    private LiteralEditor literalEditor(int index) {
        Pin pin = inputs.get(index);
        LiteralEditor cached = literalEditors.get(index);

        if (cached != null && Objects.equals(cached.pinType(), pin.genericType())) return cached;

        ScriptType type = literalType(pin);
        ValueShape shape = type == null || type.codec() == null ? null : ValueShape.of(type);
        LiteralEditor editor = new LiteralEditor(pin.genericType(), List.of(), null);

        if (shape instanceof ValueShape.Leaf || shape instanceof ValueShape.RegistryLeaf) {
            PinLiteral stored = literals.get(index);
            Object initial = shape.blank();

            if (stored != null && stored.type().equals(type)) {
                try {
                    initial = shape.fromJson(stored.value());
                } catch (RuntimeException ignored) {
                    // A value that no longer decodes starts over from blank.
                }
            }

            Object[] value = {initial};

            Consumer<@Nullable Object> write = v -> {
                value[0] = v;

                try {
                    literals.put(index, new PinLiteral(index, type, shape.toJson(v)));
                } catch (RuntimeException e) {
                    literals.remove(index);
                }
            };

            write.accept(initial);

            if (widgetFactory == null) widgetFactory = new EditorWidgetFactory(font);

            EditorWidgetFactory.PropertySlot slot = new EditorWidgetFactory.PropertySlot(pin.label(), shape, this, () -> value[0], write);

            // An edit on the node shows in the Details panel too.
            EditorWidgetFactory.PropertySlot nodeSlot = new EditorWidgetFactory.PropertySlot(
                pin.label(), shape, this, () -> value[0], v -> {
                    write.accept(v);
                    if (canvas != null) canvas.refreshDetails(this);
                });

            List<? extends AbstractWidget> produced = shape instanceof ValueShape.RegistryLeaf
                ? widgetFactory.registryCombo(nodeSlot)
                : widgetFactory.produce(nodeSlot);

            if (produced != null && !produced.isEmpty()) {
                ScreenRectangle budget = new ScreenRectangle(0, 0, LITERAL_MAX_W * produced.size(), ROW_H);
                Deque<GuiEventListener> themed = new ArrayDeque<>(produced);

                while (!themed.isEmpty()) {
                    GuiEventListener listener = themed.pop();

                    if (listener instanceof ContainerEventHandler container) themed.addAll(container.children());
                    if (!(listener instanceof ThemeApplicableWidget<?> applicable)) continue;

                    for (WidgetTheme theme : BLUEPRINT_THEMES) {
                        if (applicable.isSupportedTheme(theme)) {
                            ((ThemeApplicableWidget<WidgetTheme>) applicable).setTheme(theme);
                            break;
                        }
                    }
                }

                for (AbstractWidget widget : produced) {
                    if (widget instanceof AnchoredWidget anchored) anchored.relocate(budget);

                    pullValue(widget);
                }

                editor = new LiteralEditor(pin.genericType(), List.copyOf(produced), slot);
                fitLiteralWidths(editor);
            }
        }

        literalEditors.put(index, editor);

        return editor;
    }

    /// One Details row per input pin showing its literal editor. A value written there shows on the node too.
    public List<EditorWidgetFactory.PropertySlot> literalSlots() {
        List<EditorWidgetFactory.PropertySlot> slots = new ArrayList<>();

        for (int i = 0; i < inputs.size(); i++) {
            if (!showsLiteral(i)) continue;

            LiteralEditor editor = literalEditor(i);
            EditorWidgetFactory.PropertySlot value = editor.value();

            if (value == null) continue;

            slots.add(new EditorWidgetFactory.PropertySlot(value.label(), value.shape(), this, value.getter(), v -> {
                value.set(v);
                editor.widgets().forEach(AbstractNodeWidget::pullValue);
            }));
        }

        return slots;
    }

    /// Shows the bound value on `widget`, what a WidgetTable's initialize does for the rows it holds.
    @SuppressWarnings("unchecked")
    private static void pullValue(AbstractWidget widget) {
        if (!(widget instanceof DataBoundWidget<?> bound)) return;

        try {
            ((DataBoundWidget<Object>) bound).valueSetter().accept(bound.getDataProvider().call());
        } catch (Exception ignored) {
            // The widget keeps showing what it showed.
        }
    }

    /// Sizes every text-showing widget of `editor` to its text, between [#LITERAL_MIN_W] and
    /// [#LITERAL_MAX_W]. A widget showing no text, a checkbox, keeps its size. Returns whether a width
    /// changed.
    private boolean fitLiteralWidths(LiteralEditor editor) {
        boolean changed = false;

        for (AbstractWidget widget : editor.widgets()) {
            // The part showing text: the widget itself, or the edit box inside a picker.
            GuiEventListener shown = widget;

            if (widget instanceof ContainerEventHandler container) {
                shown = container.children().stream()
                    .<GuiEventListener>map(child -> child)
                    .filter(EditorEditBox.class::isInstance)
                    .findFirst()
                    .orElse(widget);
            }

            int width = switch (shown) {
                case EditorEditBox<?> box -> font.width(box.getText()) + EDIT_BOX_TEXT_CHROME + widget.getWidth() - box.getWidth();
                case EditorComboBox<?> combo -> font.width(combo.displayString()) + COMBO_BOX_TEXT_CHROME + widget.getWidth() - combo.getWidth();
                default -> -1;
            };

            if (width < 0) continue;

            width = Math.clamp(width, LITERAL_MIN_W, LITERAL_MAX_W);

            if (width == widget.getWidth()) continue;

            widget.setWidth(width);
            widget.setX(widget.getX()); // a picker lays its children out on a move
            changed = true;
        }

        return changed;
    }

    /// Whether this node lets the user grow/shrink its output pins at runtime
    /// (e.g. a sequence's `Then N` outputs). Enables the "Add pin" button and the
    /// right-click "Remove Pin" gesture.
    public boolean supportsDynamicOutputs() { return false; }

    /// Whether the user may delete this node from the graph. The flow's entry-point
    /// node (e.g. "Update") overrides this to false so it can never be removed.
    public boolean isDeletable() { return true; }

    /// Whether the user may copy this node. A node whose identity is a registered signature that must
    /// be unique per animator, or a definition the rest of the graph reads back by name, overrides
    /// this to false: the editor cannot mint it a name of its own, so a second one is incoherent
    /// wherever it is pasted. A node that cannot be deleted cannot be copied either.
    public boolean isCopyable() { return isDeletable(); }

    /// Whether double-clicking this node opens its own editor (e.g. a
    /// [StateMachineNodeWidget] opening its state-machine sub-tab). The host graph fires
    /// its node-activation handler for such nodes. Default: no.
    public boolean isActivatable() { return false; }

    /// Invoked when the "Add pin" button is pressed. Dynamic-output nodes override
    /// this to append an output and relayout.
    protected void onAddPin() {}

    /// Hook for a click landing on the node **body** (below the title bar, not on a
    /// pin marker). Returns true when the node consumed the click — e.g. an inline
    /// literal node editing its value. The host graph calls this before starting a
    /// header drag, so a node can claim body clicks for its own controls.
    ///
    /// Default: hands the click to the [#bodyWidgets] entry or literal editor widget under it, which
    /// keeps the keyboard while it takes text.
    public boolean onBodyClick(int graphX, int graphY, double screenX, double screenY) {
        AbstractWidget hit = null;

        for (AbstractWidget widget : bodyWidgets) {
            if (widget.isMouseOver(graphX, graphY)) hit = widget;
        }

        for (int i = 0; i < inputs.size() && hit == null; i++) {
            if (!showsLiteral(i)) continue;

            for (AbstractWidget widget : literalEditor(i).widgets()) {
                if (widget.isMouseOver(graphX, graphY)) hit = widget;
            }
        }

        if (hit != focusedWidget) endInlineEdit();
        if (hit == null) return false;

        hit.mouseClicked(new MouseButtonEvent(graphX, graphY, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0)), false);
        focusBodyWidget(hit);

        return true;
    }

    /// Gives `widget` the focus a click on it would, when it is one of this node's [#bodyWidgets] or literal
    /// editor widgets: it keeps the keyboard while it takes text. A literal editor a wire hides takes no
    /// focus. Returns whether this node holds `widget`.
    public boolean focusBodyWidget(GuiEventListener widget) {
        AbstractWidget owned = null;
        int pin = -1;

        for (AbstractWidget candidate : bodyWidgets) {
            if (candidate == widget) owned = candidate;
        }

        for (Map.Entry<Integer, LiteralEditor> entry : literalEditors.entrySet()) {
            for (AbstractWidget candidate : entry.getValue().widgets()) {
                if (candidate != widget) continue;

                owned = candidate;
                pin = entry.getKey();
            }
        }

        if (owned == null) return false;
        if (owned != focusedWidget) endInlineEdit();

        boolean shown = pin < 0
            || pin < inputs.size() && showsLiteral(pin) && literalEditor(pin).widgets().contains(owned);

        if (!shown) return true;

        owned.setFocused(true);
        focusedWidget = owned;

        // A checkbox, a combo or a picker button takes no keyboard.
        if (!isInlineEditing()) endInlineEdit();

        return true;
    }

    /// Whether this node is currently hosting an inline text editor (e.g. a literal node
    /// being typed into). While true the host graph routes keyboard input here via
    /// [#inlineKeyPressed] / [#inlineCharTyped] and suppresses its own shortcuts (so typing
    /// "d" edits the value instead of deleting the node).
    ///
    /// Default: whether the focus chain of the clicked body widget ends at a focused text box.
    public boolean isInlineEditing() {
        GuiEventListener leaf = focusedWidget;

        while (leaf instanceof ContainerEventHandler container && container.getFocused() != null) {
            leaf = container.getFocused();
        }

        return leaf != null && leaf.isFocused() && (leaf instanceof EditorEditBox<?> || leaf instanceof EditBox);
    }

    /// Routes a key press into the node's inline editor; only called while [#isInlineEditing].
    /// Default: Escape ends the edit, anything else goes to the clicked body widget.
    public boolean inlineKeyPressed(@NonNull KeyEvent event) {
        if (focusedWidget == null) return false;

        if (event.isEscape()) {
            endInlineEdit();
            return true;
        }

        return focusedWidget.keyPressed(event);
    }

    /// Routes a typed character into the node's inline editor; only called while
    /// [#isInlineEditing]. Default: goes to the clicked body widget.
    public boolean inlineCharTyped(@NonNull CharacterEvent event) {
        return focusedWidget != null && focusedWidget.charTyped(event);
    }

    /// Ends (committing) any inline edit, e.g. when the user clicks away. Default: unfocuses the clicked
    /// body widget.
    public void endInlineEdit() {
        if (focusedWidget == null) return;

        AbstractWidget widget = focusedWidget;
        focusedWidget = null;

        widget.setFocused(false);
    }

    /// Drawn after the base node (pins, labels, outline) so a subclass can overlay extra
    /// content on the body — e.g. a literal node's inline edit box. Runs inside the graph's
    /// pan/zoom pose, in graph space. Default: nothing.
    protected void extractBodyOverlay(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {}

    /// Lines shown as a tooltip while the cursor hovers the node's title bar, or null
    /// when the node has nothing to add. The host graph renders these in screen space
    /// (outside the pan/zoom pose) so they stay legible and unclipped.
    public @Nullable List<Component> headerTooltipLines() {
        return null;
    }

    /// Removes the output pin at `index` and relayouts. The host graph fixes up the
    /// connections referencing this node's outputs around the call.
    public void removeOutputPin(int index) {
        outputs.remove(index);
        relayout();
    }

    /// Recomputes width/height from the title and pin rows. Subclasses must call this
    /// once after adding their pins.
    ///
    /// Width fits the widest input label — plus any recorded value and literal editor after it — and
    /// the widest output label (execution pins included: their labels like "True"/"Then 0" take space
    /// too), keeping at least [#MIN_PIN_GAP] of clear space between the two columns so they never overlap.
    protected void relayout() {
        int titleW = font.width(getMessage()) + H_PAD * 2;

        int selfReserve = font.width(SELF_LABEL) + 6 + PIN_LABEL_GAP;
        int maxIn = 0;
        Set<Integer> shown = new HashSet<>();

        for (int i = 0; i < inputs.size(); i++) {
            Pin pin = inputs.get(i);
            String suffix = recordedSuffix(i);
            int literalW = 0;

            if (showsLiteral(i)) {
                List<AbstractWidget> widgets = literalEditor(i).widgets();

                shown.add(i);
                literalW = widgets.stream().mapToInt(AbstractWidget::getWidth).sum()
                    + LITERAL_GAP * (widgets.size() - 1)
                    + PIN_LABEL_GAP;
            }

            maxIn = Math.max(maxIn, font.width(pin.label())
                + (suffix == null ? 0 : font.width(suffix))
                + (isSelfCapable(pin) ? selfReserve : 0)
                + literalW);
        }

        shownLiterals = shown;

        int maxOut = outputs.stream().mapToInt(p -> font.width(p.label())).max().orElse(0);
        // Mirrors drawPin geometry: marker column (H_PAD) + half-pin + gap + label, on
        // each side, with MIN_PIN_GAP of slack between the input and output labels.
        int bodyW = H_PAD * 2 + PIN + PIN_LABEL_GAP * 2 + maxIn + maxOut + MIN_PIN_GAP;

        int rows = Math.max(inputs.size(), outputs.size());
        int extra = supportsDynamicOutputs() ? ROW_H : 0;

        setWidth(Math.max(MIN_WIDTH, Math.max(titleW, bodyW)));
        setHeight(titleBarHeight() + rows * ROW_H + 5 + extra);
    }

    protected int titleBarHeight() {
        return font.lineHeight + TITLE_PAD * 2;
    }

    /// Whole-node bounds test, in graph space.
    public boolean containsGraphPoint(int gx, int gy) {
        return gx >= getX() && gx < getX() + getWidth() && gy >= getY() && gy < getY() + getHeight();
    }

    /// Title-bar bounds test, in graph space — the draggable handle.
    public boolean isOverHeader(int gx, int gy) {
        return gx >= getX() && gx < getX() + getWidth() && gy >= getY() && gy < getY() + titleBarHeight();
    }

    public Pin inputPin(int index) { return inputs.get(index); }
    public Pin outputPin(int index) { return outputs.get(index); }

    /// The `": value"` written after input pin `index`'s name, or null when it records nothing.
    protected @Nullable String recordedSuffix(int index) {
        String recorded = recordedInputs.get(index);

        return recorded == null ? null : ": " + recorded;
    }

    /// Index of the output pin (exec or data) whose marker/row sits under the given
    /// graph point, or -1 when none. The host graph uses this to begin a wire drag.
    public int outputPinIndexAt(int gx, int gy) {
        int cx = getX() + getWidth() - H_PAD;

        for (int i = 0; i < outputs.size(); i++) {
            if (overPinMarker(gx, gy, cx, rowCenterY(i))) return i;
        }

        return -1;
    }

    /// Index of the output pin whose **row** (right half of the node — marker and
    /// label band) contains the given graph point, or -1 when none. More forgiving
    /// than [#outputPinIndexAt]; used for the right-click "Remove Pin" gesture.
    public int outputPinRowAt(int gx, int gy) {
        if (gx < getX() + getWidth() / 2 || gx >= getX() + getWidth()) return -1;

        int rowTop = getY() + titleBarHeight() + 3;
        for (int i = 0; i < outputs.size(); i++) {
            int top = rowTop + i * ROW_H;
            if (gy >= top && gy < top + ROW_H) return i;
        }

        return -1;
    }

    /// Index of the input pin (exec or data) whose marker/row sits under the given
    /// graph point, or -1 when none. Used to resolve a wire's drop target.
    public int inputPinIndexAt(int gx, int gy) {
        int cx = getX() + H_PAD;

        for (int i = 0; i < inputs.size(); i++) {
            if (overPinMarker(gx, gy, cx, rowCenterY(i))) return i;
        }

        return -1;
    }

    /// Index of the input pin whose **hover band** — the marker, its label and any recorded value
    /// after it, exactly the span [#drawPin] brightens — contains the given graph point, or -1 when
    /// none. More forgiving than [#inputPinIndexAt]; the right-click gesture reads from here so the
    /// menu opens wherever the row lights up.
    public int inputPinLabelAt(int gx, int gy) {
        if (gx < getX()) return -1;

        for (int i = 0; i < inputs.size(); i++) {
            Pin pin = inputs.get(i);
            String recorded = recordedSuffix(i);
            int cy = rowCenterY(i);

            int right = getX() + inputBandWidth(
                pin.label().isEmpty() ? 0 : font.width(pin.label()),
                recorded == null ? 0 : font.width(recorded)
            );

            if (gx < right && gy >= cy - ROW_H / 2 && gy < cy + ROW_H / 2) return i;
        }

        return -1;
    }

    /// Width of an input pin's hover band measured from the node's left edge: the marker column, the
    /// label, and any recorded value written after it.
    private int inputBandWidth(int labelW, int recordedW) {
        return H_PAD + PIN / 2 + PIN_LABEL_GAP + labelW + recordedW + 2;
    }

    /// Whether the given graph point is over the "Add pin" button row at the bottom
    /// of a dynamic-output node.
    public boolean isOverAddPinButton(int gx, int gy) {
        if (!supportsDynamicOutputs()) return false;

        int rows = Math.max(inputs.size(), outputs.size());
        int addTop = getY() + titleBarHeight() + 3 + rows * ROW_H;
        return gx >= getX() && gx < getX() + getWidth() && gy >= addTop && gy < addTop + ROW_H;
    }

    /// Index of the first execution input pin (the slot a flow wire connects into),
    /// or -1 when this node has none.
    public int firstExecInputPinIndex() {
        for (int i = 0; i < inputs.size(); i++) {
            if (inputs.get(i).exec()) return i;
        }

        return -1;
    }

    /// Index of the first execution output pin, or -1 — the slot a wire dragged from
    /// an execution *input* pin connects back to.
    public int firstExecOutputPinIndex() {
        for (int i = 0; i < outputs.size(); i++) {
            if (outputs.get(i).exec()) return i;
        }

        return -1;
    }

    /// Index of the first data (non-execution) output pin, or -1 — the value slot a
    /// pure node feeds into a data input pin it was spawned from.
    public int firstDataOutputPinIndex() {
        for (int i = 0; i < outputs.size(); i++) {
            if (!outputs.get(i).exec()) return i;
        }

        return -1;
    }

    /// Index of the first data (non-execution) input pin that a value of `providedType`
    /// can drive (boxing / numeric-coercion aware), or -1 — the parameter slot the value
    /// dragged from an output pin is wired into when this node is spawned to consume it.
    public int firstDataInputPinIndex(@Nullable Class<?> providedType) {
        for (int i = 0; i < inputs.size(); i++) {
            Pin pin = inputs.get(i);
            if (!pin.exec() && valueAssignable(providedType, pin.type())) return i;
        }

        return -1;
    }

    private boolean overPinMarker(int gx, int gy, int cx, int cy) {
        return gx >= cx - PIN && gx <= cx + PIN && gy >= cy - ROW_H / 2 && gy < cy + ROW_H / 2;
    }

    private int rowCenterY(int index) {
        return getY() + titleBarHeight() + 3 + index * ROW_H + ROW_H / 2;
    }

    /// Graph-space center X of the output pin at `index` (its marker column).
    public int outputPinCenterX(int index) {
        return getX() + getWidth() - H_PAD;
    }

    /// Graph-space center Y of the output pin row at `index`.
    public int outputPinCenterY(int index) {
        return rowCenterY(index);
    }

    /// Graph-space center X of the input pin at `index` (its marker column).
    public int inputPinCenterX(int index) {
        return getX() + H_PAD;
    }

    /// Graph-space center Y of the input pin row at `index`.
    public int inputPinCenterY(int index) {
        return rowCenterY(index);
    }

    /// Whether a wire from output pin `output` may connect into input pin `input`:
    /// execution pins only pair with execution pins; data pins pair when the provided
    /// type is assignable to (or numerically coercible into) the expected type, and two
    /// declared lists, sets or maps also hold matching elements, see [#argumentsCompatible].
    public static boolean pinsCompatible(Pin output, Pin input) {
        if (output.exec() || input.exec()) return output.exec() && input.exec();

        return typeCompatible(output.type(), input.type()) && argumentsCompatible(output.genericType(), input.genericType());
    }

    /// Whether the elements of a declared `List`, `Set` or `Map` fit those of the same collection expected: each
    /// provided type argument assignable to the expected one, with no numeric coercion, since the elements are
    /// not converted on the way. Accepted when either side is raw or another type, and for an argument that is
    /// a wildcard or a type variable.
    static boolean argumentsCompatible(@Nullable Type provided, @Nullable Type expected) {
        if (!(provided instanceof ParameterizedType p) || !(expected instanceof ParameterizedType e)) return true;
        if (p.getRawType() != e.getRawType() || !List.of(List.class, Set.class, Map.class).contains(p.getRawType())) return true;

        Type[] providedArguments = p.getActualTypeArguments();
        Type[] expectedArguments = e.getActualTypeArguments();

        for (int i = 0; i < providedArguments.length; i++) {
            Type pa = providedArguments[i];
            Type ea = expectedArguments[i];

            if (!(pa instanceof Class<?> || pa instanceof ParameterizedType)) continue;
            if (!(ea instanceof Class<?> || ea instanceof ParameterizedType)) continue;

            if (!boxed(ParseUtils.erase(ea)).isAssignableFrom(boxed(ParseUtils.erase(pa)))) return false;
            if (!argumentsCompatible(pa, ea)) return false;
        }

        return true;
    }

    /// Whether a value of `provided` can drive a slot expecting `expected`, under the same
    /// assignability and numeric-coercion rules as [#pinsCompatible]. A null on either side means
    /// untyped or unknown, and is accepted.
    public static boolean valueAssignable(@Nullable Class<?> provided, @Nullable Class<?> expected) {
        return typeCompatible(provided, expected);
    }

    private static boolean typeCompatible(@Nullable Class<?> provided, @Nullable Class<?> expected) {
        if (provided == null || expected == null) return true;

        Class<?> p = boxed(provided);
        Class<?> e = boxed(expected);

        if (p == Object.class || e.isAssignableFrom(p)) return true;

        return Number.class.isAssignableFrom(e) && Number.class.isAssignableFrom(p);
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive())   return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class)    return Byte.class;
        if (type == short.class)   return Short.class;
        if (type == char.class)    return Character.class;
        if (type == int.class)     return Integer.class;
        if (type == long.class)    return Long.class;
        if (type == float.class)   return Float.class;
        if (type == double.class)  return Double.class;
        return Void.class;
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();

        boolean focused = isFocused();
        boolean hovered = containsGraphPoint(mouseX, mouseY);

        // Body.
        g.fill(x, y, x + w, y + h, COLOR_BODY);

        // Title bar — brightened while focused.
        int titleH = titleBarHeight();
        int headerColor = focused ? brighten(titleColor, 0x33) : titleColor;
        g.fill(x, y, x + w, y + titleH, headerColor);
        g.fill(x, y + titleH, x + w, y + titleH + 1, COLOR_BODY_DIVIDER);
        g.text(font, getMessage(), x + H_PAD, y + (titleH - font.lineHeight) / 2, 0xFFFFFFFF, false);

        // Pin rows.
        int rowTop = y + titleH + 3;
        int rows = Math.max(inputs.size(), outputs.size());

        for (int i = 0; i < rows; i++) {
            int cy = rowTop + i * ROW_H + ROW_H / 2;
            if (i < inputs.size()) drawPin(g, x, cy, inputs.get(i), true, shouldShowSelf(i, inputs.get(i)), recordedSuffix(i), mouseX, mouseY);
            if (i < outputs.size()) drawPin(g, x + w, cy, outputs.get(i), false, false, null, mouseX, mouseY);
        }

        // Literal editors, after their pin's label and recorded value, each widget as wide as its text.
        boolean refit = false;

        for (int i = 0; i < inputs.size(); i++) {
            if (!showsLiteral(i)) continue;

            LiteralEditor editor = literalEditor(i);
            Pin pin = inputs.get(i);
            String recorded = recordedSuffix(i);
            int labelW = (pin.label().isEmpty() ? 0 : font.width(pin.label())) + (recorded == null ? 0 : font.width(recorded));
            int left = x + H_PAD + PIN / 2 + PIN_LABEL_GAP + (labelW == 0 ? 0 : labelW + PIN_LABEL_GAP);

            refit |= fitLiteralWidths(editor);

            for (AbstractWidget widget : editor.widgets()) {
                widget.setX(left);
                widget.setY(rowCenterY(i) - widget.getHeight() / 2);
                widget.extractRenderState(g, mouseX, mouseY, a);

                left += widget.getWidth() + LITERAL_GAP;
            }
        }

        // Next frame draws the node at the width the editors grew or shrank to.
        if (refit) relayout();

        // "Add pin" button row (dynamic-output nodes only), right-aligned like UE.
        if (supportsDynamicOutputs()) {
            int addTop = rowTop + rows * ROW_H;
            int addCy = addTop + ROW_H / 2;
            boolean btnHover = mouseX >= x && mouseX < x + w && mouseY >= addTop && mouseY < addTop + ROW_H;
            int col = btnHover ? COLOR_LABEL_HOVER : COLOR_LABEL;

            int plusCx = x + w - H_PAD;
            int lw = font.width(ADD_PIN_LABEL);
            g.text(font, ADD_PIN_LABEL, plusCx - PIN / 2 - PIN_LABEL_GAP - lw, addCy - font.lineHeight / 2, col, false);
            drawPlus(g, plusCx, addCy, col);
        }

        // Outline last so it sits above the fills. Red when the node failed validation, else
        // white when hovered or focused.
        int outlineColor = hasErrors()
            ? COLOR_ERROR_OUTLINE
            : (hovered || focused) ? COLOR_OUTLINE_ACTIVE : COLOR_OUTLINE_IDLE;
        g.outline(x, y, w, h, outlineColor);

        // UE-style "ERROR!" banner hung beneath the body for a node that failed validation. A runtime
        // error writes its exception there instead.
        if (hasErrors()) {
            String label = runtimeError == null
                ? ERROR_LABEL
                : font.plainSubstrByWidth(runtimeError.getClass().getSimpleName()
                    + (runtimeError.getMessage() == null ? "" : ": " + runtimeError.getMessage()), w - 8);

            drawErrorBanner(g, font, x, y + h, w, label);
        }

        // Subclass overlay (e.g. a literal node's inline edit box), drawn on top of the body.
        extractBodyOverlay(g, mouseX, mouseY, a);
    }

    /// Whether a data pin's type the script owner satisfies — i.e. it can default to `self`.
    /// Object-typed slots are excluded so only the actual owner "Target" pins (not every
    /// opaque slot) auto-default to self.
    private boolean isSelfCapable(Pin pin) {
        if (pin.exec() || scriptOwnerType == null || pin.type() == null || pin.type() == Object.class) return false;
        return ScriptType.castable(scriptOwnerType, pin.type());
    }

    /// Whether input pin `index` should render the `self` badge: a self-capable pin left
    /// unconnected.
    private boolean shouldShowSelf(int index, Pin pin) {
        return isSelfCapable(pin) && !isInputConnected(index);
    }

    private void drawPin(GuiGraphicsExtractor g, int edgeX, int cy, Pin pin, boolean leftSide, boolean showSelf, @Nullable String recorded, int mouseX, int mouseY) {
        int markerCenterX = leftSide ? edgeX + H_PAD : edgeX - H_PAD;
        int labelW = pin.label().isEmpty() ? 0 : font.width(pin.label());
        int recordedW = recorded == null ? 0 : font.width(recorded);

        // Hover band covers the marker + its label.
        int bandLeft, bandRight;
        if (leftSide) {
            bandLeft = edgeX;
            bandRight = edgeX + inputBandWidth(labelW, recordedW);
        } else {
            bandRight = edgeX;
            bandLeft = markerCenterX - PIN / 2 - PIN_LABEL_GAP - labelW - 2;
        }
        boolean pinHovered = mouseX >= bandLeft && mouseX < bandRight
            && mouseY >= cy - ROW_H / 2 && mouseY < cy + ROW_H / 2;

        int markerColor = pinHovered ? brighten(pin.color(), 0x33) : pin.color();
        int labelColor = pinHovered ? COLOR_LABEL_HOVER : COLOR_LABEL;

        if (pin.exec()) {
            drawExecTriangle(g, markerCenterX, cy, markerColor);
        } else {
            g.fill(markerCenterX - PIN / 2, cy - PIN / 2, markerCenterX - PIN / 2 + PIN, cy - PIN / 2 + PIN, markerColor);
        }

        int labelX = leftSide
            ? markerCenterX + PIN / 2 + PIN_LABEL_GAP
            : markerCenterX - PIN / 2 - PIN_LABEL_GAP - labelW;

        if (!pin.label().isEmpty()) {
            g.text(font, pin.label(), labelX, cy - font.lineHeight / 2, labelColor, false);
        }

        // Recorded value, written straight after the parameter name in its own colour so the name
        // still reads as the node's content.
        if (recorded != null) {
            g.text(font, recorded, labelX + labelW, cy - font.lineHeight / 2, COLOR_RECORDED_VALUE, false);
        }

        // "self" badge: a small pill after the (left-side) label marking the pin as
        // defaulting to the script owner when nothing is wired in.
        if (showSelf) {
            int badgeX = (pin.label().isEmpty() ? labelX : labelX + labelW + recordedW + PIN_LABEL_GAP) - 2;
            int badgeW = font.width(SELF_LABEL) + 6;
            int badgeTop = cy - font.lineHeight / 2 - 2;
            int badgeBottom = cy + font.lineHeight / 2 + 1;
            g.fill(badgeX, badgeTop, badgeX + badgeW, badgeBottom, COLOR_SELF_BADGE);
            g.outline(badgeX, badgeTop, badgeW, badgeBottom - badgeTop, COLOR_OUTLINE_IDLE);
            g.text(font, SELF_LABEL, badgeX + 3, cy - font.lineHeight / 2, COLOR_LABEL, false);
        }
    }

    /// The red banner hung beneath a body, UE's invalid-node marker. Purely decorative: it sits
    /// outside the widget's hit bounds, so it never intercepts clicks. Shared with the non-node
    /// editor surfaces that mark failures the same way.
    public static void drawErrorBanner(GuiGraphicsExtractor g, Font font, int x, int top, int w, String label) {
        int bannerH = font.lineHeight + 4;
        int bannerTop = top + 1;
        g.fill(x, bannerTop, x + w, bannerTop + bannerH, COLOR_ERROR_BANNER);
        g.outline(x, bannerTop, w, bannerH, COLOR_ERROR_OUTLINE);

        int labelW = font.width(label);
        g.text(font, label, x + (w - labelW) / 2, bannerTop + (bannerH - font.lineHeight) / 2, 0xFFFFFFFF, false);
    }

    /// A small plus glyph centered on (cx, cy) for the "Add pin" button.
    private void drawPlus(GuiGraphicsExtractor g, int cx, int cy, int color) {
        int arm = PIN / 2;
        g.fill(cx - arm, cy, cx + arm + 1, cy + 1, color);
        g.fill(cx, cy - arm, cx + 1, cy + arm + 1, color);
    }

    /// Right-pointing execution triangle, centered on (cx, cy).
    private void drawExecTriangle(GuiGraphicsExtractor g, int cx, int cy, int color) {
        int size = PIN / 2 + 1;
        int left = cx - size;
        for (int dy = -size; dy <= size; dy++) {
            int reach = size - Math.abs(dy);
            g.fill(left, cy + dy, left + reach + 1, cy + dy + 1, color);
        }
    }

    /// Adds `amount` to each RGB channel (clamped), preserving alpha.
    private static int brighten(int argb, int amount) {
        int a = argb >>> 24;
        int r = Math.min(255, ((argb >> 16) & 0xFF) + amount);
        int gg = Math.min(255, ((argb >> 8) & 0xFF) + amount);
        int b = Math.min(255, (argb & 0xFF) + amount);
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }

    // ***************************************************************
    // [AnchoredWidget] implementations
    // ***************************************************************
    private Unit x1;
    private Unit x2;
    private Unit y1;
    private Unit y2;
    private AnchoredWidget.HorizontalAnchorType horizontalAnchorType;
    private AnchoredWidget.VerticalAnchorType verticalAnchorType;

    @Override public Unit getX1() { return x1; }
    @Override public Unit getX2() { return x2; }
    @Override public Unit getY1() { return y1; }
    @Override public Unit getY2() { return y2; }
    @Override public void setX1(Unit unit) { x1 = unit; }
    @Override public void setX2(Unit unit) { x2 = unit; }
    @Override public void setY1(Unit unit) { y1 = unit; }
    @Override public void setY2(Unit unit) { y2 = unit; }
    @Override public HorizontalAnchorType getHorizontalAnchorType() { return horizontalAnchorType; }
    @Override public VerticalAnchorType getVerticalAnchorType() { return verticalAnchorType; }
    @Override public void setHorizontalAnchorType(HorizontalAnchorType horizontalAnchorType) { this.horizontalAnchorType = horizontalAnchorType; }
    @Override public void setVerticalAnchorType(VerticalAnchorType verticalAnchorType) { this.verticalAnchorType = verticalAnchorType; }
}
