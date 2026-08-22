package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.AdvancedEditBox;
import com.yesman.akytheralabs.gui.widget.ContextMenuContainer;
import com.yesman.akytheralabs.gui.widget.DropdownSizeMemory;
import com.yesman.akytheralabs.gui.widget.FrameResizer;
import com.yesman.akytheralabs.gui.widget.Modal;
import com.yesman.akytheralabs.gui.widget.PopupBox;
import com.yesman.akytheralabs.gui.widget.ResizableFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/// Search-and-pick modal over registry entries, managed as [ResourceKey]s. Opens at the cursor (no dimmer, no
/// centering — behaves like [ContextMenuContainer]). Single-click on a list row confirms.
///
/// Two entry sources: a frozen [Registry] (the value-confirming constructor), or data pack entries
/// via [#dataPackEntries] — the current project's entries, optionally merged with everything the
/// loaded world's registry access carries.
///
/// The page is **resizable** by dragging its frame ([FrameResizer]); [#MODAL_W] / [#MODAL_H] are only its
/// opening size and its floor. The size the user drags to outlives the modal when it drops from a
/// [DropdownSizeMemory] anchor — a [PopupBox] remembers it, so the next press on the same cell opens at that
/// size.
public final class RegistryLookupModal<T> extends AbstractContainerWidget
    implements Modal, AnchoredWidget, ResizableFrame {

    private static final int TITLE_H = 18;
    private static final int SEARCH_H = 16;
    private static final int ROW_H = 14;
    private static final int SCROLLBAR_W = 5;
    private static final int MODAL_W = 200;
    private static final int MODAL_H = 200;

    private final Font font;
    private final Consumer<ResourceKey<T>> onConfirm;
    private final AdvancedEditBox<String> searchBox;
    private final EntryList list;

    private final FrameResizer resizer = new FrameResizer(this, MODAL_W, MODAL_H);

    /// Frozen-registry source: rows are the registry's keys; confirm reports the registered value.
    public RegistryLookupModal(
        Font font,
        Component title,
        Registry<T> registry,
        Consumer<T> onConfirm
    ) {
        this(
            font, title,
            registry.entrySet().stream().map(Map.Entry::getKey).toList(),
            key -> onConfirm.accept(registry.getValueOrThrow(key))
        );
    }

    /// Key-based source; confirm reports the picked [ResourceKey].
    public RegistryLookupModal(
        Font font,
        Component title,
        List<ResourceKey<T>> entries,
        Consumer<ResourceKey<T>> onConfirm
    ) {
        super(0, 0, MODAL_W, MODAL_H, title, AbstractScrollArea.defaultSettings(0));

        this.font = font;
        this.onConfirm = onConfirm;
        this.list = new EntryList(entries);
        this.list.rebuild("");

        this.searchBox = AdvancedEditBox.stringBox(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TITLE_H), AnchoredWidget.pixel(SEARCH_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            list::rebuild,
            Component.translatable(LangKeys.GUI_MESSAGE_EMPTY_SEARCHBAR)
        );
    }

    /// Data pack entries for `registryKey`, managed as [ResourceKey]s: the current project's
    /// entries, plus — unless `loadProjectEntriesOnly` — everything the loaded world's registry
    /// access carries for that registry (built-in and data pack entries alike).
    public static <T> RegistryLookupModal<T> dataPackEntries(
        Font font,
        Component title,
        ResourceKey<? extends Registry<T>> registryKey,
        boolean loadProjectEntriesOnly,
        Consumer<ResourceKey<T>> onConfirm
    ) {
        AkytheraProject project = AkytheraProject.getCurrentProject();

        List<ResourceKey<T>> keys = loadProjectEntriesOnly
            ? project.getModifiableEntries(registryKey).stream().map(Map.Entry::getKey).toList()
            : project.getMergedEntries(registryKey).stream().map(Map.Entry::getKey).toList();

        return new RegistryLookupModal<>(font, title, keys, onConfirm);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        // Cover the screen so we always intercept clicks (close-on-outside).
        return isActive();
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        // The frame grip is tested first: it lies just outside the page, where a press would otherwise
        // dismiss the modal.
        if (event.button() == 0 && resizer.mousePressed(event.x(), event.y())) {
            return true;
        }

        boolean overPage = event.x() >= getX() && event.x() < getRight() && event.y() >= getY() && event.y() < getBottom();

        if (!overPage) {
            AkytheraEditorLayoutManager.getInstance().closeModal();
            return true;
        }

        super.mouseClicked(event, doubleClick);

        return true;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        return resizer.mouseDragged(event.x(), event.y()) || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        return resizer.mouseReleased() || super.mouseReleased(event);
    }

    @Override
    public FrameResizer frameResizer() {
        return resizer;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return list.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
    public @NonNull List<? extends GuiEventListener> children() {
        return List.of(searchBox, list);
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // Page background + 1-posX border (no dimmer)
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
            getX() + 6,
            getY() + (TITLE_H - font.lineHeight) / 2,
            0xFFCCCCCC,
            false
        );

        searchBox.extractRenderState(graphics, mouseX, mouseY, a);
        list.extractRenderState(graphics, mouseX, mouseY, a);

        resizer.requestCursor(graphics, mouseX, mouseY);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
    }

    private final class EntryList extends AbstractSelectionList<EntryList.@NonNull Entry> {
        private final List<ResourceKey<T>> entries;

        EntryList(List<ResourceKey<T>> entries) {
            super(Minecraft.getInstance(), MODAL_W - 2, MODAL_H - TITLE_H - SEARCH_H - 1, 0, ROW_H);
            this.entries = entries;
        }

        void rebuild(String query) {
            clearEntries();

            String lower = query.toLowerCase();

            entries.stream()
                .filter(key -> lower.isEmpty() || key.identifier().toString().toLowerCase().contains(lower))
                .sorted(Comparator.comparing(key -> key.identifier().toString()))
                .forEach(key -> addEntry(new Entry(key)));

            setScrollAmount(0.0D);
            updateSizeAndPosition(getWidth(), getHeight(), getX(), getY());
        }

        @Override
        public int getRowWidth() {
            return getWidth() - SCROLLBAR_W;
        }

        @Override
        public int scrollbarWidth() {
            return SCROLLBAR_W;
        }

        @Override
        protected int scrollBarX() {
            return getRight() - SCROLLBAR_W;
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

            int sbX = scrollBarX();
            int scrollerH = scrollerHeight();
            int scrollerY = scrollBarY();
            boolean hovered = mouseX >= sbX && mouseX < sbX + SCROLLBAR_W && mouseY >= scrollerY && mouseY < scrollerY + scrollerH;

            graphics.fill(sbX, getY(), sbX + SCROLLBAR_W, getBottom(), 0xFF1A1A1A);
            graphics.fill(sbX + 1, scrollerY, sbX + SCROLLBAR_W - 1, scrollerY + scrollerH, hovered ? 0xFF6A6A6A : 0xFF505050);
        }

        @Override
        protected void extractSelection(GuiGraphicsExtractor graphics, Entry entry, int outlineColor) {
            int x = entry.getX();
            int y = entry.getY();
            int right = entry.getContentRight();
            int bottom = y + entry.getContentHeight();

            graphics.fill(x, y, right, bottom, 0xFF0A2833);
            graphics.outline(x, y, entry.getContentWidth(), entry.getHeight(), 0xFF37D5FF);
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
        }

        final class Entry extends AbstractSelectionList.Entry<@NonNull Entry> {
            private final ResourceKey<T> key;

            Entry(ResourceKey<T> key) {
                this.key = key;
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
                if (getSelected() != this && hovered) {
                    g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF262626);
                }

                g.text(
                    font,
                    Component.literal(key.identifier().toString()),
                    getX() + 6,
                    getY() + (getHeight() - font.lineHeight) / 2,
                    getSelected() == this ? 0xFFFFFFFF : 0xFFCCCCCC,
                    false
                );
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClicked) {
                setSelected(this);
                onConfirm.accept(key);
                AkytheraEditorLayoutManager.getInstance().closeModal();

                return true;
            }
        }
    }

    // ***************************************************************
    // [AnchoredWidget] implementations
    // ***************************************************************
    private Unit x1;
    private Unit x2 = AnchoredWidget.pixel(MODAL_W);
    private Unit y1;
    private Unit y2 = AnchoredWidget.pixel(MODAL_H);
    private final AnchoredWidget.HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.LEFT_WIDTH;
    private final AnchoredWidget.VerticalAnchorType verticalAnchorType = VerticalAnchorType.TOP_HEIGHT;

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

    /// [#MODAL_W] / [#MODAL_H] are only the **defaults**: the opener widens the modal to its anchor (see
    /// [AkytheraEditorLayoutManager#openModalWidgetUnder]), so the list is laid out against the rect the
    /// units just produced, never the constants.
    @Override
    public void relocate(ScreenRectangle screenRectangle) {
        AnchoredWidget.super.relocate(screenRectangle);

        searchBox.relocate(getRectangle());
        list.updateSizeAndPosition(
            getWidth() - 2, getHeight() - TITLE_H - SEARCH_H - 1, getX() + 1, getY() + TITLE_H + SEARCH_H);
    }
}