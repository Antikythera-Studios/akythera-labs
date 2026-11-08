package com.yesman.akytheralabs.gui.widget.behavior;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yesman.akythera.api.world.behavior.AbortMode;
import com.yesman.akythera.client.api.gui.ContextMenuContainer;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.akythera.behavior.BehaviorNodeCatalog;
import com.yesman.akytheralabs.akythera.behavior.BehaviorTreeData;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.gui.widget.CanvasGrid;
import com.yesman.akytheralabs.gui.widget.script.CutSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/// The canvas of one behavior tree: the ROOT entry box and the placed nodes. A node box wraps the conditions stacked
/// on it above its title and the subtasks below it, between an input band on top and, on a flow control node, an
/// output band at the bottom. An arrow runs from the output pin of ROOT or a flow control node down to the input pin
/// of each child. ROOT runs its nodes left to right by priority, like a selector.
///
/// A left-drag from an output band draws an arrow; releasing it over a node attaches that node as the last child, or
/// as a node ROOT runs, and every input pin that can take it is highlighted meanwhile. Alt-clicking an input band
/// detaches the node from its parent; Alt-clicking an output band detaches every child. Children run left to right,
/// so they are kept sorted by their position. The selected nodes show their execution order on their left, and every
/// node does while the "Show node orders" box in the top-left corner is checked.
///
/// A left-drag on a node moves the selection; on the empty canvas it draws a selection box, adding to the selection
/// while Shift or Ctrl is held. Shift-click adds a node to the selection and Ctrl-click toggles it. Right-click opens
/// the add menus; right-drag pans; [EditorKeyMappings#DELETE] removes the selected nodes or the selected condition or
/// subtask. ROOT is never selected or deleted.
///
/// The widget owns the topology and round-trips it through [BehaviorTreeData]. What a selection holds is edited in
/// the host's Details panel.
public final class BehaviorTreeGraphWidget extends AbstractContainerWidget implements AnchoredWidget, CutSource {
    private static final int TITLE_H = 22;
    private static final int STRIP_H = 12;
    /// Height of the input band on top of a node, and of the output band below a flow control node or ROOT.
    private static final int BAND_H = 8;
    /// Gap between a node's frame and what it wraps.
    private static final int INSET = 4;
    private static final int PIN_W = 16;
    private static final int PIN_H = 4;
    private static final int NODE_PAD_X = 10;
    private static final int NODE_MIN_W = 80;
    private static final int ORDER_BADGE_W = 14;
    /// Side of the "Show node orders" check box, and its gap from the canvas corner, in screen pixels.
    private static final int CHECK_BOX = 9;
    private static final int CHECK_BOX_GAP = 6;
    private static final int DRAG_SNAP = 5;
    private static final int PASTE_STEP = 20;
    private static final float MIN_ZOOM = 0.30F;
    private static final float MAX_ZOOM = 2.50F;
    private static final float ZOOM_STEP = 1.10F;

    private static final int COLOR_FLOW_BODY = 0xFF4A4A4A;
    private static final int COLOR_TASK_BODY = 0xFF3F5E78;
    private static final int COLOR_BAND = 0xFF2A2A2A;
    private static final int COLOR_CONDITION = 0xFF6B5A2A;
    private static final int COLOR_SUBTASK = 0xFF5A3F70;
    private static final int COLOR_OUTLINE = 0xFF101010;
    private static final int COLOR_SELECTED = 0xFFC88A2A;
    private static final int COLOR_ENTRY_BODY = 0xFF2F5A2F;
    private static final int COLOR_ACTIVE = 0xFF3ACF6A;
    private static final int COLOR_EDGE = 0xFFBFBFBF;
    private static final int COLOR_PIN = 0xFF808080;
    private static final int COLOR_PIN_TARGET = 0xFF4F8F5F; // an input pin the wire being drawn can attach to
    private static final int COLOR_PIN_HOVERED = 0xFF7CFF9A; // the input pin under the cursor while drawing a wire
    private static final int COLOR_LABEL = 0xFFE0E0E0;
    private static final int COLOR_SUMMARY = 0xFFA0A0A0;
    private static final int COLOR_CUT = 0x90101010;
    private static final int COLOR_MARQUEE_FILL = 0x2266AAFF;
    private static final int COLOR_MARQUEE_BORDER = 0xFF66AAFF;

    private final Font font;

    private final List<Node> nodes = new ArrayList<>();

    /// Whether every node shows its execution order, not only the selected ones. Shared by every canvas.
    private static boolean showNodeOrders;

    /// The nodes the ROOT entry runs, left to right.
    private final List<Node> roots = new ArrayList<>();

    /// The canvas position of the ROOT entry box.
    private int entryX;
    private int entryY;

    /// The selected nodes, in the order they were selected.
    private final Set<Node> selectedNodes = new LinkedHashSet<>();

    /// The selected condition or subtask, which excludes [#selectedNodes].
    private Selection.@Nullable Strip selectedStrip;

    /// The nodes a cut waits to move, see [CutSource].
    private final Set<Node> cutNodes = new HashSet<>();

    /// Fired when the selection changes, with the new one or null.
    private @Nullable Consumer<@Nullable Selection> onSelectionChanged;

    /// Fired when a node is double-clicked.
    private @Nullable Consumer<Node> onNodeActivated;

    /// The ids of the nodes a debugged brain runs, ringed while set.
    private Set<Integer> activeNodeIds = Set.of();

    private float panX = 40.0F;
    private float panY = 40.0F;
    private float zoom = 1.0F;

    private boolean rightDown;
    private double rightPressX;
    private double rightPressY;

    /// Where the next paste puts the top-left corner of the pasted nodes, in graph space.
    private int pasteX;
    private int pasteY;

    /// The node whose drag moves the selection, and its offset from the cursor.
    private @Nullable Node dragging;
    private boolean draggingEntry;
    private int grabDx;
    private int grabDy;
    private final Map<Node, int[]> dragStarts = new HashMap<>();
    private @Nullable BehaviorTreeData dragBefore;

    private boolean wiring;
    /// The flow control node a wire is drawn from, or null for ROOT.
    private @Nullable Node wireFrom;
    private double wireEndX;
    private double wireEndY;

    private boolean marqueeing;
    private final Set<Node> marqueeBase = new LinkedHashSet<>();
    private int marqueeStartX;
    private int marqueeStartY;
    private int marqueeEndX;
    private int marqueeEndY;

    public BehaviorTreeGraphWidget(
        final Font font,
        final BehaviorTreeData data,
        final Unit x1, final Unit x2, final Unit y1, final Unit y2,
        final AnchoredWidget.HorizontalAnchorType horizontalAnchorType,
        final AnchoredWidget.VerticalAnchorType verticalAnchorType
    ) {
        super(0, 0, 0, 0, Component.empty(), AbstractScrollArea.defaultSettings(0));

        this.font = font;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchorType;
        this.verticalAnchorType = verticalAnchorType;

        importData(data);
    }

    public void setOnSelectionChanged(final @Nullable Consumer<@Nullable Selection> handler) {
        this.onSelectionChanged = handler;
    }

    public void setOnNodeActivated(final @Nullable Consumer<Node> handler) {
        this.onNodeActivated = handler;
    }

    public void setActiveNodeIds(final Set<Integer> activeNodeIds) {
        this.activeNodeIds = activeNodeIds;
    }

    // ***************************************************************
    // Persistence
    // ***************************************************************

    private void importData(final BehaviorTreeData data) {
        nodes.clear();
        selectedNodes.clear();
        selectedStrip = null;
        cutNodes.clear();

        for (BehaviorTreeData.NodeData row : data.nodes()) nodes.add(nodeOf(row, row.id(), 0, 0));

        roots.clear();
        data.roots().stream().map(this::nodeOf).filter(Objects::nonNull).forEach(roots::add);
        entryX = data.entryX();
        entryY = data.entryY();
    }

    /// A node built from `row` under `id`, moved by (`dx`, `dy`). Its child ids are the row's.
    private static Node nodeOf(final BehaviorTreeData.NodeData row, final int id, final int dx, final int dy) {
        Node node = new Node(id, row.x() + dx, row.y() + dy, row.node().deepCopy());

        node.childIds.addAll(row.children());
        row.conditions().forEach(attachment -> node.conditions.add(new Attachment(attachment.node().deepCopy(), attachment.interval())));
        row.subtasks().forEach(attachment -> node.subtasks.add(new Attachment(attachment.node().deepCopy(), attachment.interval())));
        node.abortMode = row.abortMode();

        return node;
    }

    private static BehaviorTreeData.NodeData rowOf(final Node node, final List<Integer> children) {
        return new BehaviorTreeData.NodeData(
            node.id, node.x, node.y, node.json, List.copyOf(children),
            node.conditions.stream().map(attachment -> new BehaviorTreeData.Attachment(attachment.json, attachment.interval)).toList(),
            node.subtasks.stream().map(attachment -> new BehaviorTreeData.Attachment(attachment.json, attachment.interval)).toList(),
            node.abortMode
        );
    }

    public BehaviorTreeData exportData() {
        return new BehaviorTreeData(
            nodes.stream().map(node -> rowOf(node, node.childIds)).toList(),
            roots.stream().map(node -> node.id).toList(), entryX, entryY
        );
    }

    /// Replaces the whole canvas with `data`, as one undoable unit.
    public void replaceData(final BehaviorTreeData data) {
        BehaviorTreeData before = exportData();

        importData(data);
        pushHistory(before, exportData());
        notifySelection();
    }

    /// Runs `change` and records it as one undoable unit.
    public void recordChange(final Runnable change) {
        BehaviorTreeData before = exportData();

        change.run();
        pushHistory(before, exportData());
    }

    private void pushHistory(final BehaviorTreeData before, final BehaviorTreeData after) {
        if (before.equals(after)) return;

        HistoryManager.getInstance().pushHistory(this, new WorkHistory() {
            @Override
            public void undo() {
                importData(before);
                notifySelection();
            }

            @Override
            public void redo() {
                importData(after);
                notifySelection();
            }
        });
    }

    // ***************************************************************
    // Copy / paste
    // ***************************************************************

    /// The selected nodes, each keeping only the children that are selected too, or null when none is selected.
    public @Nullable List<BehaviorTreeData.NodeData> copySelection() {
        if (selectedNodes.isEmpty()) return null;

        Set<Integer> ids = new HashSet<>();

        selectedNodes.forEach(node -> ids.add(node.id));

        return selectedNodes.stream().map(node -> rowOf(node, node.childIds.stream().filter(ids::contains).toList())).toList();
    }

    /// Marks the nodes [#copySelection] takes as cut, and answers what marks the same nodes again.
    public Runnable markSelectionCut() {
        List<Node> cut = List.copyOf(selectedNodes);

        Runnable mark = () -> {
            cutNodes.clear();
            cutNodes.addAll(cut);
        };

        mark.run();

        return mark;
    }

    /// Places copied nodes on this canvas as one undoable unit under new ids, the top-left corner of them at the last
    /// press on the canvas. The placed nodes become the selection.
    public void pasteNodes(final List<BehaviorTreeData.NodeData> pasted) {
        int dx = pasteX - pasted.stream().mapToInt(BehaviorTreeData.NodeData::x).min().orElse(pasteX);
        int dy = pasteY - pasted.stream().mapToInt(BehaviorTreeData.NodeData::y).min().orElse(pasteY);
        Map<Integer, Integer> newIds = new HashMap<>();
        List<Node> placed = new ArrayList<>();

        recordChange(() -> {
            int next = nextId();

            for (BehaviorTreeData.NodeData row : pasted) newIds.put(row.id(), next++);

            for (BehaviorTreeData.NodeData row : pasted) {
                Node node = nodeOf(row, newIds.get(row.id()), dx, dy);

                node.childIds.replaceAll(newIds::get);
                nodes.add(node);
                placed.add(node);
            }
        });

        pasteX += PASTE_STEP;
        pasteY += PASTE_STEP;

        selectedStrip = null;
        selectedNodes.clear();
        selectedNodes.addAll(placed);
        notifySelection();
    }

    // ***************************************************************
    // [CutSource]
    // ***************************************************************

    @Override
    public void clearCut() {
        cutNodes.clear();
    }

    @Override
    public void deleteCut() {
        recordChange(() -> cutNodes.stream().filter(nodes::contains).toList().forEach(this::deleteNode));
        selectedNodes.removeAll(cutNodes);
        notifySelection();
    }

    // ***************************************************************
    // Model
    // ***************************************************************

    private @Nullable Node nodeOf(final int id) {
        for (Node node : nodes) if (node.id == id) return node;
        return null;
    }

    private @Nullable Node parentOf(final Node child) {
        for (Node node : nodes) if (node.childIds.contains(child.id)) return node;
        return null;
    }

    private int nextId() {
        return nodes.stream().mapToInt(node -> node.id + 1).max().orElse(0);
    }

    private Node addNode(final JsonElement json, final int gx, final int gy) {
        Node node = new Node(nextId(), snap(gx), snap(gy), json.deepCopy());

        nodes.add(node);

        return node;
    }

    private void deleteNode(final Node node) {
        nodes.remove(node);
        nodes.forEach(other -> other.childIds.remove((Integer)node.id));
        roots.remove(node);
    }

    /// Whether a wire from `parent`, or from ROOT when null, may attach `child`: a node is never its own descendant.
    private boolean canAttach(final @Nullable Node parent, final Node child) {
        if (parent == null) return true;
        if (parent == child || !parent.isFlowControl()) return false;

        for (Node ancestor = parent; ancestor != null; ancestor = parentOf(ancestor)) {
            if (ancestor == child) return false;
        }

        return true;
    }

    /// Makes `child` the last child of `parent`, or the last node ROOT runs when `parent` is null, detached from where
    /// it ran before.
    private void attach(final @Nullable Node parent, final Node child) {
        if (!canAttach(parent, child)) return;

        detach(child);

        if (parent == null) {
            roots.add(child);
            sortRoots();
        } else {
            parent.childIds.add(child.id);
            sortChildren(parent);
        }
    }

    /// Detaches `node` from its parent, or from ROOT.
    private void detach(final Node node) {
        Node parent = parentOf(node);

        if (parent != null) parent.childIds.remove((Integer)node.id);

        roots.remove(node);
    }

    private void sortRoots() {
        roots.sort(Comparator.comparingInt(node -> node.x));
    }

    private void sortChildren(final Node parent) {
        parent.childIds.sort(Comparator.comparingInt(id -> {
            Node child = nodeOf(id);
            return child == null ? 0 : child.x;
        }));
    }

    /// The execution order of each node ROOT reaches, from 1, depth-first with ROOT's nodes and children left to right.
    private Map<Node, Integer> executionOrder() {
        Map<Node, Integer> order = new LinkedHashMap<>();

        for (Node root : roots) {
            if (!order.containsKey(root)) number(root, order);
        }

        return order;
    }

    private void number(final Node node, final Map<Node, Integer> order) {
        order.put(node, order.size() + 1);

        for (int childId : node.childIds) {
            Node child = nodeOf(childId);

            if (child != null && !order.containsKey(child)) number(child, order);
        }
    }

    // ***************************************************************
    // Geometry
    // ***************************************************************

    private int nodeWidth(final Node node) {
        int width = Math.max(font.width(node.label()), font.width(node.summary())) + NODE_PAD_X * 2;

        for (Attachment attachment : node.conditions) width = Math.max(width, font.width(Node.labelOf(attachment.json)) + INSET * 2 + 8);
        for (Attachment attachment : node.subtasks) width = Math.max(width, font.width(Node.labelOf(attachment.json)) + INSET * 2 + 8);

        return Math.max(NODE_MIN_W, width);
    }

    private static int nodeHeight(final Node node) {
        return BAND_H + STRIP_H * node.conditions.size() + TITLE_H + STRIP_H * node.subtasks.size() + (node.isFlowControl() ? BAND_H : INSET);
    }

    private static int titleTop(final Node node) {
        return node.y + BAND_H + STRIP_H * node.conditions.size();
    }

    private static int conditionTop(final Node node, final int index) {
        return node.y + BAND_H + STRIP_H * index;
    }

    private static int subtaskTop(final Node node, final int index) {
        return titleTop(node) + TITLE_H + STRIP_H * index;
    }

    private int screenToGraphX(final double sx) {
        return (int)Math.floor((sx - getX() - panX) / zoom);
    }

    private int screenToGraphY(final double sy) {
        return (int)Math.floor((sy - getY() - panY) / zoom);
    }

    /// Whether a screen point is over the "Show node orders" check box or its label.
    private boolean overOrderCheckBox(final double sx, final double sy) {
        int left = getX() + CHECK_BOX_GAP;
        int top = getY() + CHECK_BOX_GAP;

        return sx >= left && sx < left + CHECK_BOX + 4 + font.width("Show node orders") && sy >= top && sy < top + CHECK_BOX;
    }

    private boolean inNode(final Node node, final int gx, final int gy) {
        return gx >= node.x && gx < node.x + nodeWidth(node) && gy >= node.y && gy < node.y + nodeHeight(node);
    }

    private boolean inEntry(final int gx, final int gy) {
        return gx >= entryX && gx < entryX + NODE_MIN_W && gy >= entryY && gy < entryY + TITLE_H + BAND_H;
    }

    private @Nullable Node nodeAt(final int gx, final int gy) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            if (inNode(nodes.get(i), gx, gy)) return nodes.get(i);
        }

        return null;
    }

    /// The condition or subtask strip of `node` under a graph point, or null.
    private Selection.@Nullable Strip stripAt(final Node node, final int gx, final int gy) {
        if (gx < node.x + INSET || gx >= node.x + nodeWidth(node) - INSET) return null;

        for (int i = 0; i < node.conditions.size(); i++) {
            int top = conditionTop(node, i);

            if (gy >= top && gy < top + STRIP_H) return new Selection.Strip(node, true, i);
        }

        for (int i = 0; i < node.subtasks.size(); i++) {
            int top = subtaskTop(node, i);

            if (gy >= top && gy < top + STRIP_H) return new Selection.Strip(node, false, i);
        }

        return null;
    }

    private static int snap(final int graphCoord) {
        return Math.round((float)graphCoord / DRAG_SNAP) * DRAG_SNAP;
    }

    // ***************************************************************
    // Rendering
    // ***************************************************************

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        CanvasGrid.extract(g, getX(), getY(), getRight(), getBottom(), getX() + panX, getY() + panY, zoom);
        g.enableScissor(getX(), getY(), getRight(), getBottom());
        g.pose().pushMatrix();
        g.pose().translate(getX() + panX, getY() + panY);
        g.pose().scale(zoom, zoom);

        for (Node parent : nodes) {
            if (!parent.isFlowControl()) continue;

            for (int childId : parent.childIds) {
                Node child = nodeOf(childId);

                if (child != null) drawEdge(g, parent.x + nodeWidth(parent) / 2, parent.y + nodeHeight(parent) - BAND_H / 2, child);
            }
        }

        for (Node root : roots) drawEdge(g, entryX + NODE_MIN_W / 2, entryY + TITLE_H + BAND_H / 2, root);

        Node hovered = wiring ? nodeAt(screenToGraphX(wireEndX), screenToGraphY(wireEndY)) : null;
        Map<Node, Integer> order = selectedNodes.isEmpty() && !showNodeOrders ? Map.of() : executionOrder();

        for (Node node : nodes) drawNode(g, node, hovered, order.get(node));

        drawEntry(g);

        if (wiring) {
            int sx = wireFrom == null ? entryX + NODE_MIN_W / 2 : wireFrom.x + nodeWidth(wireFrom) / 2;
            int sy = wireFrom == null ? entryY + TITLE_H + BAND_H / 2 : wireFrom.y + nodeHeight(wireFrom) - BAND_H / 2;

            drawElbow(g, sx, sy, screenToGraphX(wireEndX), screenToGraphY(wireEndY));
        }

        if (marqueeing) drawMarquee(g);

        g.pose().popMatrix();

        int boxLeft = getX() + CHECK_BOX_GAP;
        int boxTop = getY() + CHECK_BOX_GAP;

        g.fill(boxLeft, boxTop, boxLeft + CHECK_BOX, boxTop + CHECK_BOX, COLOR_BAND);
        g.outline(boxLeft, boxTop, CHECK_BOX, CHECK_BOX, COLOR_PIN);

        if (showNodeOrders) g.fill(boxLeft + 2, boxTop + 2, boxLeft + CHECK_BOX - 2, boxTop + CHECK_BOX - 2, COLOR_SELECTED);

        g.text(font, Component.literal("Show node orders"), boxLeft + CHECK_BOX + 4, boxTop + 1, COLOR_SUMMARY, false);
        g.disableScissor();
    }

    /// An arrow from the graph point (`px`, `py`) down to the input pin of `child`.
    private void drawEdge(final GuiGraphicsExtractor g, final int px, final int py, final Node child) {
        int cx = child.x + nodeWidth(child) / 2;
        int cy = child.y + 2;

        drawElbow(g, px, py, cx, cy - 3);

        for (int i = 0; i < 3; i++) g.fill(cx - 3 + i, cy - 3 + i, cx + 4 - i, cy - 2 + i, COLOR_EDGE);
    }

    /// A line down from (`x0`, `y0`), across at the middle height, and down to (`x1`, `y1`).
    private static void drawElbow(final GuiGraphicsExtractor g, final int x0, final int y0, final int x1, final int y1) {
        int midY = (y0 + y1) / 2;

        g.fill(x0, Math.min(y0, midY), x0 + 1, Math.max(y0, midY) + 1, COLOR_EDGE);
        g.fill(Math.min(x0, x1), midY, Math.max(x0, x1) + 1, midY + 1, COLOR_EDGE);
        g.fill(x1, Math.min(midY, y1), x1 + 1, Math.max(midY, y1) + 1, COLOR_EDGE);
    }

    private void drawEntry(final GuiGraphicsExtractor g) {
        int h = TITLE_H + BAND_H;

        g.fill(entryX, entryY, entryX + NODE_MIN_W, entryY + h, COLOR_ENTRY_BODY);
        g.fill(entryX + 1, entryY + TITLE_H, entryX + NODE_MIN_W - 1, entryY + h - 1, COLOR_BAND);
        g.fill(entryX + (NODE_MIN_W - PIN_W) / 2, entryY + TITLE_H + (BAND_H - PIN_H) / 2,
            entryX + (NODE_MIN_W + PIN_W) / 2, entryY + TITLE_H + (BAND_H + PIN_H) / 2, roots.isEmpty() ? COLOR_PIN : COLOR_EDGE);
        g.outline(entryX, entryY, NODE_MIN_W, h, COLOR_OUTLINE);
        g.centeredText(font, Component.literal("ROOT"), entryX + NODE_MIN_W / 2, entryY + (TITLE_H - font.lineHeight) / 2 + 1, COLOR_LABEL);
    }

    /// Draws `node` with its stacked conditions and subtasks, its pins, and its execution `order` on its left when it
    /// is selected or every order shows. `hovered` is the node under a wire being drawn.
    private void drawNode(final GuiGraphicsExtractor g, final Node node, final @Nullable Node hovered, final @Nullable Integer order) {
        int w = nodeWidth(node);
        int h = nodeHeight(node);
        boolean flow = node.isFlowControl();
        boolean selected = selectedNodes.contains(node);

        g.fill(node.x, node.y, node.x + w, node.y + h, flow ? COLOR_FLOW_BODY : COLOR_TASK_BODY);

        // Input band, its pin lit while a wire being drawn can attach this node
        int pinColor = COLOR_PIN;

        if (wiring && canAttach(wireFrom, node)) pinColor = node == hovered ? COLOR_PIN_HOVERED : COLOR_PIN_TARGET;
        else if (roots.contains(node) || parentOf(node) != null) pinColor = COLOR_EDGE;

        g.fill(node.x + 1, node.y + 1, node.x + w - 1, node.y + BAND_H, COLOR_BAND);
        g.fill(node.x + (w - PIN_W) / 2, node.y + (BAND_H - PIN_H) / 2, node.x + (w + PIN_W) / 2, node.y + (BAND_H + PIN_H) / 2, pinColor);

        if (flow) {
            int top = node.y + h - BAND_H;

            g.fill(node.x + 1, top, node.x + w - 1, node.y + h - 1, COLOR_BAND);
            g.fill(node.x + (w - PIN_W) / 2, top + (BAND_H - PIN_H) / 2, node.x + (w + PIN_W) / 2, top + (BAND_H + PIN_H) / 2,
                node.childIds.isEmpty() ? COLOR_PIN : COLOR_EDGE);
        }

        for (int i = 0; i < node.conditions.size(); i++) {
            drawStrip(g, node, conditionTop(node, i), w, COLOR_CONDITION, node.conditions.get(i), new Selection.Strip(node, true, i));
        }

        int titleTop = titleTop(node);

        g.text(font, Component.literal(node.label()), node.x + NODE_PAD_X, titleTop + 3, COLOR_LABEL, false);
        g.text(font, Component.literal(node.summary()), node.x + NODE_PAD_X, titleTop + 13, COLOR_SUMMARY, false);

        for (int i = 0; i < node.subtasks.size(); i++) {
            drawStrip(g, node, subtaskTop(node, i), w, COLOR_SUBTASK, node.subtasks.get(i), new Selection.Strip(node, false, i));
        }

        g.outline(node.x, node.y, w, h, selected ? COLOR_SELECTED : COLOR_OUTLINE);

        if (activeNodeIds.contains(node.id)) g.outline(node.x - 2, node.y - 2, w + 4, h + 4, COLOR_ACTIVE);
        if (cutNodes.contains(node)) g.fill(node.x, node.y, node.x + w, node.y + h, COLOR_CUT);

        if ((selected || showNodeOrders) && order != null) {
            int left = node.x - ORDER_BADGE_W - 3;

            g.fill(left, node.y, left + ORDER_BADGE_W, node.y + 12, COLOR_SELECTED);
            g.centeredText(font, Component.literal(String.valueOf(order)), left + ORDER_BADGE_W / 2, node.y + 2, COLOR_LABEL);
        }
    }

    private void drawStrip(
        final GuiGraphicsExtractor g, final Node node, final int top, final int w, final int color, final Attachment attachment,
        final Selection.Strip strip
    ) {
        g.fill(node.x + INSET, top, node.x + w - INSET, top + STRIP_H - 1, color);

        if (strip.equals(selectedStrip)) g.outline(node.x + INSET, top, w - INSET * 2, STRIP_H - 1, COLOR_SELECTED);

        g.text(font, Component.literal(Node.labelOf(attachment.json)), node.x + INSET + 3, top + 2, COLOR_LABEL, false);
    }

    private void drawMarquee(final GuiGraphicsExtractor g) {
        int x0 = Math.min(marqueeStartX, marqueeEndX);
        int y0 = Math.min(marqueeStartY, marqueeEndY);
        int x1 = Math.max(marqueeStartX, marqueeEndX);
        int y1 = Math.max(marqueeStartY, marqueeEndY);

        g.fill(x0, y0, x1, y1, COLOR_MARQUEE_FILL);
        g.outline(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0), COLOR_MARQUEE_BORDER);
    }

    // ***************************************************************
    // Input
    // ***************************************************************

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (!isMouseOver(event.x(), event.y())) return false;

        int gx = screenToGraphX(event.x());
        int gy = screenToGraphY(event.y());

        if (event.button() == 1) {
            rightDown = true;
            rightPressX = event.x();
            rightPressY = event.y();
            return true;
        }

        if (event.button() != 0) return true;

        if (overOrderCheckBox(event.x(), event.y())) {
            showNodeOrders = !showNodeOrders;
            return true;
        }

        pasteX = snap(gx);
        pasteY = snap(gy);

        boolean shift = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean control = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean alt = (event.modifiers() & GLFW.GLFW_MOD_ALT) != 0;

        if (inEntry(gx, gy)) {
            if (gy >= entryY + TITLE_H && alt) {
                recordChange(roots::clear);
            } else if (gy >= entryY + TITLE_H) {
                beginWire(null, event);
            } else {
                draggingEntry = true;
                grabDx = gx - entryX;
                grabDy = gy - entryY;
                dragBefore = exportData();
            }

            return true;
        }

        Node hit = nodeAt(gx, gy);

        if (hit == null) {
            beginMarquee(gx, gy, shift || control);
            return true;
        }

        if (gy < hit.y + BAND_H && alt) {
            recordChange(() -> detach(hit));
            return true;
        }

        if (hit.isFlowControl() && gy >= hit.y + nodeHeight(hit) - BAND_H) {
            if (alt) {
                recordChange(hit.childIds::clear);
            } else {
                beginWire(hit, event);
            }

            return true;
        }

        Selection.Strip strip = stripAt(hit, gx, gy);

        if (strip != null) {
            selectedNodes.clear();
            selectedStrip = strip;
            notifySelection();
            return true;
        }

        if (doubleClick) {
            selectOnly(hit);
            if (onNodeActivated != null) onNodeActivated.accept(hit);
            return true;
        }

        if (control) {
            if (!selectedNodes.remove(hit)) selectedNodes.add(hit);

            selectedStrip = null;
            notifySelection();
        } else if (shift) {
            selectedNodes.add(hit);
            selectedStrip = null;
            notifySelection();
        } else if (!selectedNodes.contains(hit)) {
            selectOnly(hit);
        }

        if (selectedNodes.contains(hit)) beginDrag(hit, gx, gy);

        return true;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (rightDown) {
            panX += (float)dx;
            panY += (float)dy;
            return true;
        }

        int gx = screenToGraphX(event.x());
        int gy = screenToGraphY(event.y());

        if (dragging != null) {
            int[] anchor = dragStarts.get(dragging);
            int moveX = snap(gx - grabDx) - anchor[0];
            int moveY = snap(gy - grabDy) - anchor[1];

            dragStarts.forEach((node, start) -> {
                node.x = start[0] + moveX;
                node.y = start[1] + moveY;
            });

            return true;
        }

        if (draggingEntry) {
            entryX = snap(gx - grabDx);
            entryY = snap(gy - grabDy);
            return true;
        }

        if (wiring) {
            wireEndX = event.x();
            wireEndY = event.y();
            return true;
        }

        if (marqueeing) {
            marqueeEndX = gx;
            marqueeEndY = gy;
            updateMarqueeSelection();
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (event.button() == 1 && rightDown) {
            boolean moved = Math.abs(event.x() - rightPressX) > 3 || Math.abs(event.y() - rightPressY) > 3;
            rightDown = false;

            if (!moved) openContextMenu((int)event.x(), (int)event.y());

            return true;
        }

        if (dragging != null) {
            dragStarts.keySet().stream().map(this::parentOf).filter(Objects::nonNull).distinct().forEach(this::sortChildren);
            sortRoots();

            if (dragBefore != null) pushHistory(dragBefore, exportData());

            dragging = null;
            dragStarts.clear();
            dragBefore = null;

            return true;
        }

        if (draggingEntry) {
            if (dragBefore != null) pushHistory(dragBefore, exportData());

            draggingEntry = false;
            dragBefore = null;

            return true;
        }

        if (wiring) {
            Node parent = wireFrom;
            Node child = nodeAt(screenToGraphX(event.x()), screenToGraphY(event.y()));

            wiring = false;
            wireFrom = null;

            if (child != null && canAttach(parent, child)) recordChange(() -> attach(parent, child));

            return true;
        }

        if (marqueeing) {
            marqueeing = false;
            marqueeBase.clear();

            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sy == 0.0 || !isMouseOver(mx, my)) return false;

        float oldZoom = zoom;
        float newZoom = Math.clamp(zoom * (sy > 0 ? ZOOM_STEP : 1.0F / ZOOM_STEP), MIN_ZOOM, MAX_ZOOM);

        float graphX = (float)(mx - getX() - panX) / oldZoom;
        float graphY = (float)(my - getY() - panY) / oldZoom;

        panX = (float)(mx - getX()) - graphX * newZoom;
        panY = (float)(my - getY()) - graphY * newZoom;
        zoom = newZoom;

        return true;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (EditorKeyMappings.SELECT_ALL.matches(event)) {
            selectedStrip = null;
            selectedNodes.addAll(nodes);
            notifySelection();

            return true;
        }

        if (!EditorKeyMappings.DELETE.matches(event) && event.key() != GLFW.GLFW_KEY_BACKSPACE) return false;
        if (selectedNodes.isEmpty() && selectedStrip == null) return false;

        Selection.Strip strip = selectedStrip;
        List<Node> deleted = List.copyOf(selectedNodes);

        recordChange(() -> {
            if (strip != null) (strip.condition() ? strip.node().conditions : strip.node().subtasks).remove(strip.index());

            deleted.forEach(this::deleteNode);
        });

        selectedStrip = null;
        selectedNodes.clear();
        notifySelection();

        return true;
    }

    private void beginWire(final @Nullable Node from, final MouseButtonEvent event) {
        wiring = true;
        wireFrom = from;
        wireEndX = event.x();
        wireEndY = event.y();
    }

    /// Begins moving the selection by `grabbed`, each node keeping its offset from the others.
    private void beginDrag(final Node grabbed, final int gx, final int gy) {
        dragging = grabbed;
        grabDx = gx - grabbed.x;
        grabDy = gy - grabbed.y;
        dragBefore = exportData();
        dragStarts.clear();

        selectedNodes.forEach(node -> dragStarts.put(node, new int[] {node.x, node.y}));
    }

    /// Begins a selection box at (`gx`, `gy`), keeping the current selection when `additive`.
    private void beginMarquee(final int gx, final int gy, final boolean additive) {
        marqueeBase.clear();

        if (additive) marqueeBase.addAll(selectedNodes);

        marqueeing = true;
        marqueeStartX = gx;
        marqueeStartY = gy;
        marqueeEndX = gx;
        marqueeEndY = gy;
        updateMarqueeSelection();
    }

    /// The base selection and every node the selection box covers.
    private void updateMarqueeSelection() {
        int minX = Math.min(marqueeStartX, marqueeEndX);
        int maxX = Math.max(marqueeStartX, marqueeEndX);
        int minY = Math.min(marqueeStartY, marqueeEndY);
        int maxY = Math.max(marqueeStartY, marqueeEndY);

        selectedStrip = null;
        selectedNodes.clear();
        selectedNodes.addAll(marqueeBase);

        for (Node node : nodes) {
            if (node.x < maxX && node.x + nodeWidth(node) > minX && node.y < maxY && node.y + nodeHeight(node) > minY) selectedNodes.add(node);
        }

        notifySelection();
    }

    private void selectOnly(final Node node) {
        selectedStrip = null;
        selectedNodes.clear();
        selectedNodes.add(node);
        notifySelection();
    }

    private void notifySelection() {
        if (onSelectionChanged == null) return;

        Selection selection;

        if (selectedStrip != null) {
            selection = selectedStrip;
        } else if (selectedNodes.isEmpty()) {
            selection = null;
        } else if (selectedNodes.size() == 1) {
            selection = new Selection.Box(selectedNodes.iterator().next());
        } else {
            selection = new Selection.Boxes(List.copyOf(selectedNodes));
        }

        onSelectionChanged.accept(selection);
    }

    private void openContextMenu(final int screenX, final int screenY) {
        int gx = screenToGraphX(screenX);
        int gy = screenToGraphY(screenY);
        Node hit = nodeAt(gx, gy);
        Map<BehaviorNodeCatalog.Role, Map<Identifier, JsonElement>> catalog = BehaviorNodeCatalog.entries();
        ContextMenuContainer.PageBuilder page = ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(160);

        if (hit == null) {
            fillEntries(page.entry().message(Component.literal("Add Flow Control")).subpage().pageWidth(160),
                catalog.get(BehaviorNodeCatalog.Role.FLOW_CONTROL), json -> selectOnly(addNode(json, gx, gy))).done();
            fillEntries(page.entry().message(Component.literal("Add Task")).subpage().pageWidth(160),
                catalog.get(BehaviorNodeCatalog.Role.TASK), json -> selectOnly(addNode(json, gx, gy))).done();
        } else {
            fillEntries(page.entry().message(Component.literal("Add Condition")).subpage().pageWidth(160),
                catalog.get(BehaviorNodeCatalog.Role.CONDITION), json -> hit.conditions.add(new Attachment(json.deepCopy(), 10))).done();
            fillEntries(page.entry().message(Component.literal("Add Subtask")).subpage().pageWidth(160),
                catalog.get(BehaviorNodeCatalog.Role.SUBTASK), json -> hit.subtasks.add(new Attachment(json.deepCopy(), 10))).done();
            page.entry().message(Component.literal("Delete")).onPress(_ -> {
                List<Node> deleted = selectedNodes.contains(hit) ? List.copyOf(selectedNodes) : List.of(hit);

                recordChange(() -> deleted.forEach(this::deleteNode));
                selectedNodes.removeAll(deleted);
                notifySelection();
            }).done();
        }

        ModalManager.getInstance().openModalWidget(page.build(), screenX, screenY);
    }

    private ContextMenuContainer.PageBuilder fillEntries(
        final ContextMenuContainer.PageBuilder page, final Map<Identifier, JsonElement> entries, final Consumer<JsonElement> place
    ) {
        entries.forEach((id, json) -> page.entry().message(Component.literal(id.toString()))
            .onPress(_ -> recordChange(() -> place.accept(json))).done());

        if (entries.isEmpty()) page.entry().message(Component.literal("None")).onPress(_ -> {}).done();

        return page;
    }

    /// Anything on the canvas the host builds a Details panel for.
    public sealed interface Selection {
        /// One placed node.
        record Box(Node node) implements Selection {}

        /// Several placed nodes, in the order they were selected.
        record Boxes(List<Node> nodes) implements Selection {}

        /// A condition (`condition` true) or a subtask stacked on a node, by its position in the node's list.
        record Strip(Node node, boolean condition, int index) implements Selection {
            public Attachment attachment() {
                return (condition ? node.conditions : node.subtasks).get(index);
            }
        }
    }

    /// One placed node. The host edits [#json] and [#abortMode] from the Details panel.
    public static final class Node {
        private final int id;
        private int x;
        private int y;
        private JsonElement json;
        private final List<Integer> childIds = new ArrayList<>();
        private final List<Attachment> conditions = new ArrayList<>();
        private final List<Attachment> subtasks = new ArrayList<>();
        private AbortMode abortMode = AbortMode.NONE;

        /// The role [#json] decodes to, or null when it does not decode.
        private BehaviorNodeCatalog.@Nullable Role role;

        private Node(final int id, final int x, final int y, final JsonElement json) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.json = json;
            this.role = BehaviorNodeCatalog.roleOf(json);
        }

        public int id() {
            return id;
        }

        public JsonElement json() {
            return json;
        }

        public void setJson(final JsonElement json) {
            this.json = json;
            this.role = BehaviorNodeCatalog.roleOf(json);
        }

        public AbortMode abortMode() {
            return abortMode;
        }

        public void setAbortMode(final AbortMode abortMode) {
            this.abortMode = abortMode;
        }

        private boolean isFlowControl() {
            return role == BehaviorNodeCatalog.Role.FLOW_CONTROL;
        }

        private String label() {
            return labelOf(json);
        }

        /// The value of the field that names what the node runs, or nothing.
        private String summary() {
            if (!(json instanceof JsonObject object)) return "";

            for (String field : List.of("task", "subtask", "condition", "state_machine", "montage", "slot")) {
                if (object.has(field) && object.get(field).isJsonPrimitive()) return object.get(field).getAsString();
            }

            return "";
        }

        private static String labelOf(final JsonElement json) {
            Identifier type = BehaviorNodeCatalog.typeOf(json);

            return type == null ? "?" : type.getPath();
        }
    }

    /// A condition or a subtask stacked on a node. The host edits [#json] and [#interval] from the Details panel.
    public static final class Attachment {
        private JsonElement json;
        private int interval;

        private Attachment(final JsonElement json, final int interval) {
            this.json = json;
            this.interval = interval;
        }

        public JsonElement json() {
            return json;
        }

        public void setJson(final JsonElement json) {
            this.json = json;
        }

        public int interval() {
            return interval;
        }

        public void setInterval(final int interval) {
            this.interval = interval;
        }
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return Collections.emptyList();
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
