package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.client.api.gui.CloseableWidget;
import com.yesman.akythera.client.api.gui.Grid;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.FrameResizer;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.gui.modal.ResizableFrame;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.EditorEditBox;
import com.yesman.akytheralabs.gui.widget.OverlayingWindowModal;
import com.yesman.akytheralabs.gui.widget.PreviewTextureCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/// Picks a texture, a GUI sprite or a particle sprite, see [Mode]. Lists the project's own textures first, then every one the loaded
/// resource packs carry — vanilla's and the installed mods'. Each shows as a thumbnail in a [Grid], filtered by
/// the search box over its id. Its edges drag to resize.
public final class TextureSelectModal extends OverlayingWindowModal implements CloseableWidget, ResizableFrame {
    private static final int WIDTH = 360;
    private static final int HEIGHT = 260;
    private static final int MIN_WIDTH = 160;
    private static final int MIN_HEIGHT = 120;
    private static final int TITLE_H = 20;
    private static final int SEARCH_H = 18;
    private static final int FOOTER_H = 14;
    private static final int CELL_SIZE = 56;
    private static final int CELL_PADDING = 4;
    private static final int LABEL_H = 10;

    private static final String TEXTURE_DIRECTORY = "textures";
    private static final String TEXTURE_EXTENSION = ".png";

    private final Font font;
    private final Mode mode;
    private final Consumer<Identifier> onSelected;
    private final EditorEditBox<String> searchBox;
    private final Grid grid;
    private final List<TextureCell> allCells;
    private final PreviewTextureCache thumbnails = new PreviewTextureCache();
    private final FrameResizer frameResizer;

    /// @param onSelected told the picked id, as [Mode] answers it
    public TextureSelectModal(Font font, Mode mode, Consumer<Identifier> onSelected) {
        super(
            AnchoredWidget.pixel(WIDTH), AnchoredWidget.pixel(HEIGHT),
            Component.translatable(mode.title)
        );

        this.font = font;
        this.mode = mode;
        this.onSelected = onSelected;
        this.frameResizer = new FrameResizer(this, MIN_WIDTH, MIN_HEIGHT);

        this.searchBox = EditorEditBox.stringBox(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TITLE_H), AnchoredWidget.pixel(SEARCH_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            this::filter,
            Component.translatable(LangKeys.GUI_MESSAGE_EMPTY_SEARCHBAR)
        );

        this.grid = new Grid(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TITLE_H + SEARCH_H), AnchoredWidget.pixel(FOOTER_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            CELL_SIZE,
            CELL_SIZE,
            CELL_PADDING,
            null
        );

        // A project texture the game also loads (an exported pack of this project) keeps its project entry
        Map<Identifier, TextureCell> cells = new LinkedHashMap<>();
        AkytheraProject project = AkytheraProject.getCurrentProject();
        Path texturesRoot = project.getProjectRoot().resolve("assets").resolve(AkytheraProject.TEXTURES_DIRECTORY);

        FileSystemHelper.forEachFile(texturesRoot, file -> file.toString().endsWith(TEXTURE_EXTENSION), file -> {
            Identifier textureId = project.getTextureIdentifier(file);
            if (textureId != null && mode.lists(textureId)) cells.put(textureId, new TextureCell(textureId, file));
        });

        Minecraft.getInstance().getResourceManager()
            .listResources(TEXTURE_DIRECTORY, id -> id.getPath().endsWith(TEXTURE_EXTENSION) && mode.lists(id))
            .keySet()
            .stream()
            .sorted(Comparator.comparing(Identifier::toString))
            .forEach(textureId -> cells.putIfAbsent(textureId, new TextureCell(textureId, null)));

        this.allCells = List.copyOf(cells.values());
        filter("");
    }

    /// Shows the cells whose id contains `query`, ignoring case.
    private void filter(String query) {
        String lower = query.toLowerCase();

        grid.clearComponents();

        for (TextureCell cell : allCells) {
            if (lower.isEmpty() || cell.textureId.toString().contains(lower)) grid.addComponent(cell.textureId.toString(), cell);
        }

        grid.setScrollAmount(0);
        grid.relocate(getRectangle());
    }

    @Override
    public EditorEditBox<String> searchBox() {
        return searchBox;
    }

    @Override
    public boolean confirmSoleResult() {
        if (grid.children().size() != 1) return false;

        ((TextureCell) grid.children().getFirst()).pick();

        return true;
    }

    @Override
    public void destroy() {
        thumbnails.releaseAll();
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return List.of(searchBox, grid);
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    public FrameResizer frameResizer() {
        return frameResizer;
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
    public boolean isMouseOverHeader(double mouseX, double mouseY) {
        return mouseX >= (double)getX()
            && mouseY >= (double)getY()
            && mouseX < (double)getRight()
            && mouseY < (double)getY() + TITLE_H;
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (frameResizer.mousePressed(event.x(), event.y())) {
            return true;
        }

        // Route to children, then consume to prevent click-through to the screen below
        super.mouseClicked(event, doubleClick);

        return true;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (frameResizer.mouseDragged(event.x(), event.y())) {
            return true;
        }

        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (frameResizer.mouseReleased()) {
            return true;
        }

        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return grid.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (event.isEscape()) {
            ModalManager.getInstance().closeModal();
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

        graphics.text(
            font,
            getMessage(),
            getX() + (getWidth() - font.width(getMessage())) / 2,
            getY() + (TITLE_H - font.lineHeight) / 2,
            0xFFCCCCCC,
            false
        );

        searchBox.extractRenderState(graphics, mouseX, mouseY, a);
        grid.extractRenderState(graphics, mouseX, mouseY, a);

        // Footer: the full id of the hovered cell
        TextureCell hovered = null;

        if (grid.isMouseOver(mouseX, mouseY)) {
            for (AbstractWidget cell : grid.children()) {
                if (cell.isMouseOver(mouseX, mouseY)) hovered = (TextureCell) cell;
            }
        }

        if (hovered != null) {
            String id = font.plainSubstrByWidth(hovered.textureId.toString(), getWidth() - 8);
            graphics.text(font, id, getX() + 4, getBottom() - FOOTER_H + (FOOTER_H - font.lineHeight) / 2, 0xFF888888, false);
        }

        frameResizer.requestCursor(graphics, mouseX, mouseY);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
    }

    @Override
    protected boolean closeOnPressBackground() {
        return true;
    }

    /// What the modal lists and answers.
    public enum Mode {
        /// Every texture, answered as its path: `<namespace>:textures/<path>.png`.
        TEXTURE(null, LangKeys.GUI_TEXTURE_SELECT_MODAL_TITLE),
        /// The GUI sprites under `textures/gui/sprites/`, answered as the sprite id: `<namespace>:<path>`.
        SPRITE("textures/gui/sprites/", LangKeys.GUI_TEXTURE_SELECT_MODAL_TITLE_SPRITE),
        /// The particle sprites under `textures/particle/`, answered as the sprite id: `<namespace>:<path>`.
        PARTICLE("textures/particle/", LangKeys.GUI_TEXTURE_SELECT_MODAL_TITLE_PARTICLE);

        /// The directory listed, whose sprites are answered by their path inside it, or null for every texture.
        private final @Nullable String directory;

        private final String title;

        Mode(final @Nullable String directory, final String title) {
            this.directory = directory;
            this.title = title;
        }

        private boolean lists(final Identifier texture) {
            return directory == null || texture.getPath().startsWith(directory);
        }

        private Identifier answer(final Identifier texture) {
            String path = texture.getPath();

            return directory == null ? texture : texture.withPath(path.substring(directory.length(), path.length() - TEXTURE_EXTENSION.length()));
        }
    }

    /// One texture: its thumbnail fitted above its file name.
    private final class TextureCell extends AbstractWidget implements PatchComponent {
        private final Identifier textureId;

        /// The project file the texture reads from, or null for one the resource packs carry.
        private final @Nullable Path file;

        private final String label;

        TextureCell(Identifier textureId, @Nullable Path file) {
            super(0, 0, CELL_SIZE, CELL_SIZE, Component.literal(textureId.toString()));

            String path = textureId.getPath();

            this.textureId = textureId;
            this.file = file;
            this.label = path.substring(path.lastIndexOf('/') + 1, path.length() - TEXTURE_EXTENSION.length());
        }

        @Override
        protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();

            graphics.fill(x, y, x + w, y + h, isHovered() ? 0xFF1E4D72 : 0xFF141414);
            graphics.outline(x, y, w, h, file != null ? 0xFF5A8AAA : 0xFF3A3A3A);

            PreviewTextureCache.Entry thumbnail = file != null ? thumbnails.get(file) : thumbnails.get(textureId);

            if (thumbnail != null) {
                int area = Math.min(w, h - LABEL_H) - 6;
                float scale = Math.min((float)area / thumbnail.width(), (float)area / thumbnail.height());
                int drawW = Math.max(1, Math.round(thumbnail.width() * scale));
                int drawH = Math.max(1, Math.round(thumbnail.height() * scale));
                int left = x + (w - drawW) / 2;
                int top = y + 3 + (area - drawH) / 2;

                graphics.blit(thumbnail.textureId(), left, top, left + drawW, top + drawH, 0.0F, 1.0F, 0.0F, 1.0F);
            }

            String shown = font.plainSubstrByWidth(label, w - 4);
            graphics.text(font, shown, x + (w - font.width(shown)) / 2, y + h - LABEL_H, 0xFFAAAAAA, false);
        }

        @Override
        public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {
            pick();
        }

        /// Answers the texture as [#mode] answers it.
        private void pick() {
            onSelected.accept(mode.answer(textureId));
        }

        @Override
        public void playDownSound(@NonNull SoundManager soundManager) {
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
        }
    }
}
