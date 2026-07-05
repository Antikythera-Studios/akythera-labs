package com.yesman.akytheralabs.gui.widget;

import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.config.AkytheraLabsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// Modal container that hosts one or more [ContextMenu] pages stacked
/// horizontally (parent to the left, subpage to the right).
///
/// The container is the [Modal] surface — it intercepts clicks anywhere on
/// screen and closes itself when the user clicks outside any of its pages.
public final class ContextMenuContainer extends AbstractContainerWidget implements Modal {
    private final ContextMenu rootPage;

    /// Visible pages in z-order. Index 0 is always [#rootPage]; later entries
    /// are subpages opened by clicking an [ContextMenu.Entry] with a child
    /// builder.
    private final List<ContextMenu> openedPages = new ArrayList<>();

    ContextMenuContainer(PageBuilder rootBuilder) {
        super(0, 0, 0, 0, Component.empty(), AbstractScrollArea.defaultSettings(0));

        this.rootPage = new ContextMenu(rootBuilder);
        this.rootPage.visible = true;

        // Lazy size initialization
        setSize(rootPage.getWidth(), rootPage.getHeight());
        openedPages.add(rootPage);
    }

    @Override
    public void setPosition(int x, int y) {
        // Direction-adaptive: flip the root page upward (or clamp) so it stays
        // fully on-screen when the cursor is near the bottom edge.
        int adjustedY = fitVertically(y, getHeight());

        super.setPosition(x, adjustedY);

        rootPage.setPosition(x, adjustedY);

        // Closing all subpages when re-anchoring (matches `openModalWidget` flow)
        while (openedPages.size() > 1) {
            ContextMenu p = openedPages.removeLast();
            p.close();
        }
    }

    /// Chooses a vertical origin for a page of `height` opened at cursor `y`,
    /// comparing the page's height against the room below and above the cursor:
    /// open downward when it fits below, flip upward (bottom at the cursor) when
    /// it only fits above, otherwise clamp so as much as possible stays visible.
    private int fitVertically(int y, int height) {
        int screenH = screenHeight();
        int spaceBelow = screenH - y;

        if (height <= spaceBelow) return y;            // fits below — open downward
        if (height <= y) return y - height;            // only fits above — flip upward
        return Math.max(0, screenH - height);          // fits neither — clamp to screen
    }

    private static int screenHeight() {
        return Minecraft.getInstance().getWindow().getScreenHeight() / AkytheraLabsConfig.getEditorGuiScale();
    }

    /// Opens a subpage to the right of @param `parent`'s row. Called by
    /// [ContextMenu.Entry] when its builder has a child [ContextMenu.EntryBuilder].
    void openSubpage(ContextMenu.Entry parent, ContextMenu childPage) {
        ContextMenu parentPage = parent.getOwner();
        int idx = openedPages.indexOf(parentPage);

        // Close any subpage opened from a sibling entry first.
        while (openedPages.size() > idx + 1) {
            ContextMenu p = openedPages.removeLast();
            p.close();
        }

        int x = parentPage.getRight();
        int y = fitVertically(parent.getContentY(), childPage.getHeight());

        childPage.setPosition(x, y);
        childPage.visible = true;
        childPage.active = true;

        openedPages.add(childPage);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        // Forward to the topmost page that contains the cursor; if none does,
        // the user clicked outside every page → close the modal.
        for (int i = openedPages.size() - 1; i >= 0; i--) {
            ContextMenu p = openedPages.get(i);

            if (p.isMouseOver(event.x(), event.y())) {
                return p.mouseClicked(event, doubleClick);
            }
        }

        AkytheraEditorLayoutManager.getInstance().closeModal();

        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        for (int i = openedPages.size() - 1; i >= 0; i--) {
            ContextMenu p = openedPages.get(i);

            if (p.isMouseOver(mx, my)) return p.mouseScrolled(mx, my, sx, sy);
        }

        return false;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (event.isEscape()) {
            AkytheraEditorLayoutManager.getInstance().closeModal();
            return true;
        }

        return false;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        // Cover the screen so we always intercept clicks (close-on-outside).
        return isActive();
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        for (ContextMenu p : openedPages) p.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return Collections.unmodifiableList(openedPages);
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
    }

    /// Building a context menu from root page
    public static PageBuilder builder(Minecraft minecraft) {
        return new PageBuilder(minecraft);
    }

    public static final class PageBuilder {
        private final List<ContextMenu.EntryBuilder> entries = new ArrayList<>();
        private final Minecraft minecraft;
        private int pageWidth = 140;

        @Nullable
        private final PageBuilder parentPage;
        private final int depth;

        /// Root page constructor
        PageBuilder(Minecraft minecraft) {
            this.minecraft = minecraft;
            this.parentPage = null;
            this.depth = 0;
        }

        /// Sub page constructor
        PageBuilder(@NonNull PageBuilder parentPage) {
            this.minecraft = parentPage.minecraft;
            this.parentPage = parentPage;
            this.depth = parentPage.depth + 1;
        }

        public PageBuilder pageWidth(int pageWidth) {
            this.pageWidth = pageWidth;
            return this;
        }

        /// Page width fitted to the widest entry text (plus the row's text indent and, for
        /// subpage entries, the chevron column), so long labels never clip. [#pageWidth] is
        /// the minimum. Evaluated at [ContextMenu] construction — after every entry's
        /// message/subpage is final regardless of builder call order.
        int adaptiveWidth() {
            int width = pageWidth;

            for (ContextMenu.EntryBuilder entry : entries) {
                int entryWidth = minecraft.font.width(entry.menuMessage) + ContextMenu.ENTRY_TEXT_MARGIN;

                if (entry.subpageBuilder != null) {
                    entryWidth += minecraft.font.width(ContextMenu.CHEVRON) + ContextMenu.CHEVRON_MARGIN;
                }

                width = Math.max(width, entryWidth);
            }

            return width;
        }

        /// Begins a new entry on the root page. Subsequent calls to
        /// [ContextMenu.EntryBuilder#subpage] and [ContextMenu.EntryBuilder#done]
        /// operate on the returned builder.
        public ContextMenu.EntryBuilder entry() {
            ContextMenu.EntryBuilder b = new ContextMenu.EntryBuilder(this, pageWidth);
            entries.add(b);
            return b;
        }

        public @NonNull PageBuilder done() {
            if (parentPage == null) throw new IllegalStateException("Tried to walk back from root page builder!");
            return parentPage;
        }

        public ContextMenuContainer build() {
            if (parentPage != null) throw new IllegalStateException("Only root page can build and return ContextMenuContainer");
            return new ContextMenuContainer(this);
        }
    }

    /// A single page within a [ContextMenuContainer]. Each page is a vertical list
    /// of [Entry] rows. An entry whose builder declares `.subpage(...)` opens
    /// another [ContextMenu] page to its right when clicked.
    ///
    /// Instances are produced by [PageBuilder]; the constructor is
    /// package-private.
    public class ContextMenu extends AbstractSelectionList<ContextMenu.@NonNull Entry> {
        private static final int ROW_HEIGHT = 16;
        private static final String CHEVRON = "ᐅ";

        /// Row chrome around an entry's text: page border + text indent on the left,
        /// matching breathing room on the right. See [Entry#extractContent].
        private static final int ENTRY_TEXT_MARGIN = 14;

        /// Gap + right offset reserved for the [#CHEVRON] column of subpage entries.
        private static final int CHEVRON_MARGIN = 8;

        private final int depth;

        ContextMenu(PageBuilder pageBuilder) {
            super(pageBuilder.minecraft, pageBuilder.adaptiveWidth(), pageBuilder.entries.size() * ROW_HEIGHT + 4, 0, ROW_HEIGHT);

            this.depth = pageBuilder.depth;

            visible = false;
            pageBuilder.entries.forEach(this::addEntry);
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
        }

        private void addEntry(EntryBuilder builder) {
            addEntry(new Entry(builder.menuMessage, builder.onPress, builder.predicate, builder.subpageBuilder));
        }

        @Override
        public void setPosition(int x, int y) {
            super.setPosition(x, y);

            // Reset scroll, and effectively reposition entries
            setScrollAmount(0.0F);
        }

        public void close() {
            visible = false;
            active = false;
        }

        @Override
        protected void extractListBackground(@NonNull GuiGraphicsExtractor graphics) {
            int left = getX();
            int right = getRight();
            int top = getY();
            int bottom = getBottom();

            // Outline
            graphics.fill(left, top, right, bottom, 0xFF555555);

            // Fill
            graphics.fill(left + 1, top + 1, right - 1, bottom - 1, 0xFF2A2A2A);
        }

        @Override
        protected void extractListSeparators(@NonNull GuiGraphicsExtractor graphics) {
            // Horizontal separators: draw nothing
        }

        @Override
        protected void extractSelection(@NonNull GuiGraphicsExtractor graphics, ContextMenu.@NonNull Entry entry, int outlineColor) {
            // Selection highlight: draw nothing
        }

        @Override
        public int getRowLeft() {
            return getX() + 1;
        }

        @Override
        public int getRowRight() {
            return getRowLeft() + getRowWidth();
        }

        @Override
        public int getRowWidth() {
            return getWidth() - 2;
        }

        public class Entry extends AbstractSelectionList.Entry<ContextMenu.@NonNull Entry> {
            private final Component menuMessage;
            private final Consumer<Entry> onPress;
            private final Supplier<Boolean> predicate;
            @Nullable
            private final ContextMenu subpage;

            private Entry(
                Component menuMessage,
                Consumer<Entry> onPress,
                Supplier<Boolean> predicate,
                @Nullable PageBuilder subpage
            ) {
                this.menuMessage = menuMessage;
                this.onPress = onPress;
                this.predicate = predicate;
                this.subpage = subpage == null ? null : new ContextMenu(subpage);
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
                if (!predicate.get()) {
                    return false;
                }

                if (subpage != null) {
                    ContextMenuContainer.this.openSubpage(this, subpage);
                    return true;
                }

                // Pop the context-menu container before running the action — the
                // action commonly opens another modal that would otherwise stack
                // on top of this menu.
                AkytheraEditorLayoutManager.getInstance().closeModal();
                onPress.accept(this);
                return true;
            }

            @Override
            public boolean keyPressed(@NonNull KeyEvent event) {
                if (event.isConfirmation()) {
                    onPress.accept(this);
                    return true;
                }

                return false;
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean hovered, float a) {
                boolean pressable = predicate.get();
                if (hovered && pressable) guiGraphics.fill(getContentX() - 2, getContentY() - 2, getContentRight() + 2, getContentBottom() + 2, 0xFF373737);

                guiGraphics.text(
                    ContextMenu.this.minecraft.font,
                    menuMessage,
                    getContentX() + 2,
                    getContentY() + (getContentHeight() - ContextMenu.this.minecraft.font.lineHeight) / 2,
                    pressable ? (hovered ? 0xFFEEEEEE : 0xFFCCCCCC) : 0xFF555555
                );

                if (subpage != null) {
                    int cx = getContentRight() - ContextMenu.this.minecraft.font.width(CHEVRON) - 4;
                    int cy = getContentY() + (getContentHeight() - ContextMenu.this.minecraft.font.lineHeight) / 2;
                    guiGraphics.text(ContextMenu.this.minecraft.font, CHEVRON, cx, cy, pressable ? 0xFFAAAAAA : 0xFF555555, false);
                }
            }

            public ContextMenu getOwner() {
                return ContextMenu.this;
            }
        }

        public static final class EntryBuilder {
            Component menuMessage = Component.empty();

            Supplier<Boolean> predicate = () -> true;

            /// Behavior when this entry pressed. Ignored when [#subpage] exists
            Consumer<Entry> onPress = _ -> {};

            /// When set, this entry opens a subpage instead of running [#onPress]
            @Nullable
            PageBuilder subpageBuilder;

            /// Parent reference for `end()`
            private final PageBuilder ownerPageBuilder;

            /// Width of the page this builder belongs to (root configures it).
            int pageWidth;

            EntryBuilder(PageBuilder ownerPageBuilder, int pageWidth) {
                this.ownerPageBuilder = ownerPageBuilder;
                this.pageWidth = pageWidth;
            }

            public EntryBuilder message(Component menuMessage) {
                this.menuMessage = menuMessage;
                return this;
            }

            public EntryBuilder onPress(Consumer<Entry> onPress) {
                this.onPress = onPress;
                return this;
            }

            public EntryBuilder predicate(Supplier<Boolean> predicate) {
                this.predicate = predicate;
                return this;
            }

            /// Unfold the subpage on hover this entry. Get back to parent page builder by [PageBuilder#done()]
            /// [#onPress] will be ignored if subpage is set up.
            public PageBuilder subpage() {
                subpageBuilder = new PageBuilder(ownerPageBuilder);
                return subpageBuilder;
            }

            /// Walks back to the owner page
            public PageBuilder done() {
                return ownerPageBuilder;
            }
        }
    }
}
