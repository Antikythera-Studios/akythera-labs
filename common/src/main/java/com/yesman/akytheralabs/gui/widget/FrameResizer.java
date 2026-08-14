package com.yesman.akytheralabs.gui.widget;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/// Edge-drag resizing for a modal frame: the grip hit-testing, the drag arithmetic, the cursor
/// affordance, and the hand-off of the size the user settled on. **Held** by the widget it resizes
/// rather than inherited (see [ResizableFrame]), so each dropdown keeps its own class shape and only
/// routes its mouse / render calls in.
///
/// The frame must be an [AnchoredWidget]: a new rect is applied through the anchor units followed by
/// [AnchoredWidget#relocate], which is the only sizing channel that survives — relocate re-derives the
/// rect from those units, so writing the widget bounds directly is undone by the next one.
public final class FrameResizer {
    /// Width of the band along each frame edge that grabs it. The band sits **outside** the frame, so
    /// it can never swallow a click meant for the modal's content (an entry list, its scrollbar); the
    /// cost is that a press within this much of the frame resizes instead of dismissing the modal.
    private static final int GRIP = 4;

    /// Sink for a size the frame was dragged to — reported once per drag, when the grip is released.
    public interface SizeSink {
        void accept(int width, int height);
    }

    /// The frame edge a drag is moving, one per axis — both are set at once for a corner grab.
    private enum Grip { NONE, LEFT, RIGHT, TOP, BOTTOM }

    private final AbstractWidget frame;
    private final AnchoredWidget anchored;
    private final int minWidth;
    private final int minHeight;

    private Grip horizontalGrip = Grip.NONE;
    private Grip verticalGrip = Grip.NONE;

    /// Distance from the grab point to the edge it grabbed, so the edge tracks the cursor exactly
    /// instead of snapping to it on the first drag event.
    private int gripOffsetX;
    private int gripOffsetY;

    private @Nullable SizeSink sizeSink;

    public <F extends AbstractWidget & AnchoredWidget> FrameResizer(F frame, int minWidth, int minHeight) {
        this.frame = frame;
        this.anchored = frame;
        this.minWidth = minWidth;
        this.minHeight = minHeight;
    }

    /// Where to report a size the user drags the frame to. Pointed at the anchor's
    /// [DropdownSizeMemory] by [AkytheraEditorLayoutManager#openModalWidgetUnder], so the next open
    /// restores it; null (the default) simply forgets the size when the modal closes.
    public void setSizeSink(@Nullable SizeSink sizeSink) {
        this.sizeSink = sizeSink;
    }

    /// Grabs the frame edge(s) under the press, if any — both axes when the press is in a corner — and
    /// remembers how far each grab sits from its edge. False when the press is not on the frame, so the
    /// caller routes it as ordinary content input (or a dismissal).
    public boolean mousePressed(double mouseX, double mouseY) {
        horizontalGrip = Grip.NONE;
        verticalGrip = Grip.NONE;

        // Only the frame's own span (plus the grip band at its ends) grabs, so a press far off one side
        // can't seize the other axis' edge.
        boolean alongRows = mouseY >= frame.getY() - GRIP && mouseY <= frame.getBottom() + GRIP;
        boolean alongColumns = mouseX >= frame.getX() - GRIP && mouseX <= frame.getRight() + GRIP;

        if (alongRows && inBandBefore(mouseX, frame.getX())) {
            horizontalGrip = Grip.LEFT;
            gripOffsetX = frame.getX() - (int) mouseX;
        } else if (alongRows && inBandAfter(mouseX, frame.getRight())) {
            horizontalGrip = Grip.RIGHT;
            gripOffsetX = frame.getRight() - (int) mouseX;
        }

        if (alongColumns && inBandBefore(mouseY, frame.getY())) {
            verticalGrip = Grip.TOP;
            gripOffsetY = frame.getY() - (int) mouseY;
        } else if (alongColumns && inBandAfter(mouseY, frame.getBottom())) {
            verticalGrip = Grip.BOTTOM;
            gripOffsetY = frame.getBottom() - (int) mouseY;
        }

        return resizing();
    }

    /// Moves the grabbed edge(s) to the cursor, the opposite ones staying put. False when no grip is
    /// held, so the caller can fall through to its normal drag handling.
    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!resizing()) return false;

        int left = frame.getX();
        int top = frame.getY();
        int right = frame.getRight();
        int bottom = frame.getBottom();

        switch (horizontalGrip) {
            case LEFT -> left = Math.min((int) mouseX + gripOffsetX, right - minWidth);
            case RIGHT -> right = Math.max((int) mouseX + gripOffsetX, left + minWidth);
            default -> { }
        }

        switch (verticalGrip) {
            case TOP -> top = Math.min((int) mouseY + gripOffsetY, bottom - minHeight);
            case BOTTOM -> bottom = Math.max((int) mouseY + gripOffsetY, top + minHeight);
            default -> { }
        }

        resizeTo(left, top, right - left, bottom - top);

        return true;
    }

    /// Releases the grip and reports the settled size to [#setSizeSink]. False when no grip was held.
    public boolean mouseReleased() {
        if (!resizing()) return false;

        horizontalGrip = Grip.NONE;
        verticalGrip = Grip.NONE;

        if (sizeSink != null) sizeSink.accept(frame.getWidth(), frame.getHeight());

        return true;
    }

    /// Cursor affordance for the grip: the arrow follows which axes the pointer (or the drag in flight)
    /// has hold of — a corner gets the omnidirectional one. Call from the frame's render.
    public void requestCursor(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        boolean horizontal = horizontalGrip != Grip.NONE;
        boolean vertical = verticalGrip != Grip.NONE;

        // Not dragging: probe the frame under the pointer without disturbing the live grips.
        if (!horizontal && !vertical) {
            boolean alongRows = mouseY >= frame.getY() - GRIP && mouseY <= frame.getBottom() + GRIP;
            boolean alongColumns = mouseX >= frame.getX() - GRIP && mouseX <= frame.getRight() + GRIP;

            horizontal = alongRows
                && (inBandBefore(mouseX, frame.getX()) || inBandAfter(mouseX, frame.getRight()));
            vertical = alongColumns
                && (inBandBefore(mouseY, frame.getY()) || inBandAfter(mouseY, frame.getBottom()));
        }

        if (horizontal && vertical) {
            graphics.requestCursor(CursorTypes.RESIZE_ALL);
        } else if (horizontal) {
            graphics.requestCursor(CursorTypes.RESIZE_EW);
        } else if (vertical) {
            graphics.requestCursor(CursorTypes.RESIZE_NS);
        }
    }

    /// Whether the point is on the frame or on the [#GRIP] band around it — the frame's rect as
    /// [#mousePressed] sees it. A [FloatingModal] hit-tests with this rather than its own bounds,
    /// because the band lies outside them and would otherwise route to the screen behind.
    public boolean withinGripBounds(double mouseX, double mouseY) {
        return mouseX >= frame.getX() - GRIP && mouseX <= frame.getRight() + GRIP
            && mouseY >= frame.getY() - GRIP && mouseY <= frame.getBottom() + GRIP;
    }

    private boolean resizing() {
        return horizontalGrip != Grip.NONE || verticalGrip != Grip.NONE;
    }

    /// Whether `p` is in the [#GRIP] band immediately **before** a left / top edge.
    private static boolean inBandBefore(double p, int edge) {
        return p >= edge - GRIP && p < edge;
    }

    /// Whether `p` is in the [#GRIP] band immediately **after** a right / bottom edge (both are
    /// exclusive bounds, so the band starts on the edge value itself).
    private static boolean inBandAfter(double p, int edge) {
        return p >= edge && p <= edge + GRIP;
    }

    /// Applies a new rect through the anchor units and re-lays out the frame's children against it.
    private void resizeTo(int x, int y, int width, int height) {
        anchored.setX1(AnchoredWidget.pixel(x));
        anchored.setX2(AnchoredWidget.pixel(width));
        anchored.setY1(AnchoredWidget.pixel(y));
        anchored.setY2(AnchoredWidget.pixel(height));

        Screen editorScreen = AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen();
        if (editorScreen != null) anchored.relocate(editorScreen.getRectangle());
    }
}
