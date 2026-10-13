package com.yesman.akytheralabs.gui.panel;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.yesman.akythera.client.api.gui.ResizeCompleteCallbackListener;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class AdjustablePanelManager extends AbstractContainerWidget implements AnchoredWidget {
    public static final int DIVIDER_LENGTH = 2;

    private final PanelSection root = new PanelSection(this);
    private final List<PanelDivider> panelDividers = new ArrayList<>();
    private final List<PanelSection> leafSections = new ArrayList<>();

    @Nullable
    private PanelDivider draggingPanelDivider;

    public AdjustablePanelManager(
        Unit x1,
        Unit x2,
        Unit y1,
        Unit y2,
        AnchoredWidget.HorizontalAnchorType horizontalAnchorType,
        AnchoredWidget.VerticalAnchorType verticalAnchorType
    ) {
        super(0, 0, 0, 0, Component.empty(), AbstractScrollArea.defaultSettings(0)); // No scroll

        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchorType;
        this.verticalAnchorType = verticalAnchorType;

        leafSections.add(root);
    }

    public PanelSection.SetupProxy getRoot() {
        return root.setupProxy;
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return List.of();
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        for (PanelDivider panelDivider : panelDividers) {
            panelDivider.extractWidgetRenderState(graphics, mouseX, mouseY, a);
        }

        for (PanelSection panelSection : leafSections) {
            panelSection.panelArea.extractRenderState(graphics, mouseX, mouseY, a);
        }

        // On dragging, switch to the dragging divider's cursor icon
        if (draggingPanelDivider != null) {
            graphics.requestCursor(draggingPanelDivider.getHoveringCursor());
            return;
        }

        // When no dragging dividers, find any hovering divider
        for (PanelDivider divider : panelDividers) {
            if (divider.isMouseOver(mouseX, mouseY)) {
                graphics.requestCursor(divider.getHoveringCursor());
            }
        }
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        for (PanelDivider divider : panelDividers) {
            if (divider.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }

        for (PanelSection panelSection : leafSections) {
            if (!panelSection.panelArea.ambientArea() && panelSection.panelArea.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void setFocused(final @Nullable GuiEventListener focused) {
        // Re-focusing the already-focused child clears the focus in vanilla's implementation.
        if (getFocused() == focused) {
            return;
        }

        super.setFocused(focused);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        for (PanelDivider divider : panelDividers) {
            if (divider.mouseClicked(event, doubleClick)) {
                draggingPanelDivider = divider;
                setFocused(divider);
                setDragging(true);
                return true;
            }
        }

        for (PanelSection panelSection : leafSections) {
            PanelArea area = panelSection.panelArea;
            if (area.ambientArea() || !area.isMouseOver(event.x(), event.y())) continue;

            // Focus the clicked area regardless of whether a child consumed the click.
            boolean consumed = area.mouseClicked(event, doubleClick);
            setFocused(area);

            if (consumed && AkytheraEditorLayoutManager.acceptsDragButton(event)) {
                setDragging(true);
            }

            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (draggingPanelDivider != null) {
            // Notify divider dragging finished
            draggingPanelDivider.getFirstSection().notifyResized();
            draggingPanelDivider.getSecondSection().notifyResized();
            draggingPanelDivider.mouseReleased(event);
            draggingPanelDivider = null;
            setDragging(false);
            return true;
        }

        boolean handled = false;

        if (AkytheraEditorLayoutManager.acceptsDragButton(event) && isDragging()) {
            setDragging(false);
            GuiEventListener focused = getFocused();

            if (focused != null) {
                handled = focused.mouseReleased(event);
            }
        }

        return handled;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (draggingPanelDivider != null) {
            draggingPanelDivider.mouseDragged(event, dx, dy);
            return true;
        }

        GuiEventListener focused = getFocused();

        return focused != null && isDragging() && AkytheraEditorLayoutManager.acceptsDragButton(event) && focused.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        for (PanelSection panelSection : leafSections) {
            PanelArea area = panelSection.panelArea;
            if (area.ambientArea() || !area.isMouseOver(x, y)) continue;
            if (area.mouseScrolled(x, y, scrollX, scrollY)) return true;
        }

        return false;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        // Fan out to every panel so global shortcuts (e.g. Ctrl+A in ContentBrowserPanel)
        // fire regardless of which area currently owns focus.
        for (PanelSection panelSection : leafSections) {
            if (panelSection.panelArea.keyPressed(event)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean keyReleased(@NonNull KeyEvent event) {
        GuiEventListener focused = getFocused();
        return focused != null && focused.keyReleased(event);
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        GuiEventListener focused = getFocused();
        return focused != null && focused.charTyped(event);
    }

    @Override
    public void relocate(ScreenRectangle screenRectangle) {
        AnchoredWidget.super.relocate(screenRectangle);
        root.resize(getRectangle());
        root.completeResize();
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }

    public List<Integer> serializePanelDividerPositions() {
        return panelDividers.stream().map(PanelDivider::getRatio).toList();
    }

    public static class PanelSection {
        private final AdjustablePanelManager owner;
        @Nullable
        private Consumer<ScreenRectangle> widgetResizeCallback;
        @Nullable
        private Consumer<ScreenRectangle> widgetResizeDoneCallback;
        private List<PanelDivider> dividers;
        @Nullable
        private List<PanelSection> subsections;

        // Init with empty area
        private PanelArea panelArea = new PanelArea() {
            @Override
            protected void onPanelResized(ScreenRectangle screenRectangle) {
            }

            @Override
            protected boolean ambientArea() {
                return true;
            }
        };

        /// Used for initializing section information at outer class (typically screen)
        private final SetupProxy setupProxy = new SetupProxy();

        protected PanelSection(AdjustablePanelManager owner) {
            this.owner = owner;
        }

        private List<SetupProxy> subdivide(int subdivisions, PanelDivider.Orientation orientation, int... ratio) {
            if (subdivisions <= 0) {
                throw new IllegalArgumentException("Subdivisions must be bigger than 0");
            }

            if (subdivisions != ratio.length) {
                throw new IllegalArgumentException("Subdivisions and number of ratios must match!");
            }

            owner.leafSections.remove(this);

            ImmutableList.Builder<@NonNull PanelDivider> dividers = ImmutableList.builder();
            ImmutableList.Builder<@NonNull PanelSection> sections = ImmutableList.builder();

            // Add first section
            sections.add(new PanelSection(owner));

            for (int i = 0; i < subdivisions; i++) {
                sections.add(new PanelSection(owner));

                PanelDivider divider = switch (orientation) {
                    case HORIZONTAL ->
                        new PanelDivider(
                            i, this, orientation,
                            (order, s1, s2, newY) -> {
                                s1.panelArea.setHeight(newY - s1.panelArea.getY());
                                s2.panelArea.setY(Math.max(newY, s1.panelArea.getY() + s1.panelArea.getHeight()) + DIVIDER_LENGTH);
                                s2.panelArea.setHeight(
                                    (order == this.dividers.size() - 1 ?
                                        panelArea.getBottom() :
                                        this.subsections.get(order + 2).panelArea.getY() - DIVIDER_LENGTH
                                    )
                                    - s2.panelArea.getY());
                                s1.resize(s1.panelArea.getRectangle());
                                s2.resize(s2.panelArea.getRectangle());
                            },
                            AnchoredWidget.pixel(0),
                            AnchoredWidget.pixel(0),
                            AnchoredWidget.fraction(ratio[i]),
                            AnchoredWidget.pixel(DIVIDER_LENGTH),
                            HorizontalAnchorType.LEFT_RIGHT,
                            VerticalAnchorType.TOP_HEIGHT
                        );
                    case VERTICAL ->
                        new PanelDivider(
                            i, this, orientation,
                            (order, s1, s2, newX) -> {
                                s1.panelArea.setWidth(newX - s1.panelArea.getX());
                                s2.panelArea.setX(Math.max(newX, s1.panelArea.getX() + s1.panelArea.getWidth()) + DIVIDER_LENGTH);
                                s2.panelArea.setWidth(
                                    (order == this.dividers.size() - 1 ?
                                        panelArea.getRight() :
                                        this.subsections.get(order + 2).panelArea.getX() - DIVIDER_LENGTH)
                                    - s2.panelArea.getX()
                                );
                                s1.resize(s1.panelArea.getRectangle());
                                s2.resize(s2.panelArea.getRectangle());
                            },
                            AnchoredWidget.fraction(ratio[i]),
                            AnchoredWidget.pixel(DIVIDER_LENGTH),
                            AnchoredWidget.pixel(0),
                            AnchoredWidget.pixel(0),
                            HorizontalAnchorType.LEFT_WIDTH,
                            VerticalAnchorType.TOP_BOTTOM
                        );
                };

                dividers.add(divider);
                owner.panelDividers.add(divider);
            }

            this.dividers = dividers.build();
            this.subsections = sections.build();
            owner.leafSections.addAll(this.subsections);

            return this.subsections.stream().map(section -> section.setupProxy).toList();
        }

        /// Relocate dividers with previous ratio in newer screen rectangle
        protected void resize(ScreenRectangle screenRectangle) {
            panelArea.relocate(screenRectangle);
            if (widgetResizeCallback != null) widgetResizeCallback.accept(screenRectangle);

            // Propagate resizing toc child sections & dividers
            if (dividers != null && subsections != null) {
                // Init subsections to fit this section, then resize by panels
                for (PanelSection childSection : subsections) {
                    childSection.panelArea.setX(screenRectangle.left());
                    childSection.panelArea.setY(screenRectangle.top());
                    childSection.panelArea.setWidth(screenRectangle.width());
                    childSection.panelArea.setHeight(screenRectangle.height());
                }

                for (PanelDivider divider : dividers) {
                    divider.relocate(screenRectangle);

                    // Resize sections by relocated dividers
                    // This eventually call resize() of sub sections
                    divider.resizeSections();

                    // Match divider to section's last position if mismatches
                    // Caused by min width, height of sections
                    switch (divider.orientation) {
                        case HORIZONTAL -> {
                            if (divider.getY() != subsections.get(divider.subdivisionOrder).panelArea.getBottom()) {
                                divider.setY(subsections.get(divider.subdivisionOrder).panelArea.getBottom());
                                divider.setY1(AnchoredWidget.fraction((int)((divider.getY() - panelArea.getY()) / (float)panelArea.getHeight() * 100.0F)));
                            }
                        }
                        case VERTICAL -> {
                            if (divider.getX() != subsections.get(divider.subdivisionOrder).panelArea.getRight()) {
                                divider.setX(subsections.get(divider.subdivisionOrder).panelArea.getRight());
                                divider.setX1(AnchoredWidget.fraction((int)((divider.getX() - panelArea.getX()) / (float)panelArea.getWidth() * 100.0F)));
                            }
                        }
                    }
                }
            }
        }

        /// Iterates child that implements [ResizeCompleteCallbackListener]
        public void completeResize() {
            if (widgetResizeDoneCallback != null) {
                widgetResizeDoneCallback.accept(panelArea.getRectangle());
            }

            for (GuiEventListener guiEventListener : panelArea.children()) {
                if (guiEventListener instanceof ResizeCompleteCallbackListener resizableCompleteWidget) {
                    resizableCompleteWidget.onResizeCompleted();
                }
            }

            if (subsections != null) {
                for (PanelSection subsection : subsections) {
                    subsection.completeResize();
                }
            }
        }

        /// On panel resized. Iterate child sections to notify resize
        public void notifyResized() {
            if (widgetResizeDoneCallback != null) {
                widgetResizeDoneCallback.accept(panelArea.getRectangle());
            }

            if (subsections == null) {
                return;
            }

            for (PanelSection ps : subsections) {
                ps.notifyResized();
            }
        }

        /// Decouple builder methods from widget
        public final class SetupProxy {
            public SetupProxy resizeCallback(Consumer<ScreenRectangle> resizeCallback) {
                PanelSection.this.widgetResizeCallback = resizeCallback;
                return this;
            }

            public SetupProxy resizeFinishCallback(Consumer<ScreenRectangle> widgetResizeDoneCallback) {
                PanelSection.this.widgetResizeDoneCallback = widgetResizeDoneCallback;
                return this;
            }

            public SetupProxy area(PanelArea panelArea) {
                PanelSection.this.panelArea = panelArea;
                return this;
            }

            public SetupProxy backgroundColor(int color) {
                PanelSection.this.panelArea.bgColor = color;
                return this;
            }

            /// Subdivide the space and creates panel dividers
            ///
            /// Must be called only once. Otherwise, the existing child will be removed
            public List<SetupProxy> subdivide(int subdivisions, PanelDivider.Orientation orientation, int... ratio) {
                return PanelSection.this.subdivide(subdivisions, orientation, ratio);
            }
        }
    }

    public static abstract class PanelArea extends AbstractContainerWidget {
        private final List<AbstractWidget> children = new ArrayList<>();
        protected int bgColor = 0xFF1E1E1E;

        public PanelArea() {
            super(0, 0, 0, 0, Component.empty(), AbstractScrollArea.defaultSettings(4));
        }

        @Override
        protected int contentHeight() {
            return getHeight();
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            graphics.fill(getX(), getY(), getRight(), getBottom(), bgColor);

            for (AbstractWidget aw : children) {
                aw.extractRenderState(graphics, mouseX, mouseY, a);
            }
        }

        /// Called on panel resized both divider dragging and screen resizing
        ///
        /// @param screenRectangle rectangle of panel
        protected abstract void onPanelResized(ScreenRectangle screenRectangle);

        /// Moves this area onto `screenRectangle` and lays its children out there.
        public void relocate(ScreenRectangle screenRectangle) {
            setX(screenRectangle.left());
            setY(screenRectangle.top());
            setWidth(screenRectangle.width());
            setHeight(screenRectangle.height());
            onPanelResized(screenRectangle);
        }

        protected void addWidget(AbstractWidget widget) {
            children.add(widget);
        }

        protected void clearWidgets() {
            children.clear();
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
        }

        @Override
        public @NonNull List<? extends GuiEventListener> children() {
            return Collections.unmodifiableList(children);
        }

        @Override
        public void setFocused(final @Nullable GuiEventListener focused) {
            // Re-focusing the already-focused child clears the focus in vanilla's implementation.
            if (getFocused() == focused) {
                return;
            }

            super.setFocused(focused);
        }

        /// Returns whether the area should completely be ignored when called by [#getChildAt]
        protected boolean ambientArea() {
            return false;
        }

        @Override
        public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
            Optional<GuiEventListener> child = getChildAt(event.x(), event.y());

            if (child.isEmpty()) {
                return false;
            } else {
                GuiEventListener widget = child.get();

                if (widget.mouseClicked(event, doubleClick) && widget.shouldTakeFocusAfterInteraction()) {
                    setFocused(widget);

                    if (AkytheraEditorLayoutManager.acceptsDragButton(event)) {
                        setDragging(true);
                    }
                }

                return true;
            }
        }

        /// Forwards drags to the focused child for every button. The inherited
        /// [AbstractContainerWidget#mouseDragged] forwards only left-button drags and handles its own
        /// scrollbar grab there, so it runs first and the fallback below picks up the right-button
        /// drags it leaves behind.
        @Override
        public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
            if (super.mouseDragged(event, dx, dy)) {
                return true;
            }

            GuiEventListener focused = getFocused();
            return focused != null && isDragging() && AkytheraEditorLayoutManager.acceptsDragButton(event) && focused.mouseDragged(event, dx, dy);
        }

        /// The [#mouseDragged] treatment for the button-up: the inherited release fires only for the
        /// left button, so a right-button editor release is forwarded here and clears the drag.
        /// `super` handles and clears a left-button release itself, leaving `isDragging()` false by
        /// the time the fallback runs, so no release is ever dispatched twice.
        @Override
        public boolean mouseReleased(@NonNull MouseButtonEvent event) {
            if (super.mouseReleased(event)) {
                return true;
            }

            GuiEventListener focused = getFocused();
            if (focused != null && isDragging() && AkytheraEditorLayoutManager.acceptsDragButton(event)) {
                setDragging(false);
                return focused.mouseReleased(event);
            }

            return false;
        }

        @Override
        public boolean mouseScrolled(final double mx, final double my, final double scrollX, final double scrollY) {
            // Added checking for whether mouse pointer is in widget square
            if (!isActive() || !visible || ambientArea() || !isMouseOver(mx, my)) {
                return false;
            }

            // Override super method: panel is treated as screen and never has scroll. Call child objects' mouseScrolled instead.
            for (GuiEventListener guiEventListener : children) {
                if (guiEventListener.mouseScrolled(mx, my, scrollX, scrollY)) {
                    return true;
                }
            }

            return false;
        }
    }

    public static final class PanelDivider extends AbstractWidget implements AnchoredWidget {
        // Expanded mouse click area
        private static final int HIT_PAD = 2;

        private final int subdivisionOrder;
        private final PanelSection belongedSection;
        private final PanelDivider.Orientation orientation;
        private final SectionResizer onPositionChanged;
        private boolean dragging;

        public PanelDivider(
            int subdivisionOrder,
            PanelSection belongedSection,
            PanelDivider.Orientation orientation,
            SectionResizer onPositionChanged,
            Unit x1,
            Unit x2,
            Unit y1,
            Unit y2,
            AnchoredWidget.HorizontalAnchorType horizontalAnchorType,
            AnchoredWidget.VerticalAnchorType verticalAnchorType
        ) {
            super(0, 0, 0, 0, Component.empty());

            this.subdivisionOrder = subdivisionOrder;
            this.belongedSection = belongedSection;
            this.orientation = orientation;
            this.onPositionChanged = onPositionChanged;
            this.x1 = x1;
            this.x2 = x2;
            this.y1 = y1;
            this.y2 = y2;
            this.horizontalAnchorType = horizontalAnchorType;
            this.verticalAnchorType = verticalAnchorType;
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            if (!active) return false;

            return orientation == PanelDivider.Orientation.VERTICAL ?
                mouseX >= getX() - HIT_PAD && mouseX < getX() + getWidth() + HIT_PAD && mouseY >= getY() && mouseY < getY() + getHeight() :
                mouseX >= getX() && mouseX < getX() + getWidth() && mouseY >= getY() - HIT_PAD && mouseY < getY() + getHeight() + HIT_PAD;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            int color = (isHovered() || dragging) ? 0xFF5A8AAA : 0xFF333333;
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), color);

            // Grip dots along the center of the strip
            int dotColor = (isHovered() || dragging) ? 0xFFAABBCC : 0xFF606060;
            int cx = getX() + getWidth() / 2;
            int cy = getY() + getHeight() / 2;

            if (orientation == PanelDivider.Orientation.VERTICAL) {
                for (int i = -2; i <= 2; i++) {
                    graphics.fill(cx - 1, cy + i * 5 - 1, cx + 1, cy + i * 5 + 1, dotColor);
                }
            } else {
                for (int i = -2; i <= 2; i++) {
                    graphics.fill(cx + i * 5 - 1, cy - 1, cx + i * 5 + 1, cy + 1, dotColor);
                }
            }
        }

        @Override
        public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {
            dragging = true;
        }

        @Override
        public void onRelease(@NonNull MouseButtonEvent event) {
            dragging = false;
        }

        @Override
        public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
            if (dragging && event.button() == 0) {
                switch (orientation) {
                    case HORIZONTAL -> {
                        int min = (subdivisionOrder == 0 ? // check top section
                            belongedSection.panelArea.getY() :
                            belongedSection.subsections.get(subdivisionOrder).panelArea.getY())
                                + DIVIDER_LENGTH;
                        int max = subdivisionOrder == belongedSection.dividers.size() - 1 ? // check bottom section
                            belongedSection.panelArea.getBottom() - DIVIDER_LENGTH :
                            belongedSection.subsections.get(subdivisionOrder + 1).panelArea.getBottom();

                        // Clamps the y position to prevent exceeding divider both on up and down sides
                        setY(Math.clamp((int)event.y(), min, max));
                        setY1(AnchoredWidget.fraction((int)((getY() - belongedSection.panelArea.getY()) / (float)belongedSection.panelArea.getHeight() * 100.0F)));
                    }
                    case VERTICAL -> {
                        int min = (subdivisionOrder == 0 ? // check leftmost section
                            belongedSection.panelArea.getX():
                            belongedSection.subsections.get(subdivisionOrder).panelArea.getX())
                                + DIVIDER_LENGTH;
                        int max = subdivisionOrder == belongedSection.dividers.size() - 1 ? // check rightmost section
                            belongedSection.panelArea.getRight() - DIVIDER_LENGTH :
                            belongedSection.subsections.get(subdivisionOrder + 1).panelArea.getRight();

                        // Clamps the x position to prevent exceeding divider both on left and right sides
                        setX(Math.clamp((int)event.x(), min, max));
                        setX1(AnchoredWidget.fraction((int)((getX() - belongedSection.panelArea.getX()) / (float)belongedSection.panelArea.getWidth() * 100.0F)));
                    }
                }

                resizeSections();

                return true;
            }

            return false;
        }

        public CursorType getHoveringCursor() {
            return orientation == PanelDivider.Orientation.VERTICAL ? CursorTypes.RESIZE_EW : CursorTypes.RESIZE_NS;
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput narration) {}

        private void resizeSections() {
            switch (orientation) {
                case HORIZONTAL ->
                    onPositionChanged.resizeSections(
                        subdivisionOrder,
                        belongedSection.subsections.get(subdivisionOrder),
                        belongedSection.subsections.get(subdivisionOrder + 1),
                        getY()
                    );
                case VERTICAL ->
                    onPositionChanged.resizeSections(
                        subdivisionOrder,
                        belongedSection.subsections.get(subdivisionOrder),
                        belongedSection.subsections.get(subdivisionOrder + 1),
                        getX()
                    );
            }
        }

        public PanelSection getFirstSection() {
            return belongedSection.subsections.get(subdivisionOrder);
        }

        public PanelSection getSecondSection() {
            return belongedSection.subsections.get(subdivisionOrder + 1);
        }

        public int getRatio() {
            return switch(orientation) {
                case VERTICAL -> ((Fraction)getX1()).value();
                case HORIZONTAL -> ((Fraction)getY1()).value();
            };
        }

        /// Sizing orientation
        public enum Orientation {
            VERTICAL, HORIZONTAL
        }

        public interface SectionResizer {
            void resizeSections(int order, PanelSection section1, PanelSection section2, int newPosition);
        }

        // ***************************************************************
        // [AnchoredWidget] implementations
        // ***************************************************************
        private Unit x1;
        private Unit x2;
        private Unit y1;
        private Unit y2;
        private AnchoredWidget.HorizontalAnchorType horizontalAnchorType;
        private AnchoredWidget.VerticalAnchorType verticalAnchorType;

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

    // ***************************************************************
    // [AnchoredWidget] implementations
    // ***************************************************************
    private Unit x1;
    private Unit x2;
    private Unit y1;
    private Unit y2;
    private AnchoredWidget.HorizontalAnchorType horizontalAnchorType;
    private AnchoredWidget.VerticalAnchorType verticalAnchorType;

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
