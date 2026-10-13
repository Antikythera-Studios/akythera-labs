package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/// Shows one image over a checkered backdrop that marks its transparent pixels. Scrolling zooms
/// around the cursor, dragging pans, and [#frame] fits the image back into view.
///
/// Overlay widgets ([#addOverlay]) draw over the image and take presses before the pan does. They
/// place themselves off [#imageRectangle].
public final class TexturePreviewer extends AbstractContainerWidget implements AnchoredWidget {
    private static final Component LOADING = Component.translatable(LangKeys.GUI_TEXTURE_PREVIEW_SCREEN_MESSAGE_LOADING);
    private static final int BACKGROUND_COLOR = 0xFF101010;
    private static final int CHECKER_LIGHT = 0xFF3C3C3C;
    private static final int CHECKER_DARK = 0xFF2C2C2C;
    private static final int TEXT_COLOR = 0xFFAAAAAA;

    /// Side of one backdrop square, in widget pixels.
    private static final int CHECKER_SIZE = 8;

    /// Space kept around a framed image, in widget pixels.
    private static final int FRAME_MARGIN = 12;

    private static final float ZOOM_STEP = 1.2F;
    private static final float MIN_ZOOM = 0.05F;
    private static final float MAX_ZOOM = 128.0F;

    private final Font font;

    /// The image to show, or null while it is still loading.
    private final Supplier<PreviewTextureCache.@Nullable Entry> image;

    /// Widget pixels per image pixel. Zero until the image is framed on its first draw.
    private float zoom;

    /// Offset of the image's center from the widget's center, in widget pixels.
    private float panX;
    private float panY;

    /// Whether a press on the image, not on an overlay, is dragging it.
    private boolean panning;

    /// Where the image was last drawn, or null while it is still loading.
    private @Nullable ScreenRectangle imageRectangle;

    private final List<AbstractWidget> overlays = new ArrayList<>();

    public TexturePreviewer(
        final Font font,
        final Supplier<PreviewTextureCache.@Nullable Entry> image,
        final Unit x1,
        final Unit x2,
        final Unit y1,
        final Unit y2,
        final AnchoredWidget.HorizontalAnchorType horizontalAnchorType,
        final AnchoredWidget.VerticalAnchorType verticalAnchorType
    ) {
        super(0, 0, 0, 0, Component.literal("texture_previewer"), AbstractScrollArea.defaultSettings(0));

        this.font = font;
        this.image = image;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchorType;
        this.verticalAnchorType = verticalAnchorType;
    }

    /// Draws `overlay` over the image, in the order added.
    public void addOverlay(AbstractWidget overlay) {
        overlays.add(overlay);
    }

    /// Where the image was last drawn, in screen pixels, or null while it is still loading.
    public @Nullable ScreenRectangle imageRectangle() {
        return imageRectangle;
    }

    /// Fits the whole image into view on the next draw, at a whole zoom factor when it is enlarged.
    public void frame() {
        zoom = 0.0F;
        panX = 0.0F;
        panY = 0.0F;
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        PreviewTextureCache.Entry entry = image.get();

        graphics.fill(x, y, x + w, y + h, BACKGROUND_COLOR);

        if (entry == null) {
            imageRectangle = null;
            graphics.text(font, LOADING, x + (w - font.width(LOADING)) / 2, y + (h - font.lineHeight) / 2, TEXT_COLOR, false);
            return;
        }

        if (zoom <= 0.0F) {
            float fit = Math.min((float)(w - FRAME_MARGIN * 2) / entry.width(), (float)(h - FRAME_MARGIN * 2) / entry.height());
            zoom = Mth.clamp(fit >= 1.0F ? Mth.floor(fit) : fit, MIN_ZOOM, MAX_ZOOM);
        }

        int drawW = Math.max(1, Math.round(entry.width() * zoom));
        int drawH = Math.max(1, Math.round(entry.height() * zoom));
        int left = Math.round(x + w / 2.0F + panX - drawW / 2.0F);
        int top = Math.round(y + h / 2.0F + panY - drawH / 2.0F);

        imageRectangle = new ScreenRectangle(left, top, drawW, drawH);

        // The visible part of the image
        int visibleLeft = Math.max(left, x);
        int visibleTop = Math.max(top, y);
        int visibleRight = Math.min(left + drawW, x + w);
        int visibleBottom = Math.min(top + drawH, y + h);

        graphics.enableScissor(x, y, x + w, y + h);

        // Backdrop squares stay pinned to the image's corner while it pans
        for (int cy = visibleTop - Math.floorMod(visibleTop - top, CHECKER_SIZE); cy < visibleBottom; cy += CHECKER_SIZE) {
            for (int cx = visibleLeft - Math.floorMod(visibleLeft - left, CHECKER_SIZE); cx < visibleRight; cx += CHECKER_SIZE) {
                boolean light = ((cx - left) / CHECKER_SIZE + (cy - top) / CHECKER_SIZE) % 2 == 0;

                graphics.fill(
                    Math.max(cx, visibleLeft), Math.max(cy, visibleTop),
                    Math.min(cx + CHECKER_SIZE, visibleRight), Math.min(cy + CHECKER_SIZE, visibleBottom),
                    light ? CHECKER_LIGHT : CHECKER_DARK
                );
            }
        }

        graphics.blit(entry.textureId(), left, top, left + drawW, top + drawH, 0.0F, 1.0F, 0.0F, 1.0F);

        for (AbstractWidget overlay : overlays) {
            overlay.extractRenderState(graphics, mouseX, mouseY, a);
        }

        graphics.disableScissor();

        String size = entry.width() + " x " + entry.height() + "  (" + Math.round(zoom * 100.0F) + "%)";
        graphics.text(font, size, x + 4, y + h - font.lineHeight - 4, TEXT_COLOR, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY) || zoom <= 0.0F) return false;

        float newZoom = Mth.clamp(zoom * (float)Math.pow(ZOOM_STEP, scrollY), MIN_ZOOM, MAX_ZOOM);
        float ratio = newZoom / zoom;
        float cursorX = (float)mouseX - (getX() + getWidth() / 2.0F);
        float cursorY = (float)mouseY - (getY() + getHeight() / 2.0F);

        // Keep the image pixel under the cursor in place
        panX = cursorX - (cursorX - panX) * ratio;
        panY = cursorY - (cursorY - panY) * ratio;
        zoom = newZoom;

        return true;
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;

        panning = event.button() == 0 && isMouseOver(event.x(), event.y());

        return panning;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (panning) {
            panX += (float)dx;
            panY += (float)dy;

            return true;
        }

        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (panning) {
            panning = false;
            return true;
        }

        return super.mouseReleased(event);
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return overlays;
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
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
