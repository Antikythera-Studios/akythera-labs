package com.yesman.akytheralabs.gui.widget.blendspace2d;

import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.core.math.algorithm.DelaunayTriangulation;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.akythera.animation.AnimationRef;
import com.yesman.akytheralabs.akythera.animation.BlendSpace2DHolder;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.ListInsert;
import com.yesman.akytheralabs.editor.historytrack.ListRemove;
import com.yesman.akytheralabs.editor.historytrack.NodeXYChange;
import com.yesman.akytheralabs.editor.historytrack.PendingNodeXYChange;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.gui.widget.ContextMenuContainer;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class BlendSpaceNodeGraphWidget extends AbstractContainerWidget implements AnchoredWidget {
    private static final int PADDING = 24;
    private static final int NODE_RADIUS = 4;
    private static final int LINE_COLOR = 0xFF3A5A3A;

    /// Translucent blue used for the in-progress Asset-Browser drop preview (the node's blue at ~50% alpha).
    private static final int GHOST_NODE_COLOR = 0x8044AAFF;

    private final Font font;
    private final BlendSpace2DHolder blendSpace2DHolder;
    private final Consumer<BlendSpace2DHolder.@Nullable MutableAnimNode> nodePressCallback;
    private final BiConsumer<Float, Float> previewCursorMoveCallback;

    /// See [#isDebugDriven].
    private BooleanSupplier debugDriven = () -> false;

    /// True while the owning screen is dragging a sequence out of the Asset Browser; drives the
    /// ghost-node drop preview. Reads the screen's `draggingSequence` state live.
    private final BooleanSupplier dropDragActive;

    private final ContextMenuContainer contextMenu;
    private final List<AbstractWidget> children = new ArrayList<>();

    private final PreviewCursor previewCursor;
    private BlendSpaceNodeGraphWidget.@Nullable NodeWidget selectedNodeWidget;

    public BlendSpaceNodeGraphWidget(
        Minecraft minecraft,
        BlendSpace2DHolder blendSpace2DHolder,
        Consumer<BlendSpace2DHolder.MutableAnimNode> nodePressCallback,
        BiConsumer<Float, Float> previewCursorMoveCallback,
        BooleanSupplier dropDragActive,
        Unit x1,
        Unit x2,
        Unit y1,
        Unit y2,
        AnchoredWidget.HorizontalAnchorType horizontalAnchorType,
        AnchoredWidget.VerticalAnchorType verticalAnchorType
    ) {
        super(0, 0, 0, 0, Component.empty(), AbstractScrollArea.defaultSettings(4));

        this.font = minecraft.font;
        this.blendSpace2DHolder = blendSpace2DHolder;
        this.nodePressCallback = nodePressCallback;
        this.previewCursorMoveCallback = previewCursorMoveCallback;
        this.dropDragActive = dropDragActive;

        this.contextMenu = ContextMenuContainer.builder(minecraft).pageWidth(110)
            .entry()
                .message(Component.translatable(LangKeys.GUI_BLEND_SPACE_2D_SCREEN_MESSAGE_PLACE_NODE))
                .onPress(entry -> {
                    int x = entry.getOwner().getX();
                    int y = entry.getOwner().getY();
                    addNodeAt(x, y);
                })
                .done()
            .entry()
                .predicate(() -> selectedNodeWidget != null)
                .message(Component.translatable(LangKeys.GUI_BLEND_SPACE_2D_SCREEN_MESSAGE_REMOVE_NODE))
                .onPress(_ -> {
                    if (selectedNodeWidget != null) removeNode(selectedNodeWidget);
                })
                .done()
            .build();

        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchorType;
        this.verticalAnchorType = verticalAnchorType;

        previewCursor = new PreviewCursor(-1, -1);

        // Place preview cursor at center
        children.add(previewCursor);

        for (BlendSpace2DHolder.MutableAnimNode node : blendSpace2DHolder.getNodes()) {
            children.add(new NodeWidget(node));
        }
    }

    private void setSelectedNode(BlendSpaceNodeGraphWidget.@Nullable NodeWidget selectedNodeWidget) {
        this.selectedNodeWidget = selectedNodeWidget;
        nodePressCallback.accept(selectedNodeWidget == null ? null : selectedNodeWidget.bsNode);
    }

    public void selectNode(BlendSpace2DHolder.@Nullable MutableAnimNode node) {
        if (node == null) {
            selectedNodeWidget = null;
            return;
        }

        for (AbstractWidget w : children) {
            if (w instanceof NodeWidget nodeWidget && nodeWidget.bsNode == node) {
                selectedNodeWidget = nodeWidget;
                return;
            }
        }

        selectedNodeWidget = null;
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        /*
        if (event.button() == 0 && contextMenu.isActive()) {
            if (contextMenu.mouseClicked(event, doubleClick)) {
                return true;
            } else {
                contextMenu.close();
            }
        }
        */

        boolean isInGraphBound = plotBoundary().containsPoint((int)event.x(), (int)event.y());
        boolean contextOpened = false;

        if (event.button() == 1 && isInGraphBound) {
            // Use widget rectangle. context menu is ok to exceed graph boundary.
            AkytheraEditorLayoutManager.getInstance().openModalWidget(contextMenu, (int)event.x(), (int)event.y());
            contextOpened = true;
        }
        /*
        else {
            contextMenu.close();
        }
        */

        for (AbstractWidget w : children) {
            if (w.isMouseOver(event.x(), event.y()) && w.mouseClicked(event, doubleClick)) {
                setFocused(w);
                setDragging(true);
                return true;
            }
        }

        if (contextOpened) {
            return false;
        }

        if (isInGraphBound && !isDebugDriven()) {
            previewCursor.setPosition((int)event.x(), (int)event.y());
            previewCursorMoveCallback.accept(toBlendX((int)event.x()), toBlendY((int)event.y()));
            setFocused(previewCursor);
            setDragging(true);
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        return getFocused() != null && isDragging() && event.button() == 0 && getFocused().mouseDragged(event, dx, dy);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        // DELETE removes the selected node — the same undoable path as the context menu's "Remove Node"
        // ([#removeNode]). Selecting a node makes this graph the screen's focused widget, so the key
        // lands here; with nothing selected the event falls through (e.g. to the screen's shortcuts).
        if (EditorKeyMappings.DELETE.matches(event) && selectedNodeWidget != null) {
            removeNode(selectedNodeWidget);
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        drawGrid(graphics);
        drawTriangleWireframe(graphics);
        drawAxisLabels(graphics);

        for (AbstractWidget w : children) {
            w.extractRenderState(graphics, mouseX, mouseY, a);
        }

        // Drop preview: while a sequence is dragged from the Asset Browser, show a translucent node
        // where releasing would place it (drawn last so it sits over the existing nodes).
        if (dropDragActive.getAsBoolean()) drawGhostNode(graphics, mouseX, mouseY);
    }

    /// Translucent preview of the node an Asset-Browser drop would create at (mouseX, mouseY). Mirrors
    /// [#dropNodeAt] / [BlendSpace2DHolder#addNode]: only inside the plotting area, and snapped/clamped
    /// to the grid so the ghost sits exactly where the real node will land.
    private void drawGhostNode(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!plotBoundary().containsPoint(mouseX, mouseY)) return;

        float bx = Math.clamp(blendSpace2DHolder.snapX(toBlendX(mouseX)), blendSpace2DHolder.getMinX(), blendSpace2DHolder.getMaxX());
        float by = Math.clamp(blendSpace2DHolder.snapY(toBlendY(mouseY)), blendSpace2DHolder.getMinY(), blendSpace2DHolder.getMaxY());

        drawDiamond(graphics, toScreenX(bx), toScreenY(by), NODE_RADIUS, GHOST_NODE_COLOR);
    }

    /// The inner plotting rectangle (graph area minus padding) where nodes can be placed and the
    /// ghost-drop preview is shown.
    private ScreenRectangle plotBoundary() {
        return new ScreenRectangle(
            getX() + PADDING, getY() + PADDING,
            getWidth() - PADDING * 2, getHeight() - PADDING * 2
        );
    }

    /// Filled diamond of the given radius centered at (cx, cy). Shared by the node widgets and the
    /// ghost-drop preview.
    private static void drawDiamond(GuiGraphicsExtractor graphics, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int hw = radius - Math.abs(dy);
            graphics.fill(cx - hw, cy + dy, cx + hw + 1, cy + dy + 1, color);
        }
    }

    private void drawTriangleWireframe(GuiGraphicsExtractor graphics) {
        DelaunayTriangulation<BlendSpace2DHolder.MutableAnimNode> space = blendSpace2DHolder.getNodeSpace();

        // An interior edge is shared by two triangles, so drawing per-triangle rasterizes it (and all
        // its per-pixel fills) twice. Dedup on the edge's two point indices — order-normalized and
        // packed into a long — so each unique edge is drawn once, halving the fills and the overdraw.
        Set<Long> drawnEdges = new HashSet<>();

        for (DelaunayTriangulation.Triangle t : space.getTriangles()) {
            drawEdgeOnce(graphics, space, drawnEdges, t.a(), t.b());
            drawEdgeOnce(graphics, space, drawnEdges, t.b(), t.c());
            drawEdgeOnce(graphics, space, drawnEdges, t.c(), t.a());
        }
    }

    /// Rasterizes the edge between point indices [i] and [j] once. [drawnEdges] holds the
    /// order-normalized packed index pair of every edge already drawn this frame, so an edge shared by
    /// two triangles is skipped on its second visit. No-op if either endpoint is missing.
    private void drawEdgeOnce(
        GuiGraphicsExtractor graphics,
        DelaunayTriangulation<BlendSpace2DHolder.MutableAnimNode> space,
        Set<Long> drawnEdges,
        int i,
        int j
    ) {
        long key = ((long) Math.min(i, j) << 32) | (Math.max(i, j) & 0xFFFFFFFFL);
        if (!drawnEdges.add(key)) return;

        DelaunayTriangulation.DelaunayPoint p1 = space.getPoint(i);
        DelaunayTriangulation.DelaunayPoint p2 = space.getPoint(j);
        if (p1 == null || p2 == null) return;

        drawLine(graphics, toScreenX(p1.x()), toScreenY(p1.y()), toScreenX(p2.x()), toScreenY(p2.y()));
    }

    /// Bresenham line, but coalesced into runs along the major axis: each maximal span sharing the
    /// minor coordinate is one `fill` instead of one-per-pixel. Every `fill` is a separate
    /// `GuiRenderState` element whose insert cost is superlinear in the element count (the render
    /// state does an intersecting-bounds search per element), so the dense triangle wireframe was
    /// dominating frame time. This makes the element count scale with the minor-axis delta rather than
    /// the full pixel length — axis-aligned edges collapse to a single `fill`.
    private void drawLine(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);

        if (dx >= dy) {
            // X-major: walk left→right, flush a horizontal run each time y steps.
            if (x0 > x1) { int t = x0; x0 = x1; x1 = t; t = y0; y0 = y1; y1 = t; }
            int sy = y1 >= y0 ? 1 : -1;
            int err = dx / 2;
            int y = y0;
            int runStartX = x0;

            for (int x = x0; x <= x1; x++) {
                err -= dy;
                if (err < 0) {
                    graphics.fill(runStartX, y, x + 1, y + 1, LINE_COLOR);
                    y += sy;
                    err += dx;
                    runStartX = x + 1;
                }
            }

            if (runStartX <= x1) graphics.fill(runStartX, y, x1 + 1, y + 1, LINE_COLOR);
        } else {
            // Y-major: walk top→bottom, flush a vertical run each time x steps.
            if (y0 > y1) { int t = x0; x0 = x1; x1 = t; t = y0; y0 = y1; y1 = t; }
            int sx = x1 >= x0 ? 1 : -1;
            int err = dy / 2;
            int x = x0;
            int runStartY = y0;

            for (int y = y0; y <= y1; y++) {
                err -= dx;
                if (err < 0) {
                    graphics.fill(x, runStartY, x + 1, y + 1, LINE_COLOR);
                    x += sx;
                    err += dy;
                    runStartY = y + 1;
                }
            }

            if (runStartY <= y1) graphics.fill(x, runStartY, x + 1, y1 + 1, LINE_COLOR);
        }
    }

    private void drawGrid(GuiGraphicsExtractor graphics) {
        int gx = getX() + PADDING, gy = getY() + PADDING;
        int gw = gridWidth(), gh = gridHeight();
        int gridColor = 0xFF2A2A2A;
        int axisColor = 0xFF3A3A3A;
        int hDiv = Math.max(1, blendSpace2DHolder.getHorizontalDivision());
        int vDiv = Math.max(1, blendSpace2DHolder.getVerticalDivision());

        // vertical lines = X cells
        for (int i = 0; i <= hDiv; i++) {
            int sx = gx + (int)((float) i / hDiv * gw);
            int c  = (i == 0 || i == hDiv) ? axisColor : gridColor;
            graphics.fill(sx, gy, sx + 1, gy + gh, c);
        }

        // horizontal lines = Y cells
        for (int i = 0; i <= vDiv; i++) {
            int sy = gy + (int)((float) i / vDiv * gh);
            int c  = (i == 0 || i == vDiv) ? axisColor : gridColor;
            graphics.fill(gx, sy, gx + gw, sy + 1, c);
        }
    }

    private void drawAxisLabels(GuiGraphicsExtractor g) {
        int gx = getX() + PADDING, gy = getY() + PADDING;
        int gw = gridWidth(), gh = gridHeight();
        float minX = blendSpace2DHolder.getNodeSpace().minX(), maxX = blendSpace2DHolder.getNodeSpace().maxX();
        float minY = blendSpace2DHolder.getNodeSpace().minY(), maxY = blendSpace2DHolder.getNodeSpace().maxY();
        int labelColor = 0xFF666666;

        // X axis: bottom edge (min/max at the corners, axis name centered)
        g.text(font, String.format("%.0f", minX), gx,        gy + gh + 4, labelColor, false);
        g.text(font, String.format("%.0f", maxX), gx + gw - 10, gy + gh + 4, labelColor, false);
        String axisNameX = blendSpace2DHolder.getAxisNameX();
        g.text(font, axisNameX, gx + (gw - font.width(axisNameX)) / 2, gy + gh + 4, labelColor, false);

        // Y axis: left edge (max/min at the corners, axis name centered)
        g.text(font, String.format("%.2f", maxY), getX(), gy,           labelColor, false);
        g.text(font, String.format("%.2f", minY), getX(), gy + gh - 8,  labelColor, false);
        String axisNameY = blendSpace2DHolder.getAxisNameY();
        g.text(font, axisNameY, getX(), gy + (gh - font.lineHeight) / 2, labelColor, false);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return Collections.unmodifiableList(children);
    }

    /// Whether the preview cursor follows a possessed entity's blend-space driver instead of the
    /// user's pointer. Installed by the owning screen; false for a screen that does not debug.
    public boolean isDebugDriven() {
        return debugDriven.getAsBoolean();
    }

    /// Installs the probe behind [#isDebugDriven].
    public void setDebugDrivenChecker(final BooleanSupplier debugDriven) {
        this.debugDriven = debugDriven;
    }

    /// Places the preview cursor at a blend-space coordinate. Does not fire
    /// [#previewCursorMoveCallback] — the caller already holds the value it is moving the cursor to.
    public void setPreviewCursor(final float blendX, final float blendY) {
        previewCursor.setPosition(toScreenX(blendX), toScreenY(blendY));
    }

    private int toScreenX(float bsX) {
        float t = (bsX - blendSpace2DHolder.getNodeSpace().minX()) / (blendSpace2DHolder.getNodeSpace().maxX() - blendSpace2DHolder.getNodeSpace().minX());
        return getX() + PADDING + (int)(t * gridWidth());
    }

    private int toScreenY(float bsY) {
        float t = (bsY - blendSpace2DHolder.getNodeSpace().minY()) / (blendSpace2DHolder.getNodeSpace().maxY() - blendSpace2DHolder.getNodeSpace().minY());
        // Y-axis inverted: low values at bottom, high at top
        return getY() + PADDING + (int)((1.0f - t) * gridHeight());
    }

    private float toBlendX(int screenX) {
        float t = (float)(screenX - getX() - PADDING) / gridWidth();
        return blendSpace2DHolder.getNodeSpace().minX() + t * (blendSpace2DHolder.getNodeSpace().maxX() - blendSpace2DHolder.getNodeSpace().minX());
    }

    private float toBlendY(int screenY) {
        float t = 1.0f - (float)(screenY - getY() - PADDING) / gridHeight();
        return blendSpace2DHolder.getNodeSpace().minY() + t * (blendSpace2DHolder.getNodeSpace().maxY() - blendSpace2DHolder.getNodeSpace().minY());
    }

    private int gridWidth() {
        return Math.max(1, getWidth() - 2 * PADDING);
    }

    private int gridHeight() {
        return Math.max(1, getHeight() - 2 * PADDING);
    }

    /// Place a new empty node (context-menu "Add Node Here").
    public void addNodeAt(int screenX, int screenY) {
        addNodeAt(screenX, screenY, AnimationRef.EMPTY);
    }

    /// Place a new node carrying [animation]. The holder snaps the position to the grid when
    /// snapping is enabled. One undoable [ListInsert] captures the node (animation included).
    public void addNodeAt(int screenX, int screenY, AnimationRef animation) {
        BlendSpace2DHolder.MutableAnimNode node = blendSpace2DHolder.addNode(toBlendX(screenX), toBlendY(screenY));
        node.setAnimation(animation);
        children.add(new NodeWidget(node));

        List<BlendSpace2DHolder.MutableAnimNode> nodes = blendSpace2DHolder.getNodesMutable();

        HistoryManager.getInstance().pushHistory(new ListInsert<>(
            nodes, nodes.size() - 1, node, this::syncNodeWidgets
        ));
    }

    /// Asset-browser drop: if (screenX, screenY) is inside the graph plotting area, place a new node
    /// there carrying [animation] and return true; otherwise ignore the drop and return false.
    public boolean dropNodeAt(double screenX, double screenY, AnimationRef animation) {
        if (!plotBoundary().containsPoint((int) screenX, (int) screenY)) return false;

        addNodeAt((int) screenX, (int) screenY, animation);
        return true;
    }

    // Remove by index
    public void removeNode(int index) {
        AbstractWidget toRemove = null;

        for (AbstractWidget w : children) {
            if (w instanceof NodeWidget nodeWidget && nodeWidget.bsNode.index() == index) {
                toRemove = nodeWidget;
                break;
            }
        }

        if (toRemove != null) children.remove(toRemove);

        blendSpace2DHolder.removeNode(index);
    }

    /// Remove by widget — the one undoable removal path, shared by the context menu's "Remove Node" and
    /// the DELETE key (see [#keyPressed]). The [ListRemove] entry restores the node at its list index on
    /// undo and re-syncs the widgets through [#syncNodeWidgets].
    private void removeNode(BlendSpaceNodeGraphWidget.@NonNull NodeWidget nodeWidget) {
        BlendSpace2DHolder.MutableAnimNode node = nodeWidget.bsNode;
        List<BlendSpace2DHolder.MutableAnimNode> nodes = blendSpace2DHolder.getNodesMutable();
        int listIndex = nodes.indexOf(node);

        children.remove(nodeWidget);
        blendSpace2DHolder.removeNode(node.index());

        if (listIndex >= 0) {
            HistoryManager.getInstance().pushHistory(new ListRemove<>(
                nodes, listIndex, node, this::syncNodeWidgets
            ));
        }

        // The selection dies with the node: the Details table must stop editing an entry that is gone,
        // and a stale `selectedNodeWidget` would make the next removal delete whatever node has since
        // taken the removed one's index.
        if (selectedNodeWidget == nodeWidget) setSelectedNode(null);
    }

    /// Refresh `children` from the holder's current node list, re-running the Delaunay
    /// triangulation with it.
    private void syncNodeWidgets() {
        blendSpace2DHolder.rebuildDelaunay();
        // Undo/redo of node add/remove flows only through here (it edits the node list directly),
        // so re-verify root-motion trajectory visibility from this single point.
        blendSpace2DHolder.notifyNodeGraphChanged();

        BlendSpace2DHolder.@Nullable MutableAnimNode previouslySelected =
            selectedNodeWidget != null ? selectedNodeWidget.bsNode : null;

        children.removeIf(w -> w instanceof NodeWidget);
        selectedNodeWidget = null;

        for (BlendSpace2DHolder.MutableAnimNode n : blendSpace2DHolder.getNodesMutable()) {
            NodeWidget w = new NodeWidget(n);
            children.add(w);
            if (n == previouslySelected) selectedNodeWidget = w;
        }
    }

    private class NodeWidget extends AbstractWidget {
        private final BlendSpace2DHolder.MutableAnimNode bsNode;

        public NodeWidget(BlendSpace2DHolder.MutableAnimNode bsNode) {
            super(0, 0, NODE_RADIUS, NODE_RADIUS, Component.empty());
            this.bsNode = bsNode;
        }

        @Override
        protected boolean isValidClickButton(MouseButtonInfo buttonInfo) {
            return buttonInfo.button() == 0 || buttonInfo.button() == 1;
        }

        @Override
        public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {
            BlendSpaceNodeGraphWidget.this.setSelectedNode(this);

            if (event.button() == 0) {
                HistoryManager.getInstance().pushHistory(new PendingNodeXYChange(bsNode, bsNode.x(), bsNode.y()));
            }
        }

        @Override
        protected void onDrag(@NonNull MouseButtonEvent event, double dx, double dy) {
            if (event.button() == 0) bsNode.setXY(toBlendX((int)event.x()), toBlendY((int)event.y()));
        }

        @Override
        public boolean mouseReleased(@NonNull MouseButtonEvent event) {
            if (event.button() == 0) {
                HistoryManager.getInstance().pushHistory(new NodeXYChange(bsNode, bsNode.x(), bsNode.y()));
            }

            return super.mouseReleased(event);
        }

        @Override
        public int getX() {
            return toScreenX(bsNode.x());
        }

        @Override
        public void setX(int x) {
            bsNode.setX(toBlendX(x));
        }

        @Override
        public int getY() {
            return toScreenY(bsNode.y());
        }

        @Override
        public void setY(int y) {
            bsNode.setY(toBlendY(y));
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            int left = getX() - NODE_RADIUS;
            int top = getY() - NODE_RADIUS;
            int right = getX() + NODE_RADIUS;
            int bottom = getY() + NODE_RADIUS;

            return isActive() && mouseX >= left && mouseY >= top && mouseX < right && mouseY < bottom;
        }

        @Override
        protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            boolean selected = this == BlendSpaceNodeGraphWidget.this.selectedNodeWidget;
            int sx = getX();
            int sy = getY();
            int color = selected ? 0xFFFFCC00 : 0xFF44AAFF;
            drawDiamond(graphics, sx, sy, NODE_RADIUS, color);
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
        }
    }

    private class PreviewCursor extends AbstractWidget {
        public PreviewCursor(int x, int y) {
            super(x, y, NODE_RADIUS, NODE_RADIUS, Component.empty());
        }

        @Override
        protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            graphics.fill(getX() - 1, getY() - 6, getX() + 1, getY() + 6, 0xFFFF4444);
            graphics.fill(getX() - 6, getY() - 1, getX() + 6, getY() + 1, 0xFFFF4444);
            graphics.fill(getX() - 3, getY() - 3, getX() + 3, getY() + 3, 0xFFFF8844);
        }

        @Override
        protected void onDrag(@NonNull MouseButtonEvent event, double dx, double dy) {
            if (isDebugDriven()) return;

            setX((int)Math.clamp(event.x(), toScreenX(blendSpace2DHolder.getMinX()), toScreenX(blendSpace2DHolder.getMaxX())));
            setY((int)Math.clamp(event.y(), toScreenY(blendSpace2DHolder.getMaxY()), toScreenY(blendSpace2DHolder.getMinY())));
            previewCursorMoveCallback.accept(toBlendX(getX()), toBlendY(getY()));
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            int left = getX() - NODE_RADIUS;
            int top = getY() - NODE_RADIUS;
            int right = getX() + NODE_RADIUS;
            int bottom = getY() + NODE_RADIUS;

            return isActive() && mouseX >= left && mouseY >= top && mouseX < right && mouseY < bottom;
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
        }
    }

    // ***************************************************************
    // [AnchoredWidget] implementations
    // ***************************************************************
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

    /// Relocate the widget with the given anchor types
    public void relocate(ScreenRectangle screenRectangle) {
        ScreenRectangle oldRectangle = getRectangle();
        float ratioX = (float)previewCursor.getX() / oldRectangle.width();
        float ratioY = (float)previewCursor.getY() / oldRectangle.height();

        AnchoredWidget.super.relocate(screenRectangle);

        if (previewCursor.getX() == -1 && previewCursor.getY() == -1) { // Uninitialized
            previewCursor.setPosition(
                getX() + PADDING + (toScreenX(blendSpace2DHolder.getMaxX()) - toScreenX(blendSpace2DHolder.getMinX())) / 2,
                getY() + PADDING + (toScreenY(blendSpace2DHolder.getMinY()) - toScreenY(blendSpace2DHolder.getMaxY())) / 2
            );
        } else {
            ScreenRectangle newRectangle = getRectangle();

            previewCursor.setPosition(
                (int)(newRectangle.width() * ratioX),
                (int)(newRectangle.height() * ratioY)
            );
        }
    }
}
