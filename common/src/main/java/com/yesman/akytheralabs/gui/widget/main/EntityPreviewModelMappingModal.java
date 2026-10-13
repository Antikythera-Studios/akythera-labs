package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.*;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.gui.viewer.*;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.dataholder.EntityPreviewModel;
import com.yesman.akytheralabs.dataholder.EntityPreviewModelMapping;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
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
import java.util.List;

/// Binds every [EntityType] to the mesh and the [Armature] editor previewers draw it with, both
/// held by [EntityPreviewModelMapping]. One entity per list entry:
///
/// ```
/// minecraft:player
///   mesh      | <model>       |popup|
///   armature  | <armature>    |popup|
/// -------------------------------------
/// ```
///
/// The mesh magnifier stacks [PreviewMeshSelectModal], the armature one the shared
/// [AccessorSelectionModal] armature picker with its `<none>` row enabled, so either binding can be
/// cleared again. The right column previews the selected entity through the mapping.
///
/// A binding whose accessor no longer resolves in the project is marked red on its box
/// ([PopupBox#withValidator]) and never handed to the previewer, whose [EntityModelComponent] would
/// throw while loading the missing asset.
public final class EntityPreviewModelMappingModal extends OverlayingWindowModal
    implements ResizeCompleteCallbackListener, CloseableWidget
{
    private static final int TITLE_H = 20;
    private static final int SEARCH_H = 18;
    private static final int LIST_TOP = TITLE_H + SEARCH_H;
    private static final int FOOTER_H = 26;
    private static final int FILL_BUTTON_W = 140;
    private static final int SCROLLBAR_W = 5;
    private static final int PREVIEW_PCT = 40;   // right-column previewer width

    private static final int TEXT_ROW_H = 16;
    private static final int SEPARATOR_H = 5;
    private static final int ENTRY_H = TEXT_ROW_H * 3 + SEPARATOR_H;
    private static final int LABEL_INDENT = 14;  // the two property rows sit under the entity id
    private static final int LABEL_W = 70;       // "mesh" / "armature"
    private static final int BOX_H = 14;

    public static void open(Font font) {
        ModalManager.getInstance().openModalWidgetAtCenter(new EntityPreviewModelMappingModal(font));
    }

    private final Font font;
    private final AkytheraButton closeScreenButton;
    private final @Nullable AkytheraButton fillVanillaButton;
    private final EditorEditBox<String> searchBox;
    private final Model3DViewer preview;
    private MappingList mappingList; // assigned after the search box, whose responder captures it

    public EntityPreviewModelMappingModal(Font font) {
        super(
            AnchoredWidget.fraction(80), AnchoredWidget.fraction(90),
            Component.translatable(LangKeys.GUI_ENTITY_PREVIEW_MODEL_MAPPING_MODAL_TITLE)
        );

        this.font = font;

        this.closeScreenButton = AkytheraButton
            .buttonBuilder(font, LocalizedMessage.handwritten("X"), () -> ModalManager.getInstance().closeModal())
            .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(TITLE_H))
            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(TITLE_H))
            .horizontalAnchorType(HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(VerticalAnchorType.TOP_HEIGHT)
            .theme(AkytheraLabsButtonTheme.TOP_MENU_BUTTON)
            .build();

        // Without PAL a vanilla model cannot be posed, so a fill would bind models that never move.
        this.fillVanillaButton = !EntityPreviewModelMapping.vanillaModelsAnimatable() ? null : AkytheraButton
            .buttonBuilder(
                font,
                LocalizedMessage.translatable(LangKeys.GUI_ENTITY_PREVIEW_MODEL_MAPPING_MODAL_FILL_VANILLA),
                () -> fillVanillaBindings())
            .xUnits(AnchoredWidget.pixel(6), AnchoredWidget.pixel(FILL_BUTTON_W))
            .yUnits(AnchoredWidget.pixel(4), AnchoredWidget.pixel(FOOTER_H - 10))
            .horizontalAnchorType(HorizontalAnchorType.LEFT_WIDTH)
            .verticalAnchorType(VerticalAnchorType.BOTTOM_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();

        this.searchBox = EditorEditBox.stringBox(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.fraction(PREVIEW_PCT),
            AnchoredWidget.pixel(TITLE_H), AnchoredWidget.pixel(SEARCH_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            str -> mappingList.rebuild(str),
            Component.translatable(LangKeys.GUI_MESSAGE_EMPTY_SEARCHBAR)
        );

        this.preview = new Model3DViewer(
            AnchoredWidget.pixel(0), AnchoredWidget.fraction(PREVIEW_PCT),
            AnchoredWidget.pixel(TITLE_H), AnchoredWidget.pixel(1),
            HorizontalAnchorType.RIGHT_WIDTH,
            VerticalAnchorType.TOP_BOTTOM,
            AkytheraEditorLayoutManager.getInstance()::getLayoutGuiScale
        );

        preview.addComponent(new CameraComponent(true));
        preview.addComponent(new BackgroundComponent(18, 18, 18, 255));
        preview.addComponent(new GridFloorComponent(1.0F, 50, 68, 68, 68, 255));

        this.mappingList = new MappingList(EntityPreviewModelMapping.previewableTypes());
    }

    /// Binds every previewable entity to its vanilla model and re-reads the rows, so the boxes show
    /// what was just written. The search text is kept.
    private void fillVanillaBindings() {
        EntityPreviewModelMapping.fillVanillaBindings();
        mappingList.rebuild(ParseUtils.nvl(searchBox.getValue(), ""));
    }

    /// The mesh picker, landing back here on confirm. Rebinding re-displays so the previewer picks
    /// up the new mesh.
    private void openMeshPicker(EntityType<?> type, PopupBox<EntityPreviewModel> box) {
        PreviewMeshSelectModal modal = new PreviewMeshSelectModal(font, EntityPreviewModelMapping.getMesh(type), mesh -> {
            EntityPreviewModelMapping.setMesh(type, mesh);
            box.setValue(mesh);

            showPreview(type);
        });

        ModalManager.getInstance().openModalWidgetAtCenter(modal);
    }

    /// The shared armature picker. Confirming the `<none>` row passes a null accessor through,
    /// removing the binding.
    private void openArmaturePicker(EntityType<?> type, PopupBox<AssetAccessor<? extends Armature>> box) {
        AccessorSelectionModal modal = new AccessorSelectionModal(font, AssetAccessor.Type.ARMATURE, true, accessor -> {
            @SuppressWarnings("unchecked")
            AssetAccessor<? extends Armature> armature = (AssetAccessor<? extends Armature>) accessor;
            EntityPreviewModelMapping.setArmature(type, armature);
            box.setValue(armature);

            showPreview(type);
        });

        ModalManager.getInstance().openModalWidgetAtCenter(modal);
    }

    /// A binding is displayable when its armature is absent — a vanilla model carries its own
    /// skeleton — or still resolves against the project's listing. A dangling accessor fails to load
    /// inside [EntityModelComponent], so such an entity is marked instead of previewed.
    private static boolean bindingResolves(EntityType<?> type) {
        AssetAccessor<? extends Armature> armature = EntityPreviewModelMapping.getArmature(type);
        return armature == null || AccessorSelectionModal.resolves(armature);
    }

    /// Shows the selected entity's bound mesh in the right-column previewer, in its bind pose. An
    /// entity whose binding doesn't resolve displays nothing, dropping whatever stood there before.
    private void showPreview(EntityType<?> type) {
        if (!bindingResolves(type)) {
            preview.removeAllComponents(EntityModelComponent.class);
            return;
        }

        EntityPreviewModelMapping.display(preview, BuiltInRegistries.ENTITY_TYPE.getKey(type), true, true);
    }

    @Override
    public EditorEditBox<String> searchBox() {
        return searchBox;
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        List<GuiEventListener> children = new ArrayList<>(List.of(closeScreenButton, searchBox, mappingList, preview));
        if (fillVanillaButton != null) children.add(fillVanillaButton);

        return children;
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
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    public void setFocused(final @Nullable GuiEventListener focused) {
        if (getFocused() == focused) return;
        super.setFocused(focused);
    }

    @Override
    public boolean isMouseOverHeader(double mouseX, double mouseY) {
        return mouseX >= (double) getX()
            && mouseY >= (double) getY()
            && mouseX < (double) getRight()
            && mouseY < (double) getY() + TITLE_H;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return mappingList.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
            || preview.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (event.isEscape()) {
            if (AkytheraEditorLayoutManager.getInstance().isActive()) {
                ModalManager.getInstance().closeModal();
                return true;
            }
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
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractWidgetRenderState(g, mouseX, mouseY, a);    // dimmer

        // Modal frame
        g.fill(getX(), getY(), getRight(), getBottom(), 0xFF1E1E1E);
        g.fill(getX(), getY(), getRight(), getY() + 1, 0xFF3A3A3A);
        g.fill(getX(), getBottom() - 1, getRight(), getBottom(), 0xFF3A3A3A);
        g.fill(getX(), getY(), getX() + 1, getBottom(), 0xFF3A3A3A);
        g.fill(getRight() - 1, getY(), getRight(), getBottom(), 0xFF3A3A3A);

        // Title bar
        g.fill(getX(), getY(), getRight(), getY() + TITLE_H, 0xFF2A2A2A);
        g.text(font, getMessage(), getX() + 8, getY() + (TITLE_H - font.lineHeight) / 2, 0xFFCCCCCC, false);

        // Separator between the mapping list and the previewer column
        int previewLeft = getRight() - (int) (getWidth() * (PREVIEW_PCT / 100.0F));
        g.fill(previewLeft - 1, getY() + TITLE_H, previewLeft, getBottom() - 1, 0xFF333333);

        for (GuiEventListener e : children()) {
            if (e instanceof AbstractWidget w) w.extractRenderState(g, mouseX, mouseY, a);
        }
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }

    /// Scrollable per-entity entries. `ContainerObjectSelectionList` so each entry hosts its two
    /// PopupBoxes as real child widgets (clicks route through `children()`).
    private final class MappingList extends ContainerObjectSelectionList<@NonNull EntityEntry> implements AnchoredWidget {
        private final List<EntityType<?>> allTypes;

        // Initial size zeros are harmless — real geometry comes from relocate() against the
        // fraction-sized modal rect (fill via LEFT_RIGHT / TOP_BOTTOM anchors below).
        MappingList(List<EntityType<?>> allTypes) {
            super(Minecraft.getInstance(), 0, 0, LIST_TOP, ENTRY_H);
            this.allTypes = allTypes;
            rebuild("");
        }

        void rebuild(String query) {
            clearEntries();
            setSelected(null);
            String lower = query.toLowerCase();

            for (EntityType<?> type : allTypes) {
                if (!lower.isEmpty() && !EntityPreviewModelMapping.label(type).toLowerCase().contains(lower)) continue;
                addEntry(new EntityEntry(type));
            }

            updateSizeAndPosition(getWidth(), getHeight(), getX(), getY());
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
            if (!isMouseOver(mx, my)) return false;
            return super.mouseScrolled(mx, my, scrollX, scrollY);
        }

        @Override
        public void setSelected(@Nullable EntityEntry selected) {
            super.setSelected(selected);
            if (selected != null) showPreview(selected.type);
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
        protected void extractSelection(GuiGraphicsExtractor g, EntityEntry entry, int outlineColor) {
            g.fill(entry.getX(), entry.getY(), entry.getContentRight(), entry.getY() + entry.getContentHeight(), 0xFF0A2833);
            g.outline(entry.getX(), entry.getY(), entry.getContentWidth(), entry.getHeight(), 0xFF37D5FF);
        }

        @Override
        protected void extractScrollbar(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            if (!scrollable()) return;
            int sbX = scrollBarX(), scrollerH = scrollerHeight(), scrollerY = scrollBarY();
            boolean hovered = mouseX >= sbX && mouseX < sbX + SCROLLBAR_W && mouseY >= scrollerY && mouseY < scrollerY + scrollerH;

            graphics.fill(sbX, getY(), sbX + SCROLLBAR_W, getBottom(), 0xFF1A1A1A);
            graphics.fill(sbX + 1, scrollerY, sbX + SCROLLBAR_W - 1, scrollerY + scrollerH, hovered ? 0xFF6A6A6A : 0xFF505050);
        }

        @Override
        public void updateWidgetNarration(@NonNull NarrationElementOutput n) {
        }

        // ***************************************************************
        // [AnchoredWidget] implementations
        // ***************************************************************
        private Unit x1 = AnchoredWidget.pixel(0);
        private Unit x2 = AnchoredWidget.fraction(PREVIEW_PCT);   // right inset — previewer column
        private Unit y1 = AnchoredWidget.pixel(LIST_TOP);
        private Unit y2 = AnchoredWidget.pixel(FOOTER_H);
        private AnchoredWidget.HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.LEFT_RIGHT;
        private AnchoredWidget.VerticalAnchorType verticalAnchorType = VerticalAnchorType.TOP_BOTTOM;

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

    /// One entity's bindings: its id, then a `mesh` row and an `armature` row, closed by a
    /// separator line. Both boxes are re-seated relative to the modal every frame so header-drags
    /// and scrolling stay in sync.
    private final class EntityEntry extends ContainerObjectSelectionList.Entry<@NonNull EntityEntry> {
        private final EntityType<?> type;
        private final PopupBox<EntityPreviewModel> meshBox;
        private final PopupBox<AssetAccessor<? extends Armature>> armatureBox;
        private final List<GuiEventListener> children;

        EntityEntry(EntityType<?> type) {
            this.type = type;

            this.meshBox = new PopupBox<EntityPreviewModel>(
                font,
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(BOX_H),
                HorizontalAnchorType.LEFT_WIDTH, VerticalAnchorType.TOP_HEIGHT,
                () -> EntityPreviewModelMapping.getMesh(type),
                _ -> {},
                mesh -> mesh == null ? "" : mesh.modelId().toString(),
                Component.empty(),
                box -> openMeshPicker(type, box)
            ).withValidator(PreviewMeshSelectModal::resolves).setOnCleared(box -> {
                EntityPreviewModelMapping.setMesh(type, null);
                box.setValue(null);

                showPreview(type);
            });

            this.armatureBox = new PopupBox<AssetAccessor<? extends Armature>>(
                font,
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(BOX_H),
                HorizontalAnchorType.LEFT_WIDTH, VerticalAnchorType.TOP_HEIGHT,
                () -> EntityPreviewModelMapping.getArmature(type),
                _ -> {},
                accessor -> accessor == null ? "" : AccessorSelectionModal.displayName(accessor),
                Component.empty(),
                box -> openArmaturePicker(type, box)
            ).withValidator(AccessorSelectionModal::resolves).setOnCleared(box -> {
                EntityPreviewModelMapping.setArmature(type, null);
                box.setValue(null);

                showPreview(type);
            });

            // The modal flow refreshes via setValue on confirm; seed the display from the current
            // binding (setValue(null) renders an unbound box as <none>), which also runs the
            // validator — so a dangling binding is marked as the list is built.
            meshBox.setValue(EntityPreviewModelMapping.getMesh(type));
            armatureBox.setValue(EntityPreviewModelMapping.getArmature(type));

            this.children = List.of(meshBox, armatureBox);
        }

        @Override
        public @NonNull List<? extends GuiEventListener> children() {
            return children;
        }

        @Override
        public @NonNull List<? extends NarratableEntry> narratables() {
            return List.of(meshBox, armatureBox);
        }

        @Override
        public void setFocused(@Nullable GuiEventListener focused) {
            // Re-focusing the already-focused child clears the focus in vanilla's implementation.
            if (getFocused() == focused) return;

            super.setFocused(focused);
        }

        /// Any click on the entry selects it (→ previews its mesh); the default routing then still
        /// reaches whichever PopupBox was hit.
        @Override
        public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
            mappingList.setSelected(this);
            super.mouseClicked(event, doubleClick);
            return true;
        }

        @Override
        public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
            int textOffset = (TEXT_ROW_H - font.lineHeight) / 2;

            g.text(font, EntityPreviewModelMapping.label(type), getX() + 6, getY() + textOffset, 0xFFFFFFFF, false);

            seatRow(g, meshBox, LangKeys.GUI_ENTITY_PREVIEW_MODEL_MAPPING_MODAL_MESH, 1, mx, my, a);
            seatRow(g, armatureBox, LangKeys.GUI_ENTITY_PREVIEW_MODEL_MAPPING_MODAL_ARMATURE, 2, mx, my, a);

            int separatorY = getY() + TEXT_ROW_H * 3 + SEPARATOR_H / 2;
            g.fill(getX(), separatorY, getX() + getWidth(), separatorY + 1, 0xFF333333);
        }

        /// Draws one property label and seats its box on text row `row` of this entry.
        private void seatRow(GuiGraphicsExtractor g, PopupBox<?> box, String labelKey, int row, int mx, int my, float a) {
            int rowY = getY() + TEXT_ROW_H * row;
            int labelX = getX() + 6 + LABEL_INDENT;

            g.text(font, Component.translatable(labelKey), labelX, rowY + (TEXT_ROW_H - font.lineHeight) / 2, 0xFFCCCCCC, false);

            int boxX = labelX + LABEL_W;
            int boxY = rowY + (TEXT_ROW_H - BOX_H) / 2;
            int boxW = Math.max(0, getX() + getWidth() - boxX - 4);
            int modalX = EntityPreviewModelMappingModal.this.getX();
            int modalY = EntityPreviewModelMappingModal.this.getY();
            ScreenRectangle modalRect = EntityPreviewModelMappingModal.this.getRectangle();

            box.setX1(AnchoredWidget.pixel(boxX - modalX));
            box.setX2(AnchoredWidget.pixel(boxW));
            box.setY1(AnchoredWidget.pixel(boxY - modalY));
            box.setY2(AnchoredWidget.pixel(BOX_H));
            box.relocate(modalRect);
            box.extractRenderState(g, mx, my, a);
        }
    }
}
