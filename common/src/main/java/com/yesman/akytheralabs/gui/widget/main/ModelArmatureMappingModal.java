package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.client.gui.widget.*;
import com.yesman.akythera.client.gui.widget.viewer.*;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.AdvancedEditBox;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.OverlayingWindowModal;
import com.yesman.akytheralabs.gui.widget.PopupBox;
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
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/// Maps an [EntityType] to a default [Armature] accessor for headless model previews
/// ([Model3DViewer]) via
/// [ComponentsFactory#registerDefaultArmature]. Each row pairs an entity type with a
/// [PopupBox] (`<entity>  ->  [armature]`, matching [AnimationRetargetModal]'s bone-mapping
/// rows) whose magnifier stacks the shared [AccessorSelectionModal] armature picker — with
/// the `<none>` row enabled, so a mapping can be cleared again. The right column hosts a
/// [Model3DViewer]: selecting a row displays its vanilla model in bind pose (no animation
/// is ever pushed), re-displayed after a pick so the new armature mapping takes effect.
///
/// A mapping whose armature accessor no longer resolves in the project — the asset was removed
/// after it was bound — is marked red on the row's box ([PopupBox#withValidator], evaluated for
/// every row as the modal builds its list) and never handed to the previewer, whose
/// [EntityModelComponent] would throw while loading the missing armature. Picking a fresh
/// armature clears the mark and brings the model back.
public final class ModelArmatureMappingModal extends OverlayingWindowModal
    implements ResizeCompleteCallbackListener, CloseableWidget
{
    private static final int TITLE_H = 20;
    private static final int SEARCH_H = 18;
    private static final int LIST_TOP = TITLE_H + SEARCH_H;
    private static final int SCROLLBAR_W = 5;
    private static final int PREVIEW_PCT = 40;   // right-column previewer width

    // Mapping-row geometry, mirroring AnimationRetargetModal's bone-mapping rows.
    private static final int ROW_H = 18;
    private static final int LABEL_W = 160;      // entity id
    private static final int ARROW_W = 18;       // "->"
    private static final int BOX_H = 14;

    public static void open(Font font) {
        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(new ModelArmatureMappingModal(font));
    }

    private final Font font;
    private final AnchoredButton closeScreenButton;
    private final AdvancedEditBox<String> searchBox;
    private final Model3DViewer preview;
    private MappingList mappingList; // assigned after the search box, whose responder captures it

    public ModelArmatureMappingModal(Font font) {
        super(
            AnchoredWidget.fraction(80), AnchoredWidget.fraction(90),
            Component.translatable(LangKeys.GUI_MODEL_ARMATURE_MAPPING_MODAL_TITLE)
        );

        this.font = font;

        this.closeScreenButton = AnchoredButton
            .buttonBuilder(font, Component.literal("X"), _ -> AkytheraEditorLayoutManager.getInstance().closeModal())
            .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(TITLE_H))
            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(TITLE_H))
            .horizontalAnchorType(HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(VerticalAnchorType.TOP_HEIGHT)
            .theme(AkytheraLabsButtonTheme.TOP_MENU_BUTTON)
            .build();

        this.searchBox = AdvancedEditBox.stringBox(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.fraction(PREVIEW_PCT),
            AnchoredWidget.pixel(TITLE_H), AnchoredWidget.pixel(SEARCH_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            str -> mappingList.rebuild(str),
            Component.translatable(LangKeys.GUI_MESSAGE_EMPTY_SEARCHBAR)
        );

        // Right-column previewer, same setup as AnimationRetargetModal's.
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

        this.mappingList = new MappingList(buildEntityCatalog());
    }

    /// Every entity type the previewer can display as a vanilla model — the render
    /// dispatcher's living-renderer entries plus the player (kept in a separate map),
    /// mirroring [AnimationRetargetModal]'s vanilla catalog.
    private static List<EntityType<?>> buildEntityCatalog() {
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        return Stream.concat(
                dispatcher.renderers.entrySet().stream()
                    .filter(e -> e.getValue() instanceof LivingEntityRenderer<?, ?, ?>)
                    .map(Map.Entry::getKey),
                Stream.<EntityType<?>>of(EntityTypes.PLAYER)
            )
            .sorted(Comparator.comparing(ModelArmatureMappingModal::entityLabel))
            .toList();
    }

    private static String entityLabel(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
    }

    /// The same armature-selecting workload as [EditorWidgetFactory]'s armature PopupBox: the picker stacks
    /// over this modal and pops itself on confirm, landing back here. Confirming the `<none>` row passes a
    /// null accessor through — removing the mapping.
    private void openArmaturePicker(EntityType<?> type, PopupBox<AssetAccessor<? extends Armature>> box) {
        AccessorSelectionModal modal = new AccessorSelectionModal(font, AssetAccessor.Type.ARMATURE, true, accessor -> {
            @SuppressWarnings("unchecked")
            AssetAccessor<? extends Armature> armature = (AssetAccessor<? extends Armature>) accessor;
            ComponentsFactory.getImpl().registerDefaultArmature(type, armature);
            box.setValue(armature);

            // Re-display so the fresh EntityModelComponent picks up the new mapping (the
            // armature is read from ComponentsFactory at display time). The clicked row is
            // always the selected one — MappingRow.mouseClicked selects before routing.
            showPreview(type);
        });

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(modal);
    }

    /// A mapping is displayable when it is absent — the vanilla model carries its own skeleton — or
    /// when its accessor still resolves against the project's armature listing. A dangling accessor
    /// fails to load inside [EntityModelComponent], so such a row is marked instead of previewed.
    private static boolean mappingResolves(EntityType<?> type) {
        AssetAccessor<? extends Armature> armature = ComponentsFactory.getImpl().getExternalArmatureMappingFor(type);
        return armature == null || AccessorSelectionModal.resolves(armature);
    }

    /// Shows the selected row's vanilla model in the right-column previewer. No animation is
    /// pushed onto the component, so the model stands in its bind/default pose. A row whose
    /// mapping doesn't resolve displays nothing, dropping whatever model stood there before.
    private void showPreview(EntityType<?> type) {
        if (!mappingResolves(type)) {
            preview.removeAllComponents(EntityModelComponent.class);
            return;
        }

        preview.displayEntity(
            BuiltInRegistries.ENTITY_TYPE.getKey(type),
            EntityModelComponent.ModelType.VANILLA_ENTITY,
            true, true
        );
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return List.of(closeScreenButton, searchBox, mappingList, preview);
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
                AkytheraEditorLayoutManager.getInstance().closeModal();
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

    /// Scrollable `<entity>  ->  [PopupBox]` rows. `ContainerObjectSelectionList` so each
    /// row hosts its PopupBox as a real child widget (clicks route through `children()`).
    private final class MappingList extends ContainerObjectSelectionList<@NonNull MappingRow> implements AnchoredWidget {
        private final List<EntityType<?>> allTypes;

        // Initial size zeros are harmless — real geometry comes from relocate() against the
        // fraction-sized modal rect (fill via LEFT_RIGHT / TOP_BOTTOM anchors below).
        MappingList(List<EntityType<?>> allTypes) {
            super(Minecraft.getInstance(), 0, 0, LIST_TOP, ROW_H);
            this.allTypes = allTypes;
            rebuild("");
        }

        void rebuild(String query) {
            clearEntries();
            setSelected(null);
            String lower = query.toLowerCase();

            for (EntityType<?> type : allTypes) {
                if (!lower.isEmpty() && !entityLabel(type).toLowerCase().contains(lower)) continue;
                addEntry(new MappingRow(type));
            }

            updateSizeAndPosition(getWidth(), getHeight(), getX(), getY());
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
            if (!isMouseOver(mx, my)) return false;
            return super.mouseScrolled(mx, my, scrollX, scrollY);
        }

        @Override
        public void setSelected(@Nullable MappingRow selected) {
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
        protected void extractSelection(GuiGraphicsExtractor g, MappingRow entry, int outlineColor) {
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
        private Unit y2 = AnchoredWidget.pixel(0);
        private final AnchoredWidget.HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.LEFT_RIGHT;
        private final AnchoredWidget.VerticalAnchorType verticalAnchorType = VerticalAnchorType.TOP_BOTTOM;

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

    /// One mapping row: `<entity id>  ->  [PopupBox]`, following [AnimationRetargetModal]'s
    /// `MappingRow` (label + arrow drawn inline, the child widget re-seated relative to the
    /// modal every frame so header-drags and scrolling stay in sync).
    private final class MappingRow extends ContainerObjectSelectionList.Entry<@NonNull MappingRow> {
        private final EntityType<?> type;
        private final PopupBox<AssetAccessor<? extends Armature>> popupBox;
        private final List<GuiEventListener> children;

        MappingRow(EntityType<?> type) {
            this.type = type;
            this.popupBox = new PopupBox<AssetAccessor<? extends Armature>>(
                font,
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(BOX_H),
                HorizontalAnchorType.LEFT_WIDTH, VerticalAnchorType.TOP_HEIGHT,
                () -> ComponentsFactory.getImpl().getExternalArmatureMappingFor(type),
                _ -> {},
                accessor -> accessor == null ? "" : AccessorSelectionModal.displayName(accessor),
                Component.empty(),
                box -> openArmaturePicker(type, box)
            ).withValidator(AccessorSelectionModal::resolves);

            // The modal flow refreshes via setValue on confirm; seed the display from the
            // current registration (setValue(null) renders the unmapped box as <none>), which
            // also runs the validator — so a dangling mapping is marked as the list is built.
            popupBox.setValue(ComponentsFactory.getImpl().getExternalArmatureMappingFor(type));
            this.children = List.of(popupBox);
        }

        @Override
        public @NonNull List<? extends GuiEventListener> children() {
            return children;
        }

        @Override
        public @NonNull List<? extends NarratableEntry> narratables() {
            return List.of(popupBox);
        }

        /// Any click on the row selects it (→ previews its model); the default routing then
        /// still reaches the PopupBox when its magnifier / display box was hit.
        @Override
        public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
            mappingList.setSelected(this);
            super.mouseClicked(event, doubleClick);
            return true;
        }

        @Override
        public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
            int textBaseY = getY() + (getHeight() - font.lineHeight) / 2;
            int labelX = getX() + 6;

            g.text(font, font.plainSubstrByWidth(entityLabel(type), LABEL_W - 8), labelX, textBaseY, 0xFFCCCCCC, false);
            g.text(font, "->", labelX + LABEL_W, textBaseY, 0xFF888888, false);

            int labelEnd = labelX + LABEL_W + ARROW_W;
            int boxY = getY() + (getHeight() - BOX_H) / 2;
            int boxW = Math.max(0, getX() + getWidth() - labelEnd - 4);
            int modalX = ModelArmatureMappingModal.this.getX();
            int modalY = ModelArmatureMappingModal.this.getY();
            ScreenRectangle modalRect = ModelArmatureMappingModal.this.getRectangle();

            popupBox.setX1(AnchoredWidget.pixel(labelEnd - modalX));
            popupBox.setX2(AnchoredWidget.pixel(boxW));
            popupBox.setY1(AnchoredWidget.pixel(boxY - modalY));
            popupBox.setY2(AnchoredWidget.pixel(BOX_H));
            popupBox.relocate(modalRect);
            popupBox.extractRenderState(g, mx, my, a);
        }
    }
}
