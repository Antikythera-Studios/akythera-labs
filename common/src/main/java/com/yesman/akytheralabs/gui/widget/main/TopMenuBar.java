package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.client.gui.widget.AnchoredButton;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.ContextMenuContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/// Top menu bar with File/Edit/View/Help menus and dropdown support.
///
/// Extends [AbstractContainerWidget] so [AnchoredButton] children are dispatched
/// through Minecraft's standard [ContainerEventHandler] input routing.
public final class TopMenuBar extends AbstractContainerWidget implements AnchoredWidget {
    private static final int ITEM_PAD = 8;
    private static final int TITLE_PAD = 6;
    private static final int DROPDOWN_W = 120;

    private final Font font;
    private final AnchoredButton closeScreenButton;
    private final List<AnchoredButton> menuButtonList;
    private final AnchoredButton[] menuButtons;

    /// One dropdown per top-level menu, built from the menu's option tree. A menu whose
    /// list is empty leaves its slot `null` (the button still renders but does nothing).
    /// Each dropdown is a [ContextMenuContainer] so options can nest into sub-lists
    /// (`Export > As Pack` / `As Source Code`) the same way right-click context menus do.
    private final ContextMenuContainer[] dropdowns;

    /// Optional unsaved-changes probe. When it returns `true` a `*` is drawn in front of the title.
    /// Editor screens wire this to a [com.yesman.akytheralabs.editor.historytrack.HistoryManager]
    /// save-point comparison; left null (always clean) for bars without a save concept.
    private @Nullable BooleanSupplier dirtyChecker;

    public TopMenuBar(
        Font font,
        Component title,
        Unit x1,
        Unit x2,
        Unit y1,
        Unit y2,
        AnchoredWidget.HorizontalAnchorType horizontalAnchorType,
        AnchoredWidget.VerticalAnchorType verticalAnchorType,
        Button.OnPress onClose,
        MenuBuilder menuBuilder
    ) {
        super(0, 0, 0, 0, title, AbstractScrollArea.defaultSettings(0));

        this.font = font;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchorType;
        this.verticalAnchorType = verticalAnchorType;

        int menuCount = menuBuilder.menus.size();
        MutableInt idx = new MutableInt();
        Minecraft minecraft = Minecraft.getInstance();

        menuButtons = new AnchoredButton[menuCount];
        dropdowns = new ContextMenuContainer[menuCount];

        for (Map.Entry<Component, TopMenuBar.MenuListBuilder> entry : menuBuilder.menus.entrySet()) {
            final int i = idx.intValue();

            menuButtons[i] = AnchoredButton.buttonBuilder(font, entry.getKey(), _ -> onMenuClick(i))
                .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(0))
                .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(0))
                .theme(AkytheraLabsButtonTheme.TOP_MENU_BUTTON)
                .isHighlighted(_ -> {
                    AbstractWidget w = AkytheraEditorLayoutManager.getInstance().getModal();
                    return w != null && w == dropdowns[i];
                })
                .build();

            if (!entry.getValue().nodes.isEmpty()) {
                dropdowns[i] = buildDropdown(minecraft, entry.getValue().nodes);
            }

            idx.increment();
        }

        closeScreenButton = AnchoredButton
            .buttonBuilder(font, Component.literal("X"), onClose)
            .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(0))
            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(1))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_BOTTOM)
            .theme(AkytheraLabsButtonTheme.TOP_MENU_BUTTON)
            .build();

        menuButtonList = Stream.concat(Stream.of(menuButtons), Stream.of(closeScreenButton)).toList();
    }

    /// Installs the unsaved-changes probe driving the title's `*` marker. See [#dirtyChecker].
    public void setDirtyChecker(BooleanSupplier dirtyChecker) {
        this.dirtyChecker = dirtyChecker;
    }

    private void onMenuClick(int i) {
        AkytheraEditorLayoutManager manager = AkytheraEditorLayoutManager.getInstance();

        if (manager.getModal() == dropdowns[i]) {
            manager.closeModal();
            return;
        }

        if (dropdowns[i] != null) {
            // Switching menus: pop whatever dropdown was up so they don't stack.
            if (manager.getModal() != null) {
                manager.closeModal();
            }

            int x = menuButtons[i].getX();
            int y = getY() + getHeight();
            // openModalWidget repositions the container (and closes any open sub-pages).
            manager.openModalWidget(dropdowns[i], x, y);
        }
    }

    /// Translates a top-level menu's option tree into a [ContextMenuContainer]. Sub-lists
    /// become sub-pages that unfold to the right on hover, mirroring the right-click menus.
    private static ContextMenuContainer buildDropdown(Minecraft minecraft, List<MenuNode> nodes) {
        ContextMenuContainer.PageBuilder page = ContextMenuContainer.builder(minecraft).pageWidth(DROPDOWN_W);
        populatePage(page, nodes);
        return page.build();
    }

    private static void populatePage(ContextMenuContainer.PageBuilder page, List<MenuNode> nodes) {
        for (MenuNode node : nodes) {
            var entry = page.entry().message(node.name());

            if (node.subList() != null) {
                populatePage(entry.subpage().pageWidth(DROPDOWN_W), node.subList());
            } else {
                Runnable action = node.onPress();
                entry.onPress(_ -> action.run());
            }
        }
    }

    private void layoutButtons() {
        int curX = getX();

        for (AnchoredButton menuButton : menuButtons) {
            int w = font.width(menuButton.getMessage()) + ITEM_PAD * 2;
            menuButton.setX(curX);
            menuButton.setY(getY());
            menuButton.setWidth(w);
            menuButton.setHeight(getHeight() - 1);
            curX += w;
        }

        closeScreenButton.setX1(AnchoredWidget.pixel(getWidth() - (getHeight() - 1)));
        closeScreenButton.setX2(AnchoredWidget.pixel(getHeight() - 1));
        closeScreenButton.relocate(getRectangle());
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return menuButtonList;
    }

    /// Content fits entirely within the bar height — disables scrolling.
    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();

        graphics.fill(x, y, x + w, y + h, 0xFF1A1A1A);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF2E2E2E);

        for (AnchoredButton button : menuButtons) {
            button.extractRenderState(graphics, mouseX, mouseY, delta);
        }

        // Prefix a `*` while the screen has unsaved changes (the save point no longer matches the
        // history stack's top), dropped again once a save re-marks the point.
        Component title = dirtyChecker != null && dirtyChecker.getAsBoolean()
            ? Component.literal("*").append(getMessage())
            : getMessage();
        int titleWidth = font.width(title);
        graphics.text(font, title, closeScreenButton.getX() - titleWidth - TITLE_PAD, y + (h - font.lineHeight) / 2, 0xFFCCCCCC);

        closeScreenButton.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narration) {}

    @Override
    public void relocate(ScreenRectangle screenRectangle) {
        AnchoredWidget.super.relocate(screenRectangle);
        layoutButtons();
    }

    public static MenuBuilder menuBuilder() {
        return new MenuBuilder();
    }

    public static final class MenuBuilder {
        private final Map<Component, MenuListBuilder> menus = new LinkedHashMap<>();

        public MenuListBuilder newList(final Component name) {
            MenuListBuilder builder = new MenuListBuilder(this);
            menus.put(name, builder);

            return builder;
        }
    }

    /// A single node of a menu's option tree: either a leaf option (carries [#onPress]) or a
    /// sub-list (carries [#subList]). Exactly one of the two is non-null.
    private record MenuNode(Component name, @Nullable Runnable onPress, @Nullable List<MenuNode> subList) {
        static MenuNode option(Component name, Runnable onPress) {
            return new MenuNode(name, onPress, null);
        }

        static MenuNode subList(Component name, List<MenuNode> subList) {
            return new MenuNode(name, null, subList);
        }
    }

    /// Top-level option list of a menu. [#done] walks back to the [MenuBuilder] so the next
    /// `.newList(...)` chains naturally, matching the previous flat API.
    public static final class MenuListBuilder {
        private final MenuBuilder owner;
        private final List<MenuNode> nodes = new ArrayList<>();

        private MenuListBuilder(final MenuBuilder owner) {
            this.owner = owner;
        }

        public MenuListBuilder newOption(final Component name, final Runnable onPress) {
            nodes.add(MenuNode.option(name, onPress));
            return this;
        }

        /// Opens a nested sub-list under @param `name`. Add options to it, then [SubListBuilder#done]
        /// to return here. The sub-list renders as a flyout to the right of its parent row.
        public SubListBuilder<MenuListBuilder> newSubList(final Component name) {
            SubListBuilder<MenuListBuilder> sub = new SubListBuilder<>(this);
            nodes.add(MenuNode.subList(name, sub.nodes));
            return sub;
        }

        public MenuBuilder done() {
            return owner;
        }
    }

    /// A nested option list. Generic over the parent builder type @param `<P>` so [#done] returns
    /// the exact builder that opened it (a [MenuListBuilder] or an outer [SubListBuilder]),
    /// keeping the fluent chain type-safe at any nesting depth.
    public static final class SubListBuilder<P> {
        private final P parent;
        private final List<MenuNode> nodes = new ArrayList<>();

        private SubListBuilder(final P parent) {
            this.parent = parent;
        }

        public SubListBuilder<P> newOption(final Component name, final Runnable onPress) {
            nodes.add(MenuNode.option(name, onPress));
            return this;
        }

        public SubListBuilder<SubListBuilder<P>> newSubList(final Component name) {
            SubListBuilder<SubListBuilder<P>> sub = new SubListBuilder<>(this);
            nodes.add(MenuNode.subList(name, sub.nodes));
            return sub;
        }

        public P done() {
            return parent;
        }
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