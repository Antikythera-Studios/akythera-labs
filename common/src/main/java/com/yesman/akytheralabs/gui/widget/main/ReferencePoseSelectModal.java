package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.gui.widget.AnchoredButton;
import com.yesman.akythera.client.gui.widget.CloseableWidget;
import com.yesman.akythera.client.gui.widget.ResizeCompleteCallbackListener;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.client.gui.widget.viewer.BackgroundComponent;
import com.yesman.akythera.client.gui.widget.viewer.CameraComponent;
import com.yesman.akythera.client.gui.widget.viewer.EntityModelComponent;
import com.yesman.akythera.client.gui.widget.viewer.GridFloorComponent;
import com.yesman.akythera.client.gui.widget.viewer.Model3DViewer;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.asset.AnimationMetadataHolder;
import com.yesman.akytheralabs.akythera.asset.ReferencePose;
import com.yesman.akytheralabs.dataholder.EntityPreviewModel;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.AdvancedEditBox;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.OverlayingWindowModal;
import com.yesman.akytheralabs.gui.widget.animation.AnimationScrubBar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/// Picks a [ReferencePose] — the animation *and* the timestamp on it — in one gesture.
///
/// Takes the edited animation's accessor, which carries both halves the picker needs: its
/// [AnimationMetadataHolder] and, through [AssetAccessor#get], the clip itself.
///
/// The left list offers every animation that does not close a reference cycle with the one being
/// edited ([AnimationMetadataHolder#formsCycle]), headed by a `<none>` row that clears the pose. The
/// right pane previews the picked clip on the holder's preview model, which this modal only displays:
/// choosing a different model is the editor screen's job, not the picker's. The scrub bar drives the
/// animator's elapsed time, and `OK` reports whatever the track was left at.
public final class ReferencePoseSelectModal extends OverlayingWindowModal
    implements ResizeCompleteCallbackListener, CloseableWidget
{
    private static final int WIDTH = 800;
    private static final int HEIGHT = 480;
    private static final int TITLE_H = 20;
    private static final int SEARCH_H = 18;
    private static final int FOOTER_H = 26;
    private static final int SCRUB_H = 12;
    private static final int ROW_H = 16;
    private static final int SCROLLBAR_W = 5;
    private static final int BUTTON_W = 60;

    private final Font font;
    private final AkytheraProject.EditorAnimationAccessor edited;
    private final Consumer<ReferencePose> onConfirm;
    private final AdvancedEditBox<String> searchBox;
    private final AnimationList animationList;
    private final Model3DViewer preview;
    private final AnimationScrubBar scrubBar;
    private final AnchoredButton okButton;
    private final AnchoredButton cancelButton;

    @Nullable
    private AssetAccessor<AnimationClip> picked;

    private float timeStamp;

    public ReferencePoseSelectModal(Font font, AkytheraProject.EditorAnimationAccessor edited, Consumer<ReferencePose> onConfirm) {
        super(
            AnchoredWidget.pixel(WIDTH),
            AnchoredWidget.pixel(HEIGHT),
            Component.translatable(LangKeys.GUI_REFERENCE_POSE_MODAL_TITLE)
        );

        AnimationMetadataHolder metadata = edited.metadata();
        ReferencePose current = metadata.getReferencePose();

        this.font = font;
        this.edited = edited;
        this.onConfirm = onConfirm;
        this.picked = current.clip();
        this.timeStamp = current.timeStamp();

        this.animationList = new AnimationList();
        this.searchBox = AdvancedEditBox.stringBox(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.fraction(45),
            AnchoredWidget.pixel(TITLE_H), AnchoredWidget.pixel(SEARCH_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            animationList::rebuild,
            Component.translatable(LangKeys.GUI_MESSAGE_EMPTY_SEARCHBAR)
        );

        this.preview = new Model3DViewer(
            AnchoredWidget.pixel(0), AnchoredWidget.fraction(55),
            AnchoredWidget.pixel(TITLE_H), AnchoredWidget.pixel(FOOTER_H + SCRUB_H),
            AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            AkytheraEditorLayoutManager.getInstance()::getLayoutGuiScale
        );

        preview.addComponent(new CameraComponent(true));
        preview.addComponent(new BackgroundComponent(18, 18, 18, 255));
        preview.addComponent(new GridFloorComponent(1.0F, 6, 68, 68, 68, 255));
        EntityPreviewModel.display(preview, metadata.getPreviewModel(), true, true);

        this.scrubBar = new AnimationScrubBar(
            AnchoredWidget.pixel(0), AnchoredWidget.fraction(55),
            AnchoredWidget.pixel(FOOTER_H), AnchoredWidget.pixel(SCRUB_H),
            AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH,
            AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT,
            this::totalSeconds,
            () -> timeStamp,
            this::scrubTo
        );

        this.cancelButton = AnchoredButton
            .buttonBuilder(font, Component.translatable(LangKeys.GUI_CANCEL),
                _ -> AkytheraEditorLayoutManager.getInstance().closeModal())
            .xUnits(AnchoredWidget.pixel(6), AnchoredWidget.pixel(BUTTON_W))
            .yUnits(AnchoredWidget.pixel(4), AnchoredWidget.pixel(FOOTER_H - 10))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();

        this.okButton = AnchoredButton
            .buttonBuilder(font, Component.translatable(LangKeys.GUI_OK), _ -> confirm())
            .xUnits(AnchoredWidget.pixel(10 + BUTTON_W), AnchoredWidget.pixel(BUTTON_W))
            .yUnits(AnchoredWidget.pixel(4), AnchoredWidget.pixel(FOOTER_H - 10))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();

        animationList.rebuild("");
        applyPickedToPreview();
    }

    /// The candidates: every animation the project can see, minus the ones whose reference pose
    /// chain reaches the animation being edited.
    private List<AssetAccessor<AnimationClip>> buildCatalog(String query) {
        String lower = query.toLowerCase();

        return AkytheraProject.getCurrentProject().listAllAnimationAccessors()
            .filter(accessor -> !AnimationMetadataHolder.formsCycle(edited.identifier(), accessor.identifier()))
            .filter(accessor -> lower.isEmpty() || ParseUtils.toLowerCase(accessor.identifier()).contains(lower))
            .sorted(Comparator.comparing(accessor -> accessor.identifier().toString()))
            .toList();
    }

    private float totalSeconds() {
        AnimationClip clip = picked == null ? null : picked.get();

        return clip == null ? 0.0F : clip.getTotalTime();
    }

    private void scrubTo(double seconds) {
        timeStamp = (float) seconds;

        EntityModelComponent component = preview.getComponent(EntityModelComponent.class);

        if (component != null && component.getAnimator() instanceof EntityModelComponent.EditorScrubAnimator scrubAnimator) {
            scrubAnimator.setElapsedTime(timeStamp);
        }
    }

    /// Plays the picked clip on the preview model, frozen so the scrub bar is the only thing that
    /// moves the pose. A cleared pick hands the animator no clip, which poses the model at its bind
    /// pose rather than leaving it frozen in the last pose it was scrubbed to.
    private void applyPickedToPreview() {
        EntityModelComponent component = preview.getComponent(EntityModelComponent.class);

        if (component == null) return;

        component.setSimplePlayingAnimation(picked, false);
        scrubTo(Mth.clamp(timeStamp, 0.0F, totalSeconds()));
    }

    /// `<none>` confirms like any other row, as [ReferencePose#EMPTY] — the timestamp belongs to the
    /// clip, so clearing one clears the other.
    private void confirm() {
        onConfirm.accept(picked == null ? ReferencePose.EMPTY : new ReferencePose(picked, timeStamp));
        AkytheraEditorLayoutManager.getInstance().closeModal();
    }

    @Override
    public boolean isMouseOverHeader(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getRight() && mouseY >= getY() && mouseY < getY() + TITLE_H;
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
        return List.of(searchBox, animationList, preview, scrubBar, okButton, cancelButton);
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return animationList.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
            || preview.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (event.isEscape()) {
            AkytheraEditorLayoutManager.getInstance().closeModal();
            return true;
        }

        if (event.isConfirmation()) {
            confirm();
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
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractWidgetRenderState(graphics, mouseX, mouseY, a);

        graphics.fill(getX(), getY(), getRight(), getBottom(), 0xFF1E1E1E);
        graphics.outline(getX(), getY(), getWidth(), getHeight(), 0xFF3A3A3A);

        graphics.fill(getX(), getY(), getRight(), getY() + TITLE_H, 0xFF2A2A2A);
        graphics.text(font, getMessage(), getX() + 8, getY() + (TITLE_H - font.lineHeight) / 2, 0xFFCCCCCC, false);

        searchBox.extractRenderState(graphics, mouseX, mouseY, a);
        animationList.extractRenderState(graphics, mouseX, mouseY, a);
        preview.extractRenderState(graphics, mouseX, mouseY, a);
        scrubBar.extractRenderState(graphics, mouseX, mouseY, a);
        okButton.extractRenderState(graphics, mouseX, mouseY, a);
        cancelButton.extractRenderState(graphics, mouseX, mouseY, a);

        graphics.text(
            font,
            Component.translatable(LangKeys.GUI_REFERENCE_POSE_MODAL_TIME, ParseUtils.floatingPointToString(timeStamp)),
            getX() + 8,
            getBottom() - FOOTER_H + (FOOTER_H - font.lineHeight) / 2,
            0xFFCCCCCC,
            false
        );

        if (preview.getComponent(EntityModelComponent.class) == null) {
            Component message = Component.translatable(LangKeys.GUI_ANIMATION_PREVIEWER_MODAL_EMPTY);
            graphics.text(
                font, message,
                preview.getX() + preview.getWidth() / 2 - font.width(message) / 2,
                preview.getY() + preview.getHeight() / 2 - font.lineHeight / 2,
                0xFFAAAAAA, false
            );
        }
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }

    private final class AnimationList extends AbstractSelectionList<AnimationList.@NonNull Entry> implements AnchoredWidget {
        AnimationList() {
            super(Minecraft.getInstance(), 0, 0, 0, ROW_H);
        }

        /// The `<none>` row heads the list whatever the query says — clearing the reference pose has
        /// to stay reachable from any search state, and it is not an animation to filter.
        void rebuild(String query) {
            clearEntries();

            Entry none = new Entry(null);
            addEntry(none);

            if (picked == null) {
                setSelected(none);
            }

            for (AssetAccessor<AnimationClip> accessor : buildCatalog(query)) {
                Entry entry = new Entry(accessor);
                addEntry(entry);

                if (picked != null && picked.identifier().equals(accessor.identifier())) {
                    setSelected(entry);
                }
            }

            updateSizeAndPosition(getWidth(), getHeight(), getX(), getY());
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            return isMouseOver(mouseX, mouseY) && super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
        protected void extractSelection(GuiGraphicsExtractor graphics, Entry entry, int outlineColor) {
            graphics.fill(entry.getX(), entry.getY(), entry.getContentRight(), entry.getY() + entry.getContentHeight(), 0xFF0A2833);
            graphics.outline(entry.getX(), entry.getY(), entry.getContentWidth(), entry.getHeight(), 0xFF37D5FF);
        }

        @Override
        public void setSelected(AnimationList.@Nullable Entry selected) {
            super.setSelected(selected);

            if (selected == null) return;

            picked = selected.accessor;
            timeStamp = 0.0F;
            applyPickedToPreview();
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
        }

        final class Entry extends AbstractSelectionList.Entry<@NonNull Entry> {
            /// Null on the `<none>` row, which clears the reference pose rather than picking one.
            private final @Nullable AssetAccessor<AnimationClip> accessor;

            Entry(@Nullable AssetAccessor<AnimationClip> accessor) {
                this.accessor = accessor;
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
                if (getSelected() != this && hovered) {
                    graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF262626);
                }

                graphics.text(
                    font,
                    Component.literal(accessor == null
                        ? AccessorSelectionModal.NONE_LABEL
                        : AccessorSelectionModal.displayName(accessor)),
                    getX() + 6,
                    getY() + (getHeight() - 8) / 2,
                    getSelected() == this ? 0xFFFFFFFF : 0xFFCCCCCC,
                    false
                );
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
                setSelected(this);
                return true;
            }
        }

        /// ***************************************************************
        /// [AnchoredWidget] implementations                              *
        /// ***************************************************************
        private Unit x1 = AnchoredWidget.pixel(0);
        private Unit x2 = AnchoredWidget.fraction(45);
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
