package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.RerouteNodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/// A "reroute" knot (UE-style): a tiny pass-through node with a single input and a single
/// output of the same value type, used purely to bend a wire so the graph stays tidy. It is
/// created by double-clicking an existing wire (see
/// [ScriptNodeGraphWidget]); the wire is split so it runs source → knot → destination and
/// visually passes through the knot's dot.
///
/// It carries no title bar and no labels. Like UE, the knot distinguishes its **body** from
/// its **pin**: the central dot is the single pin (both halves of the wire meet there, and a
/// drag from it grabs a new wire), while the surrounding pill body is a move handle. UE tints
/// that body orange while selected; the editor's own convention applies here instead — a white
/// outline when the knot is hovered or selected.
///
/// A reroute is transparent to evaluation and emits nothing of its own: its value is its single
/// input.
public final class RerouteNodeWidget extends AbstractNodeWidget {
    /// Body pill dimensions (a short horizontal capsule, wider than tall like UE's knot).
    private static final int BODY_W = 26;
    private static final int BODY_H = 14;
    /// Side length of the drawn center pin dot.
    private static final int MARKER = 8;
    /// Half-extent of the square pin hit box around the center (a touch larger than the dot
    /// so the pin is easy to grab; the body outside it is the move handle).
    private static final int PIN_HIT = 5;

    private static final int COLOR_BODY = 0xFF2A2A2A;
    private static final int COLOR_OUTLINE_IDLE = 0xFF101010;
    private static final int COLOR_OUTLINE_ACTIVE = 0xFFFFFFFF;

    private final int markerColor;

    /// A knot of a recorded shape: `exec` selects an execution or a data pin, `type` the data value
    /// type (null for an exec or wildcard knot), and the pin colour follows from the two.
    public RerouteNodeWidget(Font font, boolean exec, @Nullable Class<?> type, int graphX, int graphY) {
        this(font, new Pin("", exec ? NodeColors.EXEC : NodeColors.forTypeOrWildcard(type), exec, type), graphX, graphY);
    }

    /// @param sample the wire's source output pin — the knot mirrors its exec flag, type and color.
    public RerouteNodeWidget(Font font, Pin sample, int graphX, int graphY) {
        super(font, Component.empty(), graphX, graphY);

        boolean exec = sample.exec();
        this.markerColor = exec ? NodeColors.EXEC : sample.color();

        addInput(new Pin("", markerColor, exec, sample.type()));
        addOutput(new Pin("", markerColor, exec, sample.type()));

        relayout();
    }

    @Override
    protected void relayout() {
        setWidth(BODY_W);
        setHeight(BODY_H);
    }

    private int centerX() {
        return getX() + getWidth() / 2;
    }

    private int centerY() {
        return getY() + getHeight() / 2;
    }

    // Both pins meet at the node center so the wire passes cleanly through the single dot.
    @Override public int inputPinCenterX(int index) { return centerX(); }
    @Override public int inputPinCenterY(int index) { return centerY(); }
    @Override public int outputPinCenterX(int index) { return centerX(); }
    @Override public int outputPinCenterY(int index) { return centerY(); }

    /// Whether `(gx, gy)` is over the central pin dot (vs. the surrounding move-handle body).
    private boolean overPin(int gx, int gy) {
        return Math.abs(gx - centerX()) <= PIN_HIT && Math.abs(gy - centerY()) <= PIN_HIT;
    }

    // The single pin is hit-testable only over the center dot, so a press there grabs a wire
    // while a press on the rest of the body falls through to the graph's node-move handling.
    @Override public int inputPinIndexAt(int gx, int gy) { return overPin(gx, gy) ? 0 : -1; }
    @Override public int outputPinIndexAt(int gx, int gy) { return overPin(gx, gy) ? 0 : -1; }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int cx = centerX(), cy = centerY();

        boolean active = isFocused() || containsGraphPoint(mouseX, mouseY);

        // Body pill (the move handle), then the white active outline (our style, in place of
        // UE's orange selection tint), then the center pin dot the wire threads through.
        g.fill(x, y, x + w, y + h, COLOR_BODY);
        g.outline(x, y, w, h, active ? COLOR_OUTLINE_ACTIVE : COLOR_OUTLINE_IDLE);

        int r = MARKER / 2;
        g.fill(cx - r, cy - r, cx + r, cy + r, markerColor);
    }

    // A reroute is transparent: its dangling input is not a "required" error, and any
    // wildcard pass-through wire is not an incompatibility — keep it out of validation.
    @Override
    public boolean reportsValidationErrors() {
        return false;
    }

    @Override
    public NodeData toData() {
        Pin pin = outputPin(0);
        return new RerouteNodeData(pin.exec(), Optional.ofNullable(scriptTypeOf(pin.type())));
    }
}