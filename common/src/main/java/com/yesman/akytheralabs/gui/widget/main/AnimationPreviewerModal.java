package com.yesman.akytheralabs.gui.widget.main;

import com.mojang.blaze3d.platform.Window;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.api.mesh.MeshManager;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.gui.widget.CloseableWidget;
import com.yesman.akythera.client.gui.widget.ModelPreviewer;
import com.yesman.akythera.client.gui.widget.ModelPreviewer.CameraComponent;
import com.yesman.akythera.client.gui.widget.ModelPreviewer.EntityModelComponent;
import com.yesman.akythera.client.gui.widget.ResizeCompleteCallbackListener;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.AdvancedEditBox;
import com.yesman.akytheralabs.gui.widget.Modal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Modal that previews a preloaded [AnimationClip] on a model the user picks from the left list.
///
/// Opened by double-clicking an `assets/animations/*` entry in the content browser. The clip is
/// passed in by the caller; the user only chooses which model the clip should play on. The picked
/// model is remembered in static fields so the next time the user opens this modal (with any clip)
/// it restores the previous model automatically. The memory is JVM-scoped — not written to disk.
public final class AnimationPreviewerModal extends AbstractContainerWidget
    implements AnchoredWidget, Modal, ResizeCompleteCallbackListener, CloseableWidget {

    private static final int TITLE_H = 20;
    private static final int SEARCH_H = 18;
    private static final int ROW_H = 18;
    private static final int SCROLLBAR_W = 5;
    private static final int DIMMER_COLOR = 0x88000000;

    /// Session-scoped memory of the last model the user picked from this modal.
    /// Null until the user picks one. Reset on JVM restart.
    @Nullable private static Identifier lastModelId;
    private static EntityModelComponent.@Nullable ModelType lastModelType;

    private final Font font;
    private final AssetAccessor<AnimationClip> animClip;
    private final AdvancedEditBox<String> searchBox;
    private final ModelList modelList;
    private final ModelPreviewer preview;

    public AnimationPreviewerModal(Font font, AssetAccessor<AnimationClip> animClip) {
        super(
            0, 0, 0, 0,
            Component.translatable(LangKeys.GUI_ANIMATION_PREVIEWER_MODAL_TITLE, animClip.identifier().toString()),
            AbstractScrollArea.defaultSettings(0)
        );

        this.font = font;
        this.animClip = animClip;
        this.modelList = new ModelList();
        this.modelList.rebuild("");

        this.searchBox = AdvancedEditBox.stringBox(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TITLE_H), AnchoredWidget.pixel(SEARCH_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            modelList::rebuild,
            Component.translatable(LangKeys.GUI_MESSAGE_EMPTY_SEARCHBAR)
        );

        this.preview = new ModelPreviewer(
            AnchoredWidget.pixel(0), AnchoredWidget.fraction(50),
            AnchoredWidget.pixel(TITLE_H + SEARCH_H), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            AkytheraEditorLayoutManager.getInstance()::getLayoutGuiScale
        );

        preview.addComponent(new CameraComponent(true));
        preview.addComponent(new ModelPreviewer.BackgroundComponent(18, 18, 18, 255));
        preview.addComponent(new ModelPreviewer.GridFloorComponent(1.0F, 6, 68, 68, 68, 255));

        // Restore the model the user picked the last time this modal was opened (this JVM).
        if (lastModelId != null && lastModelType != null) {
            applyModel(lastModelId, lastModelType);
            getRowById(lastModelId, lastModelType).ifPresent(modelList::setSelected);
        }
    }

    private void applyModel(Identifier id, EntityModelComponent.ModelType type) {
        preview.displayEntity(id, type, true, true);

        EntityModelComponent c = preview.getComponent(EntityModelComponent.class);
        if (c != null) c.setSimplePlayingAnimation(animClip);
    }

    @Override
    public void relocate(ScreenRectangle screenRectangle) {
        AnchoredWidget.super.relocate(screenRectangle);

        ScreenRectangle rect = getRectangle();

        searchBox.relocate(rect);
        modelList.relocate(rect);
        preview.relocate(rect);
    }

    @Override
    public void onResizeCompleted() {
        preview.onResizeCompleted();
    }

    @Override
    public void destroy() {
        preview.destroy();
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return List.of(searchBox, modelList, preview);
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    public void setFocused(final @Nullable GuiEventListener focused) {
        if (getFocused() == focused) {
            return;
        }

        super.setFocused(focused);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (!super.isMouseOver(event.x(), event.y())) {
            AkytheraEditorLayoutManager.getInstance().closeModal();
            return true;
        }

        super.mouseClicked(event, doubleClick);

        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return modelList.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
            || preview.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (event.isEscape()) {
            AkytheraEditorLayoutManager.getInstance().closeModal();
            return true;
        }

        GuiEventListener focused = getFocused();

        return focused != null && focused.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        GuiEventListener focused = getFocused();

        return focused != null && focused.charTyped(event);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return isActive();
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Window window = Minecraft.getInstance().getWindow();
        AkytheraEditorLayoutManager layoutManager = AkytheraEditorLayoutManager.getInstance();

        // Dimmer spanning the whole viewport
        graphics.fill(
            0, 0,
            window.getScreenWidth() / layoutManager.getLayoutGuiScale(),
            window.getScreenHeight() / layoutManager.getLayoutGuiScale(),
            DIMMER_COLOR
        );

        // Modal frame
        graphics.fill(getX(), getY(), getRight(), getBottom(), 0xFF1E1E1E);
        graphics.fill(getX(), getY(), getRight(), getY() + 1, 0xFF3A3A3A);
        graphics.fill(getX(), getBottom() - 1, getRight(), getBottom(), 0xFF3A3A3A);
        graphics.fill(getX(), getY(), getX() + 1, getBottom(), 0xFF3A3A3A);
        graphics.fill(getRight() - 1, getY(), getRight(), getBottom(), 0xFF3A3A3A);

        // Title bar
        graphics.fill(getX(), getY(), getRight(), getY() + TITLE_H, 0xFF2A2A2A);
        graphics.text(
            font,
            getMessage(),
            getX() + 8,
            getY() + (TITLE_H - font.lineHeight) / 2,
            0xFFCCCCCC,
            false
        );

        searchBox.extractRenderState(graphics, mouseX, mouseY, a);
        modelList.extractRenderState(graphics, mouseX, mouseY, a);
        preview.extractRenderState(graphics, mouseX, mouseY, a);

        // Empty-state overlay — drawn on top of the preview pane when no model has been picked.
        if (preview.getComponent(EntityModelComponent.class) == null) {
            Component msg = Component.translatable(LangKeys.GUI_ANIMATION_PREVIEWER_MODAL_EMPTY);
            int cx = preview.getX() + preview.getWidth() / 2 - font.width(msg) / 2;
            int cy = preview.getY() + preview.getHeight() / 2 - font.lineHeight / 2;
            graphics.text(font, msg, cx, cy, 0xFFAAAAAA, false);
        }
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
    }

    private sealed interface Row permits TitleRow, SkeletalMeshRow, VanillaModelRow {
        Component label();

        boolean selectable();
    }

    private record TitleRow(Component label) implements Row {
        @Override
        public boolean selectable() {
            return false;
        }
    }

    private record SkeletalMeshRow(AssetAccessor<SkeletalMesh> accessor) implements Row {
        @Override
        public Component label() {
            return Component.literal(accessor.identifier().toString());
        }

        @Override
        public boolean selectable() {
            return true;
        }
    }

    private record VanillaModelRow(EntityType<?> type, LivingEntityRenderer<?, ?, ?> renderer) implements Row {
        @Override
        public Component label() {
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            return Component.literal(id.getPath());
        }

        @Override
        public boolean selectable() {
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Row> buildCatalog(String query) {
        String lower = query.toLowerCase();
        List<Row> rows = new ArrayList<>();

        List<SkeletalMeshRow> skeletal = Stream.concat(
                MeshManager.getInstance().listRegisteredAccessors(),
                AkytheraProject.getCurrentProject().listIssuedEditorMeshAccessors()
            )
            .filter(acc -> acc.checkType(AssetAccessor.Type.SKELETAL_MESH))
            .collect(Collectors.toMap(AssetAccessor::identifier, acc -> acc, (a, _b) -> a, LinkedHashMap::new))
            .values().stream()
            .map(acc -> new SkeletalMeshRow((AssetAccessor<SkeletalMesh>) acc))
            .filter(row -> lower.isEmpty() || ParseUtils.toLowerCase(row.accessor().identifier()).contains(lower))
            .sorted(Comparator.comparing(row -> row.accessor().identifier().toString()))
            .toList();

        if (!skeletal.isEmpty()) {
            rows.add(new TitleRow(Component.translatable(LangKeys.GUI_PREVIEW_MODEL_SELECT_MODAL_CATEGORY_SKELETAL)));
            rows.addAll(skeletal);
        }

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        List<VanillaModelRow> vanilla =
            Stream.concat(
                dispatcher.renderers.entrySet().stream()
                    .filter(e -> e.getValue() instanceof LivingEntityRenderer<?, ?, ?>)
                    .map(e -> new VanillaModelRow(e.getKey(), (LivingEntityRenderer<?, ?, ?>) e.getValue())),
                Stream.of(new VanillaModelRow(EntityType.PLAYER, dispatcher.playerRenderers.get(DefaultPlayerSkin.getDefaultSkin().model())))
            )
            .filter(row -> lower.isEmpty() || row.label().getString().toLowerCase().contains(lower))
            .sorted(Comparator.comparing(row -> row.label().getString()))
            .toList();

        if (!vanilla.isEmpty()) {
            rows.add(new TitleRow(Component.translatable(LangKeys.GUI_PREVIEW_MODEL_SELECT_MODAL_CATEGORY_VANILLA)));
            rows.addAll(vanilla);
        }

        return rows;
    }

    private Optional<ModelList.Entry> getRowById(Identifier id, EntityModelComponent.ModelType type) {
        return modelList.children().stream().filter(e ->
            switch (type) {
                case SKELETAL_MESH ->
                    e.getRow() instanceof SkeletalMeshRow(AssetAccessor<SkeletalMesh> accessor) && accessor.identifier().equals(id);
                case VANILLA_ENTITY ->
                    e.getRow() instanceof VanillaModelRow vanillaModelRow && BuiltInRegistries.ENTITY_TYPE.getKey(vanillaModelRow.type).equals(id);
            }).findFirst();
    }

    private void onSelectRow(Row row) {
        switch (row) {
            case SkeletalMeshRow(AssetAccessor<SkeletalMesh> accessor) -> {
                Identifier id = accessor.identifier();
                applyModel(id, EntityModelComponent.ModelType.SKELETAL_MESH);
                lastModelId = id;
                lastModelType = EntityModelComponent.ModelType.SKELETAL_MESH;
            }
            case VanillaModelRow vr -> {
                Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(vr.type());
                applyModel(id, EntityModelComponent.ModelType.VANILLA_ENTITY);
                lastModelId = id;
                lastModelType = EntityModelComponent.ModelType.VANILLA_ENTITY;
            }
            case TitleRow _ -> {}
        }
    }

    private final class ModelList extends AbstractSelectionList<ModelList.@NonNull Entry> implements AnchoredWidget {
        ModelList() {
            super(Minecraft.getInstance(), 0, 0, 0, ROW_H);
        }

        void rebuild(String query) {
            clearEntries();

            List<Row> rows = buildCatalog(query);

            for (Row row : rows) {
                if (row instanceof TitleRow tr) {
                    addEntry(new TitleEntry(tr));
                } else {
                    addEntry(new ModelEntry(row));
                }
            }

            updateSizeAndPosition(getWidth(), getHeight(), getX(), getY());
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
            if (!isMouseOver(mx, my)) {
                return false;
            }

            return super.mouseScrolled(mx, my, scrollX, scrollY);
        }

        @Override
        public int getRowWidth() {
            return getWidth() - SCROLLBAR_WIDTH;
        }

        @Override
        public int scrollbarWidth() {
            return SCROLLBAR_W;
        }

        @Override
        protected int scrollerHeight() {
            return Mth.clamp((int) ((float) (this.height * this.height) / (float) this.contentHeight()), 8, this.height - 8);
        }

        @Override
        protected int scrollBarX() {
            return getRight() - scrollbarWidth();
        }

        @Override
        protected void extractListBackground(@NonNull GuiGraphicsExtractor graphics) {
        }

        @Override
        protected void extractListSeparators(@NonNull GuiGraphicsExtractor graphics) {
        }

        @Override
        protected boolean entriesCanBeSelected() {
            return true;
        }

        @Override
        protected void extractScrollbar(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            if (!scrollable()) return;
            int sbX = scrollBarX(), scrollerH = scrollerHeight(), scrollerY = scrollBarY();
            boolean hovered = mouseX >= sbX && mouseX < sbX + SCROLLBAR_W && mouseY >= scrollerY && mouseY < scrollerY + scrollerH;

            graphics.fill(sbX, getY(), sbX + SCROLLBAR_W, getBottom() - 1, 0xFF1A1A1A);
            graphics.fill(sbX + 1, scrollerY, sbX + SCROLLBAR_W - 1, scrollerY + scrollerH, hovered ? 0xFF6A6A6A : 0xFF505050);
        }

        @Override
        protected void extractSelection(GuiGraphicsExtractor graphics, Entry entry, int outlineColor) {
            int x = entry.getX(), y = entry.getY();
            int right = entry.getContentRight();
            int bottom = y + entry.getContentHeight();
            graphics.fill(x, y, right, bottom, 0xFF0A2833);
            graphics.outline(x, y, entry.getContentWidth(), entry.getHeight(), 0xFF37D5FF);
        }

        @Override
        public void setSelected(@Nullable Entry selected) {
            super.setSelected(selected);

            if (selected != null) {
                onSelectRow(selected.getRow());
            }
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
        }

        abstract static class Entry extends AbstractSelectionList.Entry<@NonNull Entry> {
            abstract Row getRow();
        }

        final class TitleEntry extends Entry {
            private final TitleRow row;

            TitleEntry(TitleRow row) {
                this.row = row;
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
                g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF141414);
                g.text(font, row.label(), getX() + 6, getY() + (getHeight() - 8) / 2, 0xFF888888, false);
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent ev, boolean dc) {
                return false;
            }

            @Override
            public Row getRow() {
                return row;
            }
        }

        final class ModelEntry extends Entry {
            private final Row row;

            ModelEntry(Row row) {
                this.row = row;
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
                if (getSelected() != this && hovered) {
                    g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF262626);
                }

                g.text(font, row.label(), getX() + 12, getY() + (getHeight() - 8) / 2, getSelected() == this ? 0xFFFFFFFF : 0xFFCCCCCC, false);
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClicked) {
                setSelected(this);
                return true;
            }

            @Override
            public Row getRow() {
                return row;
            }
        }

        /// ***************************************************************
        /// [AnchoredWidget] implementations                              *
        /// ***************************************************************
        private Unit x1 = AnchoredWidget.pixel(0);
        private Unit x2 = AnchoredWidget.fraction(50);
        private Unit y1 = AnchoredWidget.pixel(TITLE_H + SEARCH_H);
        private Unit y2 = AnchoredWidget.fraction(0);
        private final HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.LEFT_WIDTH;
        private final VerticalAnchorType verticalAnchorType = VerticalAnchorType.TOP_BOTTOM;

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

    /// ***************************************************************
    /// [AnchoredWidget] implementations                              *
    /// ***************************************************************
    private Unit x1 = AnchoredWidget.pixel(0);
    private Unit x2 = AnchoredWidget.fraction(75);
    private Unit y1 = AnchoredWidget.pixel(0);
    private Unit y2 = AnchoredWidget.fraction(80);
    private final HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.CENTER_WIDTH;
    private final VerticalAnchorType verticalAnchorType = VerticalAnchorType.CENTER_HEIGHT;

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