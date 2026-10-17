package com.yesman.akytheralabs.gui.widget.animation;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.api.gui.ContextMenuContainer;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akytheralabs.akythera.animation.AnimationMontageHolder;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/// Montage-only row: the green animation segment blocks. Drawn as TWO stacked lanes
/// (TOP / BOTTOM) that read as one merged double-height cell — even-index segments
/// live in the TOP lane, odd-index in the BOTTOM lane (the alternating look from the
/// UE montage track). Each block is its own [SegmentBlockWidget] child.
///
/// Independent of the sections row. Dragging a block onto another's spot reorders the
/// segment list ([AnimationMontageHolder#moveSegment]); the merged preview clip
/// hot-reloads off the holder's dirty flag.
public final class SegmentRow extends TimelineTrackEntry {
    private static final int BLOCK_COLOR = 0xFF5E9B43;
    private static final int BLOCK_COLOR_SELECTED = 0xFF6FA84F;
    private static final int BLOCK_COLOR_GHOST = 0xCC7ABF55;
    private static final int LABEL_COLOR = 0xFF10240A;
    private static final int INSERT_MARK_COLOR = 0xFFFFE066;

    private final AnimationTrackEditorPanel parent;
    private final AnimationMontageHolder montage;
    private final Lane lane;

    /// Asks the screen to open the Asset Browser tab (4f). May be a no-op.
    private final Runnable onSelected;
    private final Interaction interaction;
    private final List<SegmentBlockWidget> blocks = new ArrayList<>();

    /// Right-click a block → "Remove Segment". Target index captured at menu-open.
    private final ContextMenuContainer blockMenu;
    private int menuIndex = -1;

    public SegmentRow(
        AnimationTrackEditorPanel parent,
        AnimationMontageHolder montage,
        Lane lane,
        Runnable onSelected,
        Interaction interaction
    ) {
        this.parent = parent;
        this.montage = montage;
        this.lane = lane;
        this.onSelected = onSelected;
        this.interaction = interaction;

        this.blockMenu = ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(140)
            .entry()
                .message(Component.translatable(LangKeys.GUI_MONTAGE_SEGMENT_CONTEXT_REMOVE))
                .onPress(_ -> {
                    if (menuIndex >= 0) removeSegment(menuIndex);
                })
                .done()
            .build();

        rebuildWidgets();
    }

    private boolean ownsIndex(int i) {
        return (i % 2 == 0) == (lane == Lane.TOP);
    }

    private void rebuildWidgets() {
        blocks.clear();

        for (int i = 0; i < montage.segmentCount(); i++) {
            if (ownsIndex(i)) blocks.add(new SegmentBlockWidget(i));
        }
    }

    @Override
    protected Component rowLabel() {
        // Bottom lane keeps a blank label so the two rows read as one merged cell.
        return lane == Lane.BOTTOM
            ? Component.translatable(LangKeys.GUI_ANIM_MONTAGE_SCREEN_ROW_SEGMENTS)
            : Component.empty();
    }

    /// The two lanes are separate list rows, so the list's per-row gap would split the
    /// name block into two cells. Bleed each lane's fill across that gap toward the shared
    /// seam (top lane extends DOWN, bottom lane extends UP) so they read as one merged
    /// double-height cell — while keeping the outer gap vs. the rows above/below.
    @Override
    public void extractContent(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a) {
        Font font = Minecraft.getInstance().font;
        int rowLeft = getContentX();
        int blockRight = rowLeft + TimelineList.NAME_BLOCK_W - (AnimationTrackEditorPanel.DIVIDER_W + 2);
        int rowBottom = getY() + getHeight();
        int top = lane == Lane.TOP ? getContentY() : getY();
        int bottom = lane == Lane.TOP ? rowBottom : getContentY() + getContentHeight();

        g.fill(rowLeft, top, blockRight, bottom, 0xFF363636);

        // Label only on the top lane, centered on the seam = middle of the merged cell.
        if (lane == Lane.BOTTOM) {
            g.text(font, rowLabel(), rowLeft + 8, getContentY() - font.lineHeight / 2 - 2, 0xFFCCCCCC, false);
        }
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return blocks;
    }

    @Override
    public @NonNull List<? extends NarratableEntry> narratables() {
        return List.of();
    }

    @Override
    protected void extractTrackArea(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int top = getContentY();
        int h = getContentHeight();

        for (SegmentBlockWidget w : blocks) w.updatePosition(top, h);
        for (SegmentBlockWidget w : blocks) w.extractRenderState(g, mouseX, mouseY, a);

        // The lane that owns the dragged block paints the floating ghost + insertion mark.
        if (interaction.dragging() && ownsIndex(interaction.draggingIndex)) {
            drawGhost(g, top, h);
        }
    }

    private void drawGhost(GuiGraphicsExtractor g, int top, int h) {
        List<TimeWindow> bounds = montage.getSegmentBounds();
        int i = interaction.draggingIndex;
        if (i >= bounds.size()) return;

        Font font = Minecraft.getInstance().font;
        int width = parent.frameToX(bounds.get(i).to() * 60.0F) - parent.frameToX(bounds.get(i).from() * 60.0F);
        int ghostX = (int) interaction.cursorX - interaction.grabOffsetX;

        g.fill(ghostX, top + 1, ghostX + Math.max(1, width - 1), top + h - 1, BLOCK_COLOR_GHOST);
        String label = font.plainSubstrByWidth(montage.getSegmentLabel(i), Math.max(0, width - 6));
        g.text(font, Component.literal(label), ghostX + 3, top + (h - font.lineHeight) / 2, LABEL_COLOR, false);

        int markX = parent.frameToX(insertSeconds(dropIndex(interaction.cursorX)) * 60.0F);
        g.fill(markX - 1, top, markX + 1, top + h, INSERT_MARK_COLOR);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.x() < parent.getTrackStart()) return false;

        for (SegmentBlockWidget w : blocks) {
            if (w.isMouseOver(event.x(), event.y()) && w.mouseClicked(event, doubleClick)) {
                if (event.button() == 0) {
                    setFocused(w);
                    setDragging(true);
                }
                return true;
            }
        }

        if (event.button() == 0) {
            parent.scrubTo(parent.xToFrame(event.x()));
            return true;
        }

        return false;
    }

    /// Insertion slot (0..n) for a screen-space x: how many segment centers sit left of it.
    private int dropIndex(double x) {
        List<TimeWindow> bounds = montage.getSegmentBounds();
        int slot = 0;

        for (TimeWindow w : bounds) {
            int centerX = parent.frameToX((w.from() + w.to()) / 2.0F * 60.0F);
            if (x > centerX) slot++;
        }

        return slot;
    }

    /// Start time (seconds) of the boundary at insertion slot `i` (for the insert mark).
    private float insertSeconds(int slot) {
        List<TimeWindow> bounds = montage.getSegmentBounds();
        if (bounds.isEmpty()) return 0.0F;
        if (slot <= 0) return bounds.getFirst().from();
        if (slot >= bounds.size()) return bounds.getLast().to();

        return bounds.get(slot).from();
    }

    private void commitDrag() {
        int from = interaction.draggingIndex;
        interaction.clearDrag();
        if (from < 0) return;

        int slot = dropIndex(interaction.cursorX);
        int to = slot > from ? slot - 1 : slot;
        to = Math.clamp(to, 0, Math.max(0, montage.segmentCount() - 1));

        if (to == from) {
            return;
        }

        montage.moveSegment(from, to);
        parent.rebuildEntries();

        final int movedFrom = from;
        final int movedTo = to;

        HistoryManager.getInstance().pushHistory(new WorkHistory() {
            @Override
            public void undo() { montage.moveSegment(movedTo, movedFrom); parent.rebuildEntries(); }

            @Override
            public void redo() { montage.moveSegment(movedFrom, movedTo); parent.rebuildEntries(); }
        });
    }

    /// DELETE key on the selected segment row → drop the shared selected block.
    @Override
    protected void deleteSelected() {
        if (interaction.selectedIndex >= 0) removeSegment(interaction.selectedIndex);
    }

    /// Remove the segment at `index` and record it for undo. Shared by the DELETE key and the
    /// right-click "Remove Segment" menu. Undo re-inserts the same clip at the same slot.
    private void removeSegment(int index) {
        if (index < 0 || index >= montage.segmentCount()) return;

        AssetAccessor<AnimationClip> clip = montage.getSegments().get(index);
        montage.removeSegment(index);
        interaction.clearDrag();
        interaction.selectedIndex = -1;
        parent.rebuildEntries();

        HistoryManager.getInstance().pushHistory(new WorkHistory() {
            @Override
            public void undo() { montage.insertSegment(index, clip); parent.rebuildEntries(); }

            @Override
            public void redo() { montage.removeSegment(index); parent.rebuildEntries(); }
        });
    }

    public enum Lane {
        TOP, BOTTOM
    }

    /// Cross-lane interaction state shared by the two lanes (a dragged block can land
    /// in either lane, and the selection highlight must survive on both).
    public static final class Interaction {
        int selectedIndex = -1;
        int draggingIndex = -1;
        double cursorX = 0;
        int grabOffsetX = 0;

        boolean dragging() {
            return draggingIndex >= 0;
        }

        void clearDrag() {
            draggingIndex = -1;
        }
    }

    /// One green segment block. Click selects + opens the Asset Browser tab; drag floats a
    /// ghost (no list churn) and reorders on release.
    private final class SegmentBlockWidget extends AbstractWidget {
        private final int index;

        SegmentBlockWidget(int index) {
            super(0, 0, 0, 0, Component.empty());
            this.index = index;
        }

        void updatePosition(int rowTop, int rowHeight) {
            List<TimeWindow> bounds = montage.getSegmentBounds();
            if (index >= bounds.size()) return;

            int x0 = parent.frameToX(bounds.get(index).from() * 60.0F);
            int x1 = parent.frameToX(bounds.get(index).to() * 60.0F);

            setX(x0);
            setY(rowTop + 1);
            setWidth(Math.max(1, x1 - x0 - 1));
            setHeight(rowHeight - 2);
        }

        @Override
        protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            Font font = Minecraft.getInstance().font;
            // While this block is the one being dragged, dim its static slot; the ghost shows the move.
            boolean dragged = interaction.draggingIndex == index;
            int color = dragged
                ? 0x66000000 | (BLOCK_COLOR & 0x00FFFFFF)
                : (interaction.selectedIndex == index ? BLOCK_COLOR_SELECTED : BLOCK_COLOR);

            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), color);

            if (!dragged) {
                String label = font.plainSubstrByWidth(montage.getSegmentLabel(index), Math.max(0, getWidth() - 6));
                g.text(font, Component.literal(label), getX() + 3, getY() + (getHeight() - font.lineHeight) / 2, LABEL_COLOR, false);
            }
        }

        @Override
        public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
            // Right-click → select this block and open its "Remove Segment" menu.
            if (event.button() == 1) {
                interaction.selectedIndex = index;
                menuIndex = index;
                parent.setSelectedRow(SegmentRow.this);
                ModalManager.getInstance().openModalWidget(blockMenu, (int) event.x(), (int) event.y());
                return true;
            }

            if (event.button() != 0) return false;

            interaction.selectedIndex = index;
            interaction.draggingIndex = index;
            interaction.cursorX = event.x();
            interaction.grabOffsetX = (int) event.x() - getX();
            // Mark this row selected so the DELETE key targets the right lane.
            parent.setSelectedRow(SegmentRow.this);
            onSelected.run();

            return true;
        }

        @Override
        public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
            if (event.button() != 0 || !interaction.dragging()) return false;

            interaction.cursorX = event.x();

            return true;
        }

        @Override
        public boolean mouseReleased(@NonNull MouseButtonEvent event) {
            if (event.button() == 0 && interaction.dragging()) {
                commitDrag();
            }

            return super.mouseReleased(event);
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput out) {
        }
    }
}