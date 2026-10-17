package com.yesman.akytheralabs.gui.widget.animation;

import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/// Footer-anchored scroll/zoom bar over the notify track's visible-frame window.
///
/// The track is split at the animation's *center frame* — left half maps to `[0, center]`,
/// right half maps to `[center, total]`. The bar's two edges are computed independently:
///
/// ```
///   leftFraction  = (visualStart / center)         * 0.5     when visualStart <= center
///                 = 0.5 + ((visualStart - center) / (total - center)) * 0.5   when > center
///   rightFraction = same piecewise mapping applied to visualEnd
/// ```
///
/// Both fractions clamp to `[0, 1]`. So when `visualEnd = 135` on a 0..180 clip
/// (center = 90), the right half is half-filled and half-empty — independent of `visualStart`.
///
/// Three drag regions, mirroring [AdjustablePanelManager.PanelDivider]:
///   - left edge grip  → only [AnimationTrackEditorPanel#setVisualFrameStart] moves
///   - right edge grip → only [AnimationTrackEditorPanel#setVisualFrameEnd] moves
///   - body            → both shift by the same Δ-frames (pan the window)
public final class FrameScrollBar extends AbstractWidget implements AnchoredWidget {
    private static final int MIN_W = 12;
    /// Pixel width of each edge grip strip. Mirrors [AnimationTrackEditorPanel#EDGE_DRAG_THRESHOLD].
    private static final int EDGE_GRIP_W = 4;

    private final TrackTimeline parent;

    private DragKind dragKind = DragKind.NONE;
    private double pressX;
    private float dragStartVisualStart;
    private float dragStartVisualEnd;

    public FrameScrollBar(TrackTimeline parent) {
        super(0, 0, 0, 0, Component.empty());

        this.parent = parent;

        // X: span the track-strip area — left offset = name block width + divider, right offset = 0.
        // Y: bottom-anchored inside the footer strip with a small inset so it doesn't touch the edges.
        this.x1 = AnchoredWidget.pixel(TimelineList.NAME_BLOCK_W + AnimationTrackEditorPanel.DIVIDER_W);
        this.x2 = AnchoredWidget.pixel(2);
        this.y1 = AnchoredWidget.pixel(4);
        this.y2 = AnchoredWidget.pixel(AnimationTrackEditorPanel.FOOTER_H - 8);
        this.horizontalAnchorType = HorizontalAnchorType.LEFT_RIGHT;
        this.verticalAnchorType = VerticalAnchorType.BOTTOM_HEIGHT;
    }

    private float totalFrames() {
        return parent.timelineFrames();
    }

    private int trackWidth() {
        return Math.max(1, getWidth());
    }

    /// Maps a clip frame to its `[0, 1]` position on the track.
    ///
    /// Split at the center: `[0, center]` → `[0, 0.5]`, `[center, total]` → `[0.5, 1]`.
    /// Values outside the clip clamp to `0` / `1` so the bar can't grow when the window
    /// is dragged past either end of the animation.
    private float frameToTrackFraction(float frame) {
        float total = totalFrames();

        if (total <= 0F) return 0F;

        float center = total * 0.5F;

        if (frame <= center) {
            return Math.clamp((frame / center) * 0.5F, 0F, 0.5F);
        }

        return Math.clamp(0.5F + ((frame - center) / (total - center)) * 0.5F, 0.5F, 1F);
    }

    /// Inverse of [#frameToTrackFraction] — track-space x → clip frame.
    /// Used by the drag handler to translate mouse positions back into frames.
    private float trackXToFrame(double trackX) {
        float total = totalFrames();

        if (total <= 0F) return 0F;

        float center = total * 0.5F;
        float fraction = (float)((trackX - getX()) / trackWidth());

        if (fraction <= 0.5F) {
            return (fraction / 0.5F) * center;
        }

        return center + ((fraction - 0.5F) / 0.5F) * (total - center);
    }

    private int barLeft() {
        return getX() + (int)(trackWidth() * frameToTrackFraction(parent.visualFrameStart()));
    }

    private int barRight() {
        return getX() + (int)(trackWidth() * frameToTrackFraction(parent.visualFrameEnd()));
    }

    private int barWidth() {
        if (totalFrames() <= 0F) return trackWidth();

        return Math.max(MIN_W, barRight() - barLeft());
    }

    private int barX() {
        if (totalFrames() <= 0F) return getX();

        int left = barLeft();
        int w = barWidth();
        // If the window is dragged entirely past the clip, the bar would otherwise extend past
        // the right edge of the track. Shift it left so the MIN_W stays grabbable inside the track.
        int trackEnd = getX() + trackWidth();

        return Math.min(left, trackEnd - w);
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x = barX();
        int w = barWidth();
        int y = getY();
        int h = getHeight();

        // Track background
        g.fill(getX(), y, getX() + trackWidth(), y + h, 0xFF1A1A1A);

        // Bar body — brighten on hover or while dragging.
        int bodyColor = dragKind == DragKind.BODY ? 0xFF5A8AAA : 0xFF3A3A3A;
        g.fill(x, y, x + w, y + h, bodyColor);

        // Edge grips — PanelDivider-style hint that the edges are draggable.
        g.fill(x, y, x + EDGE_GRIP_W, y + h, dragKind == DragKind.LEFT_EDGE ? 0xFFAABBCC : 0xFF808080);
        g.fill(x + w - EDGE_GRIP_W, y, x + w, y + h, dragKind == DragKind.RIGHT_EDGE ? 0xFFAABBCC : 0xFF808080);
    }

    @Override
    public boolean isMouseOver(double mx, double my) {
        if (!visible || !active) return false;
        int x = barX();
        int w = barWidth();

        return mx >= x && mx < x + w && my >= getY() && my < getY() + getHeight();
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return false;
        if (!isMouseOver(event.x(), event.y())) return false;

        int x = barX();
        int w = barWidth();

        pressX = event.x();
        dragStartVisualStart = parent.visualFrameStart();
        dragStartVisualEnd = parent.visualFrameEnd();

        if (event.x() - x < EDGE_GRIP_W) {
            dragKind = DragKind.LEFT_EDGE;
        } else if ((x + w) - event.x() < EDGE_GRIP_W) {
            dragKind = DragKind.RIGHT_EDGE;
        } else {
            dragKind = DragKind.BODY;
        }

        return true;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (dragKind == DragKind.NONE) return false;

        float total = totalFrames();
        if (total <= 0F) return false;

        // Mouse-x → frame using the same piecewise mapping the bar renders with — so the edge
        // (or window translation) tracks the cursor 1:1 in track-space, even across the center
        // kink where left and right halves can have different frame-per-pixel rates.
        float deltaFrames = trackXToFrame(event.x()) - trackXToFrame(pressX);

        switch (dragKind) {
            case LEFT_EDGE -> parent.setVisualFrameStart(
                Math.min(dragStartVisualStart + deltaFrames, parent.visualFrameEnd() - 1F)
            );
            case RIGHT_EDGE -> parent.setVisualFrameEnd(
                Math.max(dragStartVisualEnd + deltaFrames, parent.visualFrameStart() + 1F)
            );
            case BODY -> {
                parent.setVisualFrameStart(dragStartVisualStart + deltaFrames);
                parent.setVisualFrameEnd(dragStartVisualEnd + deltaFrames);
            }
            case NONE -> {}
        }

        return true;
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (dragKind == DragKind.NONE) return false;

        dragKind = DragKind.NONE;

        return true;
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput o) {
    }

    private enum DragKind {
        NONE, LEFT_EDGE, RIGHT_EDGE, BODY
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