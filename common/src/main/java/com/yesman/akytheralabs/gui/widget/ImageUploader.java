package com.yesman.akytheralabs.gui.widget;

import com.mojang.blaze3d.platform.NativeImage;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akytheralabs.AkytheraLabs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class ImageUploader extends AbstractWidget implements AnchoredWidget, Modal {
    private static final Identifier PREVIEW_TEXTURE_ID = Identifier.fromNamespaceAndPath(AkytheraLabs.MODID, "icon_preview");

    /// Called when an image is loaded from file picker or drag-and-drop
    private final Consumer<Path> callback;

    @Nullable
    private Path selectedPath = null;
    private boolean hasPreview = false;

    public ImageUploader(
        final Unit x1,
        final Unit x2,
        final Unit y1,
        final Unit y2,
        final AnchoredWidget.HorizontalAnchorType horizontalAnchorType,
        final AnchoredWidget.VerticalAnchorType verticalAnchorType,
        final Consumer<Path> callback
    ) {
        super(0, 0, 0, 0, Component.literal("widget.image_uploader"));

        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchorType;
        this.verticalAnchorType = verticalAnchorType;
        this.callback = callback;
    }

    @Nullable
    public Path getSelectedPath() {
        return selectedPath;
    }

    public Consumer<Path> getCallback() {
        return callback;
    }

    /// Loads a PNG from the given path and registers it as a preview texture.
    /// Safe to call from any thread — texture registration is dispatched to the render thread.
    public void loadImageFromPath(Path path) {
        CompletableFuture.supplyAsync(() -> {
            try (InputStream in = Files.newInputStream(path)) {
                return NativeImage.read(in);
            } catch (IOException e) {
                return null;
            }
        }).thenAcceptAsync(img -> {
            if (img == null) return;

            DynamicTexture texture = new DynamicTexture(() -> "akythera_icon_preview", img);
            Minecraft.getInstance().getTextureManager().register(PREVIEW_TEXTURE_ID, texture);
            texture.upload();

            hasPreview = true;
            selectedPath = path;
            callback.accept(path);
        }, Minecraft.getInstance());
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();

        graphics.fill(x, y, x + w, y + h, 0xFF1A1A1A);
        graphics.outline(x, y, w, h, isHovered() ? 0xFF5A8DBE : 0xFF3A3A3A);

        if (hasPreview) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, PREVIEW_TEXTURE_ID, x + 1, y + 1, 0.0f, 0.0f, w - 2, h - 2, w - 2, h - 2);
        } else {
            Minecraft mc = Minecraft.getInstance();
            String hint = "[ ^ ]";
            int hintX = x + (w - mc.font.width(hint)) / 2;
            int hintY = y + (h - mc.font.lineHeight) / 2;
            graphics.text(mc.font, hint, hintX, hintY, 0xFF888888, false);
        }
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (!isMouseOver(event.x(), event.y())) return false;
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;

        openFilePicker();

        return true;
    }

    private void openFilePicker() {
        CompletableFuture.supplyAsync(() -> {
            String result = TinyFileDialogs.tinyfd_openFileDialog("Select PNG Image", "", null, "PNG Images (*.png)", false);
            if (result == null || !result.endsWith(".png")) return null;
            return Path.of(result);
        }).thenAcceptAsync(path -> {
            if (path != null) loadImageFromPath(path);
        }, Minecraft.getInstance());
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }

    /// ***************************************************************
    /// [AnchoredWidget] implementations                              *
    /// ***************************************************************
    private Unit x1;
    private Unit x2;
    private Unit y1;
    private Unit y2;
    private final AnchoredWidget.HorizontalAnchorType horizontalAnchorType;
    private final AnchoredWidget.VerticalAnchorType verticalAnchorType;

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