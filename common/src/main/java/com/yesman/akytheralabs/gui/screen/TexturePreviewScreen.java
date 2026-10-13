package com.yesman.akytheralabs.gui.screen;

import com.yesman.akythera.client.api.gui.WidgetTable;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalAccessibleScreen;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.texture.SpriteMetadataHolder;
import com.yesman.akytheralabs.dataholder.texture.SpriteScalingHolder;
import com.yesman.akytheralabs.dataholder.texture.TextureMetadataHolder;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.widget.NineSliceGuide;
import com.yesman.akytheralabs.gui.widget.PanelHeader;
import com.yesman.akytheralabs.gui.widget.PreviewTextureCache;
import com.yesman.akytheralabs.gui.widget.RefusalPopup;
import com.yesman.akytheralabs.gui.widget.TexturePreviewer;
import com.yesman.akytheralabs.gui.widget.main.ConsoleModal;
import com.yesman.akytheralabs.gui.widget.main.EditorPreferencesModalGenerator;
import com.yesman.akytheralabs.gui.widget.main.LocalizationManagerModal;
import com.yesman.akytheralabs.gui.widget.main.TopMenuBar;
import com.yesman.akytheralabs.gui.widget.main.TopMenuOption;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.metadata.gui.GuiMetadataSection;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// Previews a project texture beside the metadata its `.mcmeta` sidecar carries: a
/// [TextureMetadataSection] for a plain texture, a [GuiMetadataSection] for a GUI sprite. Saving
/// writes the sidecar, see [TextureMetadataHolder#save]. A nine-slice sprite's borders show over the
/// preview as [NineSliceGuide]s.
public final class TexturePreviewScreen extends Screen implements ModalAccessibleScreen, SafeCloseScreen {
    private static final int TOP_BAR_H = 22;
    private static final int ROW_H = 20;

    private final AdjustablePanelManager panelManager;
    private final TopMenuBar topMenuBar;
    private final TexturePreviewer previewer;
    private final PanelHeader detailsHeader;
    private final WidgetTable detailsTable;
    private final PreviewTextureCache textureCache = new PreviewTextureCache();
    private final TextureMetadataHolder<?> holder;

    /// Last rect the Details panel was laid out in, so a refill lands in place. Null until the first layout.
    private @Nullable ScreenRectangle detailsRect;

    private @Nullable WorkHistory savePoint;

    public TexturePreviewScreen(Path textureFile) {
        super(Component.translatable(LangKeys.GUI_TEXTURE_PREVIEW_SCREEN_TITLE));

        this.holder = TextureMetadataHolder.readFrom(textureFile);

        previewer = new TexturePreviewer(
            minecraft.font,
            () -> textureCache.get(textureFile),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        topMenuBar = new TopMenuBar(
            minecraft.font,
            Component.literal(Objects.toString(
                AkytheraProject.getCurrentProject().getTextureIdentifier(textureFile),
                textureFile.getFileName().toString()
            )),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(TOP_BAR_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> handleQuitRequest(),
            TopMenuBar.actions()
                .on(TopMenuOption.LOCALIZATION, () -> LocalizationManagerModal.open(minecraft.font))
                .on(TopMenuOption.SAVE, this::save)
                .on(TopMenuOption.EDITOR_PREFERENCES, () -> EditorPreferencesModalGenerator.open(font))
                .on(TopMenuOption.FRAME_CAMERA, previewer::frame)
                .on(TopMenuOption.CONSOLE, () -> ConsoleModal.open(font))
                .on(TopMenuOption.DOCS, () -> {})
        );

        detailsHeader = new PanelHeader(minecraft.font, Component.translatable(LangKeys.GUI_WIDGET_DETAILS));

        detailsTable = new WidgetTable(
            this,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(PanelHeader.DEFAULT_HEADER_HEIGHT + 2), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            ROW_H
        );

        fillDetails();

        // A sprite scaling as a nine-slice shows its borders as draggable lines
        if (holder instanceof SpriteMetadataHolder sprite) {
            for (NineSliceGuide.Edge edge : NineSliceGuide.Edge.values()) {
                previewer.addOverlay(new NineSliceGuide(
                    previewer,
                    () -> sprite.getScaling() instanceof SpriteScalingHolder.NineSlice nineSlice ? nineSlice : null,
                    edge,
                    this::fillDetails
                ));
            }
        }

        panelManager = new AdjustablePanelManager(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TOP_BAR_H), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        List<Integer> layoutRatios = AkytheraProject.getCurrentProject().getLayoutRatioOrDefault(getClass(), 70);

        var leftAndRight = panelManager.getRoot()
            .subdivide(1, AdjustablePanelManager.PanelDivider.Orientation.VERTICAL, layoutRatios.getFirst());

        leftAndRight.get(0).resizeCallback(previewer::relocate);
        leftAndRight.get(1).resizeCallback(rect -> {
            detailsRect = rect;
            detailsHeader.relocate(rect);
            detailsTable.relocate(rect);
        });

        topMenuBar.setDirtyChecker(this::hasUnsavedChanges);
        savePoint = HistoryManager.getInstance().currentSavePoint();
    }

    @Override
    protected void init() {
        super.init();

        var rect = getRectangle();
        topMenuBar.relocate(rect);
        panelManager.relocate(rect);

        addRenderableWidget(topMenuBar);
        addRenderableWidget(panelManager);
        addRenderableWidget(previewer);
        addRenderableWidget(detailsHeader);
        addRenderableWidget(detailsTable);
    }

    /// Fills the Details table from [#holder]. A write that swaps which rows exist refills it through here.
    private void fillDetails() {
        new EditorWidgetFactory(font).fillWidgetTable(detailsTable, holder, this::fillDetails);

        if (detailsRect != null) detailsTable.relocate(detailsRect);
    }

    /// Writes the sidecar. A section the game would refuse is not written, and the reason is shown.
    private boolean save() {
        String failure = holder.save();

        if (failure != null) {
            RefusalPopup.open(
                Component.translatable(LangKeys.GUI_TEXTURE_PREVIEW_SCREEN_MESSAGE_SAVE_FAILED),
                Component.literal(failure)
            );

            return false;
        }

        savePoint = HistoryManager.getInstance().currentSavePoint();

        return true;
    }

    private boolean hasUnsavedChanges() {
        return HistoryManager.getInstance().currentSavePoint() != savePoint;
    }

    @Override
    public void handleQuitRequest() {
        SafeCloseScreen.openQuiteScreen(
            font,
            Component.translatable(LangKeys.GUI_MESSAGE_ASK_TO_SAVE, topMenuBar.getMessage()),
            hasUnsavedChanges(),
            () -> {
                ModalManager.getInstance().closeModal();
                if (save()) onClose();
            },
            this::onClose
        );
    }

    @Override
    public void onClose() {
        textureCache.releaseAll();
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
        AbstractWidget modal = getHoveringModal(x, y);
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

    /// The modal owning focus, through [ModalAccessibleScreen#getFocusOwningModal], so focus is
    /// released when it closes.
    @Nullable
    public GuiEventListener getFocused() {
        AbstractWidget modal = getFocusOwningModal();
        if (modal != null) return modal;

        return super.getFocused();
    }
}
