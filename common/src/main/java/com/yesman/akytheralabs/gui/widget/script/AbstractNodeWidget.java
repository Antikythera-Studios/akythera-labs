package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
/// This stage is visual only: pins are not wired.
public abstract class AbstractNodeWidget extends AbstractWidget implements AnchoredWidget {
    protected static final int TITLE_PAD = 5;
    protected static final int ROW_H = 19;
    protected static final int PIN = 7;
    protected static final int H_PAD = 8;
    protected static final int PIN_LABEL_GAP = 7;
    protected static final int MIN_WIDTH = 104;
    /// Minimum clear space between the input-side and output-side pin labels.
    protected static final int MIN_PIN_GAP = 20;

    private static final int COLOR_BODY = 0xFF3A3A3A;
    private static final int COLOR_BODY_DIVIDER = 0xFF1A1A1A;
    private static final int COLOR_OUTLINE_IDLE = 0xFF101010;
    private static final int COLOR_OUTLINE_ACTIVE = 0xFFFFFFFF;
    private static final int COLOR_LABEL = 0xFFCCCCCC;
    private static final int COLOR_LABEL_HOVER = 0xFFFFFFFF;

    private static final int COLOR_ERROR_OUTLINE = 0xFFD83A3A;
    private static final int COLOR_ERROR_BANNER = 0xCCB01010;
    private static final String ERROR_LABEL = "ERROR!";

    private static final String ADD_PIN_LABEL = "Add pin";

    /// A connection point. `exec` pins draw as a triangle (execution flow); data pins
    /// draw as a colored rectangle with `label`. `type` is the value class a data pin
    /// carries (null for execution pins and untyped data pins) — used for connection
    /// compatibility checks.
    public record Pin(String label, int color, boolean exec, @Nullable Class<?> type) {
        public Pin(String label, int color, boolean exec) {
            this(label, color, exec, null);
        }
    }

    private static final int COLOR_SELF_BADGE = 0xFF2A2A2A;
    private static final String SELF_LABEL = "self";

    protected final Font font;

    private final List<Pin> inputs = new ArrayList<>();
    private final List<Pin> outputs = new ArrayList<>();
    private int titleColor = 0xFF3A3A4A;

    /// Validation errors flagged on this node by [ScriptNodeGraphWidget#validate] (an unconnected
    /// required input, an incompatible wire). Non-empty drives the red outline + "ERROR!" banner
    /// (UE-style) and the host's hover tooltip. Empty = the node passed validation.
    private List<Component> errors = List.of();

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

    public int outputPinCount() { return outputs.size(); }
    public int inputPinCount() { return inputs.size(); }

    protected void setOutputLabel(int index, String label) {
        Pin p = outputs.get(index);
        outputs.set(index, new Pin(label, p.color(), p.exec(), p.type()));
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
    /// Stores both for the `self` badge and notifies [#onConnectionsChanged] so type-detecting
    /// subclasses can re-type pins.
    public void updateConnections(Map<Integer, Pin> connectedInputs, Map<Integer, Pin> connectedOutputs) {
        this.connectedInputs = connectedInputs;
        this.connectedOutputs = connectedOutputs;
        onConnectionsChanged();
    }

    /// Hook for nodes that reconfigure themselves from their connections (e.g.
    /// [ComparatorNodeWidget] / [OperatorNodeWidget] deciding their value type). Default: nothing.
    protected void onConnectionsChanged() {}

    /// The source output pins currently feeding this node's inputs, in input-pin order.
    protected Iterable<Pin> connectedInputSources() {
        return connectedInputs.values();
    }

    /// The destination input pins this node's outputs currently feed into, in output-pin order.
    protected Iterable<Pin> connectedOutputTargets() {
        return connectedOutputs.values();
    }

    protected boolean isInputConnected(int index) {
        return connectedInputs.containsKey(index);
    }

    /// ***************************************************************
    /// Validation
    /// ***************************************************************

    /// Replaces this node's validation errors (UE-style red "ERROR!" banner + outline + hover
    /// tooltip). Passing an empty list clears the flag. Driven by [ScriptNodeGraphWidget#validate].
    public void setErrors(@Nullable List<Component> errors) {
        this.errors = (errors == null || errors.isEmpty()) ? List.of() : List.copyOf(errors);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /// Whether validation may flag this node. Cosmetic / transparent nodes (a reroute knot) opt
    /// out so a dangling pass-through is not reported as a "required input" error. Default: true.
    public boolean reportsValidationErrors() {
        return true;
    }

    /// The error messages flagged on this node, for the host to show as a hover tooltip.
    public List<Component> errorLines() {
        return errors;
    }

    /// Whether input pin `index` is a *required* data input left unsatisfied — not an execution
    /// pin, not wired, and not defaulting to `self`. These are the pins validation flags as errors.
    /// Nodes whose value is inline (e.g. a literal, which exposes no input pin) have no such pin
    /// and so are never flagged by this. Relies on the host having pushed the current connection
    /// state first (see [ScriptNodeGraphWidget#validate]).
    public boolean isRequiredInputUnconnected(int index) {
        Pin pin = inputs.get(index);
        return !pin.exec() && !isInputConnected(index) && !shouldShowSelf(index, pin);
    }

    /// Whether this node lets the user grow/shrink its output pins at runtime
    /// (e.g. a sequence's `Then N` outputs). Enables the "Add pin" button and the
    /// right-click "Remove Pin" gesture.
    public boolean supportsDynamicOutputs() { return false; }

    /// Whether the user may delete this node from the graph. The flow's entry-point
    /// node (e.g. "Update") overrides this to false so it can never be removed.
    public boolean isDeletable() { return true; }

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
    /// header drag, so a node can claim body clicks for its own controls. Default: no.
    public boolean onBodyClick(int graphX, int graphY, double screenX, double screenY) {
        return false;
    }

    /// Whether this node is currently hosting an inline text editor (e.g. a literal node
    /// being typed into). While true the host graph routes keyboard input here via
    /// [#inlineKeyPressed] / [#inlineCharTyped] and suppresses its own shortcuts (so typing
    /// "d" edits the value instead of deleting the node). Default: never editing.
    public boolean isInlineEditing() {
        return false;
    }

    /// Routes a key press into the node's inline editor; only called while [#isInlineEditing].
    /// Default: ignored.
    public boolean inlineKeyPressed(@NonNull KeyEvent event) {
        return false;
    }

    /// Routes a typed character into the node's inline editor; only called while
    /// [#isInlineEditing]. Default: ignored.
    public boolean inlineCharTyped(@NonNull CharacterEvent event) {
        return false;
    }

    /// Ends (committing) any inline edit, e.g. when the user clicks away. Default: nothing.
    public void endInlineEdit() {}

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
    /// Width fits the widest input label plus the widest output label (execution pins
    /// included — their labels like "True"/"Then 0" take space too), keeping at least
    /// [#MIN_PIN_GAP] of clear space between the two columns so they never overlap.
    protected void relayout() {
        int titleW = font.width(getMessage()) + H_PAD * 2;

        int selfReserve = font.width(SELF_LABEL) + 6 + PIN_LABEL_GAP;
        int maxIn = inputs.stream().mapToInt(p -> font.width(p.label()) + (isSelfCapable(p) ? selfReserve : 0)).max().orElse(0);
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
    /// type is assignable to (or numerically coercible into) the expected type.
    public static boolean pinsCompatible(Pin output, Pin input) {
        if (output.exec() || input.exec()) return output.exec() && input.exec();
        return typeCompatible(output.type(), input.type());
    }

    /// Whether a value of `provided` can drive a slot expecting `expected`, with the
    /// same assignability / numeric-coercion rules as [#pinsCompatible]. A null on
    /// either side means "untyped / unknown" and is accepted. Exposed so the host
    /// screen can pre-filter value-provider suggestions by the dragged pin's type.
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
            if (i < inputs.size()) drawPin(g, x, cy, inputs.get(i), true, shouldShowSelf(i, inputs.get(i)), mouseX, mouseY);
            if (i < outputs.size()) drawPin(g, x + w, cy, outputs.get(i), false, false, mouseX, mouseY);
        }

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

        // UE-style "ERROR!" banner hung beneath the body for a node that failed validation.
        if (hasErrors()) drawErrorBanner(g, x, y + h, w);

        // Subclass overlay (e.g. a literal node's inline edit box), drawn on top of the body.
        extractBodyOverlay(g, mouseX, mouseY, a);
    }

    /// Whether a data pin's type the script owner satisfies — i.e. it can default to `self`.
    /// Object-typed slots are excluded so only the actual owner "Target" pins (not every
    /// opaque slot) auto-default to self.
    private boolean isSelfCapable(Pin pin) {
        if (pin.exec() || scriptOwnerType == null || pin.type() == null || pin.type() == Object.class) return false;
        return pin.type().isAssignableFrom(boxed(scriptOwnerType));
    }

    /// Whether input pin `index` should render the `self` badge: a self-capable pin left
    /// unconnected.
    private boolean shouldShowSelf(int index, Pin pin) {
        return isSelfCapable(pin) && !isInputConnected(index);
    }

    private void drawPin(GuiGraphicsExtractor g, int edgeX, int cy, Pin pin, boolean leftSide, boolean showSelf, int mouseX, int mouseY) {
        int markerCenterX = leftSide ? edgeX + H_PAD : edgeX - H_PAD;
        int labelW = pin.label().isEmpty() ? 0 : font.width(pin.label());

        // Hover band covers the marker + its label.
        int bandLeft, bandRight;
        if (leftSide) {
            bandLeft = edgeX;
            bandRight = markerCenterX + PIN / 2 + PIN_LABEL_GAP + labelW + 2;
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

        // "self" badge: a small pill after the (left-side) label marking the pin as
        // defaulting to the script owner when nothing is wired in.
        if (showSelf) {
            int badgeX = (pin.label().isEmpty() ? labelX : labelX + labelW + PIN_LABEL_GAP) - 2;
            int badgeW = font.width(SELF_LABEL) + 6;
            int badgeTop = cy - font.lineHeight / 2 - 2;
            int badgeBottom = cy + font.lineHeight / 2 + 1;
            g.fill(badgeX, badgeTop, badgeX + badgeW, badgeBottom, COLOR_SELF_BADGE);
            g.outline(badgeX, badgeTop, badgeW, badgeBottom - badgeTop, COLOR_OUTLINE_IDLE);
            g.text(font, SELF_LABEL, badgeX + 3, cy - font.lineHeight / 2, COLOR_LABEL, false);
        }
    }

    /// The red "ERROR!" banner hung beneath the node body (UE's invalid-node marker). Purely
    /// decorative — it sits outside the node's hit bounds, so it never intercepts clicks.
    private void drawErrorBanner(GuiGraphicsExtractor g, int x, int top, int w) {
        int bannerH = font.lineHeight + 4;
        int bannerTop = top + 1;
        g.fill(x, bannerTop, x + w, bannerTop + bannerH, COLOR_ERROR_BANNER);
        g.outline(x, bannerTop, w, bannerH, COLOR_ERROR_OUTLINE);

        int labelW = font.width(ERROR_LABEL);
        g.text(font, ERROR_LABEL, x + (w - labelW) / 2, bannerTop + (bannerH - font.lineHeight) / 2, 0xFFFFFFFF, false);
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

    /// ***************************************************************
    /// [AnchoredWidget] implementations                              *
    /// ***************************************************************
    private Unit x1;
    private Unit x2;
    private Unit y1;
    private Unit y2;
    private final AnchoredWidget.HorizontalAnchorType horizontalAnchorType;
    private final AnchoredWidget.VerticalAnchorType verticalAnchorType;

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
}
