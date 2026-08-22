package com.yesman.akytheralabs.gui.widget.main;

import com.mojang.blaze3d.platform.Window;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akythera.client.api.mesh.MeshManager;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.gui.widget.*;
import com.yesman.akythera.client.gui.widget.viewer.*;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.EntityPreviewModel;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.AdvancedEditBox;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.OverlayingWindowModal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
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
import net.minecraft.world.entity.EntityTypes;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// A modal that picks an existing [AnimationClip] from the project's known
/// animation accessors. The model list on the left lets the user pick which
/// model the previewer renders the animation on; the animation list in the
/// middle lets the user single-click to play the animation, or double-click
/// to confirm and close the modal.
///
/// In multi-select mode the animation list accumulates rows the way the content browser's grid
/// does — ctrl toggles one, shift takes the range from the last anchor — and `OK` reports the
/// whole set.
public final class AnimationSelectModal extends OverlayingWindowModal
    implements ResizeCompleteCallbackListener, CloseableWidget
{
    private static final int TITLE_H = 20;
    private static final int SEARCH_H = 18;
    private static final int FOOTER_H = 26;
    private static final int BUTTON_W = 60;
    private static final int ROW_H = 18;
    private static final int SCROLLBAR_W = 5;
    private static final int DIMMER_COLOR = 0x88000000;
    private static final int MODEL_LIST_PCT = 22;
    private static final int ANIM_LIST_PCT = 40;

    private final Font font;
    private final OnConfirmed onAnimationsConfirmed;
    /// Whether the animation list accumulates rows or keeps only the clicked one.
    private final boolean multiSelect;
    private final AdvancedEditBox<String> modelSearchBox;
    private final AdvancedEditBox<String> animationSearchBox;
    private final ModelList modelList;
    private final AnimList animList;
    private final Model3DViewer preview;
    private final AnchoredButton closeScreenButton;
    private final AnchoredButton confirmButton;

    /// The animations `OK` reports, in click order.
    private final Set<AssetAccessor<AnimationClip>> selectedAnimations = new LinkedHashSet<>();

    /// Row a shift-click measures its range from.
    private AnimList.@Nullable AnimEntry rangeAnchor;

    /// Currently-highlighted model in the left list — reported alongside the animation on confirm so
    /// callers can persist it as the new entry's preview model. Null until the user picks a model.
    @Nullable private EntityPreviewModel selectedModel;

    /// Confirm callback: the chosen animations in click order, plus the preview model the user had
    /// selected (null if they never picked one).
    @FunctionalInterface
    public interface OnConfirmed {
        void accept(List<AssetAccessor<AnimationClip>> animClips, @Nullable EntityPreviewModel previewModel);
    }

    /// `initialModel` (nullable): pre-selects that model in the left list — so the previewer shows
    /// the caller's current preview model on open — instead of starting with nothing selected.
    public AnimationSelectModal(Font font, @Nullable EntityPreviewModel initialModel, OnConfirmed onAnimationsConfirmed) {
        this(font, initialModel, false, onAnimationsConfirmed);
    }

    /// `multiSelect` lets the animation list hold more than one row, so one confirm reports a batch.
    public AnimationSelectModal(
        Font font,
        @Nullable EntityPreviewModel initialModel,
        boolean multiSelect,
        OnConfirmed onAnimationsConfirmed
    ) {
        super(
            AnchoredWidget.fraction(90), AnchoredWidget.fraction(80),
            Component.translatable(LangKeys.GUI_ANIMATION_SELECT_MODAL_TITLE)
        );

        this.font = font;
        this.onAnimationsConfirmed = onAnimationsConfirmed;
        this.multiSelect = multiSelect;

        this.modelList = new ModelList();
        this.modelList.rebuild("");

        this.animList = new AnimList();
        this.animList.rebuild("");

        this.modelSearchBox = AdvancedEditBox.stringBox(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.fraction(MODEL_LIST_PCT),
            AnchoredWidget.pixel(TITLE_H), AnchoredWidget.pixel(SEARCH_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH, AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            modelList::rebuild,
            Component.translatable(LangKeys.GUI_MESSAGE_EMPTY_SEARCHBAR)
        );

        this.animationSearchBox = AdvancedEditBox.stringBox(
            font,
            AnchoredWidget.fraction(MODEL_LIST_PCT), AnchoredWidget.fraction(ANIM_LIST_PCT),
            AnchoredWidget.pixel(TITLE_H), AnchoredWidget.pixel(SEARCH_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH, AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            animList::rebuild,
            Component.translatable(LangKeys.GUI_MESSAGE_EMPTY_SEARCHBAR)
        );

        this.preview = new Model3DViewer(
            AnchoredWidget.fraction(MODEL_LIST_PCT + ANIM_LIST_PCT), AnchoredWidget.fraction(0),
            AnchoredWidget.pixel(TITLE_H + SEARCH_H), AnchoredWidget.pixel(FOOTER_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            AkytheraEditorLayoutManager.getInstance()::getLayoutGuiScale
        );

        preview.addComponent(new CameraComponent(true));
        preview.addComponent(new BackgroundComponent(18, 18, 18, 255));
        preview.addComponent(new GridFloorComponent(1.0F, 6, 68, 68, 68, 255));

        this.closeScreenButton = AnchoredButton
            .buttonBuilder(font, Component.literal("X"), _ -> AkytheraEditorLayoutManager.getInstance().closeModal())
            .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(TITLE_H))
            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(TITLE_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .theme(AkytheraLabsButtonTheme.TOP_MENU_BUTTON)
            .build();

        this.confirmButton = AnchoredButton
            .buttonBuilder(font, Component.translatable(LangKeys.GUI_OK), _ -> confirm())
            .xUnits(AnchoredWidget.pixel(6), AnchoredWidget.pixel(BUTTON_W))
            .yUnits(AnchoredWidget.pixel(4), AnchoredWidget.pixel(FOOTER_H - 10))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();

        // After the previewer exists — selecting a row displays the model on it.
        if (initialModel != null) {
            getModelRowById(initialModel).ifPresent(modelList::setSelected);
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
    public @NonNull List<AbstractWidget> children() {
        return List.of(modelSearchBox, animationSearchBox, modelList, animList, preview, closeScreenButton, confirmButton);
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
        return modelList.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
            || animList.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
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

        for (AbstractWidget aw : children()) {
            aw.extractRenderState(graphics, mouseX, mouseY, a);
        }
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
    }

    /// Re-applies the currently-selected animation onto the previewer's
    /// `EntityModelComponent`, if both are present.
    private void replaySelectedAnimation() {
        AnimList.@Nullable Entry sel = animList.getSelected();
        if (!(sel instanceof AnimList.AnimEntry ae)) return;

        EntityModelComponent c = preview.getComponent(EntityModelComponent.class);
        if (c != null) c.setSimplePlayingAnimation(ae.accessor());
    }

    /// Takes `clicked` into [#selectedAnimations] the way the modifiers ask: ctrl toggles that one
    /// row, shift takes the range from [#rangeAnchor], a bare click keeps only that row. A
    /// single-select modal always keeps only that row.
    private void selectAnimation(AnimList.AnimEntry clicked, MouseButtonEvent event) {
        if (multiSelect && event.hasControlDown()) {
            if (!selectedAnimations.remove(clicked.accessor())) selectedAnimations.add(clicked.accessor());
            rangeAnchor = clicked;
            return;
        }

        if (multiSelect && event.hasShiftDown() && rangeAnchor != null) {
            List<AnimList.Entry> rows = animList.children();
            int from = rows.indexOf(rangeAnchor);
            int to = rows.indexOf(clicked);

            if (from >= 0 && to >= 0) {
                selectedAnimations.clear();

                for (int i = Math.min(from, to); i <= Math.max(from, to); i++) {
                    if (rows.get(i) instanceof AnimList.AnimEntry row) selectedAnimations.add(row.accessor());
                }
            }

            return;
        }

        selectedAnimations.clear();
        selectedAnimations.add(clicked.accessor());
        rangeAnchor = clicked;
    }

    /// Reports the selection and closes. Selecting nothing is not a confirmable state — the modal
    /// has no way back once it reports.
    private void confirm() {
        if (selectedAnimations.isEmpty()) return;

        onAnimationsConfirmed.accept(List.copyOf(selectedAnimations), selectedModel);
        AkytheraEditorLayoutManager.getInstance().closeModal();
    }

    /// ---------------- Model rows ----------------

    private sealed interface ModelRow permits TitleRow, SkeletalMeshRow, VanillaModelRow {
        Component label();

        boolean selectable();
    }

    private record TitleRow(Component label) implements ModelRow {
        @Override
        public boolean selectable() {
            return false;
        }
    }

    private record SkeletalMeshRow(AssetAccessor<SkeletalMesh> accessor) implements ModelRow {
        @Override
        public Component label() {
            return Component.literal(accessor.identifier().toString());
        }

        @Override
        public boolean selectable() {
            return true;
        }
    }

    private record VanillaModelRow(EntityType<?> type, LivingEntityRenderer<?, ?, ?> renderer) implements ModelRow {
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
    private List<ModelRow> buildModelCatalog(String query) {
        String lower = query.toLowerCase();
        List<ModelRow> rows = new ArrayList<>();

        List<SkeletalMeshRow> skeletal = Stream.concat(
                MeshManager.getInstance().listRegisteredAccessors(),
                AkytheraProject.getCurrentProject().listIssuedEditorMeshAccessors()
            )
            .filter(acc -> acc.checkType(AssetAccessor.Type.SKELETAL_MESH))
            .collect(Collectors.toMap(AssetAccessor::identifier, acc -> acc, (a, _b) -> a, LinkedHashMap::new))
            .values().stream()
            .map(acc -> new SkeletalMeshRow((AssetAccessor<SkeletalMesh>) acc))
            .filter(row -> lower.isEmpty() || row.accessor().identifier().toString().toLowerCase().contains(lower))
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
                Stream.of(new VanillaModelRow(EntityTypes.PLAYER, dispatcher.playerRenderers.get(DefaultPlayerSkin.getDefaultSkin().model())))
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

    /// Model-list row matching an [EntityPreviewModel] (mirrors [ModelSelectModal]'s `getRowById`).
    private Optional<ModelList.@NonNull Entry> getModelRowById(EntityPreviewModel model) {
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

    private void onSelectModelRow(ModelRow row) {
        switch (row) {
            case SkeletalMeshRow sr -> {
                selectedModel = new EntityPreviewModel(sr.accessor().identifier(), EntityModelComponent.ModelType.SKELETAL_MESH);
                EntityPreviewModel.display(preview, selectedModel, true, true);
            }
            case VanillaModelRow vr -> {
                selectedModel = new EntityPreviewModel(BuiltInRegistries.ENTITY_TYPE.getKey(vr.type()), EntityModelComponent.ModelType.VANILLA_ENTITY);
                EntityPreviewModel.display(preview, selectedModel, true, true);
            }
            case TitleRow _ -> {}
        }

        // Re-apply the currently-selected animation onto the new model.
        replaySelectedAnimation();
    }

    private final class ModelList extends AbstractSelectionList<ModelList.@NonNull Entry> implements AnchoredWidget {
        ModelList() {
            super(Minecraft.getInstance(), 0, 0, 0, ROW_H);
        }

        void rebuild(String query) {
            clearEntries();

            for (ModelRow row : buildModelCatalog(query)) {
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
            if (selected != null) onSelectModelRow(selected.getRow());
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
        }

        abstract static class Entry extends AbstractSelectionList.Entry<@NonNull Entry> {
            abstract ModelRow getRow();
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
            public ModelRow getRow() {
                return row;
            }
        }

        final class ModelEntry extends Entry {
            private final ModelRow row;

            ModelEntry(ModelRow row) {
                this.row = row;
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
                if (getSelected() != this && hovered) {
                    g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF262626);
                }

                g.text(font, row.label(), getX() + 6, getY() + (getHeight() - 8) / 2, getSelected() == this ? 0xFFFFFFFF : 0xFFCCCCCC, false);
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClicked) {
                setSelected(this);
                return true;
            }

            @Override
            public ModelRow getRow() {
                return row;
            }
        }

        // ***************************************************************
        // [AnchoredWidget] implementations
        // ***************************************************************
        private Unit x1 = AnchoredWidget.pixel(0);
        private Unit x2 = AnchoredWidget.fraction(MODEL_LIST_PCT);
        private Unit y1 = AnchoredWidget.pixel(TITLE_H + SEARCH_H);
        private Unit y2 = AnchoredWidget.pixel(FOOTER_H);
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

    private final class AnimList extends AbstractSelectionList<AnimList.@NonNull Entry> implements AnchoredWidget {
        AnimList() {
            super(Minecraft.getInstance(), 0, 0, 0, ROW_H);
        }

        void rebuild(String query) {
            clearEntries();

            // Title row for the animationsToReplace category
            addEntry(new TitleEntry(Component.translatable(LangKeys.GUI_ANIMATION_SELECT_MODAL_CATEGORY_ANIMATIONS)));

            String lower = ParseUtils.toLowerCase(query);

            AkytheraProject.getCurrentProject().listAllAnimationAccessors()
                .collect(Collectors.toMap(AssetAccessor::identifier, acc -> acc, (a, _) -> a, LinkedHashMap::new))
                .values().stream()
                .filter(accessor -> lower.isEmpty() || ParseUtils.toLowerCase(accessor.identifier()).contains(lower))
                .sorted(Comparator.comparing(acc -> acc.identifier().toString()))
                .forEach(acc -> addEntry(new AnimEntry(acc)));

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
            // vertical separator
            graphics.fill(getX() - 1, getY(), getX(), getBottom(), 0xFF333333);
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
        protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
        }

        abstract static class Entry extends AbstractSelectionList.Entry<@NonNull Entry> {
        }

        final class TitleEntry extends Entry {
            private final Component label;

            TitleEntry(Component label) {
                this.label = label;
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
                g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF141414);
                g.text(font, label, getX() + 6, getY() + (getHeight() - 8) / 2, 0xFF888888, false);
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent ev, boolean dc) {
                return false;
            }
        }

        final class AnimEntry extends Entry {
            private final AssetAccessor<AnimationClip> accessor;

            AnimEntry(AssetAccessor<AnimationClip> accessor) {
                this.accessor = accessor;
            }

            AssetAccessor<AnimationClip> accessor() {
                return accessor;
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
                boolean picked = selectedAnimations.contains(accessor);

                if (getSelected() != this && (picked || hovered)) {
                    g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), picked ? 0xFF0A2833 : 0xFF262626);
                }

                g.text(
                    font, Component.literal(accessor.identifier().toString().replace(AnimationManager.DIRECTORY, "")),
                    getX() + 6, getY() + (getHeight() - 8) / 2,
                    getSelected() == this || picked ? 0xFFFFFFFF : 0xFFCCCCCC, false
                );
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClicked) {
                setSelected(this);
                selectAnimation(this, event);

                // Single click → play the animation in the previewer.
                EntityModelComponent c = preview.getComponent(EntityModelComponent.class);
                if (c != null) c.setSimplePlayingAnimation(accessor);

                if (doubleClicked) confirm();

                return true;
            }
        }

        // ***************************************************************
        // [AnchoredWidget] implementations
        // ***************************************************************
        private Unit x1 = AnchoredWidget.fraction(MODEL_LIST_PCT);
        private Unit x2 = AnchoredWidget.fraction(ANIM_LIST_PCT);
        private Unit y1 = AnchoredWidget.pixel(TITLE_H + SEARCH_H);
        private Unit y2 = AnchoredWidget.pixel(FOOTER_H);
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
