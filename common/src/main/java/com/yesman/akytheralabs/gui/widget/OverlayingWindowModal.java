package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.Modal;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/// A modal for overlapped pop-up screen that shows the parent screen as background with a dimmer
///
/// Initially appeared in the center of the screen, but users can relocate the location by dragging
/// header section.
public abstract class OverlayingWindowModal extends AbstractContainerWidget implements AnchoredWidget, Modal {
    private boolean moveByDragging;
    private double xAccumul;
    private double yAccumul;

    public OverlayingWindowModal(Unit x2, Unit y2, Component message) {
        // Real position & size are always cover the whole screen
        super(
            0, 0,
            AkytheraEditorLayoutManager.getInstance().isActive()
                ? AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen().width
                : Minecraft.getInstance().gui.screen().width,
            AkytheraEditorLayoutManager.getInstance().isActive()
                ? AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen().height
                : Minecraft.getInstance().gui.screen().height,
            message,
            AbstractScrollArea.defaultSettings(0)
        );

        // Locate to the center
        this.x1 = AnchoredWidget.pixel(0);
        this.y1 = AnchoredWidget.pixel(0);
        this.x2 = x2;
        this.y2 = y2;
    }

    /// Returns if the mouse hovering header
    public abstract boolean isMouseOverHeader(double mouseX, double mouseY);

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        // The modal should interrupt any input actions going to the parent screen
        return isActive();
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }

        if (isMouseOverHeader(event.x(), event.y())) {
            moveByDragging = true;
            return true;
        }

        if ((event.x() < getX() || event.y() < getY() || event.x() > getRight() || event.y() > getBottom()) && closeOnPressBackground()) {
            // close the modal when mouse clicked outside the modal
            ModalManager.getInstance().closeModal();
        }

        return false;
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (moveByDragging) {
            moveByDragging = false;
            xAccumul = 0;
            yAccumul = 0;

            // Reset
            backtrack(getRectangle());

            return true;
        }

        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (moveByDragging) {
            xAccumul += dx;
            yAccumul += dy;
            int xMove = (int)xAccumul;
            int yMove = (int)yAccumul;

            if (xMove != 0 || yMove != 0) {
                setPosition(getX() + xMove, getY() + yMove);

                // Move children
                for (GuiEventListener e : children()) {
                    dragChildrenRecursively(e, xMove, yMove);
                }

                xAccumul -= xMove;
                yAccumul -= yMove;
            }
        }

        return super.mouseDragged(event, dx, dy);
    }

    private static void dragChildrenRecursively(GuiEventListener e, int xMove, int yMove) {
        if (e instanceof AbstractWidget aw) {
            if (aw instanceof AbstractSelectionList<?> selectionList) {
                selectionList.updateSizeAndPosition(aw.getWidth(), aw.getHeight(), aw.getX() + xMove, aw.getY() + yMove);
            } else {
                aw.setPosition(aw.getX() + xMove, aw.getY() + yMove);

                if (aw instanceof AbstractContainerWidget abstractContainerWidget) {
                    for (GuiEventListener child : abstractContainerWidget.children()) {
                        dragChildrenRecursively(child, xMove, yMove);
                    }
                }
            }
        }
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // Fill background
        graphics.fill(
            0, 0,
            AkytheraEditorLayoutManager.getInstance().isActive()
                ? AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen().width
                : Minecraft.getInstance().gui.screen().width,
            AkytheraEditorLayoutManager.getInstance().isActive()
                ? AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen().height
                : Minecraft.getInstance().gui.screen().height,
            0x88000000
        );
    }

    /// Close the modal on pressing outside the widget rectangle
    protected boolean closeOnPressBackground() {
        return false;
    }

    // ***************************************************************
    // [AnchoredWidget] implementations
    // ***************************************************************
    private Unit x1;
    private Unit x2;
    private Unit y1;
    private Unit y2;
    private HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.CENTER_WIDTH;
    private VerticalAnchorType verticalAnchorType = VerticalAnchorType.CENTER_HEIGHT;

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

    /// Custom implementation: set up the window location, size fields instead of [AbstractWidget] fields
    @Override
    public void relocate(ScreenRectangle screenRectangle) {
        AnchoredWidget.super.relocate(screenRectangle);
        ScreenRectangle windowRectangle = getRectangle();

        for (GuiEventListener e : children()) {
            if (e instanceof AnchoredWidget aw) aw.relocate(windowRectangle);
        }
    }
}
