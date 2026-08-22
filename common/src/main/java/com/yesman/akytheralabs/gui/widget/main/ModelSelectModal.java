package com.yesman.akytheralabs.gui.widget.main;

import com.mojang.blaze3d.platform.Window;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.api.mesh.MeshManager;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.gui.widget.CloseableWidget;
import com.yesman.akythera.client.gui.widget.viewer.BackgroundComponent;
import com.yesman.akythera.client.gui.widget.viewer.Model3DViewer;
import com.yesman.akythera.client.gui.widget.viewer.CameraComponent;
import com.yesman.akythera.client.gui.widget.viewer.EntityModelComponent;
import com.yesman.akythera.client.gui.widget.ResizeCompleteCallbackListener;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.EntityPreviewModel;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.AdvancedEditBox;
import com.yesman.akytheralabs.gui.widget.OverlayingWindowModal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ModelSelectModal extends OverlayingWindowModal
    implements ResizeCompleteCallbackListener, CloseableWidget
{
    private static final int TITLE_H = 20;
    private static final int SEARCH_H = 18;
    private static final int ROW_H = 18;
    private static final int SCROLLBAR_W = 5;
    private static final int DIMMER_COLOR = 0x88000000;

    private final Font font;
    private final Consumer<EntityPreviewModel> onConfirm;
    private final CatalogSource catalog;
    private final AdvancedEditBox<String> searchBox;
    private final ModelList modelList;
    private final Model3DViewer preview;

    /// Default catalog: skeletal meshes + every vanilla living entity.
    public ModelSelectModal(
        Font font,
        @Nullable EntityPreviewModel initialModel,
        Consumer<EntityPreviewModel> onConfirm
    ) {
        this(font, initialModel, onConfirm, defaultCatalog());
    }

    /// `catalog` lets callers swap what the list offers (e.g. entity-types-only via
    /// [#entityTypeCatalog]) without touching the modal's rendering/selection plumbing.
    public ModelSelectModal(
        Font font,
        @Nullable EntityPreviewModel initialModel,
        Consumer<EntityPreviewModel> onConfirm,
        CatalogSource catalog
    ) {
        super(
            AnchoredWidget.fraction(75), AnchoredWidget.fraction(80),
            Component.translatable(LangKeys.GUI_PREVIEW_MODEL_SELECT_MODAL_TITLE)
        );

        this.font = font;
        this.onConfirm = onConfirm;
        this.catalog = catalog;
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

        this.preview = new Model3DViewer(
            AnchoredWidget.pixel(0), AnchoredWidget.fraction(50),
            AnchoredWidget.pixel(TITLE_H + SEARCH_H), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            AkytheraEditorLayoutManager.getInstance()::getLayoutGuiScale
        );

        preview.addComponent(new CameraComponent(true));
        preview.addComponent(new BackgroundComponent(18, 18, 18, 255));

        if (initialModel != null) {
            getRowById(initialModel.modelId(), initialModel.modelType()).ifPresent(modelList::setSelected);
        }
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
            // Close modal if pressed out of the box
            AkytheraEditorLayoutManager.getInstance().closeModal();
            return true;
        }

        super.mouseClicked(event, doubleClick);

        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return modelList.mouseScrolled(mouseX, mouseY, scrollX, scrollY) || preview.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
    public boolean isMouseOverHeader(double mouseX, double mouseY) {
        return getY() <= mouseY && mouseY <= getY() + TITLE_H;
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

    private record VanillaModelRow(EntityType<?> type) implements Row {
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

    private List<Row> buildCatalog(String query) {
        return catalog.build(query);
    }

    /// Source of the modal's selectable rows for a given search `query`. Injected via the
    /// constructor so a caller can change *what* is offered (e.g. entity-types-only) while
    /// reusing all of the modal's rendering, search and selection behaviour.
    @FunctionalInterface
    public interface CatalogSource {
        List<Row> build(String query);
    }

    /// Skeletal meshes (project + registered) followed by every vanilla living entity,
    /// plus the player. This is the modal's original behaviour.
    public static CatalogSource defaultCatalog() {
        return ModelSelectModal::buildDefaultCatalog;
    }

    /// Entity types straight from [BuiltInRegistries#ENTITY_TYPE] — not from the renderer
    /// map — keeping only those the [EntityRenderDispatcher] resolves to a
    /// [LivingEntityRenderer] (i.e. have a previewable model). Used by the entity-patch
    /// flow, where the chosen entity type names the new patch.
    public static CatalogSource entityTypeCatalog() {
        return ModelSelectModal::buildEntityTypeCatalog;
    }

    @SuppressWarnings("unchecked")
    private static List<Row> buildDefaultCatalog(String query) {
        String lower = query.toLowerCase();
        List<Row> rows = new ArrayList<>();

        List<SkeletalMeshRow> skeletal = Stream.concat(
                MeshManager.getInstance().listRegisteredAccessors(),
                AkytheraProject.getCurrentProject().listIssuedEditorMeshAccessors()
            )
            .filter(acc -> acc.checkType(AssetAccessor.Type.SKELETAL_MESH))
            .collect(Collectors.toMap(AssetAccessor::identifier, acc -> acc, (a, _) -> a, LinkedHashMap::new))
            .values().stream()
            .map(acc -> new SkeletalMeshRow((AssetAccessor<SkeletalMesh>)acc))
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
                    .map(e -> new VanillaModelRow(e.getKey())),
                Stream.of(new VanillaModelRow(EntityTypes.PLAYER))
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

    private static List<Row> buildEntityTypeCatalog(String query) {
        String lower = query.toLowerCase();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();

        List<VanillaModelRow> types = Stream.concat(
            BuiltInRegistries.ENTITY_TYPE.stream()
                .filter(type -> dispatcher.renderers.get(type) instanceof LivingEntityRenderer<?, ?, ?>)
                .map(VanillaModelRow::new),
            Stream.of(new VanillaModelRow(EntityTypes.PLAYER))
        )
        .filter(row -> lower.isEmpty() || row.label().getString().toLowerCase().contains(lower))
        .sorted(Comparator.comparing(row -> row.label().getString()))
        .toList();

        if (types.isEmpty()) return List.of();

        List<Row> rows = new ArrayList<>();
        rows.add(new TitleRow(Component.translatable(LangKeys.GUI_PREVIEW_MODEL_SELECT_MODAL_CATEGORY_ENTITY_TYPES)));
        rows.addAll(types);

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
        EntityModelComponent existing = preview.getComponent(EntityModelComponent.class);
        if (existing != null) preview.removeComponent(existing);

        switch (row) {
            case SkeletalMeshRow sr -> {
                preview.displayEntity(
                    sr.accessor().identifier(),
                    EntityModelComponent.ModelType.SKELETAL_MESH,
                    true, true
                );
            }
            case VanillaModelRow vr -> {
                preview.displayEntity(
                    BuiltInRegistries.ENTITY_TYPE.getKey(vr.type()),
                    EntityModelComponent.ModelType.VANILLA_ENTITY,
                    true, true
                );
            }
            case TitleRow _ -> {}
        }
    }

    @Override
    protected boolean closeOnPressBackground() {
        return true;
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

            // Effectively call repositionEntries without access widener
            updateSizeAndPosition(getWidth(), getHeight(), getX(), getY());
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
            // Scroll previewer when mouse pointer doesn't hover
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
            return Mth.clamp((int)((float)(this.height * this.height) / (float)this.contentHeight()), 8, this.height - 8);
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

                if (doubleClicked) {
                    if (row instanceof SkeletalMeshRow(AssetAccessor<SkeletalMesh> accessor)) {
                        onConfirm.accept(new EntityPreviewModel(accessor.identifier(), EntityModelComponent.ModelType.SKELETAL_MESH));
                    } else if (row instanceof VanillaModelRow vanillaModelRow) {
                        onConfirm.accept(new EntityPreviewModel(BuiltInRegistries.ENTITY_TYPE.getKey(vanillaModelRow.type()), EntityModelComponent.ModelType.VANILLA_ENTITY));
                    }

                    AkytheraEditorLayoutManager.getInstance().closeModal();
                }

                return true;
            }

            @Override
            public Row getRow() {
                return row;
            }
        }

        // ***************************************************************
        // [AnchoredWidget] implementations
        // ***************************************************************
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
}