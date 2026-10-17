package com.yesman.akytheralabs.gui.widget.animation;

import com.yesman.akythera.client.api.gui.ContextMenuContainer;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.mixinaccessor.client.GuiGraphicsAccessor;
import com.yesman.akytheralabs.akythera.animation.AnimationMontageHolder;
import com.yesman.akytheralabs.editor.historytrack.DraggingDataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.PendingDataChange;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.EditorEditBox;
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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// Montage-only row: the section indicators (blue chips). Each section is its own
/// draggable [SectionIndicatorWidget] child (owned via [#children]), so this row is
/// a real parent→child container like [NotifyRow].
///
/// Sections can be renamed inline (double-click a chip, or the RENAME key on the
/// selected section) — the edit box overlays the chip just like the content browser's
/// rename, and the change is undoable. The blue start-edge is drawn down the *whole*
/// track (deferred to the top), not just this row, so it reads as a section boundary.
///
/// Independent of the segments row — sections are just named time-stamps on the
/// merged timeline ([AnimationMontageHolder#getSections]).
public final class SectionRow extends TimelineTrackEntry {
    private static final int SNAP_PX = 6;
    private static final int CHIP_H = 20;
    /// Horizontal padding added to a name's text width to size both a chip and its rename box.
    private static final int CHIP_TEXT_PAD = 10;
    /// Floor so an empty/short name still leaves a grabbable rename box.
    private static final int MIN_EDIT_BOX_W = 16;
    private static final int EDGE_COLOR = 0xFF5C7CFF;
    private static final int CHIP_COLOR = 0xCC3A4FA0;
    private static final int CHIP_COLOR_SELECTED = 0xFF5066C0;

    private final AnimationTrackEditorPanel parent;
    private final AnimationMontageHolder montage;

    /// Asks the screen to open the Montage Sections tab (4f). May be a no-op.
    private final Runnable onSelected;

    /// Notifies the screen that the section set/names changed, so the Montage Sections
    /// page can reload its combo chain (add / delete / rename, plus their undo/redo).
    private final Runnable onSectionsChanged;

    private final List<SectionIndicatorWidget> indicators = new ArrayList<>();

    /// Inline rename box, shared across chips (only one section edits at a time).
    private final EditorEditBox<String> nameEditor;
    private boolean editing;
    private @Nullable String editingName;

    /// Right-click a chip → delete enabled. Right-click empty → add here + delete disabled.
    private final ContextMenuContainer chipMenu;
    private final ContextMenuContainer emptyMenu;

    /// Captured at menu-open so the (once-built) menu entries know their target.
    private @Nullable String menuSection;
    private float menuFrameSeconds;

    private @Nullable String selected;

    public SectionRow(AnimationTrackEditorPanel parent, AnimationMontageHolder montage, Runnable onSelected, Runnable onSectionsChanged) {
        this.parent = parent;
        this.montage = montage;
        this.onSelected = onSelected;
        this.onSectionsChanged = onSectionsChanged;

        Minecraft mc = Minecraft.getInstance();

        this.nameEditor = EditorEditBox.stringBox(
            mc.font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(CHIP_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            _ -> {},
            Component.empty()
        );
        nameEditor.visible = false;

        this.chipMenu = ContextMenuContainer.builder(mc).pageWidth(140)
            .entry()
                .message(Component.translatable(LangKeys.GUI_MONTAGE_SECTION_CONTEXT_RENAME))
                .onPress(_ -> {
                    if (menuSection != null) startEditing(menuSection);
                })
                .done()
            .entry()
                .message(Component.translatable(LangKeys.GUI_MONTAGE_SECTION_CONTEXT_DELETE))
                .onPress(_ -> {
                    if (menuSection != null) deleteSection(menuSection);
                })
                .done()
            .build();

        this.emptyMenu = ContextMenuContainer.builder(mc).pageWidth(140)
            .entry()
                .message(Component.translatable(LangKeys.GUI_MONTAGE_SECTION_CONTEXT_ADD_HERE))
                .onPress(_ -> addSection(menuFrameSeconds))
                .done()
            .entry()
                .predicate(() -> false) // delete is meaningless on empty space → disabled
                .message(Component.translatable(LangKeys.GUI_MONTAGE_SECTION_CONTEXT_DELETE))
                .done()
            .build();

        rebuildWidgets();
    }

    private void rebuildWidgets() {
        indicators.clear();
        for (String name : montage.getSections().keySet()) {
            indicators.add(new SectionIndicatorWidget(name));
        }
    }

    @Override
    protected Component rowLabel() {
        return Component.translatable(LangKeys.GUI_ANIM_MONTAGE_SCREEN_ROW_SECTIONS);
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return indicators;
    }

    @Override
    public @NonNull List<? extends NarratableEntry> narratables() {
        return List.of();
    }

    @Override
    @Nullable
    protected AbstractWidget activeInlineEditor() {
        return editing ? nameEditor : null;
    }

    @Override
    protected void commitInlineEdit() {
        stopEditing();
    }

    @Override
    protected void beginInlineEdit() {
        if (selected != null) startEditing(selected);
    }

    @Override
    protected void extractTrackArea(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int top = getContentY();
        int h = getContentHeight();
        for (SectionIndicatorWidget w : indicators) w.updatePosition(top);
        for (SectionIndicatorWidget w : indicators) w.extractRenderState(g, mouseX, mouseY, a);

        // Inline rename box overlays the chip being edited.
        if (editing && editingName != null) {
            Font font = Minecraft.getInstance().font;
            int boxW = Math.max(font.width(nameEditor.getText()) + CHIP_TEXT_PAD, MIN_EDIT_BOX_W);

            for (SectionIndicatorWidget w : indicators) {
                if (w.name.equals(editingName)) {
                    nameEditor.setX(w.getX());
                    nameEditor.setY(w.getY() + 1);
                    nameEditor.setWidth(boxW - 1);
                    nameEditor.setHeight(CHIP_H - 2);
                    nameEditor.extractRenderState(g, mouseX, mouseY, a);
                    break;
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        // Name block clicks fall through to the list's default handling.
        if (event.x() < parent.getTrackStart()) return false;

        for (SectionIndicatorWidget w : indicators) {
            if (w.isMouseOver(event.x(), event.y()) && w.mouseClicked(event, doubleClick)) {
                if (event.button() == 0) {
                    setFocused(w);
                    setDragging(true);
                }
                return true;
            }
        }

        // Empty track area.
        if (event.button() == 1) {
            menuSection = null;
            menuFrameSeconds = Math.max(0.0F, parent.xToFrame(event.x()) / 60.0F);
            ModalManager.getInstance().openModalWidget(emptyMenu, (int) event.x(), (int) event.y());
            return true;
        }

        if (event.button() == 0) {
            parent.scrubTo(parent.xToFrame(event.x()));
            return true;
        }

        return false;
    }

    /// Snap a time (seconds) to the nearest segment edge if within [#SNAP_PX] on screen.
    private float snapToSegmentEdges(float seconds) {
        int curX = parent.frameToX(seconds * 60.0F);
        float best = seconds;
        int bestDist = SNAP_PX;

        for (TimeWindow window : montage.getSegmentBounds()) {
            for (float edgeSec : new float[]{window.from(), window.to()}) {
                int dist = Math.abs(parent.frameToX(edgeSec * 60.0F) - curX);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = edgeSec;
                }
            }
        }

        return best;
    }

    /// Rebuild the track rows AND the Montage Sections combo chain — used wherever the
    /// section set or names change (add / delete / rename + their undo/redo).
    private void refreshAll() {
        parent.rebuildEntries();
        onSectionsChanged.run();
    }

    private void addSection(float seconds) {
        String name = montage.addSectionAt(seconds);
        refreshAll();

        HistoryManager.getInstance().pushHistory(new WorkHistory() {
            @Override public void undo() { montage.removeSection(name); refreshAll(); }
            @Override public void redo() { montage.getSections().put(name, seconds); refreshAll(); }
        });
    }

    private void deleteSection(String name) {
        Float oldTime = montage.getSections().get(name);
        if (oldTime == null) return;

        List<List<String>> orderSnapshot = new ArrayList<>();
        for (List<String> seq : montage.getPlayingOrders()) orderSnapshot.add(new ArrayList<>(seq));
        montage.removeSection(name);
        refreshAll();

        HistoryManager.getInstance().pushHistory(new WorkHistory() {
            @Override
            public void undo() {
                montage.getSections().put(name, oldTime);
                montage.getPlayingOrders().clear();
                for (List<String> seq : orderSnapshot) montage.getPlayingOrders().add(new ArrayList<>(seq));
                refreshAll();
            }

            @Override
            public void redo() {
                montage.removeSection(name);
                refreshAll();
            }
        });
    }

    /// Open the inline rename box for a section. Targets the chip with this name.
    private void startEditing(String name) {
        if (!montage.getSections().containsKey(name)) return;

        editing = true;
        editingName = name;
        selected = name;
        parent.setSelectedRow(this);

        nameEditor.setValue(name);
        nameEditor.setCursorPosRightmost();
        nameEditor.visible = true;
        nameEditor.setFocused(true);
        nameEditor.setOnCommitted((_, _) -> stopEditing());
    }

    /// Commit the rename. Blank / unchanged / colliding names are dropped (no-op).
    private void stopEditing() {
        if (!editing) return;

        String oldName = editingName;
        String newName = nameEditor.getText();

        editing = false;
        editingName = null;
        nameEditor.setFocused(false);
        nameEditor.visible = false;

        if (oldName == null || !montage.renameSection(oldName, newName)) return;

        selected = newName;
        rebuildWidgets();
        onSectionsChanged.run();

        HistoryManager.getInstance().pushHistory(new WorkHistory() {
            @Override public void undo() { montage.renameSection(newName, oldName); refreshAll(); }
            @Override public void redo() { montage.renameSection(oldName, newName); refreshAll(); }
        });
    }

    /// One section's blue indicator: a full-track-height edge line + a clickable label chip.
    /// The chip is the drag/click handle; dragging moves the section's start time.
    private final class SectionIndicatorWidget extends AbstractWidget {
        private final String name;

        SectionIndicatorWidget(String name) {
            super(0, 0, 0, CHIP_H, Component.empty());
            this.name = name;
        }

        void updatePosition(int rowTop) {
            Float sec = montage.getSections().get(name);
            if (sec == null) return;

            Font font = Minecraft.getInstance().font;
            setX(parent.frameToX(sec * 60.0F));
            setY(rowTop + 2);
            setWidth(font.width(name) + CHIP_TEXT_PAD);
            setHeight(CHIP_H);
        }

        @Override
        protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            int x = getX();

            // The blue start-edge spans the whole track, not just this row. Defer it to the
            // top (drawn at the frame's tail) so it sits above every row's content below.
            int trackTop = parent.getY() + AnimationTrackEditorPanel.HEADER_H;
            int trackBottom = parent.getBottom() - AnimationTrackEditorPanel.FOOTER_H;
            ((GuiGraphicsAccessor) g).akythera$setDeferredWidgetRenderer(
                () -> g.fill(x, trackTop, x + 1, trackBottom, EDGE_COLOR)
            );

            Font font = Minecraft.getInstance().font;
            boolean isEditing = editing && name.equals(editingName);

            if (isEditing) {
                setWidth(font.width(nameEditor.getText()) + CHIP_TEXT_PAD);
            }

            g.fill(x, getY(), x + getWidth(), getY() + getHeight(), name.equals(selected) ? CHIP_COLOR_SELECTED : CHIP_COLOR);
            if (!isEditing) g.text(font, Component.literal(name), x + CHIP_TEXT_PAD / 2, getY() + (getHeight() - font.lineHeight) / 2, 0xFFFFFFFF, false);
        }

        @Override
        public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == 0) {
                if (doubleClick) {
                    startEditing(name);
                    return true;
                }

                selected = name;
                parent.setSelectedRow(SectionRow.this);
                onSelected.run();

                Float cur = montage.getSections().get(name);

                if (cur != null) {
                    HistoryManager.getInstance().pushHistory(new PendingDataChange<>(name, cur, v -> montage.setSectionTime(name, v)));
                }

                return true;
            }

            if (event.button() == 1) {
                menuSection = name;
                ModalManager.getInstance().openModalWidget(chipMenu, (int) event.x(), (int) event.y());
                return true;
            }

            return false;
        }

        @Override
        public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
            if (event.button() != 0 || editing) return false;

            float sec = Math.max(0.0F, parent.xToFrame(event.x()) / 60.0F);
            montage.setSectionTime(name, snapToSegmentEdges(sec));

            return true;
        }

        @Override
        public boolean mouseReleased(@NonNull MouseButtonEvent event) {
            if (event.button() == 0 && !editing) {
                Float cur = montage.getSections().get(name);

                if (cur != null) {
                    HistoryManager.getInstance().pushHistory(new DraggingDataChange<>(name, cur, v -> montage.setSectionTime(name, v)));
                }
            }

            return super.mouseReleased(event);
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput out) {
        }
    }
}