package com.yesman.akytheralabs.gui.widget;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.mojang.serialization.JsonOps;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.client.api.gui.AkytheraWidget;
import com.yesman.akythera.client.api.gui.BlueprintWidgets;
import com.yesman.akythera.client.api.gui.CheckBox;
import com.yesman.akythera.client.api.gui.ContextMenuContainer;
import com.yesman.akythera.client.api.gui.ProgressionBar;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.core.blueprint.widget.WidgetComponentBlueprint;
import com.yesman.akythera.core.blueprint.widget.WidgetLayout;
import com.yesman.akythera.core.blueprint.widget.WidgetUsage;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentAttachableHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import com.yesman.akytheralabs.akythera.widget.CheckBoxHolder;
import com.yesman.akytheralabs.akythera.widget.ProgressionBarHolder;
import com.yesman.akytheralabs.akythera.widget.WidgetBlueprintHolder;
import com.yesman.akytheralabs.akythera.widget.WidgetBlueprintRefHolder;
import com.yesman.akytheralabs.akythera.widget.WidgetComponentHolder;
import com.yesman.akytheralabs.akythera.widget.WidgetComponentHolders;
import com.yesman.akytheralabs.akythera.widget.WidgetLayoutHolder;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.components.ComponentEditorPanel;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/// The Canvas tab of the widget editor: the blueprint's widgets, built by the engine as they are built in game,
/// laid out in a frame standing in for the root's parent, on the grid of the other canvases.
///
/// - **View**: right or middle drag pans, the wheel zooms around the pointer. A right click without a drag opens a
///   menu adding a widget (under the focused container, or at the pointer) and deleting the focused one; Delete
///   deletes it too. The frame's edges resize it, and the
///   toolbar's presets set common window sizes. The frame's size is saved with the blueprint, see
///   [WidgetBlueprintHolder#getCanvasWidth].
/// - **Editing**: a click selects a widget. A widget placed straight under the root is dragged by its body to move
///   and by its edges or corners to resize; the rect goes back to its units through [AnchoredWidget#backtrack], one
///   undo step per drag. While [#snapping], a dragged edge snaps to the frame's and the other widgets' edges and
///   centers. A widget inside a grid or a container is placed by its cell and only selected.
/// - **Preview**: the widgets take no input and run no script. They are rebuilt whenever what the editor would save
///   for them changes, which covers every edit, undo and redo. A placed widget blueprint is drawn as a box.
public final class WidgetCanvas extends AbstractContainerWidget implements AnchoredWidget {
    private static final int TOOLBAR_H = 20;

    /// Half the side of a resize handle, and how far from an edge a press grabs it, in screen pixels.
    private static final int HANDLE = 3;

    /// How far from a line a dragged edge snaps onto it, in screen pixels.
    private static final int SNAP_REACH = 5;

    /// The frame's smallest side, in GUI pixels.
    private static final int MIN_FRAME = 16;

    private static final float MIN_ZOOM = 0.30F;
    private static final float MAX_ZOOM = 3.00F;
    private static final float ZOOM_STEP = 1.10F;

    private static final int COLOR_TOOLBAR = 0xFF202020;
    private static final int COLOR_FRAME_BORDER = 0xFF6A6A6A;
    private static final int COLOR_SELECTION = 0xFFFFCC00;
    private static final int COLOR_ANCHOR = 0xFF4FC3F7;
    private static final int COLOR_GUIDE = 0xFFFF6EC7;
    private static final int COLOR_PLACEHOLDER_FILL = 0x30FFFFFF;
    private static final int COLOR_PLACEHOLDER_BORDER = 0xFF8A8A8A;
    private static final int COLOR_TEXT = 0xFFAAAAAA;

    /// Window sizes the frame can take, as GUI pixels at a GUI scale.
    private static final List<FramePreset> PRESETS = List.of(
        new FramePreset("1920 x 1080, GUI 2", 960, 540),
        new FramePreset("1920 x 1080, GUI 3", 640, 360),
        new FramePreset("1920 x 1080, GUI 4", 480, 270),
        new FramePreset("1280 x 720, GUI 2", 640, 360),
        new FramePreset("1280 x 720, GUI 3", 426, 240),
        new FramePreset("854 x 480, GUI 2", 427, 240)
    );

    private static final int EDGE_LEFT = 1;
    private static final int EDGE_RIGHT = 2;
    private static final int EDGE_TOP = 4;
    private static final int EDGE_BOTTOM = 8;

    private final Font font;
    private final WidgetBlueprintHolder root;

    /// How far a right press may travel and still open the menu rather than pan, in screen pixels.
    private static final int CLICK_SLOP = 3;

    /// The Widgets panel: what a click selects through, and what adds and removes widgets.
    private final ComponentEditorPanel widgets;

    /// Whether a dragged widget edge snaps to the lines around it. Shared by every canvas for the session.
    private static boolean snapping = true;

    private final EditorComboBox<FramePreset> presetCombo;
    private final CheckBox snapCheckBox;

    /// Where the frame's top left draws, from the top left of the canvas area, in screen pixels.
    private float panX;
    private float panY;
    private float zoom = 1.0F;

    /// Whether the frame was centered in the canvas area yet.
    private boolean centered;

    /// The root the preview widgets are attached to, laid out against the frame. Null until first built.
    private @Nullable AkytheraWidget preview;

    /// The editor JSON of the root's widgets the preview was built from.
    private List<Map.Entry<String, JsonElement>> builtFrom = List.of();

    /// The preview widgets in draw order, each with the holder it was built from.
    private final List<Placed> placed = new ArrayList<>();

    private @Nullable ComponentDataHolder<?> selected;

    private @Nullable Drag drag;

    /// The lines the dragged widget snapped onto, in frame coordinates.
    private final IntList guidesX = new IntArrayList();
    private final IntList guidesY = new IntArrayList();

    public WidgetCanvas(
        final Font font,
        final WidgetBlueprintHolder root,
        final ComponentEditorPanel widgets
    ) {
        super(0, 0, 0, 0, Component.empty(), AbstractScrollArea.defaultSettings(0));

        this.font = font;
        this.root = root;
        this.widgets = widgets;
        this.x1 = AnchoredWidget.pixel(0);
        this.x2 = AnchoredWidget.pixel(0);
        this.y1 = AnchoredWidget.pixel(0);
        this.y2 = AnchoredWidget.pixel(0);
        this.horizontalAnchorType = HorizontalAnchorType.LEFT_RIGHT;
        this.verticalAnchorType = VerticalAnchorType.TOP_BOTTOM;
        this.presetCombo = new EditorComboBox<>(
            font,
            AnchoredWidget.pixel(4), AnchoredWidget.pixel(140),
            AnchoredWidget.pixel(2), AnchoredWidget.pixel(TOOLBAR_H - 4),
            HorizontalAnchorType.LEFT_WIDTH, VerticalAnchorType.TOP_HEIGHT,
            () -> PRESETS.stream().filter(preset -> preset.width() == root.getCanvasWidth() && preset.height() == root.getCanvasHeight()).findFirst().orElse(null),
            preset -> {
                if (preset != null) commitFrameSize(frameRectangle(), new ScreenRectangle(0, 0, preset.width(), preset.height()));
            },
            PRESETS.size(),
            Component.empty(),
            PRESETS,
            FramePreset::label
        );
        this.presetCombo.setClearable(false);

        Component snapLabel = Component.translatable(LangKeys.GUI_WIDGET_SCREEN_CANVAS_SNAP);

        this.snapCheckBox = new CheckBox(
            font,
            AnchoredWidget.pixel(6), AnchoredWidget.pixel(12 + 4 + font.width(snapLabel)),
            AnchoredWidget.pixel(4), AnchoredWidget.pixel(12),
            HorizontalAnchorType.RIGHT_WIDTH, VerticalAnchorType.TOP_HEIGHT,
            () -> snapping,
            value -> snapping = value,
            snapLabel,
            true
        );
        this.snapCheckBox.valueSetter().accept(snapping);
    }

    /// Outlines `widget` and shows its anchoring, or nothing for null.
    public void setSelected(final @Nullable ComponentDataHolder<?> widget) {
        selected = widget;
    }

    // ***************************************************************
    // Preview
    // ***************************************************************

    /// Rebuilds the preview when what the editor would save for the root's widgets changed since the last build.
    private void refreshPreview() {
        List<Map.Entry<String, JsonElement>> current = root.getComponents().entrySet().stream()
            .map(entry -> Map.entry(entry.getKey(), WidgetComponentHolders.REGISTRY.codec().encodeStart(JsonOps.INSTANCE, entry.getValue())
                .result().orElse(JsonNull.INSTANCE)))
            .toList();

        if (preview != null && current.equals(builtFrom)) {
            if (preview.getWidth() != root.getCanvasWidth() || preview.getHeight() != root.getCanvasHeight()) preview.relocate(frameRectangle());

            return;
        }

        builtFrom = current;
        placed.clear();
        preview = new AkytheraWidget(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0), AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            HorizontalAnchorType.LEFT_RIGHT, VerticalAnchorType.TOP_BOTTOM
        );

        AkytheraWidget host = preview;

        root.getComponents().forEach((name, holder) -> {
            PatchComponent component;

            // A placed widget blueprint is an empty widget of its layout, so neither its script nor the world's
            // registry is reached
            try {
                component = holder instanceof WidgetBlueprintRefHolder reference
                    ? BlueprintWidgets.widget(reference.getLayout().makeObject())
                    : holder.makeObject() instanceof WidgetComponentBlueprint blueprint ? blueprint.instantiate(host) : null;
            } catch (RuntimeException e) {
                AkytheraLabs.LOGGER.warn("Can't preview widget {}: {}", name, e.getMessage());
                return;
            }

            if (!(component instanceof AbstractWidget widget)) return;

            host.addComponent(name, (AbstractWidget & PatchComponent) widget);
            list(widget, holder, host, true, 0);
        });

        host.relocate(frameRectangle());
    }

    /// Lists `widget` as built from `holder` and laid out by `parent`, with its preview value set, then the widgets it
    /// holds against their holders, by name.
    private void list(final AbstractWidget widget, final ComponentDataHolder<?> holder, final AkytheraWidget parent, final boolean movable, final int depth) {
        if (holder instanceof ProgressionBarHolder bar && widget instanceof ProgressionBar progressionBar) {
            progressionBar.setProgressionImmediately(bar.getPreviewProgression());
        } else if (holder instanceof CheckBoxHolder box && widget instanceof CheckBox checkBox) {
            checkBox.setWidgetValue(box.isPreviewChecked());
        }

        placed.add(new Placed(widget, holder, parent, movable, depth));

        if (holder instanceof ComponentAttachableHolder host && widget instanceof AkytheraWidget container) {
            host.getComponents().forEach((name, child) -> {
                if (container.getComponents().get(name) instanceof AbstractWidget childWidget) list(childWidget, child, container, false, depth + 1);
            });
        }
    }

    /// Resizes the frame from `from` to `to` as one undo step.
    private void commitFrameSize(final ScreenRectangle from, final ScreenRectangle to) {
        Consumer<ScreenRectangle> writer = size -> root.setCanvasSize(size.width(), size.height());

        HistoryManager.getInstance().pushHistory(new DataChange<>(from, to, writer));
        writer.accept(to);
    }

    private ScreenRectangle frameRectangle() {
        return new ScreenRectangle(0, 0, root.getCanvasWidth(), root.getCanvasHeight());
    }

    private @Nullable Placed placedOf(final @Nullable ComponentDataHolder<?> holder) {
        for (Placed entry : placed) {
            if (entry.holder() == holder) return entry;
        }

        return null;
    }

    // ***************************************************************
    // Coordinates
    // ***************************************************************

    private int areaTop() {
        return getY() + TOOLBAR_H;
    }

    private float originX() {
        return getX() + panX;
    }

    private float originY() {
        return areaTop() + panY;
    }

    private float toFrameX(final double screenX) {
        return (float) ((screenX - originX()) / zoom);
    }

    private float toFrameY(final double screenY) {
        return (float) ((screenY - originY()) / zoom);
    }

    private int toScreenX(final float frameX) {
        return Math.round(originX() + frameX * zoom);
    }

    private int toScreenY(final float frameY) {
        return Math.round(originY() + frameY * zoom);
    }

    /// The edges of the screen rect `left`..`right` x `top`..`bottom` within [#HANDLE] of the point, as `EDGE_*`
    /// bits.
    private static int edgesAt(final double x, final double y, final int left, final int top, final int right, final int bottom) {
        if (x < left - HANDLE || x > right + HANDLE || y < top - HANDLE || y > bottom + HANDLE) return 0;

        int edges = 0;

        if (Math.abs(x - left) <= HANDLE) edges |= EDGE_LEFT;
        else if (Math.abs(x - right) <= HANDLE) edges |= EDGE_RIGHT;

        if (Math.abs(y - top) <= HANDLE) edges |= EDGE_TOP;
        else if (Math.abs(y - bottom) <= HANDLE) edges |= EDGE_BOTTOM;

        return edges;
    }

    /// Snaps the sides `low`..`high` of a rect on one axis to the nearest of `lines` within `reach`, moving the
    /// sides `edges` drags (both, and the center, for none), and adds the lines it lands on to `guides`.
    ///
    /// @return the snapped `{low, high}`
    private static int[] snap(int low, int high, final int edges, final int lowEdge, final int highEdge, final IntList lines, final float reach, final IntList guides) {
        boolean whole = edges == 0;
        boolean movesLow = whole || (edges & lowEdge) != 0;
        boolean movesHigh = whole || (edges & highEdge) != 0;
        int offset = Integer.MAX_VALUE;

        for (int line : lines) {
            if (movesLow && Math.abs(line - low) < Math.abs(offset)) offset = line - low;
            if (movesHigh && Math.abs(line - high) < Math.abs(offset)) offset = line - high;
            if (whole && Math.abs(line - (low + high) / 2) < Math.abs(offset)) offset = line - (low + high) / 2;
        }

        if (Math.abs(offset) > reach) offset = 0;
        if (movesLow) low += offset;
        if (movesHigh) high += offset;

        for (int line : lines) {
            if ((movesLow && line == low) || (movesHigh && line == high) || (whole && line == (low + high) / 2)) guides.add(line);
        }

        return new int[] {low, high};
    }

    // ***************************************************************
    // Rendering
    // ***************************************************************

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int frameWidth = root.getCanvasWidth();
        int frameHeight = root.getCanvasHeight();

        if (!centered && getWidth() > 0) {
            panX = (getWidth() - frameWidth * zoom) / 2.0F;
            panY = (getBottom() - areaTop() - frameHeight * zoom) / 2.0F;
            centered = true;
        }

        refreshPreview();

        for (Placed entry : placed) {
            ScreenRectangle area = entry.parent().childArea(entry.widget());

            if (area != null && entry.holder() instanceof WidgetComponentHolder<?> widget) {
                widget.getLayout().setPlacement(new WidgetLayoutHolder.Placement(area, entry.widget().getRectangle()));
            }
        }

        CanvasGrid.extract(graphics, getX(), areaTop(), getRight(), getBottom(), originX(), originY(), zoom);
        graphics.enableScissor(getX(), areaTop(), getRight(), getBottom());

        graphics.pose().pushMatrix();
        graphics.pose().translate(originX(), originY());
        graphics.pose().scale(zoom, zoom);

        // The preview takes no input, so it sees no pointer either
        if (preview != null) preview.extractRenderState(graphics, -1, -1, a);

        for (Placed entry : placed) {
            if (!(entry.holder() instanceof WidgetBlueprintRefHolder reference) || !entry.widget().visible) continue;

            AbstractWidget box = entry.widget();
            graphics.fill(box.getX(), box.getY(), box.getRight(), box.getBottom(), COLOR_PLACEHOLDER_FILL);
            graphics.outline(box.getX(), box.getY(), box.getWidth(), box.getHeight(), COLOR_PLACEHOLDER_BORDER);
            graphics.text(
                font,
                Component.literal(reference.getWidget() == null ? "?" : EditorHolderCodecs.entryId(reference.getWidget()).toString()),
                box.getX() + 3, box.getY() + 3, COLOR_TEXT, false
            );
        }

        graphics.pose().popMatrix();

        graphics.outline(toScreenX(0) - 1, toScreenY(0) - 1, Math.round(frameWidth * zoom) + 2, Math.round(frameHeight * zoom) + 2, COLOR_FRAME_BORDER);

        Placed selection = placedOf(selected);

        // The selection's outline. A movable widget also gets its resize handles and its anchoring: a marker on
        // each point of the frame it is anchored to, with a line to the edge of the widget measured from it
        if (selection != null && selection.widget().visible) {
            AbstractWidget widget = selection.widget();
            int left = toScreenX(widget.getX());
            int top = toScreenY(widget.getY());
            int right = toScreenX(widget.getRight());
            int bottom = toScreenY(widget.getBottom());

            graphics.outline(left - 1, top - 1, right - left + 2, bottom - top + 2, COLOR_SELECTION);

            if (selection.movable() && selection.holder() instanceof WidgetComponentHolder<?> holder) {
                for (int x : new int[] {left, (left + right) / 2, right}) {
                    for (int y : new int[] {top, (top + bottom) / 2, bottom}) {
                        if (x == (left + right) / 2 && y == (top + bottom) / 2) continue;

                        graphics.fill(x - HANDLE, y - HANDLE, x + HANDLE + 1, y + HANDLE + 1, COLOR_SELECTION);
                    }
                }

                WidgetLayout layout = holder.getLayout().makeObject();
                int frameLeft = toScreenX(0);
                int frameTop = toScreenY(0);
                int frameRight = toScreenX(frameWidth);
                int frameBottom = toScreenY(frameHeight);

                // pairs of (anchor point on the frame, the widget's edge measured from it), per axis
                int[][] horizontal = switch (layout.horizontalAnchor()) {
                    case LEFT_WIDTH -> new int[][] {{frameLeft, left}};
                    case RIGHT_WIDTH -> new int[][] {{frameRight, right}};
                    case CENTER_WIDTH -> new int[][] {{(frameLeft + frameRight) / 2, (left + right) / 2}};
                    case LEFT_RIGHT -> new int[][] {{frameLeft, left}, {frameRight, right}};
                };
                int[][] vertical = switch (layout.verticalAnchor()) {
                    case TOP_HEIGHT -> new int[][] {{frameTop, top}};
                    case BOTTOM_HEIGHT -> new int[][] {{frameBottom, bottom}};
                    case CENTER_HEIGHT -> new int[][] {{(frameTop + frameBottom) / 2, (top + bottom) / 2}};
                    case TOP_BOTTOM -> new int[][] {{frameTop, top}, {frameBottom, bottom}};
                };

                for (int[] x : horizontal) {
                    for (int[] y : vertical) {
                        graphics.fill(x[0] - 3, y[0] - 1, x[0] + 4, y[0] + 2, COLOR_ANCHOR);
                        graphics.fill(x[0] - 1, y[0] - 3, x[0] + 2, y[0] + 4, COLOR_ANCHOR);
                        graphics.fill(Math.min(x[0], x[1]), y[1], Math.max(x[0], x[1]) + 1, y[1] + 1, COLOR_ANCHOR);
                        graphics.fill(x[1], Math.min(y[0], y[1]), x[1] + 1, Math.max(y[0], y[1]) + 1, COLOR_ANCHOR);
                    }
                }
            }
        }

        for (int x : guidesX) {
            int screenX = toScreenX(x);

            for (int y = areaTop(); y < getBottom(); y += 4) graphics.fill(screenX, y, screenX + 1, Math.min(y + 2, getBottom()), COLOR_GUIDE);
        }

        for (int y : guidesY) {
            int screenY = toScreenY(y);

            for (int x = getX(); x < getRight(); x += 4) graphics.fill(x, screenY, Math.min(x + 2, getRight()), screenY + 1, COLOR_GUIDE);
        }

        graphics.disableScissor();

        // A resize cursor over an edge a press would grab, and through the resize it started
        Drag hover = drag != null ? drag : mouseY >= areaTop() && isMouseOver(mouseX, mouseY) ? edgeGrabAt(mouseX, mouseY) : null;
        int hoverEdges = switch (hover) {
            case Drag.Frame frame -> frame.edges();
            case Drag.Reshape reshape -> reshape.edges();
            case null, default -> 0;
        };
        boolean horizontal = (hoverEdges & (EDGE_LEFT | EDGE_RIGHT)) != 0;
        boolean vertical = (hoverEdges & (EDGE_TOP | EDGE_BOTTOM)) != 0;

        if (horizontal && vertical) {
            graphics.requestCursor(CursorTypes.RESIZE_ALL);
        } else if (horizontal) {
            graphics.requestCursor(CursorTypes.RESIZE_EW);
        } else if (vertical) {
            graphics.requestCursor(CursorTypes.RESIZE_NS);
        }

        // Window presets don't size a widget, whatever places it does
        presetCombo.visible = root.usage() != WidgetUsage.WIDGET;

        graphics.fill(getX(), getY(), getRight(), areaTop(), COLOR_TOOLBAR);
        graphics.text(
            font, Component.literal(frameWidth + " x " + frameHeight),
            presetCombo.visible ? presetCombo.getRight() + 8 : getX() + 6, getY() + (TOOLBAR_H - font.lineHeight) / 2 + 1, COLOR_TEXT, false
        );
        presetCombo.extractRenderState(graphics, mouseX, mouseY, a);
        snapCheckBox.extractRenderState(graphics, mouseX, mouseY, a);
    }

    // ***************************************************************
    // Input
    // ***************************************************************

    /// The resize a press at the point would start: the selected movable widget's edges first, then the frame's.
    /// Null off every edge.
    private @Nullable Drag edgeGrabAt(final double x, final double y) {
        Placed selection = placedOf(selected);

        if (selection != null && selection.movable() && selection.widget().visible) {
            AbstractWidget widget = selection.widget();
            int edges = edgesAt(x, y, toScreenX(widget.getX()), toScreenY(widget.getY()), toScreenX(widget.getRight()), toScreenY(widget.getBottom()));

            if (edges != 0) return new Drag.Reshape(selection, edges, new ScreenRectangle(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()), x, y);
        }

        int frameEdges = edgesAt(x, y, toScreenX(0), toScreenY(0), toScreenX(root.getCanvasWidth()), toScreenY(root.getCanvasHeight()));

        return frameEdges != 0 ? new Drag.Frame(frameEdges, frameRectangle(), panX, panY, x, y) : null;
    }

    @Override
    public boolean mouseClicked(final @NonNull MouseButtonEvent event, final boolean doubleClick) {
        if (event.y() < areaTop()) return super.mouseClicked(event, doubleClick);
        if (!isMouseOver(event.x(), event.y())) return false;

        Drag grab = event.button() == 0 ? edgeGrabAt(event.x(), event.y()) : null;

        if (grab != null) {
            drag = grab;
            return true;
        }

        float frameX = toFrameX(event.x());
        float frameY = toFrameY(event.y());
        Placed hit = null;

        for (Placed entry : placed) {
            AbstractWidget widget = entry.widget();

            if (widget.visible && frameX >= widget.getX() && frameX < widget.getRight() && frameY >= widget.getY() && frameY < widget.getBottom()
                && (hit == null || entry.depth() >= hit.depth())) {
                hit = entry;
            }
        }

        // A right press on a widget focuses it for the menu its release opens; on nothing it keeps the focus
        if (event.button() != 0) {
            if (event.button() == 1 && hit != null) widgets.focusListedComponent(hit.holder());

            drag = new Drag.Pan(event.button(), event.x(), event.y());
            return true;
        }

        if (hit == null) {
            widgets.focusComponent(null);
        } else {
            widgets.focusListedComponent(hit.holder());
        }

        if (hit != null && hit.movable()) {
            AbstractWidget widget = hit.widget();
            drag = new Drag.Reshape(hit, 0, new ScreenRectangle(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()), event.x(), event.y());
        }

        return true;
    }

    @Override
    public boolean mouseDragged(final @NonNull MouseButtonEvent event, final double dx, final double dy) {
        switch (drag) {
            case Drag.Pan _ -> {
                panX += (float) dx;
                panY += (float) dy;
            }
            case Drag.Frame frame -> {
                int moveX = Math.round((float) (event.x() - frame.pressX()) / zoom);
                int moveY = Math.round((float) (event.y() - frame.pressY()) / zoom);
                ScreenRectangle start = frame.start();
                int edges = frame.edges();
                int width = Math.max(MIN_FRAME, start.width() + ((edges & EDGE_RIGHT) != 0 ? moveX : (edges & EDGE_LEFT) != 0 ? -moveX : 0));
                int height = Math.max(MIN_FRAME, start.height() + ((edges & EDGE_BOTTOM) != 0 ? moveY : (edges & EDGE_TOP) != 0 ? -moveY : 0));

                // A left or top edge drags the frame's origin, so the opposite edge stays put
                if ((edges & EDGE_LEFT) != 0) panX = frame.panX() + (start.width() - width) * zoom;
                if ((edges & EDGE_TOP) != 0) panY = frame.panY() + (start.height() - height) * zoom;

                root.setCanvasSize(width, height);
            }
            case Drag.Reshape reshape -> {
                int moveX = Math.round((float) (event.x() - reshape.pressX()) / zoom);
                int moveY = Math.round((float) (event.y() - reshape.pressY()) / zoom);
                ScreenRectangle start = reshape.start();
                int edges = reshape.edges();
                int left = start.left() + (edges == 0 || (edges & EDGE_LEFT) != 0 ? moveX : 0);
                int top = start.top() + (edges == 0 || (edges & EDGE_TOP) != 0 ? moveY : 0);
                int right = start.right() + (edges == 0 || (edges & EDGE_RIGHT) != 0 ? moveX : 0);
                int bottom = start.bottom() + (edges == 0 || (edges & EDGE_BOTTOM) != 0 ? moveY : 0);
                AbstractWidget widget = reshape.placed().widget();

                guidesX.clear();
                guidesY.clear();

                if (snapping) {
                    int frameWidth = root.getCanvasWidth();
                    int frameHeight = root.getCanvasHeight();
                    IntList linesX = new IntArrayList(new int[] {0, frameWidth / 2, frameWidth});
                    IntList linesY = new IntArrayList(new int[] {0, frameHeight / 2, frameHeight});
                    int index = placed.indexOf(reshape.placed());

                    for (int i = 0; i < placed.size(); i++) {
                        // The dragged widget and the widgets it holds move along
                        if (i == index) {
                            while (i + 1 < placed.size() && placed.get(i + 1).depth() > 0) i++;
                            continue;
                        }

                        AbstractWidget other = placed.get(i).widget();

                        if (!other.visible) continue;

                        linesX.add(other.getX());
                        linesX.add((other.getX() + other.getRight()) / 2);
                        linesX.add(other.getRight());
                        linesY.add(other.getY());
                        linesY.add((other.getY() + other.getBottom()) / 2);
                        linesY.add(other.getBottom());
                    }

                    int[] horizontal = snap(left, right, edges, EDGE_LEFT, EDGE_RIGHT, linesX, SNAP_REACH / zoom, guidesX);
                    int[] vertical = snap(top, bottom, edges, EDGE_TOP, EDGE_BOTTOM, linesY, SNAP_REACH / zoom, guidesY);
                    left = horizontal[0];
                    right = horizontal[1];
                    top = vertical[0];
                    bottom = vertical[1];
                }

                widget.setRectangle(Math.max(1, right - left), Math.max(1, bottom - top), left, top);

                if (widget instanceof AnchoredWidget anchored) anchored.relocateChildren();
            }
            case null -> {
                return super.mouseDragged(event, dx, dy);
            }
        }

        return true;
    }

    /// Ends a drag. A resized frame, or a moved or resized widget, writes its new state as one undo step.
    @Override
    public boolean mouseReleased(final @NonNull MouseButtonEvent event) {
        Drag ended = drag;
        drag = null;
        guidesX.clear();
        guidesY.clear();

        if (ended instanceof Drag.Frame frame) {
            if (!frameRectangle().equals(frame.start())) commitFrameSize(frame.start(), frameRectangle());

            return true;
        }

        // A right click that did not pan: add under the focused container, or at the pointer under the root
        if (ended instanceof Drag.Pan pan
            && pan.button() == 1
            && Math.abs(event.x() - pan.pressX()) < CLICK_SLOP
            && Math.abs(event.y() - pan.pressY()) < CLICK_SLOP) {
            ComponentAttachableHolder host = selected instanceof ComponentAttachableHolder container ? container : root;
            int frameX = Math.round(toFrameX(event.x()));
            int frameY = Math.round(toFrameY(event.y()));
            ContextMenuContainer.PageBuilder builder = ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(150);
            ContextMenuContainer.PageBuilder add = builder.entry().message(Component.translatable(LangKeys.GUI_WIDGET_SCREEN_MENU_ADD_WIDGET)).subpage();

            widgets.fillAttachEntries(add, host, holder -> {
                if (host != root || !(holder instanceof WidgetComponentHolder<?> widget)) return;

                WidgetLayout layout = widget.getLayout().makeObject();

                widget.getLayout().readFrom(new WidgetLayout(
                    new WidgetLayout.Unit(frameX, false), layout.x2(), new WidgetLayout.Unit(frameY, false), layout.y2(),
                    WidgetLayout.Horizontal.LEFT_WIDTH, WidgetLayout.Vertical.TOP_HEIGHT
                ));
            });
            add.done();

            if (selected != null) {
                ComponentDataHolder<?> target = selected;

                builder.entry()
                    .message(Component.translatable(LangKeys.GUI_WIDGET_SCREEN_MENU_DELETE_WIDGET))
                    .onPress(_ -> widgets.removeListedComponent(target))
                    .done();
            }

            ModalManager.getInstance().openModalWidget(builder.build(), (int) event.x(), (int) event.y());
            return true;
        }

        if (!(ended instanceof Drag.Reshape reshape)) return ended != null || super.mouseReleased(event);

        AbstractWidget widget = reshape.placed().widget();

        if (widget.getRectangle().equals(reshape.start())
            || !(widget instanceof AnchoredWidget anchored)
            || !(reshape.placed().holder() instanceof WidgetComponentHolder<?> holder)) {
            return true;
        }

        WidgetLayout before = holder.getLayout().makeObject();

        anchored.backtrack(frameRectangle());

        WidgetLayout after = BlueprintWidgets.layoutOf(anchored);
        Consumer<WidgetLayout> writer = layout -> {
            holder.getLayout().readFrom(layout);
            widgets.refocusDetails();
        };

        HistoryManager.getInstance().pushHistory(new DataChange<>(before, after, writer));
        writer.accept(after);

        return true;
    }

    /// Delete removes the focused widget.
    @Override
    public boolean keyPressed(final @NonNull KeyEvent event) {
        if (selected != null && EditorKeyMappings.DELETE.matches(event)) {
            widgets.removeListedComponent(selected);
            return true;
        }

        return super.keyPressed(event);
    }

    /// Zooms around the pointer, a step reaching a whole zoom stopping on it, where text draws sharp.
    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        if (scrollY == 0.0 || mouseY < areaTop() || !isMouseOver(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);

        float oldZoom = zoom;
        float newZoom = Math.clamp(zoom * (scrollY > 0 ? ZOOM_STEP : 1.0F / ZOOM_STEP), MIN_ZOOM, MAX_ZOOM);
        float whole = scrollY > 0 ? (float) Math.floor(newZoom) : (float) Math.ceil(newZoom);

        if (whole >= 1.0F && (scrollY > 0 ? whole > oldZoom : whole < oldZoom)) newZoom = whole;

        float frameX = toFrameX(mouseX);
        float frameY = toFrameY(mouseY);

        zoom = newZoom;
        panX = (float) (mouseX - getX()) - frameX * zoom;
        panY = (float) (mouseY - areaTop()) - frameY * zoom;

        return true;
    }

    // ***************************************************************
    // Container
    // ***************************************************************

    @Override
    public @NonNull List<AbstractWidget> children() {
        return List.of(presetCombo, snapCheckBox);
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }

    /// A window size the frame can take.
    ///
    /// @param width  in GUI pixels
    /// @param height in GUI pixels
    private record FramePreset(String label, int width, int height) {
    }

    /// A preview widget and the holder it was built from.
    ///
    /// @param parent  the widget laying it out: the preview root or a container
    /// @param movable whether it sits straight under the root, so a drag moves and resizes it
    /// @param depth   how many containers it sits in
    private record Placed(AbstractWidget widget, ComponentDataHolder<?> holder, AkytheraWidget parent, boolean movable, int depth) {
    }

    /// What a press is dragging.
    private sealed interface Drag {
        /// The view, by `button`, from a press at `pressX`, `pressY` in screen pixels.
        record Pan(int button, double pressX, double pressY) implements Drag {
        }

        /// The frame by `edges`, from its size and the view's pan at the press.
        record Frame(int edges, ScreenRectangle start, float panX, float panY, double pressX, double pressY) implements Drag {
        }

        /// A widget by `edges`, or as a whole for none, from its rect at the press, in GUI pixels.
        record Reshape(Placed placed, int edges, ScreenRectangle start, double pressX, double pressY) implements Drag {
        }
    }

    // ***************************************************************
    // [AnchoredWidget] implementations
    // ***************************************************************
    private Unit x1;
    private Unit x2;
    private Unit y1;
    private Unit y2;
    private HorizontalAnchorType horizontalAnchorType;
    private VerticalAnchorType verticalAnchorType;

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
