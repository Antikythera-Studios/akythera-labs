package com.yesman.akytheralabs.gui.widget.main;

import com.mojang.blaze3d.platform.Window;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.api.gui.CloseableWidget;
import com.yesman.akythera.client.api.gui.ResizeCompleteCallbackListener;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.gui.viewer.BackgroundComponent;
import com.yesman.akythera.client.api.gui.viewer.CameraComponent;
import com.yesman.akythera.client.api.gui.viewer.EntityModelComponent;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.EntityPreviewModel;
import com.yesman.akytheralabs.dataholder.EntityPreviewModelMapping;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.EditorEditBox;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/// Picks the mesh an entity type is previewed with: a project skeletal mesh, or a vanilla entity
/// model. Opened from [EntityPreviewModelMappingModal]'s mesh row.
///
/// The vanilla category is offered only while Player Animation Library is installed — it is what
/// poses a vanilla [net.minecraft.client.model.EntityModel] from an
/// [com.yesman.akythera.core.asset.armature.Armature], and without it such a model cannot follow an
/// animation.
public final class PreviewMeshSelectModal extends OverlayingWindowModal
    implements ResizeCompleteCallbackListener, CloseableWidget
{
    private static final int TITLE_H = 20;
    private static final int SEARCH_H = 18;
    private static final int ROW_H = 18;
    private static final int SCROLLBAR_W = 5;
    private static final int DIMMER_COLOR = 0x88000000;

    private final Font font;
    private final Consumer<EntityPreviewModel> onConfirm;
    private final EditorEditBox<String> searchBox;
    private final ModelList modelList;
    private final Model3DViewer preview;

    public PreviewMeshSelectModal(
        Font font,
        @Nullable EntityPreviewModel initialModel,
        Consumer<EntityPreviewModel> onConfirm
    ) {
        super(
            AnchoredWidget.fraction(75), AnchoredWidget.fraction(80),
            Component.translatable(LangKeys.GUI_PREVIEW_MODEL_SELECT_MODAL_TITLE)
        );

        this.font = font;
        this.onConfirm = onConfirm;
        this.modelList = new ModelList();
        this.modelList.rebuild("");

        this.searchBox = EditorEditBox.stringBox(
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
            getRowById(initialModel).ifPresent(modelList::setSelected);
        }
    }

    /// Whether `mesh` still names something this project can draw. Read by
    /// [EntityPreviewModelMappingModal]'s mesh-box validator.
    public static boolean resolves(@Nullable EntityPreviewModel mesh) {
        if (mesh == null) return true;

        return switch (mesh.modelType()) {
            case SKELETAL_MESH -> EntityPreviewModelMapping.resolveSkeletalMesh(mesh.modelId()) != null;
            case VANILLA_ENTITY -> BuiltInRegistries.ENTITY_TYPE.containsKey(mesh.modelId());
        };
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
    public EditorEditBox<String> searchBox() {
        return searchBox;
    }

    @Override
    public boolean confirmSoleResult() {
        List<ModelList.ModelEntry> rows = modelList.children().stream()
            .filter(ModelList.ModelEntry.class::isInstance)
            .map(ModelList.ModelEntry.class::cast)
            .toList();

        if (rows.size() != 1) return false;

        rows.getFirst().confirm();

        return true;
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
            ModalManager.getInstance().closeModal();
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
            ModalManager.getInstance().closeModal();
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
    protected boolean closeOnPressBackground() {
        return true;
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
    }

    private record TitleRow(Component label) implements Row {
    }

    private record SkeletalMeshRow(AssetAccessor<SkeletalMesh> accessor) implements Row {
        @Override
        public Component label() {
            return Component.literal(accessor.identifier().toString());
        }
    }

    private record VanillaModelRow(EntityType<?> type) implements Row {
        @Override
        public Component label() {
            return Component.literal(BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath());
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Row> buildCatalog(String query) {
        String lower = query.toLowerCase();
        List<Row> rows = new ArrayList<>();

        // The project's merged listing, not MeshManager's: a project mesh is never registered there,
        // and one reached through MeshManager.CODEC reports STATIC_MESH until it is first loaded.
        List<SkeletalMeshRow> skeletal = AkytheraProject.getCurrentProject()
            .listAllMeshAccessors()
            .filter(acc -> acc.checkType(AssetAccessor.Type.SKELETAL_MESH))
            .map(acc -> new SkeletalMeshRow((AssetAccessor<SkeletalMesh>) acc))
            .filter(row -> lower.isEmpty() || ParseUtils.toLowerCase(row.accessor().identifier()).contains(lower))
            .sorted(Comparator.comparing(row -> row.accessor().identifier().toString()))
            .toList();

        if (!skeletal.isEmpty()) {
            rows.add(new TitleRow(Component.translatable(LangKeys.GUI_PREVIEW_MODEL_SELECT_MODAL_CATEGORY_SKELETAL)));
            rows.addAll(skeletal);
        }

        if (!EntityPreviewModelMapping.vanillaModelsAnimatable()) return rows;

        List<VanillaModelRow> vanilla = EntityPreviewModelMapping.previewableTypes().stream()
            .map(VanillaModelRow::new)
            .filter(row -> lower.isEmpty() || row.label().getString().toLowerCase().contains(lower))
            .sorted(Comparator.comparing(row -> row.label().getString()))
            .toList();

        if (!vanilla.isEmpty()) {
            rows.add(new TitleRow(Component.translatable(LangKeys.GUI_PREVIEW_MODEL_SELECT_MODAL_CATEGORY_VANILLA)));
            rows.addAll(vanilla);
        }

        return rows;
    }

    private Optional<ModelList.Entry> getRowById(EntityPreviewModel model) {
        return modelList.children().stream().filter(e ->
            switch (model.modelType()) {
                case SKELETAL_MESH ->
                    e.getRow() instanceof SkeletalMeshRow(AssetAccessor<SkeletalMesh> accessor)
                        && accessor.identifier().equals(model.modelId());
                case VANILLA_ENTITY ->
                    e.getRow() instanceof VanillaModelRow vanillaModelRow
                        && BuiltInRegistries.ENTITY_TYPE.getKey(vanillaModelRow.type()).equals(model.modelId());
            }).findFirst();
    }

    /// The model `row` stands for, or null for a category header.
    private static @Nullable EntityPreviewModel modelOf(Row row) {
        return switch (row) {
            case SkeletalMeshRow sr ->
                new EntityPreviewModel(sr.accessor().identifier(), EntityModelComponent.ModelType.SKELETAL_MESH);
            case VanillaModelRow vr ->
                new EntityPreviewModel(BuiltInRegistries.ENTITY_TYPE.getKey(vr.type()), EntityModelComponent.ModelType.VANILLA_ENTITY);
            case TitleRow _ -> null;
        };
    }

    private void onSelectRow(Row row) {
        switch (row) {
            case SkeletalMeshRow(AssetAccessor<SkeletalMesh> accessor) ->
                preview.displaySkeletalMesh(accessor, null, true, true);
            case VanillaModelRow vr -> preview.displayEntity(
                BuiltInRegistries.ENTITY_TYPE.getKey(vr.type()),
                EntityModelComponent.ModelType.VANILLA_ENTITY,
                null, true, true
            );
            case TitleRow _ -> {}
        }
    }

    private final class ModelList extends AbstractSelectionList<ModelList.@NonNull Entry> implements AnchoredWidget {
        ModelList() {
            super(Minecraft.getInstance(), 0, 0, 0, ROW_H);
        }

        void rebuild(String query) {
            clearEntries();

            for (Row row : buildCatalog(query)) {
                if (row instanceof TitleRow tr) addEntry(new TitleEntry(tr));
                else addEntry(new ModelEntry(row));
            }

            // Effectively calls repositionEntries
            updateSizeAndPosition(getWidth(), getHeight(), getX(), getY());
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
            if (!isMouseOver(mx, my)) return false;
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

                if (doubleClicked) confirm();

                return true;
            }

            void confirm() {
                EntityPreviewModel model = modelOf(row);

                if (model != null) {
                    onConfirm.accept(model);
                    ModalManager.getInstance().closeModal();
                }
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
        private HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.LEFT_WIDTH;
        private VerticalAnchorType verticalAnchorType = VerticalAnchorType.TOP_BOTTOM;

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
}
