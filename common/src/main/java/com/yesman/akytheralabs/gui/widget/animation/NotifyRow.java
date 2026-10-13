package com.yesman.akytheralabs.gui.widget.animation;

import com.yesman.akythera.client.api.gui.ContextMenuContainer;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.akythera.animation.NotifyTrackHolder;
import com.yesman.akytheralabs.akythera.animation.notify.AnimationNotifyHolder;
import com.yesman.akytheralabs.akythera.animation.notify.AnimationNotifyStateHolder;
import com.yesman.akytheralabs.dataholder.DataHolderMapper;
import com.yesman.akytheralabs.editor.historytrack.DraggingDataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.ListInsert;
import com.yesman.akytheralabs.editor.historytrack.ListRemove;
import com.yesman.akytheralabs.editor.historytrack.PendingDataChange;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.EditorEditBox;
import com.yesman.akytheralabs.gui.widget.main.RegistryLookupModal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// One notify-track row. Self-contained: owns its notify / notify-state widgets, its
/// inline rename editor, and its right-click menus (rename / remove track, add notify /
/// state). Built by the editor screens and handed to the generic [AnimationTrackList]
/// through the host panel's row builder.
public final class NotifyRow extends AnimTrackEntry {
    final AnimationTrackEditorPanel parent;
    final NotifyTrackHolder.Track track;
    final List<NotifyWidget> notifyWidgets = new ArrayList<>();
    final List<NotifyStateWidget> notifyStateWidgets = new ArrayList<>();

    private final EditorEditBox<String> nameEditor;
    private boolean editing;

    private final ContextMenuContainer trackNameMenu;
    private final ContextMenuContainer trackSheetMenu;

    /// Captured at sheet-menu open so the (once-built) menu entries know where to act.
    private float sheetMenuFrame;
    private int sheetMenuX;
    private int sheetMenuY;

    public NotifyRow(AnimationTrackEditorPanel parent, NotifyTrackHolder.Track track) {
        this.parent = parent;
        this.track = track;

        this.nameEditor = EditorEditBox.stringBox(
            Minecraft.getInstance().font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(AnimationTrackList.ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            _ -> {},
            Component.empty()
        );
        nameEditor.visible = false;

        this.trackNameMenu = buildNameMenu();
        this.trackSheetMenu = buildSheetMenu();

        rebuildWidgets();
    }

    public void rebuildWidgets() {
        notifyWidgets.clear();
        notifyStateWidgets.clear();
        for (var n : track.notifies) notifyWidgets.add(new NotifyWidget(parent, track, n));
        for (var ns : track.notifyStates) notifyStateWidgets.add(new NotifyStateWidget(parent, track, ns));
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        List<GuiEventListener> all = new ArrayList<>(notifyWidgets.size() + notifyStateWidgets.size());
        all.addAll(notifyStateWidgets);
        all.addAll(notifyWidgets);
        return all;
    }

    @Override
    protected Component rowLabel() {
        return Component.literal(track.name);
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
        startEditing();
    }

    @Override
    public void extractContent(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a) {
        var font = Minecraft.getInstance().font;
        int rowLeft = getContentX();
        int rowTop = getContentY();
        int rowHeight = getContentHeight();
        int blockRight = rowLeft + AnimationTrackList.NAME_BLOCK_W - (AnimationTrackEditorPanel.DIVIDER_W + 2);

        // Track name block (left fixed pane)
        boolean isSelected = parent.selectedRow() == this;
        int blockBg = isSelected ? 0xFF2A4060 : (hovered && mouseX < blockRight ? 0xFF4A4A4A : 0xFF363636);
        g.fill(rowLeft, rowTop, blockRight, rowTop + rowHeight, blockBg);

        if (!editing) {
            g.text(font, track.name, rowLeft + 8, rowTop + (rowHeight - font.lineHeight) / 2, 0xFFCCCCCC, false);
        } else {
            nameEditor.setX(rowLeft + 4);
            nameEditor.setY(rowTop + (rowHeight - 14) / 2);
            nameEditor.setWidth(AnimationTrackList.NAME_BLOCK_W - 12);
            nameEditor.setHeight(14);
            nameEditor.extractRenderState(g, mouseX, mouseY, a);
        }
    }

    /// Draw notifies at last so that measures don't overlap notify widgets
    @Override
    protected void extractTrackArea(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int rowTop = getContentY();
        int rowHeight = getContentHeight();

        // Position widgets first (so click hit-testing later in the same frame
        // sees them in their rendered locations), then render.
        for (NotifyStateWidget w : notifyStateWidgets) w.updatePosition(rowTop, rowHeight);
        for (NotifyWidget w : notifyWidgets) w.updatePosition(rowTop, rowHeight);
        for (NotifyStateWidget w : notifyStateWidgets) w.extractRenderState(g, mouseX, mouseY, a);
        for (NotifyWidget w : notifyWidgets) w.extractRenderState(g, mouseX, mouseY, a);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        boolean inNameBlock = event.x() < getContentX() + AnimationTrackList.NAME_BLOCK_W;

        if (event.button() == 1) {
            if (inNameBlock) openTrackNameMenu(event);
            else openSheetMenu(event);
            return true;
        }

        if (event.button() == 0) {
            if (inNameBlock) {
                if (doubleClick) {
                    startEditing();
                    return true;
                }

                parent.setSelectedRow(this);
                return true;
            }

            // Handle mouse press of notifies
            for (NotifyWidget w : notifyWidgets) {
                if (w.isMouseOver(event.x(), event.y()) && w.mouseClicked(event, doubleClick)) {
                    setFocused(w); setDragging(true);
                    return true;
                }
            }

            for (NotifyStateWidget w : notifyStateWidgets) {
                if (w.isMouseOver(event.x(), event.y()) && w.mouseClicked(event, doubleClick)) {
                    setFocused(w); setDragging(true);
                    return true;
                }
            }

            // Empty space click → place playhead and clear selection
            parent.scrubTo(parent.xToFrame(event.x()));
            parent.setSelected(null);
            return true;
        }

        return false;
    }

    void startEditing() {
        editing = true;
        nameEditor.setValue(track.name);
        nameEditor.setCursorPosRightmost();
        nameEditor.visible = true;
        nameEditor.setFocused(true);
        nameEditor.setOnCommitted((_, _) -> stopEditing());
        parent.setSelectedRow(this);

        HistoryManager.getInstance().pushHistory(new PendingDataChange<>(
            track, track.name, v -> track.name = v
        ));
    }

    void stopEditing() {
        if (!editing) return;

        String name = nameEditor.getText();
        if (!name.isBlank()) track.name = name;

        editing = false;
        nameEditor.setFocused(false);
        nameEditor.visible = false;

        HistoryManager.getInstance().pushHistory(new DraggingDataChange<>(
            track, track.name, v -> track.name = v
        ));
    }

    private void openTrackNameMenu(MouseButtonEvent event) {
        parent.setSelectedRow(this);
        ModalManager.getInstance().openModalWidget(trackNameMenu, (int) event.x(), (int) event.y());
    }

    /// Capture where the right-click hit so the "Add Notify Here" callback knows the
    /// frame and where the follow-up lookup modal should pop up.
    private void openSheetMenu(MouseButtonEvent event) {
        sheetMenuFrame = parent.xToFrame(event.x());
        sheetMenuX = (int) event.x();
        sheetMenuY = (int) event.y();
        ModalManager.getInstance().openModalWidget(trackSheetMenu, sheetMenuX, sheetMenuY);
    }

    private ContextMenuContainer buildNameMenu() {
        return ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(120)
            .entry()
                .message(Component.translatable(LangKeys.GUI_NOTIFY_TRACKS_CONTEXT_RENAME))
                .onPress(_ -> startEditing())
                .done()
            .entry()
                .message(Component.translatable(LangKeys.GUI_NOTIFY_TRACKS_CONTEXT_REMOVE_TRACK))
                .onPress(_ -> {
                    NotifyTrackHolder holder = parent.holder();
                    int index = holder.getTracks().indexOf(track);

                    holder.removeTrack(track);
                    parent.rebuildEntries();

                    HistoryManager.getInstance().pushHistory(new ListRemove<>(
                        holder.getTracks(), index, track, parent::rebuildEntries
                    ));
                })
                .done()
            .build();
    }

    private ContextMenuContainer buildSheetMenu() {
        return ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(160)
            .entry()
                .message(Component.translatable(LangKeys.GUI_NOTIFY_TRACKS_CONTEXT_ADD_NOTIFY))
                .onPress(_ -> openTypePicker(
                    Component.translatable(LangKeys.GUI_NOTIFY_TRACKS_SELECT_NOTIFY_TYPE),
                    AkytheraRegistries.Frozen.ANIM_NOTIFY_TYPES,
                    mapCodec -> {
                        Supplier<AnimationNotifyHolder<?>> factory = DataHolderMapper.notifyHolderFor(mapCodec);

                        if (factory == null) {
                            AkytheraLabs.LOGGER.warn("No holder factory registered for notify {}", mapCodec);
                            return;
                        }

                        NotifyTrackHolder.MutableNotify added = new NotifyTrackHolder.MutableNotify(factory.get(), sheetMenuFrame / 60.0F);
                        track.notifies.add(added);
                        rebuildWidgets();

                        HistoryManager.getInstance().pushHistory(new ListInsert<>(
                            track.notifies, track.notifies.size() - 1, added, parent::rebuildEntries
                        ));
                    }
                ))
                .done()
            .entry()
                .message(Component.translatable(LangKeys.GUI_NOTIFY_TRACKS_CONTEXT_ADD_NOTIFY_STATE))
                .onPress(_ -> openTypePicker(
                    Component.translatable(LangKeys.GUI_NOTIFY_TRACKS_SELECT_NOTIFY_STATE_TYPE),
                    AkytheraRegistries.Frozen.ANIM_NOTIFY_STATE_TYPES,
                    mapCodec -> {
                        Supplier<AnimationNotifyStateHolder<?>> factory = DataHolderMapper.notifyStateHolderFor(mapCodec);

                        if (factory == null) {
                            AkytheraLabs.LOGGER.warn("No holder factory registered for notify-state {}", mapCodec);
                            return;
                        }

                        NotifyTrackHolder.MutableNotifyState added = new NotifyTrackHolder.MutableNotifyState(
                            factory.get(), sheetMenuFrame / 60.0F,
                            (sheetMenuFrame + Math.max(
                                (parent.visualFrameEnd() - parent.visualFrameStart()) * 0.1F,
                                NotifyStateWidget.MIN_STATE_FRAMES
                            )) / 60.0F
                        );

                        track.notifyStates.add(added);
                        rebuildWidgets();

                        HistoryManager.getInstance().pushHistory(new ListInsert<>(
                            track.notifyStates, track.notifyStates.size() - 1, added, parent::rebuildEntries
                        ));
                    }
                ))
                .done()
            .entry()
                .predicate(() -> parent.getSelected() != null)
                .message(Component.translatable(LangKeys.GUI_NOTIFY_TRACKS_CONTEXT_REMOVE_NOTIFY))
                .onPress(_ -> removeSelectedNotify())
                .done()
            .build();
    }

    private void removeSelectedNotify() {
        var ref = parent.getSelected();

        if (ref instanceof NotifyTrackHolder.NotifyRef.Point(
            NotifyTrackHolder.Track t, NotifyTrackHolder.MutableNotify mutableNotify
        )) {
            int index = t.notifies.indexOf(mutableNotify);
            t.notifies.remove(mutableNotify);

            HistoryManager.getInstance().pushHistory(new ListRemove<>(
                t.notifies, index, mutableNotify, parent::rebuildEntries
            ));
        } else if (ref instanceof NotifyTrackHolder.NotifyRef.State(
            NotifyTrackHolder.Track t, NotifyTrackHolder.MutableNotifyState mutableNotifyState
        )) {
            int index = t.notifyStates.indexOf(mutableNotifyState);
            t.notifyStates.remove(mutableNotifyState);

            HistoryManager.getInstance().pushHistory(new ListRemove<>(
                t.notifyStates, index, mutableNotifyState, parent::rebuildEntries
            ));
        }

        parent.setSelected(null);
        parent.rebuildEntries();
    }

    private <T> void openTypePicker(Component title, Registry<T> registry, Consumer<T> onPick) {
        RegistryLookupModal<T> modal = new RegistryLookupModal<>(Minecraft.getInstance().font, title, registry, onPick);
        modal.setX1(AnchoredWidget.pixel(sheetMenuX));
        modal.setY1(AnchoredWidget.pixel(sheetMenuY));

        ModalManager.getInstance().openModalWidget(modal, sheetMenuX, sheetMenuY);
    }
}