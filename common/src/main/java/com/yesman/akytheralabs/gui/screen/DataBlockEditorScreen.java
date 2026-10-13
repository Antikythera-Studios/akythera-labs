package com.yesman.akytheralabs.gui.screen;

import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalAccessibleScreen;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.DataBlockHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.panel.components.ComponentDetailsPanel;
import com.yesman.akytheralabs.gui.widget.AwaitIconModal;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import com.yesman.akytheralabs.gui.widget.main.ConsoleModal;
import com.yesman.akytheralabs.gui.widget.main.ContentWidget;
import com.yesman.akytheralabs.gui.widget.main.EditorPreferencesModalGenerator;
import com.yesman.akytheralabs.gui.widget.main.LanguageSelectionModal;
import com.yesman.akytheralabs.gui.widget.main.LocalizationManagerModal;
import com.yesman.akytheralabs.gui.widget.main.TopMenuBar;
import com.yesman.akytheralabs.gui.widget.main.TopMenuOption;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/// Editor screen for one [EnumerableDataBlock] entry that carries properties — a single Details panel
/// over the holder's [EditorEditable] rows.
///
/// A property-less type never reaches here: [ContentWidget] only opens a data block whose holder
/// declares editor fields. The entry's identifier is owned by the explorer (a rename re-keys the
/// registry), so this screen edits nothing but the values.
public final class DataBlockEditorScreen extends Screen implements ModalAccessibleScreen, SafeCloseScreen {
    private static final int TOP_BAR_H = 22;

    private final AdjustablePanelManager panelManager;
    private final TopMenuBar topMenuBar;
    private final ComponentDetailsPanel detailsPanel;

    private final ResourceKey<? extends EnumerableDataBlock> resourceKey;

    /// History stack top at the last save (or screen open); the title shows a `*` while the live top
    /// differs from it.
    private @Nullable WorkHistory savePoint;

    public DataBlockEditorScreen(
        ResourceKey<? extends EnumerableDataBlock> resourceKey, DataBlockHolder<?> holder
    ) {
        super(Component.literal(resourceKey.identifier().toString()));

        this.resourceKey = resourceKey;

        topMenuBar = new TopMenuBar(
            minecraft.font,
            Component.literal(resourceKey.identifier().toString()),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(TOP_BAR_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> handleQuitRequest(),
            TopMenuBar.actions()
                .on(TopMenuOption.LOCALIZATION, () -> LocalizationManagerModal.open(minecraft.font))
                .on(TopMenuOption.SAVE, this::save)
                .on(TopMenuOption.LANGUAGE, () -> LanguageSelectionModal.open(minecraft.font))
                .on(TopMenuOption.EDITOR_PREFERENCES, () -> EditorPreferencesModalGenerator.open(minecraft.font))
                .on(TopMenuOption.CONSOLE, () -> ConsoleModal.open(minecraft.font))
                .on(TopMenuOption.DOCS, () -> {})
                .on(TopMenuOption.ISSUE_REPORT, () -> {})
        );

        detailsPanel = new ComponentDetailsPanel(minecraft.font, this);
        detailsPanel.showComponent(holder);

        panelManager = new AdjustablePanelManager(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TOP_BAR_H), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );
        panelManager.getRoot().area(detailsPanel);

        topMenuBar.setDirtyChecker(this::hasUnsavedChanges);
        savePoint = HistoryManager.getInstance().currentSavePoint();
    }

    // ***************************************************************
    // Widget registration / layout
    // ***************************************************************

    @Override
    protected void init() {
        super.init();

        var rect = getRectangle();
        topMenuBar.relocate(rect);
        panelManager.relocate(rect);

        addRenderableWidget(topMenuBar);
        addRenderableWidget(panelManager);
    }

    // ***************************************************************
    // Save
    // ***************************************************************

    /// Rows write straight to the holder, so there is nothing to flush before the disk write.
    private CompletableFuture<Void> save() {
        WidgetContainerModal awaitModal = AwaitIconModal.openSaving(minecraft.font);

        return AkytheraProject.getCurrentProject().saveEntryAsync(resourceKey)
            .whenCompleteAsync((_, error) -> {
                if (ModalManager.getInstance().getModal() == awaitModal) {
                    ModalManager.getInstance().closeModal();
                }

                if (error != null) {
                    AkytheraLabs.LOGGER.error("Failed to save data block '{}'", resourceKey.identifier(), error);
                    return;
                }

                savePoint = HistoryManager.getInstance().currentSavePoint();
            }, minecraft);
    }

    private boolean hasUnsavedChanges() {
        return HistoryManager.getInstance().currentSavePoint() != savePoint;
    }

    // ***************************************************************
    // Screen lifecycle / input — mirrors ItemPatchEditorScreen
    // ***************************************************************

    @Override
    public void handleQuitRequest() {
        SafeCloseScreen.openQuiteScreen(
            minecraft.font,
            Component.translatable(LangKeys.GUI_MESSAGE_ASK_TO_SAVE, topMenuBar.getMessage()),
            hasUnsavedChanges(),
            () -> {
                ModalManager.getInstance().closeModal();
                save().thenRun(this::onClose);
            },
            () -> {
                if (hasUnsavedChanges()) AkytheraProject.getCurrentProject().reloadEntryFromDisk(resourceKey);
                onClose();
            }
        );
    }

    @Override
    public void onClose() {
        AkytheraProject.getCurrentProject().saveLayoutOfScreen(this, panelManager.serializePanelDividerPositions());
        AkytheraEditorLayoutManager.getInstance().removeLastScreen();
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // Draw nothing
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        AkytheraEditorLayoutManager.blurFocusChainLeaf(this, event.x(), event.y());
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && shouldCloseOnEsc()) {
            handleQuitRequest();
            return true;
        }
        if (EditorKeyMappings.SAVE.matches(event)) {
            save();
            return true;
        }
        if (EditorKeyMappings.REDO.matches(event)) {
            HistoryManager.getInstance().redo();
            return true;
        }
        if (EditorKeyMappings.UNDO.matches(event)) {
            HistoryManager.getInstance().undo();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public @NonNull Optional<GuiEventListener> getChildAt(double x, double y) {
        var modal = getHoveringModal(x, y);
        if (modal != null) return Optional.of(modal);
        return super.getChildAt(x, y);
    }

    @Override
    public void setFocused(@Nullable GuiEventListener g) {
        if (isModal(g)) {
            if (g != null) g.setFocused(true);
        } else {
            super.setFocused(g);
        }
    }

    @Override
    public @Nullable GuiEventListener getFocused() {
        var modal = ModalManager.getInstance().getInputModal();
        if (modal != null) return modal;
        return super.getFocused();
    }
}
